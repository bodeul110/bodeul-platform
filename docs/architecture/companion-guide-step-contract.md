# 동행 가이드 13개 화면과 단계 계약

기준일: 2026-08-29

상태: PostgreSQL `stepCode` 검증과 세션 snapshot 계약은 Flyway V14로 구현했고, Core API 상세 응답과 Android 공통 화면 registry가 이를 소비한다. 가이드 5의 필수 확인은 Flyway V16과 Android 확인·재진입 UI로 준비했으며 서버 차단은 기본 비활성이다. V18은 가이드 8 선택 결제 증빙, 가이드 10 선택 이미지, 가이드 12 `CARE_ENDED`, 가이드 13 선택 일지와 리포트 재시도 상태를 구현했다. V21은 가이드 2 snapshot에 선택 영상 자산 메타데이터와 대체문구를 추가하고 Android가 재생 경로가 없을 때 텍스트·카카오맵으로 fail-closed하도록 준비한다. 실제 영상 재생과 production 게시는 아직 범위 밖이다.

## 검증 기준

- Figma `보들 가이드`의 `Page 2(460:2)`와 가이드 1~13 node는 PR #295 당시 마지막으로 실조회한 결과를 사용한다.
- Notion `개발 정책 관련 내용`의 2026-08-28 갱신본과 GitHub #307의 2026-08-28 OWNER 댓글을 확인했다. 두 근거 모두 가이드 5 필수 확인을 바로 구현 가능한 기존 계약으로 분류한다.
- 이번 문서 작성에서는 Figma MCP Starter 호출 한도로 원본을 다시 열지 못했다. 따라서 node와 화면 내용은 마지막 검증 스냅샷이며 이후 변경이 없다고 단정하지 않는다.
- 구현 상태는 PostgreSQL Flyway V2·V5·V8·V10~V16, Spring Core API와 Android 현재 변경 코드를 기준으로 다시 대조했다.

## 판단 기록

### 작업 목적

Figma의 13개 화면, PostgreSQL 병원 가이드의 데이터 기반 단계 수, Android의 7단계 공통 화면이 서로 다른 기준으로 진행되는 문제를 막는다.

### 선택한 방식

화면 번호와 표시 순서에서 분리된 안정적인 `stepCode`를 두고, 단계 완료 이벤트와 사용자 입력 계약을 별도 항목으로 관리한다. 진행 가능 여부는 Core API가 판단하고 Android는 서버가 준 단계 계약을 표시한다.

### 대안

- Figma의 화면 번호 1~13을 `current_step_order`에 고정할 수 있다.
- 기존 7단계 공통 가이드를 유지하고 13개 화면은 시안으로만 둘 수 있다.
- Android가 단계 제목을 해석해 화면과 필수 입력을 자체 결정할 수 있다.

### 선택 이유

현재 MVP 규모에서도 병원별 단계 수와 순서가 달라질 수 있다. 순번을 의미로 사용하면 중간 단계 추가만으로 진행 중 세션의 의미가 바뀌고, 앱과 서버가 서로 다른 완료 조건을 적용할 수 있다. 안정적인 코드와 서버 판정 경계를 두면 화면 순서를 바꾸거나 병원별 단계를 추가해도 같은 업무 의미를 유지할 수 있다.

### 리스크

- 실행 중인 세션에 가이드 버전이나 단계 스냅샷이 없어서 관리자 가이드 수정이 과거 세션의 총 단계 수를 바꿀 수 있다.
- Firebase Storage 객체와 PostgreSQL 메타데이터 저장 사이의 보상 삭제가 실패하면 #222 정리 작업이 회수해야 한다.
- V18 적용 전 서버나 앱을 먼저 배포하면 새 열·상태를 읽지 못하므로 DB → 서버 → 앱 순서를 지켜야 한다.

## V14 구현 판단

### 작업 목적

관리자 가이드 변경이 진행 중 세션의 총 단계 수와 의미를 바꾸지 않도록 세션 생성 시점의 단계 배열을 고정한다.

### 선택한 방식

기존 코드 없는 가이드는 계약 버전 `0`으로 보존하고, 명시적으로 버전 `1`인 가이드만 `stepCode`·연속 `order`·제목·설명 구조를 엄격히 검증한다. 신규 배정은 같은 가이드 행의 ID, revision, 계약 버전과 단계 배열을 세션에 함께 저장한다.

### 대안

기존 가이드를 13개 코드로 일괄 변환하거나, 세션 조회 때마다 최신 가이드를 계속 조인하거나, `currentStepCode`를 DB 생성 열로 중복 저장할 수 있다.

### 선택 이유

현재 데이터에는 코드 없는 7단계 가이드와 출처를 확정할 수 없는 Firestore 세션이 함께 있다. 전환 migration에서 의미를 추정하면 잘못된 단계가 영구 기록되므로 legacy 상태를 명시하고, 코드 기반 가이드는 새 계약으로 별도 진입시키는 방식이 현재 규모와 롤링 배포에 맞다.

