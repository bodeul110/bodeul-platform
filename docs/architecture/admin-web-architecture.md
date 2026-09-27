# 관리자 웹 역할과 서버 경계

기준일: 2026-09-27. 운영 전용 DB 연결과 최초 관리자 MFA 로그인까지 반영.

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결론

관리자 웹은 [bodeul110/bodeul-admin-web](https://github.com/bodeul110/bodeul-admin-web) 저장소의 Next.js 애플리케이션이 source of truth다. Vercel 서버가 Firebase ID token, PostgreSQL `ADMIN` 진입 자격과 활성 세부 역할을 확인한 뒤 관리자 전용 DB role로 직접 조회한다. Spring Core API나 기존 Node API를 중간 proxy로 두지 않는다.

## 검증 범위

9월 27일 운영 DB V23·관리자 전용 LOGIN과 Vercel Production 연결을 확인했다. 웹 [PR #76](https://github.com/bodeul110/bodeul-admin-web/pull/76)·[#77](https://github.com/bodeul110/bodeul-admin-web/pull/77)은 로그인 전 Firestore 사용자 조회를 제거하고 서버의 PostgreSQL 인가로 통일했다. 최초 개인 ADMIN·SUPER_ADMIN 등록과 TOTP 인증 후 대시보드 접속을 확인했다. 주요 업무 전체, MFA 강제·복구와 App Check 강제의 검증은 별개다. [실행 증거](../reports/dev-production-separation-2026-09-27.md)를 따른다.

아래는 2026-07-17~18 Preview 실연동 검증 기록이며 이번에 재실행한 결과가 아니다. 당시 이후 관리자 세부 역할·업무 함수 계약이 추가됐으므로 현재 운영 검증을 대신하지 않는다.

| 시나리오 | 결과 |
| --- | --- |
| Preview 루트 | 200 |
| Authorization 없음 | 401 `missing_authorization` |
| 비관리자 token | 403 `admin_role_required` |
| 관리자 token | 200, 병원 가이드 조회 |
| 임시 검증 데이터 | 검증 후 Firebase 사용자와 DB row 삭제 확인 |
| DB TLS | Supabase Root CA를 명시하고 인증서 검증 유지 |
| DB 권한 | 당시 runtime 조회 권한과 연결 상한 5 확인. 현재 계약은 제한된 조회와 허용된 업무 함수 실행이며 테이블 직접 쓰기는 금지 |

환경별 DB·자격 증명 준비 상태와 이후 검증은 [관리자 웹 환경 기준](../operations/admin-web-environments.md)을 따른다. 웹 배포 완료와 production 업무 전환 완료를 분리한다.

## 역할

- 매니저 서류 심사와 보완 메모
- 신고·문의·운영 상태 확인
- 병원 가이드와 운영 데이터 조회
- 민감정보 마스킹과 관리자 유휴 세션 종료
- 로그인·2차 인증·관리 화면에 유지되는 개발/운영 배포 환경 표시
- 관리자 권한과 감사 이력 관리
- `SUPER_ADMIN`, `OPERATIONS`, `DEVELOPER` 역할별 메뉴와 API 제한
- 민감정보 원문 접근 사유, 최대 60분 break-glass와 추가 전용 감사

## 선택 이유

- 운영 화면은 데스크톱에서 반복 조회·비교하는 작업에 적합하다.
- same-origin Route Handler를 사용하면 별도 CORS와 서버 간 hop을 만들지 않는다.
- 브라우저에는 DB 자격 증명을 노출하지 않고 서버 환경변수로 제한할 수 있다.
- 사용자 서비스와 관리자 배포·권한 범위를 분리할 수 있다.
- 별도 저장소에서 웹 담당자의 PR, Dependabot, Vercel Preview를 독립 운영할 수 있다.

## 대안

| 대안 | 판단 |
| --- | --- |
| Android 관리자 화면만 사용 | 현장 보조에는 쓸 수 있지만 대량 심사와 비교 작업에는 비효율적이다. |
| Spring Core API가 관리자 요청까지 처리 | 사용자와 관리자 권한·배포 경계가 결합된다. |
| Next.js → Spring → DB | 불필요한 hop과 장애 지점이 생긴다. |
| Firebase Console 직접 운영 | 비개발자 사용성과 실수 방지, 마스킹, 감사 이력을 제공하기 어렵다. |

## 남은 범위

- 운영 DB 접속·최초 관리자 로그인 이후의 역할별 업무·감사·교차 환경 token 거부 검증
- 최종 서비스 도메인과 Firebase Auth authorized domain 대조
- reCAPTCHA Enterprise 기반 App Check와 enforcement 기준 검증
- 현재 차단한 브라우저 ADMIN의 Firestore/Storage 직접 권한을 유지하고 신규 업무도 서버 세부 역할·감사 경유로만 확장
- 추가 개인 운영자 등록, MFA 강제·비상 복구와 긴급 권한 회수 리허설

Vite 빌드는 별도 저장소에 rollback 자산으로 남아 있다. 메인 저장소의 중복 `admin-web/`은 제거했으므로 웹 변경과 배포는 별도 저장소에서만 진행한다.

## 관련 문서

- [목표 인프라 구조](target-infrastructure.md)
- [관리자 웹 환경 기준](../operations/admin-web-environments.md)
- [관리자 웹 저장소 분리 기록](../operations/admin-web-repository-split.md)
- [관리자 웹 데이터 계약](admin-web-data-contract.md)
- [관리자 세부 역할과 감사 계약](admin-rbac.md)
