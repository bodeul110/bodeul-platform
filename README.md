<div align="center">

# 보들 (BoDeul)

**환자·보호자·매니저·운영자를 연결하는 병원 동행 플랫폼**

[![Android Preflight](https://github.com/bodeul110/bodeul-platform/actions/workflows/android-preflight.yml/badge.svg?branch=master)](https://github.com/bodeul110/bodeul-platform/actions/workflows/android-preflight.yml)
[![Core API](https://github.com/bodeul110/bodeul-platform/actions/workflows/core-api.yml/badge.svg?branch=master)](https://github.com/bodeul110/bodeul-platform/actions/workflows/core-api.yml)
[![Firebase Rules](https://github.com/bodeul110/bodeul-platform/actions/workflows/firebase-rules.yml/badge.svg?branch=master)](https://github.com/bodeul110/bodeul-platform/actions/workflows/firebase-rules.yml)
[![CodeQL](https://github.com/bodeul110/bodeul-platform/actions/workflows/codeql.yml/badge.svg?branch=master)](https://github.com/bodeul110/bodeul-platform/actions/workflows/codeql.yml)

[문서](docs/README.md) · [아키텍처](docs/architecture/infra-overview.md) · [관리자 웹 저장소](https://github.com/bodeul110/bodeul-admin-web) · [이슈](https://github.com/bodeul110/bodeul-platform/issues)

</div>

## 프로젝트 소개

보들은 병원 방문에 도움이 필요한 환자와 보호자에게 검증된 동행 매니저를 연결하고, 예약부터 동행 종료까지의 과정을 하나의 흐름으로 관리하는 서비스입니다. Android 앱은 환자·보호자·매니저의 현장 경험을 제공하고, 별도 관리자 웹은 심사와 매칭, 문의 대응 등 운영 업무를 담당합니다.

| 사용자 | 주요 경험 |
| --- | --- |
| 환자 | 동행 요청, 일정 확인, 동의 기반 정보 공유와 채팅, 동행 리포트 확인 |
| 보호자 | 환자 동의 범위 내 예약·동행 진행과 리포트 확인 |
| 매니저 | 자격 서류 제출, 일정 관리, 동행 수행, 최종 리포트 작성 |
| 운영자 | 매니저 심사, 매칭 관리, 문의 처리, 병원 가이드와 운영 데이터 관리 |

## 핵심 기능

| 영역 | 기능 |
| --- | --- |
| 계정과 권한 | 이메일·소셜 로그인, 이메일 인증, 환자·보호자·매니저·관리자 역할 분리 |
| 예약과 매칭 | 동행 요청, 일정 조율, 매니저 배정, 진행 상태와 후속 처리 |
| 실시간 동행 | 채팅, 읽음 처리, 이미지·PDF 첨부, FCM 알림 |
| 위치와 동의 | 내 위치 확인과 서버 공유 분리. 위치 수집·공유는 동의·종료·파기 정책에 따라 별도 활성화 |
| 매니저 업무 | 자격 서류 심사, 일정과 병원 가이드, 동행 기록, 최종 리포트 |
| 관리자 운영 | 매칭, 심사, 문의·답변, 병원 가이드, 민감정보 보호와 감사 흐름 |
| 운영 자동화 | 데이터 점검, seed, 백업·복원, 보관 기간에 따른 자동 파기 |

## 서비스 구조

관리자 웹과 사용자 앱은 서로 다른 서버 경계를 사용하지만 **같은 환경의 PostgreSQL**을 바라봅니다. 개발과 운영은 서버·DB·Firebase 프로젝트를 분리합니다. 각 서버는 독립된 최소 권한 DB role을 사용하며, 클라이언트는 데이터베이스에 직접 연결하지 않습니다.

```mermaid
flowchart LR
  App["Android 앱<br/>환자 · 보호자 · 매니저"]
  Web["관리자 웹<br/>운영자"]
  Core["코어 서버<br/>Spring · Cloud Run"]
  Admin["관리자 전용 서버<br/>Next.js · Vercel"]
  DB[("공용 데이터베이스<br/>Supabase PostgreSQL + Realtime")]
  Firebase["Firebase 지원 플랫폼<br/>Auth · FCM · Storage · Firestore"]
  Jobs["Cloud Functions v2<br/>알림 · 자동 파기"]
  Kakao["Kakao APIs<br/>로그인 · 지도 · 장소 검색"]

  App -->|"사용자 API"| Core
  Web -->|"관리 API"| Admin
  Core -->|"Core role"| DB
  Admin -->|"Admin role"| DB
  Jobs -->|"Retention role"| DB
  Core -->|"장소 검색"| Kakao
  App -.->|"인증 · 알림 · 비이전 기능"| Firebase
  Core -.->|"토큰 검증 · 채팅 첨부 · 푸시"| Firebase
  Admin -.->|"토큰 검증 · 서류 심사"| Firebase
  Jobs -->|"FCM · Storage"| Firebase

  classDef client fill:#dbeafe,stroke:#0969da,color:#0d1117,stroke-width:2px;
  classDef backend fill:#fff1c2,stroke:#9a6700,color:#0d1117,stroke-width:2px;
  classDef platform fill:#dafbe1,stroke:#1a7f37,color:#0d1117,stroke-width:2px;
  classDef external fill:#f6f8fa,stroke:#57606a,color:#0d1117,stroke-width:2px;
  class App,Web client;
  class Core,Admin backend;
  class DB,Firebase,Jobs platform;
  class Kakao external;
```

### 데이터 책임

| 데이터 | 기준 시스템 |
| --- | --- |
| 로그인과 사용자 세션 | Firebase Authentication |
| 예약·매칭·동행·채팅·위치·리포트 | Supabase PostgreSQL |
| 실시간 변경 알림 | Supabase Realtime private Broadcast |
| 첨부파일과 증빙 원본 | Firebase Storage |
| 푸시 알림 | Firebase Cloud Messaging |
| Firebase 결합 프로필·지원·서류 | Cloud Firestore |
| 장소 검색 | Spring Core API를 통한 Kakao Local REST API |

설계 배경과 세부 요청 흐름은 [인프라 구성](docs/architecture/infrastructure.md), [데이터 및 API 계약](docs/architecture/data-api.md), [목표 인프라 구조](docs/architecture/target-infrastructure.md)에서 확인할 수 있습니다.

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Android | Java 17, XML, Android SDK 37, Firebase Android SDK, Kakao SDK |
| Core API | Java 21, Spring Boot 3.5.16, Spring Security, JDBC, Flyway |
| 관리자 웹 | TypeScript, React 19, Next.js 16, Tailwind CSS 4, Vercel |
| 데이터 | PostgreSQL 17, Supabase, Supabase Realtime |
| Firebase | Authentication, Firestore, Storage, Functions v2, FCM, App Check |
| 인프라 | Google Cloud Run, Artifact Registry, Secret Manager, WIF |
| CI·운영 | GitHub Actions, Firebase CLI, Node.js 22 운영 도구 |

## 저장소 구성

```text
Bodeul/
├─ app/                    Android 앱
├─ core-api/               Spring Core API와 Flyway migration
├─ functions/              Firebase Functions v2
├─ tools/firebase/         seed, 점검, 백업·복원, 운영 리포트
├─ docs/                   설계, 운영, 보안, 검증 문서
├─ .github/workflows/      CI/CD와 수동 운영 workflow
├─ firestore.rules         Firestore 권한 규칙
├─ storage.rules           Storage 권한 규칙
└─ firebase.json           Firebase 배포 설정
```

관리자 UI, Next.js 서버와 Vercel 배포는 별도 [bodeul-admin-web](https://github.com/bodeul110/bodeul-admin-web) 저장소에서 관리합니다. 이 저장소는 Android 앱, Core API, 공용 데이터 계약, Firebase Rules·Functions를 소유합니다.

## 개발 시작

기능 개발은 `dev`에서 분기해 `dev`로 PR을 보냅니다. `master`는 운영 브랜치이며, 검증된 `dev`를 release PR으로 승격합니다. 환경별 주소와 배포 절차는 [개발·운영 환경 기준](docs/operations/dev-production-branch-transition-plan.md)을 따릅니다.

### 요구 환경

| 대상 | 요구 사항 |
| --- | --- |
| Android | Codex/CLI, Gradle 실행용 JDK 21, Android SDK 37. 앱 소스 호환성은 Java 17이며 Android Studio는 선택 사항 |
| Core API | JDK 21 |
| Functions·운영 도구 | Node.js 22, npm |
| Firebase 작업 | Firebase CLI |

### 기본 검증

```powershell
# Android
.\gradlew.bat assembleDebug --console=plain

# Core API
.\core-api\gradlew.bat -p core-api check --console=plain

# Firebase Functions
npm --prefix functions ci
npm --prefix functions test

# Firebase 운영 도구
npm --prefix tools/firebase ci
npm --prefix tools/firebase run preflight:local
```

`google-services.json`, `local.properties`, DB 접속 정보, API key와 서비스 계정 key는 저장소에 커밋하지 않습니다. 역할별 테스트와 환경 설정은 [내부 테스트 가이드](docs/operations/internal-test-guide.md)와 [Core API 인프라 런북](docs/operations/core-api-infrastructure-runbook.md)을 따릅니다.

## 문서

| 문서 | 내용 |
| --- | --- |
| [문서 홈](docs/README.md) | 프로젝트 문서 전체 색인 |
| [Android 앱 구조](docs/architecture/app-architecture.md) | Activity, Coordinator, Binder, Repository 책임 |
| [인프라 구성](docs/architecture/infrastructure.md) | 서버, 데이터, 인증, 배포 경계 |
| [데이터 및 API 계약](docs/architecture/data-api.md) | 도메인 데이터와 API 기준 |
| [보안 문서](docs/security/README.md) | 권한, Rules, App Check와 보안 원칙 |
| [협업 규칙](docs/operations/collaboration-rules.md) | 브랜치, PR, 검증과 문서화 방식 |

작업은 [GitHub Issues](https://github.com/bodeul110/bodeul-platform/issues)와 `BoDeul 작업 백로그`에서 관리합니다.