### 리스크

V14 뒤 Core API는 세션에 고정된 snapshot만 읽고, live 병원 가이드를 세션 조회에 다시 조인하지 않는다. rollback은 V14 이후 가이드나 세션 데이터가 생기면 정보 손실을 막기 위해 중단한다.

## Core API snapshot 진행 판단

### 작업 목적

관리자 가이드 수정과 무관하게 진행 중 세션의 단계 응답과 advance 결과를 동일하게 유지한다.

### 선택한 방식

V14 snapshot을 구조화해 additive 응답으로 제공하고, 조회와 advance가 같은 코드·순서·현재 범위 판정을 사용한다. repository UPDATE에도 snapshot 범위와 낙관 잠금 조건을 함께 둔다.

### 대안

조회 때 최신 `hospital_guides`를 계속 조인하거나, Android가 `totalStepCount`만 보고 단계 코드를 추정하거나, legacy 제목에서 코드를 자동 생성할 수 있다.

### 선택 이유

현재 MVP 규모에서도 관리자 수정이 진행 중 동행의 의미를 바꾸면 실기기 복구와 보호자 표시가 서로 달라진다. 세션 생성 시점 snapshot을 단일 기준으로 쓰면 별도 이벤트 모델을 도입하기 전에도 기존 API 호환성을 유지하면서 잘못된 진행을 409로 차단할 수 있다.

### 리스크

코드 없는 `LEGACY_HOSPITAL_GUIDE_V0` 세션은 안전하게 진행을 차단한다. V14 적용 뒤 Core API를 배포하고, 신규 배정에 쓰는 병원 가이드를 코드 계약 v1으로 승격하지 않으면 운영 세션이 `STEP_CONTRACT_MISMATCH` 상태가 될 수 있다.

## Android snapshot 표시 판단

### 작업 목적

Android가 서버의 고정 단계 배열을 무시하고 7단계 공통 가이드를 다시 만드는 문제를 없애며, 재조회 뒤에도 같은 현재 단계와 진행 제한을 복구한다.

### 선택한 방식

Core API 응답에 `steps` 키가 있으면 빈 배열까지 서버 snapshot으로 보존하고, 키 자체가 없는 롤링 배포 호환 응답에만 기존 fallback을 사용한다. `currentStepCode`로 포커스 단계를 먼저 찾고, 알려진 코드는 공통 표시 유형 registry에 연결하며 유효한 unknown 코드는 제목·설명을 유지한 일반 화면으로 보낸다. `canAdvance=false`이면 Android 저장소가 advance 요청을 보내지 않는다.

### 대안

Android가 단계 제목이나 순번으로 13개 화면을 추정하거나, 서버 응답과 무관하게 7단계 fallback을 계속 사용할 수 있다. 모든 코드를 Android enum으로 고정해 unknown 코드를 오류로 처리하는 방법도 있다.

### 선택 이유

현재 MVP에서도 병원별 추가 단계와 진행 중 가이드 revision 고정이 필요하다. 서버 snapshot을 원본으로 쓰면 0·1·7·13·13초과 단계가 잘리지 않고, 앱 프로세스 재생성과 realtime 재조회도 최신 병원 가이드가 아닌 해당 세션의 동일 snapshot으로 복구된다. unknown 코드를 일반 화면으로 보존하면 서버의 additive 확장을 구버전 앱이 임의로 차단하지 않는다.

### 리스크

현재 registry와 presentation resolver는 canonical 13단계와 `LEGACY_CORE_*` 7단계를 의미에 맞는 최신 전용 화면으로 선택하되, 각 서버 계약이 허용하는 입력만 노출한다. 코드 없는 legacy snapshot은 일반 표시가 가능하더라도 서버가 `STEP_CONTRACT_MISMATCH`로 진행을 막는다. 개발 DB와 Core API에서는 legacy 7단계 snapshot의 진행, 화면 재진입, 앱 프로세스 재시작 복구를 실기기로 확인했으며, 운영 환경도 V14와 Core API 배포 순서를 지킨 뒤 같은 검증을 반복해야 한다.

## 계약 원칙

