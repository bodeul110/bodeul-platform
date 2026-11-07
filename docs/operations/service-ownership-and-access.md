# 서비스 소유 계정과 개발 권한

최종 확인: 2026-11-07

공용 계정은 자산 소유와 복구에 사용하고, 개발자는 개인 계정으로 작업한다. 공용 계정의 비밀번호를 팀 전체에 배포하는 방식은 사용하지 않는다. 서비스별 로그인 식별자와 소유 범위가 다르므로 이름만 보고 같은 계정이나 권한으로 판단하지 않는다.

## 소유 계정 표

| 서비스 | 공용 소유·관리 주체 | 개인 개발 권한 | 확인 결과 |
| --- | --- | --- | --- |
| Google Cloud / Firebase | `bodeul.kr` 조직, 공식 관리자 `scp@bodeul.kr` | 개발 서비스별 역할, 필요한 운영 메타데이터 조회 | 개인 계정을 관리자 그룹에서 제거하고 개발 Editor를 대체했다. |
| Cloud Billing | 공식 조직의 `bodeul-billing`, 공식 관리자 | 결제 관리 권한 없음 | 공용 결제 계정 활성. 기존 개인 결제 계정은 닫힌 상태다. |
| GitHub | `bodeul110` 사용자 계정 | 플랫폼·웹 저장소의 Write | 공용 계정만 Admin. GitHub Organization으로 이전한 상태는 아니다. |
| Vercel | BoDeul 팀(`bodeul110`), `bodeul.official@gmail.com` | 개인 두 명 Developer | 공용 계정만 Owner. 프로젝트와 배포 연결은 유지했다. |
| Supabase | BoDeul 조직, `bodeul.official@gmail.com` | 개인 두 명 Developer | 공용 계정만 Owner. 임시 326 계정의 미수락 Owner 초대는 취소했다. |

`scp@bodeul.kr`은 Google 관리 계정이고, `bodeul.official@gmail.com`은 외부 서비스 소유 계정이다. `bodeul110`은 GitHub 사용자명과 Vercel 팀 주소에 쓰이는 이름이다. 이름이 같다고 로그인 방식이나 권한이 같은 것은 아니다.

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
| `bodeul326-org` | 임시 구축 당시 조직. 현재 보들 개발·운영 프로젝트의 상위 조직이 아님 | 삭제하지 않고 과거 자산 보관 대상으로 구분 |
| `My First Project` | 구 조직에 남은 기본 프로젝트. 326 계정 직접 Owner, 결제 비활성 | 실행 서비스·업무 데이터 사용을 단정하지 않고 보존 |
| `bodeul-test` | 닫힌 기존 개인 결제 계정 | 재개·결제수단 변경 안 함 |
| 기본 프로젝트의 별도 결제 연결 | 닫힌 결제 계정, `billingEnabled=false`, 326 계정이 Billing Admin | 기존 326 자격 증명으로 읽기 확인. 공식 관리자에게 조회 권한이 없으며 공용 결제·`bodeul-test`와 다른 계정 |
| Supabase의 326 Owner 초대 | 미수락 초대 | 취소 완료 |
| 현행 Google 개발·운영 프로젝트 | 직접 IAM과 지원되는 Cloud Asset IAM 검색에서 326 주체 없음 | 프로젝트 이전·데이터 복사 불필요 |

구 조직의 독립 조직 소유자 목록과 감사 보존 필요성 확인이 남아 있으므로 구 조직·계정·프로젝트를 자동 삭제하지 않는다. 연결이 닫혔다고 결제 이력과 복구 책임까지 없어지는 것은 아니다. 자세한 이전 이력은 [공식 조직 이전](google-cloud-organization-migration.md)을 참고한다.

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

이번에 실조회한 범위는 Google Cloud/Firebase, GitHub, Vercel, Supabase다. 카카오·네이버·도메인/DNS·Notion·Figma의 현재 소유권까지 재검증한 것으로 확대하지 않는다.

실행 범위와 검증 한계는 [공용 소유권 정리 기록](../reports/shared-ownership-access-cleanup-2026-11-07.md), Google 역할별 상세는 [Google Cloud IAM 기준](google-cloud-access-governance.md)을 따른다.

## 근거

- [Google Cloud IAM 역할](https://docs.cloud.google.com/iam/docs/roles-overview)
- [Vercel 역할별 접근](https://vercel.com/docs/rbac/access-roles)
- [Supabase 접근 제어와 요금제 제한](https://supabase.com/docs/guides/platform/access-control)
