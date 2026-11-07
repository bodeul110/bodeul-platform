# 보호자 예약 수신함 구현·검증

## 구현한 내용

#502의 리뷰 보완 사항을 반영하고 최신 `dev`에서 빌드·단위 테스트 402건·환경 혼합 차단 4건·PR CI를 확인해 `44fee2c`로 병합했다. 병합 후 preflight와 Android CodeQL도 통과했다.

후속 수신함은 다음을 구현했다.

- 현재 참여 계정의 요청 목록과 서버 원문 상세, 현재 프로필 이름·마스킹 전화번호 표시
- 전체 내용·성인 환자 본인 확인 후 승인, 최신 승인 버전으로 철회
- 명시적 철회 전용 모드, 조회 실패·빈 목록·원문 만료·저장 결과 불명 구분
- 계정 변경·로그아웃·백그라운드·재생성 시 원문과 확인 체크 정리, 늦은 응답 폐기
- 일정·이력 진입점과 개발 기본 OFF·Release 고정 OFF, Firebase 미설정 빌드에서 진입 차단

## 변경된 범위

Android Activity·Coordinator·Binder·인증 세션 관찰·리소스와 테스트, 관련 설계·상태 문서만 변경했다. 기존 직접 본문 화면의 원문 포맷을 공용 Formatter로 옮겼으며 기존 합성 UI 회귀도 실행했다. [설계 판단](../design/guardian-booking-inbox.md)을 따른다.

기능을 실제 환경에서 켜거나 운영·개발 DB, Core API, Functions, 관리자 웹, 의존성 버전을 변경하지 않았다. 수신함에서는 보호자의 원문 작성과 예약 접수를 실행하지 않는다.

## 검증

| 검증 | 결과 |
| --- | --- |
| `assembleDebug testDebugUnitTest assembleDebugAndroidTest :app:verifyReleaseAppCheckClasspath` | 성공. Firebase 설정 없는 별도 작업 폴더의 Mock/CI 빌드 |
| Android 단위 테스트 | 428건, 실패·오류·skip 0. 신규 수신함 Coordinator 26건 |
| `node tools/android/check-environment-boundary.mjs` | 개발/운영 Core API·Realtime 혼합 차단 4건 성공 |
| Android 11/API 30 에뮬레이터, 360×640 dp | 수신함 10건 + 기존 승인 화면 7건 성공 |
| 동일 에뮬레이터 글꼴 배율 1.3 | 수신함 10건 성공. 목록·상세·승인 버튼 캡처 확인 후 배율 원복 |
| Release 수신함 플래그 | 개발 opt-in을 `true`로 전달해도 생성된 Release bool은 `false` |

Release 플래그는 **리소스 생성만** 확인했다. 최초 리소스 생성 요청은 기존 Release 서명 검증 때문에 중단됐으며, `:app:generateReleaseResValues -PbodeulGuardianBookingInboxEnabled=true -x :app:preReleaseBuild --no-configuration-cache`로 리소스만 생성해 검사했다. Release APK 빌드·서명·운영 배포 통과 증거가 아니다. Debug APK와 검증 APK는 기본 OFF 설정으로 다시 빌드했다.

화면 테스트는 합성 계정·메모리 Repository를 사용하며 Firebase 로그인·원격 API·DB에 접근하지 않는다. 합성 화면만 캡처를 허용하고 실제 수신함은 `FLAG_SECURE`를 유지한다. 실제 Activity의 기본 비활성·비공개 선언도 검증했다. 로컬 캡처는 `.tmp/guardian-booking-inbox/`에 보관하고 커밋하지 않는다.

## 남은 범위

- 보호자 새 요청 작성·상대방 지정, 원문 만료·프로세스 종료 이후에도 같은 요청의 접수 결과 복구
- 실제 DEV V24~V27 migration·계정 조회/원문 파기 권한·파기 스케줄 검증
- 환자·보호자 실제 로그인 두 계정과 실기기의 종단 검증, 승인 후 별도 기능 활성화
- 최신 Figma 수신함 시안 대조와 운영 개방

이번에는 실기기가 연결되지 않았고 최신 Notion·Figma를 다시 열람하지 않았다. 기존 #419 API 계약과 기획 기준으로 구현했으며, 합성 테스트 성공을 실제 서비스 개방이나 #419 전체 완료로 보지 않는다.
