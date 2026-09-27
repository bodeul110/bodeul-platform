# Production 인프라 기본값

기준일: 2026-09-27

이 문서는 BoDeul production 리소스의 식별자, 리전, 배포 경계와 운영 기준을 관리한다. 리소스 생성 기록과 현재 사용 가능 상태는 구분한다. 도메인은 보유 여부와 실제 연결을 확인하고, 새로 구매해야 한다고 단정하지 않는다.

## 현재 확인 요약

- 2026-09-27 개발·운영 Supabase는 Pro 조직에서 각각 Healthy이며, 두 DB 모두 Flyway V23·실패 이력 0건을 확인했다. 운영 V23 dump의 외부 보관과 격리 복원도 통과했다.
- 운영 Core API는 배포 후 `/health` 200과 무인증 API 401을 확인했다. 운영 관리자 웹은 전용 DB 자격 증명 연결, 최초 개인 `SUPER_ADMIN` 등록과 TOTP 로그인 후 대시보드 진입까지 확인했다.
- 환경 기반 구축 완료와 서비스 출시 승인은 다르다. 정상 운영 토큰·환경 교차 거부, Realtime 실제 구독, 관리자 업무 흐름, 보안 강제 모드와 rollback 검증은 남아 있다. [9월 27일 환경 분리 기록](../reports/dev-production-separation-2026-09-27.md)을 확인한다.
- 2026-09-22 두 Google Cloud 프로젝트의 공식 조직 소속과 공용 결제 연결·활성을 확인했다. [명칭 기준](resource-naming.md)에 따라 표시 이름만 갱신했으며, 결제 연결·비밀값·IAM은 이름 정리 과정에서 변경하지 않았다.
- Supabase Pro 전환은 완료했으며, 실제 서비스 전환일은 미정이다. 관리자 웹의 확인 범위는 [관리자 웹 환경](admin-web-environments.md)을 따른다.

## 결정 요약

- 개발과 production은 Google Cloud/Firebase 프로젝트와 Supabase 프로젝트를 각각 분리한다.
- 관리자 웹은 기존 Vercel 프로젝트 `bodeul-admin-web`의 Preview와 Production 환경을 구분해 사용한다. Vercel 프로젝트를 하나 더 만들지 않는다.
- 관리자 웹과 Core API, Supabase는 Tokyo 리전에 맞춘다.
- 관리자 웹은 Vercel Next.js 서버, 사용자 서비스는 Cloud Run Spring Core API가 담당한다.
- 두 서버는 같은 production PostgreSQL을 서로 다른 최소 권한 role로 사용하며 서로를 proxy로 호출하지 않는다.
- Firebase Auth, FCM, Storage와 Firebase 결합 Functions는 production Firebase 프로젝트에서 유지한다.
- 초기 Core API는 Cloud Run 기본 동적 outbound를 사용한다. Kakao가 호출 허용 IP를 필수로 요구하거나 별도 보안 검토가 승인될 때만 Direct VPC egress, Cloud NAT와 고정 IP를 추가한다.
- 사람의 Google Cloud 권한은 역할별 Cloud Identity 보안 그룹으로 관리하고, CI와 런타임은 WIF와 서비스 계정을 사용한다.
- Production 구성 드리프트는 배포·백업 계정을 재사용하지 않고 전용 읽기 전용 감사 계정과 보호된 수동 workflow로 확인한다.
- 개발 Preview를 출시 전 검증 환경으로 사용하고, 현재 규모에서는 세 번째 staging 환경을 만들지 않는다.
- 연말은 초기 목표이며 이전 2026-12-15는 임시 일정이다. 운영 게이트를 통과한 뒤 실제 전환일을 정한다.
- 월 반복 비용 승인 한도는 150,000 KRW, 정상 목표는 100,000~130,000 KRW로 둔다.

## 리소스 기준

