# 관리자 무통장입금 계약

## 작업 판단

- 작업 목적: #27의 사용자 결제 계약을 관리자 조회·입금 대조·수동 환불 기록까지 연결한다.
- 선택한 방식: 관리자 서버가 전용 PostgreSQL role로 제한 함수만 호출한다. V23은 단일 예약 상세와 최근 20개 처리 이력 조회를 추가하며, 상태 변경은 V22 함수를 재사용한다.
- 대안: 관리자 runtime에 원장 SELECT·UPDATE 권한을 주거나 Spring을 중간 proxy로 두는 방식을 검토했다.
- 선택 이유: 현재 MVP 규모에서는 기존 예약 코드 검색을 진입점으로 재사용하면 전체 환자·결제 목록 노출 없이 필요한 예약만 처리할 수 있다. 기존 DB 트랜잭션·역할·중복 방지 규칙도 유지된다.
- 리스크: 조회 결과에는 대조에 필요한 입금자명과 처리 사유가 포함된다. 운영 역할만 허용하고 조회 감사를 남기며 HTTP·브라우저 영구 캐시와 원문 로그를 금지한다.

## 공용 DB 경계

| 용도 | 함수 | 호출 역할 |
| --- | --- | --- |
| 결제 상세·이력 | `get_admin_bank_transfer_payment(actor_admin_user_id, appointment_request_id)` | `bodeul_admin_runtime` |
| 입금 확인·검토·환불 상태 변경 | `transition_appointment_bank_transfer_payment(appointment_request_id, actor_admin_user_id, operation_id, expected_payment_version, target_status, received_amount, reason)` | `bodeul_admin_runtime` |
| 환자 본인 조회·입금자명 수정 | 기존 V22 환자 함수 | `bodeul_core_runtime` |

관리자 서버는 Firebase ID token과 App Check/MFA 정책을 검증하고 PostgreSQL에서 활성 역할을 확인한다. DB 함수도 `app_users.role=ADMIN`과 철회되지 않은 `SUPER_ADMIN` 또는 `OPERATIONS`를 다시 확인한다. `DEVELOPER`, 철회 역할과 일반 사용자는 거부한다. 브라우저가 보낸 관리자 ID를 신뢰하지 않는다.

조회 결과는 예약 ID·공개 코드·예약 및 결제 상태, 기대 금액·입금자명·기한·기록된 금액, 확인 담당자·시각, 환불 요청·완료 시각, 결제 버전, 최근 20개 이벤트와 `hasMoreEvents`다. 이벤트에는 작업 지문·연락처·인증 UID가 없으며, 더 오래된 이력이 있으면 전체 이력을 보여 준 것으로 표시하지 않는다. 조회 시 `RAW_VIEW / APPOINTMENT_PAYMENT` 감사를 같은 트랜잭션에 남긴다.

원장·이벤트·감사의 직접 접근 권한을 새로 부여하지 않는다. 제한된 `SECURITY DEFINER` 함수는 고정 `search_path`와 명시적 EXECUTE 회수·부여를 사용한다. [Supabase 함수 권한 문서](https://supabase.com/docs/guides/database/functions)의 주의사항과 기존 V22 경계를 따른다.

## 관리자 웹 영향

- 구현 위치: 별도 `bodeul110/bodeul-admin-web` 저장소의 Next.js 서버와 예약 코드 검색 화면.
- 정확한 예약 코드 검색 뒤 해당 예약의 결제 상세를 연다. 새로운 전체 결제 목록은 이번 범위에 추가하지 않는다.
- `DEPOSIT_CONFIRMED`, `REVIEW_REQUIRED`, `REFUND_REQUESTED`, `REFUNDED`만 관리자 상태 변경 대상으로 받는다. 입금자명 변경과 예약 취소는 기존 사용자 계약의 책임이다.
- 상태 변경은 UUID 작업 ID, 기대 결제 버전, 10~500자 사유를 요구한다. 입금 확인·검토는 정수 금액을 요구하고 환불 상태 변경에는 금액을 다시 받지 않는다.
- 전송 결과가 불명확하면 같은 작업 ID·payload를 재시도한다. 변경 사유나 금액을 바꾸어 같은 ID를 재사용하지 않는다. 충돌 뒤에는 다시 조회해 판단한다.
- 실제 송금이나 환불 API를 호출하지 않고 운영자가 확인한 상태만 기록한다. 웹 쓰기는 별도 서버 gate로 기본 차단하며 production 운영 승인과 혼동하지 않는다.
- 요금 변경 후 새 예약은 기본 2시간 40,000원이지만 관리자 화면은 항상 원장의 기대 금액을 사용한다. 이전 예약의 69,000원·96,000원 등을 현재 가격으로 덮어쓰지 않는다. 원장 생성은 예약 `final_price`를 복사하는 기존 V22 계약을 그대로 사용한다.
- `paymentDueAt`이 없는 예약에 웹이 임의 마감 시각을 생성하거나 자동 취소·환불을 적용하지 않는다. Android의 24시간 입금 원칙 안내와 개별 기한 저장·공유는 구분한다.

2026-09-28 요금 변경은 Android와 Core API의 신규 견적·기존 예약 보존 범위다. 관리자 웹 소스나 DB 권한·schema는 변경하지 않는다. [검증 기록](../reports/issue-27-mvp-price-2026-09-28.md)에 로컬 테스트·격리 PostgreSQL CI와 미수행 실서버·브라우저 통합 검증을 구분했다.

## 적용·복구

1. 메인 저장소 V23 PR을 병합하고 승인된 Preview migration workflow로 적용한다.
2. 의존하는 관리자 웹 PR을 검증·병합하고 Vercel Preview에서 관리자 성공·비관리자 거부와 합성 상태 전이를 확인한다.
3. production 계좌 노출·금전 수취·환불은 #27의 운영 gate 전까지 활성화하지 않는다.

V23 rollback은 조회 함수만 제거한다. 원장·결제 이벤트·관리자 감사는 보존하며 V22 rollback보다 먼저 실행한다. 사용자 Core API 계약과 Android에는 변경이 없다.

2026-09-05 개발 DB V23 적용, 승인된 최초 관리자 역할 부여와 실제 Preview의 관리자 조회 `200`·일반 사용자 거부 `403`·쓰기 잠금 `423`을 확인했다. [개발 DB 적용·연동 검증 기록](../reports/issue-27-admin-payment-integration-2026-09-05.md)에 근거와 남은 상태 변경·MFA/App Check 검증 범위를 기록했다. 코드 병합·DB 적용과 Production 결제 활성화는 별도다.
