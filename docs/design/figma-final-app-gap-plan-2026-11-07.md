# Figma 완성본과 앱 비교 및 반영 계획

기준일: 2026-11-07

## 결론

**최신 Figma 완성본이 기존 MVP보다 우선한다.** 시각 디자인뿐 아니라 제품 범위·사용자 흐름도 완성본을 목표로 반영한다. 기존 코드·계약과의 차이는 디자인을 축소할 근거가 아니라 함께 구현할 변경 범위다. 실제 인가·개인정보 보호·데이터 호환성을 해제하지 않고 필요한 계약 변경을 단계별로 진행한다.

| 구분 | 이번 판단 |
| --- | --- |
| 바로 시작 | 공통 색상·글자·버튼·입력창, 로그인, 환자·매니저 홈의 시각 구조 |
| 기존 기능을 살려 개편 | 예약 입력과 완료, 이용내역, 동행 단계, 리포트, 채팅 상세, 내 정보 |
| 별도 기능 작업 | 두 종류의 예약 시각, 사전 질문 목록, 달력형 이용내역, 채팅 목록·검색, 3항목 별점 |
| 선행 계약·설정 필요 | 매니저 매칭, 자동결제·출금, Apple 로그인·SMS 본인확인, 녹음·OCR·외부 리포트 공유. 민감서류는 실제 수집 적법성도 확인 |
| 첫 구현 단위 | **공통 스타일과 로그인 화면 1개**. 기존 로그인 제공자·인증 흐름은 그대로 두고 작은 PR로 검증 |

**반영 순서:** 공통 스타일·로그인 → 홈 → 예약 → 이용내역 → 동행 진행 → 리포트 → 채팅·내 정보. 계약이 필요한 신규 기능은 이 순서를 막지 않고 별도 작업으로 분리한다.

**11월 9일 후속 적용:** 공통 스타일·로그인 #510, 환자·보호자 홈 #511, 매니저 홈 #514가 `dev`에 병합됐다(각각 `1aa8ba6`, `6558738`, `5a957b9`). 범위는 [환자 홈 구현 기록](figma-final-client-home-implementation-2026-11-07.md)과 [매니저 홈 구현 기록](figma-final-manager-home-implementation-2026-11-09.md)을 따른다. 예약 입력은 `codex/figma-final-booking`에서 단계형 화면으로 반영하며 [구현·검증 기록](figma-final-booking-2026-11-09.md)에 기존 기능 보존과 미반영 계약을 구분한다. 예약 선택기·결제 승인·완료 화면 전체 전환은 아직 남아 있고, 로컬 구현을 병합·배포 완료로 보지 않는다. 아래 비교표는 최초 조사 당시 차이를 보존한다.

## 비교 기준과 확인 범위

**11월 10일 선택기 후속:** 병원 검색·진료과 선택은 `codex/figma-final-hospital-selector`에서 로컬 구현·검증했다. [구현 기록](figma-final-hospital-selector-2026-11-10.md)에 원본 node, 기존 기능 보존, 데이터 계약 한계와 검증 범위를 구분했다. 날짜·시간·만남 장소와 결제 완료 화면은 후속 범위이며, 선택기 로컬 완료를 전체 예약 화면의 병합·실기기 검증 완료로 보지 않는다.

**11월 11일 일정·장소 후속:** 날짜·시간과 만남 장소 선택 UI를 `codex/figma-final-booking-schedule-location`에서 로컬 반영·검증했다. [구현 기록](figma-final-booking-schedule-location-2026-11-11.md)에 달력·시간 휠·직접 입력·기존 장소 보존과 확대 화면 검증을 정리했다. 현재 단일 `appointmentAt` 계약은 유지하며, 두 시각 분리 저장·주소 검색·거리 제한·결제 승인·완료 화면은 후속 목표로 남는다. 로컬 UI 검증을 서버 계약 확장이나 병합·배포 완료로 보지 않는다.

**11월 13일 결제 확인·완료 후속:** #518이 병합된 `dev`의 `09029453`을 기준으로 `codex/figma-final-booking-completion`에서 결제 확인·신청 완료 시트를 반영했다. [구현·검증 기록](figma-final-booking-completion-2026-11-13.md)에 동의·취소·재생성, 기존 결제 결과, 상세·지도·예약 내역 이동과 화면 크기별 검증을 구분한다. 원본 확인 아이콘·정보 배치를 적용하되 실제 연동이 없는 자동결제·입금·매칭 기한을 성공 상태로 표시하지 않는다. 두 시각·병원 사진/주소·실제 결제 연동과 실기기 종단 검증은 남아 있다. `dev` 대상 리뷰 변경이며 병합·배포 완료 기록은 아니다.

