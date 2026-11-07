# 서비스 소유 계정과 개발 권한

최종 확인: 2026-11-07

프로젝트 자산의 공식 소유·복구 계정은 `scp@bodeul.kr`로 통일한다. 개발자는 개인 계정으로 작업하고, 공용 계정의 비밀번호를 팀 전체에 배포하는 방식은 사용하지 않는다. 목표 기준과 서비스별 실제 전환 완료 여부를 구분한다.

## 소유 계정 통일 기준

- 기존 소유 계정의 로그인·기본 이메일을 `scp@bodeul.kr`로 변경하는 방식을 먼저 사용한다. 이메일 변경을 위해 저장소·프로젝트·DB를 새로 만들거나 기존 계정을 삭제하지 않는다.
- GitHub 사용자명 `bodeul110`, Vercel 팀 주소, 프로젝트 ID, 서비스 계정, DB role과 기존 리소스 주소는 유지한다. 이메일 주소와 서비스 내부 소유자 ID는 다른 식별자다.
- 기존 계정의 이메일 변경이 불가능하거나 대상 이메일이 별도 계정에 이미 등록돼 있으면 멈추고 계정 관계를 확인한다. 확인 없이 새 Owner를 추가하거나 계정을 합치지 않는다.
- 새 이메일 인증, MFA·복구 수단, 기존 자산 접근, 소유자 ID와 개발자 역할 유지 여부를 확인한 후에만 전환 완료로 표시한다.
- 기존 이메일·로그인은 검증 전 제거하지 않는다. 과거 결제 기록과 복구에 필요한 Google 계정 자체를 자동 삭제하지 않는다.
- 관리자 웹의 실명 계정, 개발자 개인 계정, CI·런타임 서비스 계정은 통일 대상이 아니다. 모든 실행 주체를 공용 계정으로 바꾸지 않는다.

## 확인된 현재 소유 계정

| 서비스 | 공용 소유·관리 주체 | 개인 개발 권한 | 확인 결과 |
| --- | --- | --- | --- |
| Google Cloud / Firebase | `bodeul.kr` 조직, 공식 관리자 `scp@bodeul.kr` | 개발 서비스별 역할, 필요한 운영 메타데이터 조회 | 개인 계정을 관리자 그룹에서 제거하고 개발 Editor를 대체했다. |
| Cloud Billing | 공식 조직의 `bodeul-billing`, 공식 관리자 | 결제 관리 권한 없음 | 공용 결제 계정 활성. 기존 개인 결제 계정은 닫힌 상태다. |
| GitHub | `bodeul110`, 기본·알림 이메일 `scp@bodeul.kr` | 플랫폼·웹 저장소의 Write | 이메일 인증·Primary 지정과 MFA 등록 유지 확인. 저장소 소유자 ID·주소 유지 |
| Vercel | BoDeul 팀(`bodeul110`), Owner의 기본 이메일 `scp@bodeul.kr` | 개인 두 명 Developer | 이메일 인증·Primary 지정·MFA 등록과 기존 Owner ID·역할·프로젝트·GitHub 연결 유지 확인 |
| Supabase | BoDeul 조직, 로그인·기본 이메일 `scp@bodeul.kr` | 개인 두 명 Developer | 공용 Owner 1명·MFA 등록 유지, 개발·운영 DB의 기존 ID와 `ACTIVE_HEALTHY` 확인. 326 초대 없음 |

GitHub·Vercel·Supabase의 기존 공용 소유 계정은 `scp@bodeul.kr`을 기본 이메일로 사용한다. GitHub·Vercel의 이전 Gmail 주소는 검증된 보조 이메일로 보존했다. `bodeul110`은 유지하는 서비스 식별자이지 별도의 최종 소유 이메일이 아니다. 아래 나머지 외부 서비스까지 전환 완료했다는 뜻은 아니다.

이메일 변경 후 자산 접근과 역할을 확인했으며 DB 이동·데이터 복사·배포 설정 변경은 하지 않았다. 복구 수단과 새 로그인·복구 경로의 최종 점검은 비공개 운영 기록으로 관리하고, 확인 전 기존 보조 이메일을 제거하지 않는다.

관리자 웹 안의 `SUPER_ADMIN`은 업무 권한이다. Google Cloud·Vercel·Supabase의 인프라 Owner와 별개이며, 이번 작업에서 변경하지 않았다.

## 개발자가 할 수 있는 일

