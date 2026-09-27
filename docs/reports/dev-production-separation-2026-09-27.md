# 개발·운영 분리 실행 기록

기준일: 2026-09-27. 완료 여부는 실제 실행 증거만으로 구분한다.

| 항목 | 확인 결과 |
| --- | --- |
| Supabase 플랜과 프로젝트 | 기존 BoDeul 조직 Pro, 개발·운영 두 프로젝트 Healthy |
| 개발 DB | Flyway V23, 실패 이력 0 |
| 운영 DB 사전 점검 | [36306348087](https://github.com/bodeul110/bodeul-platform/actions/runs/36306348087) 성공. V15, 실패 이력 0, 가이드·세션 0건 |
| 운영 Environment | 플랫폼 5곳과 웹 Production을 `master` branch 전용으로 제한. 기존 승인자·우회 설정 유지 |
| 운영 백업 | 최초 [36306623420](https://github.com/bodeul110/bodeul-platform/actions/runs/36306623420)은 GCS 보관 403으로 실패. 인증 복구 후 [36309935196](https://github.com/bodeul110/bodeul-platform/actions/runs/36309935196)은 logical dump·격리 복원·GCS 외부 보관 모두 성공. 원본/복원 manifest 일치와 보관 객체·checksum 재조회 확인 |
| WIF 복구 | 개발 2개·운영 4개 기존 서비스 계정의 binding을 정확한 immutable subject로 교체하고 전체 정책 read-back 확인. 이름 wildcard·새 키 없이 기존 GitHub 인증 방식 유지 |
| 임시 권한 회수 | 사용자 승인으로 두 프로젝트에 최대 2시간 조건부 서비스 계정 관리 역할 부여. 수정 직후 회수하고 프로젝트 IAM binding이 작업 전과 동일함을 비교 확인. 개인 CLI 계정·기본 프로젝트 유지 |
| 운영 인프라 감사 | [36310156875](https://github.com/bodeul110/bodeul-platform/actions/runs/36310156875)의 contract·baseline-drift 성공. 인증 및 현재 단계 기준을 읽기 전용으로 검증. 기존 9월 22일 미승인 감사 실행은 취소하고 최신 master로 대체 |
| 운영 DB 갱신 | [36310121342](https://github.com/bodeul110/bodeul-platform/actions/runs/36310121342) 성공. master `f4b69e5`에서 V16~V23 8개 적용, 계정 삭제 영향도 계약 검증 통과. SQL 재조회 V23·실패 0건·예약/세션/가이드 각각 0건 |
| 운영 Realtime | 사용자 승인 후 기존 bootstrap `003`, `005`, `006`과 production Firebase 허용 목록을 한 트랜잭션으로 적용. 먼저 같은 SQL의 rollback 검증 성공, 이후 commit 및 새 쿼리로 재조회 |
| 운영 Realtime 검증 | 허용 Firebase는 `bodeul-prod-110` 한 개. authenticated role에서 잘못된 JSON claims·개발 프로젝트·다른 issuer·존재하지 않는 세션 참여자 거부. 종료 매니저 제한과 보호자 Broadcast 제외 함수 정의, 익명 helper 실행 및 업무 schema·허용 목록 직접 조회 차단 확인 |
| 운영 보안 진단 | Supabase Security Advisor 재실행 후 Error 0, Warning 0, Info 6. 참고 항목은 관리자 권한·감사·결제 원장 테이블의 `RLS Enabled No Policy`이며, 직접 접근을 거부하고 서버 함수만 허용하는 기존 계약을 유지 |
| 플랫폼 기반 PR | [#451](https://github.com/bodeul110/bodeul-platform/pull/451) 병합, `f4b69e5`. preflight·Core API CI·migration 계약·CodeQL 성공 |
| 관리자 웹 기반 PR | [#73](https://github.com/bodeul110/bodeul-admin-web/pull/73) 병합, `8887570`. lint/build·CodeQL·Vercel Preview 성공 |
| 장기 브랜치 보호 | 두 저장소 `dev` 생성. master와 동일한 PR·필수 CI·삭제/force push 금지 규칙 적용. 출시·동기화용 merge commit 허용 |
| 개발 Environment | `core-api-preview`, `core-api-migration-preview`, `dev`는 정확한 `dev` branch만 허용. migration DB ref 확인값 등록 |
| 개발 WIF provider | Core 배포와 Firebase preflight provider를 repository/owner ID·정확한 dev workflow/ref/event로 제한. 서비스 계정 binding도 각각 정확한 Environment 또는 dev ref immutable subject로 축소 완료 |
| 개발 Core API 배포 | `dev`에서 실행한 [36308064890](https://github.com/bodeul110/bodeul-platform/actions/runs/36308064890) 성공. `bodeul-core-api-preview-00022-6w4`에 트래픽 100%. `/health` 200 `UP`, 무인증 `/v1/me` 401 |
| Android 환경 검사 | [#452](https://github.com/bodeul110/bodeul-platform/pull/452)를 `dev`에 병합, `c251cbe`. Debug 빌드·단위 테스트·Release App Check 의존성 검사 성공. 양방향 API/Realtime 설정 혼합 거부 4건 및 운영 Firebase 파일 누락 거부 확인. 병합 후 preflight·CodeQL 성공 |
| 관리자 웹 환경 검사 | [#74](https://github.com/bodeul110/bodeul-admin-web/pull/74)를 `dev`에 병합, `a1801e9`. 서버 테스트 153건·lint·Next.js 빌드·무인증 런타임 9건 성공. 병합 후 Build·CodeQL 성공 |
| 관리자 웹 개발 배포 | `a1801e9`의 Vercel Preview가 `READY`, Functions 리전 `hnd1`. [dev 고정 주소](https://bodeul-admin-web-git-dev-bodeul110.vercel.app) 연결 확인. Production으로 승격하지 않음 |
| 개발 Realtime | 기존 `006_companion_completion_realtime_authorization.sql`의 동행 종료 권한 제한 적용. 개발 Firebase 허용 목록과 V18 이상 스키마를 확인한 뒤 함수만 변경. 업무 데이터 쓰기 없음 |
| 개발 Realtime 검증 | 허용 Firebase는 `bodeul-dev` 한 개. 종료 조건과 보호자 직접 접근 제외를 read-back 확인. 잘못된 JWT·운영 프로젝트·다른 issuer 거부. helper는 authenticated만 실행 가능하고 업무 schema·허용 목록 직접 읽기는 계속 차단 |
| 운영 배포 사전 점검 | `core-api-production`의 GCP 리전·프로젝트·배포/실행 계정·WIF·DB Secret version 확인. Kakao 등록 후 `KAKAO_LOCAL_REST_API_KEY_SECRET_VERSION=1` 추가 및 재조회. 필수 Secret 4개 모두 숫자 version 1 사용 |
| Kakao 키 등록 | 사용자 승인에 따라 개발 서비스가 참조하는 REST 키의 version 1을 운영 전용 Secret에 메모리로만 전달. 기존 등록 스크립트의 프로젝트·허용 목록 검사를 거쳐 운영 version 1 `ENABLED` 확인. 새 키 발급·키 값 출력·파일 저장 없음 |
| Kakao 임시 권한 회수 | 공식 관리자의 읽기 권한을 개발 Kakao Secret version 1과 최대 10분으로 제한. 등록 후 즉시 회수하고 작업 전후 프로젝트 IAM binding 동일 확인. GitHub에는 비밀값이 아닌 숫자 version만 등록 |
| 최초 운영 Core API 배포 | [#457](https://github.com/bodeul110/bodeul-platform/pull/457)의 dev → master merge commit `493cacc056d5359d4b20bc1528f47082b6bb13b4`에서 기존 수동 workflow·Environment 승인을 거쳐 배포. [36312471495](https://github.com/bodeul110/bodeul-platform/actions/runs/36312471495)는 서비스 생성까지 성공했지만 초기 비공개 설정 때문에 무인증 smoke가 403으로 실패 |
| 최초 공개 호출 설정 | 먼저 IAM 인증 헤더를 별도로 전달해 비공개 서비스의 `/health` 200 `UP`과 업무 API의 Firebase 무인증 401을 확인. 이후 해당 서비스만 Invoker IAM 검사를 해제. 조직 도메인 제한·기존 서비스 IAM·배포 계정 역할은 유지하고 `allUsers` binding이나 상시 권한을 추가하지 않음 |
| 운영 Core API 재배포 | 같은 master SHA로 [36312927138](https://github.com/bodeul110/bodeul-platform/actions/runs/36312927138) 성공. 정상 revision `bodeul-core-api-00002-2s6`, 최신 revision에 트래픽 100%. 앱 배포에 DB migration을 포함하지 않음 |
| 운영 Core API HTTP 검증 | `https://bodeul-core-api-s4vqtcl6ka-an.a.run.app`의 `/health` 200 `UP`, 무인증 `/api/auth/me`·`/api/places/search` 401 `missing_authorization`. 실제 Kakao 검색 성공이나 정상 Firebase 로그인까지 검증한 것으로 보지 않음 |
| 운영 감사 도구 보완 | 배포 workflow의 세션 플래그·공개 설정·ProtoJSON의 false 기본값 생략을 감사 계약에 반영. 미완료 revision, 환경변수 누락/중복/추가, 타 프로젝트 Secret 참조 거부 포함 단위 테스트 33건 통과 |
| 운영 Core API 설정 재조회 | Cloud Run v2와 Secret version 메타데이터를 읽어 서비스 준비 상태·운영 설정·공개 호출 3개 검사 모두 PASS. 실제 서버 생성에 맞춰 `cloudRun=present`로 기준 변경. 전체 인프라 감사의 GitHub 실행과는 구분 |
| 배포 후 전체 운영 감사 | [#459](https://github.com/bodeul110/bodeul-platform/pull/459)의 master `e2d2a2f`에서 실행한 [36313836486](https://github.com/bodeul110/bodeul-platform/actions/runs/36313836486) 성공. contract·baseline-drift 모두 통과. App Check 준비와 Storage UBLA는 여전히 별도 출시 게이트 |
| 운영 관리자 DB 로그인 | 사용자 승인과 직접 SQL 실행으로 기존 `bodeul_admin_service`를 LOGIN으로 전환. 새 운영 전용 비밀번호를 생성하고 개발 비밀번호는 재사용하지 않음. TLS 인증서 검증·실제 로그인·가이드 테이블 조회 검증 성공 |
| 운영 관리자 DB 최소 권한 | 별도 SQL 재조회에서 연결 제한 5, 기존 `bodeul_admin_runtime` 상속, superuser·CREATEDB·CREATEROLE·REPLICATION·BYPASSRLS 모두 false, 업무 테이블 직접 쓰기 권한 0건 확인. 기존 허용 업무 함수 권한은 유지 |
| 운영 관리자 DB 비밀값 | 11:16 UTC에 Vercel Production에만 `ADMIN_DATABASE_URL`을 sensitive 형식으로 등록. 메타데이터 재조회 완료. 비밀번호·DB URL 원문은 출력·파일 저장·커밋하지 않았고 Preview 설정은 변경하지 않음 |
| 관리자 웹 운영 출시 | [웹 #75](https://github.com/bodeul110/bodeul-admin-web/pull/75)를 dev → master merge commit `559d950d612959f338c714bc61cbd5d0d13262dd`로 병합. 필수 CI 통과 후 Production 환경으로 다시 빌드. [Vercel 배포](https://vercel.com/bodeul110/bodeul-admin-web/3pxNQh2TaALMbAABzgTprj9fLDqs) `READY`, Functions `hnd1`, 기존 운영 주소가 새 배포를 가리킴 |
| 관리자 웹 운영 HTTP 검증 | [운영 웹](https://bodeul-admin-web-iota.vercel.app/)의 로그인 화면에서 `운영 환경 / 운영 배포 · Production` 확인. access-context·가이드·결제 조회 GET에 무인증·잘못된 인증 형식·가짜 Firebase token을 보내 9건 모두 401·JSON·no-store·입력 token 비노출 확인. 인증된 업무 요청이나 실제 쓰기는 수행하지 않음 |
| 업무 관리자 등록 상태 | 운영 DB의 `app_users` 중 ADMIN 0건, 활성 세부 관리자 역할 0건. DB 서비스 로그인 활성화와 사람의 업무 권한 부여는 별개이며 이번 작업에서 사람 계정 권한은 추가하지 않음 |

## 운영 적용 근거

- 작업 목적: 개발·운영의 인증·DB 경계를 맞추고 운영 migration의 복구 가능성을 확보한다.
- 선택한 방식: 기존 서비스 계정 binding만 수정하고 임시 권한을 즉시 회수한다. 외부 보관까지 검증된 백업 후 기존 수동 workflow로 migration을 실행한다.
- 대안: GitHub immutable subject를 끄거나 이름 wildcard로 넓히는 방법은 검토했으나 저장소 식별과 환경 제한을 약화하므로 사용하지 않았다.
- 선택 이유: 현재 MVP 규모에서는 새 인프라나 상시 관리자 권한을 늘리지 않고 기존 자동화와 검증된 migration을 유지하는 편이 운영 부담이 작다.
- 리스크: SQL 권한 검사와 workflow 성공은 실제 로그인·Realtime 소켓·운영 서버 업무 흐름 전체의 성공을 의미하지 않는다. 남은 연결 검증 전에는 분리 작업 전체를 완료로 표시하지 않는다.

### 복원 증적

운영 migration은 아래 V15 백업의 외부 보관 성공을 확인한 뒤 실행했다. 운영 DB에 복원하지 않고 GitHub 실행기의 격리 PostgreSQL에서 복원했다.

- 실행: [운영 백업 및 격리 복원](https://github.com/bodeul110/bodeul-platform/actions/runs/36309935196)
- 객체: `gs://bodeul-prod-110-db-backups/postgres/verified/2026/09/27/20260927T093755Z-bodeul-production/20260927T093755Z-bodeul-production.dump`
- SHA-256: `55dba2ed2f4c98ba913faea915333ce1f3794d812e7901847eff5b30f37ac15a`
- 크기: 167,123 bytes. 원본/복원 manifest 일치. GCS 객체와 별도 checksum 파일 재조회 완료.

Realtime 검사에는 SQL 세션의 claims를 사용했다. 실제 서명된 Firebase 토큰의 교차 환경 검증, 정상 참여자의 소켓 구독 및 메시지 수신은 별도 실연결 검증 범위다. 실제 환자 데이터나 테스트 예약은 생성하지 않았다.

### 최초 서버 연결 판단

- 작업 목적: 운영 전용 DB와 인증 설정으로 Core API를 기동하고 요청의 서버 인증 경계를 확인한다.
- 선택한 방식: 검증된 master SHA를 수동 workflow로 배포한다. 최초 서비스는 IAM 인증으로 먼저 점검하고, 조직 정책을 유지한 채 서비스 단위 공개 호출만 활성화한다.
- 대안: 조직의 도메인 제한 해제나 배포 계정의 상시 Run 관리자 권한 추가는 필요하지 않아 제외했다.
- 선택 이유: 현재 MVP 규모에서는 기존 Spring Firebase 인증을 유지하면서 최초 공개 설정만 분리하는 편이 권한 범위와 운영 부담이 작다.
- 리스크: 공개 health와 무인증 거부는 정상 사용자 로그인·권한별 업무·Kakao 응답 검증을 대체하지 않는다. Kakao 키는 환경별 Secret에 보관하되 쿼터와 폐기 영향은 공유한다.

Cloud Run API의 `reconciling`은 boolean이고 ProtoJSON은 false 기본값을 생략할 수 있다. 감사 도구는 생략과 명시적 false만 허용하면서 성공 상태, 최신 revision 일치, 유효한 generation 일치를 계속 요구한다. [Cloud Run API](https://docs.cloud.google.com/run/docs/reference/rest/v2/projects.locations.services), [ProtoJSON 기본값](https://protobuf.dev/programming-guides/json/#presence-and-default-values).

### 관리자 서버 연결 판단

- 작업 목적: 운영 관리자 서버가 개발 DB 자격 증명 없이 같은 운영 PostgreSQL에 독립적으로 연결하도록 준비한다.
- 선택한 방식: 기존 관리자 전용 role의 로그인만 활성화하고, TLS와 직접 쓰기 차단을 확인한 뒤 Vercel Production에 새 자격 증명을 등록한다.
- 대안: 개발 비밀번호 공유, postgres 계정 사용, Core API를 통한 관리자 proxy는 환경 혼합과 권한 확대를 유발하므로 제외했다.
- 선택 이유: 현재 MVP 규모에서는 기존 Next.js 서버·DB role을 유지하고 환경별 자격 증명을 분리하는 편이 추가 서버와 운영 부담 없이 멘토가 제안한 서버 경계를 지킨다.
- 리스크: 로컬에서의 실제 DB 연결과 운영 빌드·무인증 API 검증만으로 인증된 관리자 업무를 보장할 수 없다. 운영 관리자 역할·MFA·App Check와 정상 업무 요청은 별도로 검증한다.

## 남은 범위

- 운영 Firebase 계정과 PostgreSQL ADMIN·세부 관리자 역할을 연결하고, 실제 로그인 후 Vercel 서버에서 DB 업무 조회가 성공하는지 검증한다. 현재 업무 관리자 등록은 0건이며 서비스 계정 권한을 사람의 관리자 권한으로 간주하지 않는다.
- 운영 Core API의 실제 서명된 Firebase 토큰, 개발 토큰 거부 및 인증 후 Kakao 검색을 검증한다. 공유 쿼터와 키 폐기 영향은 [키 관리 결정](../architecture/kakao-local-core-api.md#개발운영-키-관리)을 따른다.
- Android Release 설정, 환경 간 정상 토큰 거부와 실제 Realtime 소켓을 검증한다. 웹 Preview의 일반 HTTP 접근은 Vercel 로그인으로 전환되므로, 이를 앱 API의 200 성공으로 계산하지 않았다.

개발·운영의 브랜치·배포 경계, 앱/웹 연결 검사, 자동화 인증 복구, 양쪽 DB V23 및 Realtime 인가 설정, 운영 Core API 배포와 관리자 웹 Production 출시·DB 자격 증명 등록까지 반영했다. 관리자 웹의 인증된 DB 업무 요청과 정상 로그인 후 환경 간 경계 검증은 아직 완료하지 않았다. 기존 로컬 변경과 팀원 기능 PR은 건드리지 않았으며 운영 DB 복원이나 개발 데이터 복사는 하지 않았다. 실기기 검증은 요청에 따라 제외한다.
