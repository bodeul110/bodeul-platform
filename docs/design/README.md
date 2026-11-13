# 디자인 문서

기준일: 2026-11-13

최신 Figma 완성본을 기존 MVP보다 우선하는 제품·화면 기준으로 사용하며 현재 구현과의 차이를 관리한다.

## 현재 기준

- [Figma 완성본과 앱 비교 및 반영 계획](figma-final-app-gap-plan-2026-11-07.md)
  - `보들 어플 완성본` Page 1과 11월 7일 `dev` 대조. 공통 스타일부터 화면별 수정 범위·계약 차이·PR 순서 정리
- [환자·보호자 공통 하단 내비게이션](client-bottom-navigation.md)
  - 완성본 홈 반영 브랜치의 5탭·날짜 필터·서버 인가 경계. 원격 반영 여부는 PR 기록과 구분
- [디자인 참조 정리](../../design_refs/README.md)
  - Figma 원본과 로컬 export 사용 원칙
- [화면 개편 목표](../planning/screen-restructure-target.md)
  - 제품 흐름을 반영한 화면 정보 구조
- [소셜 로그인 브랜드 자산](social-login-brand-assets.md)
  - 로그인 버튼의 공식 출처, 적용 기준과 파일 무결성

## 최근 화면 구현 근거

- [완성본 예약 결제 확인·신청 완료](figma-final-booking-completion-2026-11-13.md)
  - 결제 동의와 기존 결과 보존, 완료 시트·상세·지도 이동, 확대 화면 검증과 실제 결제 미연동 경계
- [완성본 매니저 홈](figma-final-manager-home-implementation-2026-11-09.md)
  - 본인 배정 집계·서류·안내·5탭, 기존 가이드와 일정 메모 유지, 검증과 후속 계약 구분
- [완성본 환자·보호자 홈](figma-final-client-home-implementation-2026-11-07.md)
  - 원본 자산·예약 진입·실제 진행 상태·안내 4개·5탭, 기존 기능과 검증 범위
- [완성본 공통 스타일·로그인](figma-final-login-implementation-2026-11-07.md)
  - 원본 자산·색상·입력창·3개 소셜 버튼, 기존 인증 경로와 시스템 영역 처리
- [보호자 예약 수신함](guardian-booking-inbox.md)
  - 서버 원문 목록·상세·환자 승인/철회, 계정 수명주기와 기본 비활성 경계
- [브랜드 스플래시](figma-brand-splash-implementation-2026-09-19.md)
- [환자 홈](figma-patient-home-implementation-2026-09-12.md)
- [환자 예약 메인](figma-patient-booking-main-implementation-2026-09-27.md)
- [병원 검색](figma-patient-hospital-search-implementation-2026-09-12.md)
- [예약 날짜·시간](figma-patient-appointment-schedule-implementation-2026-09-12.md)
- [예약 접수 완료](figma-patient-booking-completion-implementation-2026-09-12.md)
- [매니저 홈](figma-manager-home-implementation-2026-08-30.md)
- [매니저 자격 증빙](figma-manager-qualification-implementation-2026-08-30.md)
- [가이드 1](figma-manager-guide-step-one-implementation-2026-09-03.md)
- [가이드 3 접수](figma-manager-guide-reception-implementation-2026-09-15.md)
- [가이드 4~5](figma-manager-guide-pre-consultation-implementation-2026-09-15.md)
- [내 현재 위치 보기](manager-guide-current-location-implementation-2026-09-19.md)
- [단계 자유 메모 모음](manager-guide-step-memo-summary-2026-09-19.md)

각 문서는 해당 구현 당시 근거다. 병합 여부와 재시험 필요 사항은 현재 구현 상태의 최신 요약을 함께 확인한다.

## 이전 Figma 기준

- [보들 MVP 화면 인벤토리와 Android 구현 매핑](figma-mvp-implementation-map-2026-08-29.md)
  - 8월 인벤토리와 9월 21일 metadata·병합 코드 비교. 새 완성본 적용의 현재 화면 기준은 아님
- [보들 가이드 화면 지도](figma-current-screen-map.md)
  - `Page 2(460:2)`의 38개 화면과 prototype을 정리한 과거 비교 기록

## 과거 감사 이력

아래 문서는 2026-05~07 당시 기능설명서와 다운로드 산출물을 대조한 기록이다. 현재 판단에는 위의 현행 문서를 사용한다.

- [디자인 레퍼런스 재정리 메모](reference-review-2026-05-22.md)
- [기능설명서와 Figma 대조 메모](feature-spec-figma-audit-2026-05-22.md)
- [기능설명서 항목별 구현 체크리스트](feature-spec-gap-checklist-2026-05-22.md)

## 사용 원칙

- 최신 완성본은 기존 MVP보다 우선하는 제품·화면 목표다. 필요한 서버 계약과 외부 연동을 별도 구현·검증하며, 디자인만으로 개인정보 수집이나 권한 경계를 해제하지 않는다.
- frame이 존재해도 prototype 연결, 정책 승인과 실제 구현을 각각 확인한다.
- 다운로드 ZIP, PDF, PNG는 임시 비교 자료로만 사용한다.
- 구현 완료 여부는 [현재 구현 상태](../status/implementation-status.md)를 따른다.
