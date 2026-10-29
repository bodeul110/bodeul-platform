# 보호자 예약 승인 API 연결

기준일: 2026-09-29. Android 준비, 비활성 보호자의 기존 승인 조회·철회, 서버 원문 전달 계약은 2026-10-28에 보충했다. 원문 API의 Android 데이터 계층과 V27 파기 연결은 2026-10-29에 보충했다.

## 설계 판단

- 작업 목적: #419의 승인 저장소를 환자 본인 승인과 보호자 예약 INSERT에 연결한다.
- 선택한 방식: 기존 인증 필터와 Core API를 사용하고 요청 본문은 서버에서 정규화한다. 승인 ID·버전·본문을 잠근 최신 상태와 비교한 뒤 같은 트랜잭션에서 예약을 저장한다.
- 대안: 정보공유 동의 재사용, 보호자 역할만으로 생성 허용, 승인 후 별도 트랜잭션에서 예약 저장을 검토했다.
- 선택 이유: 현재 MVP에서는 기존 DB 트랜잭션과 요청별 승인으로 충분하다. 읽기 동의를 예약 생성 권한으로 확대하지 않고 철회·재승인 경합을 처리할 수 있다.
- 리스크: Firebase 계정 상태와 PostgreSQL은 하나의 분산 트랜잭션이 아니다. 생성 직전에 두 참여자의 Firebase 활성 여부와 DB 역할을 재확인하되 서로 다른 저장소의 상태 변경을 완전히 원자적이라고 주장하지 않는다.

## 활성화 경계

`bodeul.guardian-booking.enabled`의 기본값은 `false`다. 실제 개발 DB V24~V27 적용, 만료 원문 파기 작업의 환경 연결 검증, Android 환자 확인 화면과 보호자 요청 전달, DEV 종단 검증 전에는 켜지 않는다. 기존 예약 POST에서 보호자 생성을 거부하는 계약과 보호자 수정·취소 제한은 유지한다. 운영 배포와 DB migration은 이 작업에 포함하지 않는다.

환경변수 이름은 `BODEUL_GUARDIAN_BOOKING_ENABLED`다. 실제 배포 환경변수와 runtime DB 권한은 변경하지 않는다. V26 소스에는 새 원문 테이블의 Core 읽기·삽입과 파기 전용 role의 함수 실행 권한만 선언한다. Firebase 계정 조회에는 해당 환경 runtime의 `firebaseauth.users.get` 권한과 명시된 `FIREBASE_PROJECT_ID`가 필요하다. 권한·연결 실패를 활성 계정으로 간주하지 않고 503으로 닫는다. 이 권한의 실제 Cloud Run 검증도 활성화 전 조건이다.

## 입력과 응답

- 환자 본인이 보호자·요청 ID·예약 본문을 확인해 승인한다. 클라이언트의 fingerprint·정책·승인 시각·역할은 권한 근거로 받지 않는다.
- 서버가 같은 정규화 함수로 미리보기·승인·생성 본문을 처리한다. 승인 fingerprint에는 가격 정책과 확인 금액도 포함한다.
- 승인 유효기간은 서버 기준 최대 24시간이며 예약 시각을 넘기지 않는다. 이는 변경 가능한 MVP 기본값이고 법률상 보관기간을 뜻하지 않는다.
- 보호자는 환자가 승인한 ID·버전과 같은 본문으로 생성한다. 재승인·철회·만료·정책 변경 후에는 기존 승인으로 새 예약을 만들 수 없다.
- 접수 결과는 예약 ID와 접수 코드만 반환한다. 상태·병원·건강정보·위치·채팅·리포트 열람은 기존 예약별 정보공유 인가를 따른다. 같은 생성 요청의 재시도에도 새 정보공유 권한을 만들지 않는다.

기본 경로는 `/api/appointments/guardian-booking`이며 모든 요청은 Firebase 인증과 기존 App Check 설정을 따른다. 성공·도메인 오류 응답은 `Cache-Control: no-store`를 사용한다.

| 메서드와 접미사 | 호출자 | 입력과 결과 |
| --- | --- | --- |
| `POST /preview` | 환자 본인 | `guardianUserId`, `appointment`를 검증하고 정규화된 `{clientRequestId, draft, pricePolicyVersion, expectedFinalPrice}`를 반환 |
| `POST /approvals` | 환자 본인 | 위 입력과 `expectedVersion`, `adultPatientConfirmed=true`; 승인 ID·버전·시각·활성 여부 반환 |
| `GET /patients/{patientId}/guardians/{guardianId}/requests/{requestId}` | 지정 환자 또는 보호자 | 현재 승인 메타데이터와 `revocationOnly` 반환. 양쪽 활성 계정의 미승인 상태만 버전 0·비활성으로 반환 |
| `POST /guardians/{guardianId}/requests/{requestId}/revoke` | 환자 본인 | `expectedVersion`; 철회된 최신 메타데이터 반환 |
| `POST /create` | 지정 보호자 | `patientUserId`, `grantId`, `approvalVersion`, `appointment`; `{appointmentId, publicCode}` 접수증만 반환 |