| 범위 | 허용 | 공용 관리자 또는 승인된 자동화로 처리 |
| --- | --- | --- |
| Google 개발 환경 | Firebase 조회, 개발 Auth·App Check·Firestore 관리, 개발 파일 버킷의 객체 관리, 서비스·로그·모니터링·Secret 메타데이터 조회 | IAM 변경, 서비스 계정 키 생성·대행, Secret 값 읽기, 서버·Rules·Functions 배포 |
| Google 운영 환경 | 지정된 운영 담당자의 로그·모니터링·Cloud Run·Secret 메타데이터 조회 | 운영 데이터·비밀값 접근과 변경, IAM, 배포, migration |
| GitHub | 개인 계정으로 PR·리뷰·개발 브랜치 작업 | 저장소 소유권, 보호 규칙과 관리자 설정 |
| Vercel | Developer 역할의 개발·Preview 작업 | 팀 소유권·결제·구성원과 Production 설정 |
| Supabase | Developer 역할의 프로젝트 콘텐츠 관리 | 조직·결제·프로젝트 설정, 이전·삭제, 구성원 관리 |

배포는 기존 GitHub Actions와 WIF/서비스 계정을 사용한다. 인프라 권한 축소가 GitHub의 출시 PR·Environment 승인 규칙을 대체하지는 않는다. Vercel Developer도 Git 브랜치 병합을 통한 운영 배포에 영향을 줄 수 있으므로 `master` 보호를 유지한다.

### Supabase Pro의 제한

현재 개발·운영 DB는 같은 Pro 조직에 있다. 조직 단위 Developer는 두 프로젝트의 콘텐츠에 모두 접근할 수 있다. 따라서 이번 Owner 회수는 **소유권·결제·설정 권한 분리**이며, **개인 계정의 운영 DB 데이터 접근 차단까지 완료했다는 뜻은 아니다**.

프로젝트 범위 역할은 Team/Enterprise 기능이다. 완전한 개발 전용 접근이 필요하면 요금제 변경, 조직 분리 또는 대시보드 접근을 대체하는 제한된 DB 접근 경로를 따로 검토한다. 이번에는 요금제를 올리거나 DB를 이동하지 않았다.

## 326 계정과 조직

| 항목 | 상태 | 처리 |
| --- | --- | --- |
| `bodeul326-org` | 2026-11-07 15:24 KST 삭제 요청, `DELETE_REQUESTED` | 기록 보존과 개발·운영 정상 확인 후 실행. 영구 삭제 완료는 아님 |
| `My First Project` | 2026-11-07 14:57 KST 삭제 요청, `DELETE_REQUESTED` | 사전 메타데이터·IAM·조회 가능한 로그와 결제 상태 기록 보존 |
| `bodeul-test` | 닫힌 기존 개인 결제 계정 | 재개·결제수단 변경 안 함 |
| 기본 프로젝트의 별도 결제 연결 | 닫힌 결제 계정, `billingEnabled=false`, 326 계정이 Billing Admin | 기존 326 자격 증명으로 읽기 확인. 공식 관리자에게 조회 권한이 없으며 공용 결제·`bodeul-test`와 다른 계정 |
| Supabase의 326 Owner 초대 | 미수락 초대 | 취소 완료 |
| 현행 Google 개발·운영 프로젝트 | 직접 IAM과 지원되는 Cloud Asset IAM 검색에서 326 주체 없음 | 프로젝트 이전·데이터 복사 불필요 |

조직 삭제 후에도 공식 개발·운영 프로젝트의 조직·결제 연결·기존 IAM이 유지되고 두 Core API `UP`, 두 DB `Healthy`, 두 관리자 목록 HTTP 200을 확인했다. 기본 프로젝트와 조직은 안내상 삭제 요청 후 30일의 복구 기간이 있으며 Google 계정과 닫힌 결제 계정은 보존한다. 전체 과거 로그·청구서 백업이나 앱 전체 종단 검증을 수행했다는 뜻은 아니다. 자세한 이전 이력은 [공식 조직 이전](google-cloud-organization-migration.md)을 참고한다.

## 작업과 복구

