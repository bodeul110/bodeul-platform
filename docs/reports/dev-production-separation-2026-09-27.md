# 개발·운영 분리 실행 기록

기준일: 2026-09-27. 완료 여부는 실제 실행 증거만으로 구분한다.

| 항목 | 확인 결과 |
| --- | --- |
| Supabase 플랜과 프로젝트 | 기존 BoDeul 조직 Pro, 개발·운영 두 프로젝트 Healthy |
| 개발 DB | Flyway V23, 실패 이력 0 |
| 운영 DB 사전 점검 | [36306348087](https://github.com/bodeul110/bodeul-platform/actions/runs/36306348087) 성공. V15, 실패 이력 0, 가이드·세션 0건 |
| 운영 Environment | 플랫폼 5곳과 웹 Production을 `master` branch 전용으로 제한. 기존 승인자·우회 설정 유지 |
| 운영 백업 | [36306623420](https://github.com/bodeul110/bodeul-platform/actions/runs/36306623420): logical dump·격리 복원 성공, GCS 외부 보관은 WIF impersonation 403으로 실패. 사용 가능한 백업 증적 아님 |
| WIF 원인 | GitHub는 immutable subject 사용, 운영 GCP 서비스 계정 binding은 이전 이름 형식. 공식 관리자 재인증 완료. 서비스 계정 IAM 수정 권한이 없어 한시적 권한 승인 대기 |
| 플랫폼 기반 PR | [#451](https://github.com/bodeul110/bodeul-platform/pull/451) 병합, `f4b69e5`. preflight·Core API CI·migration 계약·CodeQL 성공 |
| 관리자 웹 기반 PR | [#73](https://github.com/bodeul110/bodeul-admin-web/pull/73) 병합, `8887570`. lint/build·CodeQL·Vercel Preview 성공 |
| 장기 브랜치 보호 | 두 저장소 `dev` 생성. master와 동일한 PR·필수 CI·삭제/force push 금지 규칙 적용. 출시·동기화용 merge commit 허용 |
| 개발 Environment | `core-api-preview`, `core-api-migration-preview`, `dev`는 정확한 `dev` branch만 허용. migration DB ref 확인값 등록 |
| 개발 WIF provider | Core 배포와 Firebase preflight provider를 repository/owner ID·정확한 dev workflow/ref/event로 제한. 서비스 계정 binding 축소는 승인 후 진행 |
| 개발 Core API 배포 | `dev`에서 실행한 [36308064890](https://github.com/bodeul110/bodeul-platform/actions/runs/36308064890) 성공. `bodeul-core-api-preview-00022-6w4`에 트래픽 100%. `/health` 200 `UP`, 무인증 `/v1/me` 401 |
| Android 환경 검사 | [#452](https://github.com/bodeul110/bodeul-platform/pull/452)를 `dev`에 병합, `c251cbe`. Debug 빌드·단위 테스트·Release App Check 의존성 검사 성공. 양방향 API/Realtime 설정 혼합 거부 4건 및 운영 Firebase 파일 누락 거부 확인. 병합 후 preflight·CodeQL 성공 |
| 관리자 웹 환경 검사 | [#74](https://github.com/bodeul110/bodeul-admin-web/pull/74)를 `dev`에 병합, `a1801e9`. 서버 테스트 153건·lint·Next.js 빌드·무인증 런타임 9건 성공. 병합 후 Build·CodeQL 성공 |
| 관리자 웹 개발 배포 | `a1801e9`의 Vercel Preview가 `READY`, Functions 리전 `hnd1`. [dev 고정 주소](https://bodeul-admin-web-git-dev-bodeul110.vercel.app) 연결 확인. Production으로 승격하지 않음 |
| 개발 Realtime | 기존 `006_companion_completion_realtime_authorization.sql`의 동행 종료 권한 제한 적용. 개발 Firebase 허용 목록과 V18 이상 스키마를 확인한 뒤 함수만 변경. 업무 데이터 쓰기 없음 |
| 개발 Realtime 검증 | 허용 Firebase는 `bodeul-dev` 한 개. 종료 조건과 보호자 직접 접근 제외를 read-back 확인. 잘못된 JWT·운영 프로젝트·다른 issuer 거부. helper는 authenticated만 실행 가능하고 업무 schema·허용 목록 직접 읽기는 계속 차단 |

## 남은 범위

- 공식 관리자에게 한시적 서비스 계정 관리 역할을 부여하는 승인 후, 기존 자동화 계정 6개의 정확한 immutable OIDC subject를 수정하고 권한을 즉시 회수한다. 개인 CLI 계정은 변경하지 않는다.
- WIF 수정 후 운영 백업을 재실행해 외부 보관까지 성공한 증적을 만든다. 그 전에는 운영 migration을 실행하지 않는다.
- 운영 DB의 V16~V23 migration과 운영 Realtime bootstrap을 적용·검증한다. 현재 운영은 V15다.
- 운영 관리자 DB 로그인과 Production `ADMIN_DATABASE_URL`을 준비한다. 현재 관리자 DB role은 `NOLOGIN`, 해당 Vercel 환경변수는 미등록이다.
- 운영 Core API의 Kakao REST 비밀값 등 필수 설정을 준비하고 수동 배포 workflow로 배포한다. 현재 운영 Kakao Secret Manager 항목에는 사용 가능한 version이 없다. 개발 키를 임의 복사하지 않았다.
- 웹 변경의 `dev → master` 출시와 실제 관리자 인증·DB 업무 연결, Android Release 설정 및 환경 간 정상 토큰 거부를 검증한다. 웹 Preview의 일반 HTTP 접근은 Vercel 로그인으로 전환되므로, 이를 앱 API의 200 성공으로 계산하지 않았다.

개발·운영의 브랜치·배포 경계와 앱/웹 연결 검사까지 반영했지만 운영 업무 연결까지 완료한 상태는 아니다. 기존 로컬 변경과 팀원 기능 PR은 건드리지 않았다. 운영 DB에 migration·restore를 실행하지 않았고, 백업 workflow의 격리 복원만 수행했다. 실기기 검증은 요청에 따라 제외한다.