| 항목 | 의미 | 규칙 |
| --- | --- | --- |
| `stepCode` | 단계의 업무 의미 | 대문자 스네이크 케이스를 사용하고 한번 배포한 의미를 재사용하지 않는다. 화면 번호나 배열 위치를 코드에 넣지 않는다. |
| `order` | 가이드 안의 표시·진행 순서 | 같은 가이드 버전 안에서 1부터 시작하는 고유 양수다. 업무 의미 식별자로 사용하지 않는다. |
| `eventCode` | 서버가 확정한 상태 변화 | 상태 변경과 멱등 이벤트 레코드는 같은 DB 트랜잭션에서 기록한다. Realtime·FCM 같은 외부 발행만 커밋 성공 뒤 처리한다. 알림 범위는 #299가 확정하기 전 임의로 확대하지 않는다. |
| `inputContract` | 단계에서 받는 값과 검증 | 형식·최대값과 필수·선택 여부를 서버와 Android가 같은 규칙으로 적용한다. #307에서는 가이드 8·10 첨부와 가이드 13 일지를 선택 입력으로 확정했다. |
| `guideRevision` | 세션에 고정된 가이드 개정 | 새 세션 생성 시 고정한다. 진행 중 세션은 최신 관리자 가이드로 자동 치환하지 않는다. |
| `videoAssetId`·`videoAssetVersion`·`videoFallbackText` | 가이드 단계의 영상 자산 참조와 대체 안내 | 세 필드는 모두 없거나 `null`이어야 하고, 값이 있으면 모두 비어 있지 않은 문자열이어야 한다. 원본 URL, 서명 URL과 인증 토큰은 snapshot·APK·로그에 저장하지 않는다. |

`MATCHED`는 가이드 1의 완료 이벤트가 아니라 동행 가이드 진입 전 예약 배정 이벤트다.

## 13개 화면 목표 계약

아래 `stepCode`와 이벤트는 후속 migration·Core API·Android 구현을 나누기 위한 이름 초안이다. 표에 `후보`로 표시된 이벤트는 현재 서버에서 생성되지 않는다.

| # | Figma node | `stepCode` 초안 | 화면 목적 | 사용자 입력과 CTA | 완료 이벤트 후보 |
| ---: | --- | --- | --- | --- | --- |
| 1 | `846:1056` | `MEETING_CONFIRMATION` | 매니저와 환자의 상봉을 확인하고 동행 시작 시점을 남긴다. | 별도 입력 없음. `상봉 완료` 시각을 서버가 기록한다. | `MEETING_CONFIRMED` |
| 2 | `941:549` | `HOSPITAL_ROUTE` | `혜화역 3번 출구 → 병원 로비 → 신경과` 이동을 한 화면에서 안내한다. | Route 1의 `로비 도착`, Route 2의 `진료과 도착`. 로비 도착만으로 다음 단계로 진행하지 않는다. | `ARRIVED_LOBBY`, `ARRIVED_DEPARTMENT` |
| 3 | `846:1309` | `RECEPTION_QUEUE` | 접수 상태와 대기번호를 기록해 보호자 진행 화면에 공유한다. | 영문·숫자 혼합 대기번호 입력, `대기번호 저장` 또는 다음 단계 CTA. | `QUEUE_UPDATED` |
| 4 | `846:1384` | `VITALS_CHECK` | 병원에서 확인한 기초 측정값을 기록한다. | 측정값은 선택 입력이며 빈 값을 숫자 `0`으로 저장하지 않는다. 구조화된 측정 필드와 단위는 후속 계약이다. | `VITALS_RECORDED` 후보 |
| 5 | `846:1521` | `PRE_CONSULTATION` | 증상, 질문과 전달 사항을 진료 전에 다시 확인한다. | 메모는 선택이고 확인 체크는 필수다. 확인 상태를 서버에 저장한 뒤에만 `진료 준비 완료`로 진행한다. | 별도 이벤트 없음 |
| 6 | `846:2386` | `CONSULTATION_SUPPORT` | 진료 중 핵심 안내와 결과를 현장 기록으로 남긴다. | 진료 메모 입력 후 `진료 완료`. 녹음·STT·AI 요약은 현재 범위가 아니다. | `CONSULT_COMPLETED` |
| 7 | `846:1653` | `CONSULTATION_SUMMARY` | 진료 요약을 검토하고 수납 전 공유 내용을 확정한다. | 진료 요약 확인·수정 후 `요약 저장`. 최종 리포트 완료와는 구분한다. | `CONSULT_SUMMARY_READY` |
| 8 | `846:1737` | `PAYMENT_EVIDENCE` | 수납 완료와 결제 증빙을 기록한다. | 첨부는 선택이다. 등록하면 JPEG·PNG·PDF 중 1개, 파일당 10 MiB 이하이며 다시 선택하거나 삭제할 수 있다. 실제 PG 결제는 범위가 아니다. | `PAYMENT_COMPLETED` 후보 |
| 9 | `846:1819` | `PHARMACY_ROUTE` | 처방전 기준 약국 이동을 돕는다. | `카카오맵에서 약국 찾기`를 누르면 카카오맵 장소 검색으로 외부 이동한다. 앱을 열 수 없으면 모바일 웹, 웹도 열 수 없으면 설치 화면으로 연결한다. | 별도 이벤트 없음 |
| 10 | `846:2507` | `PRESCRIPTION_DOCUMENTS` | 처방 관련 이미지 자료를 등록한다. | 첨부는 선택이다. 등록하면 JPEG·PNG 이미지 1~3장, 파일당 10 MiB 이하이며 다시 선택하거나 모두 삭제할 수 있다. OCR은 범위가 아니다. | 별도 이벤트 없음 |
| 11 | `846:1916` | `MEDICATION_CONFIRMATION` | 처방전, 약 수령과 복약 안내 완료 여부를 확인한다. | 처방전 수령·약국 완료·복약 안내 상태와 메모를 저장한다. | 별도 이벤트 없음 |
| 12 | `857:7252` | `CARE_COMPLETION` | 환자 상태와 인계 내용을 최종 확인하고 실제 동행을 종료한다. | `동행 종료`의 최초 서버 시각을 `care_ended_at`에 한 번만 기록하고 `CARE_ENDED`로 전환한다. 중복 요청과 재진입은 같은 시각을 반환한다. 사고·긴급상황은 #297로 분리한다. | 세션 상태 `CARE_ENDED` |
| 13 | `846:2576` | `MANAGER_JOURNAL` | 최대 300자 매니저 일지를 작성하고 최종 완료·후기 진입으로 연결한다. | 일지는 선택이며 빈 값도 허용한다. 제출 시 세션을 먼저 `COMPLETED`로 확정하고 리포트 저장은 `PENDING`·`READY`·`FAILED`로 별도 추적해 실패 후 재시도한다. | 세션 `COMPLETED`, 리포트 `READY` |