| 범위 | 확정값 | 비고 |
| --- | --- | --- |
| Google Cloud/Firebase 표시 이름 | `bodeul-prod` | 두 관리 API의 표시 이름을 일치시켰다. |
| Google Cloud project ID / number | `bodeul-prod-110` / `649312328770` | 식별자 유지. 공식 조직 소속과 공용 결제 연결·활성 확인 |
| Google Cloud/Cloud Run 리전 | `asia-northeast1` | Tokyo |
| Cloud Run 서비스 | `bodeul-core-api` | preview 접미사를 사용하지 않는다. |
| Artifact Registry/이미지 | `bodeul-core-api` | 개발 프로젝트와 이름은 같아도 프로젝트 경계로 분리된다. |
| 배포 서비스 계정 | `bodeul-core-deployer` | WIF 배포 전용 |
| 런타임 서비스 계정 | `bodeul-core-runtime` | Secret Manager 접근과 실행 전용 |
| DB 백업 서비스 계정 | `bodeul-db-backup` | 검증된 dump의 GCS 생성·조회 전용, 삭제 권한 없음 |
| WIF pool/provider | `github-actions` / `bodeul-core-api-production` | 불변 저장소·소유자 ID, `master`, deploy workflow, 수동 이벤트와 `core-api-production` subject 전용 |
| DB 백업 WIF provider | `github-actions` / `bodeul-db-backup-production` | 불변 저장소·소유자 ID, backup workflow, 수동 이벤트와 `core-api-migration-production` subject 전용 |
| 배포 Environment | `core-api-production` | 수동 production 배포와 승인 보호 |
| migration Environment | `core-api-migration-production` | 앱 배포와 DB 변경을 분리한다. |
| 인프라 감사 Environment | `production-infrastructure-audit` | metadata-only WIF 점검과 승인 보호 |
| Supabase 표시 이름 / ref | `bodeul-db-prod` / `aoijbzgozbopsxzrasbb` | 개발 DB와 분리, Pro 조직, Healthy, V23 확인 |
| Supabase 리전 | `ap-northeast-1` | Tokyo |
| Vercel 프로젝트 | `bodeul-admin-web` | 기존 프로젝트를 유지한다. |
| Vercel production branch | `master` | 보호된 PR 병합만 허용한다. |
| Vercel Functions 리전 | `hnd1` | Tokyo |
| 관리자 도메인 | `admin.<기준-도메인>` | 목표 호스트 예시. 현재 배포 주소는 관리자 웹 환경 문서 기준 |
| Core API 도메인 | `api.<기준-도메인>` | 목표 호스트 예시. 실제 연결 전 DNS·인증서 확인 |

## 환경 분리

| 환경 | Google Cloud/Firebase | Supabase | Vercel | 용도 |
| --- | --- | --- | --- | --- |
| 개발 (`dev`) | `bodeul-dev` | `bodeul-db-dev` | Preview | PR, 실연동, 실기기 검증 |
| production (`master`) | `bodeul-prod-110` (표시 이름 `bodeul-prod`) | `bodeul-db-prod` | Production | 출시 전 격리 운영 |

Vercel Preview에는 개발 Firebase와 개발 관리자 DB 값만 둔다. Production에는 production 값만 두며, 값이 없을 때 서버 API가 설정 오류로 종료되는 fail-closed 상태를 유지한다. Firebase authorized domain에는 실제 관리자 도메인과 출시 전 검증에 필요한 Vercel 도메인만 정확한 호스트명으로 등록하고 wildcard를 사용하지 않는다.

Production Supabase의 9월 21일 일시정지 기록은 과거 상태다. 9월 27일에는 재개·Pro 전환·V23 적용과 격리 복원을 확인했다. 제공자 자동 백업의 실제 복구 지점과 파일 백업, 서비스 전환 후 정상 업무 검증은 별도로 확인한다.

동시 릴리스가 늘거나 production과 같은 데이터 규모·외부 연동으로 장기간 QA해야 할 때 세 번째 staging 프로젝트를 검토한다. 현재 MVP 규모에서는 비용과 운영 대상을 늘리는 효과가 더 크므로 추가하지 않는다.

