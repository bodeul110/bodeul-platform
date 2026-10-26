# 프로젝트와 인프라 명칭

명칭 변경 기준일: 2026-09-22. 현재 상태 대조: 2026-09-27

## 판단 기준

- 작업 목적: 개인 계정명과 초기 임시 이름 대신 서비스 역할과 개발·운영 환경을 구분한다.
- 선택한 방식: 제품 이름은 `BoDeul`, 리소스 이름은 `bodeul-<역할>-<환경>`을 기본으로 한다. 프로젝트 전체는 `bodeul-dev`와 `bodeul-prod`로 구분한다.
- 대안: 프로젝트·서버·DB를 새 이름으로 재생성하는 방법도 있지만, 현재 MVP에서는 데이터 이전과 인증·주소 변경 비용이 이름 통일의 이점보다 크다.
- 선택 이유: 표시 이름부터 변경하고 접속·권한에 쓰이는 식별자는 유지하면 기존 서비스와 개발 환경을 보존할 수 있다.
- 리스크: GitHub 저장소 이름은 배포 인증 조건에 포함된다. 단순 웹 리다이렉트만 믿고 먼저 바꾸면 Actions 인증이 중단될 수 있다.

## 명칭 대응표

| 대상 | 사용할 명칭 | 실제 식별자·범위 | 상태 |
| --- | --- | --- | --- |
| 제품 | `BoDeul` / 보들 | Android 앱과 관리자 서비스 전체 | 유지 |
| 메인 GitHub 저장소 | `bodeul-platform` | Android, Core API, Functions, Rules, 공용 계약·문서 | `bodeul110/bodeul-platform`으로 변경 완료 |
| 관리자 GitHub 저장소 | `bodeul-admin-web` | 관리자 UI와 Next.js 관리자 서버 | 유지 |
| GitHub 작업 보드 | `BoDeul 작업 백로그` | 앱·웹의 공통 작업 관리 | 유지 |
| Google Cloud/Firebase 개발 | `bodeul-dev` | project ID `bodeul-dev` | 유지 |
| Google Cloud/Firebase 운영 | `bodeul-prod` | project ID `bodeul-prod-110` | 두 관리 API에서 변경·조회 확인 |
| 공용 Cloud Billing | `bodeul-billing` | 기존 공용 결제 계정, 연결·결제수단 유지 | 변경 완료, 두 프로젝트 결제 활성 확인 |
| Supabase 개발 DB | `bodeul-db-dev` | project ref `parpdzttloacinyvhwmx` | 변경 완료 |
| Supabase 운영 DB | `bodeul-db-prod` | project ref `aoijbzgozbopsxzrasbb` | 이름 변경 후 9월 27일 Healthy·Pro·V23 확인 |
| 개발 Core API | `bodeul-core-api-preview` | Cloud Run, `bodeul-dev` / `asia-northeast1` | 기존 서비스 ID 유지 |
| 운영 Core API | `bodeul-core-api` | `bodeul-prod-110` / `asia-northeast1` | 9월 27일 배포·기본 smoke 확인 |
| 관리자 웹·서버 | `bodeul-admin-web` | Vercel 프로젝트, Preview와 Production 환경 분리 | 유지 |
| Vercel 팀 | `BoDeul` | 기존 팀 ID와 `bodeul110` slug 유지 | 표시 이름 변경·팀 목록 재조회 확인 |

`preview`는 현재 개발 환경을 뜻하는 기존 리소스 식별자다. 이름 정리를 이유로 별도의 서버를 만들거나 운영 배포를 실행하지 않는다. 관리자 서버는 Next.js에 포함되어 있으므로 별도 `bodeul-admin-api` 서버가 배포된 것처럼 표기하지 않는다.

## 변경하지 않는 식별자

- Google Cloud project ID·number, 공식 조직 `bodeul.kr`과 조직 ID
- Firebase App ID, Android package/application ID, Auth domain, Storage bucket
- Supabase project ref, API/DB 접속 주소, PostgreSQL database `postgres`, schema와 role
- Cloud Run 서비스 ID·URL, Secret Manager secret ID, 서비스 계정 이메일, Artifact Registry 경로
- Vercel project/team ID, team slug와 기존 배포 도메인
- 로컬 작업 폴더·worktree 경로, GitHub 소유자 계정, 기본 브랜치 `master`
- 보존정책 격리 데이터의 기존 소유 표식 `bodeul110/Bodeul`: 이미 저장된 테스트 데이터를 식별하는 값이며 저장소 URL과 함께 바꾸지 않는다.

이 값들은 이름이 아니라 연결 계약이다. 변경할 때는 이번 표시 이름 정리와 별도로 마이그레이션·호환성·복귀 절차를 검증한다. 과거 보고서의 당시 명칭은 이력으로 보존한다.

## GitHub 저장소 이름 변경 순서