| 대상 | 이번 확인 |
| --- | --- |
| Figma | [보들 어플 완성본 / Page 1](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=0-1), file key `fqwi44prjEgVFZhaM6LuzS`, node `0:1` |
| 원본 조사 | 페이지 직계 자식 이름·종류 목록, 주요 화면 30개 본문·구조, Color·Text Style·Button 영역, 대표 화면 이미지 9개 |
| 앱 코드 | `bodeul110/bodeul-platform`의 `dev`, `55c7d753b6808700163b9518e3e3a0c352d7fb5f` 기준 |
| 로컬 렌더링 | Android API 30, 720×1280 에뮬레이터의 예약·진행·최종 리포트 합성 데이터 미리보기 3개 |
| GitHub | 열린 이슈 목록과 #312·#304·#419 본문. #509는 `dev` 병합 상태 확인 |
| 정책 | 저장소의 [기획·디자인·구현 기준](../planning/source-of-truth.md), [MVP 범위](../planning/mvp-scope.md). 이번 작업에서 Notion 원문을 새로 조회하지는 않음 |

페이지 직계 자식은 총 542개이며 `FRAME` 500개, `INSTANCE` 34개, `SECTION` 6개, `COMPONENT_SET` 1개, `COMPONENT` 1개다. **500개를 독립 앱 화면 수로 보지 않는다.** 상태 변형, 같은 이름의 화면, 레이아웃용 frame을 포함하므로 구현 작업은 아래 사용자 흐름 단위로 묶는다.

로컬 미리보기는 #509 head `1e2c56cf1872e75992e172a49690699e41dc958f`의 기존 debug APK를 사용했다. 해당 head와 비교 기준 `dev`의 `app/` 소스 차이는 없었다. 실제 계정·서버를 연결하지 않는 mock 미리보기이며, 실기기·개발 API 종단 검증이나 설치된 운영 앱 비교를 대신하지 않는다. 전체 frame의 픽셀·모든 상태·prototype 연결을 전수 확인한 것도 아니다.

이전 [보들 MVP 구현 매핑](figma-mvp-implementation-map-2026-08-29.md)은 과거 비교 기록으로 보존한다. 이후 화면 작업은 새 완성본의 해당 node를 다시 읽고 시작한다. 원본 PNG 묶음이나 합성 계정 데이터는 저장소에 추가하지 않는다.

## 공통 디자인 차이

| 항목 | 현재 앱 | 완성본 | 적용 판단 |
| --- | --- | --- | --- |
| 주 색상 | `#1958B7`, 보조 `#3C72D1` | 주요 버튼 실제 fill `#01A1FF`, 텍스트 강조 `#018DFF` | 브랜드 색과 조작용 색을 분리한 의미 기반 리소스로 이행. 모든 파란색을 일괄 치환하지 않음 |
| 텍스트·배경 | 본문 `#191C1E`, 보조 `#424752`, 배경 `#F7F9FC` | 검정, `#545B68`, `#868E96`, `#94A3B8`, `#CBD1DB`, `#EDF1F7`, `#F2F5F8` | 본문·보조·비활성·구분선·표면 역할별 매핑. 상태 색은 색만으로 구분하지 않음 |
| 글자 | Pretendard는 이미 있음. 홈 인사 36sp 등 큰 제목 중심 | Pretendard 400/500/600/700, 제목 25/22/20, 본문 16/15/14, 보조 13/12/10 | 폰트 추가 없이 해당 화면 node의 크기·굵기·행간 적용. Android 글꼴 확대를 지원하고 원본의 작은 글자 문제는 별도 접근성 검토로 기록 |
| 버튼·모서리 | 로그인 주 버튼 64dp, 홈 CTA 56dp. 홈 카드 반경 48dp·32dp | 공통 버튼 높이 50·56px, 반경 10px | 버튼·입력창·선택 상태를 공용 스타일로 만들고 화면별 필요 치수만 조정 |
| 화면 밀도 | 큰 인사말·일러스트와 개별 카드가 공간을 많이 차지 | 작은 상단 제목과 목록·단계별 정보 중심 | 예약·진행 상태와 다음 행동이 먼저 보이도록 재배치 |
| 기준 폭 | 이전 시안 390px 기반 화면이 섞임 | 확인한 주요 모바일 frame 폭 375px | Android `dp/sp`와 가변 폭 사용. 전체 화면을 비율 확대·축소하지 않음 |
| 시스템 영역 | Android 상태바·내비게이션·IME | 시안에 기기 상태바·키보드 표현 포함 | 기기 장식을 이미지로 복제하지 않고 실제 WindowInsets·IME를 처리 |

