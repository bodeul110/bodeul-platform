# 인프라 리스크와 보완 계획

기준일: 2026-09-27

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 결론

현재 가장 큰 리스크는 개발에서 검증한 PostgreSQL Core 업무 원본 경계와 자동 파기를 production에서 그대로 재현하는 것이다. 목표 구조는 Core 업무 도메인의 Supabase PostgreSQL 단일 원본, Spring Core API와 Next.js 관리자 서버의 분리된 접근, Firebase Auth·FCM·App Check·Storage와 일부 Firestore 결합 데이터 유지다.

Core 업무는 PostgreSQL 단일 쓰기이며 매칭 배정은 관리자 서버의 제한 함수가 담당한다. 9월 27일 양쪽 DB V23·운영 백업과 격리 복원·운영 Core 배포·관리자 DB 연결·최초 MFA 로그인을 확인했다. 남은 위험은 정상 업무·교차 환경 token·운영 Realtime 소켓·MFA/App Check 강제·복구 검증이다. [실행 기록](../reports/dev-production-separation-2026-09-27.md)의 SQL·HTTP·사용자 확인 범위를 구분한다. 기존 매니저 위치는 기본 OFF이고 환자 중심 위치 정책의 구현·실검증은 별도다.

## 리스크 요약

| 리스크 | 현재 상태 | 보완 계획 |
| --- | --- | --- |
| 이중 데이터 원본 | 개발 Core 업무 쓰기는 PostgreSQL로 단일화했고 해당 Firestore 문서는 rollback 비교 자료다. production은 아직 사용자 트래픽을 받지 않는다. | production 전환 뒤 최대 30일 비교 기간을 두고 관련 Firestore Core 업무 경로를 제거한다. |
| 권한 기준 분기 | Core는 PostgreSQL 역할·참여 관계, 관리자는 ADMIN과 활성 세부 역할을 확인한다. 브라우저 ADMIN의 Firebase 직접 접근은 차단한다. | Firebase UID를 연결 키로 쓰되 인증과 업무 인가를 분리하고 서버·Rules 경계를 각각 검증한다. |
| 개발 API 가용성 | #429에 Preview 500/503 재확인 항목이 남아 있다. 최근 앱 PR 병합은 서버 복구 증명이 아니다. | health와 인증된 예약·세션 요청, DB 연결을 재검증한다. |
| 실시간 운영 | 채팅·읽음·상태 이벤트는 PostgreSQL과 private Broadcast 계약을 사용한다. legacy 위치는 기본 OFF다. | 재연결·동의 회수·종료 후 접근과 환자 위치 흐름을 따로 검증한다. |
| DB 연결 고갈 | Cloud Run과 Vercel이 같은 DB를 사용한다. | Core pool은 Preview 5·Production 2이며 인스턴스 수와 Admin·retention·migration 연결을 합산해 한도를 검증한다. |
| App Check | 개발 Android valid 검증과 관리자 웹 Production client·서버 `observe` 배포를 완료했다. release Play Integrity와 인증된 Web `VALID`·enforcement는 남았다. | release Play Integrity와 Android·Web 실제 요청을 확인한 뒤 서비스별 observe에서 enforce로 전환한다. |
| 민감 데이터 보관 | 과거 fixture APPLY·cleanup 기록이 있다. 현재 적용 기준은 후속 기획 답변을 반영한 보관 정책이며 모든 정책 충돌을 미결정으로 취급하지 않는다. | 현재 정책과 실제 파기 job·고지를 대조하고 최신 운영 schema에서 격리 fixture를 검증한다. |
| 복구 | production PostgreSQL 격리 복원은 완료했다. | Cloud Run·Vercel rollback을 리허설하고 분기별 DB 복원을 반복한다. |
| 비용 | 과거 budget·플랜 기록과 현재 청구 상태는 다르다. 이번 작업에서는 결제를 재조회하지 않았다. | 월 150,000 KRW 계획 한도 안에서 실제 운영 전 플랜·백업·사용량·결제 연결을 확인한다. |
| 외부 API | Kakao Local은 Core API로 이동했지만 production key가 없다. | Secret Manager version을 추가하고 429, timeout과 fallback을 production 후보에서 검증한다. |
| 운영자 의존 | 실명 운영자와 rollback 승인자가 확정되지 않았다. | 출시 전 최소 2명과 장애 연락·승인 경로를 지정한다. |

## Firestore Core 업무 경계 종료 리스크

개발 Core 업무 쓰기는 이미 Firestore에서 제거했지만 인증 프로필, 지원, 매니저 서류와 Storage 인가처럼 아직 Firebase에 남긴 기능이 있어 Firestore 전체를 즉시 제거할 수는 없다. Core 업무 원본과 Firebase 결합 데이터를 섞지 않도록 다음 기준을 적용한다.