1. 저장소 ID `1209358990`, 소유자 ID `275679915`, 기본 브랜치·열린 PR·Actions 설정을 기록한다.
2. 개발·운영 WIF provider의 repository/workflow 조건과 서비스 계정 principal을 확인한다.
3. 같은 저장소 ID에 한정한 이전·새 이름 호환 조건을 준비한다. 브랜치·환경·workflow·event 제한을 제거하지 않는다.
4. workflow의 저장소 확인 조건, 운영 도구, 현재 문서 링크를 수정하고 테스트·PR 검증을 통과시킨다.
5. 저장소 관리자 계정으로 `Bodeul`을 `bodeul-platform`으로 변경한다. GitHub 소유자와 이슈·PR·커밋은 유지한다.
6. 원격 주소, 자체 실행기, 외부 연동을 확인하고 새 이름에서 개발 CI와 운영 읽기 점검의 WIF 인증을 검증한다.
7. 호환 기간이 끝나면 이전 이름 허용을 제거한다. 리다이렉트 유지를 위해 이전 저장소 이름을 재사용하지 않는다.

개인 개발 계정의 기존 쓰기 권한은 유지하고 저장소 소유자 세션에서 이름·설명을 변경했다. 로컬 `D:\BoDeul`과 연결된 worktree의 `origin`은 새 저장소 주소로 갱신하되 작업 폴더와 미커밋 변경은 유지한다.

운영 인증 설정 변경에는 공식 관리자에게 `bodeul-prod-110` 프로젝트 범위의 WIF 관리·서비스 계정 관리 역할을 최대 2시간 조건으로 부여했다. 실제 변경 대상은 운영 provider 4개와 배포·운영 서비스 계정 4개의 저장소 인증 조건뿐이다. 새 조건을 다시 조회한 직후 임시 역할 2개를 회수했으며 잔여 임시 바인딩은 0개로 확인했다. 개인 개발 계정의 상시 권한, 데이터 조회·비밀값 조회·키 생성 권한은 추가하지 않았다.

## 이름 변경 당시 확인 범위

아래는 9월 22일 명칭 변경 작업의 기록이다. 이후 운영 DB 재개·서버 배포와 브랜치 분리는 [9월 27일 환경 분리 기록](../reports/dev-production-separation-2026-09-27.md)을 따른다.

- Supabase 소유자 세션에서 개발·운영 프로젝트의 표시 이름을 저장했다. project ref·리전·운영 일시정지 상태는 유지한다.
- Google Cloud와 Firebase의 운영 표시 이름을 각각 갱신했다. project ID·number와 공식 조직 소속은 그대로다.
- 공용 결제 계정은 `bodeul-shared-billing1`에서 `bodeul-billing`로 표시 이름만 바꿨다. 두 프로젝트 모두 기존 계정 연결과 결제 활성 상태를 유지한다.
- Vercel 팀 표시 이름은 `bodeul110's projects`에서 `BoDeul`로 변경했다. 프로젝트 `bodeul-admin-web`, 팀 ID·slug·배포 주소는 유지한다.
- 메인 저장소 이름은 `Bodeul`에서 `bodeul-platform`으로 변경했다. 저장소 ID `1209358990`, 소유자, 기본 브랜치 `master`, 기존 PR은 유지한다.
- WIF provider 개발 2개·운영 4개와 운영 서비스 계정 subject 4개를 새 저장소 이름으로 전환하고 재조회했다. 이전 이름 호환 조건은 제거했으며 기존 브랜치·환경·workflow·event 제한은 유지한다. 개발 Core API provider에는 변경되지 않는 저장소 ID 제한도 추가했다.
- 임시 프로젝트 권한 2개는 설정 재조회 후 즉시 회수했다. 개인 계정의 기본 CLI 설정은 변경하지 않았다.
- 다른 업무 조직에 연결된 Supabase MCP는 이번 변경에 사용하지 않았다.
- 서버 재배포, 운영 DB 재개·migration, 데이터 변경, 상시 권한 확대, 결제 연결 변경은 이 작업에 포함하지 않는다.

## 로컬 검증

- Node 22.23.0에서 Functions 테스트: 86개 통과, 에뮬레이터 조건부 3개 생략, 실패 없음.
- 운영 인프라 감사 도구 테스트: 26개 통과. 표시 이름뿐 아니라 기존 project ID·number도 검증한다.
- Core API `check`: 성공.
- 변경한 workflow YAML 파싱, PowerShell 구문, JavaScript 구문과 `git diff --check`: 통과.
- 이름 변경 후 실제 Actions WIF 인증은 개발 preflight와 운영 읽기 점검으로 별도 확인한다. 설정 재조회·로컬 성공을 배포·데이터 검증으로 해석하지 않는다.

## 근거

- [GitHub 저장소 이름 변경](https://docs.github.com/en/repositories/creating-and-managing-repositories/renaming-a-repository): 관리자 권한과 리다이렉트 예외를 확인한다.
- [Firebase 프로젝트 식별자](https://firebase.google.com/docs/projects/learn-more#project-name): 표시 이름과 변경할 수 없는 project ID·number를 구분한다.
- [Cloud Billing 표시 이름 변경](https://docs.cloud.google.com/billing/docs/reference/rest/v1/billingAccounts/patch): `displayName`만 갱신하고 결제 연결은 유지한다.