원본 영역: [Color](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-13541), [Text Style](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-13316), [Button](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-13108).

원본 내부에도 차이가 있다. Color의 `1:13565`는 `#01A1FF`라고 적혀 있지만 실제 fill은 `#018DFF`에 가깝고, 주요 버튼 `1:13141`·`1:13165`의 fill은 `#01A1FF`다. 구현할 화면 node의 실제 fill을 우선한다. 흰 글자와 `#01A1FF`의 계산상 명암 대비는 약 2.78:1이다. #510 리뷰 후 사용자가 배치·브랜드색 유지와 필수 텍스트 대비 보완을 승인했으므로, 로그인 힌트·오류·버튼에는 [별도 텍스트 토큰](figma-final-login-implementation-2026-11-07.md)을 적용한다. Color 영역의 존재를 공식 variables/styles 연결 완료로 간주하지 않는다.

현재 근거: [색상 리소스](../../app/src/main/res/values/colors_figma_mvp.xml), [로그인 XML](../../app/src/main/res/layout/activity_login.xml), [환자 홈 XML](../../app/src/main/res/layout/activity_client_home_figma.xml), [매니저 홈 XML](../../app/src/main/res/layout/activity_manager_home.xml).

## 화면별 수정 목록

### 진입·홈

| Figma | 현재 앱과 차이 | 반영 범위 |
| --- | --- | --- |
| [로그인 `1:13671`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-13671) | 현재 큰 로고·버튼 구성과 간격이 다름. 이메일·Kakao·Google 경로는 이미 있음 | 1차: 원본 로고·입력·오류·보조 링크·3개 소셜 버튼 적용. Apple은 제공자 미연동 안내를 명확히 하고 실제 인증 연결은 선행 설정 후 구현 |
| [회원가입 본인인증 `1:13797`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-13797) | 6자리 문자 코드·3분 만료 시안. 현재 이름·전화 형식 입력과 이메일 인증은 같은 기능이 아님 | 기존 가입 화면 스타일은 반영 가능. 실제 SMS·본인확인은 별도 제공자·비용·오용 방지 계약 필요 |
| [일반 홈 `1:6666`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-6666) | 현재 큰 인사말·진행 카드에서 작은 제목·예약 진입 영역·콘텐츠 목록으로 변경 | 2차: 실제 예약 상태를 유지하면서 밀도 조정. 이번에는 홈의 위치·날짜·시간 영역을 기존 예약 폼 진입으로 연결한다. 홈 자체 입력과 별도 만남 시각 초안은 3차 예약 계약과 함께 반영 |
| [매니저 홈 `1:7071`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-7071) | 진행·완료·취소 집계, 서류 진입, 콘텐츠가 중심 | 2차: 본인에게 허용된 데이터로 상태별 카드 표시. 전체 건수가 없는 API 응답을 전체 집계처럼 표현하지 않음 |
| [캘린더 `1:7866`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-7866)와 홈 하단 메뉴 | 시안은 일반 5탭, 매니저 5탭. 현재 환자·보호자는 공통 4탭 | 2차부터 Figma의 역할별 5탭을 목표로 이동·복귀·딥링크 계약 변경. 연결 기능이 없으면 별도 기능 작업을 함께 마련하고 기존 4탭을 최종 기준으로 고정하지 않음 |

근거: [LoginActivity](../../app/src/main/java/com/example/bodeul/ui/auth/LoginActivity.java), [MainActivity](../../app/src/main/java/com/example/bodeul/MainActivity.java), [ManagerActivity](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerActivity.java), [공통 내비게이션 계약](client-bottom-navigation.md).

홈의 보들백서 썸네일은 완성본 원본 자산을 사용하고 본문은 실제 앱 안내에 연결한다. 사진을 실제 사용자 정보로 쓰거나 예시 게시일·미구현 AI 매칭을 실제 서비스처럼 고정하지 않는다.

### 예약

