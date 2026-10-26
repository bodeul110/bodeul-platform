# Production 인프라 읽기 전용 점검

현재 구성 대조: 2026-09-27. 8월 26일 최초 구성 결과는 아래에 당시 이력으로 유지한다.

## 목적

Production 배포나 데이터 조회 없이 Google Cloud/Firebase 기반의 누락과 설정 드리프트를 반복해서 확인한다. 배포, 백업, 보존 작업의 서비스 계정을 재사용하지 않고 감사 전용 WIF와 서비스 계정을 사용한다.

## 실행 경계

- GitHub Actions `workflow_dispatch`에서만 실행한다.
- 저장소 `bodeul110/bodeul-platform`, `master`, 현재 commit SHA, `bodeul-prod-110`을 모두 확인한다.
- GitHub Environment `production-infrastructure-audit`의 승인을 통과해야 한다.
- WIF provider는 저장소 이름과 불변 ID, 소유자 ID, `master`, Environment, workflow 경로와 이벤트를 모두 제한한다.
- 서비스 계정 JSON key를 만들지 않는다.
- 감사 계정에는 구성 metadata 조회 권한만 부여한다. Secret payload, Firestore 문서, Auth 사용자, Storage 객체는 읽을 수 없다.

## 점검 범위

| 범위 | 확인 내용 |
| --- | --- |
| Project/API | project ID·number·상태와 필수 API 활성화 |
| Artifact Registry | Tokyo Docker repository 존재와 형식 |
| WIF/IAM | 전용 provider 조건, 서비스 계정 상태, 사용자 관리 key 0개, project-local IAM binding |
| Secret Manager | 고정 secret 리소스와 version metadata 상태. payload는 조회하지 않음 |
| Cloud Run | 첫 배포 전 부재 또는 존재 시 단일 컨테이너·승인 이미지·준비된 최신 revision·runtime 계정·동적 outbound 기준 |
| Firebase/Auth | Firebase project와 Email/Password·email privacy 설정. 사용자 목록은 조회하지 않음 |
| Firebase App Check | Android·Web 앱 식별자, exact release SHA-256, provider API·설정, Identity Platform·Firestore·Storage 모드, callable Functions 환경변수, 최근 7일 `ALLOW`·`VALID` 요청 |
| Firestore | `(default)` database의 Tokyo·Native mode·삭제 방지 metadata |
| Storage | Firebase Storage와 DB backup bucket의 위치·uniform access·public access prevention·보존 metadata |

9월 27일 Cloud Run 운영 revision과 Kakao secret version은 준비돼 baseline에 포함한다. 아직 남은 release App Check provider/enforcement와 Firebase Storage UBLA는 별도 출시 게이트다. 같은 날 감사 run `36313836486`과 실제 구성 근거는 [분리 실행 기록](../reports/dev-production-separation-2026-09-27.md)을 따른다. Supabase와 PostgreSQL은 [Production DB migration 사전 점검](production-database-migration-readiness.md)에서 다룬다.

IAM 검사는 production 프로젝트에 직접 설정된 binding을 대조한다. 조직·폴더에서 상속된 Allow와 Deny는 이 서비스 계정의 조회 범위가 아니므로 effective IAM 전체를 보장하지 않는다. 2026-08-26 구성 시 조직 Deny 정책 0건과 WIF provider 생성 권한의 `DENY_ACCESS_STATE_NOT_DENIED`를 별도 관리자 점검으로 확인했고, 이때 사용한 임시 `denyReviewer`, WIF 관리자와 서비스 계정 관리자 역할은 확인 직후 모두 회수했다.

## 상태 판정

| 상태 | 의미 |
| --- | --- |
| `PASS` | 인증된 전체 응답을 정상 파싱했고 기대값과 일치 |
| `DRIFT` | 권한 있는 조회에서 리소스 또는 설정 불일치를 확인 |
| `EXPECTED_BLOCKER` | 아직 완료하지 않은 출시 게이트를 확인 |
| `EXPECTED_ABSENT` | 현재 단계에서 부재가 명시된 리소스 |
| `UNAVAILABLE` | 401·403·429, 권한 부족 또는 API 가용성 문제로 판정할 수 없음 |
| `ERROR` | 네트워크·5xx·응답 파싱 등 점검 자체의 오류 |

