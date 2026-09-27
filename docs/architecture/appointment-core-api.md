# 예약 Core API 전환 계약

기준일: 2026-07-19

최종 갱신: 2026-09-24

보호자 예약 생성은 #419의 후속 구현 범위다. [예약 전 생성 승인 판정](adult-patient-guardian-booking-authorization.md)을 준비했지만 API에는 연결하지 않았으므로 아래의 보호자 생성·수정·취소 차단이 현재 동작이다. 예약 후 정보공유 동의를 생성 권한으로 확대하지 않는다.

## 작업 목적

Firestore `appointmentRequests`에 직접 쓰던 예약 기본 흐름을 Spring Core API와 PostgreSQL의 단일 쓰기 경로로 옮긴다.

## 선택한 방식

- `appointment_requests`를 기존 read model에서 PostgreSQL 운영 원본으로 승격한다.
- Firebase ID token 검증 뒤 PostgreSQL `app_users.role`과 예약 참여자 UUID를 확인한다.
- 예약 가격, 최초 상태, 결제 상태와 요청자 식별자는 서버가 결정한다.
- 무통장입금은 `BANK_TRANSFER`로 구분하고 입금 상태 전이는 PostgreSQL과 Core API만 변경한다.
- 생성 요청에는 `clientRequestId`, 수정·취소에는 `version`을 사용한다.
- Core runtime에는 SELECT·INSERT·UPDATE만 주고 물리 DELETE는 허용하지 않는다.
- Admin runtime의 일반 테이블 권한은 SELECT-only를 유지한다. 무통장입금 상태 변경은 `SUPER_ADMIN` 또는 `OPERATIONS`를 다시 확인하는 제한 `SECURITY DEFINER` 전이 함수의 EXECUTE만 예외로 둔다.

## 대안

1. Android가 Supabase Data API에 직접 쓰는 방식은 서버 인가와 가격 계산을 우회할 수 있어 제외했다.
2. Firestore와 PostgreSQL에 계속 이중 쓰는 방식은 장애 시 어느 쪽이 원본인지 결정하기 어려워 제외했다.
3. 매칭·세션·채팅까지 한 번에 옮기는 방식은 rollback 범위가 너무 커 예약 CRUD와 분리했다.

## 선택 이유

현재 MVP 규모에서는 예약 CRUD 하나를 먼저 끝내 DB role, API 인가, Android 네트워크와 backfill 절차를 검증하는 것이 운영 위험이 가장 작다. PostgreSQL 원본 전환 경험을 확보한 뒤 같은 방식을 매칭·동행·리포트에 적용할 수 있다.

## API

| 메서드 | 경로 | 용도 | 성공 응답 |
| --- | --- | --- | ---: |
| `GET` | `/api/appointments` | 환자·배정 매니저 예약 목록. 보호자는 유효한 `APPOINTMENT` 동의가 있는 예약만 포함 | 200 |
| `GET` | `/api/appointments/{id}` | 예약 상세. 보호자는 `APPOINTMENT` 동의 필수 | 200 |
| `POST` | `/api/appointments` | 환자 예약 생성. 보호자 신규 생성은 환자 프로필 조회 전에 거부 | 201 / 403 |
| `PUT` | `/api/appointments/{id}` | 환자 본인의 `REQUESTED` 예약 수정. 보호자 쓰기는 거부 | 200 / 403 |
| `POST` | `/api/appointments/{id}/cancel` | 환자 본인의 `REQUESTED`, `MATCHED` 예약 취소. 보호자 쓰기는 거부 | 200 / 403 |
| `GET` | `/api/appointments/{id}/guardian-sharing-consent` | 해당 환자·지정 보호자의 동의 상태 조회 | 200 |
| `PUT` | `/api/appointments/{id}/guardian-sharing-consent` | 성인 환자 본인의 범위별 동의 생성·갱신 | 200 |
| `DELETE` | `/api/appointments/{id}/guardian-sharing-consent` | 성인 환자 본인의 즉시 철회 | 200 |

예약 응답의 `publicCode`는 `BD-`와 영문 대문자·숫자 6자리로 구성한다. Core API가 생성할 때 발급하며, 목록·상세 조회자가 신청자 또는 배정 매니저인 경우에만 반환한다. 연결 참여자가 신청자가 아니면 빈 값으로 가린다. 코드는 표시용 식별자이고 내부 UUID나 인가 관계를 대체하지 않는다. 상세 계약은 [예약 공개 코드 계약](appointment-public-code.md)을 따른다.

