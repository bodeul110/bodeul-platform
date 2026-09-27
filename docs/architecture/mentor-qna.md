# 멘토 Q&A 준비

기준일: 2026-09-27

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## Firebase/Firestore

### 왜 Firebase를 선택했나?

초기 MVP에서는 Android 앱, 인증, 데이터, 파일과 알림을 빠르게 연결하는 것이 중요했다. 현재는 Firebase의 역할을 Auth, FCM, Storage, Functions와 인증 프로필·지원·서류처럼 Firebase에 남긴 데이터로 제한했다. 관계형 운영 데이터는 PostgreSQL, 사용자 API는 Spring Core API, 관리자 서버는 Next.js가 담당한다.

### 왜 Firestore를 선택했나?

초기 예약 요청, 동행 세션, 리포트와 상태 변경은 문서 단위 갱신이 많았고 실시간 위치·채팅은 listener와 연결하기 쉬워 Firestore로 시작했다. 개발 환경에서는 해당 업무 데이터를 PostgreSQL로 옮기고 Firestore client 쓰기를 차단했다. Firestore에 남긴 인증 프로필·지원·서류는 PostgreSQL 업무 원본과 역할을 분리한다.

### 왜 MySQL/PostgreSQL이 아닌가?

초기에는 관계형 DB와 API 서버를 함께 운영하는 비용이 기능 검증 속도를 떨어뜨린다고 판단했다. 지금은 Firebase ID token 검증, 역할 인가, 관계형 조회와 감사 경계를 서버에 모을 필요가 커져 Supabase PostgreSQL과 별도 서버를 도입했다. 즉 처음 선택은 개발 속도 기준이었고 현재 전환은 운영 경계 기준이다.

### 나중에 어떻게 바꿀 수 있나?

Firestore 백업을 PostgreSQL로 import하고 row count, 외래키, 주요 필드와 역할별 권한 비교가 통과한 도메인부터 source of truth를 옮긴다. 개발 환경은 이 절차로 예약·매칭·동행·리포트·후속 처리·채팅·읽음·위치를 전환했고, production은 별도 백업·검증·전환 승인을 거쳐 같은 절차를 반복한다. 연말은 초기 목표이며 실제 운영 전환일은 확정하지 않았다.

9월 27일 개발·운영 DB는 모두 V23이며 운영 logical backup·격리 복원·외부 보관을 검증했다. 두 브랜치와 서버·DB·인증 설정을 분리하고 운영 Core 배포 및 최초 개인 관리자 MFA 로그인까지 확인했다. 정상 사용자 업무, 실제 Realtime 소켓, 교차 환경 token 거부와 출시 전 보안 검증은 아직 남아 있다. [Migration 목록](database-migration-catalog.md)과 [환경 분리 실행 기록](../reports/dev-production-separation-2026-09-27.md)을 기준으로 완료 범위를 설명한다.

### 멘토 피드백 이후에는 어떻게 전환하나?

역할을 고정한 혼용 구조를 사용한다. Firebase Auth, FCM, Storage와 Firebase 결합 기능은 유지하고, 예약·세션·채팅·위치·관리자 운영·정산·통계 데이터는 Supabase PostgreSQL로 옮긴다. Spring Core API와 Next.js 관리자 서버는 PostgreSQL 접근, Firebase ID token 검증과 역할 인가를 담당한다.

### 왜 Supabase를 1순위로 잡았나?

PostgreSQL의 관계·트랜잭션과 private Realtime Broadcast를 같은 기반에서 사용할 수 있기 때문이다. 현재 채팅·읽음·상태 이벤트는 서버가 확정하고 클라이언트가 구독하는 계약을 구현했다. 별도 DB와 실시간 서버를 조합하는 대안보다 현재 팀의 운영 부담이 작다고 판단했으며, 실제 비용·동시 연결 요구가 달라지면 재평가한다.

### 왜 Firebase와 Supabase를 섞어서 쓰나?

지금 규모에서는 Firebase가 맡는 Auth, FCM과 Storage까지 한 번에 옮기는 비용이 DB 전환 이익보다 크다. 대신 관계형 조회, 정산, 통계와 운영 감사 데이터는 PostgreSQL로 옮긴다. 중요한 기준은 “두 플랫폼을 막 섞는다”가 아니라 “인증·푸시·파일은 Firebase, 운영 DB는 PostgreSQL”처럼 역할과 source of truth를 고정하는 것이다.

### Oracle Cloud 대신 무엇을 쓰나?

Oracle Free Tier 계정 잠금 이후 Spring Core API의 개발 실행 환경은 Google Cloud Run으로 변경했다. 현재 API는 상태를 로컬 디스크에 저장하지 않고 Supabase PostgreSQL을 사용하므로 24시간 VM보다 요청 기반 컨테이너가 현재 규모에 맞다. Cloud Run은 Java 21 컨테이너, 기본 HTTPS, revision rollback과 Firebase 서비스 계정 ADC를 제공한다.

