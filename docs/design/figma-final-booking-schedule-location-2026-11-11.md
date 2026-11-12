# 일정·만남 장소 선택 완성본 반영

## 판단

- 작업 목적: 병원·진료과 선택에 이어 날짜·시간과 만남 장소 선택을 Figma 완성본에 맞춘다.
- 선택한 방식: 달력·시간 BottomSheet와 병원 요약·만남 장소 목록·직접 입력을 적용한다. 기존 ActivityResult 및 단일 `appointmentAt`, `meetingPlace` 계약은 유지한다.
- 대안: 만남 시각과 병원 예약 시각, 주소 검색·거리·추천 API를 한 번에 추가하는 방식을 검토했다.
- 선택 이유: 현재 예약·결제·관리자 계약을 유지하면서 화면을 단계적으로 교체한다. 저장되지 않는 두 번째 시각이나 계산하지 않은 거리·소요시간을 실제 값처럼 표시하지 않는다.
- 리스크: 두 시각의 분리 저장과 주소 검색·거리 제한은 이번 UI 작업만으로 완성되지 않는다. 기존 임의 분 단위 값과 직접 작성한 만남 장소가 덮어써지지 않도록 회귀 검증한다.

## 기준과 범위

- [일정 선택](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-2183), [시간 선택](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-1893), [만남 장소 선택](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-1532), [직접 입력](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-6821).
- 레이아웃·Pretendard·브랜드색은 완성본을 따르고 필수 글자는 #510에서 승인한 공통 대비 토큰을 재사용한다.
- 현재 `appointmentAt`의 예약 희망 시각 의미와 서울 시간대 문자열을 유지한다. 만남 시각과 병원 예약 시각을 각각 저장했다고 표시하지 않는다.
- 기존 병원 내 네 가지 안내 지점을 유지한다. 도식은 실제 지도와 구분한 별도 안내 창으로 이동한다. 실제 병원 사진·주소·추천 여부·거리·소요시간을 만들어 넣지 않는다.
- 직접 입력은 장소 문구를 저장하는 기존 계약에 연결하며 주소 검색으로 표시하지 않는다. Core API·DB·관리자 웹·결제·위치 권한·의존성 버전은 변경하지 않는다.

## 구현한 내용

- 일정: 60dp 헤더, 실제 월·요일·윤년을 계산하는 7열 달력, 원본 월 이동·선택일·오늘 자산, 시간 선택 필드와 하단 확인 버튼을 적용했다. 과거 날짜 선택 제한과 서울 시간대 결과 형식은 유지한다.
- 시간: 네이티브 `NumberPicker`의 오전/오후·시·분 휠을 BottomSheet로 구성했다. `10:17` 같은 기존 값을 반올림하지 않는다. 새 예약은 시간 확인 후 하단 진행 버튼이 나타나며, 시트 취소는 이전 시각을 유지한다.
- 만남 장소: 병원·진료과 요약, 구분선 중심의 장소 목록, 직접 입력을 적용했다. 기존 정문·외래·주차장·약국 지점과 안내도 터치 선택도 유지한다. 직접 입력한 장소에 가짜 지도 좌표를 붙이지 않는다.
- 입력 보존: 목록에 없는 기존 장소는 직접 입력 항목으로 남긴다. 선택기 진입만으로 기본 지점으로 덮어쓰지 않는다. 시트 확인과 전체 선택기 확인을 구분하고, 닫기·뒤로 가기는 예약 폼에 결과를 반환하지 않는다.
- 화면 재생성: 선택 날짜·표시 월·분 단위 시간·직접 입력 초안·안내도 선택 상태를 복원한다. 시트 내부의 미확정 값을 복원하더라도 취소하면 예약 값은 바뀌지 않는다.
- 접근성: 기존 필수 글자 대비 토큰, 최소 48dp 닫기·월 이동 터치 영역, 날짜·장소 선택 상태를 적용했다. 글자 확대 시 장소 제목과 직접 입력을 세로 배치하고, 본문은 스크롤하며 확인 버튼은 고정한다. 안내도 지점은 라디오 목록으로도 선택할 수 있다.

