# Spring Core API Cloud Run 인프라 런북

기준일: 2026-09-27

이 문서는 `core-api/`를 Google Cloud Run에 배포하고 Supabase PostgreSQL, Firebase Auth, Kakao 서버 API를 연결하는 개발 환경 기준을 정한다. 실제 secret 값은 저장소와 공개 GitHub 대화에 남기지 않는다.

## 결정

- 관리자 브라우저는 Vercel의 Next.js 관리자 서버를 사용한다.
- 환자·보호자·매니저 웹과 Android 앱은 Spring Core API를 사용한다.
- 두 서버는 서로를 경유하지 않고 같은 Supabase PostgreSQL에 서로 다른 runtime role로 접근한다.
- OCI Free Tier 계정 잠금으로 중단된 Spring preview는 Cloud Run으로 교체한다.
- Cloudflare는 도메인이 생긴 뒤 DNS, WAF, DDoS 방어 계층으로 검토하며 Spring 실행 환경으로 사용하지 않는다.
- Kakao가 호출 허용 IP를 필수로 요구하지 않는 동안 Cloud Run 기본 동적 outbound를 유지하고 VPC·Cloud NAT를 추가하지 않는다.

Cloud Run은 현재 Spring 애플리케이션을 컨테이너로 유지하고, 요청이 없을 때 인스턴스를 0으로 줄일 수 있다. Firebase와 같은 Google Cloud 프로젝트의 서비스 계정 ADC를 사용할 수 있어 장기 서비스 계정 JSON 파일도 필요하지 않다. 단점은 첫 요청의 cold start와 결제 계정 등록이 필요하다는 점이다.

## 현재 코드와 과거 검증 기록

소스와 양쪽 DB는 9월 27일 V23·실패 이력 0건으로 대조했다. 운영 Core `bodeul-core-api-00002-2s6` 배포와 health 200·무인증 401, 전용 DB·Kakao Secret 참조를 확인했다. 정상 Firebase 인증·Kakao 응답과 교차 환경 거부는 별도다. 아래 초기 전환 run·revision은 당시 증거이며 최신 결과는 [실행 기록](../reports/dev-production-separation-2026-09-27.md)을 따른다.

- Java 21, Spring Boot 3.5.16, `/health`, `preview` DB profile이 구현돼 있다.
- Firebase ID token 검증과 PostgreSQL `app_users.role` 인가가 구현돼 있다.
- V1~V12와 세션 백필·RLS는 초기 개발 전환 검증 기록이다. 이후 관리자 RBAC·영상 메타데이터·결제 계약이 V20~V23으로 추가됐다.
- 세션 진행·리포트용 V6 최소 컬럼 쓰기 권한은 개발 DB run `29639792606`에서 검증했다. Core API Preview 배포, 무인증 경계와 실제 token 역할 검증을 완료했다.
- 예약 후속 처리용 V7 최소 컬럼 쓰기 권한과 Core API·Android 연결을 완료했다. 개발 DB migration run `29642658596`, Cloud Run Preview run `29642778613`, SM-S921N 실기기 후기·정산·긴급 지원 저장을 검증했다.
- `core-api/Dockerfile`과 `Core API Preview Deploy` workflow를 배포 기준으로 사용한다.
- Cloud Run `bodeul-core-api-preview` 배포, 외부 smoke test와 revision rollback 리허설이 완료됐다.
- 실제 Firebase ID token과 PostgreSQL role 연결은 Issue #157에서 검증했다.
- Kakao Local REST Secret 버전 `1`과 인증된 장소 검색 실호출은 Issue #158 검증 기록에서 확인했다.
- Android App Check header 전달과 Spring `off/observe/enforce` 검증을 구현했다. preview는 Android 실기기 `valid`를 확인했지만 release Play Integrity와 rollback 검증 전까지 `observe`로 운용한다.
- 채팅·읽음·위치 Core API와 Supabase private Realtime 전환을 배포했다. 당시 리비전 `bodeul-core-api-preview-00014-wnr`에서 실제 세션, FCM 실기기 알림과 10개 동시 연결을 검증했다.
- production Google Cloud/Firebase `bodeul-prod-110`과 Supabase `bodeul-db-prod`를 사용한다. 9월 27일 운영 V23 적용·격리 복원과 첫 Core 배포를 완료했다. 기존 Kakao REST 키를 승인에 따라 운영 전용 Secret에 등록했으며 키 자체의 쿼터·폐기 영향은 개발과 공유한다.

실제 revision, image digest, 응답과 로그 검사 결과는 [Issue 156 Cloud Run preview 검증 기록](../reports/issue-156-core-api-cloud-run-preview-2026-07-16.md)에 정리한다.

## 리소스 이름

| 항목 | 개발 기준 |
| --- | --- |
| Google Cloud/Firebase project | `bodeul-dev` |
| 리전 | `asia-northeast1` (Tokyo) |
| Cloud Run 서비스 | `bodeul-core-api-preview` |
| Artifact Registry | `bodeul-core-api` |
| 컨테이너 이미지 | `bodeul-core-api` |
| 배포 서비스 계정 | `bodeul-core-preview-deployer` |
| 런타임 서비스 계정 | `bodeul-core-preview-runtime` |
| GitHub Environment | `core-api-preview` |
| WIF pool/provider | 기존 pool `github-actions`, 전용 provider `bodeul-core-api-preview` |

production은 다음 식별자를 사용한다.

| 항목 | production 기준 |
| --- | --- |
| Google Cloud/Firebase project | `bodeul-prod-110` (`649312328770`) |
| Supabase project | `bodeul-db-prod` (`aoijbzgozbopsxzrasbb`, 표시 이름만 변경) |
| 리전 | `asia-northeast1` / `ap-northeast-1` (Tokyo) |
| Cloud Run 서비스 | `bodeul-core-api` |
| Artifact Registry | `bodeul-core-api` |
| 배포/런타임 서비스 계정 | `bodeul-core-deployer` / `bodeul-core-runtime` |
| WIF pool/provider | `github-actions` / `bodeul-core-api-production` |