개발 환경은 Tokyo의 `bodeul-core-api-preview`를 최소 인스턴스 0, 최대 인스턴스 1로 운영한다. Cloudflare는 도메인이 생긴 뒤 DNS와 WAF 계층으로 검토하며 Spring을 Workers로 다시 작성하지 않는다.

## 앱 구조

### Activity는 무엇을 담당하나?

Activity는 생명주기, 권한 요청, 화면 이동, 저장소 호출 연결만 담당한다. 화면에 보여줄 데이터 조합과 문자열 정책은 Coordinator와 Formatter로 분리한다.

### Coordinator는 왜 있는가?

Repository에서 받은 도메인 데이터를 화면 모델로 바꾸기 위해 있다. Activity 안에 상태 분기와 카드 조합이 쌓이는 문제를 줄인다.

### Binder는 왜 있는가?

XML View에 ScreenModel을 반복적으로 연결하는 코드를 Activity에서 분리하기 위해 있다. 카드 목록, 상태 배지, 버튼 표시 같은 렌더링 규칙을 모은다.

### Repository는 왜 있는가?

Core API, Firebase 결합 기능과 Mock 구현을 화면 흐름에서 분리하기 위해 있다. 데이터 접근 계약을 숨기면 화면이 HTTP·Firebase SDK 세부 사항을 직접 관리하지 않아도 된다.

### Mock 모드는 왜 있는가?

Firebase 설정이 없는 환경에서 화면 데모와 테스트를 할 수 있게 하기 위해 있다. CI/Dependabot 환경에서도 `google-services.json` 없이 컴파일할 수 있다. Firebase 연동 모드의 Core API 오류가 Mock이나 Firestore 쓰기로 자동 우회되는 구조는 아니다.

## 관리자 웹

### 관리자 웹은 왜 필요한가?

관리자 웹은 서비스 신뢰성을 위한 운영 도구다. 매니저 서류 심사, 신고/문의 처리, 운영 상태 확인, 민감정보 마스킹, 관리자 세션 관리를 앱 사용자 흐름과 분리한다.

### 왜 Vercel인가?

관리자 웹은 Firebase ID token 검증과 PostgreSQL 관리자 role 조회를 서버에서 수행해야 하므로 정적 Hosting보다 Next.js server runtime이 필요하다. 별도 저장소를 Vercel Git 연동으로 배포해 PR Preview와 master target을 분리했고, 실제 Preview에서 401·403·200을 검증했다. 기존 Firebase Hosting과 전용 WIF 자원은 종료했다.

## 보안/운영

### 관리자는 어떻게 구분하나?

Next.js 서버가 Firebase ID token, PostgreSQL `app_users.ADMIN`과 활성 `SUPER_ADMIN`·`OPERATIONS`·`DEVELOPER` 세부 역할을 확인한다. 일반 테이블 직접 쓰기는 제한하고 배정·결제·감사 등 허용된 DB 함수만 실행한다. 브라우저 ADMIN의 Firestore/Storage 직접 접근은 차단한다. Realtime용 `role: authenticated` claim은 관리자 권한이 아니다. [관리자 RBAC](admin-rbac.md)와 [Rules 경계](../security/firebase-rules-validation.md)를 함께 본다.

### App Check는 왜 아직 강제하지 않았나?

초기에는 시연, preview, 디버그 환경이 자주 바뀌어서 enforcement를 바로 켜면 정상 검증이 막힐 수 있다. App Check는 Auth와 Rules를 대체하는 기능이 아니라 정상 앱/기기에서 온 요청인지 확인하는 추가 방어선이다. 현재는 Android debug/release provider, 관리자 웹 site key 경로, Functions 전환 스위치까지 준비했고, debug token과 웹 site key, 릴리스 provider 검증이 끝난 뒤 Functions, Storage, Firestore 순서로 단계적으로 강제한다. 세부 기준은 [App Check 적용 로드맵](../operations/app-check-enforcement-roadmap.md)에 정리했다.

### 백업/복원은 실제로 테스트했나?

Firestore는 emulator에서 백업 apply와 복원 후 diff를 검증했다. 9월 27일 production V23 logical dump를 격리 PostgreSQL 17에 복원하고 manifest·외부 보관 checksum을 대조했다. Supabase 조직 Pro 전환은 완료됐으며 제공자 백업의 실제 복구 지점·보존과 Storage 파일 복원은 별도로 확인한다. 운영 DB 자체에 복원한 결과는 아니다.

### API Key는 어디에 두나?

Firebase Web API Key는 클라이언트 설정에 포함될 수 있지만 Auth 허용 도메인, Rules와 App Check를 함께 적용한다. Kakao Local REST 키는 Google Secret Manager에 두고 Cloud Run Core API만 읽는다. DB URL과 서버 전용 비밀값은 Vercel 또는 Cloud Run server runtime에만 주입하고 Git과 브라우저 환경변수에는 넣지 않는다.
