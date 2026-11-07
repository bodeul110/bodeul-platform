# 디자인 참조 정리

기준일: 2026-11-07

## 현재 기준

- 디자인 원본은 [Figma 보들 어플 완성본](https://www.figma.com/design/fqwi44prjEgVFZhaM6LuzS?node-id=0-1)이다.
- file key는 `fqwi44prjEgVFZhaM6LuzS`, 기준 페이지는 `Page 1`, node `0:1`이다.
- 화면별 node, Android와의 차이와 반영 순서는 [완성본 비교·반영 계획](../docs/design/figma-final-app-gap-plan-2026-11-07.md)에 정리한다. frame·상태 변형 개수를 독립 화면 수로 합산하지 않는다.
- 이전 `보들 MVP`는 [과거 구현 매핑](../docs/design/figma-mvp-implementation-map-2026-08-29.md), `보들 가이드`의 38개 화면은 [과거 화면 지도](../docs/design/figma-current-screen-map.md)에 보존하며 현행 시각 기준으로 사용하지 않는다.
- `design_refs/local/`은 Git에 올리지 않는 일회성 export·비교 캐시로만 쓴다.

## 사용 원칙

- 화면 배치, 정보 위계, CTA와 시각 상태는 Figma를 기준으로 판단한다.
- 제품 목표·사용자 흐름도 최신 완성본이 기존 MVP보다 우선한다. Figma에 없는 세부사항과 법률·보안·구현 사실의 구분은 [기획·디자인·구현 기준](../docs/planning/source-of-truth.md)을 따른다.
- Figma 문구만으로 권한 필수 여부, 인증 제공자, AI·OCR 동작, 결제·환불이나 서버 구조를 확정하지 않는다.
- 구현 또는 리뷰에는 전체 페이지보다 해당 화면의 좁은 node ID를 남긴다.
- 원본 이미지·ZIP·PDF를 저장소에 다시 커밋하지 않는다.

## 로컬 자산 상태

- 과거 `assets/`, `auth/`, `common/`, `manager/`, `overview/` PNG 묶음은 저장소에서 제거됐다.
- 현재 `design_refs/local/`에는 사용 규칙을 설명하는 README만 추적한다.
- 새 export가 필요하면 날짜가 드러나는 로컬 디렉터리에 두고 작업이 끝나면 정리한다.
