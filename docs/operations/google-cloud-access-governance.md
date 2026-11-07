# Google Cloud 계정 및 IAM 운영 기준

조직·관리자·개인 개발 권한 재확인일: 2026-11-07. Google Workspace 사용자·라이선스 항목은 별도 표시한 2026-08-23 확인 기록이다.

이 문서는 BoDeul의 Google Cloud, Firebase, Cloud Identity 접근 주체를 사람, 역할 그룹, 자동화 계정으로 분리하는 기준과 현재 전환 상태를 기록한다. 공개 사이트의 문의 주소를 개발 또는 운영 로그인으로 사용하지 않는다.

Google 외의 GitHub·Vercel·Supabase와 326 잔여 계정은 [서비스 소유 계정과 개발 권한](service-ownership-and-access.md)에서 함께 구분한다.

2026-11-07 후속 결정에 따라 프로젝트 자산의 공식 소유 계정은 외부 서비스를 포함해 `scp@bodeul.kr`로 통일한다. 외부 서비스의 실제 이메일 전환 완료 여부는 위 문서에서 별도로 추적한다. 개인 개발자 계정, 관리자 웹 업무 계정, CI·런타임 서비스 계정은 그대로 분리한다.

## 역할 경계

| 주체 | 기준 | 사용 범위 |
| --- | --- | --- |
| 사이트 문의 주소 | 메일 또는 공유 받은편지함 전용 | 외부 문의 수신. Google Cloud IAM 권한 없음 |
| 개인별 개발자 계정 | 사용자별 MFA와 식별 가능한 계정 | 개발 프로젝트 접근 |
| 개인별 운영자 계정 | 사용자별 MFA와 별도 복구 수단 | production 조회와 승인된 운영 작업 |
| 관리자 보안 그룹 | `gcp-admins@bodeul.kr` | 조직, 결제, 프로젝트 IAM 복구와 관리 |
| 개발자 보안 그룹 | `developers@bodeul.kr` | `bodeul-dev` 개발 접근 |
| 운영 담당자 보안 그룹 | `prod-operators@bodeul.kr` | production 로그, 모니터링, Cloud Run과 Secret 메타데이터 조회 |
| CI와 런타임 | GitHub OIDC/WIF와 용도별 서비스 계정 | 배포, 실행, 백업, 보존 작업 |

Google 계정 비밀번호를 여러 사람이 공유하지 않는다. 사람의 일상 작업은 개인별 계정으로 추적하고, GitHub Actions와 Cloud Run은 사람 계정 대신 기존 WIF와 서비스 계정을 유지한다.

한 계정이 여러 그룹에 속하면 부여된 권한이 합산된다. `prod-operators`의 읽기 전용 역할이 같은 계정의 `gcp-admins` 관리자 권한을 제한하지는 않는다. 그룹별 역할과 사용자별 실효 권한을 구분해서 점검한다.

## 확인된 현재 상태