## 가이드 2 영상 1차 계약 판단

### 작업 목적

승인 영상이 아직 준비되지 않은 환경에서도 가이드 2의 데이터 계약과 안전한 대체 흐름을 먼저 고정하고, 자산 누락이나 불완전한 응답이 다음 단계 진행을 막지 않게 한다.

### 선택한 방식

- `HOSPITAL_ROUTE` 단계 snapshot에 `videoAssetId`, `videoAssetVersion`, `videoFallbackText`를 선택 필드로 둔다.
- 세 필드는 전부 없거나 `null`이어야 하고, 값이 있으면 전부 비어 있지 않은 문자열이어야 한다. 기존 4필드 snapshot은 그대로 유효하다.
- Core API는 자산 식별자와 버전만 전달하고 원본 URL이나 장기 유효 URL은 반환하지 않는다.
- Android는 메타데이터가 없거나 일부만 있으면 영상이 없는 상태로 처리하고 `videoFallbackText`, 단계 설명, 카카오맵 순서로 대체한다.
- `길안내 확인 완료`는 확인창을 거친 뒤 기존 `/advance`를 호출하며 영상 종료만으로 자동 완료하지 않는다.
- 확인창이 본 세션 ID와 `stepCode`를 저장소가 다시 읽은 최신 상태와 비교하고, 일치할 때만 기존 version 조건 요청을 보낸다.

### 대안

- 영상 원본 URL을 단계 JSON이나 Android 상수에 직접 넣을 수 있다.
- 기존 첨부 다운로드 API로 영상 전체 바이트를 전달할 수 있다.
- 플랫폼 `VideoView`를 먼저 붙이고 저장소·인가 계약은 나중에 정할 수 있다.

### 선택 이유

현재 MVP 규모에서도 영상 교체, 접근 권한과 캐시 무효화는 서버가 통제해야 한다. 기존 첨부 경로는 최대 10 MiB 전체 응답을 전제로 하므로 1080p 영상 탐색과 재시도에 맞지 않는다. 자산 ID와 버전을 snapshot에 고정하면 진행 중 세션의 안내가 관리자 수정으로 바뀌지 않고, 후속 재생 API가 짧은 수명의 URL을 발급할 때도 앱에 비밀값을 남기지 않는다.

### 리스크

- V21과 Android 1차 변경만으로 실제 재생·일시정지·탐색·재시도는 제공되지 않는다.
- 후속 구현에는 현재 세션·배정 매니저·단계·자산 버전을 검증하는 재생 경로 발급 API와 Range를 지원하는 저장소 전달 방식이 필요하다.
- Media3 도입, 승인 영상의 촬영·게시 권리와 비식별 검수, 실기기 재생 검증이 끝나기 전 production에서는 대체 안내만 유지한다.

## 가이드 9 카카오맵 외부 이동 결정

### 작업 목적

가이드 9에서 매니저가 약국을 직접 탐색할 수 있게 하면서 외부 앱 실행과 서버 검색의 책임을 섞지 않는다.

### 선택한 방식