모든 경로는 Firebase ID token이 필요하며 응답에 `Cache-Control: no-store`를 사용한다. 타인 예약은 403, 없는 예약은 404, 허용되지 않은 상태 전이와 오래된 `version`은 409를 반환한다.

## 서버 소유 값

- 기본 가격은 69,000원이다.
- 왕복은 22,000원, 보행 보조는 8,000원, 휠체어는 15,000원을 더한다.
- 첫 방문 쿠폰은 5,000원, 가족 쿠폰은 10,000원을 뺀다.
- 예약 최초 상태는 `REQUESTED`다.
- 무통장입금 예약은 서버가 `AWAITING_DEPOSIT`으로 시작한다. 클라이언트가 전달한 결제 상태는 사용하지 않는다.
- 입금 상태는 `AWAITING_DEPOSIT`, `DEPOSIT_CONFIRMED`, `REVIEW_REQUIRED`, `REFUND_REQUESTED`, `REFUNDED`, `CANCELED`만 서버가 전이한다.
- 기존 `PENDING`, `AUTHORIZED`, `DEFERRED`는 카드·간편결제 시뮬레이션과 현장결제, 전환 전 데이터 호환을 위해 유지하며 무통장입금 상태로 자동 변환하지 않는다.
- 클라이언트가 전달한 가격이나 결제 승인값은 신뢰하지 않는다.

## 무통장입금 MVP 계약

| 결제 상태 | 의미 | 변경 주체 |
| --- | --- | --- |
| `AWAITING_DEPOSIT` | 예약은 생성됐지만 입금을 확인하지 않은 상태 | 예약 생성 시 Core API |
| `DEPOSIT_CONFIRMED` | 운영자가 입금액과 예약을 확인한 상태 | Next.js 관리자 서버가 제한 전이 함수 호출 |
| `REVIEW_REQUIRED` | 입금자명·금액·시점 또는 취소 상태가 맞지 않아 수동 확인이 필요한 상태 | Next.js 관리자 서버가 제한 전이 함수 호출 |
| `REFUND_REQUESTED` | 고객센터에서 환불 요청을 접수한 상태 | Next.js 관리자 서버가 제한 전이 함수 호출 |
| `REFUNDED` | 운영자가 환불 완료를 기록한 상태 | Next.js 관리자 서버가 제한 전이 함수 호출 |
| `CANCELED` | 해당 예약의 입금 처리 흐름을 취소한 상태 | Core API 취소 경계 또는 제한 전이 함수 |

예약 업무 상태와 결제 상태는 서로 다른 값이다. `DEPOSIT_CONFIRMED`여도 예약 상태는 매칭 전까지 `REQUESTED`를 유지하며, Android의 `예약 접수 완료` 문구는 두 값을 조합한 표시 결과다. 일반 사용자는 결제 상태를 직접 변경할 수 없고, 입금 확인·검토·환불 변경에는 처리자와 처리 시각, 변경 사유를 감사 기록으로 남긴다.

취소 뒤 확인된 입금은 금액이 예상액과 같더라도 `CANCELED`에서 `REVIEW_REQUIRED`로 보내 수동 검토한다. 예약 자체가 이미 `CANCELED`인 검토 건은 `DEPOSIT_CONFIRMED`로 되돌리지 않고 `REFUND_REQUESTED`로만 진행한다. 입금자명이나 시점이 맞지 않는 경우에도 금액 일치만으로 자동 확정하지 않는다.

현재 개발·preview MVP에는 실제 은행명, 계좌번호와 예금주를 설정하지 않는다. 합성 fixture로 상태 전이를 검증할 수 있지만 앱과 Firestore→PostgreSQL seed 도구는 계좌 안내나 기한을 임의 생성하지 않는다. 1차 Core API는 `instructionAvailable=false`를 반환하고, nullable `paymentDueAt`이 없으면 기한도 표시하지 않는다. 운영 명의 계좌·현금영수증·환불 절차와 접근 책임자가 준비되기 전에는 production에서 실제 수취를 활성화하지 않는다.