필수 baseline 항목에 `DRIFT`, `UNAVAILABLE`, `ERROR`가 하나라도 있으면 workflow를 실패시킨다. 403을 리소스 부재로 간주하지 않으며, 404도 선행 project/API 확인이 끝난 고정 리소스에서만 부재로 판단한다.

### App Check 단계 상태

App Check 단계는 [`tools/gcp/production-infrastructure-state.json`](../../tools/gcp/production-infrastructure-state.json)의 기대값과 실제 metadata를 대조한다. 이 값은 구성 상태를 나타내며, 실기기·브라우저 사용자 흐름 검증을 대신하지 않는다.

| 상태 | 구성 기준 | 다음 게이트 |
| --- | --- | --- |
| `unverified` | Android·Web production provider가 준비되지 않았고 Identity Platform·Firestore·Storage가 모두 기본 `OFF` | release SHA-256과 provider 준비 |
| `preparing` | 하나 이상의 provider가 일부 또는 전부 준비됐지만 세 Firebase 서비스는 모두 `OFF` | 두 provider와 제한 조건을 모두 완료한 뒤 monitoring 시작 |
| `observe` | 두 provider, production callable이 준비됐고 callable과 세 Firebase 서비스가 모두 관찰 상태 | Android·Web 각각 최근 7일 `ALLOW`·`VALID`와 주요 흐름 확인 |
| `staged` | callable 또는 세 Firebase 서비스 중 일부만 강제된 상태 | 단계별 정상·거부·rollback 확인 |
| `enforced` | callable과 세 Firebase 서비스가 모두 강제된 상태 | 최근 정상 요청과 운영 오류율 지속 관찰 |

`OFF`는 App Check 보호와 관련 메트릭 수집이 모두 꺼진 상태다. `UNENFORCED`는 요청을 차단하지 않고 메트릭만 수집하는 관찰 상태다. 따라서 provider 등록만으로 `observe`나 `enforced` 완료로 판정하지 않는다.

### App Check 감사 계약

- Firebase Management API로 production Android·Web 앱의 활성 상태와 저장소 기준 App ID를 확인한다. App ID 원문은 내부 exact match에만 사용하고 Summary에 출력하지 않는다.
- Production Android·Web 앱의 App Check debug token은 0개여야 한다. debug token이 있으면 provider를 구분할 수 없는 `ALLOW`·`VALID` 메트릭을 release 증거로 인정하지 않는다.
- `observe` 이상에서는 보호된 GitHub Environment의 `ANDROID_RELEASE_SHA256`과 Firebase에 등록된 SHA-256이 정확히 일치해야 한다. 인증서 지문 원문은 로그나 artifact에 남기지 않는다.
- Android는 Play Integrity API와 App Check 설정의 존재·TTL을 확인한다. 현재 배포 채널 확정 전 계약은 인식되지 않은 앱 버전을 허용하지 않고, 기기 무결성·라이선스는 Firebase 기본 정책을 유지하는 것이다. 팀 소유 release key와 Google Play 연결이 없는 상태를 provider 준비 완료로 간주하지 않는다. Google Play 전용 배포가 확정되면 라이선스 요구를 별도 변경으로 강화한다.
- Web은 reCAPTCHA Enterprise API와 App Check 설정을 확인한다. 연결 키는 `SCORE` 유형, 승인된 production hostname 제한, `allowAllDomains=false`, AMP 비허용, production testing option 부재를 모두 만족해야 한다. site key 원문은 출력하지 않는다.
- Identity Platform 설정은 Firebase Authentication의 Email/Password와 이메일 열거 보호를 확인한다. App Check 모드는 `identitytoolkit.googleapis.com`, Firestore는 `firestore.googleapis.com`, Storage는 `firebasestorage.googleapis.com`의 개별 설정을 직접 조회한다. 설정이 생략된 경우 공식 기본값인 `OFF`로 판정한다.
- production callable Functions는 고정 함수 목록의 배포 여부와 `ENABLE_APPCHECK_ENFORCEMENT` 환경변수를 확인한다. 함수가 0개이면 강제 완료로 보지 않고 현재 단계의 부재로 기록한다.
- Cloud Monitoring의 `firebaseappcheck.googleapis.com/services/verification_count`를 최근 7일 범위로 조회한다. Android·Web App ID별 `result=ALLOW`, `security=VALID`만 정상 증거로 집계하고 불완전한 페이지나 조회 오류는 성공으로 처리하지 않는다.
- 위 Monitoring 지표는 Firebase 통합 서비스 요청만 다룬다. Spring Core API의 custom backend 검증은 Cloud Run의 `app_check_verdict`, 앱 구분, 대상 경로를 포함한 구조 로그와 실제 HTTP 결과로 별도 입증한다.

