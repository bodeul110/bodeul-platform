# 계정 탈퇴·삭제 준비 상태

기준일: 2026-09-29

## 작업 목적

실제 계정 삭제를 구현하기 전에 인증된 본인과 연결된 저장소별 데이터 범위와 기술적 차단 사실을 원문 개인정보 없이 확인한다. 현재 API는 영향도 조사만 수행하며 탈퇴 승인, 삭제 순서 결정과 데이터 변경을 수행하지 않는다.

## 현재 API 계약

| 항목 | 값 |
| --- | --- |
| 경로 | `GET /api/account/deletion-readiness` |
| 사용자 식별 | 검증된 Firebase token으로 조회한 PostgreSQL principal UUID와 같은 principal의 Firebase UID |
| 요청 사용자 ID | 받지 않음 |
| 캐시 | `Cache-Control: no-store` |
| 삭제 실행 | 항상 `deletionExecuted=false` |
| 삭제 판단 | 항상 `decision=NOT_EVALUATED` |
| 전체 점검 완료 | 항상 `complete=false` |

응답에는 출처별 상태, 집계 건수, 관찰 코드와 점검 차단 코드만 포함한다. 사용자 UUID, Firebase UID, 개별 레코드 ID, 이름, 연락처, 건강정보, 채팅 본문, 좌표, 파일명과 Storage 경로는 반환하지 않는다.

## 출처별 상태

| 출처 | 현재 상태 | 확인 범위 |
| --- | --- | --- |
| PostgreSQL | V24 승인 저장소까지 집계 성공 시 `COMPLETE`, V24 미적용 시 `PARTIAL`, 연결·함수 오류 시 `ERROR` | 프로필, 예약, 세션, 리포트, 후속 처리, 배정 감사, 채팅, 첨부 메타데이터, 읽음, 위치, 무통장입금·이벤트와 예약 생성 승인·감사 건수 |
| Firestore | 본인 사용자·지원·필드별 직접 참조 집계 성공 시 `PARTIAL`, 일부 조회라도 실패하면 `ERROR` | `users/{firebaseUid}` 정확 조회, 지원 문의 2개 필드와 예약 4개·동행 세션 3개 UID 필드의 aggregation count 확인 |
| Firebase Storage | `NOT_EVALUATED` | 첨부·증빙 원본은 아직 미점검 |
| Firebase Auth | `NOT_EVALUATED` | 최근 재인증, token 폐기와 Auth 사용자 삭제는 아직 미점검 |
| 백업 | `NOT_EVALUATED` | 삭제 재적용 목록과 복원 후 재활성화 방지는 아직 미점검 |

PostgreSQL 출처가 `COMPLETE`이고 Firestore 출처가 `PARTIAL`이어도 전체 탈퇴 준비가 완료된 것은 아니다. Firestore의 전환 잔존·간접 연관 문서, Storage, Firebase Auth와 백업이 남아 있으므로 `INVENTORY_INCOMPLETE`는 항상 포함한다.

## Firestore 부분 점검 경계

Core API는 요청에서 Firebase UID를 받지 않고 인증된 `AppUser`에 저장된 Firebase UID만 사용한다. UID가 비어 있으면 Firestore에 접근하기 전에 오류로 닫는다. `users/{firebaseUid}` 문서는 정확 조회하고 나머지는 아래 equality aggregation count만 실행한다.

| 응답 key | Firestore 조건 |
| --- | --- |
| `clientSupportRequests` | `clientSupportRequests.userId == firebaseUid` |
| `supportInquiries` | `supportInquiries.managerUserId == firebaseUid` |
| `appointmentRequestsAsPatient` | `appointmentRequests.patientUserId == firebaseUid` |
| `appointmentRequestsAsGuardian` | `appointmentRequests.guardianUserId == firebaseUid` |
| `appointmentRequestsAsManager` | `appointmentRequests.managerUserId == firebaseUid` |
| `appointmentRequestsAsRequester` | `appointmentRequests.requesterUserId == firebaseUid` |
| `companionSessionsAsPatient` | `companionSessions.patientUserId == firebaseUid` |
| `companionSessionsAsGuardian` | `companionSessions.guardianUserId == firebaseUid` |
| `companionSessionsAsManager` | `companionSessions.managerUserId == firebaseUid` |

예약에서 요청자는 환자 또는 보호자와 같을 수 있고 한 사용자가 여러 역할을 가질 수도 있다. 각 key는 해당 필드의 직접 참조 건수이며 서로 더한 값은 고유 예약·세션 문서 수가 아니다. 그래서 `appointmentRequests`, `companionSessions` 또는 전체 합계 key는 제공하지 않는다. 현재 UID 필드가 없는 legacy 문서는 equality query에서 제외되므로 0건도 과거 관계가 없다는 증거로 사용하지 않는다.