- 현재 단계 코드가 정확히 `PHARMACY_ROUTE`일 때만 약국 찾기 CTA를 표시한다. 순번, 제목 또는 넓은 `MEDICATION` 표시 유형으로 추정하지 않는다.
- CTA는 카카오 공식 URL scheme인 `kakaomap://open?page=placeSearch`를 먼저 연다.
- 카카오맵을 열 수 없으면 `http://m.map.kakao.com/scheme/open?page=placeSearch`, 앱 스토어 순서로 시도하고 사용자에게 대체 경로를 안내한다.
- 외부 앱 실행은 단계 완료 이벤트나 `/advance` 요청을 만들지 않는다. 일반 복귀에서는 기존 ViewModel의 같은 세션과 단계를 유지하고, 구독 중인 Realtime 갱신만 반영한다.
- 장소 검색어와 결과를 앱이나 PostgreSQL에 새로 저장하지 않는다. 예약 병원 검색과 내장 지도 후보 조회에 쓰는 Core API Kakao Local 경로는 유지한다.

### 검토한 대안

- Android가 Kakao Local REST API를 직접 호출하면 키가 APK에 포함되고 서버의 역할 검증·호출 제한을 우회하므로 제외했다.
- 가이드 9도 Core API 검색 결과 목록을 먼저 표시하는 방식은 구현과 상태 관리가 늘고, 현재 Figma의 외부 이동 CTA보다 범위가 커서 제외했다.
- 모든 가이드 단계에서 약국 CTA를 계속 표시하면 `PHARMACY_ROUTE`의 업무 의미와 맞지 않으므로 제외했다.

### 선택 이유

현재 MVP에서는 약국을 선택·예약하는 내부 업무가 아니라 현장에서 외부 지도를 여는 보조 동작이므로 카카오맵의 장소 검색 화면을 직접 여는 방식이 가장 단순하다. 서버 검색은 앱 안에서 구조화된 장소 목록이 필요한 예약·지도 기능에만 남겨 키와 쿼터를 Core API가 통제한다.

### 리스크

- 카카오맵 URL scheme이나 스토어 주소가 바뀌면 외부 실행이 실패할 수 있어 대체 경로와 실기기 회귀 검증이 필요하다.
- 외부 앱에 머무는 동안 Android 프로세스가 종료되면 화면 상태를 다시 구성해야 한다. 현재 매니저당 미종료 세션 1개라는 운영 전제를 벗어나 여러 세션을 동시에 허용할 때는 세션 ID 지정 조회 계약을 추가한다.
- 외부 앱에서 선택한 약국은 BoDeul에 자동 반영되지 않는다. 약국 선택·공유가 제품 요구가 되면 Core API 검색 결과와 세션 저장 계약을 별도 이슈로 추가한다.

## 현재 구현 차이

V18 코드 기준 PostgreSQL `companion_sessions`는 `care_ended_at`, `manager_journal`, 리포트 생성 상태를 갖고, `companion_session_artifacts`가 Storage 객체 메타데이터를 보관한다. Core API는 snapshot 단계와 진행 판정에 종료·리포트 재시도 상태와 첨부 메타데이터를 함께 반환한다. Android Core 경로는 현재 `stepCode`에 맞는 작업 영역만 표시하며, `steps` 키가 없는 구버전 응답에만 7단계 fallback을 사용한다.