## 배포 정책

### 관리자 웹

- 기능 PR은 `dev`로 보내고 `lint-and-build`, CodeQL과 Vercel Preview를 확인한 뒤 squash merge한다.
- 운영 승격은 검증된 `dev`에서 `master`로 release PR을 만들고 merge commit으로 반영한다. 기능 PR의 `dev` 병합은 production 배포 승인이 아니다.
- Vercel은 같은 프로젝트의 `master` 병합을 Production에 자동 배포한다.
- 배포 후 루트 200, 무인증 관리자 API 401, 함수 리전 `hnd1`을 확인한다.
- 이전 정상 deployment로 즉시 rollback할 수 있어야 한다.

### Core API와 DB migration

- production 배포와 migration은 `workflow_dispatch`로만 실행한다.
- `core-api-production`과 `core-api-migration-production` GitHub Environment의 승인 보호를 유지한다.
- 배포 workflow는 `master`의 실제 40자 commit SHA와 `bodeul-core-api` 서비스명을 다시 입력해야 진행한다.
- production DB migration도 `master`의 실제 40자 commit SHA와 복원 가능한 백업 증적을 요구한다.
- DB migration을 먼저 실행하고 호환성 검증 뒤 애플리케이션을 배포한다.
- Cloud Run은 commit SHA image를 사용하고 `/health` 200, 무인증 API 401을 확인한다.
- smoke test 실패 시 직전 정상 revision이 있으면 트래픽을 자동 복구하고, 수동 rollback도 출시 전에 리허설한다.
- production 리소스와 secret version이 준비되기 전에는 production workflow를 실행하지 않는다.

초기 production 런타임은 1 vCPU, 1 GiB, 최소 인스턴스 0, 최대 인스턴스 2, 인스턴스당 DB pool 2로 시작한다. 최대 DB 연결을 4개로 제한하면서 초기 트래픽에 두 인스턴스까지 대응하는 현재 MVP 기준이다. 실제 지연·연결 수와 비용을 확인한 뒤 조정한다.

Kakao Local은 production REST 키를 Secret Manager에서만 주입하고 호출 허용 IP는 초기에는 활성화하지 않는다. Kakao 문서상 호출 허용 IP는 선택적 보안 기능이며, 고정 outbound에는 VPC 경로, Cloud NAT, 외부 IP와 추가 비용·장애 지점이 생긴다. 현재 MVP에서는 인증·역할 인가·분당 제한·6시간 캐시·키 회전·로그 비노출을 우선 통제로 사용한다. 전환 조건과 순서는 [Kakao Local Core API 경계](../architecture/kakao-local-core-api.md)를 따른다.

9월 27일 사용자 승인에 따라 기존 개발 Kakao 키를 운영의 별도 Secret 항목에도 등록했다. 같은 Kakao 앱·키이므로 쿼터와 폐기 영향은 공유하며, DB 비밀번호는 공유하지 않는다.

production Secret Manager ID는 다음으로 고정한다.

- `bodeul-core-api-production-db-jdbc-url`
- `bodeul-core-api-production-db-username`
- `bodeul-core-api-production-db-password`
- `bodeul-core-api-production-kakao-local-rest-api-key`

`core-api/deploy/cloud-run/set-production-secrets.ps1`은 production project ID 재입력과 허용된 secret ID 검사를 통과한 경우에만 기존 secret에 version을 추가한다.

## PostgreSQL 권한

production DB도 개발 DB와 같은 역할 경계를 사용하되 자격 증명은 새로 만든다.

| role | 용도 | 초기 권한 |
| --- | --- | --- |
| `bodeul_migration` / `bodeul_migrator` | Flyway와 schema 변경 | migration에만 사용 |
| `bodeul_core_runtime` / `bodeul_core_service` | 사용자 서비스 | 전환한 도메인의 필요한 DML만 부여 |
| `bodeul_admin_runtime` / `bodeul_admin_service` | 관리자 서버 | 제한된 조회와 배정·결제·감사 함수 실행, 일반 테이블 직접 쓰기 금지 |
| `bodeul_retention_runtime` / `bodeul_retention_service` | 보존 worker | 승인된 후보 조회·파기 함수만 실행 |

