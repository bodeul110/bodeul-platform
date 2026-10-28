# 비활성 보호자의 기존 예약 승인 조회·철회 검증

기준일: 2026-10-28. 대상은 #419의 기존 승인 조회·철회 복구이며 실제 DEV 사용 개방이 아니다.

## 구현한 내용

보호자가 비활성·삭제되거나 역할이 바뀌어도 활성 환자 본인은 자신이 남긴 승인 메타데이터를 다시 조회하고 철회할 수 있다. GET의 `revocationOnly=true`를 Android에서 엄격하게 확인한 뒤 미리보기 없이 철회 전용 화면을 사용한다. 승인 원문은 숨기고 새 승인·예약 접수는 열지 않는다.

기존 승인 없는 조합, 다른 환자·보호자·역할, 환자 본인 비활성, 계정 조회 장애에는 이 예외를 적용하지 않는다. 미리보기·새 승인·예약 생성은 계속 양쪽 활성 계정을 요구한다. 철회 후 재조회와 반복 철회도 기존 버전·멱등성 규칙을 유지한다.

## 변경된 범위

- Core API 승인 GET의 본인 확인·기존 이력 경계, 추가 응답 필드, 기존 철회 본인 확인 로직 재사용
- Android 전용 Repository의 철회 전용 응답 처리, 관련 단위·합성 화면 테스트
- [API 계약](../architecture/guardian-booking-api.md), [설계 판단](../design/guardian-booking-revocation-recovery.md), [Android 연결 설계](../design/guardian-booking-android.md), 구현 상태와 보고서 목록

DB schema·runtime 권한·migration·환경변수·기능 플래그·의존성은 바꾸지 않았다. 관리자 웹의 응답·권한·테이블 계약도 바꾸지 않았다. 일반 사용자 메뉴와 release 진입점은 여전히 없다.

## 검증

| 검증 | 결과 |
| --- | --- |
| `core-api/gradlew.bat -p core-api check --console=plain` | 554건 통과, 실패·오류·건너뜀 0 |
| `gradlew.bat assembleDebug testDebugUnitTest :app:verifyReleaseAppCheckClasspath --console=plain` | APK 빌드·App Check 경계 통과, Android 단위 테스트 365건 통과 |
| `gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.example.bodeul.debug.GuardianBookingPreviewTest --console=plain` | Android 11/API 30 에뮬레이터 합성 화면 7건 통과, 실패·오류·건너뜀 0 |

Core 단위 테스트는 정상 양쪽 계정, 비활성·삭제·역할 변경 보호자, 미승인·만료·철회 이력, 환자 본인 변경·비활성, 기능 OFF와 Firebase 조회 장애를 구분한다. HTTP 테스트는 최소 메타데이터·명시적 boolean·`no-store` 응답을 확인한다.

Android 테스트는 명시적 철회 모드에서 미리보기 POST를 하지 않음, 철회 완료 이력, 구버전 응답 호환, 잘못된 타입·역할·미승인 응답 거부, 조회 오류를 임의 권한으로 대체하지 않음을 확인한다.

합성 화면에서는 검증되지 않은 본문 숨김, 확인 체크·새 승인 비활성, 접수 버튼 숨김, 철회 후 버전 2 재조회와 반복 작업 차단을 확인했다. 인증 계정·원격 데이터는 사용하지 않았으며 테스트용 읽기 전용 AVD는 검증 후 종료했다.

격리 PostgreSQL 테스트에는 실제 `bodeul_core_runtime` 권한으로 승인 → 보호자 비활성 → 본인 재조회 → 철회 → 재조회·반복 철회 흐름을 추가했다. 예약 INSERT 0건과 감사 이력 2건을 확인한다. 로컬 Docker가 실행 중이 아니어서 이 검증은 PR의 `migration-contract` CI 결과를 별도로 기록한다.

## 남은 범위

- 요청 원문의 인가된 서버 저장·전달·수신과 검증된 상대방 표시 정보
- 실제 DEV V24·V25 적용 여부·runtime 권한 확인, 기능 활성화 승인, 두 계정의 종단 검증
- 실제 화면에서 로그아웃·계정 변경 시 원문 폐기와 프로세스 종료 후 접수 결과 복구

단위·격리 DB·합성 화면 검증을 실기기나 실제 DEV 계정의 E2E 결과로 해석하지 않는다. 원격 DB 쓰기나 배포는 실행하지 않았다.
