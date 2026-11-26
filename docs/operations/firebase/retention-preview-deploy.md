# 개발 보존 함수 배포

대상은 `bodeul-dev` 서울 리전의 기존 `cleanupExpiredData` 한 개다. 보호자 예약 원문을 지우지 않고 남은 후보 수를 실제 정기 실행에서 확인하는 절차다. 운영, 다른 함수, 예약 기능 플래그, 기존 스케줄러 호출 계정은 바꾸지 않는다.

## 권한을 나누는 이유

기존 함수의 빌드·실행 계정에는 개발 프로젝트의 `Editor` 역할이 있다. 그 계정을 GitHub에서 사용할 수 있게 하거나 Firebase 전체 배포 권한을 주지 않는다. 대신 전용 서비스 계정과 Docker 저장소를 쓰며, 기존 계정의 권한은 건드리지 않는다. 이미지 빌드·저장 비용이 발생할 수 있다.

| 전용 계정 | 허용하는 작업 | 허용하지 않는 작업 |
| --- | --- | --- |
| `bodeul-retention-dev-deploy` | 승인된 GitHub 실행에서 소스 업로드 준비·작업 상태 조회, **이 함수** 조회·갱신, 아래 두 계정 사용 | 다른 함수 배포, 비밀값 직접 조회, 기본 계정 사용 |
| `bodeul-retention-dev-build` | 이 함수의 소스 객체 읽기, 빌드 로그 기록, 전용 `bodeul-retention-dev` 저장소에 이미지 쓰기 | 공유 이미지 저장소 쓰기, DB 비밀값·업무 데이터 조회 |
| `bodeul-retention-dev-run` | 기본 개발 Firestore의 문서 조회, 기존 개발 DB 연결 시크릿 두 버전 사용 | Firestore 쓰기, Storage 파일 읽기·삭제, 운영 자원 접근 |

실행 계정의 Firestore 읽기는 컬렉션 단위가 아니라 개발 기본 DB 범위다. DB 연결은 기존 보존 전용 DB 역할을 사용하므로 DB 계정 자체를 읽기 전용이라고 간주하지 않는다. dry-run도 실행 이력과 집계는 기록한다. 실제 파기는 명시적으로 차단한다.

## 최초 설정

1. 개발 프로젝트 번호 `533563500316`, 리전 `asia-northeast3`, 기존 함수와 Cloud Scheduler의 현재 리비전·대상·호출 계정을 확인한다. 새 서비스 계정에 키를 만들지 않는다.
2. GitHub 환경 `firebase-retention-preview`는 사용자 `bodeul110`의 독립 승인, 본인 승인·관리자 우회 금지, `dev` 브랜치만 허용하도록 구성한다. WIF 공급자 `github-actions/bodeul-retention-dev`는 여기서는 **비활성**이다. 저장소·소유자 ID, 워크플로 경로, 수동 실행, 환경, 브랜치가 모두 일치해야 한다. 풀 전체에 권한을 주지 않는다.
3. 개발 프로젝트에서 맞춤 역할 생성과 리소스 IAM 설정 권한을 **이미** 보유한 관리자가 저장소 루트에서 다음 스크립트를 실행한다. 첫 줄은 조회용 계획이다. 두 번째 명령은 담당자의 이메일을 묻고, Enter만 누르면 변경 없이 끝난다. Windows PowerShell 5.1을 지원한다.

   ```powershell
   powershell.exe -NoProfile -File .\tools\firebase\iam\configure-retention-preview-boundaries.ps1
   powershell.exe -NoProfile -File .\tools\firebase\iam\configure-retention-preview-boundaries.ps1 -Apply -ConfirmProject bodeul-dev
   ```

   스크립트는 맞춤 역할 정의 두 개와 개별 리소스 바인딩 네 개만 준비한다. 사람에게 권한을 부여하거나 프로젝트 단위 `actAs`를 부여하지 않는다. 담당자가 없으면 WIF를 끈 상태로 중단한다.
