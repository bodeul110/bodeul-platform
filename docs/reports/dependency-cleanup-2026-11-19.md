# 개발 의존성 PR 정리

## 판단 기준

- 작업 목적: 누적된 의존성 PR을 최신 `dev`에서 함께 검증하고 운영 브랜치 직접 반영을 막는다.
- 선택한 방식: 승인된 앱·Core API·Firebase 도구 변경을 개발용 통합 PR으로 검증한다. 원래 PR은 반영 확인 후 대체 링크와 함께 닫는다.
- 대안: 오래된 `master` 대상 PR을 그대로 병합하거나 각 lockfile 변경을 따로 병합하는 방식을 검토했다.
- 선택 이유: 현재 개발·운영 분리 구조에서는 최신 개발 코드와의 호환성과 의존성 조합을 함께 확인하는 것이 우선이다.
- 리스크: 로컬 테스트와 CI 성공은 실제 운영 인증·데이터·배포 검증을 대신하지 않는다. 운영 반영은 별도 출시 절차를 따른다.

## 범위

| 구분 | 기존 PR | 변경 |
| --- | --- | --- |
| Android 빌드 | #512, #529 | AGP 9.5.1, Google Services 4.6.0 |
| Core API | #531, #532 | Gradle 8.14.7, Firebase Admin 9.12.0 |
| Functions | #504, #519 | body-parser 2.4.0, fast-xml-parser 5.12.0 및 필요한 하위 의존성 |
| Firebase 도구 | #503, #520, #523, #524 | fast-uri 3.1.9, @hono/node-server 2.1.8, firebase-tools 15.36.0, hono 4.13.18 |

Firebase BoM 35 전환(#530)과 postgres 4 전환(#533)은 이번 승인 범위에서 제외한다. Java 21, Node 22, Spring Boot, Android SDK 수준, Firebase Rules, API·DB 계약, 데이터와 배포 설정은 바꾸지 않는다. 관리자 웹 계약에도 변경이 없다.

## 검증

- Java 21에서 `assembleDebug testDebugUnitTest assembleDebugAndroidTest :app:verifyReleaseAppCheckClasspath` 성공, 앱 단위 테스트 539건 통과.
- Java 21에서 `core-api check` 성공, 단위 테스트 599건 통과.
- Node 22.23.4에서 Functions `npm ci`와 테스트 성공: 98건 통과, 외부 환경이 필요한 3건은 기존 조건에 따라 건너뜀.
- 같은 Node 22에서 Firebase 도구 `npm ci`, `test:toolkit` 76건, `preflight:ci` 통과.
- `preflight:local`은 격리 폴더에 Firebase 프로젝트 설정이 없어 원격 점검 단계에서 중단됐다. Android 검사는 통과했다. 실제 프로젝트 설정을 복사하지 않고 CI용 검사를 별도로 통과시켰다.
- 로컬 8080 포트가 사용 중이므로 Firestore·Storage 에뮬레이터, PostgreSQL migration 계약과 컨테이너 검증은 통합 PR의 CI에서 확인한다. 해당 검사 성공 전에는 병합하지 않는다.
- 앱은 비밀값 없는 Mock/CI 구성으로 빌드했다. 실기기 로그인, 원격 데이터 조회·쓰기, 서비스 배포는 실행하지 않았다.
