# PostgreSQL 운영 전환 런북

기준일: 2026-09-27

## 목적

예약·매칭·동행·채팅·읽음·위치·리포트·후속 처리의 운영 원본을 Supabase PostgreSQL로 전환할 때 필요한 리소스, 검증 순서와 rollback 기준을 고정한다. 인증·푸시·파일과 Firebase에 남기기로 한 데이터는 전환 대상에서 제외한다.

현재 구조와 데이터 경계는 [목표 인프라 구조](../architecture/target-infrastructure.md), 운영 일정과 Go/No-Go는 [2026년 Production 운영 전환 계획](production-transition-plan-2026.md)을 우선한다.

## 현재 리소스 상태

| 범위 | 개발 | production | 남은 작업 |
| --- | --- | --- | --- |
| Supabase PostgreSQL | Pro 조직, Healthy, V23·실패 0건 | Pro 조직, Healthy, V23·실패 0건, 외부 dump·격리 복원 완료 | 제공자 자동 백업 복구 지점과 실제 서비스 데이터 검증 |
| Spring Core API | `dev` 기반 Cloud Run preview, health 200·무인증 401 | 운영 revision 배포, health 200·무인증 401, Kakao Secret 등록 | 정상 운영 토큰·환경 교차 거부·Kakao 검색·rollback |
| 관리자 Next.js | `dev` Preview, 개발 DB·Firebase 경계 | `master` Production, 전용 DB 연결, 최초 개인 `SUPER_ADMIN` MFA 로그인·대시보드 확인 | 업무 smoke·MFA 강제/복구·세부 역할별 거부·rollback |
| Firebase | `bodeul-dev` Auth·FCM·App Check·Storage | `bodeul-prod-110` 분리와 결제·기본 리소스 준비 | release App Check와 운영 키·도메인 검증 |
| 보관·파기 | V13, Core 첨부와 Firestore 전환 문서·매니저 증빙 fixture APPLY·cleanup, 최종 dry-run 검증 | migration·역할·복원과 읽기 전용 fixture 상태 검증 | 후속 기획 답변을 반영한 보관 정책·실제 job·고지 대조와 최신 schema 격리 fixture 검증 |

최신 환경별 증거는 [9월 27일 개발·운영 분리 기록](../reports/dev-production-separation-2026-09-27.md)을 따른다. [Production 인프라 구축 기록](../reports/production-infrastructure-bootstrap-2026-07-17.md), [초기 PostgreSQL 복원 리허설](../reports/postgres-production-backup-restore-rehearsal-2026-07-18.md), [개인정보 자동 파기 구현 기록](../reports/issue-222-data-retention-2026-07-19.md)은 해당 시점의 이력이다.

## 데이터와 요청 경계

| 범위 | 원본 | 허용 경로 |
| --- | --- | --- |
| 예약·동행·채팅·읽음·위치·리포트·후속 처리 | Supabase PostgreSQL `bodeul` schema | Spring Core API |
| 매니저 배정 | Supabase PostgreSQL `bodeul` schema | Next.js 관리자 서버의 admin-only 함수 |
| 인증 프로필·지원·매니저 서류 심사 메타데이터 | Cloud Firestore | Firebase 결합 저장소와 Rules |
| 세션 채팅 첨부·매니저 증빙 원본 | Firebase Storage | Core API 중계 또는 매니저 서류 전용 Firebase 경로 |
| 인증·요청 출처 | Firebase Auth·App Check | 서버에서 token 검증 |
| 실시간 화면 갱신 | Supabase Realtime private Broadcast | PostgreSQL 커밋 알림 뒤 서버 API 재조회 |
| 백그라운드 알림 | Firebase FCM | 서버 또는 Firebase 결합 Functions |

- Android와 브라우저는 PostgreSQL 접속 문자열이나 service role을 가지지 않는다.
- Core API는 `bodeul_core_service`, 관리자 서버는 `bodeul_admin_service`처럼 분리된 최소 권한 runtime role을 사용한다.
- DDL은 메인 저장소 `core-api/`의 Flyway migration만 소유한다.
- Supabase Data API를 Core 업무의 조회·쓰기 경로로 사용하지 않는다.
- 한 도메인의 쓰기 원본을 Firestore와 PostgreSQL에 동시에 두지 않는다.

## production 전환 순서

9월 27일 운영 DB 재개·V23·격리 복원, 서버 배포와 최초 관리자 로그인까지 완료했다. 아래 순서는 재배포·서비스 전환 시에도 유지할 점검 절차이며, 완료된 기반을 다시 생성하거나 자동으로 운영 데이터를 변경하라는 지시가 아니다.