### 2026-08-26 production 확인 결과

- production Android·Web 앱 리소스는 존재한다. Web은 canonical production hostname으로 제한한 reCAPTCHA Enterprise `SCORE` key, App Check 설정과 Auth 허용 도메인까지 구성했다.
- Android exact release SHA-256과 Play 배포 연결은 아직 준비되지 않았다.
- Identity Platform·Firestore·Storage의 App Check 모드는 모두 명시 설정이 없는 기본 `OFF`다.
- production callable Functions는 0개이며 최근 7일 Android·Web `ALLOW`·`VALID` 요청도 각각 0건이다.
- 현재 단계는 `preparing`이다. Web provider 기반 설정은 완료했지만 Android provider, 클라이언트 token 전송, monitoring과 enforcement는 완료로 기록하지 않는다.

## 공개 로그 기준

Actions Summary에는 고정 check ID, 상태, 안전한 enum·boolean과 commit SHA만 남긴다. 다음 항목은 출력하거나 artifact로 업로드하지 않는다.

- OAuth/OIDC token과 credential 파일
- Secret payload·version resource name, JDBC URL과 DB 접속 정보
- IAM policy 원문과 개인·그룹·서비스 계정 이메일
- Firebase API key·App ID·Auth domain 원문
- bucket/object 경로와 목록, Firestore 문서
- Cloud Run 전체 설정, 환경변수, secret 참조와 서비스 URL
- `gcloud` 또는 REST의 원시 stdout·stderr

## 최초 구성

Google Cloud 관리자 권한이 있는 계정으로 재인증한 뒤 다음 스크립트를 실행한다.

```powershell
.\core-api\deploy\cloud-run\setup-production-infrastructure-auditor.ps1 `
  -ProjectId bodeul-prod-110 `
  -ConfirmProjectId bodeul-prod-110 `
  -ConfirmProjectNumber 649312328770 `
  -ConfirmEnvironment production-infrastructure-audit `
  -OperatorAccount <Google-Cloud-관리자-계정> `
  -ConfirmOperatorAccount <Google-Cloud-관리자-계정> `
  -ConfirmApply APPLY-PRODUCTION-INFRA-AUDITOR
