# 디자인 문서

기준일: 2026-09-27

Figma 원본의 현재 화면 구조, 구현에 적용할 수 있는 시각 기준과 불일치를 관리한다.

## 현재 기준

- [Figma MVP 화면 인벤토리와 Android 구현 매핑](figma-mvp-implementation-map-2026-08-29.md)
  - 현재 `보들 MVP` Page 1의 8월 화면 인벤토리와 9월 21일 metadata·병합 코드 대조. 화면 수와 픽셀을 새로 실측한 기록은 아님
- [이전 Figma 화면 지도](figma-current-screen-map.md)
  - 이전 `보들 가이드` 파일 `Page 2(460:2)`의 38개 화면, prototype 흐름과 과거 비교 기준
- [환자·보호자 공통 하단 내비게이션](client-bottom-navigation.md)
  - 홈, 일정·이력, 동행방, 내 정보 탭과 서버 인가 소비 경계
- [디자인 참조 정리](../../design_refs/README.md)
  - Figma 원본과 로컬 export 사용 원칙
- [화면 개편 목표](../planning/screen-restructure-target.md)
  - 제품 흐름을 반영한 화면 정보 구조
- [소셜 로그인 브랜드 자산](social-login-brand-assets.md)
  - 로그인 버튼의 공식 출처, 적용 기준과 파일 무결성

## 최근 화면 구현 근거

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

## 과거 감사 이력

아래 문서는 2026-05~07 당시 기능설명서와 다운로드 산출물을 대조한 기록이다. 현재 판단에는 위의 현행 문서를 사용한다.

- [디자인 레퍼런스 재정리 메모](reference-review-2026-05-22.md)
- [기능설명서와 Figma 대조 메모](feature-spec-figma-audit-2026-05-22.md)
- [기능설명서 항목별 구현 체크리스트](feature-spec-gap-checklist-2026-05-22.md)

## 사용 원칙

- Figma는 화면 구조와 시각 위계의 원본이며 제품 정책이나 기술 계약의 원본이 아니다.
- frame이 존재해도 prototype 연결, 정책 승인과 실제 구현을 각각 확인한다.
- 다운로드 ZIP, PDF, PNG는 임시 비교 자료로만 사용한다.
- 구현 완료 여부는 [현재 구현 상태](../status/implementation-status.md)를 따른다.
