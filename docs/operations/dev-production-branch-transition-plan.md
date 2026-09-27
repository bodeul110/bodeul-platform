# 개발·운영 환경 분리

기준일: 2026-09-27. 이 문서는 전환 기준과 실행 순서다. 실제 적용 결과는 별도 [실행 기록](../reports/dev-production-separation-2026-09-27.md)과 구분한다.

## 환경 대응

| 구분 | 개발 | 운영 |
| --- | --- | --- |
| Git 브랜치 | `dev` | `master` |
| Google Cloud / Firebase | `bodeul-dev` | `bodeul-prod-110` |
| Core API | `bodeul-core-api-preview` | `bodeul-core-api` |
| Supabase | `bodeul-db-dev` (`parpdzttloacinyvhwmx`) | `bodeul-db-prod` (`aoijbzgozbopsxzrasbb`) |
| 관리자 웹 | Vercel Preview, `dev` 고정 주소 | Vercel Production, `master` |
| Android | debug, 개발 인증·API·Storage·Realtime | release, 운영 인증·API·Storage·Realtime |

같은 환경 안에서는 관리자 서버와 Core API가 같은 DB를 보되 별도 최소 권한 role을 쓴다. 개발과 운영 사이에는 DB·인증·파일·자격 증명을 공유하지 않는다. Supabase 콘솔의 `main`/`Production` 표시는 각 Supabase 프로젝트 내부 브랜치이며 보들의 개발·운영 구분과 다르다.

## 브랜치 흐름

1. 기능 브랜치는 `dev`에서 만들고 `dev` 대상 PR로 통합한다. 기본은 squash merge다.
2. 출시할 묶음은 `dev → master` PR로 올리고 merge commit으로 반영한다. 장기 브랜치의 공통 이력을 유지하기 위해 이 PR은 squash하지 않는다.
3. 긴급 수정이 `master`에 먼저 반영되면 `master → dev` PR을 merge commit으로 동기화한다.

두 저장소의 기본 브랜치는 `master`를 유지한다. 기존 팀원 PR의 base와 작업 내용은 자동 변경하지 않는다. `dev`와 `master` 모두 PR, 필수 CI, 미해결 리뷰 해소, 삭제·강제 push 금지를 적용한다. 일반 Dependabot 버전 PR은 `dev`를 대상으로 하며 보안 업데이트는 기본 브랜치 대상일 수 있다.

## 배포와 DB 변경

- 개발 Core API는 `dev` push 또는 `dev` 수동 실행에서 배포한다. 단위 테스트, Firestore Emulator, migration 계약, 컨테이너 빌드 검증 뒤에만 배포 권한을 사용한다.
- 개발 배포 실패 시 직전 100% 트래픽 revision으로 복구한다. 운영 배포는 기존 `master` SHA·서비스명 확인과 Environment 승인을 유지한다.
- DB 변경은 자동 배포에 포함하지 않는다. 개발 migration은 `dev`, 운영 migration은 `master`에서 수동 실행하며 각각 DB project ref를 재입력한다.
- 운영 migration 전에는 logical backup의 checksum과 격리 복원 검증, 외부 보관을 모두 완료한다. 실패한 백업 workflow를 복원 증적으로 사용하지 않는다.
- 새 스키마가 필요한 코드는 개발 DB에서 먼저 적용·rollback을 검증한다. 운영에서는 호환 가능한 스키마 변경 후 앱을 배포하며 DB rollback과 앱 rollback을 동일하게 취급하지 않는다.
- 운영 Environment는 보호 브랜치 전체가 아니라 정확히 `master`만 허용한다. 개발 Environment는 정확히 `dev`만 허용한다.
- WIF는 repository/owner ID, 정확한 workflow·ref·Environment·event를 검증한다. GitHub OIDC의 immutable subject를 실제 설정값과 일치시키며 이름 기반 wildcard로 완화하지 않는다.

## 적용 순서

1. 현재 환경·권한·배포·DB 상태를 읽고 복구 기준을 남긴다.
2. 운영 Environment를 `master`로 제한하고 `dev` CI와 보호 규칙을 준비한다.
3. WIF 인증을 검증하고 개발 배포 및 수동 migration을 `dev`로 전환한다.
4. 개발·운영 Realtime bootstrap과 Firebase 허용 목록을 각각 검증한다.
5. 검증된 운영 백업을 만든 뒤 필요한 Flyway migration을 적용한다.
6. 운영 Core API와 관리자 서버의 전용 DB role·비밀값·환경 연결을 확인하고 배포한다.
7. Android 환경 혼합 방지 검사를 추가한다. 개발·운영의 health, 무인증 거부, 환경 간 토큰 거부를 확인한다.
8. 실제 결과와 미완료 항목을 기록하고 협업 기준을 갱신한다. 실기기 검증은 이번 작업에서 제외한다.

## 범위 제외

새 DB나 서버 사업자 도입, 결제 플랜 변경, 개발 데이터의 운영 복사, 실제 서비스 공개일 확정, 실제 환자 데이터 생성, 기능 PR 병합은 이번 환경 전환에 포함하지 않는다. 운영용 계정·서버 연결 완료는 실제 서비스 출시 승인과 다르다.

## 참고

- [GitHub OIDC subject](https://docs.github.com/en/actions/reference/security/oidc#immutable-subject-claims)
- [Google Cloud 접근 관리](google-cloud-access-governance.md)
