# Google Cloud 공식 조직 이전

조직 이전 기록: 2026-09-22. 후속 상태 안내: 2026-09-27

조직 이전 이후 운영 DB 재개·V23·Core API 배포와 최초 관리자 로그인까지 진행했다. 이 문서의 이전 당시 리소스 개수와 검증 범위는 역사 기록이며, 현재 운영 상태는 [9월 27일 환경 분리 기록](../reports/dev-production-separation-2026-09-27.md)을 따른다.

## 판단과 범위

| 항목 | 결정 |
| --- | --- |
| 작업 목적 | 임시 개인 조직에 남아 있는 개발·운영 프로젝트를 공식 `bodeul.kr` 관리 경계로 옮긴다. |
| 선택한 방식 | 기존 프로젝트의 parent만 변경한다. 개발을 먼저 확인한 뒤 production을 진행한다. |
| 대안 | 프로젝트 재생성 또는 임시 조직의 관리자만 바꾸는 방법을 검토했다. |
| 선택 이유 | 현재 MVP에서는 Auth·데이터·프로젝트 번호·서비스 계정·WIF를 유지하는 것이 재생성보다 영향이 작다. 관리자 추가만으로는 공식 조직의 정책 상속으로 전환되지 않는다. |
| 리스크 | 상위 IAM·조직 정책·할당량이 달라진다. 이전 허용 정책의 전파 지연과 공개 API·개인 개발 계정·자동화 접근을 별도로 확인한다. |

이 작업은 조직 소속 변경이다. 앱 배포, DB migration, 운영 DB 재개, 결제 수단 등록, 기존 조직 삭제를 포함하지 않는다. 관리자 웹의 Vercel·Supabase 소유권도 바꾸지 않는다.

## 대상

| 구분 | 이름 | 식별자 |
| --- | --- | --- |
| 기존 조직 | `bodeul326-org` | `766471701894` |
| 공식 조직 | `bodeul.kr` | `1038381475908` |
| 개발 프로젝트 | `bodeul-dev` | `533563500316` |
| 운영 프로젝트 | `bodeul-prod-110` | `649312328770` |
| 공용 결제 | `bodeul-shared-billing1` | 공식 조직 소속, 두 프로젝트 연결 유지 |

사전 점검에서 두 프로젝트의 parent는 기존 조직이었다. 2026-09-22 이전 후 두 계정으로 재조회한 parent는 모두 공식 조직 `1038381475908`이며 `ACTIVE` 상태다.

## 사전 점검

- 공식 관리자 재인증 후 관리자 그룹의 두 구성원과 공식·개인 계정의 두 조직 및 프로젝트 접근을 확인한다.
- IAM, 서비스 계정, WIF 조건, Secret 메타데이터·IAM, Cloud Run revision·traffic·IAM, 조직 정책, 결제 연결을 비공개 경로에 저장한다. 비밀값과 업무 데이터는 읽거나 공개하지 않는다.
- 출발·도착 조직의 사용자 정의 역할과 상위 방화벽 연결을 확인한다. 목록 조회 실패를 빈 목록으로 취급하지 않는다.
- Analyze Move의 경고·차단·분석 오류를 구분한다. 이 API는 도착 조직의 모든 조건을 검사하지 않으므로 도착지 정책을 따로 대조한다.
- 개발 API의 `/health` 200·`UP`, 무인증 `/api/auth/me`와 `/api/places/search`의 401·`missing_authorization`을 기록한다.
- 개발·운영 프로젝트의 결제 활성 상태와 프로젝트 번호를 고정 확인한다.

## 임시 권한과 정책

새 사용자를 추가하지 않고 검증된 `gcp-admins@bodeul.kr` 그룹에만 2시간 만료 조건을 사용한다. 종료 시 만료를 기다리지 않고 이번 조건부 binding만 제거한다. 기존 IAM 전체를 과거 스냅샷으로 덮어쓰지 않는다.

