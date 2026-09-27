# 개발·운영 분리 실행 기록

기준일: 2026-09-27. 완료 여부는 실제 실행 증거만으로 구분한다.

| 항목 | 확인 결과 |
| --- | --- |
| Supabase 플랜과 프로젝트 | 기존 BoDeul 조직 Pro, 개발·운영 두 프로젝트 Healthy |
| 개발 DB | Flyway V23, 실패 이력 0 |
| 운영 DB 사전 점검 | [36306348087](https://github.com/bodeul110/bodeul-platform/actions/runs/36306348087) 성공. V15, 실패 이력 0, 가이드·세션 0건 |
| 운영 Environment | 플랫폼 5곳과 웹 Production을 `master` branch 전용으로 제한. 기존 승인자·우회 설정 유지 |
| 운영 백업 | [36306623420](https://github.com/bodeul110/bodeul-platform/actions/runs/36306623420): logical dump·격리 복원 성공, GCS 외부 보관은 WIF impersonation 403으로 실패. 사용 가능한 백업 증적 아님 |
| WIF 원인 | GitHub는 immutable subject 사용, 기존 GCP 서비스 계정 binding은 이전 이름 형식. 공식 관리자 재인증 후 수정·재검증 필요 |

## 진행 중

- `dev` 브랜치 보호, CI, 개발 배포·migration 전환
- WIF subject 복구와 운영 백업 재실행
- 운영 스키마·Realtime·관리자 DB role·Core API 배포
- 웹·Android 환경별 연결 검증

기존 로컬 변경과 팀원 기능 PR은 건드리지 않았다. 운영 데이터 migration·restore는 아직 실행하지 않았다. 실기기 검증은 요청에 따라 제외한다.