Android는 환자 본인의 `BANK_TRANSFER` 예약에만 결제 화면을 노출한다. 전용 저장소가 Firebase ID token과 App Check token을 포함해 `GET /api/appointments/{appointmentId}/payment`를 호출하고, 입금 대기 또는 검토 상태에서만 `PATCH /api/appointments/{appointmentId}/payment/depositor`로 입금자명을 제출한다. PATCH에는 직전 조회의 `paymentVersion`과 새 `operationId`를 함께 보내며, 네트워크 오류 재시도는 같은 요청 본문과 작업 ID를 한 번만 재사용한다. 알 수 없는 결제 수단, 누락되거나 음수인 금액, 잘못된 예약 ID와 버전은 화면 값으로 보정하지 않고 실패 처리한다. 서버가 새 결제 상태를 반환하면 `UNKNOWN`으로 표시하고 변경 기능을 잠가 이전 상태로 오인하지 않게 한다.

결제 원장은 PostgreSQL이다. `appointment_requests.payment_status_code`는 목록·매칭용 projection이며, `appointment_bank_transfer_payments`는 상세 원장, append-only `appointment_payment_events`는 감사 이력이다. Firestore 결제 필드는 백필과 rollback 비교에만 사용한다. 메인 저장소는 V22·V23 함수, 사용자 조회·입금자명 API와 Android 환자 화면을 소유한다. 별도 관리자 웹의 PR #51·#52로 입금 확인·검토·환불 UI와 Preview 검증이 반영됐다. 브라우저가 DB에 직접 쓰지 않으며, 개발 fixture 통과는 실제 계좌 노출·금전 수취 승인과 다르다. 관리자 계약은 [무통장입금 관리자 계약](admin-bank-transfer-payment-contract.md)을 따른다.

Firestore 예약 seed는 V22 생성 trigger가 상세 원장을 완전하게 초기화할 수 있는 `BANK_TRANSFER` + `AWAITING_DEPOSIT` 조합만 SQL 생성 대상으로 허용한다. `DEPOSIT_CONFIRMED`, `REVIEW_REQUIRED`, `REFUND_REQUESTED`, `REFUNDED`, `CANCELED`인 기존 예약은 projection만 옮기면 상세 원장과 이벤트가 불완전해지므로 `needs_review`로 차단한다. 이 다섯 상태는 현재 projection, `appointment_bank_transfer_payments`와 `appointment_payment_events`를 함께 검증하는 별도 backfill로 이관해야 한다. 무통장입금 seed를 재적용할 때 기존 PostgreSQL 예약이 seed와 다르면 SQLSTATE `55000`으로 전체 작업을 중단하며, 일치하는 행도 Firestore 값으로 다시 갱신하지 않는다.

무통장입금 seed rollback은 결제 상태가 `AWAITING_DEPOSIT`이고 상세 원장이 빈 초기값과 `payment_version=0`을 유지하며 `CREATED` 이벤트가 정확히 한 건인 경우에만 허용한다. 조건을 통과하면 이벤트, 상세 원장, 예약 순서로 같은 트랜잭션에서 삭제한다. 입금자명 제출, 상태 전이 또는 추가 이벤트가 한 번이라도 있으면 운영 이력을 지우지 않고 SQLSTATE `55000`으로 rollback 전체를 중단한다.

## 데이터와 권한

- `firestore_id`와 `imported_at`은 전환 전 생성된 legacy 행에만 값이 있다.
- `client_request_id`는 사용자별 중복 생성을 차단한다.
- `create_request_fingerprint`는 요청자 ID·역할, `client_request_id`와 정규화된 클라이언트 생성 입력 전체의 SHA-256 지문이다. 현재 프로필 스냅샷, 가격과 최초 상태 같은 서버 파생값은 제외하고 예약 수정과 분리해 보존한다. 같은 `client_request_id` 재시도는 이 값이 정확히 일치할 때만 허용하며, 기존·seed 행처럼 지문이 없으면 409로 기본 거부한다.
- `version`은 수정·취소 경쟁을 검출한다.
- `payment_method_code`와 `payment_status_code`는 Core API가 검증하고, 운영 상태 변경은 최신 `version`과 관리자 권한을 모두 확인한다.
- `appointment_bank_transfer_payments`와 `appointment_payment_events`는 Core/Admin runtime의 직접 테이블 접근을 허용하지 않고 역할을 다시 검증하는 제한 함수만 실행한다.
- `app_users.name`, `email`, `phone`은 현재 사용자 스냅샷과 연결 계정 조회에 사용한다.
- V4 적용 시 기존 예약의 최신 스냅샷으로 참여 사용자 프로필을 우선 채운다.
- 전체 환자·보호자 프로필 백필이 끝나지 않은 사용자의 예약 생성은 409로 차단한다.
- V17은 예약별 보호자 정보공유 현재 상태·감사 이벤트·현재 정책 설정을 추가한다. 보호자 관계는 `APPOINTMENT` 열람 근거가 아니며 정책 버전 불일치, 만료와 철회는 모두 기본 거부한다.
- V22 계정 삭제 영향도는 예약 참여 관계뿐 아니라 무통장입금 확인 관리자와 결제 이벤트 처리자 관계도 건수로 포함한다. 입금자명 같은 결제 원문은 영향도 응답에 반환하지 않는다.

