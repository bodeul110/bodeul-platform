# BoDeul Core API

환자, 보호자, 매니저 웹과 Android 앱이 공통으로 사용하는 BoDeul Core API다. Java와 Spring Boot로 구현하고 Google Cloud Run에 배포하며, Supabase PostgreSQL을 운영 데이터 저장소로 사용한다.

## 현재 범위

- Spring Boot 3.5.16
- Java 21 LTS
- Gradle Wrapper
- 공개 `GET /health`
- Firebase ID token과 PostgreSQL `app_users.role`을 연결하는 `GET /api/auth/me`
- 삭제 실행 없이 본인 계정의 PostgreSQL 연관 건수와 Firestore 사용자·지원 문서 부분 점검 상태를 확인하는 `GET /api/account/deletion-readiness`
- 인증된 사용자의 병원·약국 검색을 대행하는 `GET /api/places/search`
- 환자 예약 생성·수정·취소, 보호자 동의 범위 내 최소 조회와 배정 매니저 조회를 처리하는 `/api/appointments`
- 성인 환자의 예약별 보호자 정보공유 동의·조회·철회를 처리하는 `/api/appointments/{id}/guardian-sharing-consent`
- 참여자·배정 매니저의 동행 조회와 매니저 진행·리포트를 처리하는 `/api/companion-sessions`
- 채팅 snapshot·메시지·읽음·첨부와 legacy 위치 경계, PostgreSQL 커밋 후 Realtime Broadcast
- 무통장입금 조회·입금자명 제출과 동행 증빙 업로드·조회·삭제
- 명시적으로 허용하지 않은 경로는 기본 차단
- `local` profile에서는 DB 없이 기동
- `preview`, `production` profile에서는 PostgreSQL 설정 필수

기존 `api/`의 Node `bodeul-api` prototype은 검증 후 제거했다. 현재 `core-api/`는 사용자 계약을 직접 처리하며 Node API나 관리자 서버를 중간 서버로 호출하지 않는다. 코드의 API·migration 존재와 배포 환경의 적용 여부는 구분한다.

## API 찾기

| 범위 | 경로 | 계약 |
| --- | --- | --- |
| 예약·후속 처리 | `/api/appointments`, `/{id}/follow-up` | [예약 계약](../docs/architecture/appointment-core-api.md) |
| 무통장입금 | `/api/appointments/{id}/payment`, `/payment/depositor` | 환자 본인 조회·입금자명 제출. 관리자 상태 전이는 별도 Next.js 서버 |
| 보호자 동의 | `/api/appointments/{id}/guardian-sharing-consent` | [동의 계약](../docs/architecture/adult-patient-guardian-sharing-consent.md) |
| 보호자 예약 승인 | `/api/appointments/guardian-booking` | [별도 생성 승인 API](../docs/architecture/guardian-booking-api.md). 기본 OFF, 환자 승인·보호자 최소 접수증 |
| 동행·종료·리포트 | `/api/companion-sessions`, `/{id}/advance`, `/care-end`, `/report` | [동행 계약](../docs/architecture/companion-session-core-api.md) |
| 채팅·읽음·첨부 | `/api/companion-sessions/{id}/realtime`, `/messages`, `/read-receipt`, `/attachments/{attachmentId}` | 서버 인가·저장, Realtime은 변경 신호만 전달 |
| 동행 증빙 | `/api/companion-sessions/{id}/artifacts` | PostgreSQL 메타데이터와 Firebase Storage 원본 분리 |
| 기존 매니저 위치 | `/api/companion-sessions/{id}/locations` | 기본 OFF, production 고정 OFF. 환자 GPS 1분 공유 구현과 별개 |

위 경로의 축약된 접미사는 같은 예약 또는 세션 base path를 사용한다. 정확한 HTTP 메서드는 Controller와 각 계약 문서를 따른다. Flyway 소스는 [V1~V25 목록](../docs/architecture/database-migration-catalog.md)에서 확인한다. V24 보호자 예약 승인 저장과 V25 감사 조회 인덱스는 실제 DB 미적용이며 예약 API의 보호자 생성 차단을 바꾸지 않는다.

