# 성인 환자·보호자 정보공유 동의 계약

기준일: 2026-08-29

상태: PostgreSQL·Core API·Android·Firebase Rules 구현 완료, 개발 DB 적용과 배포 전

2026-09-24 보충: 위 상태는 이 문서의 기존 구현 시점 기록이며 새 배포 확인 결과가 아니다. #419에서 준비하는 [예약 전 생성 승인](adult-patient-guardian-booking-authorization.md)은 이 정보공유 계약과 별도다. 현재 API의 보호자 생성 차단과 범위별 조회 검사는 그대로 유지한다.

## 작업 목적

성인 환자가 지정 보호자에게 예약 단위로 필요한 정보만 공유하고 언제든 철회할 수 있게 한다. 예약 참여 관계나 가족 관계만으로 보호자 열람권이 생기지 않게 하는 것이 핵심이다.

## 선택한 방식

- #375의 `AdultPatientGuardianSharingPolicy` 순수 판정 계약을 저장·API 인가의 공통 판정기로 사용한다.
- 현재 동의와 추가 전용 감사 이력을 `guardian_sharing_consents`, `guardian_sharing_consent_events`로 분리한다.
- 현재 정책 버전과 위치 기능 플래그는 `guardian_sharing_consent_settings` 한 행을 Core API가 읽는다.
- 동의는 예약 1건과 지정 보호자 1명에 묶고, 재동의는 현재 행의 `version`을 올리면서 감사 이벤트를 새로 남긴다.
- Android는 성인 환자 본인 확인과 범위 선택을 함께 받고 Core API만 호출한다.
- 보호자는 Firestore 전환 비교 문서와 Firebase Storage 첨부에 직접 접근하지 않고 Core API의 PostgreSQL 동의 판정을 거친다.

## 대안

1. 예약 참여 관계만으로 보호자의 모든 조회를 허용하면 동의 없는 민감정보 노출을 막을 수 없어 제외했다.
2. 동의 범위를 하나로 합치면 예약 일정 공유가 위치·리포트 공개까지 넓어질 수 있어 제외했다.
3. 정책 버전을 환경변수와 DB에 각각 두면 Core API 판정이 배포 단위마다 달라질 수 있어 DB 단일 원본을 선택했다.
4. 의사결정 능력 제한과 법정대리인 증빙까지 같은 흐름에 넣는 방식은 법률·운영 절차가 확정되지 않아 MVP에서 제외했다.

## 선택 이유

현재 MVP 규모에서는 환자 본인이 직접 선택하는 정상 경로를 먼저 완결하고, 모호한 대리권 경로는 기본 거부하는 편이 안전하다. 현재 프로필에는 생년월일 검증 원본이 없으므로 성인 본인 확인 체크와 시각을 증적으로 남긴다. 향후 본인확인·생년 데이터가 생기면 자기선언을 서버 검증으로 교체하되 동의 범위와 감사 구조는 유지할 수 있다.

## 정보 범위

| 범위 | 허용되는 보호자 데이터 | 범위가 없을 때 |
| --- | --- | --- |
| `APPOINTMENT` | 예약 일정·병원·상태의 최소 조회, 세션 기본 상태 | 예약·세션 조회 거부 |
| `CHAT` | 채팅 본문·읽음 상태, 채팅 FCM | 채팅 조회·작성·알림 거부 |
| `ATTACHMENT` | 채팅 첨부 metadata·원본 | 첨부 숨김·업로드·다운로드 거부 |
| `REPORT` | 동행 결과와 리포트 필드 | 리포트 조회와 결과 필드 숨김 |
| `LOCATION` | 위치 snapshot·위치 알림 | 좌표·위치 요약·알림·Realtime topic 거부 |

첨부는 `CHAT`과 `ATTACHMENT`가 모두 있어야 접근한다. `ATTACHMENT`만 선택하는 요청은 API와 Android에서 모두 거부한다. 보호자는 범위와 관계없이 Supabase Broadcast를 구독하지 않고, 매 요청마다 동의를 다시 판정하는 Core API polling만 사용한다. 환자와 배정 매니저의 private Broadcast는 유지한다.

