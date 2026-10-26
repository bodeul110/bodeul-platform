# 개발·운영 환경 전환 계획

최초 계획: 2026-07-18. 현재 코드·문서 대조: 2026-09-27

## 목표와 일정

관리자 웹은 Next.js 관리자 서버를, 사용자·매니저 앱과 향후 사용자 웹은 Spring Core API를 거쳐 **환경별 공용 PostgreSQL**을 사용한다. 개발과 운영은 Firebase 프로젝트·DB·서버·비밀값을 분리하며, 한 환경 안에서 관리자 서버와 Core 서버가 같은 DB를 사용한다.

초기 목표는 2026년 말 운영 전환이었다. 이전 문서의 `2026-12-15` 전환일과 `2026-11-16` 유료 전환일은 임시 일정이며 확정된 실행일이 아니다. 아래 게이트를 통과한 뒤 실제 일정을 정한다. 임시 날짜만으로 결제 변경·데이터 이관·운영 배포를 실행하지 않는다.

## 현재 확인 범위

| 구분 | 코드·기록 기준 상태 | 남은 확인 |
| --- | --- | --- |
| 저장소 | Android/Core/Firebase 공용 계약은 메인, 관리자 웹·서버는 별도 저장소 | 변경되는 공용 계약을 두 저장소에서 함께 검증 |
| 브랜치·배포 | 두 저장소 `dev`→개발, `master`→운영. 기능 PR squash, 운영 승격 merge commit. Core Preview는 dev push/수동, Production은 수동 | 정상 운영 인증·업무 흐름과 rollback 검증 |
| 데이터 전환 | 소스와 개발·운영 DB 모두 V23, 실패 이력 0건. Core 업무는 PostgreSQL, legacy 직접 쓰기 차단 | 환경별 실제 업무 데이터 정합성·권한 검증 |
| 관리자 웹 | 전용 운영 DB 연결, 최초 개인 `SUPER_ADMIN`, TOTP 로그인·대시보드 진입 확인 | 업무별 동작·역할 거부, 전역 MFA 강제·복구는 별도 |
| 운영 DB | Pro 조직에서 Healthy, V23 적용, 최소 권한·외부 dump·격리 복원 확인 | 제공자 자동 백업 복구 지점, Realtime 실제 token·socket 검증 |
| 개발 API | [#429](https://github.com/bodeul110/bodeul-platform/issues/429)는 9월 24일 종료. 9월 27일 health 200·무인증 API 401 재확인 | 기본 smoke를 모든 앱 흐름 성공으로 보지 않음 |
| 보관·복원 | 9월 27일 운영 V23 dump의 외부 보관·격리 복원 통과 | 파일 복원, 실제 데이터 규모의 RTO, 보존 job 확인 |

최신 배포·계정 구분은 [관리자 웹 환경](admin-web-environments.md)과 [9월 27일 개발·운영 분리 기록](../reports/dev-production-separation-2026-09-27.md)에 둔다. 실행 절차는 [브랜치 전환 계획](dev-production-branch-transition-plan.md)을 따른다.

## 데이터 경계

| 범위 | 원본 | 접근 경로 |
| --- | --- | --- |
| 예약·동행·채팅·읽음·리포트·후속 처리·결제 상태 | Supabase PostgreSQL `bodeul` | Spring Core API, 관리자 업무는 Next.js 서버의 제한된 DB 함수 |
| 위치 | 정책 목표는 환자 1분 주기·동의한 보호자 조회 | 기존 매니저 GPS 경로는 기본 OFF. 환자 중심 흐름과 운영 검증은 별도 진행 |
| 인증 프로필·지원·서류 심사 메타데이터 | Firestore | 본인 경로는 Rules, 관리자 업무는 서버 인가·감사 경유 |
| 사용자 인증·푸시 | Firebase Auth / FCM | 각 서버의 ID token 검증과 서버 발송 |
| 채팅 첨부·매니저 증빙 원본 | Firebase Storage | Core 첨부 중계 또는 제한된 본인 업로드·관리자 서버 원문 조회 |
| 실시간 알림 | PostgreSQL 커밋 후 private Realtime Broadcast | Firebase Third-Party Auth와 채널 RLS. 클라이언트 DB 쓰기 금지 |
| 전환된 업무의 Firestore 문서 | 과거 읽기 전용 비교 자료 | 신규 업무 쓰기 금지. 보존·복구 필요를 확인한 뒤 별도 정리 |

Realtime의 `role: authenticated` claim은 관리자 역할이 아니다. App Check도 인증·업무 인가를 대체하지 않는다. [목표 인프라](../architecture/target-infrastructure.md), [관리자 RBAC](../architecture/admin-rbac.md), [보관 정책](data-retention-policy.md)을 함께 적용한다.

## 단계별 실행 게이트

| 순서 | 작업 | 종료 조건 |
| --- | --- | --- |
| 1 | 접근·환경·비용 확인 | 공용 관리 주체와 개인 개발자 최소권한 확인, 개발/운영별 실제 결제 연결·DB 상태·서버 접근 확인 |
| 2 | 개발 환경 안정화 | 종료된 #429 복구 증거 유지, 이후 변경의 migration과 역할별 API·앱·관리자 회귀 검증 |
| 3 | 개발·운영 배포 전략 정착 | 브랜치와 GitHub Environment, WIF, Vercel target, 비밀값·DB가 서로 뒤섞이지 않음 |
| 4 | 운영 DB 준비 | V23·역할·격리 복원은 완료. 이후 migration도 사전 백업과 개발 검증 뒤 별도 적용 |
| 5 | 운영 서버·앱 준비 | 운영 비밀값, 관리자 MFA·세부 역할, Auth 도메인·Kakao·App Check release 검증, 실제 업무 smoke |
| 6 | Go/No-Go와 전환 | 운영자·복구 담당자 확인, 차단 항목 해소, 실제 전환일 결정 후 명시적 실행 |
| 7 | 안정화·legacy 정리 | 오류·비용·정합성 점검 후 비교 자료의 필요성과 보존 예외를 확인하고 별도 삭제 승인 |

소스 변경, DB migration, 앱 배포를 한 작업으로 묶지 않는다. Core 운영 배포·migration·백업 복원은 각 `workflow_dispatch`와 `master`의 실제 commit SHA 확인 경계를 유지한다.

## 비용 기준

기존 승인 기준은 월 **150,000 KRW 이내**, 정상 계획 범위는 **100,000~130,000 KRW**다. 이는 청구액 보증이나 플랫폼의 자동 지출 차단 설정이 아니다. 현재 가입 등급·결제 연결은 별도로 조회한다.

Supabase Pro/Micro 2개와 Vercel Pro 개발자 2석의 계획 비용, 사용량·환율·세금 가정은 [비용 모니터링](cost-monitoring.md) 한 곳에서 관리한다. Supabase Pro는 이미 전환했으며, Vercel 플랜·좌석은 별도 확인 대상이다. 추가 유료 변경은 임시 달력 날짜에 맞춰 자동 활성화하지 않는다.

## 운영 전환 조건

- 개발·운영 Firebase, Supabase, 서버와 비밀값이 분리되고, 각 관리자 서버와 Core 서버만 해당 DB에 접근한다.
- 최신 DB 백업·격리 복원이 통과하고 운영용 백업 등급과 보존 기간이 확인된다.
- Cloud Run revision과 Vercel deployment의 복구를 검증한다.
- 예약·배정·동행·채팅·관리자 심사·결제 상태를 운영 격리 데이터로 확인한다. 실제 송금·계좌·환불은 별도 승인 범위다.
- 환자 위치 기능은 동의·종료·파기·권한 검증 전까지 활성화하지 않는다.
- release Android의 Auth·App Check·Kakao 설정과 관리자 MFA·세부 역할을 검증한다.
- 운영 Supabase는 운영 Firebase만 신뢰하며 다른 프로젝트 token과 비참여자 Realtime 구독을 거부한다.
- 공개 DB role 권한, Storage 원문 접근, RLS와 감사 경계를 확인한다.
- 운영·복구 담당자와 장애 연락 경로, 실제 수집·보관에 맞는 고지·동의를 준비한다.

## 복구 원칙

배포 오류는 직전 정상 배포로 되돌린다. DB 변경은 호환 애플리케이션 복구와 데이터 보정을 분리하고, 파괴적 복구는 승인된 정비 시간에만 수행한다. PostgreSQL 전환 뒤 Firestore 이중 쓰기로 복구하지 않는다.

Firestore 비교 자료는 고정된 임시 날짜에 일괄 삭제하지 않는다. 도메인별 안정화 결과, 백업과 보존 예외를 확인한 뒤 [보관 정책](data-retention-policy.md)에 따라 정리한다.
