# 병원 검색·진료과 선택 완성본 반영

## 판단

- 작업 목적: 예약 메인에서 사용하는 병원 검색과 진료과 선택을 Figma 완성본에 맞춘다.
- 선택한 방식: 기존 Core API 검색·가이드 목록·ActivityResult 계약은 유지하고, 병원 목록과 상태 UI 및 진료과 BottomSheetDialogFragment를 적용한다.
- 대안: 검색 API에 사진·주소를 추가하거나 예약 단계와 서버 계약까지 한 번에 바꾸는 방식을 검토했다.
- 선택 이유: 현재 규모에서는 선택기만 교체하면 예약 초안·좌표·결제 흐름의 변경 위험을 줄일 수 있다. 서버에 없는 예시 사진·주소는 실제 정보로 표시하지 않는다.
- 리스크: 원본의 사진·주소 영역은 현재 데이터 계약으로 완전히 재현할 수 없다. 실제 API·실기기 확인과 합성 데이터 에뮬레이터 검증을 구분한다.

## 기준과 범위

- [병원 검색 목록](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-1208), [검색 결과 없음](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-1293), [진료과 선택](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-6634), [선택 상태](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-6648).
- 완성본 우선, 기존 승인과 같이 필수 텍스트 대비는 공통 접근성 토큰을 사용한다.
- 기존 지역 검색 단축 동작은 별도 메뉴로 유지하고, 직접 입력·검색 취소·오류 재시도를 유지한다.
- 실제 진료과 목록이 있으면 그대로 표시한다. 목록이 없는 병원은 완성본의 일반 진료과 선택과 직접 입력을 제공하며, 해당 병원의 운영 진료과를 검증했다고 표시하지 않는다.
- 관리자 웹, DB, Core API 계약, 위치 수집 권한, 예약 저장·결제 정책은 변경하지 않는다.

## 구현한 내용

- 병원 검색: 60dp 헤더, 44dp 검색창, 60dp 썸네일 영역, 병원명·보조 정보·지도 버튼, 원본 빈 결과 상태를 적용했다. 키보드가 열려도 직접 입력은 하단에서 접근할 수 있다.
- 진료과: 2열 선택 목록, 원본 선택 배경·체크·하단 확인 버튼을 적용했다. 확인 전 선택과 취소는 예약 값을 바꾸지 않는다. 1.3배를 넘는 확대 글꼴에서는 1열로 바꾸고 목록을 스크롤한다.
- 직접 입력: 병원명·진료과 필수 검증, 입력 복원, 오류 위치 스크롤을 유지한다. 병원명을 바꾸면 기존 병원 좌표는 지우고, 진료과만 바꾸면 좌표를 유지한다.
- 오류·재시도: 가이드 목록을 못 받아도 외부 검색과 직접 입력을 사용할 수 있다. 목록 오류는 스크롤 영역 안에 표시하고, 검색 중에는 메뉴에서도 목록 재시도를 제공한다.
- 수명주기: 검색어, 진료과 선택, 직접 입력을 화면 재생성 후 복원한다. 이전 검색·종료된 화면의 늦은 콜백은 현재 결과를 덮어쓰지 않는다.
- 지도 버튼: 병원 좌표 또는 병원명으로 외부 지도 앱의 `geo:` 요청을 만든다. 이 동작은 병원 선택을 확정하거나 위치 권한을 요청하지 않는다. 지도 앱이 없으면 안내한다.

닫기·검색·삭제·지도·빈 결과 아이콘과 하단 페이드는 해당 Figma node의 내보내기 자산을 사용했다. SVG는 4배 PNG로 변환해 `drawable-xxxhdpi`에 넣었으며, 체크는 기존 예약 체크 자산과 SHA-256이 같아 재사용했다. 실제 기기의 상태바·키보드 이미지는 복제하지 않는다.

필수 문자의 대비는 #510에서 승인한 공통 접근성 토큰을 유지한다. 지도 버튼은 원본보다 큰 최소 48dp 터치 영역을 사용한다. 검색 삭제 버튼은 원본 검색창 안의 40×44dp 영역을 유지한다.

## 변경된 범위