`location_sharing_enabled` 기본값은 `false`다. 동의에 `LOCATION`이 포함되어도 기능 플래그가 꺼져 있으면 Core API는 위치를 공개하지 않는다. 플래그를 켜는 작업은 별도 운영 검증 뒤 수행한다.

## 저장 계약

V17 migration은 다음 값을 저장한다.

- 예약, 환자, 지정 보호자 ID
- 선택 범위와 정책 버전
- 동의 행위자, 동의 시각과 성인 본인 확인 시각
- 임시 만료 시각, 실제 동행 종료·취소 시각과 만료 확정 여부
- 철회 행위자와 철회 시각
- 낙관적 잠금 `version`

동행 진행 중에는 지연으로 동의가 먼저 만료되지 않도록 만료를 임시 상태로 저장하고, 매 요청 인가에서 진행 중 동의를 만료로 거부하지 않는다. 예약 취소 또는 동행 완료가 기록되면 실제 취소·완료 시각을 `care_ended_at`에 저장하고 만료를 정확히 7일 뒤로 확정한다. 원본 데이터의 보관기간이 더 짧으면 동의가 남아 있어도 삭제된 원본을 복원하거나 열람권을 연장하지 않는다.

Core runtime만 현재 상태의 `SELECT`, `INSERT`, `UPDATE`와 감사 이벤트 `SELECT`, `INSERT`를 갖는다. 물리 `DELETE`는 허용하지 않는다. `anon`, `authenticated`, `service_role`, Admin runtime은 동의 테이블에 직접 접근하지 않는다. 세 테이블에는 RLS를 활성화한다.

## Core API

| 메서드 | 경로 | 주체 | 처리 |
| --- | --- | --- | --- |
| `GET` | `/api/appointments/{id}/guardian-sharing-consent` | 해당 환자·지정 보호자 | 현재 동의, 범위, 만료·철회, 활성 상태 조회 |
| `PUT` | `/api/appointments/{id}/guardian-sharing-consent` | 해당 성인 환자 | 본인 확인과 범위를 검증한 뒤 생성·갱신 |
| `DELETE` | `/api/appointments/{id}/guardian-sharing-consent` | 해당 성인 환자 | 즉시 철회, 반복 철회는 같은 결과 반환 |

`PUT` 요청은 `scopes`와 `adultPatientConfirmed=true`를 요구한다. 종료·취소 예약의 새 동의, 대상 보호자 역할 불일치, 빈 범위, 위치 기능 비활성 상태의 `LOCATION` 요청은 거부한다. 응답은 `Cache-Control: no-store`다.

인가 시 동의 없음, 다른 예약·환자·보호자, 범위 불일치, 정책 버전 불일치, 시작 전, 확정 만료와 철회를 모두 기본 거부한다. 보호자 예약 참여 관계는 판정 입력일 뿐 허용 근거가 아니다. 보호자의 신규 예약 생성은 환자 프로필 확인 전에 403으로 차단하며, 환자 본인이 예약을 만든 뒤 공유 동의를 요청해야 한다.

정보공유 동의는 예약 업무 대리 권한이 아니다. 별도 대리권 정책이 없는 MVP에서는 보호자의 예약 생성·수정·취소와 후기·정산·긴급 지원 기록 저장을 모두 요청 초입에서 403으로 차단한다. 보호자 예약 응답은 일정·병원·상태만 제공하고 참여자 UUID, 연락처, 건강·복약 정보, 구체 만남 장소, 매니저 식별자, 결제·쿠폰·승인 정보는 제공하지 않는다. `REPORT` 범위도 후속 기록 읽기만 허용한다.

## Android

환자의 예약 상태 화면에서 정보 공유 설정으로 이동한다. 화면은 예약·채팅·첨부·리포트 범위를 각각 선택하고 임시 또는 확정 만료 상태를 보여 준다. 채팅을 해제하면 첨부도 해제·비활성화하고, 위치 기능이 꺼져 있으면 위치 선택을 비활성화한다. 저장할 때 만 19세 이상이며 의사결정 능력이 있는 환자 본인이라는 확인을 다시 요구한다. 활성 동의는 같은 화면에서 즉시 철회할 수 있다. 보호자 신규 예약 화면은 환자 본인이 먼저 예약을 생성해야 한다고 안내하며, 예약 카드의 수정·취소 액션과 후속 기록 저장 액션은 표시하지 않는다. 보호자 후속 화면은 `REPORT` 범위의 읽기 전용이다.

