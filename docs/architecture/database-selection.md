# DB 선택 근거

초기 선택·전환 기록: 2026-07-19. 현재 경계 대조: 2026-09-21.

BoDeul의 초기 MVP는 Cloud Firestore를 주 저장소로 사용했다. 현재 운영 목표는 `Spring Core API + Next.js 관리자 서버 + 공용 Supabase PostgreSQL`이며 Firebase는 Auth, FCM, App Check, Storage, 결합 Functions와 인증 프로필·지원·서류 데이터만 유지한다.

개발 Android의 Core 업무 쓰기는 PostgreSQL로 전환했다. 9월 27일에는 개발·운영 DB V23, 운영 Core 배포·관리자 DB 연결과 최초 MFA 로그인을 확인했다. 실제 사용자 트래픽 전환일은 미정이며 업무·보안·복구 게이트를 별도로 통과해야 한다. 이 문서의 Firestore 비교는 초기 선택 기록이다. 현재 결정은 [PostgreSQL 운영 전환 결정](postgres-operational-transition.md), 적용 증거는 [환경 분리 실행 기록](../reports/dev-production-separation-2026-09-27.md)을 따른다.

## 초기 판단 기준

- Android 앱과 관리자 웹이 같은 사용자, 예약, 동행, 리포트, 문의 데이터를 읽고 써야 한다.
- 환자, 보호자, 매니저, 관리자 역할별 접근 경계가 Firestore Rules와 Storage Rules로 표현 가능해야 한다.
- 실시간 위치, 안심 채팅, 예약 상태 변경처럼 문서 단위 실시간 반영이 중요하다.
- 초기 운영에서는 별도 DB 서버, API 서버, 배포 파이프라인을 최소화해야 한다.
- Firebase Auth, Storage, Functions, FCM과 같은 프로젝트 안에서 묶어 운영하는 편이 개발 속도와 운영 단순성이 높다.

## 대안 비교

| 후보 | 장점 | 리스크 | 현재 판단 |
| --- | --- | --- | --- |
| Cloud Firestore | Auth/Rules/Functions/FCM/Storage와 통합이 쉽고, 문서 단위 실시간 구독이 자연스럽다. 서버 없이 Android 앱과 관리자 웹이 같은 계약을 공유할 수 있다. | 조인, 복잡한 집계, 장기 분석 쿼리에는 약하다. 두 DB를 병행하면 정합성과 복구 기준이 분기된다. | 초기 MVP 선택 기록이며 운영 업무 원본에서는 제거한다. |
| MySQL/PostgreSQL + Spring/Next.js API | 관계형 무결성, 조인, 정산/통계 쿼리에 강하다. | API 서버, 인증/권한, 배포와 장애 대응을 운영해야 한다. | 현재 채택했다. |
| Supabase/PostgreSQL | 관리형 PostgreSQL, connection pooler, 백업과 Realtime을 제공한다. | Firebase Auth·FCM·Storage와 운영 공급자가 나뉜다. | 공용 DB와 실시간 전달 계층으로 채택했다. |
| Firebase Realtime Database | 실시간 상태 동기화에 단순하고 빠르다. | 계층형 JSON 모델이라 예약, 리포트, 관리자 운영 컬렉션처럼 도메인별 문서 계약을 관리하기 어렵다. 다운로드량 중심 과금 리스크도 크다. | 위치 전용 보조 저장소가 필요해질 때만 검토한다. |
| 자체 VM + DB 직접 운영 | 인프라 제어권이 가장 크다. | 보안 패치, 백업, 장애 대응, 배포 자동화까지 모두 직접 책임져야 한다. 현재 팀 효율 기준으로 과하다. | 현재 단계에서는 제외한다. |

## 초기 Firestore가 맞았던 이유

- `users`, `appointmentRequests`, `companionSessions`, `sessionReports`, `appointmentFollowUps`, `supportInquiries`는 문서 단위로 읽고 갱신하는 흐름이 중심이다.
- 세션 위치, 채팅, 문의 답변, 예약 상태 변경은 문서 변경 감지와 FCM 트리거로 연결하기 좋다.
- 관리자 웹도 Firebase Auth 로그인 뒤 Firestore와 Storage를 직접 읽는 구조라 별도 관리자 API 서버가 필요하지 않다.
- 운영 도구는 `tools/firebase`에서 REST 기반으로 상태 점검, 백업, 복원, 리포트를 수행하므로 같은 데이터 계약을 재사용할 수 있다.

## 초기 보완 기준

- 서버 검증이 필요한 작업은 Cloud Functions callable 또는 Firestore trigger로 이동한다.
- 당시 역할은 `users/{uid}.role`과 Rules 중심이었다. 현재 관리자는 PostgreSQL 세부 역할·서버 감사로 바뀌었고 브라우저 ADMIN은 차단한다. Realtime용 `role: authenticated` claim은 관리자 권한과 별개다.
- 관리자 대시보드가 전체 컬렉션 스캔에 가까워지면 서버 집계 문서나 페이지네이션 쿼리로 바꾼다.
- 정산, 통계, 장기 분석이 커지면 BigQuery export 또는 PostgreSQL 보조 저장소를 검토한다.

## 이전 검토 기준

아래 조건 중 둘 이상이 실제 운영 지표로 확인되면 Firestore 단독 구조를 다시 검토한다.

- 관리자 대시보드와 리포트가 일 단위 무료 읽기 한도를 반복적으로 넘긴다.
- 예약/세션/정산 화면에서 여러 컬렉션 조인이 주 기능이 된다.
- 검색, 통계, 정산 쿼리가 앱 화면보다 운영 분석 중심으로 이동한다.
- Rules로 표현하기 어려운 복잡한 권한이 늘어나 대부분의 쓰기를 Functions로 우회하게 된다.

## 참고 공식 문서

- Firebase 가격표: <https://firebase.google.com/pricing>
- Firestore 과금 방식: <https://firebase.google.com/docs/firestore/pricing>
- Google Cloud Firestore 가격표: <https://cloud.google.com/firestore/pricing>