| Figma | 현재 앱과 차이 | 반영 범위 |
| --- | --- | --- |
| [예약 대상 `1:2722`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-2722) | 본인·대신 예약을 먼저 선택. 보호자 수신함·승인/철회는 #509로 들어왔지만 생성 흐름 전체가 완료된 것은 아님 | 3차: 본인 예약의 단계 분리부터. 보호자 초안·수신자 지정·동의 흐름은 #419 계약을 재사용하고 기능 플래그를 임의로 켜지 않음 |
| [환자 정보 `1:2939`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-2939), [건강·질문 `1:5408`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-5408) | 환자 정보·건강·복약·사전 질문을 단계별 입력. 현재 건강·복약·특이사항은 있으나 사전 질문 배열 등은 기존 초안 필드와 일치하지 않음 | 기존 필드의 검증·초안 복원은 유지. 새 필드를 화면에서만 저장한 척하거나 특이사항 문자열에 몰아넣지 않음 |
| [진료과 `1:3303`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-3303), 병원·시간 선택 화면군 | 병원 검색·날짜·장소 선택 기능은 존재. 시안의 만남 시각과 병원 예약 시각은 현재 단일 `appointmentAt`과 다름 | 3차: 기존 선택기와 예약 폼 왕복부터 개편. 두 시각의 의미·순서 검증·기존 예약 호환성을 정한 별도 계약 PR 뒤 추가 |
| [카드 완료 `1:7289`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-7289), [입금 완료 `1:7467`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-7467) | 시안에 매칭 후 자동결제·입금 및 매칭 기한 문구가 있음. 현재 MVP의 무통장입금·서버 상태와 다름 | Figma의 결제·매칭 흐름을 목표로 채택하고 원장·결제 서비스 연동을 별도 작업으로 마련. 연동 전에는 실제 접수·배정·입금 상태만 표시하며 결제 성공·확정 기한을 꾸며내지 않음 |
| [거동 상태 `1:21540`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-21540), [성년후견 `1:21554`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-21554) | 시안에 휠체어·성년후견에 따른 서비스 불가 안내가 있음 | Figma만으로 새 이용 제한을 만들지 않음. 현재 확정된 대상·동의 정책과 대조할 항목으로 분리 |

근거: [BookingActivity](../../app/src/main/java/com/example/bodeul/ui/booking/BookingActivity.java), [BookingFormBinder](../../app/src/main/java/com/example/bodeul/ui/booking/BookingFormBinder.java), [BookingRequestDraft](../../app/src/main/java/com/example/bodeul/domain/model/BookingRequestDraft.java), [완료 화면](../../app/src/main/java/com/example/bodeul/ui/booking/BookingCompletionActivity.java), [보호자 예약 수신함](guardian-booking-inbox.md).

예약 입력을 나눌 때 공통 초안 객체 하나를 유지하고, 뒤로 가기·화면 회전·프로세스 복원·중복 제출 방지를 먼저 검증한다. 새 필드가 필요하면 Android만 바꾸지 않고 Core API DTO·검증·DB migration·관리자 웹의 표시 영향을 같은 작업 범위에 적는다.

### 이용내역·매니저 업무·동행 진행

| Figma | 현재 앱과 차이 | 반영 범위 |
| --- | --- | --- |
| [매칭 목록 `1:8430`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-8430), [상세 `1:9594`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-9594) | 미배정 목록을 보고 매니저가 `매칭하기`를 누르는 흐름. 현재는 관리자 배정 | Figma 매칭 흐름을 구현 목표로 채택. 목록의 최소 정보·승인 매니저 자격·중복 수락/배정 경쟁을 서버 계약으로 검증한 뒤 활성화. 로그인 스타일 작업과는 분리 |
| [이용내역 완료 `1:9053`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-9053) | 상태별 탭, 병원 카드, 날짜·장소, 리포트·평가 진입 | 4차: 현재 목록/상세와 상태 모델 재사용. 취소·진행·완료는 서버 원문 상태로 분류하고 조회 범위·페이지 범위를 보존 |
| [인증 서류 `1:14617`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-14617) | 시안은 병원동행 자격증·범죄경력 서류 필수 등. 현재 업로드 허용 정책과 다름 | 상태 카드·파일 선택 UI는 개선 가능. `LICENSE`·`NURSING_LICENSE`의 현행 허용 범위 유지. enum에 남아 있다는 이유로 범죄경력 업로드를 다시 열지 않음 |
| [진행 화면 `1:10211`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-10211) | 시안 9개 번호 단계와 최종 확인/리포트. 현재 서버 기반 단계 코드 13종과 구형 7단계 호환 | 5차: Figma의 9단계 사용자 흐름을 목표로 표시/저장 매핑과 새 세션 계약을 정리. 진행 중인 과거 세션은 호환하고 기존 DB 단계를 일괄 덮어쓰지 않음 |
| [접수 확인 `1:10944`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-10944), [진료 기록 `1:11481`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-11481) | 스캔·녹음 진입이 있고 현재 수동 입력·내부 STT 개발과 구분 필요 | 대기 정보·수동 메모 UI부터 반영. OCR·환자 음성 활성화·AI 요약을 동일 PR에서 추가하지 않음 |