응답에는 사용자 문서 존재 건수, 정규화한 FCM token 수, token 메타데이터 항목·불일치 수, 매니저 증빙 메타데이터·경로 참조 수와 위 직접 참조 건수만 포함한다. aggregation query는 문서 ID, 문의 제목·본문·답변, 예약·세션 내용과 다른 필드를 Core API로 가져오지 않는다. UID, token, 메타데이터 키, 파일명, Storage 경로와 사용자 문서 원문은 응답이나 로그에 넣지 않는다.

사용자 문서 조회와 아홉 aggregation query는 모두 먼저 시작하고 전체 12초 제한을 공유한다. 하나라도 권한, 인덱스, quota, timeout 또는 SDK 오류로 실패하면 시작된 작업을 모두 취소하고 성공한 일부 건수도 반환하지 않는다. Firestore 출처 전체를 `ERROR`, 빈 counts와 `SOURCE_UNAVAILABLE`로 처리한다. 이 단계는 UID 필드가 없거나 잘못된 legacy 문서, `sessionId`·`requestId`로만 연결되는 리포트·후속 처리·관리자 파생 문서, Storage 객체, Firebase Auth 사용자와 백업을 확인하지 않으므로 성공해도 `COMPLETE`가 아닌 `PARTIAL`이다.

Firebase Admin SDK는 `firestore.rules`가 아니라 런타임 서비스 계정의 IAM으로 조회한다. 모의 SDK 테스트는 각 query의 실패·timeout·취소 계약을 검증하고, 격리 Firestore Emulator 테스트는 합성 UID 문서의 실제 aggregation count와 타 UID 격리, Firestore source 장애 시 `ERROR`·빈 counts를 검증한다. Emulator는 IAM을 재현하지 않으므로 개발 Cloud Run 서비스 계정의 aggregation query 권한은 별도 실호출 전까지 미검증이다.

`notificationTokens` 건수는 문자열만 trim한 뒤 빈 값과 중복을 제외해 계산한다. token metadata map key는 Android 저장 계약과 같은 token UTF-8의 Base64 URL·padding 제거 값으로 검증한다. `notificationTokenEntryMismatches`는 아래 다섯 범주의 합계다.

- 유효한 token 배열 값에 대응하는 유효 metadata token이 없는 건수
- token 배열에 없는 유효 metadata token의 중복 제거 건수
- map이 아니거나 nonblank 문자열 `token`이 없는 metadata 항목 건수
- 같은 유효 token을 가리키는 metadata 항목 중 첫 항목을 제외한 중복 건수
- metadata의 token은 유효하지만 map key가 해당 token의 인코딩 값과 다른 항목 건수

## PostgreSQL 권한 경계

Flyway V15의 `bodeul.account_deletion_postgres_inventory(uuid)` 함수가 집계를 담당한다. 함수는 `bodeul_migration`이 소유하고 `search_path`를 고정한 `SECURITY DEFINER`로 실행한다. Core API와 관리자 서버 runtime에는 함수 실행 권한만 부여하며 배정 감사 테이블의 원문 조회 권한을 Core runtime에 추가하지 않는다.

집계는 현재 사용자와 직접 연결된 FK뿐 아니라 사용자의 예약·세션을 통해 연결된 행을 포함한다. 과거 배정 매니저처럼 현재 세션 관계에서는 빠질 수 있는 경우를 위해 채팅 발신자, 위치 기록자와 후속 처리 작성자 직접 참조도 별도로 포함한다. 유효한 legal hold는 DB 내부 영향도에는 포함하지만 내부 조사·분쟁 보존 사실의 공개 정책이 정해지기 전까지 본인 API의 건수와 관찰 코드에는 노출하지 않는다.

## 관찰 코드

보호자 예약 생성 승인 저장은 V24의 기존 Core SELECT 권한으로 별도 집계한다. 본인이 환자 또는 지정 보호자인 현재 행 수 `guardianBookingApprovals`, 철회되지 않고 유효기간 안에 있는 행 수 `activeGuardianBookingApprovals`, 감사 수 `guardianBookingApprovalAudits`를 반환한다. 현재 상태와 감사 원문은 반환하지 않는다. 테이블이 없으면 이 세 key를 생략하고 PostgreSQL을 `PARTIAL`로 표시한다. 테이블·집계 오류는 출처 전체를 `ERROR`로 처리한다. 승인 만료는 권한 종료일 뿐 감사 삭제가 아니며 자동 파기나 탈퇴 적용 권한은 추가하지 않는다.

감사 테이블의 환자 조건은 기존 PK, 보호자 조건은 V25의 `ix_guardian_booking_approval_events_guardian` 인덱스로 지원한다. V25는 조회 결과·RLS·권한을 변경하지 않으며 실제 환경에 적용하기 전에는 이 성능 보완도 적용 완료로 간주하지 않는다.

| 코드 | 의미 |
| --- | --- |
| `ACTIVE_APPOINTMENT_PRESENT` | 완료·취소되지 않은 연관 예약이 있음 |
| `ACTIVE_SESSION_PRESENT` | 완료·취소되지 않은 연관 동행 세션이 있음 |
| `ACTIVE_BOOKING_APPROVAL_PRESENT` | 본인이 참여한 유효한 예약 생성 승인이 있음 |
| `POSTGRES_PROFILE_MISSING` | 인증 principal에 대응하는 PostgreSQL 프로필 집계가 없음 |