| # | PostgreSQL 현재 값 | Core API 현재 동작 | Android 현재 동작 | 필요한 후속 계약 |
| ---: | --- | --- | --- | --- |
| 1 | 첫 advance 때 기록하는 `started_at`은 있으나 상봉 완료 전용 시각·이벤트 없음 | 범용 `/advance`만 제공 | 7단계의 `환자 접촉`으로 표시 | 상봉 커밋 시각과 `MEETING_CONFIRMED` 정의 |
| 2 | V21이 선택 영상 자산 ID·버전·대체문구의 snapshot 형식을 검증하지만 Route·checkpoint 상태는 없음 | 영상 메타데이터를 additive하게 반환하며 재생 URL은 반환하지 않음 | 재생 경로가 없으면 텍스트·카카오맵을 표시하고 `길안내 확인 완료` 확인 뒤 진행 | 서명 재생 경로, Media3 재생·복원·오류 재시도, 로비·진료과 checkpoint |
| 3 | 전용 대기번호 필드 없음 | `guardian_update`에 자유 메모만 가능 | 대기번호 형식·저장 UI 없음 | 문자열 대기번호와 보호자 표시 계약 |
| 4 | 구조화된 기초 측정 필드 없음 | 범용 메모 외 검증 없음 | 단계별 측정 폼 없음 | 측정 항목·단위와 선택 입력 계약 |
| 5 | V16 `pre_consultation_confirmed`가 확인 상태를 저장 | 확인 상태 변경은 현재 단계에서만 허용한다. `bodeul.session.pre-consultation-enforcement=true`일 때만 미확인 상태를 `STEP_INPUT_REQUIRED`로 반환하고 `/advance` SQL도 다시 차단한다. 기본값은 `false`다. | 확인 체크를 Core API로 저장·해제하고 재조회·재진입 때 서버 값을 복원한다. 새 앱은 서버 차단이 꺼져 있어도 화면에서 미확인 진행을 막는다. | Android 보급·검증과 별도 승인 뒤 서버 설정을 켠다. 확인 항목 자체의 구조화가 필요해지면 별도 checklist 계약으로 확장 |
| 6 | `guardian_update`, `field_photo_note`만 있음 | PATCH 메모는 가능하나 진료 완료 이벤트 없음 | 공통 메모 입력을 제공 | 진료 완료와 요약 작성 시작 경계 |
| 7 | `session_reports.summary`, `treatment_notes`가 있음 | V18에서도 최종 리포트 입력으로 유지하며 중간 요약 전용 저장은 아직 없음 | 공통 메모 입력은 있으나 중간 확정 단계와 분리되지 않음 | 중간 요약 저장이 제품 요구가 되면 별도 필드·API로 분리 |
| 8 | V18 `PAYMENT_EVIDENCE` 메타데이터 행, 요청 UUID와 파일 제한 | 배정 매니저만 현재 단계에서 1개 교체·삭제 가능, 참여자는 인증 다운로드 가능 | JPEG·PNG·PDF 1개 선택·재선택·삭제, 미첨부 진행 허용 | V18 적용 뒤 Storage 실업로드와 만료 정리 검증 |
| 9 | `pharmacy_summary`, `pharmacy_completed`가 있음 | Kakao Local 검색은 예약·내장 지도 후보 조회에 유지 | `PHARMACY_ROUTE`에서만 카카오맵 장소 검색 CTA를 표시하고 외부 이동만으로 단계를 진행하지 않음 | 외부 앱 설치·미설치·복귀 실기기 회귀 검증 |
| 10 | V18 `PRESCRIPTION_IMAGE` 메타데이터 행 | 현재 단계에서 JPEG·PNG 1~3장 교체·삭제를 검증 | 이미지 1~3장 선택·재선택·삭제, 미첨부 진행 허용 | 실기기 다중 선택·프로세스 재진입 검증 |
| 11 | `prescription_collected`, `pharmacy_completed`, `medication_guidance_completed`, 메모 필드가 있음 | PATCH로 상태와 메모 저장 가능 | 공통 가이드 화면에서 각 상태를 수정 가능 | stepCode에 따른 노출과 완료 판정 연결 |
| 12 | V18 `care_ended_at`, `CARE_ENDED` | `/care-end`가 배정·버전·현재 코드 확인 뒤 최초 시각 보존 | `CARE_COMPLETION` CTA를 동행 종료 요청으로 분리 | V18 적용 뒤 중복 탭·재시작 실기기 검증 |
| 13 | V18 `manager_journal` 최대 300자와 리포트 상태 | 일지 선택 제출로 세션 완료를 먼저 확정하고 리포트 실패를 `FAILED`로 기록 | 선택 일지 300자 제한, 실패 세션 재진입·다시 저장 | 구버전 앱 보급 확인 뒤 완료 강제 flag 승인 |

원본은 기존 서버 전용 Firebase Storage 경계에 두고 PostgreSQL에는 경로·파일명·형식·크기만 저장한다. 결제 증빙은 JPEG·PNG·PDF 1개, 처방 이미지는 JPEG·PNG 3개까지로 용도별 정책을 분리한다. 앱 사전 검증 뒤 서버가 파일 시그니처와 10 MiB 제한을 다시 확인한다.

현재 PostgreSQL에는 제품 단계 이벤트 테이블이 없다. `CARE_ENDED`와 리포트 상태는 세션 행의 멱등 상태 전이이며 별도 알림 이벤트가 아니다. Realtime·FCM 범위를 이번 작업에서 확대하지 않는다.

새 앱은 `CARE_COMPLETION`에서 `/care-end`를 호출한 뒤 `MANAGER_JOURNAL`에서 완료한다. 구버전 앱의 마지막 단계 직접 완료는 롤링 호환을 위해 `BODEUL_SESSION_COMPLETION_ENFORCEMENT=false`인 동안만 허용하며, 이번 작업에서 preview·production 값을 켜지 않는다.

## 목표 API 최소 응답

Core API는 Android가 제목을 해석해 화면을 선택하지 않도록 아래 의미를 additive 응답으로 제공한다.