달력 화살표·검은 선택 원·회색 오늘 원은 위 Figma node의 SVG를 4배 PNG로 변환해 `drawable-xxxhdpi`에 넣었다. 닫기와 드롭다운은 기존 내보내기 자산과 SHA-256이 같아 재사용했다. 키보드·시스템 바는 실제 Android UI이며 이미지로 복제하지 않는다. 시간 휠은 접근성과 기존 분 단위 값을 보존하는 네이티브 위젯이므로 원본의 5행 정적 시안 대신 3행으로 표시한다.

## 변경된 범위

- [일정 Activity](../../app/src/main/java/com/example/bodeul/ui/booking/BookingAppointmentSelectorActivity.java)는 화면 흐름을 연결하고, 달력 생성과 시간 시트는 각각 [CalendarBinder](../../app/src/main/java/com/example/bodeul/ui/booking/BookingScheduleCalendarBinder.java), [TimeSheet](../../app/src/main/java/com/example/bodeul/ui/booking/BookingTimeSheet.java)로 분리했다.
- [장소 Activity](../../app/src/main/java/com/example/bodeul/ui/booking/BookingLocationSelectorActivity.java)는 목록·결과 연결을 맡는다. 저장 장소 해석은 [SelectionPolicy](../../app/src/main/java/com/example/bodeul/ui/booking/BookingMeetingSelectionPolicy.java), 직접 입력과 안내도는 별도 시트가 처리한다.
- 기존 `createIntent/parseResult`의 `selectedAppointmentAt`, `pointId`, `meetingPlace` 결과 계약은 유지한다. 예약 작성·수정·결제·인증 코드와 서버·DB는 바꾸지 않았다.
- 두 Activity는 계속 `exported=false`이며, 장소 화면에 키보드 크기 대응만 추가했다. 새로운 외부 진입점·권한·의존성은 없다.

## 초기 검증 (2026-11-11)

검증 기준은 `dev`의 `ced200abcffdd44bb0c5f32131e6a684b8479926`에서 분기한 `codex/figma-final-booking-schedule-location`의 로컬 변경이다. 개발 Firebase 설정이 없는 Mock/CI 빌드와 읽기 전용 API 34 AVD를 사용했다. 실기기와 실제 API·예약 쓰기는 실행하지 않았다.

| 검사 | 결과 |
| --- | --- |
| `assembleDebug testDebugUnitTest assembleDebugAndroidTest --console=plain` | 성공, 단위 테스트 464건 통과, 실패·생략 없음 |
| `:app:verifyReleaseAppCheckClasspath` | 성공 |
| `node tools/android/check-environment-boundary.mjs` | 개발·운영 혼합 차단 4건 통과 |
| 기존 예약 수명주기·예약 화면·병원 선택 계측 | API 34 기본 화면에서 11 + 17 + 10 = 38건 통과 |
| 새 선택기 계측, 375×812dp·글꼴 1.0·제스처 | 14건 통과 |
| 새 선택기 계측, 360×640dp·글꼴 2.0·3버튼 | 14건 통과 |
| 새 선택기 계측, 412×915dp·글꼴 1.3·제스처 | 14건 통과 |
| `lintDebug` | 기존 미변경 10개 파일의 `NewApi` 오류 35건으로 실패. 이번 변경 파일의 lint 오류·경고는 0건 |
| 변경 XML·문서 링크·`git diff --check` | 통과 |

[새 계측 테스트](../../app/src/androidTestDebug/java/com/example/bodeul/debug/BookingScheduleLocationFigmaTest.java)는 실제 선택기 Activity를 사용해 월·연도 경계, 윤년, 과거 날짜 차단, 시간 휠 조작, 기존 임의 분 보존, 장소 행과 안내도 실제 터치, 직접 입력의 공백 차단, 확인/취소, 화면 재생성, 반환 결과를 검사한다. 글자 잘림·말줄임, 주요 버튼 대비 4.5:1 이상, 키보드 표시 중 확인 버튼 노출도 확인한다. [장소 해석 단위 테스트](../../app/src/test/java/com/example/bodeul/ui/booking/BookingMeetingSelectionPolicyTest.java) 7건은 기본 지점·기존 장소·직접 입력·빈 목록을 검사한다.