근거: [이용내역 모델](../../app/src/main/java/com/example/bodeul/ui/booking/ClientBookingHistoryScreenModel.java), [매니저 이력](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerHistoryActivity.java), [서류 허용 정책](../../app/src/main/java/com/example/bodeul/data/ManagerDocumentUploadPolicy.java), [ManagerGuideActivity](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideActivity.java), [단계 registry](../../app/src/main/java/com/example/bodeul/ui/manager/ManagerGuideStepRegistry.java).

동행 진행은 다음 경계를 유지한다.

- 서버가 내려 준 `GuideStep.code/order`와 진행 가능 여부가 기준이다. UI에서 다음 단계나 종료 성공을 선반영하지 않는다.
- 진료 보조·진료 요약, 약국 이동·처방 서류·복약 확인처럼 시안에서 합쳐진 정보를 기존 필수 단계에서 누락하지 않는다. 9개 시각 그룹이 필요하면 저장 단계와 분리된 표시 매핑을 별도 검증한다.
- `CARE_ENDED`와 `COMPLETED`를 합치지 않는다. 종료 시각·종료 후 매니저 접근 제한·최종 일지 300자 제한을 유지한다.
- #422 단계 표시, #423 메모 모음, #424 현재 위치의 기존 구현을 재사용한다. 새 디자인이라는 이유로 별도 저장 경로나 중복 기능을 만들지 않는다.

### 리포트·채팅·내 정보

| Figma | 현재 앱과 차이 | 반영 범위 |
| --- | --- | --- |
| [최종 리포트 `1:11317`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-11317), [일반 사용자 리포트 `1:19303`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-19303) | 시안은 병원·시간·진료·약국·일지 요약 중심. 현재는 큰 완료 문구·일러스트 뒤에 정보가 나옴 | 6차: 상단 장식을 줄이고 실제 리포트를 먼저 표시. 빈 항목은 데이터 없음으로 구분. PDF 저장·외부 공유는 별도 권한·동의·파일 수명 계약 후 구현 |
| [채팅 목록 `1:9194`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-9194), [상세 `1:15194`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-15194) | 현재 상세는 예약 ID 기반으로 메시지·첨부·읽음·알림을 처리. 전체 방 목록·닉네임 검색은 별도 기능 | 7차: 상세 헤더·말풍선·상태·진행/상세 링크부터. 방 목록은 허용된 참여관계 조회 계약을 확인한 뒤 추가 |
| [마이페이지 `1:7645`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-7645), [설정 `1:3382`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-3382) | 현재 프로필·건강정보·로그아웃·문의가 분산. 새 화면은 메뉴 그룹과 일반/매니저 전환을 노출 | 7차: 실제 기능만 메뉴로 묶음. 토글 하나로 서버 역할·승인을 바꾸지 않음. 결제수단 등 미구현 설정을 정상 동작처럼 노출하지 않음 |
| [평가 `1:18646`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-18646) | 시안은 친절도·전문성·소통별 별점. 현재는 단일 5단계 평가 enum | 3항목 점수·집계를 목표로 API와 기존 값 호환 설계. 과거 단일 평가를 임의로 3개 점수로 복제하지 않음 |
| [수익금 `1:4367`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-4367), [출금 `1:20447`](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=1-20447) | 잔액·입출금·계좌·출금 기한은 실제 원장과 지급 실행을 전제 | 현재 정산 관련 필드를 출금 가능한 잔액으로 간주하지 않음. #27과 연계된 별도 금전 기능이며 시연용 잔액·출금 성공을 실제 화면에 만들지 않음 |

근거: [GuardianReportActivity](../../app/src/main/java/com/example/bodeul/ui/report/GuardianReportActivity.java), [리포트 조회 정책](../../app/src/main/java/com/example/bodeul/ui/report/GuardianFinalReportPolicy.java), [CompanionChatActivity](../../app/src/main/java/com/example/bodeul/ui/chat/CompanionChatActivity.java), [ClientProfileActivity](../../app/src/main/java/com/example/bodeul/ui/profile/ClientProfileActivity.java), [현행 평가 값](../../app/src/main/java/com/example/bodeul/domain/model/AppointmentFollowUpReviewRating.java).

리포트의 건강 조언·날씨·질환별 안내 예시는 실제 계산·의료 판단 결과로 옮기지 않는다. 채팅의 시스템 메시지도 실제 서버 이벤트가 있을 때만 표시하며, 종료된 매니저·동의 철회된 참여자가 새 목록이나 링크를 통해 다시 접근하지 못하게 한다.