- Cloud Identity API를 `bodeul-dev`에서 활성화했다.
- `bodeul.kr` Cloud Identity 디렉터리에 세 보안 그룹을 만들었다.
- `gcp-admins@bodeul.kr`의 구성원은 공식 관리자 한 명이다. 2026-11-07 공식 계정의 재인증과 그룹 Owner를 확인한 뒤 개인 개발 계정을 관리자 그룹에서 제거했다.
- 관리자 그룹의 공식 조직과 개발·production 프로젝트 관리 권한은 유지한다. 개인 개발 계정은 더 이상 이 그룹의 권한을 상속하지 않는다. 구 326 조직은 후속 검증 후 삭제 대기 상태로 전환했다.
- `developers@bodeul.kr`에는 공식 관리자 Owner와 개인 Member 세 명이 있다. 개발 프로젝트의 Editor를 아래 서비스별 역할로 대체했다.
- `prod-operators@bodeul.kr`에는 `bodeul-prod-110`의 Logging Viewer, Monitoring Viewer, Cloud Run Viewer, Secret Manager Viewer만 부여했다. Secret payload, Firestore 데이터와 Storage 객체 읽기 권한은 포함하지 않는다.
- 2026-09-22 공식 관리자 재인증 후 두 계정의 조직·프로젝트 IAM 조회를 확인했고, 조직 이동 뒤에도 두 계정으로 개발·production 프로젝트 접근을 다시 확인했다.
- 2026-09-06에는 공식 관리자에게 직접 부여된 기존 조직의 관리자 역할 4건을 제거한 기록이 있다. 2026-09-22 실조회에서는 기존 조직에 공식 관리자의 직접 `roles/resourcemanager.organizationAdmin`과 `roles/iam.denyReviewer`가 존재한다. 조직 이전과 추가 권한 회수를 섞지 않고 복구 접근으로 보존했다. 과거 중복 제거 기록을 현재의 직접 권한 부재로 해석하지 않는다.
- 팀에서 제외된 이전 개발자 계정의 `bodeul-dev` 직접 `roles/editor` binding을 제거했다.
- `prod-operators@bodeul.kr`에는 공식 관리자 Owner와 개인 개발 담당자 Member 한 명이 있다. 이 개인 계정은 production에서 조회 역할만 갖는다.
- 개발 그룹 구성원과 IAM을 실조회한 뒤 기존 두 개인 계정의 직접 Editor를 제거했다. 개인 개발 담당자 계정의 실제 개발 조회와 `testIamPermissions`로 새 역할 경계를 확인했다. 다른 구성원으로 직접 로그인하는 검증은 수행하지 않았다.
- 개인 계정의 개발·production 프로젝트 IAM 변경, 프로젝트 삭제, Cloud Run 변경·IAM, Secret payload, 서비스 계정 키 생성·대행·토큰 발급은 불허로 확인했다. 개발 Auth·Firestore 관리 권한은 유지하고 production에서는 차단했다.
- Git 작성자와 GitHub 작업 계정은 작업 시 실제 인증된 사용자 및 사용자의 계정 지정에 맞춘다. Google Cloud 관리자 계정과 GitHub 작성자를 같은 계정으로 강제하지 않는다.

과거 권한 정리는 [2026-09-06 재정비 기록](../reports/google-cloud-access-realignment-2026-09-06.md), 조직 소속은 [공식 조직 이전 기록](google-cloud-organization-migration.md), 최신 권한 축소는 [2026-11-07 정리 기록](../reports/shared-ownership-access-cleanup-2026-11-07.md)을 따른다.

### 개발 그룹 역할

| 범위 | 역할 | 목적 |
| --- | --- | --- |
| `bodeul-dev` 프로젝트 | `roles/firebase.viewer` | Firebase 구성 조회 |
| 같은 프로젝트 | `roles/firebaseauth.admin`, `roles/firebaseappcheck.admin` | 개발 인증 계정과 App Check 검증 관리 |
| 같은 프로젝트 | `roles/datastore.user` | 개발 Firestore 데이터 테스트 |
| 같은 프로젝트 | `roles/logging.viewer`, `roles/monitoring.viewer`, `roles/run.viewer` | 로그·모니터링·서버 조회 |
| 같은 프로젝트 | `roles/artifactregistry.reader`, `roles/serviceusage.serviceUsageConsumer` | 개발 이미지 조회와 API 사용 |
| 같은 프로젝트 | `roles/secretmanager.viewer` | 비밀값이 아닌 Secret 메타데이터 조회 |
| 개발 Firebase 파일 버킷만 | `roles/storage.objectUser` | 개발 첨부 객체 테스트 |

개발 서버·Rules·Functions 배포와 DB migration은 기존 GitHub workflow 및 WIF 경계로 수행한다. 개인 개발 계정에 IAM·서비스 계정 대행·Secret payload 권한을 되돌려 주는 방식으로 배포 오류를 해결하지 않는다. 추가 작업이 필요하면 대상 환경과 필요한 권한을 확인해 별도로 처리한다.

### Google Workspace 확인 결과 (2026-08-23)