Mock 모드도 같은 성인 확인과 범위·철회 계약을 따르지만 실제 인가 증거로 사용하지 않는다.

## Firebase 최소권한 대조

예약·세션·리포트·후속 처리의 운영 원본은 PostgreSQL이다. 전환 비교용 Firestore 문서는 환자·배정 매니저·관리자만 읽고, 관계만 있는 보호자는 읽지 못한다. `companion-chat-attachments` Storage 경로도 보호자 직접 접근을 거부한다. 보호자 앱은 Core API가 `CHAT`과 `ATTACHMENT`를 함께 판정한 응답만 사용한다.

Firebase의 사용자 본인 프로필과 본인 지원 문의 규칙은 기존 최소권한을 유지한다. 이 데이터는 예약 동의 범위에 포함하지 않으며 다른 사용자의 프로필·지원 본문을 보호자 관계로 공개하지 않는다.

Firebase Admin SDK는 Firestore·Storage Rules를 우회한다. 따라서 PostgreSQL 동의를 안전하게 확인할 전용 최소권한 경계가 없는 기존 Functions 채팅·위치·예약 리마인더는 보호자를 발송 대상에서 제외한다. 환자·매니저 발송만 유지하며, 보호자 알림은 Core API의 동의 판정 뒤 발송하는 후속 dispatcher가 생기기 전까지 fail-closed 상태다.

## 적용 순서

1. 개발 DB 백업과 복원 가능 증적을 확인한다.
2. Flyway V17을 migration role로 적용한다.
3. `db/verification/012_guardian_sharing_consent_checks.sql`을 읽기 전용으로 실행한다.
4. postgres 권한으로 `db/bootstrap/005_guardian_sharing_realtime_authorization.sql`을 적용한다.
5. `db/verification/004_companion_realtime_authorization_scenarios.sql`로 동의 여부와 무관하게 보호자 Broadcast가 거부되고 환자·매니저만 허용되는지 확인한다.
6. Core API와 Android를 배포하고 미동의·범위별·만료·철회 회귀를 확인한다.
7. Firestore·Storage Rules를 배포하고 Rules emulator와 실제 보호자 계정 거부를 확인한다.

Rollback은 `db/bootstrap/rollback/005_guardian_sharing_realtime_authorization_rollback.sql`로 V17 테이블 의존성을 먼저 제거한다. 이 rollback도 보호자 Broadcast를 다시 열지 않는다. 이어 Core API·Android를 이전 버전으로 되돌린다. `db/rollback/V17__remove_guardian_sharing_consents.sql`은 동의·감사 행이 한 건이라도 있으면 중단하므로, [동의 데이터 보존 rollback 절차](../operations/postgres/guardian-sharing-consent-rollback.md)에 따라 export와 복원 검증, 삭제 승인을 먼저 완료해야 한다. 이 문서 작성 시점에는 개발·production DB 적용과 배포를 실행하지 않았다.

## 리스크

- 성인 확인은 현재 자기선언이다. 생년월일·본인확인 원본과 대조한 법적 연령 검증은 아니다.
- 의사결정 능력 제한, 법정대리인 증빙과 관리자 승인 경로는 구현하지 않았다. 해당 사용자는 MVP에서 동의를 만들 수 없게 안내하고 별도 요구가 확정될 때 새 작업으로 다룬다.
- 기존 활성 보호자도 V17 적용 뒤 새 동의가 없으면 즉시 조회가 막힌다. 배포 전에 테스트 계정과 사용자 안내가 필요하다.
- 보호자 화면은 15초 Core API polling을 사용하므로 환자·매니저 Broadcast보다 갱신이 늦을 수 있다. 장기 연결 중 철회 즉시성을 증명하는 별도 인가 체계가 생기기 전에는 보호자 Broadcast를 열지 않는다.
- 정책 버전을 바꾸면 기존 동의가 전부 fail-closed된다. 새 문구 고지와 재동의 전환 계획 없이 설정 값을 바꾸면 안 된다.

관련 이슈: [#296](https://github.com/bodeul110/Bodeul/issues/296), [#374](https://github.com/bodeul110/Bodeul/issues/374)