| 값 | 목적 |
| --- | --- |
| `guideId`, `guideRevision` | 세션에 고정된 병원 가이드 식별과 개정 |
| `steps[].code`, `steps[].order` | 안정적인 업무 의미와 표시 순서 분리 |
| `steps[].title`, `steps[].description` | 병원별 안내 표시 |
| `steps[].inputContract` | 입력 종류, 형식, 최대값과 정책 확정 뒤 필수 여부 |
| `currentStepCode`, `currentStepOrder` | 현재 의미와 순서의 일치 검증 |
| `canAdvance`, `blockedReason` | 서버가 판정한 진행 가능 여부와 사용자 안내 |
| `careEndedAt`, `managerJournal` | 실제 동행 종료 최초 시각과 선택 일지 재진입 복구 |
| `reportGenerationStatus`, `reportGenerationAttempts`, `reportGenerationLastError` | 세션 완료와 분리한 리포트 생성 실패·재시도 상태 |
| `artifacts[]` | 결제 증빙·처방 이미지의 인증된 메타데이터 표시 |
| `completedEvents[]` | checkpoint와 중복 요청 복구를 위한 커밋 완료 상태 |

`completedEvents[]`와 단계별 `inputContract`는 아직 응답하지 않는다. #324 범위에서는 `steps[].code`, `order`, `title`, `description`, `currentStepCode`, `canAdvance`, `blockedReason`까지만 고정했다.

### Core API 진행 판정

| `blockedReason` | 의미 |
| --- | --- |
| `SESSION_TERMINAL` | 세션이 `COMPLETED` 또는 `CANCELED`라 더 진행할 수 없음 |
| `GUIDE_NOT_READY` | snapshot이 없거나 비어 있고, 가이드를 찾지 못했거나 legacy 원본이 미확정임 |
| `STEP_CONTRACT_MISMATCH` | 코드 계약을 지원하지 않거나 order·code·현재 순번이 snapshot과 일치하지 않음 |
| `STEP_INPUT_REQUIRED` | 서버 진행 차단 설정이 켜진 상태에서 `PRE_CONSULTATION` 필수 확인을 저장하지 않아 다음 단계로 진행할 수 없음 |
| `LAST_STEP_REACHED` | 현재 순번이 snapshot의 마지막 단계임 |
| `CARE_ENDED_PENDING_COMPLETION` | 실제 동행은 종료됐고 선택 일지 제출 화면으로 재진입해야 함 |
| `REPORT_RETRY_REQUIRED` | 세션 완료는 유지하면서 실패·중단된 리포트 저장만 다시 시도해야 함 |

진행 가능한 경우 `blockedReason`은 `null`이다. `currentStepOrder=0`은 가이드 진입 전이므로 `currentStepCode=null`이고, `1..N`은 `steps[order-1].code`와 일치해야 한다. 유효한 형식의 unknown code는 일반 단계로 보존하며 차단 사유로 사용하지 않는다.

Android는 `canAdvance=false`를 우회해 순서를 올리지 않고, 서버에 없는 이벤트를 로컬 완료로 간주하지 않는다. 새 Android는 서버 진행 차단 설정이 꺼진 롤링 배포 기간에도 `PRE_CONSULTATION` 미확인 상태를 로컬 정책으로 차단한다. 서버 설정을 켜기 전까지 구버전 앱의 진행은 호환을 위해 허용된다.

## 단계 수와 알 수 없는 코드 처리

| 입력 상태 | 현재 처리 | 남은 범위 |
| --- | --- | --- |
| 가이드 없음 또는 0단계 | Core API는 빈 `steps`, `canAdvance=false`, `GUIDE_NOT_READY`를 반환한다. Android는 `가이드 준비 중`을 표시하고 advance 요청을 보내지 않는다. | 운영 가이드 준비 상태의 담당자 안내와 재시도 UX는 별도 운영 정책으로 보완한다. |
| 1단계 | Core API는 진입 전 order 0에서 진행을 허용하고 order 1에서 `LAST_STEP_REACHED`를 반환한다. | 한 단계의 전용 CTA와 종료·리포트 전이는 별도 상태 계약으로 연결한다. |
| 7단계 | `LEGACY_CORE_7_V1` snapshot의 7개 코드·제목·설명과 진행 검증에는 원본 코드를 그대로 사용하고, Android 화면 선택에만 같은 의미의 최신 전용 화면을 연결한다. Firebase 설정이 없는 일반 debug 앱의 기본 Mock 가이드도 같은 코드를 사용한다. 코드 없는 `LEGACY_HOSPITAL_GUIDE_V0`는 자동 추정하지 않고 차단한다. | 13단계의 기초 측정·진료 전 확인·진료 요약·약국 이동·처방 자료·동행 종료는 독립 단계로 복원하지 않는다. Legacy 서버가 허용하지 않는 결제 증빙과 `/care-end`도 노출하지 않는다. |
| 13단계 | Core API와 Android가 13개 상세 단계를 자르지 않고 표시하며 알려진 코드를 공통 표시 유형에 연결한다. 가이드 8·10의 선택 첨부, 12의 동행 종료, 13의 선택 일지·완료 재시도 계약은 V18 코드로 준비했다. | 나머지 코드별 입력은 각 단계의 제품 요구가 확정될 때 별도 계약으로 추가한다. |
| 13단계 초과 | 전체 배열을 보존하고 추가 코드는 일반 제목·설명 화면으로 표시하며 `canAdvance`를 따른다. | 서버가 새 코드를 정식 제품 코드로 확정하면 registry 표시 유형을 추가한다. |
| 알 수 없는 `stepCode` 또는 순서 | 유효한 unknown code는 일반 화면으로 보존한다. order 불연속·중복 code·현재 순번 범위 오류는 `STEP_CONTRACT_MISMATCH` 안내와 함께 진행을 차단한다. | unknown 코드에는 코드 전용 입력을 노출하지 않는다. |