Android, Firebase 도구, 공통 데이터 계약과 함께 변경 내용을 검토하기 위해 메인 저장소 안에서 관리한다. 배포는 저장소 구조와 별개로 Cloud Run 서비스와 `core-api-preview` 또는 `core-api-production` GitHub Environment를 사용한다.

개발 배포는 `dev`, 운영 배포는 `master`로 분리한다. 개발은 CI 통과 후 push 배포와 수동 배포를 지원하고, 운영은 기존 SHA·서비스명 확인과 승인을 유지한다. DB migration은 자동 배포에 포함하지 않는다. [환경 분리 기준과 실행 기록](../docs/operations/dev-production-branch-transition-plan.md)을 함께 확인한다.

## 로컬 검증

```powershell
.\gradlew.bat check --console=plain
.\gradlew.bat bootRun --console=plain
```

기본 profile은 `local`이며 DB를 초기화하지 않는다.

```powershell
curl.exe http://127.0.0.1:8080/health
```

컨테이너는 Java 21 build stage와 비루트 distroless runtime으로 구성한다.

```powershell
docker build --tag bodeul-core-api:local .
docker run --rm --publish 8080:8080 bodeul-core-api:local
```

## DB profile

`preview`와 `production`은 `database` profile을 포함한다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "preview"
$env:CORE_DB_JDBC_URL = "jdbc:postgresql://<host>:5432/postgres?sslmode=require"
$env:CORE_DB_USERNAME = "<runtime-role>"
$env:CORE_DB_PASSWORD = "<runtime-password>"
.\gradlew.bat bootRun --console=plain
```

실제 값은 로컬 비공개 설정 또는 Google Secret Manager로만 주입한다. `.env`와 접속 문자열을 커밋하지 않는다.

## 인증과 인가

클라이언트는 `Authorization: Bearer <Firebase ID token>`을 전달한다. Core API는 Firebase Admin SDK `verifyIdToken`으로 서명, 만료, 발급 project를 확인하고 검증된 `uid`만 `bodeul.app_users.firebase_uid` 조회에 사용한다. Firebase custom claim은 서비스 역할의 최종 근거로 사용하지 않는다.

정상 인증은 `GET /api/auth/me`에서 내부 사용자 ID와 PostgreSQL 역할만 반환한다. 원본 ID token은 응답, 로그, Spring Security credentials에 보관하지 않는다.

| 상태 | HTTP | 오류 코드 |
| --- | ---: | --- |
| Authorization 누락 또는 잘못된 형식 | 401 | `missing_authorization`, `invalid_authorization` |
| 만료, 변조, 다른 Firebase project token | 401 | `invalid_firebase_token` |
| `app_users` 역할 미등록 | 403 | `role_not_found` |
| 인증됐지만 endpoint 권한 부족 | 403 | `permission_denied` |
| Firebase 또는 DB 설정 누락 | 503 | `auth_not_configured`, `authorization_not_configured` |
| PostgreSQL 역할 조회 장애 | 503 | `role_lookup_failed` |

Cloud Run에서는 전용 runtime 서비스 계정의 Application Default Credentials를 사용한다. 서비스 계정 JSON과 `GOOGLE_APPLICATION_CREDENTIALS` 파일을 만들지 않으며, `FIREBASE_PROJECT_ID`를 명시해 다른 project token을 거부한다.

첫 범위는 Firebase Admin SDK의 기본 `verifyIdToken`을 사용하므로 token 폐기 여부를 추가 조회하지 않는다. ID token 만료 전 즉시 차단이 필요하면 PostgreSQL 역할을 제거하고, 계정 폐기 확인을 매 요청에 적용할지는 네트워크 비용과 캐시 전략을 정한 뒤 별도 반영한다.

기본 OFF인 보호자 예약 승인 API는 예외적으로 두 참여자의 현재 PostgreSQL 역할과 Firebase 사용자 존재·disabled 상태를 서버에서 추가 확인한다. 이 확인이 다른 기존 API 전체의 token 폐기 검사까지 변경하는 것은 아니다.

## 계정 삭제 영향도 점검

`GET /api/account/deletion-readiness`는 인증된 principal의 내부 UUID와 Firebase UID만 사용해 PostgreSQL 연관 데이터 건수와 Firestore 부분 집계를 조회한다. Firestore는 `users/{firebaseUid}` 문서 한 건을 정확 조회하고, 지원 문의와 예약·동행 세션은 인증 UID가 들어 있는 허용 필드별 aggregation count만 실행해 문서 ID와 원문을 읽지 않는다. 예약 요청자와 예약·세션 참여 역할은 겹칠 수 있으므로 고유 문서 합계를 만들지 않고 필드별 직접 참조 건수만 반환한다. 요청에서 사용자 ID를 받지 않으며 응답에는 Firebase UID, token, metadata key, 레코드 ID, 이름, 연락처, 본문, 좌표, 파일명과 Storage 경로를 포함하지 않는다. 응답은 캐시하지 않는다.

이 API는 삭제 가능 여부를 승인하거나 데이터를 삭제하지 않는다. `readOnly=true`, `deletionExecuted=false`, `decision=NOT_EVALUATED`, `complete=false`가 현재 고정 계약이다. 현재 필드가 없는 legacy 문서와 요청·세션 ID로만 이어지는 간접 문서는 집계되지 않는다. 따라서 Firestore 조회가 성공해도 `PARTIAL`이며, Storage, Firebase Auth와 백업도 미점검으로 남는다. 실제 탈퇴 기능으로 사용하면 안 되며 자세한 구현 경계는 [계정 탈퇴·삭제 준비 상태](../docs/operations/account-deletion-readiness.md)를 따른다.

## Kakao Local 장소 검색

`GET /api/places/search`는 `query`와 `category=HOSPITAL|PHARMACY`를 받고 Kakao Local 결과 중 이름과 좌표만 반환한다. Firebase 인증과 PostgreSQL 역할 확인을 통과해야 하며, 사용자별 분당 60회 제한과 6시간·최대 1,000건 서버 캐시를 적용한다.

로컬 또는 배포 환경에는 다음 값을 비공개 경로로 주입한다.

```powershell
$env:KAKAO_LOCAL_REST_API_KEY = "<Kakao REST API key>"
```

Cloud Run preview에서는 `bodeul-core-api-preview-kakao-local-rest-api-key`, production에서는 `bodeul-core-api-production-kakao-local-rest-api-key` Secret Manager secret을 사용한다. 현재 키 자체는 사용자 승인에 따라 공유하므로 쿼터와 폐기 영향도 공유한다. DB 자격 증명은 환경별로 분리한다. 키 값과 Kakao 원본 오류 본문은 응답이나 로그에 남기지 않는다. 자세한 계약과 확장 조건은 [Kakao Local Core API 경계](../docs/architecture/kakao-local-core-api.md)를 따른다.

## 예약 API

`/api/appointments`는 PostgreSQL UUID로 예약을 식별한다. 환자는 본인 예약을, 배정 매니저는 본인 배정 예약을 조회한다. 보호자는 환자가 해당 예약에 부여한 `APPOINTMENT` 동의가 있어야 목록·상세를 사용할 수 있고, 응답도 일정·병원·상태만 남긴 최소 형태다. 정보공유 동의는 업무 대리 권한이 아니므로 보호자의 예약 생성·수정·취소는 프로필이나 예약을 조회하기 전에 403으로 차단한다. 생성은 `clientRequestId`로 중복을 막고 환자 수정·취소는 응답의 `version`을 다시 보내야 한다. 가격과 최초 결제 상태는 서버가 계산하며 클라이언트 가격·승인값을 받지 않는다.

`GET /api/appointments/{id}/follow-up`은 환자와 배정 매니저에게 후기·정산 기록을 제공한다. 보호자는 별도 `REPORT` 동의가 있어야 읽을 수 있다. 과거 응답 호환을 위해 `supportEscalationStatus`와 저장 시각은 읽기 모델에 남지만 현재 MVP 기능이 아니다. `PATCH /api/appointments/{id}/follow-up`은 완료 예약의 환자만 사용할 수 있고 최신 후속 기록의 `version`과 후기·정산 변경 필드만 받는다. 값이 비어 있지 않은 신규 `supportEscalationStatus` 요청은 `409 support_escalation_not_supported`로 거부하며, 후기·정산 갱신은 기존 legacy 값을 덮어쓰지 않는다. 보호자 쓰기는 별도 대리권 정책이 없는 MVP에서 403으로 차단한다.

V4 migration은 `app_users`의 최소 프로필 컬럼과 Core runtime의 예약 INSERT·UPDATE 권한을 추가한다. V5 migration은 동행 세션·리포트·후속 처리와 관리자 배정 함수를 추가한다. V6는 Core runtime에 세션 진행 컬럼 UPDATE와 리포트 지정 컬럼 INSERT·UPDATE만 허용하고, V7은 후속 처리 지정 컬럼 INSERT·UPDATE만 허용한다. V8은 채팅·첨부 메타데이터·읽음 위치·최근 위치 이력을 정규화하고 Core runtime의 최소 DML, 위치 기록 함수와 종료 시 보관 만료 예약을 추가한다. V9은 읽음 위치의 복합 외래키를 덮는 인덱스를 추가한다. V14는 배정 시점의 병원 가이드 ID·revision·단계 배열을 세션에 고정하고 이후 변경을 막는다. V17은 예약별 보호자 동의 현재 상태, 추가 전용 감사 이벤트와 정책 설정을 추가한다. 자세한 계약은 [예약 Core API 전환 계약](../docs/architecture/appointment-core-api.md), [매칭·동행·리포트 PostgreSQL 전환 계약](../docs/architecture/companion-session-core-api.md), [성인 환자·보호자 정보공유 동의 계약](../docs/architecture/adult-patient-guardian-sharing-consent.md)을 따른다.

## 보호자 정보공유 동의

해당 예약의 환자만 `PUT`과 `DELETE /api/appointments/{id}/guardian-sharing-consent`를 사용할 수 있다. `PUT`은 `adultPatientConfirmed=true`와 `APPOINTMENT`, `CHAT`, `ATTACHMENT`, `REPORT`, `LOCATION` 중 하나 이상의 범위를 받는다. 지정 보호자는 `GET`으로 현재 동의 상태를 확인할 수 있지만 관계만으로 업무 데이터 열람권을 얻지는 않는다.

현재 정책 버전과 위치 기능 플래그는 `guardian_sharing_consent_settings`가 단일 원본이다. 저장된 정책 버전이 다르거나 동의가 없고, 확정 만료·철회됐거나 요청 범위가 없으면 기본 거부한다. `ATTACHMENT`는 `CHAT` 없이 선택할 수 없고, `LOCATION`은 DB 플래그 기본값이 `false`라 동의 생성과 위치 공개가 모두 차단된다. 진행 중 동의는 임시 만료 상태로 두고 실제 예약 취소 또는 동행 완료 시각에서 7일 뒤로 만료를 확정한다.

성인 확인은 현재 프로필에 생년 원본이 없어 환자의 자기선언 시각을 저장하는 MVP 경계다. 의사결정 능력 제한과 법정대리인 흐름은 구현하지 않는다.

## 동행 세션 API

`/api/companion-sessions`는 환자에게 본인 세션을, 보호자에게 `APPOINTMENT` 동의가 있는 세션을 읽기 전용으로 제공하고, 배정된 매니저에게만 현장 메모·단계·리포트 쓰기를 허용한다. 보호자 응답은 `CHAT`, `ATTACHMENT`, `REPORT`, `LOCATION` 범위별로 필드를 숨기며 알림 대상도 같은 범위를 확인한다. 모든 쓰기는 응답의 `version`을 요구한다. 단계 목록과 진행 한계는 V14가 세션 생성 시 고정한 `guide_steps_snapshot`에서 계산하며 이후 병원 가이드 수정의 영향을 받지 않는다. 응답은 기존 필드와 함께 `guideId`, `guideRevision`, 상세 `steps`, `currentStepCode`, `canAdvance`, `blockedReason`을 제공한다.

V16의 진료 전 확인값 저장은 롤링 배포 호환을 위해 서버 진행 차단과 분리한다. `BODEUL_SESSION_PRE_CONSULTATION_ENFORCEMENT`의 기본값은 `false`이며, 이 상태에서는 구버전 앱도 기존처럼 진행할 수 있다. V16 migration과 Core API를 먼저 배포하고 새 Android의 저장·재진입을 검증한 뒤 별도 승인을 받아 `true`로 바꾼다. 설정을 켜면 서비스의 `STEP_INPUT_REQUIRED` 판정과 repository의 동시 진행 방지 SQL 조건이 함께 적용된다.

V19는 예약에 `BD-` + 영문 대문자·숫자 6자리의 `publicCode`를 추가한다. Core API가 생성 시 발급하고 DB unique 충돌이면 최대 5회 재시도한다. 이 값은 신청자와 배정 매니저의 기존 목록·상세 응답에만 포함되는 표시용 코드이며, 연결 참여자가 신청자가 아니면 응답에서 빈 값으로 가린다. 내부 UUID 또는 인가 관계를 대체하지 않으며, 관리자 정확 검색과 감사는 별도 관리자 웹 서버가 `bodeul.search_appointment_by_public_code` 함수로 처리한다.

리포트 저장은 예약과 세션을 함께 `COMPLETED`로 바꾸며, 매칭된 예약 취소는 활성 세션을 함께 `CANCELED`로 바꾼다. 어느 한쪽 갱신이라도 실패하면 Spring transaction 전체를 rollback한다.

## 연결 원칙

- Cloud Run은 IPv4가 가능한 Supabase Supavisor session mode의 5432 포트를 우선 사용한다.
- Vercel 관리자 서버는 Supavisor transaction mode의 6543 포트를 사용한다.
- migration 계정과 runtime 계정을 분리한다.
- application pool의 로컬 기본값은 최대 5개이며 운영 Cloud Run은 인스턴스당 2개·최대 인스턴스 2개로 제한한다. 실제 배포 설정은 인프라 런북을 따른다.
- Firebase ID token 검증 후 PostgreSQL role과 리소스 소유권을 확인한다.
- Kakao Local REST와 알림톡처럼 서버 key가 필요한 연동은 이 API 뒤에 둔다.

## DB 권한 bootstrap

`db/bootstrap/001_database_access.sql`은 다음 기반만 만든다.

- Data API에 노출하지 않는 `bodeul` schema
- `bodeul_migration`, `bodeul_core_runtime`, `bodeul_admin_runtime` 권한 role
- 비밀번호 설정 전까지 접속할 수 없는 `bodeul_migrator`, `bodeul_core_service`, `bodeul_admin_service` role
- runtime role의 DDL 차단과 최대 연결 수 제한
- `public` schema 신규 객체의 Data API 자동 노출 차단

bootstrap은 개발 DB에서 `postgres` 권한으로 먼저 적용한다. 각 bootstrap 파일은 명시적 트랜잭션으로 권한 role 전환과 default privilege 변경을 묶으며, 오류 시 전체 파일을 rollback한다. 비밀번호는 SQL 파일에 추가하지 않고 보안 경로에서 별도로 설정한 뒤 각 로그인 role을 활성화한다.

V17 적용 뒤에는 `db/verification/012_guardian_sharing_consent_checks.sql`을 읽기 전용으로 실행하고, postgres 권한으로 `db/bootstrap/005_guardian_sharing_realtime_authorization.sql`을 적용한다. 이어 `db/verification/004_companion_realtime_authorization_scenarios.sql`로 보호자가 동의 여부와 무관하게 Broadcast에서 거부되고 환자·매니저만 허용되는지 확인한다. 보호자는 매 요청 동의를 판정하는 Core API polling만 사용한다.

V18 적용 뒤에는 postgres 권한으로 `db/bootstrap/006_companion_completion_realtime_authorization.sql`을 적용하고 `db/verification/015_companion_completion_realtime_authorization_scenarios.sql`을 실행한다. 진행 중 배정 매니저만 Broadcast를 구독하고 `care_ended_at` 이후 신규·재인가 구독은 거부되어야 한다. V18 schema rollback이 성공한 뒤에만 `db/bootstrap/rollback/006_companion_completion_realtime_authorization_rollback.sql`로 V17 권한식을 복원한다.

Flyway는 runtime profile에서 실행하지 않는다. migration 전용 자격 증명을 준비한 환경에서만 다음처럼 실행한다. migration profile은 연결 직후 `SET ROLE bodeul_migration`을 실행해 history와 업무 객체의 소유자를 로그인 계정이 아닌 migration 권한 role로 통일한다.

```powershell
$env:SPRING_PROFILES_ACTIVE = "migration"
$env:MIGRATION_DB_JDBC_URL = "jdbc:postgresql://<host>:5432/postgres?sslmode=require"
$env:MIGRATION_DB_USERNAME = "bodeul_migrator"
$env:MIGRATION_DB_PASSWORD = "<migration-password>"
.\gradlew.bat migrateDatabase --console=plain
```

runtime 서버에는 `MIGRATION_DB_*` 값을 주입하지 않는다.
GitHub에서는 `Core API DB Migration` workflow를 수동 실행하고 대상 Environment의 승인을 거친다.

V15 이후 migration workflow는 Flyway 적용 뒤 `verifyAccountDeletionInventory`를 실행한다. 이 task는 합성 UUID만 사용해 함수 반환 열과 0건 결과를 확인하고 실제 Core/Admin 서비스 역할의 스키마·함수 권한, 공개 역할 차단과 Core 서비스의 배정 감사 원문 조회 거부를 읽기 전용 트랜잭션에서 검증한다.

개발 DB의 동행 세션 백필은 `applyCompanionSessionSeed` task를 사용한다. 이 실행기는 `companion_sessions`, `session_reports`, `appointment_follow_ups`의 순서가 맞는 제한된 upsert만 허용하며 DDL, DELETE, 다른 schema 참조를 거부한다. GitHub workflow에서는 생성 SQL을 일회성 Environment secret으로 전달하고 입력 SHA-256이 일치할 때만 실행한 뒤 임시 파일과 secret을 삭제한다.

## 다음 작업

채팅·읽음·legacy 위치 endpoint, private Broadcast와 Android 저장소 전환 코드는 이미 반영됐다. 다음 작업은 다음과 같이 구분한다.

1. 개발·운영 분리 기반과 V23 적용은 [9월 27일 기록](../docs/reports/dev-production-separation-2026-09-27.md)에서 확인한다. #429는 종료됐으며 정상 운영 token·환경 교차 거부·Kakao·Realtime 실제 구독 검증은 남아 있다.
2. [#419](https://github.com/bodeul110/bodeul-platform/issues/419): 보호자 예약 생성. 현재 정보공유 동의만으로 예약 쓰기를 허용하지 않는다.
3. [#420](https://github.com/bodeul110/bodeul-platform/issues/420): 비식별 테스트 데이터로 Naver Cloud STT 내부 연동. OCR·AI 리포트 자동 생성은 제외한다.
4. [#222](https://github.com/bodeul110/bodeul-platform/issues/222), [#348](https://github.com/bodeul110/bodeul-platform/issues/348): production 파기 검증, 탈퇴·법정 보존 분리. 실제 apply는 별도 승인 경계다.

## 자동 파기 DB 권한

`db/bootstrap/004_retention_runtime.sql`은 예약 파기 작업을 Core API와 관리자 서버 자격 증명에서 분리한다. `bodeul_retention_service`는 환경별 비밀번호를 설정하기 전까지 `NOLOGIN`으로 유지하고, 로그인 후에도 `bodeul_retention_runtime`에 공개된 V13 함수만 실행한다. 테이블 직접 DML 권한은 부여하지 않는다.

V13은 채팅 본문 비식별화, 정밀 위치 삭제, Storage 첨부 삭제 claim·완료, 비식별 실행 이력과 월간 집계를 제공한다. Storage 삭제는 예약 함수가 수행하며, 실패한 첨부는 `DELETE_PENDING`으로 남겨 다음 실행에서 재시도한다. 자세한 실행 순서는 [#222 개인정보 자동 파기 구현 기록](../docs/reports/issue-222-data-retention-2026-07-19.md)을 따른다.

## 보안

취약점은 공개 이슈에 실제 공격 정보나 secret을 적지 말고 메인 저장소의 private vulnerability reporting 경로로 제보한다.