| 위치 | 임시 역할 | 목적 |
| --- | --- | --- |
| 두 조직 | Organization Policy Administrator | 이전·복귀 허용 정책 설정과 회수 |
| 두 조직 | Compute Viewer, Organization Role Viewer, IAM Deny Reviewer | 조직 정책 영향과 이전 분석의 조회 공백 해소 |
| 공식 조직 | Project Creator, Project Mover | 공식 조직으로 반입 및 문제 발생 시 기존 조직으로 복귀 |

기존 조직에는 Project Creator·Project Mover가 이미 있다. 프로젝트의 기존 Project IAM Admin 및 update 권한도 확인한다. 관리자 역할과 이동 권한은 동일한 실행 계정에서 실제로 유효해야 한다.

| 조직 | 정책 | 허용값 |
| --- | --- | --- |
| 기존 조직 | `resourcemanager.allowedExportDestinations` | `under:organizations/1038381475908` |
| 공식 조직 | `resourcemanager.allowedImportSources` | `under:organizations/766471701894` |
| 공식 조직 | `resourcemanager.allowedExportDestinations` | `under:organizations/766471701894` |
| 기존 조직 | `resourcemanager.allowedImportSources` | `under:organizations/1038381475908` |

뒤의 두 정책은 복귀용이다. 제3의 조직이나 전체 조직으로 이동을 허용하지 않는다. 기존 정책이 있으면 중단하고 검토하며, 새 정책은 조회한 etag를 사용해 설정한다. 최종 검증 후 이번에 만든 네 정책만 etag 대조 후 제거한다.

## 실행과 확인

1. 두 방향의 권한과 이전 정책을 확인한다. 정책 조회 성공과 실제 이전 허용 전파 완료는 다를 수 있다.
2. `bodeul-dev`의 기존 project 객체를 읽어 Resource Manager v1 update에서 parent만 공식 조직으로 변경한다.
3. parent·프로젝트 번호·공용 결제 연결, 직접 IAM, 서비스 계정·WIF 조건, Cloud Run revision·traffic·공개 호출을 이전 전과 비교한다.
4. GitHub WIF 인증과 필요한 읽기 점검을 확인한다. 로컬 관리자 조회만으로 CI 인증 성공을 대신하지 않는다.
5. 개발 검증 후에만 `bodeul-prod-110`에 같은 절차를 적용한다. 조직·설정 이전 검증과 운영 업무 기능 검증을 구분한다.
6. 프로젝트 설정·관리 접근을 확인한 뒤 임시 이전 정책과 조건부 역할을 회수하고 두 관리 계정의 접근을 다시 확인한다. 읽기 전용 자동화의 승인 대기 때문에 이전용 권한을 계속 유지하지 않는다.

공식 조직의 도메인 제한과 서비스 계정 키 금지 정책은 무조건 해제하지 않는다. 기존 공개 Cloud Run IAM과 신규 공개 서비스 권한 부여는 다르다. 신규 운영 서비스 배포 때에는 공식 조직의 도메인 제한 아래 공개 접근 방식을 별도 검증한다. 개인 개발자는 공식 그룹 경유 권한을 유지하며, 개인 Gmail 직접 권한을 새로 추가할 때에는 도메인 제한을 먼저 확인한다.

## 복구

- 이동 뒤 API·자동화·관리자 접근이 이전 전보다 악화되면 production 이동을 중단한다.
- 복귀 권한과 정책이 살아 있는 동안 동일 프로젝트 parent를 `766471701894`로 되돌린다. 자동 rollback이 제공된다고 가정하지 않는다.
- 복귀 후 프로젝트 번호·결제·IAM·서비스 revision·API 경계를 다시 비교한다. 새 프로젝트를 만들거나 데이터를 복원하는 방식으로 대체하지 않는다.
- 임시 권한·정책을 이미 회수했다면 승인된 관리 계정으로 필요한 이전·복귀 권한과 두 조직 사이의 정책만 다시 준비한다.
- 기존 조직은 즉시 삭제하지 않는다. 남은 프로젝트·폴더·결제·조직 전용 자산·감사 기록과 복구 필요성을 확인한 뒤 별도로 종료한다.

## 실행 기록