4. 직접 부여하는 나머지 권한은 다음 범위를 다시 읽어 확인한다.
   - 빌드: 개발 로그 작성자. 소스 버킷 `gcf-v2-sources-533563500316-asia-northeast3`에서는 `cleanupExpiredData/` 아래 객체만 읽는다. 다른 소스 경로가 필요하다고 나오면 범위를 일괄 확대하지 않고 실패한 객체를 먼저 확인한다.
   - 실행: `datastore.entities.get/list`만 포함한 역할을 `projects/bodeul-dev/databases/(default)`로 제한한다. 시크릿 접근은 `RETENTION_DATABASE_URL:2`와 `RETENTION_DATABASE_CA_CERT:1`의 정확한 버전만 허용한다. 버전 교체에는 별도 권한·배포 검토가 필요하다.
5. WIF가 비활성이고 배포자에게 함수 변경 권한이 없는 상태에서 기존 함수의 상태·삭제 차단·고정 시크릿을 확인한다. 권한 있는 관리자가 Cloud Functions v2 PATCH의 `updateMask`를 `buildConfig.serviceAccount,buildConfig.dockerRepository,serviceConfig.serviceAccountEmail`로 제한해 **기존 함수**를 전환한다. 소스·환경변수·스케줄을 함께 바꾸지 않는다. 작업 완료 후 함수가 `ACTIVE`이고 전용 빌드 계정·전용 저장소·전용 실행 계정과 기존 스케줄러 대상이 맞는지 재조회한다.
6. 그 이후에만 `bodeulRetentionDevStage`(업로드 준비·작업 조회)를 개발 배포 계정에, `bodeulRetentionDevRelease`(함수 조회·갱신)를 **해당 함수에서만** 같은 계정에 부여한다. 이 절차가 검토되어 보호된 `dev`에 반영된 뒤 WIF를 활성화한다. 함수 IAM을 설정하지 못하면 프로젝트 전체의 함수 편집자로 대체하지 않는다.

## 배포 및 실제 확인

- 보호된 `dev`의 검토된 40자 SHA로만 기존 `Firebase Rules` 워크플로를 수동 실행한다. 일반 PR·기본 수동 실행은 클라우드 인증 없이 테스트만 한다.

  ```shell
  gh workflow run firebase-rules.yml --repo bodeul110/bodeul-platform --ref dev -f deploy_retention_preview=true -f confirm_dev_sha=<검토한-dev-SHA-40자>
  ```

- 요청자와 다른 승인자가 환경과 SHA를 확인한다. 테스트와 승인 후 같은 SHA를 다시 확인하고 추적된 함수 소스만 업로드한다. 기존 함수의 소스 및 환경변수 필드만 갱신하며 `RETENTION_APPLY_ENABLED=false`, `RETENTION_GUARDIAN_BOOKING_ENABLED=true`로 고정한다. 배포 성공은 새 리비전·계정·시크릿 연결까지 확인했다는 뜻이며, 정기 실행 성공을 뜻하지 않는다.
- 기존 작업 `firebase-schedule-cleanupExpiredData-asia-northeast3`(매일 04:45, 서울)의 다음 실행을 확인한다. 앞당길 필요가 있으면 진행 중인 실행이 없는지 먼저 확인하고 기존 스케줄러를 한 번만 호출한다. GitHub 배포자에게 호출 권한은 주지 않는다.
- **해당 호출 이후** 개발 DB `bodeul.retention_job_runs`의 새 행에서 `mode=DRY_RUN`, `status=COMPLETED`, `guardian_draft_checked=true`, `guardian_drafts_purged=0` 및 다른 실제 삭제 지표가 모두 0인지 확인한다. 후보·잔여 수는 0일 필요가 없고 dry-run에서는 서로 같아야 한다. 새 행이 없거나 실패하면 검증 미완료다. 이슈에는 시각·리비전·집계만 남기고 원문·비밀값·개인 식별자는 남기지 않는다.
- 실패 시 같은 명령을 자동 반복하지 않는다. 진행 중 작업과 서비스가 제공 중인 리비전을 먼저 확인한다. 새 집계만 중단할 때는 승인된 관리 경로에서 보호자 집계 플래그만 끄고 삭제 차단을 유지한다. IAM 문제로 실패하면 WIF를 비활성화한 채 확인하며, 기본 `Editor` 계정을 GitHub에 연결해 우회하지 않는다.

[설계 및 DB 집계 계약](../../design/guardian-booking-retention.md) · [Google의 사용자 지정 빌드 계정 안내](https://docs.cloud.google.com/functions/docs/building) · [Cloud Functions v2 소스 업로드](https://docs.cloud.google.com/functions/docs/reference/rest/v2/projects.locations.functions/generateUploadUrl)
