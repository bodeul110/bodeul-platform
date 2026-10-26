# 전체 문서 현행화 기록

기준일: 2026-09-27

## 범위와 기준

메인 저장소의 추적 Markdown 206개와 별도 관리자 웹 저장소의 8개를 문서 목록으로 삼아 현재 기준과 날짜가 붙은 이력을 구분했다. 모든 문서의 날짜를 바꾸거나 과거 검증 결과를 재실행한 것으로 덮어쓰지 않았다.

- 제품 범위: [Notion MVP와 내부 테스트 대조](notion-mvp-feedback-triage-2026-09-27.md), [기준 문서 우선순위](../planning/source-of-truth.md). Notion 연결 API는 페이지를 읽지 못해 브라우저에서 MVP 상단과 확인 가능한 본문을 열람했다. 모든 하위 토글·댓글을 새로 전수 확인한 것은 아니다.
- 구현: 현재 `dev`의 코드·package script·workflow·migration과 미병합 PR의 상태를 대조했다. 팀원 미병합 화면은 구현 완료 목록에 합치지 않았다.
- 실제 구성: [개발·운영 분리 실행 기록](dev-production-separation-2026-09-27.md)의 동일 날짜 증거와 사용자의 최초 관리자 대시보드 진입 확인을 사용했다. 이번 문서 갱신에서 클라우드 전체를 재감사하거나 DB를 변경하지 않았다.

## 정리한 내용

| 구분 | 변경 |
| --- | --- |
| 저장소·협업 | 기능 PR은 `dev`에 squash, 개발→운영 출시와 운영→개발 동기화는 merge commit. 로컬 사용자 변경 보존 |
| 환경 | 개발·운영 Firebase와 PostgreSQL 분리. 같은 환경의 관리자 Next.js와 사용자 Spring만 업무 DB 공유 |
| DB·Core API | 양쪽 Supabase Pro·Tokyo·V23, 운영 Core API 배포·health·무인증 거부와 백업·격리 복원 근거 반영 |
| 관리자 웹 | 운영 전용 DB 로그인·Production 연결, PostgreSQL 역할 기반 진입, 최초 TOTP·SUPER_ADMIN·대시보드 확인 |
| 비밀값 | 개발·운영 DB 자격 증명 분리. 승인된 Kakao REST 키 공유는 예외로 명시하고 할당량·폐기 영향 기록 |
| 문서 구조 | README는 프로젝트 소개 중심으로 유지. 현재 상태는 운영 문서, 당시 실행 결과는 날짜별 보고서에 둠 |
| 남은 검증 | 실제 운영 정상 token·환경 간 거부·Kakao·Realtime 구독·관리자 업무·보안 강제·복구를 완료와 분리 |

현재 설정의 기준값도 `production-infrastructure-state.json`의 `cloudRun=present`, `kakaoSecret=enabled`, `appCheck=preparing`, `firebaseStorageUbla=deferred`와 대조했다. 오래된 V15·무료 플랜·일시정지 설명은 현재 절차에서 제거하고, 해당 날짜의 보고서와 변경 이력에서는 보존했다.

## PR 처리

- 웹 #68·#69·#71·#72는 사용자의 의존성 변경 승인 후 최신 `dev`를 반영하고 각 head의 CI·CodeQL·Vercel Preview를 확인해 `dev`에 병합했다. Babel 8은 추가로 로컬 설치·의존성 트리·Vite 빌드를 확인했다. 운영 출시 PR이나 Production 승격은 실행하지 않았다.
- React와 React DOM 버전 조합을 수정하지 않은 웹 #70은 보류했다.
- 앱 #444·#445·#446은 코드 검토를 승인하고 선행 PR부터 `dev` 대상으로 처리하도록 안내했다. 팀원 PR은 직접 병합하지 않았다.
- 앱 #450은 새 진료 요약 저장의 mutation gate와 미저장 이탈 보호가 빠져 수정 요청을 남겼다.
- 팀원 스택 통합 head `db3f4b3`에서 `assembleDebug testDebugUnitTest assembleDebugAndroidTest`가 성공했다. 단위 테스트 298건, 실패·오류·건너뜀 0건이다. 기기 테스트 APK 컴파일은 실제 기기 실행과 다르다.

## 검증 경계

신규 기록을 포함한 메인 Markdown 207개의 상대 링크·경로 997건과 웹 Markdown 8개의 상대 링크·경로 8건을 검사했고 누락 0건이었다. 두 저장소 모두 UTF-8 디코딩 오류와 `git diff --check` 오류가 없었다. 외부 사이트 전체의 가용성이나 모든 Markdown 앵커를 전수 검증한 것은 아니다.

Android 실기기 테스트는 사용자 요청에 따라 실행하지 않았다. 운영 심사·결제·배정 데이터를 만들거나 MFA/App Check 모드를 바꾸지 않았다. 첫 관리자 한 명의 TOTP 성공은 모든 관리자의 복구 준비와 MFA 강제 완료를 뜻하지 않는다.
