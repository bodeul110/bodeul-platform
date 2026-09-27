# 매니저 동행 가이드 Step 7·9·12·13 구현 판단

## 기준

- Figma 파일: `NX07k3Tu4cLc6YgAV82RXp` / Page 1 (`0:1`)
- Step 7: `8:1088`
- Step 9: `8:1254`
- Step 12: `8:2614`
- Step 13: `8:1911`
- 구현 기준은 현재 13단계 Core API 계약과 `ManagerGuideCoordinator`가 만든 화면 모델이다.

각 노드는 `get_design_context`의 코드 컨텍스트와 스크린샷을 함께 확인했다. Figma의 카드 반경, 여백, 단계 배지, 큰 제목, 고정 하단 CTA 위계를 재사용하되, 샘플 데이터처럼 보이는 정적 요소는 실제 계약과 구분했다.

## 단계별 적용

| 단계 | Figma에서 가져온 위계 | 실제 연결 | 의도적으로 제외한 항목 |
| --- | --- | --- | --- |
| 7 진료 요약 | 요약 히어로, 환자 카드, 핵심 내용 카드 | `fieldPhotoNote` 조회·수정, 환자/병원/진료과 표시, 기존 다음 단계 액션 | AI 요약 표기, 가짜 진단·회복률·진료 시간, 보호자 전송 성공 주장 |
| 9 약국 이동 | 약국 안내 히어로, 지도 카드, 강조 CTA | 기존 Kakao 약국 장소 검색 액션과 실제 `MapView`, 기존 경로 완료 확인 | 정적 약국 후보·거리·영업 상태, 약국 선택 저장, 영수증 촬영 |
| 12 동행 종료 | 최종 확인 히어로, 확인 카드, 경고, 종료 CTA | 기존 단계 메모 원문 모아보기, 환자/병원 정보, 기존 `END_CARE` 액션 | 환자 컨디션/소지품/도착 알림을 저장했다는 표현과 새 체크리스트 계약 |
| 13 매니저 일지 | 서비스 완료 히어로, 기록 카드, 하단 완료 CTA | 기존 300자 `managerJournal`, 현재 리포트 필드, 제출 및 서버 재시도 상태 | 환자 리뷰·별점, 샘플 사진, 새 AI 리포트 생성 계약 |

## 구조

- `ManagerGuideActivity`는 Binder 생성, 뒤로 가기, 저장 버튼, 상태 바인딩만 담당한다.
- 각 단계는 별도 Binder와 include layout으로 분리한다.
- Step 9는 기존 지도 카드와 `ManagerGuideMapActionModel.isKakaoPlaceSearch()` 결과를 재사용한다. 비약국 지도 액션은 이 단계에서 시각적으로 숨기지만 모델이나 런처 계약은 바꾸지 않는다.
- Step 12는 기존 `ManagerGuideMemoItem` 원문만 렌더링한다.
- Step 13은 기존 리포트 폼을 재사용하고 매니저 일지 입력에 300자 필터와 Material counter를 함께 노출한다.
- 문자열은 공용 `strings.xml` 대신 `strings_manager_guide_remaining_steps.xml`에 둔다.

## 동적 자산 판단

Figma의 지도 이미지, 약국 목록, 환자 사진과 진료 결과는 실제 세션/API 데이터가 들어갈 자리다. 따라서 정적 이미지를 앱에 포함하지 않고 기존 Kakao 지도와 현재 도메인 데이터를 사용한다. 이 선택은 화면이 샘플 상태를 실제 결과로 오인시키지 않게 한다.

## 검증 범위

- Debug instrumentation은 Step 7 메모 저장·전환, Step 9 Kakao 약국 액션 필터·경로 완료 확인, Step 12 메모 모아보기·Step 13 전환, Step 13 300자 제한·기존 리포트 제출을 검증한다.
- 운영 저장소/클라우드 권한 없이 `ManagerGuidePreviewActivity`와 로컬 preview repository만 사용한다.
- 실제 Kakao 앱 열기, 서버 리포트 생성 완료, 재시도 응답은 외부 통합 범위이므로 UI 테스트에서 실행하지 않는다.