`appointment`는 기존 예약 생성 요청의 평탄한 필드 구조다. `clientRequestId`, 환자 본인의 `linkedParticipantName/Phone/Email`, 병원·진료과·좌표, 서울 시간 `yyyy-MM-dd HH:mm` 형식의 예약 일시, 만남 장소·상태·복약·이동·결제 입력과 가격 확인을 포함한다. 미리보기 응답의 `draft`를 이 입력 필드로 다시 펼쳐 승인·생성에 사용한다. 이름과 연락처는 지정 환자의 현재 프로필과 일치해야 하며, 보호자의 다른 사용자 프로필 검색은 제공하지 않는다. 가격 정책은 `mvp-fixed-40000-v1`, 확인 금액은 40,000원이고 신규 쿠폰은 `NONE`만 허용한다.

버전과 확인 금액은 JSON 정수, 성인 확인은 JSON boolean만 받는다. 클라이언트가 보낸 시각·fingerprint·역할·승인 정책 값은 사용하지 않는다. 성인 여부는 환자의 자기선언이며 출생일 검증이나 법정대리인 증명 절차를 구현한 것은 아니다.

서버의 원문 저장·목록·조회와 저장된 원문으로 승인·접수하는 경로는 아래와 같이 준비했다. Android에는 기존 직접 본문 전달 API의 Repository, 전체 본문 확인·승인·철회·접수 상태 제어와 debug 합성 화면이 있다. 새 원문 API의 전용 Repository도 추가했지만, 수신·상대방 지정·복구 화면과 일반 사용자 진입점은 아직 연결하지 않았다. 보호자가 서버에서 환자 정보를 검색해 승인을 대신하는 경로도 없다. [Android 연결 설계](../design/guardian-booking-android.md)와 [저장 원문 데이터 계층](../design/guardian-booking-android-drafts.md)을 따른다.

## 서버 원문 전달

기본 경로는 위와 같다. 기존 본문 전달 API는 호환성을 위해 유지하고 새 저장 경로와 구분한다. 두 경로 모두 같은 예약 정규화·승인 행 잠금·INSERT 계약을 사용한다.

| 메서드와 접미사 | 호출자 | 입력과 결과 |
| --- | --- | --- |
| `POST /drafts` | 보호자 | `patientUserId`, `appointment`; 불변 초안의 식별자·저장 시각·만료 시각만 반환 |
| `GET /drafts` | 환자 또는 보호자 | 현재 당사자로 확인된 활성 요청 중 최신 50건의 식별자·시각만 반환. 원문·프로필은 목록에 넣지 않음 |
| `GET /drafts/guardians/{guardianId}/requests/{requestId}` | 지정 환자 또는 작성 보호자 | `{request, appointment, patient, guardian}`. `appointment`는 정규화된 생성 command, 상대방 표시는 현재 프로필의 `{name, maskedPhone}` |
| `POST /drafts/guardians/{guardianId}/requests/{requestId}/approvals` | 지정 환자 | `expectedVersion`, `adultPatientConfirmed`; 클라이언트 원문 대신 저장 원문으로 승인 |
| `POST /drafts/guardians/{guardianId}/requests/{requestId}/create` | 작성 보호자 | `grantId`, `approvalVersion`; 저장 원문으로 접수하며 응답은 기존 최소 접수증만 반환 |

`request`에는 `patientUserId`, `guardianUserId`, `clientRequestId`, `createdAt`, `expiresAt`만 포함한다. 서버 프로필은 상대방 표시용으로 이름과 전화번호 끝 4자리만 제공하며 이메일·Firebase UID는 추가하지 않는다. 원문 안의 환자 연락처는 보호자가 제출하고 서버 프로필 일치를 확인한 입력이다. 프로필 일치는 통신사 본인 인증이나 실명 확인을 의미하지 않는다.

초안은 보호자·요청 ID별로 불변이며 같은 정규화 입력의 재시도는 만료를 연장하지 않는다. 다른 환자·본문으로 같은 ID를 쓰면 `409 guardian_booking_draft_conflict`, 활성 초안 20건을 넘는 보호자 저장은 `429 guardian_booking_draft_limit`이다. 동일 보호자의 저장을 트랜잭션 잠금으로 직렬화해 동시 요청에도 한도를 유지한다. 환자 ID를 알아도 입력한 이름·연락처가 현재 프로필과 맞지 않으면 저장되지 않으며 검색 API는 제공하지 않는다.

