# BoDeul 작업 규칙

## 기본 원칙

- UI 문구, 사용자에게 보이는 오류/안내 문구, 주석, 문서의 설명 문장은 한국어로 작성한다.
- 파일 인코딩은 UTF-8을 유지한다. 깨진 한글이나 mojibake를 발견하면 해당 파일을 수정할 때 함께 바로잡는다.
- 사용자 승인 없이 의존성 버전, Gradle/Android SDK 버전, Node 런타임 버전, Firebase 패키지 버전을 올리지 않는다.
- 보안값, API 키, `google-services.json`, `local.properties`, Firebase 토큰, 서비스 계정 키는 커밋하지 않는다.
- 기존 사용자 변경을 되돌리지 않는다. 특히 문서 정리, 파일 이동, 로컬 설정 변경이 섞인 상태에서는 요청 범위의 파일만 좁게 수정한다.

## 프로젝트 구조

- `app/`: Java 기반 Android 앱. 화면 흐름, Core API와 Firebase 결합 데이터 접근, 인증/예약/위치/리포트 기능을 포함한다.
- `core-api/`: Java 21 + Spring Boot 기반 사용자 서비스 API. Google Cloud Run에 독립 배포한다.
- `functions/`: Firebase Functions. Node 22 기준으로 운영한다.
- `tools/firebase/`: Firebase 점검, 백업, seed, preflight, 운영 리포트용 Node 스크립트.
- `docs/`: 설계, 운영, 보안, 상태, 보고서 문서의 기준 위치.
- `.github/`: PR/Issue 템플릿, CODEOWNERS, Dependabot, SECURITY 정책, Actions workflow.

- 관리자 웹·Next.js 서버의 source of truth는 별도 `bodeul110/bodeul-admin-web` 저장소다. 메인 저장소의 과거 `admin-web/` 로컬 산출물을 소스로 취급하지 않는다.
- 현재 개발은 Codex와 CLI 기준이며 Android Studio는 선택 사항이다. Android 소스 Java 17, Gradle 실행 JDK 21 기준을 구분한다.

## 관리자 웹 작업

- 웹 UI·Next.js 서버·Vercel 설정·웹 전용 환경변수는 별도 웹 저장소에서 수정한다.
- 메인 저장소의 Rules·PostgreSQL schema·Core API 계약 변경은 웹 영향과 검증을 공용 문서에 남긴다.
- 웹 검증은 해당 저장소의 현재 package script와 CI를 따른다. 메인 저장소에서 과거 `npm --prefix admin-web ...`를 실행했다고 기록하지 않는다.

## Android 앱 작업

- 새 기능, 리팩터링, 버그 수정 후에는 반드시 `.\gradlew.bat assembleDebug --console=plain`로 검증한다.
- 화면, 도메인, 데이터 로직은 역할이 분리된 객체로 나눈다. Activity/Fragment에는 화면 흐름 제어와 연결 코드만 남긴다.
- Firebase, 인증, 위치, 예약, 리포트처럼 외부 상태와 연결되는 코드는 Repository/Service 계층에 둔다.
- UI 문구는 하드코딩을 피하고 가능한 리소스 문자열로 관리한다.
- `google-services.json`이 없는 CI/Dependabot 환경에서도 컴파일이 깨지지 않도록 fallback을 고려한다.
- 사용자가 볼 수 없는 내부 로그도 한국어 맥락을 유지하되, 민감정보는 남기지 않는다.

## Firebase와 운영 스크립트

- `functions/` 변경은 Node 22 기준을 유지한다.
- Firebase Rules, Functions, 운영 스크립트 변경은 `docs/operations/firebase/` 또는 `docs/reports/`에 영향 범위와 검증 결과를 남긴다.
- `tools/firebase/` 스크립트 변경은 가능한 경우 `npm --prefix tools/firebase run preflight:local` 또는 관련 개별 스크립트로 검증한다.
- 운영 데이터에 쓰기 작업을 하는 스크립트는 기본적으로 dry-run 경로를 먼저 사용하고, apply 실행은 명시적 요청이 있을 때만 한다.
- Firestore/Storage Rules 변경은 배포 전에 로컬 검증과 영향 범위 문서화를 우선한다.

## Core API 작업