## 진행 중 세션 호환 원칙

1. 세션 생성 시 `guideId`, `guideRevision`과 단계 snapshot을 고정한다.
2. 관리자 가이드 수정은 새 세션부터 적용하고 `IN_PROGRESS` 세션의 순서·의미·총 단계를 바꾸지 않는다.
3. 기존 7단계 세션은 완료될 때까지 생성 당시 실제 표시 기준을 legacy snapshot으로 유지한다. Core API 경로는 서버 snapshot, Firebase 경로는 세션에서 참조하던 저장 가이드 배열을 우선하며, 출처를 판별할 수 없는 경우 운영 확인 대상으로 분리한다. 순번이나 제목 유사도만 보고 13개 코드로 추정 변환하지 않는다.
4. `currentStepOrder`와 `currentStepCode`가 불일치하면 자동 보정하지 않고 진행을 막아 운영 확인 대상으로 보낸다.
5. 동일 이벤트 재요청은 세션·단계·이벤트 기준으로 멱등 처리한다.
6. `CARE_ENDED` 이후에는 운영 메모와 첨부를 수정하지 않고 선택 일지 제출만 허용한다. 완료된 리포트가 `FAILED` 또는 `PENDING`이면 같은 세션으로 재진입해 리포트만 다시 저장한다.

## 남은 정책

| 이슈 | 결정 전 확정하지 않을 값 |
| --- | --- |
| #299 | `MATCHED`, 상봉, 단계 진행, 동행 종료와 최종 완료 중 어떤 이벤트를 누구에게 FCM·앱 내 알림으로 보낼지 |
| #297 | 사고·긴급상황의 중단·인계·지원 상태. 정상 `CARE_ENDED`나 `COMPLETED`로 합치지 않는다. |

## 적용·검증 분리 기준

코드 구현 이후에도 다음 세 범위를 분리해 검증한다. migration 적용과 애플리케이션 배포는 같은 작업으로 묶지 않는다.

| 범위 | 포함할 변경 | 완료 증거 |
| --- | --- | --- |
| PostgreSQL migration | V18 종료·완료·리포트 상태와 첨부 메타데이터 | 코드 계약 테스트를 통과했다. 이후 개발 DB 연속 적용·rollback, 기존 완료 row backfill과 runtime role을 검증한다. |
| Core API | `/care-end`, 선택 일지 완료, 리포트 실패·재시도, 용도별 첨부 교체·삭제·다운로드 | 서비스·HTTP 테스트를 통과한 뒤 개발 환경에서 역할별 200·403·409·503을 검증한다. |
| Android | 종료 CTA, 선택 일지 300자, 재시도 화면, 가이드 8·10 SAF 선택·삭제 | 단위 테스트 뒤 실기기 중복 탭·다중 선택·재시작·네트워크 실패를 검증한다. |

## 현재 제외 범위

- 실제 PG, 녹음·STT·AI 요약과 OCR은 구현하지 않았다.
- 사고·긴급상황을 정상 완료 상태에 포함하지 않았다.
- `stepCode` 초안을 운영 데이터에 seed하지 않았다.
- 새 보호자 알림을 발송하지 않았다.
- V18을 개발·production DB에 적용하거나 완료 강제 flag를 활성화하지 않았다.
- Figma 원본을 이번 작업에서 최신으로 재검증했다고 표시하지 않았다.

## 관련 문서와 이슈

- [Figma 현행 화면 지도](../design/figma-current-screen-map.md)
- [Notion 제품 기준 정합성](../planning/notion-product-alignment.md)
- [매칭·동행·리포트 PostgreSQL 전환 계약](companion-session-core-api.md)
- [화면 재구성 목표](../planning/screen-restructure-target.md)
- #301 동행 가이드 1~13 화면과 DB 단계 이벤트 정규화
- #306 Figma 동행 가이드 13개 화면과 단계 계약표 작성
- #307 동행 단계 선택 입력·종료·완료 경계 구현
- #299 알림 이벤트 정책
- #314 가이드 9 Kakao 지도 경로 정렬: 본 문서의 외부 이동 계약과 Android CTA에 반영