1. 완료된 Supabase Pro·V23·외부 dump 구성을 기준으로 spend cap과 제공자 일일 백업 복구 지점을 확인한다.
2. production Firebase만 신뢰하도록 설정한 Third-Party Auth와 Realtime RLS를 실제 서명 token·socket으로 검증한다. SQL 권한 경계 검증은 완료했다.
3. 운영 Secret과 Cloud Run revision을 확인한다. Kakao REST 키는 승인된 개발·운영 공용 키이며, 별도 Secret 항목을 사용해도 쿼터·폐기 영향은 공유한다.
4. Core API의 health, Firebase 인증, DB 401·403·200, Kakao 검색과 attachment smoke를 실행한다.
5. Vercel Production의 Firebase·App Check 값과 관리자 최소권한 DB 연결을 각각 확인한다. 일반 테이블 직접 쓰기는 금지하고 허용된 업무 함수만 실행한다.
6. 관리자 서버의 인증, 역할 거부, 조회와 감사 이력을 격리 운영 데이터로 검증한다.
7. release Android로 예약, 채팅, 위치, 첨부, FCM과 재연결 흐름을 실기기에서 확인한다.
8. Cloud Run revision과 Vercel deployment rollback을 실제로 재현한다.
9. 완료된 개발 Firestore 전환 문서와 매니저 증빙 fixture APPLY·cleanup 증적을 재확인한다.
10. 자동 파기 production dry-run을 확인한 뒤 정책·약관 승인 시에만 apply를 활성화한다.
11. Go/No-Go 승인 뒤 migration과 애플리케이션 배포를 수행하고 30일 안정화 기간을 시작한다.

## 도메인별 전환 게이트

각 도메인은 다음 조건을 모두 만족해야 PostgreSQL을 production 쓰기 원본으로 사용한다.

1. Flyway migration과 적용 전 백업이 있다.
2. 개발 DB에서 backfill row 수, 필수 필드, FK와 핵심 API 응답이 일치한다.
3. Firebase ID token과 PostgreSQL role의 정상·401·403 테스트가 있다.
4. Android 또는 관리자 웹이 서버 경로만 사용하고 legacy Firestore 쓰기가 거부된다.
5. 보관 기간, 자동 파기와 legal hold가 같은 도메인 계약에 포함된다.
6. 실패 시 복원할 PostgreSQL 백업과 호환 가능한 직전 애플리케이션 revision이 있다.
7. 운영 smoke 담당자와 rollback 승인자가 정해져 있다.

## Rollback 기준

다음 상황에서는 신규 쓰기를 중지하고 직전 애플리케이션과 PostgreSQL 상태를 복구한다.

- migration row 수, FK 또는 필수 필드가 승인된 결과와 다르다.
- 예약·세션 상태 전이가 누락되거나 중복된다.
- Firebase token·role 검증 오류가 기준치를 넘는다.
- Core API 또는 관리자 서버 장애가 15분 이상 지속된다.
- 채팅·위치 커밋은 성공했지만 Realtime 재조회로 상태를 회복할 수 없다.

Rollback 순서:

1. 배포와 신규 쓰기를 중지한다.
2. Cloud Run revision 또는 Vercel deployment를 직전 호환 버전으로 돌린다.
3. migration 전 백업 또는 검증된 역방향 보정으로 PostgreSQL을 복구한다.
4. 핵심 API와 데이터 정합성 smoke를 다시 실행한다.
5. 원인, 손실 가능 시간과 복구 결과를 보고서로 남긴다.

전환된 도메인을 Firestore 이중 쓰기나 클라이언트 토글로 되돌리지 않는다. 안정화 기간의 Firestore 읽기 전용 자료는 비교에만 사용한다.

## 완료 조건

- production의 Core 업무 도메인은 PostgreSQL만 쓰기 원본으로 사용한다.
- 관리자 웹과 Android는 각각 Next.js 관리자 서버와 Spring Core API를 거친다.
- runtime role에 DDL·과도한 schema 권한·공개 role 권한이 없다.
- PostgreSQL backup/restore와 Cloud Run·Vercel rollback 기록이 있다.
- release App Check, Realtime RLS, Storage 접근과 실기기 핵심 흐름이 통과한다.
- 비용 한도, 보관 정책, 실명 운영자와 장애 연락 경로가 승인됐다.

## 관련 문서

- [PostgreSQL 운영 전환 결정](../architecture/postgres-operational-transition.md)
- [PostgreSQL API 경계](../architecture/postgres-api-boundary.md)
- [Spring Core API 인프라 런북](core-api-infrastructure-runbook.md)
- [Production 인프라 기본값](production-infrastructure-defaults.md)
- [데이터 보관 및 파기 정책](data-retention-policy.md)