검증 중 월 이동 버튼을 회전시켰을 때 터치 좌표가 어긋나는 문제를 발견해 이미지 자산만 회전하도록 바꿨다. 작은 화면·2배 글꼴에서 시간 휠의 불필요한 높이가 선택 값을 가리는 문제도 수정했다. 최종 화면 캡처 6종(일정·시간·장소 목록·직접 입력·키보드·안내도)과 계측 로그는 로컬 `.tmp/schedule-validation/`에 보관한다.

## 2026-11-12 리뷰 반영

- 작업 목적: #518에서 지적한 달력 제목·완료 버튼의 두 자리 연도 표시를 네 자리로 복구한다.
- 선택한 방식: 달력에 전체 연도를 전달하고 월 문자열은 `%1$04d`, 완료 버튼 날짜는 `yyyy년 M월 d일(E)` 형식을 사용한다.
- 대안: 저장 형식을 수정하거나 별도 날짜 표시 계층을 추가하는 대신 기존 두 표시 경로만 수정한다.
- 선택 이유: 기존 `appointmentAt` 계약과 선택 동작에는 문제가 없으므로 화면 표시 회귀만 좁게 수정한다.
- 리스크: 글자가 두 자리 늘어나므로 작은 화면·큰 글꼴에서 잘림과 완료 버튼 노출을 다시 검사한다.
- 수정 전에는 추가한 계측 테스트 두 건에서 실제 `26년 12월`과 `26년 12월 31일(목) 오전 10:17로 진행` 문구를 검출했다. 테스트는 실행 연도를 사용해 화면 재생성·연도 경계 이동·반환 값 보존을 확인한다.
- 수정 후 `assembleDebug testDebugUnitTest assembleDebugAndroidTest :app:verifyReleaseAppCheckClasspath` 성공, 단위 테스트 464건과 환경 혼합 차단 4건이 통과했다.
- API 34의 위 세 화면·글꼴·내비게이션 구성에서 선택기 계측 16건씩 통과했고, 기본 구성에서 기존 예약·병원 선택 38건도 다시 통과했다. 총 86회 실행이며 서로 다른 테스트는 54건이다. 세 구성의 일정 화면 캡처에서 네 자리 연도와 완료 버튼 문구의 잘림이 없는 것을 확인했다.
- `lintDebug`는 기존 미변경 10개 파일의 `NewApi` 오류 35건으로 다시 실패했다. SARIF 기준 이 PR 변경 파일의 오류·경고는 0건이다.
- 이번 검증도 Mock/CI 빌드와 읽기 전용 에뮬레이터에서만 수행했다. 로그·화면은 `.tmp/schedule-validation/review-year-*`에 보관하며, 실제 계정·API·실기기 검증과 운영 반영은 수행하지 않았다.

## 남은 범위

- 만남 시각과 병원 예약 시각의 독립 저장, 순서 검증, 기존 예약 호환성은 별도 계약 변경이 필요하다.
- 주소 검색·병원별 실제 추천 지점·거리 제한은 실제 데이터와 좌표 계약을 갖춘 뒤 연결한다.
- 위 기능도 Figma 완성본의 최종 목표에서 제외하지 않는다. 이번 작업은 기존 계약 위의 선택기 UI 전환이며 전체 예약 흐름의 완성본 적용 완료를 뜻하지 않는다.
- 실제 개발 계정·Core API를 통한 예약 저장, 실기기·TalkBack 음성 검증은 수행하지 않았다.
- 이 기록은 `dev` 대상 PR의 구현·검증 범위다. 병합·개발 배포·운영 반영 완료를 뜻하지 않으며, 원격 검토 상태는 해당 PR을 따른다.