## 정책·계약 차이 처리

아래는 전체 작업을 멈추는 승인 목록이 아니다. **제품 목표는 Figma로 확정**하고, 필요한 서버 계약·외부 서비스·법률/보안 검증은 의존 작업으로 분리한다. Figma에 명시된 색상·크기·간격·메뉴는 임의로 재설계하지 않는다. 미정 오류·로딩 등은 기존 기능을 유지하며 보완한다.

| 차이 | 이번 적용 기준 | 후속 조건 |
| --- | --- | --- |
| 5탭과 매칭 탭 | Figma의 역할별 5탭·매칭 흐름을 목표로 변경 | 내비게이션 회귀, 승인 매니저 범위·동시 수락·기존 배정 호환 검증 |
| 9단계 디자인 | Figma 9단계 흐름을 목표로 변경 | 기존 세션 코드·구형 단계 호환과 필수 기록 누락 방지 |
| 서류·대상 제한 | 현행 최소수집과 동의 기준 유지 | #313·#347 근거 확인 전 신규 민감서류 수집·이용 제한 금지 |
| 카드 자동결제·수익금·출금 | Figma 흐름을 목표로 추가. 기존 #27 계약은 호환·전환 자료이며 미구현 기능을 성공 상태로 표시하지 않음 | 원장·수취/환불·정산·지급 주체·결제 서비스 검증 |
| Apple·SMS | Figma 기능을 목표로 추가. 기존 로그인은 유지하고 제공자 미연동 여부를 명확히 구분 | 제공자 설정, 요금·재시도/남용 방지, 서버·앱 연동 검증 |
| 녹음·OCR·영상·AI | 수동 입력, #420 내부 개발 경계, #391 자산 검수 유지 | 실제 민감 데이터 처리·사용 권리·운영 활성화 조건 별도 충족 |
| 외부 리포트 공유 | 기존 인앱 권한 조회 유지 | 공유 대상·동의·철회·만료·다운로드 후 잔존 위험 검토 |

사용자는 새 완성본을 기존 MVP보다 상위의 제품 기준으로 지정했다. 아래 과거 기획·이슈는 세부 처리와 기술 의존성 확인 자료이며 새 디자인을 거절하는 근거가 아니다. 다만 실제 수집·인가·결제·삭제를 안전하게 실행할 조건은 별도로 갖춘다.

## PR 단위 반영 순서

아래 차수는 의존 순서다. 한 차수를 반드시 하나의 큰 PR로 묶지는 않는다. 특히 환자/매니저 홈, 예약 하위 화면, 리포트/채팅은 각각 작게 나눈다.

| 순서 | 작업 단위 | 완료 기준 | 의존·추적 |
| --- | --- | --- | --- |
| 0 | 원본·코드 기준과 차이 기록 | 이 문서와 디자인 색인 연결 | 이번 문서 작업 |
| 1 | 의미 기반 색상·텍스트·버튼·입력 스타일 + 로그인 1개 | 360/375/412dp에서 입력·오류·키보드·로딩·소셜 버튼이 읽히고 기존 인증 경로 유지 | #312를 공통 토큰 범위로 활용. 새 SDK·폰트·의존성 추가 없음 |
| 2 | 환자 홈, 이어서 매니저 홈·5탭 | 예약 없음/대기/진행/완료와 긴 이름·빈 집계, 새 탭 이동·복귀 확인. 매칭은 서버 계약 작업과 연결 | 1 이후, #304의 오래된 node 기준 정리 |
| 3 | 본인 예약, 선택기, 건강정보, 접수 완료 | 입력 왕복·초안 복원·중복 제출 방지·서버 오류·완료 문구 확인 | 1 이후. #419 보호자 생성 작업은 별도 기능 PR과 연결 |
| 4 | 이용내역·상세, 필요 시 달력형 보기 | 서버 상태별 필터, 빈 목록, 권한 없는 예약, 목록 범위 확인 | 2~3 이후. 달력은 일정·이력 안에서 먼저 구현 가능성 확인 |
| 5 | 동행 단계 표시와 단계별 입력 | 현재·완료·잠금·재진입·중복 진행 방지, 구형/신형 snapshot과 종료 경계 회귀 없음 | 1·4 이후, #422·#423·#424 재사용 |
| 6 | 최종 리포트 정보 위계 | 승인된 독자만 실제 저장된 진료·복약·일지 표시. 미완료/빈 리포트 구분 | 5 이후. 외부 공유·AI 문구 제외 |
| 7 | 채팅 상세, 내 정보·문의 메뉴 | 첨부/읽음/알림·종료 제한·계정 전환·로그아웃 유지 | 공통 컴포넌트 재사용. 채팅 목록·검색은 계약 확인 후 분리 |
| 별도 | 예약 새 필드, 3항목 평가, 방 목록 등 | DTO·서버 인가·기존 데이터 호환·화면 검증을 갖춘 독립 기능 | 화면만 있는 완료 상태를 만들지 않음 |