개발과 production Supabase DB가 모두 Tokyo이므로 Core API도 `asia-northeast1`로 고정한다.

## 런타임 기준

| 항목 | 값 | 이유 |
| --- | --- | --- |
| CPU | 1 vCPU | 단일 Spring API 초기 기준 |
| Memory | 1 GiB | Spring, Firebase Admin, JDBC의 512 MiB OOM 위험 완화 |
| 최소 인스턴스 | 0 | 개발 환경 유휴 비용 제한 |
| 최대 인스턴스 | 1 | 비용과 DB 연결 수 상한 고정 |
| Concurrency | 8 | 최대 30 MiB multipart 요청과 1 GiB 메모리 한도를 함께 고려 |
| DB pool | 최대 5 | Admin, migration, Supabase 관리 연결 여유 확보 |
| Request timeout | 60초 | 모바일 첨부 업로드 허용. 외부 API 호출은 애플리케이션 내부 timeout으로 제한 |
| Port | Cloud Run `PORT`, 기본 8080 | 플랫폼 계약 준수 |
| 실행 사용자 | distroless `nonroot` | 컨테이너 root 실행 방지 |
| Kakao outbound | 기본 동적 IP | 호출 허용 IP가 선택 사항인 MVP에서 NAT 상시 비용과 운영 대상을 추가하지 않음 |

컨테이너 파일 시스템은 영속 저장소로 사용하지 않는다. 파일 원본은 Firebase Storage에, 운영 데이터는 PostgreSQL에 둔다.

## Supabase 연결

Cloud Run에서 사용하는 값은 다음 세 개다.

- `CORE_DB_JDBC_URL`
- `CORE_DB_USERNAME`
- `CORE_DB_PASSWORD`

Cloud Run의 외부 PostgreSQL 연결은 IPv4가 가능한 Supavisor session mode 5432를 우선 사용한다. `bodeul_core_service` 로그인과 `bodeul_core_runtime` 권한 경계를 유지하고, migration 계정은 런타임에 주입하지 않는다.

DB role은 다음처럼 분리한다.

| role | 용도 | 연결 상한 |
| --- | --- | ---: |
| `bodeul_migration` / `bodeul_migrator` | Flyway와 schema 변경 | 2 |
| `bodeul_core_runtime` / `bodeul_core_service` | 사용자 서비스 | 5 |
| `bodeul_admin_runtime` / `bodeul_admin_service` | Next.js 관리자 서버 | 5 |

관리자 배정은 별도 `bodeul-admin-web` 저장소의 `POST /admin/companion-assignments`에서 처리한다. Firebase ID token과 PostgreSQL `ADMIN` 역할을 확인한 뒤 `bodeul_admin_runtime`이 `assign_companion_session` 함수만 실행하며 테이블 직접 쓰기 권한은 갖지 않는다. 2026-07-18 Vercel Preview에서 거부 경계와 성공 배정, 감사 기록, 임시 데이터 정리까지 확인했다. production은 V5~V7 migration과 복원 지점 승인이 끝나기 전 이 route와 후속 처리 API를 운영에 사용하지 않는다.

## Firebase 인증과 App Check

1. 클라이언트가 Firebase Auth로 로그인한다.
2. Firebase ID token을 `Authorization: Bearer`로 Core API에 보낸다.
3. Cloud Run runtime 서비스 계정의 ADC로 Firebase Admin SDK를 초기화한다.
4. 검증된 Firebase UID를 `bodeul.app_users.firebase_uid`와 연결한다.
5. PostgreSQL role과 resource ownership으로 최종 권한을 판정한다.

Cloud Run은 Firebase project와 같은 `bodeul-dev`에서 실행한다. 서비스 계정 JSON, `GOOGLE_APPLICATION_CREDENTIALS`, Firebase Admin private key를 만들거나 배포하지 않는다. `FIREBASE_PROJECT_ID=bodeul-dev`를 명시해 다른 project token을 거부한다.

App Check는 Firebase Auth와 PostgreSQL role 인가를 대체하지 않는 별도 앱 무결성 신호다.

1. Android가 App Check token을 발급받아 `X-Firebase-AppCheck` 헤더로 보낸다.
2. Core API는 Firebase App Check JWKS로 RS256 서명을 검증한다.
3. `FIREBASE_PROJECT_NUMBER`로 issuer와 audience를 고정하고 `typ=JWT`, 만료, app ID를 확인한다.
4. `off`는 검증하지 않고, `observe`는 판정만 기록하며, `enforce`는 누락·위조 요청을 거부한다.

Cloud Run preview에는 `BODEUL_APP_CHECK_MODE=observe`를 고정한다. Android debug 실기기의 `valid`는 확인했지만, `enforce`는 release Play Integrity와 Issue #192 롤백까지 재현한 뒤 적용한다. Java Admin SDK 9.10.0은 App Check 검증 API를 제공하지 않으므로 Spring Security의 JWT/JWKS 검증기를 사용한다.