- `core-api/`는 메인 저장소에서 관리하되 Cloud Run의 독립 서비스로 배포한다.
- 사용자·매니저 앱 계약은 Spring에 직접 구현하고 다른 API 서버를 중간 proxy로 두지 않는다.
- Java 21과 현재 Spring Boot 3.5.x 기준을 사용자 승인 없이 올리지 않는다.
- Firebase ID token 검증, PostgreSQL role 인가, 외부 API key 처리는 서버 경계에 둔다.
- 변경 후 `.\core-api\gradlew.bat -p core-api check --console=plain`로 검증한다.
- DB migration·운영 배포·백업 복원은 각 수동 workflow 경계를 유지한다. 소스 migration 존재와 환경별 실제 적용 완료를 구분한다.

## GitHub 운영

- `dev`와 `master`는 PR과 `preflight` 체크를 거쳐 반영한다. 새 기능은 `dev`를 대상으로 한다.
- `dev → master` 출시 PR과 `master → dev` 동기화 PR은 공통 이력을 보존하도록 merge commit을 사용하고, 일반 기능 PR은 squash merge한다.
- 개발 Core API 배포와 개발 DB migration은 `dev`, 운영 배포와 운영 DB migration은 `master`에서 실행한다. DB migration은 자동 배포와 분리된 수동 실행으로 유지한다.
- merge 방식은 squash merge를 기본으로 한다.
- Dependabot PR은 의존성 변경이므로 사용자 승인 없이 병합하지 않는다.
- GitHub Project `BoDeul 작업 백로그`와 Issue/Milestone을 실제 작업 추적의 기준으로 사용한다.
- PR 제목에는 `[codex]`, `[AI]`처럼 사용한 도구를 표시하지 않고 실제 변경 내용을 적는다.
- 일반 PR 본문은 `배경`, `변경 내용`, `확인`, 필요한 경우 `참고할 점`만 짧게 적는다. 빈 구획이나 작업과 무관한 체크리스트는 남기지 않는다.
- 설계, 보안, 인프라처럼 판단 근거가 중요한 PR에는 선택한 방식, 검토한 대안, 선택 이유, 리스크를 자연스러운 문장으로 덧붙인다.
- 리뷰 댓글은 결론과 확인 근거를 바로 적는다. 모든 댓글에 `리뷰 결과`, `병합 판단`, `남은 범위` 같은 형식의 제목을 반복하지 않는다.
- 실행하지 않은 검증이나 확인하지 않은 운영 상태를 수행한 것처럼 적지 않는다.
- “AI가 제안했다”는 선택 이유가 될 수 없다. AI 제안을 검토했더라도 최종 판단 근거는 현재 프로젝트 규모, 대안, 리스크와 연결해 적는다.
- 보안 취약점이나 실제 비밀값은 공개 Issue/PR 댓글에 적지 않고 private vulnerability reporting 경로를 사용한다.

## 검증 기준

- Android 앱 코드 변경: `.\gradlew.bat assembleDebug --console=plain`
- Core API 변경: `.\core-api\gradlew.bat -p core-api check --console=plain`
- Firebase 운영 스크립트 변경: `npm --prefix tools/firebase run preflight:local` 또는 관련 스크립트
- Functions 변경: `npm --prefix functions test` (Node 22)
- Firestore/Storage Rules: `npm --prefix tools/firebase run test:rules`
- Firebase 도구 단위 테스트: `npm --prefix tools/firebase run test:toolkit`
- GitHub YAML 변경: `yq e '.' <파일>`로 파싱 확인
- 문서 전용 변경은 빌드가 필요하지 않지만, 링크와 경로가 현재 구조와 맞는지 확인한다.

## 문서화

- 기능 개발이나 구조 변경 전후에는 [설계 판단 기록 규칙](docs/architecture/decision-log.md)의 `작업 목적`, `선택한 방식`, `대안`, `선택 이유`, `리스크`를 한 줄씩이라도 남긴다.
- 선택 근거는 “현재 MVP 규모에서는”, “운영 부담 기준으로는”, “데이터/트래픽이 커지면”처럼 현재 규모와 전환 조건에 연결해 설명한다.
- 작업 완료 후 답변과 문서에 `구현한 내용`, `변경된 범위`, `검증`, `남은 범위`를 정리한다.
- 설계 판단은 `docs/design/`, 아키텍처와 데이터 계약은 `docs/architecture/`, 운영 절차는 `docs/operations/`, 결과 보고는 `docs/reports/`에 둔다.
- 문서 이동이나 이름 변경은 기존 링크 영향까지 확인한다.
- 코드 의도를 이해해야 하는 부분에는 목적이 드러나는 짧은 한국어 주석을 남긴다.