```

스크립트는 현재 활성 계정을 바꾸지 않고 지정한 계정으로만 호출한다. 전용 custom role, `bodeul-infra-auditor` 서비스 계정, `github-actions/bodeul-infra-audit-production` provider와 정확한 WIF impersonation binding을 멱등적으로 구성한다.

GitHub Environment에는 공개 식별자만 변수로 등록한다. 비밀값과 서비스 계정 key는 등록하지 않는다.

현재 진행 단계는 [`tools/gcp/production-infrastructure-state.json`](../../tools/gcp/production-infrastructure-state.json)에 고정한다. 상태가 실제로 바뀌면 리소스 변경과 같은 PR에서 이 파일을 함께 갱신한다. 실제 상태와 기대 상태가 앞서거나 뒤처지면 감사는 드리프트로 실패한다. GitHub Environment 변수로 단계 값을 재정의하지 않는다.

| JSON 필드 / 실행 환경변수 | 현재 값 | 전환 값 |
| --- | --- | --- |
| `cloudRun` / `CLOUD_RUN_EXPECTED_STATE` | `present` | 승인 이미지·정상 revision 유지 |
| `kakaoSecret` / `KAKAO_SECRET_EXPECTED_STATE` | `enabled` | 운영 version 등록 상태 유지. 실제 정상 token·Kakao 호출은 별도 검증 |
| `firestorePitr` / `FIRESTORE_PITR_EXPECTED_STATE` | `enabled` | 7일 version 보존 유지 |
| `firebaseStorageUbla` / `FIREBASE_STORAGE_UBLA_EXPECTED_STATE` | `deferred` | 개발 버킷 실검증 후 `enabled` |
| `appCheck` / `APP_CHECK_EXPECTED_STATE` | `preparing` | 전체 provider·클라이언트 준비 후 `observe`, callable·서비스별 전환 `staged`, 전체 강제 `enforced` |

2026-08-26에 필수 API 15개, 감사 custom role, keyless 서비스 계정, exact-subject WIF provider와 impersonation을 구성했다. Firestore PITR과 7일 version 보존을 활성화했고 Firebase Storage에는 Public Access Prevention을 bucket 수준으로 강제했다. Web App Check에는 canonical production hostname만 허용한 reCAPTCHA Enterprise key와 Auth domain을 구성했으며 enforcement는 `OFF`로 유지했다. UBLA는 유효 조직 정책 아래 활성화하면 바로 되돌릴 수 없으므로, 개발 버킷에서 매니저 서류·채팅 첨부·Core API·보존 정책 경로를 실검증할 때까지 `deferred`로 둔다.

같은 날 deploy와 DB backup provider도 불변 저장소·소유자 ID, 정확한 workflow 경로와 `workflow_dispatch`로 제한하고, 서비스 계정 impersonation을 Environment 전체 principalSet에서 exact subject 하나로 축소했다. 이 변경은 각 기존 workflow 파일과 Environment 이름을 유지하므로 정상 수동 실행 subject는 바뀌지 않는다.

Web App Check 기반 설정은 다음 스크립트로 적용한다. 이 스크립트는 canonical production hostname으로 제한한 reCAPTCHA Enterprise key, Web App Check 설정과 exact Auth domain만 구성하며 enforcement와 Vercel 환경변수는 변경하지 않는다. 예상 밖 기존 설정이나 권한 부족을 확인하면 쓰기 전에 중단한다.

```powershell
.\tools\gcp\configure-production-web-app-check.ps1 `
  -ProjectId bodeul-prod-110 `
  -ConfirmProjectId bodeul-prod-110 `
  -ConfirmProjectNumber 649312328770 `
  -ConfirmWebAppId <production-web-app-id> `
  -ConfirmHostname bodeul-admin-web-iota.vercel.app `
  -OperatorAccount <Google-Cloud-관리자-계정> `
  -ConfirmOperatorAccount <Google-Cloud-관리자-계정> `
  -ConfirmApply APPLY-PRODUCTION-WEB-APP-CHECK
```

운영 provider 강화는 Google Cloud 관리자 권한을 임시로 부여한 뒤 다음 스크립트로 적용한다. 스크립트는 기존 provider와 서비스 계정에 예상 밖 설정이 있으면 변경하지 않고 중단하며, 적용 후 정확한 조건과 단일 subject binding을 다시 검증한다.

```powershell
.\core-api\deploy\cloud-run\harden-production-operational-wif.ps1 `
  -ProjectId bodeul-prod-110 `
  -ConfirmProjectId bodeul-prod-110 `
  -ConfirmProjectNumber 649312328770 `
  -ConfirmDeployEnvironment core-api-production `
  -ConfirmBackupEnvironment core-api-migration-production `
  -OperatorAccount <Google-Cloud-관리자-계정> `
  -ConfirmOperatorAccount <Google-Cloud-관리자-계정> `
  -ConfirmApply APPLY-PRODUCTION-OPERATIONAL-WIF-HARDENING
```

## 실행

1. `master` 최신 commit SHA를 확인한다.
2. Actions의 `Production Infrastructure Audit`을 연다.
3. project ID와 commit SHA를 입력하고 실행한다.
4. `production-infrastructure-audit` 승인을 완료한다.
5. Summary의 baseline 상태와 출시 차단 항목을 확인한다.

2026-08-26 운영 provider 강화, Firestore PITR과 Web App Check 기반 설정을 적용했다. App Check 조회 권한까지 갱신한 뒤 [`master` GitHub WIF 감사 run 32978549310](https://github.com/bodeul110/Bodeul/actions/runs/32978549310)에서 baseline 36개가 모두 통과했고 출시 차단 항목 5개가 분리됐다. 이후 실행 결과도 원시 응답이 아니라 정제된 상태표와 run 링크만 `docs/reports/` 및 Issue #134에 남긴다.