- 2026-09-22: 사전 상태와 두 계정의 관리 접근을 확인했다. 두 조직의 사용자 정의 역할·상위 방화벽 연결 및 두 프로젝트 lien은 0건이다. Analyze Move 재검사에는 명시적 blocker와 분석 오류가 없으며, IAM·정책·할당량 상속 변경 경고는 검토 대상이다.
- 개발 Cloud Run 13개와 운영 Cloud Run 0개를 확인했다. 개발 API의 200·401·401 경계는 이전 전 정상이다. 이 결과를 로그인·예약·DB 업무 전체 검증으로 확대하지 않는다.
- 임시 조건부 IAM과 두 조직 사이의 이전·복귀 정책을 설정했다. 첫 개발 이전 요청은 export/import 정책 검사에서 거부됐고 프로젝트는 이동하지 않았다. 정책 조회값을 재확인하고 전파 시간을 둔 후 재요청해 성공했다.
- 개발 이전 후 37개, 운영 이전 후 24개 설정 파일을 비교했다. 의도한 parent 변경과 etag·시간 경과에 따른 Firestore 복원 가능 시점은 비교에서 구분했고, 나머지 비교 대상에는 차이가 없었다. 프로젝트 직접 IAM·서비스 계정·WIF provider·Secret 메타데이터 및 IAM·활성 API·Cloud Run revision/traffic/IAM·결제 연결을 포함한다. 상위 조직의 정책 상속까지 동일하다는 뜻은 아니다.
- 개발 이전 후 `/health` 200·`UP`, 무인증 두 경로의 401·`missing_authorization`이 유지됐다. [개발 WIF 포함 preflight](https://github.com/bodeul110/Bodeul/actions/runs/35699701318)도 성공했다.
- [운영 이전 전 읽기 점검](https://github.com/bodeul110/Bodeul/actions/runs/35699344716)이 성공한 뒤 운영 프로젝트를 이동했다. 공식 관리자·개인 개발 관리자 모두 두 프로젝트의 새 parent와 기존 번호를 확인했다.
- 조건부 IAM binding 10건과 이전·복귀 정책 4건을 회수했다. etag를 제외한 두 조직의 IAM·명시 정책이 사전 기록과 일치하며, 이번 작업의 임시 항목은 0건이다. 회수 후에도 두 계정의 두 프로젝트 IAM 조회, 공용 결제 활성, 개발 API의 200·401·401 경계를 다시 확인했다.
- [운영 이전 후 읽기 점검](https://github.com/bodeul110/Bodeul/actions/runs/35700118124)은 기존 GitHub Environment 승인을 거쳐 성공했다. 임시 권한·정책 회수 후에도 운영 WIF 인증과 기반 설정 대조가 모두 통과했다. 배포·migration·DB 쓰기는 실행하지 않았다.

### 남은 범위

- 9월 22일 조직 이전 당시 운영 Cloud Run은 이전 전후 모두 0개였다. 이후 배포·DB 재개는 9월 27일 환경 분리 작업에서 별도로 수행했다.
- 실제 로그인·예약·결제·백업 복원·실기기 업무 흐름은 이번 조직 이전에서 재실행하지 않았다. 개발 API의 인증 경계 확인만으로 기존 업무 검증 이슈를 닫지 않는다.
- 기존 조직의 종료, 공식 관리자 복구 체계 추가 정리 및 신규 공개 서비스의 도메인 제한 대응은 별도 범위다.

## 근거

- [Google Cloud 이전 절차와 수동 복귀](https://docs.cloud.google.com/resource-manager/docs/perform-migration)
- [두 조직 사이의 이전 정책](https://docs.cloud.google.com/resource-manager/docs/configure-org-policy)
- [Analyze Move 범위와 오류](https://docs.cloud.google.com/resource-manager/docs/analyze-move)
- [조직 이동 시 특수 조건](https://docs.cloud.google.com/resource-manager/docs/handle-special-cases)
- [도메인 제한과 공개 Cloud Run](https://docs.cloud.google.com/organization-policy/restrict-domains)