## 단계 경계

매칭 이후 취소는 `appointment_requests`와 `companion_sessions`를 같은 PostgreSQL 트랜잭션에서 갱신한다. 개발 환경에서는 #220과 #221을 거쳐 매칭·세션·채팅·실시간 상세까지 Core API 경계로 전환했다.

Android의 환자·보호자 예약 기본 경로는 다음 방식으로 전환했다.

1. `CoreApiBookingRepository`가 목록·상세·생성·수정·취소를 Core API로 보낸다.
2. 기존 예약의 `legacyFirestoreId`는 rollback 비교 식별자로 유지하지만 동행 세션·채팅·후속 기록은 Core API에서 읽는다.
3. PostgreSQL에서 새로 만든 예약은 Firestore 문서를 만들지 않으며 매칭 전 기본 상세만 표시한다.
4. Core API 오류가 나도 Firestore 예약 쓰기로 자동 대체하지 않는다.
5. 수정·취소 직전에 API 상세를 다시 읽어 최신 `version`으로 낙관적 잠금을 수행한다.
6. 보호자 화면은 예약 관계만으로 열리지 않으며 환자가 부여한 `APPOINTMENT` 범위를 Core API가 확인한다. 후기·정산 조회는 별도 `REPORT` 범위를 요구한다. 신규 긴급 지원 저장은 MVP에서 제공하지 않는다.

개발 환경에서는 V4~V12 적용, preview 배포, 실제 Firebase ID token 기반 역할별 API와 Android 예약·매칭·동행·채팅·위치 흐름을 확인했다. Firestore 신규 예약·세션·채팅·위치 쓰기는 차단했다. production 사용자 트래픽 전환은 별도 Go/No-Go와 migration 검증 뒤 수행한다.

## 리스크

- 프로필 연락처 중복이 있으면 연결 계정을 하나로 정할 수 없어 409를 반환한다.
- 현재 가격 규칙은 코드 상수이므로 운영자가 가격을 바꿔야 할 시점에는 별도 가격 정책 테이블이 필요하다.
- 실제 결제 승인 서버가 없으므로 카드·간편 결제 완료를 운영 사실로 간주하면 안 된다.
- 실제 계좌가 미설정된 상태에서 합성 계좌를 production 값으로 오인하거나 입금을 받으면 안 된다.
- 입금자명·금액 불일치와 취소 후 입금은 자동 확정하지 않고 `REVIEW_REQUIRED`에서 운영자가 확인해야 한다.
- 환불 상태는 수동 처리 기록일 뿐 은행 이체 완료를 자동 검증하지 않으므로 증빙과 이중 확인 절차가 필요하다.
- 기존 Firestore 예약·세션 문서는 rollback 비교 자료이므로 앱이나 운영 도구가 이를 업무 원본으로 다시 쓰지 않도록 회귀 검증해야 한다.
- source of truth 전환 뒤에는 Firestore 이중 쓰기로 rollback하지 않고 PostgreSQL 백업 또는 검증된 보정 절차를 사용한다.
- Core API 신규 버전은 V22가 적용된 뒤 배포해야 한다. V22 rollback은 데이터 검사를 하기 전에 예약·결제 원장·이벤트 테이블을 배타 잠그고, 새 생성 지문이나 결제 자료가 하나라도 있으면 기본 차단한다. 해당 자료를 보존·복원할 별도 절차 없이 열이나 테이블을 제거하지 않는다.
- 보호자의 신규 예약 생성은 환자 연락처나 프로필을 조회하기 전에 403으로 차단한다. 환자 본인이 예약을 만든 뒤 `APPOINTMENT` 공유 동의를 부여해야 한다. 의사결정 능력 제한·법정대리인 예약 경로는 이번 MVP에 포함하지 않는다.