아래 항목은 당시 Google Admin Console에서 확인한 기록이며, 이번 IAM 재정비에서는 사용자·별칭·라이선스를 다시 조회하거나 변경하지 않았다.

- Google Admin Console에는 관리 사용자 `scp@bodeul.kr` 한 명과 Google Workspace Business Standard 라이선스 한 개만 있다.
- `scp@bodeul.kr`에는 보조 이메일 주소와 이메일 별칭이 등록돼 있지 않다.
- 관리 도메인은 기본 도메인 `bodeul.kr`, 시스템 게스트 도메인, 가입 시 생성된 테스트 도메인 별칭뿐이다.
- `bodeul.kr`로 이전할 비관리 Google 계정은 0건이다.
- 당시 `bodeul.official@gmail.com`은 두 프로젝트, 두 조직과 세 역할 그룹에서 IAM 주체로 사용되지 않았다. Google Cloud 권한을 이 주소에 추가하지 않는 기준은 유지한다. 외부 서비스 소유 이메일은 2026-11-07 후속 결정에 따라 `scp@bodeul.kr`로 전환하며, 과거 주소의 계정 자체를 자동 삭제하지 않는다.

## 조직 경계

2026-09-22 기존 `bodeul326-org`에서 공식 `bodeul.kr`로 개발·production 프로젝트를 순차 이전했다. 두 프로젝트의 ID와 번호는 유지했다.

| 프로젝트 | 현재 상위 조직 | 프로젝트 번호 |
| --- | --- | --- |
| `bodeul-dev` | `bodeul.kr` (`1038381475908`) | `533563500316` |
| `bodeul-prod-110` | `bodeul.kr` (`1038381475908`) | `649312328770` |

기존 조직 `766471701894`와 남아 있던 기본 프로젝트는 기록 보존·현재 서비스 점검 후 2026-11-07 순차 삭제 요청했다. 두 자원 모두 `DELETE_REQUESTED`이며 영구 삭제 완료는 아니다. 공식 프로젝트 ID·번호·조직·결제와 기존 IAM은 유지된다. 9월 22일 조직 이전, 11월 7일 사람 계정 역할 축소와 후속 자원 삭제를 [정리 기록](../reports/shared-ownership-access-cleanup-2026-11-07.md)에서 구분한다.

공식 조직의 IAM·조직 정책 상속은 이전 조직과 다르다. 기존 공개 Cloud Run 접근과 새 공개 서비스 IAM 부여를 구분하며, 신규 production 배포 시 도메인 제한과 공개 접근 방식을 검증한다. 이전 절차·비교 항목·복귀 경계는 [공식 조직 이전 기록](google-cloud-organization-migration.md)을 따른다.

## 계정 전환 절차

1. 새 개인별 계정을 그룹에 추가한다.
2. MFA, 복구 이메일과 복구 코드를 설정한다.
3. 새 계정으로 Google Cloud Console, Firebase Console과 필요한 CLI 조회를 검증한다.
4. production 쓰기 권한은 필요한 역할만 별도로 승인한다.
5. 감사 로그에서 새 계정 또는 그룹 경유 접근을 확인한다.
6. 검증이 끝난 뒤에만 기존 직접 IAM binding을 제거한다. 2026-08-23 최초 전환 이후에도 변경이 생길 수 있으므로, 실제 IAM과 그룹 구성원을 다시 대조한다.

계정 삭제, 이메일 별칭 제거와 프로젝트 조직 이동을 먼저 실행하지 않는다. Google Admin Console 검증 결과 `bodeul.official@gmail.com`과 `scp@bodeul.kr` 사이의 Workspace 별칭이나 비관리 계정 충돌은 없다. 로그인 시 `scp@bodeul.kr`로 전환되는 현상은 두 계정을 합친 서버 설정이 아니라 브라우저 프로필과 Google 계정 선택 세션을 분리해 확인한다.

## 공용 결제 전환 경계