- `anon`, `authenticated`, `service_role`을 애플리케이션 DB 접속 계정으로 사용하지 않는다.
- 브라우저와 APK에서 Supabase Data API나 PostgreSQL에 직접 연결하지 않는다.
- public schema의 public role grant는 0을 유지한다.
- 도메인마다 Firestore 또는 PostgreSQL 중 하나만 쓰기 source of truth로 둔다.

## 백업과 복원

- Supabase Pro 전환은 완료했다. 실제 사용자 데이터를 받기 전에 제공자 일일 백업의 실제 복구 지점과 복원 권한을 확인한다.
- 제공자 일일 백업은 최소 7일 보존을 기준으로 한다.
- 매주 암호화한 logical dump를 제공자 외부의 제한된 저장소에 보관하고 4주 뒤 순환 삭제한다.
- 최초 출시 전 복원 리허설을 완료하고 이후 분기마다 반복한다.
- 복원 후 custom login role 비밀번호를 교체하고 runtime 권한, row 수와 핵심 쿼리를 다시 검증한다.
- PostgreSQL 백업에는 Firebase Storage 객체가 포함되지 않으므로 파일 백업과 복원 절차를 별도로 유지한다.
- DB가 4GB를 넘거나 허용 가능한 데이터 손실 시간이 24시간보다 짧아지면 PITR을 적용한다.
- `.github/workflows/postgres-production-backup-restore.yml`은 production migration 자격 증명으로 읽기 전용 custom-format dump를 만든다. 별도 PostgreSQL 컨테이너에서 owner, ACL, 전체 테이블 row 수, RLS, 정책, 인덱스, 제약과 Flyway 이력을 대조한 경우에만 GCS에 업로드한다.
- workflow의 GCS 권한은 `bodeul-db-backup`에 한정하며 object 생성·조회만 허용한다. dump는 GitHub Artifact에 올리지 않는다.
- 검증된 object는 `gs://bodeul-prod-110-db-backups/postgres/verified/YYYY/MM/DD/<실행시각>/`에 dump, SHA-256과 복원 보고서를 함께 보관한다.

## 보안과 모니터링

- production DB 자격 증명은 새로 만들어 Google Secret Manager와 Vercel Production에 등록했다. 개발 비밀번호는 복사하지 않는다. Kakao REST 키만 사용자 승인에 따라 같은 값을 별도 Secret 항목으로 관리한다.
- 서비스 계정 JSON key는 발급하지 않고 GitHub OIDC와 WIF를 사용한다.
- [Production 인프라 읽기 전용 점검](production-infrastructure-audit.md)은 Secret payload, Firestore 문서, Auth 사용자와 Storage 객체 권한 없이 project·IAM·서비스 metadata만 확인한다.
- 모든 관리자 계정에 MFA를 적용하고 공용 계정을 금지하는 것이 출시 기준이다. 현재 최초 개인 관리자의 TOTP 로그인은 확인했지만 전역 MFA 강제 모드 전환과 복구 절차 검증은 남아 있다.
- 출시 전 최소 2명의 실명 운영자를 정해 한 명의 계정 잠금이 전체 운영 중단으로 이어지지 않게 한다.
- `gcp-admins@bodeul.kr`에 주 관리자와 복구용 관리자 두 소유자 계정을 등록하고, 두 조직 및 개발·production 프로젝트 조회를 그룹 경유로 각각 검증했다. 이후 두 프로젝트와 두 조직의 `scp@bodeul.kr` 직접 관리자 IAM binding을 모두 제거해 0건으로 만들었다.
- 개발자는 `developers@bodeul.kr`, production 조회 담당자는 `prod-operators@bodeul.kr`로 관리한다. 팀에서 제외된 이전 개발자의 `bodeul-dev` 직접 Editor 권한은 제거했으며, 현재 개발자의 직접 Editor 권한은 개별 그룹 경유 접근 검증 전까지만 유지한다. production 조회 그룹에는 로그·모니터링·Cloud Run·Secret 메타데이터 조회만 허용한다.
- 알림용 Google Cloud budget은 개발 10,000 KRW, production 30,000 KRW와 50%·80%·100%를 계획 기준으로 둔다. 현재 결제 연결·알림 수신 설정은 [비용 모니터링](cost-monitoring.md)에 따라 재확인한다.
- Supabase 조직의 Pro 전환은 완료했다. 실제 운영 전 Vercel의 현재 플랜과 개발자 좌석 조건을 확인한다.
- Supabase spend cap을 유지하고 PITR, custom domain과 Log Drain은 초기 운영 비용에 포함하지 않는다.
- Cloud Run 오류율·지연·인스턴스 수, PostgreSQL 연결 수·용량·백업, Vercel 실패 배포와 Firebase Auth 오류를 확인한다.