- [Activity](../../app/src/main/java/com/example/bodeul/ui/booking/BookingHospitalSelectorActivity.java)는 화면 흐름을 연결하고, 검색 조합은 기존 [Coordinator](../../app/src/main/java/com/example/bodeul/ui/booking/BookingHospitalSelectorCoordinator.java)에서 처리한다.
- 진료과 목록과 직접 입력은 각각 [BottomSheet](../../app/src/main/java/com/example/bodeul/ui/booking/BookingDepartmentSheet.java), [DialogFragment](../../app/src/main/java/com/example/bodeul/ui/booking/BookingHospitalManualDialog.java)로 분리했다.
- 기존 `createIntent/parseResult`의 병원명·진료과·위경도 계약은 동일하다. 예약 메인·결제·건강정보 저장 코드는 바꾸지 않았다.
- 서버 응답, 인증/App Check, DB, 의존성 버전에는 변경이 없다. debug 전용 합성 화면은 외부에 노출하지 않으며 Release에 포함되지 않는다.

## 검증

검증 기준은 `dev`의 `7689a4e53a52341f50684af84fa61365356e13a0`에서 분기한 로컬 변경이다. 개발 Firebase 설정 없는 Mock/CI 빌드를 사용했다.

| 검사 | 결과 |
| --- | --- |
| `assembleDebug testDebugUnitTest assembleDebugAndroidTest --console=plain` | 성공, 단위 테스트 455건 통과, 실패·생략 없음 |
| `:app:verifyReleaseAppCheckClasspath` | 성공 |
| `node tools/android/check-environment-boundary.mjs` | 개발·운영 혼합 방지 4건 통과 |
| API 34, 375×812dp, 글꼴 1.0 | 선택기 10건 + 기존 예약 수명주기 11건 + 기존 예약 화면 17건, 총 38건 통과 |
| API 34, 360×640dp/2.0배, 375×812dp/1.0배, 412×915dp/1.3배 | 최종 선택기 테스트 각 10건 재실행 통과, 제스처 내비게이션·스크린샷 확인 |
| API 34, 360×640dp/1.0배, 3버튼 내비게이션 | 최종 선택기 테스트 10건 통과, 하단 확인·직접 입력 가림 없음 |
| `lintDebug` | 기존 10개 미변경 파일의 `NewApi` 오류 35건으로 실패. 이번 변경 파일에는 lint 오류 없음 |
| `git diff --check` | 통과 |

[선택기 계측 테스트](../../app/src/androidTestDebug/java/com/example/bodeul/debug/BookingHospitalFigmaTest.java)는 확인/취소, 실제 Activity 결과와 좌표, 빈 가이드 목록에서 외부 검색, 역순 응답, 재생성, 직접 입력 검증, 오류 후 재시도, 지도 요청과 지역 단축 검색을 검사한다. [단위 테스트](../../app/src/test/java/com/example/bodeul/ui/booking/BookingHospitalSelectorCoordinatorTest.java)는 검색 결과와 기존 진료과 병합·외부 검색 실패 대체를 확인한다. 진료과 목록 정규화·사용자 입력 보존은 [별도 테스트](../../app/src/test/java/com/example/bodeul/ui/booking/BookingDepartmentOptionsTest.java)로 확인한다.

화면 검사는 긴 한글의 가로·세로 잘림과 말줄임, 확인 버튼 대비 4.5:1 이상, 실제 IME 표시 중 직접 입력 버튼 노출을 포함한다. 목록 조회·검색은 합성 응답이며 예약 쓰기를 호출하면 테스트가 실패하도록 구성했다. 지도는 실제 앱을 열지 않고 생성한 Intent를 검사한다.

검증 중 작은 화면·2배 글꼴에서 목록 오류가 직접 입력을 밀어내는 현상과 필수 항목 오류가 직접 입력 창 아래에 가려지는 현상을 발견했다. 오류를 고정 높이 화면의 스크롤 영역 안으로 옮기고 첫 오류 위치로 이동하도록 수정한 뒤 같은 조건에서 재검증했다.

## 남은 범위

- 실제 개발 계정·Core API·실기기를 연결한 병원 검색부터 예약 저장까지의 종단 확인은 하지 않았다. 외부 지도 앱 실행과 TalkBack 음성 동작도 이번 계측의 성공 범위가 아니다.
- 현재 장소 검색 계약은 병원명·위경도만 제공한다. 병원 사진·주소 표시에는 별도 데이터 계약 확장이 필요하며, Figma 예시 정보를 실제 병원 정보로 표시하지 않았다.
- 날짜·시간, 만남 장소, 결제 승인·완료 화면의 완성본 전환과 새 예약 시각 필드는 별도 후속 범위다.
- 이 기록은 개발 브랜치 PR의 구현·검증 범위다. 병합·배포 완료를 의미하지 않으며 원격 검토 상태는 해당 PR을 따른다.