2026-07-17 [Core API Preview Deploy #29518038972](https://github.com/bodeul110/Bodeul/actions/runs/29518038972)로 commit `000afc350fa3654cb97c9d23a539e45322322e95`를 배포했다. `asia-northeast1`의 리비전 `bodeul-core-api-preview-00007-8hk`가 트래픽 100%를 처리하며 `FIREBASE_PROJECT_ID=bodeul-dev`, `FIREBASE_PROJECT_NUMBER=533563500316`, `BODEUL_APP_CHECK_MODE=observe`를 사용한다. health 200과 무인증 auth/place search 401 smoke test는 통과했다. 같은 날 ARM debug 실기기에서 인증된 장소 검색 3건이 모두 `app_check_verdict=valid`와 HTTP 200으로 기록됐다. 상세 증적은 [Issue 190 ARM 실기기 검증 기록](../reports/issue-190-arm-device-validation-2026-07-17.md)에 남겼다.

2026-07-18 [Core API Preview Deploy #29639915209](https://github.com/bodeul110/Bodeul/actions/runs/29639915209)로 commit `9d08c1be5c84c28c6c34094e0b5c5511ce02de46`을 배포했다. 리비전 `bodeul-core-api-preview-00010-pd9`가 트래픽 100%를 처리하며 `/health`는 200 `UP`, `/api/companion-sessions`는 무인증 요청에 401 `missing_authorization`을 반환했다. 개발 기준선 계정의 실제 Firebase ID token으로 환자·보호자·매니저 목록 200과 각 2건, 관리자 목록 403, 환자 수정 403, 매니저의 잘못된 version 수정 409를 확인했다. token 원문은 출력하거나 파일에 저장하지 않았다.

같은 리비전에서 SM-S921N Android debug 앱이 `/api/appointments`, 예약 상세, `/api/companion-sessions`를 호출했고 세 요청 모두 `app_check_verdict=valid`로 기록됐다. 계측 테스트가 앱 데이터를 제거하면 debug token도 재발급되므로 Firebase allowlist에 새 token을 비공개 등록해야 한다. 재등록 과정에서도 token 원문은 메모리에서만 처리하고 파일·Issue·PR·명령 출력에 남기지 않는다.

2026-07-18 [Core API Preview Deploy #29642778613](https://github.com/bodeul110/Bodeul/actions/runs/29642778613)로 commit `e2611cfdffbb6f69b63f4ea6460d41f2c63f7050`을 배포했다. 리비전 `bodeul-core-api-preview-00011-tp4`가 트래픽 100%를 처리하며 `/health` 200, 무인증 후속 처리 경로 401을 반환했다. SM-S921N의 실제 환자 계정으로 임시 완료 예약의 후기·정산·긴급 지원을 순서대로 저장해 GET·PATCH 7건이 모두 200, `app_check_verdict=valid`였고 DB version은 3, 세 actor는 모두 환자 계정과 일치했다. 검증 직후 임시 예약과 후속 기록을 삭제해 잔여 0건을 확인했다.

2026-07-18 [Core API Preview Deploy #29643728174](https://github.com/bodeul110/Bodeul/actions/runs/29643728174)로 commit `4edb9ea08aad69ff9e5d88c5cad798c16e05756e`을 배포했다. 리비전 `bodeul-core-api-preview-00012-tqv`가 트래픽 100%를 처리한다. Firestore `appointmentRequests`와 연결 `companionSessions`가 각각 0건인 임시 PostgreSQL 예약·배정을 만들어 SM-S921N에서 매니저 홈 `0/7`, 보호자 진행 현황, 환자 예약 상세를 확인했다. 임시 예약 상세 GET 3건과 역할별 예약·세션 목록 요청은 모두 200이고 App Check 판정은 전부 `valid`였다. 첫 환자 화면에서 매니저 모델 누락을 발견해 Core-only 상세 조합을 보완했으며 재설치 후 담당 매니저가 표시되는 것을 확인했다. 검증 뒤 임시 예약·세션·감사를 각각 1건 삭제하고 잔여 0건을 확인했다.

### FCM 런타임 권한과 실행 경계

채팅·위치 보조 알림을 위해 preview runtime 서비스 계정에는 다음 project role만 추가한다.

- `roles/datastore.viewer`: Firestore `users` 문서의 기기 token 조회
- `roles/firebasecloudmessaging.admin`: Firebase Admin SDK의 FCM 발송

Core-only 채팅 첨부를 위해서는 프로젝트 role을 추가하지 않고 Firebase 기본 버킷에만 다음 권한을 부여한다.

- preview `gs://bodeul-dev.firebasestorage.app`: `bodeul-core-preview-runtime@bodeul-dev.iam.gserviceaccount.com`에 `roles/storage.objectUser`
- production `gs://bodeul-prod-110.firebasestorage.app`: `bodeul-core-runtime@bodeul-prod-110.iam.gserviceaccount.com`에 `roles/storage.objectUser`

이 권한은 원본 생성·조회·보상 삭제에 필요하다. DB 백업 버킷과 프로젝트 전체에는 부여하지 않는다. 애플리케이션은 `FIREBASE_STORAGE_BUCKET`이 없으면 `${FIREBASE_PROJECT_ID}.firebasestorage.app`을 사용하며, 기본 이름과 다른 버킷을 쓸 때만 명시한다. Storage 구현은 버킷 메타데이터 조회 없이 객체 API를 직접 사용하므로 `storage.buckets.get`이나 `roles/storage.legacyBucketReader`를 추가하지 않는다.

2026-07-28 Preview deploy run `30364434679`로 commit `e30d87e75cb93da7a8223956ac6e682e227cd8dc`를 배포했다. 리비전 `bodeul-core-api-preview-00017-x9r`에서 SM-S921N 인증 첨부 메시지 POST 200, Storage 객체 생성과 첨부 GET 200을 확인했다. 같은 `clientMessageId`의 다른 첨부로 실제 DB 충돌 409를 유도한 뒤 충돌 객체만 삭제되고 기존 원본이 유지되는 것도 확인했다. 기존 버킷 단위 `roles/storage.objectUser` 외 IAM 권한은 추가하지 않았다.

Cloud Run은 [request-based billing](https://cloud.google.com/run/docs/configuring/billing-settings)을 사용하므로 요청 처리 중에만 CPU가 할당된다. [응답 뒤 background activity](https://cloud.google.com/run/docs/tips/general)는 실행을 보장할 수 없으므로 알림 listener는 PostgreSQL `AFTER_COMMIT` 이후 같은 요청 안에서 실행한다. token 조회는 `getAll`로 묶으며 Firestore와 FCM 대기를 각각 12초로 제한한다. 발송 실패는 이미 commit된 업무 쓰기를 rollback하지 않는다. 응답 지연 p95가 5초를 넘거나 재시도가 필요하면 Cloud Tasks 같은 durable queue로 옮긴다.

2026-07-19 Preview deploy run `29651623086`으로 commit `f509240bb45ce0d9f5202e61e4bc8b94520627a1`을 배포했다. 리비전 `bodeul-core-api-preview-00014-wnr`에서 채팅 API 200, FCM `successCount=1`, `failureCount=0`과 SM-S921N의 실제 채팅 알림 생성을 확인했다. CLI 통합 요청은 App Check가 `missing`이었으며 preview `observe` 검증일 뿐 release App Check 유효성 근거는 아니다. 상세 결과는 [Issue 221 Android Realtime 전환 4단계](../reports/issue-221-android-realtime-phase-4-2026-07-18.md)에 둔다.

## Secret Manager

preview에서 다음 secret ID를 사용한다.

| Secret Manager ID | Cloud Run 환경변수 |
| --- | --- |
| `bodeul-core-api-preview-db-jdbc-url` | `CORE_DB_JDBC_URL` |
| `bodeul-core-api-preview-db-username` | `CORE_DB_USERNAME` |
| `bodeul-core-api-preview-db-password` | `CORE_DB_PASSWORD` |
| `bodeul-core-api-preview-kakao-local-rest-api-key` | `KAKAO_LOCAL_REST_API_KEY` |

런타임 서비스 계정에 각 secret의 `roles/secretmanager.secretAccessor`만 부여한다. 배포 workflow와 애플리케이션 로그에는 secret 원문을 출력하지 않는다.

Cloud Run 환경변수는 `latest` 대신 숫자 version을 참조한다. 회전할 때 새 version을 등록하고 해당 GitHub Environment의 version 변수만 바꾼 뒤 재배포한다.

2026-07-16 실제 Firebase ID token과 PostgreSQL role 조회를 확인한 뒤 기존 GitHub Environment의 `CORE_DB_*` secret을 삭제했다. Secret Manager가 runtime source of truth이며 GitHub Environment에는 숫자 secret version 변수만 둔다.

같은 날 Kakao Local REST 키를 `bodeul-core-api-preview-kakao-local-rest-api-key` 버전 `1`로 등록하고 런타임 서비스 계정에 accessor 권한만 부여했다. Cloud Run 리비전 `bodeul-core-api-preview-00006-hdk`가 이 버전을 참조한다.

production DB 자격 증명은 개발과 분리한다. Kakao REST 키만 사용자 승인에 따라 같은 키를 별도 운영 Secret에 등록했다. 다음 ID를 사용한다.

| Secret Manager ID | Cloud Run 환경변수 |
| --- | --- |
| `bodeul-core-api-production-db-jdbc-url` | `CORE_DB_JDBC_URL` |
| `bodeul-core-api-production-db-username` | `CORE_DB_USERNAME` |
| `bodeul-core-api-production-db-password` | `CORE_DB_PASSWORD` |
| `bodeul-core-api-production-kakao-local-rest-api-key` | `KAKAO_LOCAL_REST_API_KEY` |

네 secret 모두 version을 등록했으며 9월 27일 운영 배포는 Kakao Secret version `1`을 사용한다. 회전은 `core-api/deploy/cloud-run/set-production-secrets.ps1 -ProjectId bodeul-prod-110`으로 수행한다. 이 스크립트는 `bodeul-dev`와 허용 목록 밖 secret을 거부하고 project ID 재입력을 요구한다.

## Google Cloud 최초 설정

결제 계정 연결과 Cloud Run API 사용 가능 여부는 Google Cloud Console에서 먼저 확인한다. 이후 프로젝트 소유자 권한이 있는 로컬 `gcloud` 또는 Cloud Shell에서 아래 순서로 설정한다.

```powershell
$ProjectId = "bodeul-dev"
$Region = "asia-northeast1"
$Repository = "bodeul-core-api"
$DeployAccount = "bodeul-core-preview-deployer@$ProjectId.iam.gserviceaccount.com"
$RuntimeAccount = "bodeul-core-preview-runtime@$ProjectId.iam.gserviceaccount.com"

gcloud config set project $ProjectId
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com iamcredentials.googleapis.com sts.googleapis.com

gcloud artifacts repositories create $Repository `
  --repository-format=docker `
  --location=$Region `
  --description="BoDeul Core API images"

gcloud iam service-accounts create bodeul-core-preview-deployer `
  --display-name="BoDeul Core API preview deployer"
gcloud iam service-accounts create bodeul-core-preview-runtime `
  --display-name="BoDeul Core API preview runtime"

gcloud iam workload-identity-pools providers create-oidc bodeul-core-api-preview `
  --workload-identity-pool=github-actions `
  --location=global `
  --issuer-uri="https://token.actions.githubusercontent.com" `
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.repository_owner=assertion.repository_owner,attribute.ref=assertion.ref,attribute.environment=assertion.environment,attribute.actor=assertion.actor,attribute.workflow=assertion.workflow" `
  --attribute-condition="assertion.repository_id == '1209358990' && assertion.repository_owner_id == '275679915' && assertion.repository == 'bodeul110/bodeul-platform' && assertion.ref == 'refs/heads/dev' && assertion.environment == 'core-api-preview' && assertion.workflow_ref == 'bodeul110/bodeul-platform/.github/workflows/core-api-preview-deploy.yml@refs/heads/dev' && assertion.event_name in ['push', 'workflow_dispatch']"
```

이미 존재하는 리소스의 create 명령은 다시 실행하지 않는다. `describe` 또는 Google Cloud Console에서 현재 상태를 먼저 확인한다.

### 배포 계정 권한

```powershell
$ProjectNumber = gcloud projects describe $ProjectId --format="value(projectNumber)"
$WifMember = "principal://iam.googleapis.com/projects/$ProjectNumber/locations/global/workloadIdentityPools/github-actions/subject/repo:bodeul110@275679915/bodeul-platform@1209358990:environment:core-api-preview"

gcloud projects add-iam-policy-binding $ProjectId `
  --member="serviceAccount:$DeployAccount" `
  --role="roles/run.developer"

gcloud artifacts repositories add-iam-policy-binding $Repository `
  --location=$Region `
  --member="serviceAccount:$DeployAccount" `
  --role="roles/artifactregistry.writer"

gcloud iam service-accounts add-iam-policy-binding $RuntimeAccount `
  --member="serviceAccount:$DeployAccount" `
  --role="roles/iam.serviceAccountUser"

gcloud iam service-accounts add-iam-policy-binding $DeployAccount `
  --member=$WifMember `
  --role="roles/iam.workloadIdentityUser"
```

관리자 Firebase Hosting 종료에 따라 기존 `bodeul-repo` provider와 관리자 배포 서비스 계정은 2026-07-17에 삭제했다. 개발 Core의 `bodeul-core-api-preview` provider는 불변 repository/owner ID·정확한 `dev` ref·workflow·Environment·event를 검증한다. 운영 배포·백업·감사 provider는 각 `master` workflow 경계를 유지한다. 9월 27일 실제 GitHub immutable subject에 맞춰 서비스 계정 binding을 복구했고 임시 IAM은 즉시 회수했다. 서비스 계정 key JSON은 발급하지 않는다.

### Secret 생성과 권한

```powershell
$SecretIds = @(
  "bodeul-core-api-preview-db-jdbc-url",
  "bodeul-core-api-preview-db-username",
  "bodeul-core-api-preview-db-password",
  "bodeul-core-api-preview-kakao-local-rest-api-key"
)

foreach ($SecretId in $SecretIds) {
  gcloud secrets create $SecretId --replication-policy=automatic --project=$ProjectId
  gcloud secrets add-iam-policy-binding $SecretId `
    --project=$ProjectId `
    --member="serviceAccount:$RuntimeAccount" `
    --role="roles/secretmanager.secretAccessor"
}
```

secret 값은 콘솔에서 입력하거나 `core-api/deploy/cloud-run/set-preview-secrets.ps1`을 사용한다. 스크립트는 보안 입력을 프로세스 표준 입력으로만 전달하고 파일이나 shell history에 남기지 않는다.

Kakao 키만 회전할 때는 DB 자격 증명을 다시 입력하지 않고 대상 secret을 지정한다.

```powershell
.\core-api\deploy\cloud-run\set-preview-secrets.ps1 `
  -SecretIds "bodeul-core-api-preview-kakao-local-rest-api-key"
```

## GitHub Environment

`core-api-preview`에는 다음 Variables만 등록한다.

- `GCP_PROJECT_ID=bodeul-dev`
- `GCP_REGION=asia-northeast1`
- `CLOUD_RUN_SERVICE=bodeul-core-api-preview`
- `CLOUD_RUN_ARTIFACT_REPOSITORY=bodeul-core-api`
- `CLOUD_RUN_WORKLOAD_IDENTITY_PROVIDER=projects/533563500316/locations/global/workloadIdentityPools/github-actions/providers/bodeul-core-api-preview`
- `CLOUD_RUN_DEPLOY_SERVICE_ACCOUNT=bodeul-core-preview-deployer@bodeul-dev.iam.gserviceaccount.com`
- `CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT=bodeul-core-preview-runtime@bodeul-dev.iam.gserviceaccount.com`
- `CORE_DB_JDBC_URL_SECRET_VERSION=1`
- `CORE_DB_USERNAME_SECRET_VERSION=1`
- `CORE_DB_PASSWORD_SECRET_VERSION=1`
- `KAKAO_LOCAL_REST_API_KEY_SECRET_VERSION=1`
- `FIREBASE_PROJECT_ID=bodeul-dev`
- `FIREBASE_PROJECT_NUMBER=533563500316`
- `BODEUL_SESSION_PRE_CONSULTATION_ENFORCEMENT=false`
- `BODEUL_SESSION_COMPLETION_ENFORCEMENT=false`

`FIREBASE_PROJECT_NUMBER`는 token issuer와 audience를 제한하기 위한 공개 project 식별자이며 secret으로 저장하지 않는다. 배포 workflow가 `BODEUL_APP_CHECK_MODE=observe`를 Cloud Run 환경변수로 주입한다. 진료 전 확인과 동행 종료·완료 분리의 서버 강제는 Android 보급 전이므로 모두 `false`를 유지한다.

`OCI_REGION`, `CORE_API_SERVICE_NAME` 같은 OCI 변수는 제거한다. `core-api-production`에는 실제 GCP/Firebase 식별자와 DB secret version만 등록했고 Kakao version은 비워 뒀다. `core-api-migration-production`에는 별도 production migration 자격 증명을 등록했다.

### `core-api-production` Variables

production 리소스 생성 후 다음 값만 등록한다. DB URL과 비밀번호 원문은 GitHub에 두지 않는다.

- `GCP_PROJECT_ID=bodeul-prod-110`
- `GCP_REGION=asia-northeast1`
- `CLOUD_RUN_SERVICE=bodeul-core-api`
- `CLOUD_RUN_ARTIFACT_REPOSITORY=bodeul-core-api`
- `CLOUD_RUN_WORKLOAD_IDENTITY_PROVIDER=projects/649312328770/locations/global/workloadIdentityPools/github-actions/providers/bodeul-core-api-production`
- `CLOUD_RUN_DEPLOY_SERVICE_ACCOUNT=bodeul-core-deployer@bodeul-prod-110.iam.gserviceaccount.com`
- `CLOUD_RUN_RUNTIME_SERVICE_ACCOUNT=bodeul-core-runtime@bodeul-prod-110.iam.gserviceaccount.com`
- `CORE_DB_JDBC_URL_SECRET_VERSION=1`
- `CORE_DB_USERNAME_SECRET_VERSION=1`
- `CORE_DB_PASSWORD_SECRET_VERSION=1`
- `KAKAO_LOCAL_REST_API_KEY_SECRET_VERSION=<숫자 version>`
- `FIREBASE_PROJECT_ID=bodeul-prod-110`
- `FIREBASE_PROJECT_NUMBER=649312328770`
- `BODEUL_APP_CHECK_MODE=observe`
- `BODEUL_SESSION_PRE_CONSULTATION_ENFORCEMENT=false`
- `BODEUL_SESSION_COMPLETION_ENFORCEMENT=false`

첫 release 요청이 정상 App Check 판정을 받고 rollback을 재현한 뒤 `BODEUL_APP_CHECK_MODE=enforce`로 변경한다. DB version을 포함한 공개 Variables는 등록했지만 `KAKAO_LOCAL_REST_API_KEY_SECRET_VERSION`이 없으므로 production workflow는 인증 전에 fail-closed다.

### 진료 전 확인 점진적 적용

`BODEUL_SESSION_PRE_CONSULTATION_ENFORCEMENT`는 V16 schema 적용 여부가 아니라 미확인 세션의 서버 진행 차단 여부만 제어한다. Core API가 열을 항상 조회하고 PATCH하므로 설정이 `false`여도 V16 migration은 먼저 필요하다.

1. V16 migration과 검증 SQL을 적용한다.
2. preview와 production GitHub Environment의 값을 `false`로 둔 채 Core API를 배포한다.
3. 확인 상태 저장·해제·재진입을 지원하는 Android를 보급하고 실제 기기에서 검증한다.
4. 구버전 잔존율과 rollback 경로를 확인하고 별도 승인을 받는다.
5. preview를 `true`로 바꾸어 `STEP_INPUT_REQUIRED`와 동시 advance 차단을 검증한 뒤 production 전환을 별도로 승인한다.

문제가 생기면 값을 `false`로 되돌려 Core API를 다시 배포한다. DB 열은 상태 저장과 롤링 호환에 계속 필요하므로 애플리케이션보다 먼저 rollback하지 않는다. 이번 구현 시점에는 preview와 production 모두 서버 차단을 켜지 않는다.

### 동행 종료·완료 분리 점진적 적용

`BODEUL_SESSION_COMPLETION_ENFORCEMENT`는 V18 schema 적용 여부가 아니라 구버전 앱의 마지막 단계 직접 완료를 허용할지 제어한다. Core API가 `care_ended_at`, `manager_journal`, 리포트 상태와 가이드 첨부를 항상 읽으므로 설정이 `false`여도 V18 migration을 먼저 적용해야 한다.

설정이 `false`인 혼합 버전 기간에는 돌봄 종료 시 `care_ended_at`과 다음 단계만 저장하고 구버전이 알고 있는 기존 `current_status`를 유지한다. 새 앱은 `CARE_ENDED_PENDING_COMPLETION` 진행 판정으로 일지 화면에 재진입하고, 구버전 앱은 알 수 없는 `CARE_ENDED` enum을 받지 않는다. `true`로 전환한 뒤에만 상태값도 `CARE_ENDED`로 바뀐다. 다만 채팅·첨부·위치 쓰기 차단, 매니저 조회 회수, 정보공유 동의 만료와 실시간 원문의 보존 시작점은 플래그와 무관하게 최초 `care_ended_at`에서 즉시 적용한다.

1. V18 migration을 개발 DB에 적용하고 기존 `COMPLETED` 행 backfill, runtime role, `CARE_ENDED` 기준 `+180일/+30일/+24시간`, 동의 `+7일`과 rollback SQL을 검증한다.
2. postgres 권한으로 `core-api/db/bootstrap/006_companion_completion_realtime_authorization.sql`을 적용하고 `015_companion_completion_realtime_authorization_scenarios.sql`로 진행 중 매니저 허용, 종료 매니저 거부와 환자 유지 여부를 확인한다.
3. preview와 production GitHub Environment의 값을 `false`로 둔 채 Core API를 배포한다.
4. 새 Android에서 가이드 8·10 선택 첨부, 가이드 12 중복 종료 요청, 종료 직후 채팅·첨부·위치 거부와 Realtime 구독 종료·역할별 조회 회수, 가이드 13 빈 일지·300자 제한과 리포트 실패 재시도를 실기기로 검증한다.
5. 구버전 앱 잔존율, `CARE_ENDED` 세션 재진입과 직전 Core API revision rollback을 확인하고 별도 승인을 받는다.
6. preview에서만 값을 `true`로 바꾸어 구버전 직접 완료 차단과 새 앱 정상 완료를 검증한다. production 활성화는 별도 출시 승인 뒤 진행한다.

문제가 생기면 값을 `false`로 되돌려 Core API를 다시 배포한다. V18 열과 첨부 테이블은 롤링 호환과 이미 저장된 종료 시각을 위해 유지하며 애플리케이션보다 먼저 rollback하지 않는다. 이번 구현에서는 preview·production 값을 만들거나 변경하지 않고 워크플로 기본값도 `false`로 둔다.

V18 schema 자체를 되돌려야 할 때는 앱·Core API 쓰기와 신규 Realtime 연결을 차단한 maintenance window를 먼저 확보하고 다음 순서를 지킨다. schema와 Realtime 권한식을 서로 다른 시점에 되돌리면 존재하지 않는 `care_ended_at`을 참조하거나 종료 매니저 권한을 먼저 다시 열 수 있으므로, 두 rollback을 같은 maintenance 상태에서 연속 실행한다.

1. maintenance 상태 진입과 쓰기·신규 연결 차단 시각을 기록하고, 기존 Core API 요청이 끝난 것을 확인한다.
2. `companion_session_artifacts`의 세션 ID, 용도와 `storage_path`, `companion_session_artifact_operations`의 요청 UUID·fingerprint·revision을 접근 제한된 산출물로 각각 export한다.
3. export한 경로의 Firebase Storage 원본을 삭제하고 삭제 결과를 검증한다.
4. `CARE_ENDED`, `care_ended_at`, 매니저 일지와 리포트 생성 상태·오류·갱신 시각을 export한다. V18 baseline 이후 값이 바뀐 행은 자동 역변환하지 않으며 운영자가 복원 가능성을 확인해 정리하기 전 rollback이 중단된다.
5. 확인된 첨부 메타데이터와 operation ledger 행을 삭제한다. 두 테이블 중 어느 한쪽이라도 행이 남아 있으면 rollback SQL은 의도적으로 중단된다.
6. `core-api/db/rollback/V18__merge_companion_care_completion.sql`을 실행한다. migration 직후 legacy 원문의 TTL과 동의 만료 경계도 비공개 baseline ledger 값으로 복원된다. 대상 원문이 이미 파기됐으면 백업 복원 전 rollback이 중단된다.
7. schema rollback 성공을 확인한 같은 maintenance 상태에서 곧바로 `core-api/db/bootstrap/rollback/006_companion_completion_realtime_authorization_rollback.sql`을 실행하고 V17 권한식 복원을 확인한다. 순서를 바꾸거나 중간에 트래픽을 열지 않는다.
8. 두 rollback 중 하나라도 실패하면 maintenance 상태를 유지하고 백업 복원 또는 누락된 rollback을 완료한다. schema와 V17 Realtime helper가 함께 검증된 뒤에만 Core API와 신규 Realtime 연결을 다시 연다.

CI의 `db/verification/verify_companion_completion_migration.sh`는 V17 fixture에서 V18을 실제 적용하고, `care_ended_at` 기준 채팅·첨부·위치 TTL, 동의 만료 확정·재부여 차단, 종료와 동시에 들어오는 직접 쓰기 거부, 매니저 Realtime 회수, 늦은 `COMPLETED` 전환의 TTL 불변, legacy baseline TTL 복원, baseline 변조와 신규 V18 상태별 rollback 실패, 용도별 0~1/0~3 제약, SHA-256, operation ledger 유지, 후반 오류가 발생한 rollback의 원자 복구, export·정리 후 rollback 성공을 disposable PostgreSQL 17에서 검증한다.

## 최초 서비스 공개

Android와 사용자 웹은 Google Cloud IAM token이 아니라 Firebase ID token을 사용한다. 따라서 Cloud Run IAM 단계에서는 서비스 호출을 공개하고 Spring Security가 `/health` 외 요청을 인증해야 한다.

Cloud Run은 일부 `z`로 끝나는 URL 경로를 예약하므로 Core API 상태 경로는 `/healthz`가 아니라 `/health`를 사용한다. 이는 [Cloud Run 알려진 문제의 Reserved URL paths](https://cloud.google.com/run/docs/known-issues#reserved_url_paths) 회피 기준이다.

최초 image 배포 후 프로젝트 소유자가 한 번만 실행한다.

```powershell
gcloud run services add-iam-policy-binding bodeul-core-api-preview `
  --project=bodeul-dev `
  --region=asia-northeast1 `
  --member=allUsers `
  --role=roles/run.invoker
```

production 서비스도 같은 인증 경계를 사용한다. 공식 조직의 도메인 제한에서는 새 `allUsers` binding이 거부되므로 조직 정책을 완화하지 않는다. 첫 비공개 image 배포 뒤 권한 있는 운영자가 해당 서비스의 Invoker IAM 검사만 해제한다. 이는 Cloud Run 서비스 설정이며 Firebase ID token·PostgreSQL 역할 검사와 App Check 설정은 유지한다.

```powershell
gcloud run services update bodeul-core-api `
  --project=bodeul-prod-110 `
  --region=asia-northeast1 `
  --no-invoker-iam-check
```

배포 서비스 계정에는 IAM policy 변경 권한을 주지 않는다. 최초 workflow가 서비스를 만든 뒤 공개 호출 설정 전 smoke test에서 403으로 실패할 수 있다. 먼저 IAM 인증 요청으로 `/health`와 업무 API의 Firebase 무인증 거부를 확인하고, 위 서비스 설정을 적용한 뒤 같은 `master` commit으로 workflow를 다시 실행한다. custom domain이나 사용자 트래픽을 연결하기 전 단계이므로 최초 실패나 비공개 확인만으로 production 검증 완료를 기록하지 않는다.

공개 설정을 되돌릴 때에는 같은 서비스에 `--invoker-iam-check`를 적용한다. 신규 조직 전체의 도메인 제한을 풀거나 배포 계정에 상시 Cloud Run Admin을 추가하는 방식은 사용하지 않는다. [Google Cloud 공개 호출 문서](https://docs.cloud.google.com/run/docs/authenticating/public)의 도메인 제한 대응 기준을 따른다.

배포 workflow는 Cloud Run IAM policy를 변경하지 않는다. 공개 호출을 허용하더라도 `/api/auth/me` 무인증 요청은 Spring에서 401을 반환해야 한다.

## 배포

`Core API Preview Deploy` workflow는 다음 순서로 실행한다.

1. `master`와 확인 입력값을 검사한다.
2. Gradle test를 실행한다.
3. GitHub OIDC와 WIF로 배포 계정에 인증한다.
4. Java 21 비루트 컨테이너를 빌드해 Artifact Registry에 commit SHA tag로 게시한다.
5. Secret Manager reference와 runtime 서비스 계정을 연결한다.
6. 최신 revision에 트래픽 100%를 보낸다.
7. `/health` 200과 `/api/auth/me` 무인증 401을 검사한다.

production은 자동 배포하지 않는다. `.github/workflows/core-api-production-deploy.yml`은 GitHub Environment 승인을 거친 수동 `workflow_dispatch`만 허용하고 DB migration과 앱 배포를 분리한다. `master`, 40자 commit SHA, 서비스명, production project·리전·계정·WIF·secret version 형식을 모두 확인한 뒤에만 인증을 시작한다. 기존 production 서비스에 VPC connector나 Direct VPC egress가 연결돼 있으면 현재 `dynamic` 정책과 다르므로 image 빌드 전에 중단한다.

DB migration은 `.github/workflows/core-api-migration.yml`의 `production` target을 사용한다. `master`의 실제 commit SHA와 복원 가능한 백업 증적 URL 또는 ID가 모두 있어야 `core-api-migration-production` Environment 승인으로 넘어간다. workflow는 Core API 검사를 통과한 뒤 Flyway를 실행하고 target, commit, 백업 참조를 job summary에 남긴다.

초기 production은 1 vCPU, 1 GiB, 최소 인스턴스 0, 최대 인스턴스 2, concurrency 8과 인스턴스당 DB pool 2를 사용한다. 배포 뒤 `/health` 200과 무인증 auth/place search 401을 확인한다. smoke test가 실패하고 직전 정상 revision이 있으면 workflow가 트래픽을 직전 revision 100%로 자동 복구한다. 최초 배포처럼 직전 revision이 없으면 실패 상태를 유지하고 운영자가 원인을 확인한다.

### Kakao 고정 outbound 전환 조건

현재 배포 workflow는 `kakao-egress=dynamic` label을 남기고 VPC 관련 설정을 넣지 않는다. Kakao 호출 허용 IP는 production 키 등록과 별개이며 초기 배포의 필수 조건이 아니다.

고정 outbound는 다음 순서로 별도 변경한다.

1. Kakao 계약·정책 또는 보안 검토에서 호출 허용 IP가 필요한 근거와 비용 승인을 기록한다.
2. `asia-northeast1`에 Direct VPC egress, Cloud NAT와 reserved external IP를 구성한다.
3. Cloud Run의 모든 outbound를 VPC로 보내고 실제 출발 IP, Supabase 연결과 Kakao 장소 검색을 확인한다.
4. 확인된 IP를 Kakao REST 키의 호출 허용 IP에 등록하고 인증된 검색, timeout·429·fallback을 다시 검증한다.
5. rollback할 때는 Kakao 호출 허용 IP를 먼저 해제한 뒤 Cloud Run과 NAT 설정을 되돌린다.

이 전환 PR에서는 production workflow의 동적 outbound 중단 검사를 고정 egress 검증으로 교체하고, Cloud NAT·외부 IP 시간 비용과 처리량 비용을 비용 문서에 추가한다.

## Rollback

Cloud Run revision은 immutable image digest를 참조한다. 장애 시 직전 정상 revision으로 트래픽을 되돌린다.

```powershell
gcloud run revisions list `
  --service=bodeul-core-api-preview `
  --project=bodeul-dev `
  --region=asia-northeast1

gcloud run services update-traffic bodeul-core-api-preview `
  --project=bodeul-dev `
  --region=asia-northeast1 `
  --to-revisions="<정상-revision>=100"
```

DB migration은 배포 workflow와 분리돼 있으므로 애플리케이션 rollback이 schema rollback을 자동 실행하지 않는다.

## 검증 기록

배포와 rollback 결과는 `docs/reports/`와 Issue #156에, 실제 token과 PostgreSQL role 연결 결과는 Issue #157에 기록한다. 채팅·위치·Realtime·FCM 결과는 Issue #221에, Kakao Local Secret 주입과 실호출 결과는 [Issue 158 Kakao Local Core API preview 실검증](../reports/issue-158-kakao-local-core-api-2026-07-16.md)에 기록한다.

- commit SHA, Cloud Run revision, 리전과 서비스 URL
- `/health` 200
- 무인증 `/api/auth/me` 401
- 정상, 만료, 변조, 다른 Firebase project token 응답
- DB role 조회와 secret/token 로그 비노출
- 인증된 `GET /api/places/search` 200과 Kakao 콘솔 쿼터 반영
- Cloud Run revision의 `kakao-egress` label과 VPC 연결 부재 또는 승인된 고정 outbound IP
- 임시 Firebase 사용자와 PostgreSQL 역할 행 정리
- cold start 시간과 정상 기동 여부
- 직전 revision rollback 결과

## 중단 조건

- Google Cloud 결제 계정이나 프로젝트 소유권이 확인되지 않음
- WIF provider가 `bodeul110/bodeul-platform`과 불변 저장소 ID로 제한되지 않음
- 서비스 계정 JSON key를 발급하거나 저장소에 넣어야만 배포 가능함
- DB owner 또는 migration 자격 증명을 runtime에 사용함
- Cloud Run 최대 인스턴스와 DB pool 상한이 설정되지 않음
- secret, Firebase token, DB 연결 문자열이 로그나 GitHub 출력에 노출됨
- Firebase와 PostgreSQL 양쪽에 운영 쓰기를 하면서 source of truth가 정해지지 않음