- 개발에서는 PostgreSQL이 Core 업무 데이터를 쓰고 Firestore의 해당 업무 쓰기는 차단한다.
- production cutover 전에는 migration과 역할별 종단 검증을 반복하고 사용자 트래픽을 연결하지 않는다.
- 전환 대상 Core 업무 Firestore 문서는 최대 30일 동안 읽기 전용 비교와 rollback 판단 자료로만 유지한다.
- rollback은 Firestore 이중 쓰기가 아니라 PostgreSQL 백업 복원 또는 검증된 보정 스크립트를 사용한다.
- rollback 기간이 끝나면 전환된 Core 업무에만 관련된 Rules, Functions, index와 운영 스크립트를 제거한다. 인증 프로필·지원·매니저 서류 경계는 유지한다.

## Realtime 리스크

Realtime 이벤트는 누락, 중복 또는 순서 변경이 발생할 수 있다. 이벤트 자체를 데이터 원본으로 사용하지 않고 PostgreSQL 커밋 결과를 알려주는 신호로만 사용한다.

- Android와 웹은 재연결 후 서버 API에서 최신 상태를 다시 읽는다.
- private Broadcast 채널 이름에는 사용자 개인정보를 넣지 않는다.
- Supabase Third-Party Auth는 개발·production Firebase 프로젝트를 분리해 등록하고, Firebase ID token의 `role: authenticated` custom claim과 발급 프로젝트를 검증한다.
- `realtime.messages` RLS는 채널 주제뿐 아니라 예약·동행 참여 관계를 확인하며, 기존 사용자 claim 백필 후 token을 강제로 갱신한다.
- 클라이언트는 영속 쓰기를 Realtime이나 Supabase Data API로 보내지 않는다.
- 채팅은 메시지 ID로 중복을 제거하고 위치 이벤트는 최신 시각보다 오래된 값을 무시한다.
- 백그라운드 알림은 FCM이 담당하며 FCM payload에도 민감 본문을 넣지 않는다.

## 민감 데이터와 파기

위치 좌표, 채팅 첨부와 매니저 증빙은 일반 운영 데이터보다 짧게 보관한다. 개발 파기 job은 dry-run과 apply를 분리했고 Core 중첩 첨부, Firestore 전환 문서와 매니저 증빙의 실제 개발 fixture APPLY·cleanup을 완료했다. 감사 로그에는 원문 대신 비식별 집계와 처리 결과만 남긴다.

법정 보존 또는 분쟁 대응 예외는 일반 테이블 조회에서 격리하고 근거, 승인자와 만료일을 기록한다. 상세 기간은 [데이터 보관 및 파기 정책](../operations/data-retention-policy.md)을 따른다.

## 백업과 비용

- production PostgreSQL은 2026-08-26 Flyway V15 적용 전후 dump를 PostgreSQL 17에 격리 복원해 owner, ACL, row 수, RLS, 정책, 인덱스, 제약과 Flyway 이력까지 검증했다.
- 실제 사용자 데이터 전에는 Supabase Pro의 일일 7일 백업을 활성화한다.
- 외부 logical dump는 GCS에 4주 순환 보관하고 분기마다 복원한다.
- Supabase spend cap을 유지하고 초기에는 PITR과 Log Drain을 구매하지 않는다.
- 기존 GCP 알림용 budget은 자동 지출 차단을 보장하지 않으므로 최대 인스턴스, API quota와 Storage 업로드 제한을 같이 유지한다.

## 출시 차단 조건

- 예약·매칭·동행·채팅·위치 중 하나라도 source of truth가 불명확하다.
- release App Check 또는 역할별 401·403·정상 응답이 검증되지 않았다.
- Cloud Run, Vercel rollback 또는 PostgreSQL restore 증적이 없다.
- 개인정보 처리방침과 실제 보관·파기 job이 다르다.
- 운영자 2명, Kakao production key 또는 production secret이 준비되지 않았다.
- 월 비용이 150,000 KRW를 넘을 것으로 예상되는데 별도 승인이 없다.

## 관련 문서

- [목표 인프라 구조](target-infrastructure.md)
- [PostgreSQL 운영 전환 결정](postgres-operational-transition.md)
- [2026년 Production 운영 전환 계획](../operations/production-transition-plan-2026.md)
- [Production 인프라 기본값](../operations/production-infrastructure-defaults.md)
- [비용과 쿼터 모니터링](../operations/cost-monitoring.md)
- [데이터 보관 및 파기 정책](../operations/data-retention-policy.md)
- [2026-08-25 정책·법률 정합성 점검](../reports/notion-policy-legal-alignment-2026-08-25.md)
- [Production PostgreSQL 복원 리허설](../reports/postgres-production-backup-restore-rehearsal-2026-07-18.md)