조회 만료는 저장 후 최대 24시간과 예약 시각 중 이른 때다. 만료·파기·다른 당사자의 원문은 `404 guardian_booking_draft_not_found`로 응답한다. 비활성 상대방의 원문 조회·새 승인은 허용하지 않지만 기존 승인 메타데이터 조회·철회 예외는 그대로 유지한다. 인증·계정 조회 장애는 성공이나 빈 결과로 숨기지 않는다.

접수 응답이 유실된 보호자는 같은 요청 경로의 `/create`를 재호출할 수 있다. 서버가 현재 참여자와 기존 접수 소유권을 확인한 뒤, 원문이 만료·파기되었더라도 접수증만 돌려준다. 새 INSERT, 새 동의, 예약 상세 열람은 발생하지 않는다.

V26의 `preview_expired_guardian_booking_drafts(asOf)`는 파기 대상 건수만 조회한다. V27에서는 runtime이 `purge_expired_guardian_booking_drafts(jobId, limit)`를 호출해 원문 파기와 실행별 건수를 같은 트랜잭션으로 기록하며, 기존 `(asOf, limit)` 함수의 runtime 직접 호출은 회수한다. 최대 500건씩 처리하고 미래 기준 시각으로 활성 원문을 지울 수 없으며 현재 DB 시각까지로 제한한다. 요청 ID 재사용을 막기 위해 참여자 UUID·fingerprint·시각은 남긴다. 정기 실행 연결 코드는 #501로 `dev`에 병합했지만 기본 OFF이며 **실제 DB 적용·Functions 배포·삭제 실행은 별도**다. 원문 조회 만료가 물리적 파기 완료를 뜻하지 않는다. 관리자·일반 클라이언트·Core runtime은 파기 함수를 실행할 수 없다. [원문 설계](../design/guardian-booking-draft-handoff.md), [정기 파기 설계](../design/guardian-booking-retention.md)와 [검증 기록](../reports/issue-419-guardian-booking-retention-2026-10-28.md)을 함께 확인한다.

## 비활성 보호자의 기존 승인 철회

현재 DB 역할·Firebase 활성 상태가 확인된 환자는 자신의 기존 승인 메타데이터를 보호자 상태와 별개로 다시 조회할 수 있다. 보호자가 비활성·삭제되었거나 더 이상 GUARDIAN 역할이 아니면 GET 응답에 `revocationOnly=true`를 반환한다. 기존 승인이 없는 조합은 403으로 닫으며 임의 계정 조회나 새 승인 상태를 제공하지 않는다. 철회·만료된 이력도 같은 최소 메타데이터만 조회할 수 있다.

승인 응답 필드는 `grantId`, `version`, `policyVersion`, `grantedAt`, `expiresAt`, `revokedAt`, `active`, `revocationOnly`다. `active`는 승인 자체의 시각·철회·정책 유효성이지 참여 계정의 활성 여부나 예약 생성 허가가 아니다. `revocationOnly`는 GET의 화면 모드를 전달하는 값이고, 승인·철회 응답의 기본값 `false`는 권한 재부여를 뜻하지 않는다. 모든 쓰기는 서버에서 별도로 인가하며 미리보기·새 승인·생성의 양쪽 활성 계정 조건은 완화하지 않는다.

Android는 환자 본인의 기존 승인에 명시적인 boolean `true`가 온 경우에만 미리보기를 건너뛰고 검증되지 않은 본문을 숨긴 채 철회만 제공한다. 필드가 없는 구버전 응답은 기존 미리보기 경로를 유지하고, 문자열·숫자·null이나 보호자/미승인 상태의 `true`는 거부한다. 401·403·503을 철회 모드로 바꾸지 않는다. Firebase 조회 장애는 503이며 비활성으로 추정하지 않는다. [상세 판단](../design/guardian-booking-revocation-recovery.md)과 [검증 기록](../reports/issue-419-revocation-recovery-2026-10-28.md)을 따른다.

## 재시도와 동시성

새 예약은 승인 행의 `FOR UPDATE` 잠금부터 예약 INSERT까지 동일 Spring 트랜잭션으로 처리한다. 먼저 커밋된 철회는 대기 중인 생성을 거부하고, 생성이 먼저 잠금을 얻으면 예약 커밋 이후 철회가 진행된다. 철회는 이미 생성된 예약을 취소하지 않는다.

이미 생성된 같은 요청은 활성 참여자·환자 ID·요청자 역할·최초 본문 및 가격 fingerprint가 모두 일치할 때 접수증만 재전송한다. 이때 과거 승인 만료·철회는 접수증 재전송을 막지 않으며 새 INSERT와 정보공유 권한은 만들지 않는다. 다른 본문이나 환자로 같은 요청 ID를 재사용하면 409로 거부한다. 환자의 승인·철회는 최신 버전이 필요하며 경합 시 재조회 후 다시 확인한다.