## 생성과 출시 순서

1. 월 150,000 KRW 계획 한도와 게이트 기반 전환을 기준으로 한다. 결제 책임자, 사용할 도메인·주소, 운영자와 실제 일정은 출시 전에 확인한다.
2. production Google Cloud 프로젝트를 만들고 Firebase를 활성화한다. 완료.
3. production Supabase Pro·Healthy, V23 적용과 역할 경계를 확인했다.
4. WIF, 서비스 계정, Artifact Registry와 Secret Manager 구성 및 Cloud Run 첫 배포·기본 smoke를 완료했다. 정상 인증·외부 연동 검증은 별도다.
5. Vercel Production 전용 DB 연결과 최초 관리자 MFA 로그인까지 완료했다. 업무별 동작과 사용할 최종 도메인은 확인한다.
6. Firebase authorized domain, App Check 강제 모드, 관리자 MFA 강제·복구와 최소 권한을 검증한다.
7. V23 DB 격리 복원은 완료했다. Cloud Run revision과 Vercel deployment rollback, 파일 복원은 별도로 리허설한다.
8. smoke test와 운영 담당자 확인 뒤 트래픽을 전환한다.

## 기반 구축·검증 이력

다음은 2026년 7~8월 구축·검증 당시 기록이다. 최신 DB 가동·Rules revision·secret version·권한·요금제를 다시 확인한 결과가 아니며, 현재 상태는 위 요약과 환경별 보고서를 우선한다.