**1차 공통 스타일·로그인은 #510으로 `dev`에 병합됐으며 [구현 기록](figma-final-login-implementation-2026-11-07.md)에 검증을 분리한다.** 2차의 환자·보호자 홈과 일반 사용자 5탭은 [홈 구현 기록](figma-final-client-home-implementation-2026-11-07.md)을 따른다. 캘린더는 본인 예약의 날짜 필터부터 연결했고 전체 시각 개편은 4차에서 이어간다. 실제 외부 인증·실기기·개발 API 종단 검증은 이 작업에서 수행하지 않았다. 전역 테마를 먼저 바꿔 모든 구형 화면에 동시에 영향을 주거나 500개 frame마다 Activity를 만드는 방식은 쓰지 않는다.

보호자 예약 #419는 디자인 전체 교체를 기다릴 필요가 없다. 승인/철회 수신함에 이어 초안 작성·수신자 지정·제출/복구를 기능 단위로 진행하고, 새 예약 UI가 준비되면 같은 계약에 연결한다. 실제 두 계정 개발 환경 검증 전에는 기본 비활성 경계를 유지한다.

### 11월 13일 예약 상세 작업

이용내역 목록 #516과 결제 확인·신청 완료 #521의 `dev` 병합을 확인하고, 다음 범위인 환자·보호자 예약 상세를 `codex/figma-final-booking-status`에서 구현했다. 매칭중·입금대기·진행전·진행중·완료·취소 시안과 기존 역할별 동작을 함께 적용한다. 구현·검증 결과와 원본에 있지만 현재 계약에 없는 항목은 [예약 상세 구현 기록](figma-final-booking-status-2026-11-13.md)에 구분한다. 개발 브랜치의 실제 반영 여부는 해당 PR의 병합 상태를 따르며, 배포는 별도 단계다.

다음 화면 단위 작업은 5차의 동행 진행·단계별 입력이다. 별도 만남 시각·인적사항·사진·3항목 평가 등 새로운 데이터 계약과 전체 실기기 리허설이 남아 있으므로, 예약 상세의 화면 전환을 전체 Figma 적용 완료로 보지 않는다.

## 기존 이슈와 연결