관찰 코드는 현재 데이터의 사실만 나타내며 탈퇴 가능·불가를 결정하지 않는다. 특히 진행 중 예약·세션을 어떤 절차로 처리할지는 정책·운영 승인 대상이다.

## 점검 차단 코드

| 코드 | 의미 |
| --- | --- |
| `SOURCE_UNAVAILABLE` | PostgreSQL 또는 이번 범위의 Firestore 집계 출처를 조회할 수 없음 |
| `INVENTORY_INCOMPLETE` | 모든 저장소와 백업의 영향도 점검이 아직 끝나지 않음 |

점검 차단 코드는 영향도 조사가 완전하지 않음을 나타낼 뿐 삭제 가능 여부를 판정하지 않는다.

## 선택한 방식과 대안

현재 MVP 규모에서는 실제 삭제 orchestration보다 원문을 노출하지 않는 영향도 API를 먼저 두는 편이 부분 실패와 과삭제 위험을 줄인다. Core runtime에 여러 테이블의 추가 권한을 부여하는 방식은 배정 감사 원문까지 노출하므로 제외하고, 집계 전용 함수 실행 권한만 부여했다. Firestore도 전체 컬렉션을 읽지 않고 사용자 문서는 exact get, 직접 UID 필드가 있는 문서는 aggregation count로 제한해 읽기 비용과 개인정보 노출 범위를 줄였다.

예약·세션마다 여러 역할 필드를 `OR`로 묶어 고유 문서 수를 계산하는 대안은 중복 제거 의미와 legacy 필드 누락을 숨길 수 있어 선택하지 않았다. 현재 삭제 영향도에는 역할별 직접 참조가 더 정확하며, 간접 관계는 별도 계약을 정한 뒤 추가한다.

클라이언트가 사용자 ID를 보내게 하는 방식은 다른 계정의 존재와 건수를 조회할 위험이 있어 제외했다. 실제 삭제 API와 `canDelete` 같은 승인값도 정책이 확정되지 않은 상태에서 클라이언트가 삭제 가능으로 오해할 수 있어 이번 범위에 넣지 않았다.

## 검증 기준

- 인증이 없으면 401이며 다른 사용자 ID를 지정할 입력이 없다.
- 응답은 읽기 전용·미판정·미완료 상태를 유지하고 민감 식별자를 포함하지 않는다.
- PostgreSQL 연결 또는 함수 오류는 `SOURCE_UNAVAILABLE`로 닫힌다.
- Firestore는 인증 principal의 Firebase UID로 사용자 문서를 정확 조회하고, 지원·예약·세션은 허용된 직접 UID 필드의 aggregation count만 실행한다.
- 역할별 건수는 서로 독립적으로 유지하고 예약·세션 문서 총계나 역할 합계를 만들지 않는다.
- 사용자 문서 또는 집계 중 하나라도 실패하면 원문 없이 `ERROR`, 빈 counts와 `SOURCE_UNAVAILABLE`로 닫힌다.
- Firestore 집계 결과는 UID, token, 문서 ID, 문의 원문, 파일명과 Storage 경로 없이 건수만 반환하며 성공해도 `PARTIAL`을 유지한다.
- `npm --prefix tools/firebase run test:core-api-firestore-emulator`는 `demo-` project의 로컬 Firestore Emulator에서만 합성 fixture를 사용하고 운영·개발 Firebase에는 접근하지 않는다.
- migration은 집계 함수 외 DML 권한을 추가하지 않고 rollback은 해당 함수만 제거한다.
- 개발·운영 migration workflow는 Flyway 적용 뒤 함수 소유·고정 경로, 실제 Core/Admin 서비스 역할의 최소 실행 권한, Core 서비스의 배정 감사 원문 차단과 합성 UUID 0건 결과를 읽기 전용 트랜잭션에서 확인한다.
- Core API 전체 테스트를 통과해야 한다.

## 남은 범위

- Firestore 예약·세션의 legacy 필드 누락, 관리자 파생·간접 연관 문서, Storage, Firebase Auth와 백업 영향도 점검기
- 개발 Cloud Run 서비스 계정의 IAM을 사용한 비식별 fixture Firestore aggregation 실호출
- 최근 재인증과 token 폐기 확인
- 법정 보존자료 분리, tombstone·비식별화와 FK 처리 방식
- legal hold 존재 여부를 사용자에게 공개할지에 대한 정책·법률 확인
- 저장소별 삭제 순서, 부분 실패 재시도와 감사 이벤트
- 복원된 백업에 삭제를 재적용하는 삭제 ledger
- 가상 fixture 기반 정상 탈퇴, 진행 중 예약, legal hold, 부분 실패와 재가입 검증

실제 삭제 기능의 완료 조건과 정책 추적은 [#348](https://github.com/bodeul110/Bodeul/issues/348), 보관·파기 기본값은 [데이터 보관 및 파기 정책](data-retention-policy.md)을 따른다.