1. 평소에는 개인 GitHub·gcloud·Vercel 계정을 사용한다. Google의 `bodeul-ops` 기본 계정도 개인 개발 계정을 유지한다.
2. 소유권·결제·IAM 변경은 공용 관리자로 재인증한 뒤 대상 서비스와 환경을 명시한다.
3. 관리자 재인증에 `--no-activate`를 사용하면 개인 CLI 기본 계정을 바꾸지 않을 수 있다. 전체 인증 삭제나 무관한 계정 로그아웃을 복구 수단으로 쓰지 않는다.
4. 새 구성원은 필요한 그룹·Developer·Write 역할에만 추가한다. Owner를 기본 개발 역할로 부여하지 않는다.
5. 공용 관리자 MFA·복구 수단을 별도로 보관한다. 공용 계정 잠금은 소유 관리 작업을 막을 수 있으므로 운영 전 복구 절차를 점검한다.
6. 결제 계정의 소유권, 플랜, 미납 여부와 다음 결제 성공은 서로 다른 항목으로 점검한다.

### 로컬 작업 계정

- 플랫폼·웹 저장소의 로컬 Git 작성자는 개인 GitHub 계정의 이름과 공개용 `noreply` 이메일로 맞췄다. GitHub CLI 인증도 같은 개인 계정이다.
- 전역 Git 작성자 설정과 기존 커밋 이력은 변경하지 않았다. 보들 외의 저장소에 남은 326 설정은 별도 점검 대상이다.
- gcloud의 개인 기본 계정과 공용 관리자의 재인증 자격 증명은 별개다. 보관된 326 자격 증명은 구 자산 확인·복구가 끝나기 전에 일괄 삭제하지 않는다.
- Firebase CLI에도 개인 로그인을 추가하고 메인 저장소·이번 작업 worktree의 폴더별 기본 계정으로 선택했다. `login:list`와 프로젝트 조회로 확인했으며 공용 로그인은 복구용으로 보존했다. 새 worktree에서는 기본 계정을 다시 확인한다.

### 결제 확인

공용 소유자 설정 완료와 정상 과금은 별개다. 현재 청구 상태와 결제 담당자의 확인 항목은 비공개 운영 기록으로 관리한다. 이번 작업에서 결제·요금제·좌석 자동 추가 설정은 변경하지 않았다.

### 나머지 서비스의 전환 확인

| 대상 | 확인과 전환 기준 |
| --- | --- |
| Kakao Developers | 기존 앱 Owner와 카카오 로그인 계정 재확인 필요. 이메일 변경 또는 앱 소유권 이전 가능 여부를 확인하되 앱 ID·키·허용 도메인은 유지 |
| Naver Developers | 현재 application 관리자와 로그인 계정 확인 필요. 현재 비활성 로그인 기능을 이번 소유권 통일 작업에서 새로 활성화하지 않음 |
| 도메인·DNS·사이트 | 등록기관·도메인 등록인·DNS 및 사이트 관리자 계정을 각각 확인. 공개 NS 조회만으로 소유자를 판정하지 않고 DNS·MX 값을 임의 변경하지 않음 |
| Notion·Figma | 워크스페이스·팀·프로젝트 소유 권한을 확인. 기존 Figma 이전 제외 결정은 전체 소유 계정 통일 요청에 따른 적용 범위를 재확인하기 전 임의로 확대 변경하지 않음 |
| 출시·결제·메시징 계정 | 실제 존재하고 사용하는 계정만 목록화. 신원·사업자 심사와 계약·카드 입력은 담당자가 진행하며 현재 미개설 계정을 자동 생성하지 않음 |

카카오·네이버·도메인/DNS·Notion·Figma는 `scp@bodeul.kr` 전환 완료로 확인한 상태가 아니다. 계정 소유 통일과 법적 계약 주체, 청구 수신 주소, 카드 소유자는 별도로 검증한다.

실행 범위와 검증 한계는 [공용 소유권 정리 기록](../reports/shared-ownership-access-cleanup-2026-11-07.md), Google 역할별 상세는 [Google Cloud IAM 기준](google-cloud-access-governance.md)을 따른다.

## 근거

- [Google Cloud IAM 역할](https://docs.cloud.google.com/iam/docs/roles-overview)
- [Vercel 역할별 접근](https://vercel.com/docs/rbac/access-roles)
- [Supabase 접근 제어와 요금제 제한](https://supabase.com/docs/guides/platform/access-control)
- [Vercel 계정 이메일 관리](https://vercel.com/docs/accounts)
- [Supabase 계정 이메일 변경](https://supabase.com/kb/troubleshooting/change-email-associated-with-supabase-account-T5eHNT)
- [GitHub 기본 이메일 변경](https://docs.github.com/en/account-and-profile/how-tos/email-preferences/changing-your-primary-email-address)
