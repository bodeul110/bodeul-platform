# #448 예약 제출·키보드·OS 내비게이션 실기기 검증

검증일: 2026-09-29(KST). 제품 코드 기준: PR #474가 병합된 `dev`의 `7170812`.

## 구현한 내용

`ClientNavigationReturnTest`에 합성 예약 접수부터 전체 탭 복귀까지 검증하는 테스트를 추가했다. 기존 실기기에는 Debug APK와 테스트 APK를 데이터 삭제 없이 설치했다.

1. 홈에서 예약 화면을 열고 테스트용 병원·일시·만남 장소·건강정보·보호자 정보를 준비한다. 병원 검색 API는 호출하지 않는다.
2. 실제 건강정보 화면에서 입력하고 키보드를 표시·숨긴다. 기기 WindowInsets의 IME 표시 상태와 입력창 노출을 확인한 뒤 예약 화면으로 돌아온다.
3. 접수 버튼을 고정 메뉴 위로 스크롤해 실제 터치하고, 동의·승인 화면을 거쳐 접수 완료 화면에 도달한다. 테스트 저장소의 예약이 정확히 1건 증가하는지 확인한다.
4. 예약 목록 → 홈 → 일정·이력 → 동행방 → 내 정보 → 홈을 왕복한다. 시스템 뒤로가기와 일정·이력 재진입도 확인한다.
5. 각 화면의 선택 탭, 메뉴 높이, 항목의 전체 노출과 최소 48dp 높이를 검사한다. 홈은 기존 Figma 레이아웃의 88dp, 나머지는 공통 레이아웃의 84dp에 실제 시스템 하단 inset을 더한 값과 비교한다.

## 변경된 범위

- 테스트 코드와 검증 문서만 변경했다. 제품 UI·라우팅·의존성·서버·DB·관리자 웹 계약 변경은 없다.
- 인증·예약·리포트·고객지원·매니저·첨부 Repository는 테스트 프로세스의 대역으로 격리하고 종료 시 원래 참조를 복원한다. 진행 중인 fixture도 제외해 동행방의 원격 Realtime 구독을 시작하지 않는다.
- 실제 Firebase 로그인, 앱 데이터, 권한, 원격 예약과 결제 상태는 변경하지 않았다. 합성 예약은 테스트 메모리 안에만 존재한다.
- 사용자가 승인한 휴대폰의 내비게이션 모드만 잠시 변경했다. OS overlay 전환은 `try/finally`로 원복하며 앱의 테스트 코드 자체가 기기 설정을 바꾸지는 않는다.

## 검증

Galaxy S24, Android 16에서 실행했다. 22건은 기존 선택·복귀·안전영역·예약/리포트 미리보기 21건과 이번 전체 흐름 1건이다.

| 검사 | 결과 |
| --- | --- |
| `assembleDebug assembleDebugAndroidTest` | 통과 |
| `testDebugUnitTest --rerun` | 320건 통과, 실패·오류·건너뜀 0건 |
| `:app:verifyReleaseAppCheckClasspath` | 통과 |
| 실제 OS 3버튼 모드 | 22건 통과 |
| 실제 OS 제스처 모드 | 동일한 22건 통과 |
| 원래 3버튼 복구 후 신규 전체 흐름 재실행 | 1건 통과 |

전환 전 `navigation_mode=0`과 `threebutton` overlay를 확인했다. 전환 후 `navigation_mode=2`, OS의 `config_navBarInteractionMode=2`를 확인하고 검사했다. 종료 시 두 값 모두 `0`, `threebutton` overlay 활성 상태로 복구했다. 이후 일반 Launcher 경로로 앱을 실행하여 기존 개발용 사용자 화면과 4개 탭이 나타나는 것을 확인했다.

합성 inset만 주입한 기존 검사와 달리 이번 신규 테스트의 IME 표시·숨김 및 화면 높이는 실제 기기 WindowInsets를 사용한다. 다만 자동 뒤로가기는 OS `KEYCODE_BACK`으로 실행하며, 손가락의 가장자리 스와이프 자체를 검증한 것은 아니다. 동행방은 활성 세션이 없는 상태의 탭 진입을 확인했으며 채팅 입력·전송 검증이 아니다.

재실행 명령은 다음과 같다. OS 모드는 승인된 기기에서 별도로 전환하고 반드시 원복한다.

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest --rerun :app:verifyReleaseAppCheckClasspath --console=plain
adb -s <device> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <device> install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s <device> shell am instrument -w -r -e class com.example.bodeul.ui.navigation.ClientBottomNavigationSelectionTest,com.example.bodeul.ui.navigation.ClientNavigationReturnTest,com.example.bodeul.ui.navigation.ClientBottomNavigationInsetsTest,com.example.bodeul.ui.booking.ClientBookingHistoryInsetsTest,com.example.bodeul.debug.BookingFigmaPreviewTest,com.example.bodeul.debug.GuardianReportFigmaPreviewTest com.example.bodeul.test/androidx.test.runner.AndroidJUnitRunner
```

ADB 프로세스 종료 코드만으로 성공을 판단하지 않고 instrumentation의 `OK (22 tests)`를 확인한다. 개인정보가 포함될 수 있는 실제 계정 화면 원본은 저장소에 추가하지 않았다.

## 남은 범위

#448의 합성 예약 전체 동선·실제 IME·OS 모드 전환 검증은 완료했으며, 이번 회귀 테스트 PR의 리뷰와 `dev` 반영이 남았다. 원래 내부 테스트 캡처의 빌드와 경로는 특정되지 않았으므로 동일 원인을 재현했다고 단정하지 않는다.

원격 예약 생성·취소와 실제 개발 계정의 읽기 전용 왕복은 각각 #473과 [#474 검증 기록](issue-448-navigation-return-2026-09-29.md)에 구분되어 있다. 이번 검사를 결제 수취, 활성 채팅·첨부, 운영 권한, 운영 배포 검증으로 확대하지 않는다.