- `.github/workflows/core-api-production-deploy.yml`에 보호된 수동 배포, 대상 재확인과 smoke 실패 rollback을 준비했다.
- `.github/workflows/core-api-migration.yml`의 production 경로에 `master` SHA, 백업 증적과 사전 Core API 검사를 적용했다.
- `core-api/deploy/cloud-run/set-production-secrets.ps1`에 production 전용 secret version 입력 경계를 준비했다.
- `core-api-production`에는 production GCP/Firebase 식별자와 DB Secret Manager version을 등록했다. Kakao production secret version은 비어 있어 첫 배포는 계속 fail-closed다.
- `core-api-migration-production`에는 production migration 자격 증명을 등록했다. run `32981200371`에서 V15까지 적용했고, run `32981484159`에서 최신 version과 실패 이력 0건을 읽기 전용으로 재확인했다.
- production Firestore와 Storage에는 저장소의 현재 Rules를 배포했다. Firestore는 Tokyo, 삭제 방지와 7일 PITR version 보존을 사용하고 App Check는 아직 강제하지 않는다.
- production Firebase Storage는 bucket 수준 Public Access Prevention을 강제했다. UBLA는 조직 정책 아래 즉시 되돌릴 수 없으므로 개발 버킷의 업로드·미리보기·삭제 실검증 전까지 보류한다.
- production Supabase는 빈 데이터 상태로 Flyway V15와 계정 삭제 영향도 DB 계약을 갖는다. 최소 권한 role과 공개 role table grant 0건을 유지하며 migration 후 Security Advisor 경고도 0건이다.
- 당시 production Supabase 조직은 Free였다. 임시 유료 전환일에 자동 결제하지 않고 실제 운영 전 현재 플랜·백업·사용량 상한 조건을 확인한다.
- production Core API의 초기 Kakao outbound 정책은 `dynamic`이다. 저장소의 배포 설정에는 VPC와 Cloud NAT를 연결하지 않으며 production workflow가 기존 서비스의 실제 VPC 연결을 조회해 정책과 다르면 배포를 중단한다. Kakao 호출 허용 IP의 실제 콘솔 상태는 production 키 등록 때 별도로 확인한다.
- 기존 migration 전·V12·V13 검증 dump와 2026-08-26 V14·V15 적용 전후 검증 dump를 비공개 GCS bucket에 28일 보존으로 저장했다. 최신 V15 restore 리허설을 완료했고, 실제 데이터 규모의 복구 시간 측정은 출시 후 분기 리허설에서 반복한다.
- production logical dump 전용 서비스 계정, WIF provider와 GitHub Environment 변수를 구성했다. 2026-07-18 당시 V3 dump를 격리 PostgreSQL에 복원해 owner, ACL, row 수, RLS, 정책, 인덱스, 제약과 Flyway 이력 일치를 확인했다.
- production 인프라 감사용 keyless 서비스 계정, custom metadata role, exact-subject WIF provider와 보호된 GitHub Environment를 구성했다. 단계 기대값은 저장소의 `tools/gcp/production-infrastructure-state.json`에서 PR 이력으로 관리한다.
- production 리소스 생성 후 첫 배포 전에는 App Check를 `observe`로 시작하고 정상 release 요청을 확인한 뒤 `enforce`로 바꾼다.

## 사람 결정이 필요한 항목

- 보유 도메인과 사용할 호스트·DNS 관리 주체
- 실명 운영자 2명, 장애 대응 책임자와 rollback 승인자
- 실제 전환일에 맞춘 사용자 공지와 최종 점검 시간

나머지 리소스 이름, 리전, 환경 경계, 배포·백업·보안 기본값은 이 문서를 기준으로 진행한다.

## 근거

- [Vercel 환경 분리](https://vercel.com/docs/deployments/environments)
- [Vercel Functions 리전 설정](https://vercel.com/docs/functions/configuring-functions/region)
- [Vercel 리전 목록](https://vercel.com/docs/regions)
- [Supabase 환경 관리](https://supabase.com/docs/guides/deployment/managing-environments)
- [Supabase 백업](https://supabase.com/docs/guides/platform/backups)
- [Firebase 환경 분리](https://firebase.google.com/docs/projects/dev-workflows/overview-environments)

## 관련 문서

- [목표 인프라 구조](../architecture/target-infrastructure.md)
- [관리자 웹 환경 기준](admin-web-environments.md)
- [Spring Core API Cloud Run 인프라 런북](core-api-infrastructure-runbook.md)
- [비용과 쿼터 모니터링](cost-monitoring.md)
- [2026년 Production 운영 전환 계획](production-transition-plan-2026.md)
- [데이터 보관 및 파기 정책](data-retention-policy.md)
- [Google Cloud 계정 및 IAM 운영 기준](google-cloud-access-governance.md)
- [Production 인프라 읽기 전용 점검](production-infrastructure-audit.md)
- [Production PostgreSQL 백업·복원 리허설](../reports/postgres-production-backup-restore-rehearsal-2026-07-18.md)
- [Production PostgreSQL V13 migration·복원 검증](../reports/postgres-production-v13-migration-restore-2026-07-19.md)
- [Production DB V15 migration·복원 검증](../reports/production-db-migration-readiness-2026-08-26.md)