공용 계정으로 로그인하는 것과 Cloud Billing 계정·결제 수단을 이전하는 것은 별개다. 이번 접근 권한 정리를 이유로 기존 개인 결제 계정을 자동 재개하거나 개인 결제 수단을 다시 등록하지 않는다.

2026-11-07 재조회에서도 공식 조직 소속 `bodeul-billing`은 `open=true`이며, 두 프로젝트 모두 이 계정에 연결돼 `billingEnabled=true`다. 기존 개인 결제 `bodeul-test`는 닫힌 상태이며 현행 개발·운영 프로젝트와 연결되지 않는다. 구 조직의 기본 프로젝트에 남은 다른 닫힌 결제 계정과 혼동하지 않는다. 조직 이전 후 [명칭 정리](resource-naming.md)에서 기존 `bodeul-shared-billing1`의 표시 이름만 변경했고, 이번 권한 정리에서는 결제 계정 ID·연결·결제 수단을 변경하지 않았다.

향후 결제 전환도 계정의 `open=true`, 각 프로젝트의 예상 연결과 `billingEnabled=true`로 확인한다. 결제 활성 확인은 카드의 상세 내역·다음 청구 성공이나 앱 업무 기능 검증을 대신하지 않는다. 결제 등록·약관·카드 입력은 담당자가 직접 완료하며, 결제 계정과 Google Payments 권한을 동일한 것으로 취급하지 않는다.

## 점검 명령

현재 로컬 보들 설정 이름은 `bodeul-ops`이고 기본 계정은 개인 개발 계정이다. 기본 프로젝트를 추정하지 않고 설정 이름과 대상 프로젝트를 명시한다. 그룹·IAM 점검은 공식 관리자를 명시하며, 활성 계정이나 다른 프로젝트의 인증을 교체하지 않는다.

```powershell
gcloud config configurations describe bodeul-ops
gcloud identity groups memberships list --group-email=gcp-admins@bodeul.kr --view=full --configuration=bodeul-ops --account=scp@bodeul.kr
gcloud identity groups memberships list --group-email=developers@bodeul.kr --view=full --configuration=bodeul-ops --account=scp@bodeul.kr
gcloud projects get-iam-policy bodeul-dev --configuration=bodeul-ops --account=scp@bodeul.kr
gcloud projects get-iam-policy bodeul-prod-110 --configuration=bodeul-ops --account=scp@bodeul.kr
gcloud organizations list --configuration=bodeul-ops --account=scp@bodeul.kr
```

`Reauthentication failed`는 먼저 해당 계정의 재인증으로 확인한다. 이 오류만으로 IAM 손상이나 프로젝트 재생성 필요성을 판단하지 않는다. `gcloud auth login`은 기존 보들 설정과 명시한 계정으로 실행하고, `--update-adc`를 추가하거나 다른 계정의 인증을 일괄 폐기하지 않는다. 브라우저 자동 실행이 불가능하면 공식 `--no-launch-browser` 흐름을 사용하며 인증 코드는 해당 터미널에만 입력한다.

명령 결과를 공개 문서나 Issue에 붙일 때는 개인 이메일과 credential 정보를 제거한다. IAM 조회 성공은 결제 활성 상태, 앱 로그인, 배포와 서비스 가용성의 검증을 대신하지 않는다.

## 근거

- [Google Cloud 계정 및 조직 계획 권장사항](https://cloud.google.com/architecture/identity/best-practices-for-planning)
- [Google Workspace 관리자 계정 보안 권장사항](https://knowledge.workspace.google.com/admin/users/security-best-practices-for-administrator-accounts)
- [Google Cloud 서비스 계정 보안 권장사항](https://cloud.google.com/iam/docs/best-practices-service-accounts)
- [Google Cloud 프로젝트 조직 간 이동](https://cloud.google.com/resource-manager/docs/project-migration)
- [Cloud Billing과 Google Payments 접근 권한](https://docs.cloud.google.com/billing/docs/how-to/billing-access)
- [기존 프로젝트의 Cloud Billing 계정 변경](https://docs.cloud.google.com/billing/docs/how-to/modify-project)