승인 없는 새 생성, 만료·철회·재승인 충돌은 `409 guardian_booking_approval_conflict`, 비활성 계정·당사자/역할 불일치는 403, 계정 상태 확인 장애는 503이다. 위 환자 본인의 기존 승인 조회·철회만 비활성 보호자 예외이며 환자 본인의 인증·활성 검사는 유지한다. DB 원문 오류와 Firebase UID를 응답에 넣지 않는다.

## 개인정보와 영향도

승인 저장소에는 요청 본문 원문을 보관하지 않는다. 승인·감사 이력은 기존 추가 전용 계약을 유지하며, 계정 삭제 영향도에 현재 승인·활성 승인·감사 건수를 포함한다. V24가 없는 환경은 이 부분을 미확인으로 표시하며 0건이나 전체 조사 완료로 처리하지 않는다. 승인 만료는 권한 종료이며 감사 삭제를 뜻하지 않는다. 감사 파기는 보존 정책과 별도 승인된 운영 절차의 대상이며 이 API에 삭제 권한을 추가하지 않는다.

별도 V26 원문 저장소의 전체 최소 기록 수, 원문이 남은 수, 활성 원문 수도 계정 삭제 영향도에 포함한다. V26 미적용 환경은 기존 집계를 유지하되 PostgreSQL 원문 범위를 미확인으로 표시한다. 계정 삭제를 실제 실행하거나 파기된 원문의 최소 기록까지 자동 삭제하지 않는다. V26 rollback은 기록이 하나라도 남아 있으면 중단하며 기존 예약·승인·감사 테이블은 변경하지 않는다.

관리자 웹의 테이블 권한과 응답은 바뀌지 않는다. 예약 생성 후에도 정보공유 동의를 자동 발급하지 않는다.

## 감사 집계 인덱스

- 작업 목적: 본인 계정 삭제 영향도의 `patient_user_id = :userId OR guardian_user_id = :userId` 감사 집계가 보호자 조건 때문에 전체 이력을 순회하는 것을 방지한다.
- 선택한 방식: 이미 병합된 V24를 수정하지 않고 V25에서 `guardian_booking_approval_events (guardian_user_id)` B-tree 인덱스를 추가한다. 환자 조건은 기존 PK의 첫 열이 담당한다.
- 대안: 집계 쿼리를 두 개로 분리하거나 V24를 수정하는 방식 대신 후속 인덱스를 선택했다. 조회 의미와 기존 Flyway checksum을 보존한다.
- 선택 이유: 현재 MVP에서는 추가 인덱스 하나로 기존 OR 집계를 지원할 수 있다. [PostgreSQL의 인덱스 결합](https://www.postgresql.org/docs/17/indexes-bitmap-scans.html)처럼 두 인덱스를 `BitmapOr`로 사용할 수 있는지 격리 DB에서 확인한다.
- 리스크: 인덱스 저장 공간과 감사 INSERT 유지 비용이 증가한다. 일반 `CREATE INDEX`는 생성 중 쓰기를 막으므로 기능 OFF 상태에서 적용한다. 감사 데이터가 커진 뒤 적용한다면 [동시 인덱스 생성의 제약](https://www.postgresql.org/docs/17/sql-createindex.html#SQL-CREATEINDEX-CONCURRENTLY)을 고려한 별도 비트랜잭션 적용 계획이 필요하다.

V25 rollback은 새 인덱스만 제거하며 승인 상태·감사 이력·RLS·권한을 바꾸지 않는다. API의 V24 존재 여부 검사는 집계 결과의 완전성 검사이며 인덱스 적용을 대신 확인하지 않는다. 기능 활성화 전에는 별도 migration 이력에서 V25 성공까지 확인한다.

## 확인

로컬 `core-api check` 531건과 CI의 격리 PostgreSQL 17 테스트 35건을 통과했다. API 인증·엄격한 입력 타입·오류/캐시, 활성 계정·역할 재확인, 승인 ID/버전/본문/가격, 재시도 최소 응답과 삭제 영향도 부분 집계를 포함한다. PostgreSQL에서는 실제 INSERT, 중복 요청, 생성/철회/재승인 경합, 트랜잭션 rollback, 정보공유 차단과 runtime 권한도 검증했다. V25 보완으로 합성 감사 10,001건에서 인덱스 결합과 보호자 단독 인덱스 사용, 인덱스 rollback·재적용의 데이터 보존도 확인했다. 실제 DB 적용·환경 활성화·Android 실기기 검증과 이 테스트는 구분한다.

인증 계정의 삭제·비활성 확인은 [Firebase Admin 사용자 관리](https://firebase.google.com/docs/auth/admin/manage-users), 트랜잭션 행 잠금은 [PostgreSQL 문서](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS)를 따른다. 검증 증적은 [API 연결 기록](../reports/issue-419-booking-api-2026-09-29.md)에 남긴다.
