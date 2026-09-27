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
| Android 환경 검사 | Debug 빌드·단위 테스트·Release App Check 의존성 검사 성공. 양방향 API/Realtime 설정 혼합 거부 4건 및 운영 Firebase 파일 누락 거부 확인 |
| 관리자 웹 환경 검사 | 서버 테스트 152건·Next.js 빌드·무인증 런타임 9건 성공. 실제 Preview/Production 적용은 후속 PR과 배포 검증 필요 |

## 진행 중

- 새 `dev` 경로의 Core API 실제 배포 및 회귀 검사 PR 검증
- WIF subject 복구와 운영 백업 재실행
- 운영 스키마·Realtime·관리자 DB role·Core API 배포
- 웹·Android 환경별 연결 검증

기존 로컬 변경과 팀원 기능 PR은 건드리지 않았다. 운영 데이터 migration·restore는 아직 실행하지 않았다. 실기기 검증은 요청에 따라 제외한다.
