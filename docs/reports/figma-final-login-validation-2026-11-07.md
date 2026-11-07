# 완성본 공통 스타일·로그인 검증

확인일: 2026-11-07

## 범위와 기준

- 제품 목표는 **최신 Figma가 기존 MVP보다 우선**한다. [전체 반영 계획](../design/figma-final-app-gap-plan-2026-11-07.md)과 [구현 기록](../design/figma-final-login-implementation-2026-11-07.md)을 따른다.
- 원본: `보들 어플 완성본`의 [로그인 1:13671](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-13671), [이메일 오류 1:14278](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-14278).
- 비교 기준은 `dev`의 `55c7d753b6808700163b9518e3e3a0c352d7fb5f`, 구현은 `codex/figma-final-login`의 로컬 변경이다. 원격 PR·병합·배포를 수행한 결과가 아니다.
- 기존 인증 Repository와 로그인 후 Router는 유지한다. 실제 계정 대신 debug 전용 가짜 저장소로 요청 인자·성공/실패·화면 상태를 검사했다.

## 구현한 내용과 변경 범위

공통 토큰·스타일, 로그인 전용 테마, 원본 이미지 7개, 로그인 배치·입력 오류·키보드 처리를 적용했다. 첫 화면에서 역할 칩을 제거하고 인증 직전 선택창으로 옮겼으며 기존 회원가입·복구·메일 재전송 기능을 유지했다. 타 화면의 전역 테마, 서버·DB·인증 제공자 설정, 의존성 버전은 바꾸지 않았다.

Apple 버튼은 원본처럼 표시하지만 제공자 미연동 안내만 제공한다. 현재 지원하지 않는 로그인을 성공하는 것처럼 구현하지 않았다.

## 실행 결과

| 검사 | 결과 |
| --- | --- |
| `assembleDebug` | 통과. Firebase 설정 파일이 없는 Mock/CI 구성 |
| `assembleDebugAndroidTest` | 통과 |
| `testDebugUnitTest` | 83개 suite, 428건 통과. 실패·오류·건너뜀 0건 |
| `:app:verifyReleaseAppCheckClasspath` | 통과 |
| `node tools/android/check-environment-boundary.mjs` | 개발/운영 API·Realtime 혼합 거부 4건 통과 |
| 로그인 계측 테스트 | 11건을 아래 4개 구성에서 각각 통과, 총 44회 |
| `lintDebug` | 실패: 오류 36건·경고 188건. 오류는 이번에 변경하지 않은 파일 11개에 있으며 변경 파일의 오류는 0건 |

실행 명령:

```powershell
.\gradlew.bat assembleDebug assembleDebugAndroidTest testDebugUnitTest :app:verifyReleaseAppCheckClasspath --console=plain
.\gradlew.bat lintDebug --console=plain
node tools/android/check-environment-boundary.mjs
```

전체 lint 통과로 기록하지 않는다. 예를 들어 기존 `CoreApiGuardianBookingRepository.java`의 `Instant.parse` 등 최소 API 호환 문제가 남아 있다. SARIF의 오류 위치를 이번 변경 파일 목록과 대조했으며, 기준 커밋을 별도 checkout해 lint를 다시 실행한 것은 아니다. 범위 밖 오류를 숨기는 baseline·suppression도 추가하지 않았다.

### 에뮬레이터 구성

모든 구성은 밀도 320dpi다. 이번 검증을 위해 시작한 읽기 전용 에뮬레이터만 사용했으며 개인 휴대폰 설정은 변경하지 않았다.

| Android | 화면 크기 | 논리 크기 | 글꼴 배율 | 내비게이션 | 결과 |
| --- | --- | --- | --- | --- | --- |
| API 30 | 720×1280 | 360×640dp | 1.0 | 3버튼 | 11/11 |
| API 30 | 750×1624 | 375×812dp | 1.0 | 3버튼 | 11/11 |
| API 30 | 824×1830 | 412×915dp | 1.3 | 3버튼 | 11/11 |
| API 34 | 750×1624 | 375×812dp | 1.0 | 제스처 | 11/11 |

검사 대상은 초기 배치·색상·폰트, 필수값/잘못된 이메일, 오류 후 복구, 이메일 요청 인자와 로딩, 역할 선택/취소, Kakao·Google 호출, 회원가입/정규화/화면 재생성, 재설정·재전송 쿨다운, 관리자 제한, Apple 미연동 안내, 실제 IME 입력과 제출 버튼 접근성이다. 외부 인증 성공을 확인한 테스트가 아니다.

### 화면 비교와 수정

- 최초 검증에서 스크롤 안쪽 inset 때문에 회원가입 보조 버튼이 시스템 영역에 가려지는 문제를 확인했다. 바깥 컨테이너에서 실제 시스템·IME inset을 처리한 뒤 4개 구성을 다시 통과했다.
- 375dp 캡처의 표면 `#FFFFFF`, 입력 배경 `#F2F5F8`, 주 버튼 `#01A1FF` 픽셀을 원본과 대조했다. 원본 로고·소셜 아이콘을 사용하며 Pretendard Medium/Bold와 0 letter spacing을 검사했다.
- 로그인, 잘못된 이메일, 회원가입 키보드 캡처에서 잘림·가림을 확인했다. 글꼴 1.3배와 제스처 환경에서도 입력창·제출 동작이 유지됐다.
- Android 시스템 상태바·내비게이션·IME는 Figma의 기기 장식과 구분한다. 전체 화면의 픽셀 동일성이나 모든 API 버전의 실행을 검증했다고 보지 않는다.
- 원본 버튼의 흰 글자 대비 약 2.78:1은 임의로 색을 바꾸지 않고 디자인 접근성 과제로 기록했다.

로컬 산출물은 무시 경로 `.tmp/login-captures/`의 구성별 `clean.png`, `initial.png`, `invalid-email.png`, `register-keyboard.png`, `instrumentation.txt` 및 `results-emulator-*.json`에 있다. 단위 테스트 XML은 `app/build/test-results/testDebugUnitTest/`, lint 보고서는 `app/build/reports/lint-results-debug.sarif`에 있다. 합성 데이터 캡처와 빌드 산출물을 소스 저장소에 추가하지 않는다.

## 남은 범위

- 실제 개발 계정의 이메일·Kakao·Google 로그인, 메일 도착, 성공 후 역할별 화면 전환을 개발 환경에서 확인한다. 가짜 저장소의 성공 콜백 검증을 종단 인증 검증으로 대체하지 않는다.
- 실기기 실행과 API 24 등 이번에 실행하지 않은 OS 버전은 남아 있다. 빌드·lint 호환 점검과 실제 실행을 구분한다.
- Apple 실제 인증·SMS 본인확인과 전체 회원가입 완성본 흐름은 별도 제공자 설정·구현이 필요하다.
- 홈·예약·5탭·매칭·9단계 등 나머지 화면과 계약은 전체 반영 계획 순서로 진행한다.
- 원격 PR·리뷰·병합과 개발/운영 배포는 이번 로컬 검증에 포함하지 않는다.