| 이슈 | 이번 비교에 따른 처리 방향 |
| --- | --- |
| [#312 디자인 토큰](https://github.com/bodeul110/bodeul-platform/issues/312) | 새 Color/Text Style/Button 영역과 색상 불일치 근거로 작업 범위를 갱신할 수 있음. 토큰 존재만으로 앱 전체 일치·완료 처리하지 않음 |
| [#304 역할별 홈·권한](https://github.com/bodeul110/bodeul-platform/issues/304) | 이전 가이드 node 중심의 조건을 새 완성본과 현재 로그인/권한 정책에 맞춰 대체. 구형 화면 수정 요구를 그대로 반복하지 않음 |
| [#419 보호자 예약](https://github.com/bodeul110/bodeul-platform/issues/419) | #509 수신함 병합과 남은 생성·동의·개발 검증 범위를 분리. 새 예약 대상 화면의 기능 근거로 사용 |
| [#422 단계](https://github.com/bodeul110/bodeul-platform/issues/422), [#423 메모](https://github.com/bodeul110/bodeul-platform/issues/423), [#424 위치](https://github.com/bodeul110/bodeul-platform/issues/424) | 기존 구현을 디자인에 연결하고 남은 검증만 유지. 새 시안 때문에 동일 기능의 이슈를 중복 생성하지 않음 |
| [#313 서류](https://github.com/bodeul110/bodeul-platform/issues/313), [#27 결제](https://github.com/bodeul110/bodeul-platform/issues/27), [#420 STT](https://github.com/bodeul110/bodeul-platform/issues/420), [#391 영상](https://github.com/bodeul110/bodeul-platform/issues/391) | 시안의 수집·금전·미디어 기능을 활성화하는 근거가 아니라 별도 범위·조건을 확인하는 연결점 |

최초 비교 단계에서는 GitHub 이슈·PR 본문이나 상태를 수정하지 않았다. 이후 공통 스타일·로그인은 #510으로 별도 반영했으며, 이번 환자 홈의 원격 반영·배포 여부는 해당 PR 기록과 구분한다.

## 구현 시 검증 기준

- 화면마다 Figma의 좁은 node를 다시 확인하고 같은 역할·상태·데이터로 비교한다. 장식용 상태바·키보드와 실제 Android 시스템 UI는 분리한다.
- 폭 360/375/412dp, 글꼴 배율 1.0/1.3, 긴 한글·큰 금액·여러 줄 오류, IME, 3버튼/제스처 내비게이션에서 잘림·가림을 확인한다.
- 정상 화면뿐 아니라 로딩·빈 데이터·네트워크 실패·권한 거부·재시도·버튼 비활성·진행 중 이탈을 포함한다. 터치 영역은 최소 48dp를 목표로 하고 글자·상태 색 대비를 확인한다.
- Android 코드 변경마다 `.\gradlew.bat assembleDebug --console=plain`과 변경 영역 단위 테스트를 수행한다. 공통 스타일·화면 흐름 변경은 관련 화면의 회귀 캡처까지 확인한다.
- 환경이나 인증 진입에 영향이 있으면 `node tools/android/check-environment-boundary.mjs`와 관련 인증 회귀 검사를 추가한다. Core API 변경 시 별도로 `.\core-api\gradlew.bat -p core-api check --console=plain`을 수행한다.
- 계약 변경 시 개발 DB에서 migration·이전 데이터 호환·복구 경로를 확인하고 관리자 웹 영향을 기록한다. UI 변경만을 이유로 운영 migration·배포를 실행하지 않는다.
- 마지막에는 개발 환경에서 환자 예약 → 관리자 배정 → 매니저 동행 진행/종료 → 환자 리포트까지 확인한다. 보호자 경로는 별도 계정과 동의/철회·종료 후 접근 검증을 추가한다.

## 이번 검증과 남은 범위

아래 표는 최초 비교 단계의 증적이다. 이후 앱 변경·검증 결과는 [로그인 구현 기록](figma-final-login-implementation-2026-11-07.md)과 [환자 홈 구현 기록](figma-final-client-home-implementation-2026-11-07.md)을 따른다.

| 구분 | 결과 |
| --- | --- |
| 확인한 것 | 완성본 주요 화면·기초 스타일, 현재 `dev` 코드 경로, 기존 이슈, 합성 미리보기 3화면 |
| 렌더링에서 본 차이 | 예약의 기존 단계형 폼, 진행 화면의 큰 제목·카드, 리포트 상단의 큰 완료 장식이 새 시안과 다름 |
| 재현 확인 후보 | 예약 미리보기 상단 제목과 도구 영역 간격. debug 미리보기의 영향일 수 있어 실제 앱의 확정 결함으로 기록하지 않음 |
| 변경한 범위 | 비교·반영 계획, 디자인/기획 기준과 참조 색인. 앱 코드·Figma 원본·서버·DB·의존성·GitHub 상태 변경 없음 |
| 수행하지 않은 것 | 새 APK 빌드, 실기기·실제 계정 로그인·서버/DB 왕복, 전체 화면 픽셀 검사, Notion 최신 답변 재확인 |
| 남은 범위 | 1차부터 실제 구현·화면별 검증·개발 환경 리허설. 이 문서는 디자인 적용 완료 보고가 아님 |

## 설계 판단

- 작업 목적: 새 완성본을 현재 앱에 적용할 때 시각 개선과 기능·정책 변경을 구분하고 재작업을 줄인다.
- 선택한 방식: 최신 완성본을 기존 MVP보다 우선하고 공통 스타일·로그인부터 적용한다. 기존 계약과 충돌하는 목표는 후속 서버·데이터 변경까지 포함해 구현한다.
- 대안: 전체 테마와 모든 화면을 한 번에 교체하거나 새 Figma 흐름에 맞춰 서버 계약까지 동시에 변경한다.
- 선택 이유: 현재 MVP 규모에서는 예약·동행·인가가 이미 연결돼 있어, 작은 화면 PR로 나누는 편이 회귀 원인을 찾기 쉽고 시연 흐름을 유지할 수 있다. 새 데이터가 필요한 기능은 별도 계약 PR로 추적한다.
- 리스크: 전환 중 구형·신형 스타일이 공존할 수 있다. 공용 컴포넌트 적용 현황과 동일 상태 캡처로 관리하며, 스타일 리소스의 일괄 변경으로 비전환 화면을 깨뜨리지 않는다.
