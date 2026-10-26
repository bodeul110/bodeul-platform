# 비용과 쿼터 모니터링

요금표 기준: 2026-09-21. 환경 상태 반영: 2026-09-27 (실제 청구·budget 수신자 재조회 아님)

이 문서는 개발·production 인프라의 비용과 쿼터 점검 기준이다. 기존 BoDeul 기록의 budget은 알림용(Alerts-only)이며 자동 지출 차단을 확인한 설정이 아니다. Google Cloud의 별도 Spend cap budget과 혼동하지 않는다. 알림 수신자는 원인을 확인하고 대응해야 한다. [Google Cloud budget 문서](https://docs.cloud.google.com/billing/docs/how-to/budgets)

## Google Cloud budget

아래는 [2026-07-16 설정 기록](../reports/issue-65-cost-monitoring-2026-07-16.md)의 기준값이다. 이후 결제 계정 변경이 있었으므로 현재 연결·budget·수신자는 아래 명령으로 다시 확인한다.

| 환경 | 대상 프로젝트 | budget 이름 | 월 금액 | 알림 |
| --- | --- | --- | ---: | --- |
| 개발 | `bodeul-dev` | `BoDeul dev monthly budget` | 10,000 KRW | 현재 지출 50%, 80%, 100% |
| production | `bodeul-prod-110` | `BoDeul production monthly budget` | 30,000 KRW | 현재 지출 50%, 80%, 100% |

당시 설정의 수신자는 결제 계정의 Billing Account Administrator와 Billing Account User다. 현재 수신 권한을 이번 문서 수정에서 재검증하지 않았다.

현재 규모에서는 낮은 개발·production 예산으로 오설정, 무한 호출, scheduled job 반복을 조기에 발견하는 것이 목적이다. 정상 검증이 예산을 반복해서 넘기면 금액을 바로 높이지 않고 환경별 서비스 사용량과 무료 할당량을 먼저 확인한다. budget은 지출 상한이 아니라 알림 기준이다.

## 전체 운영 비용 승인

| 항목 | 운영 기준 | 월 예상 |
| --- | --- | ---: |
| Supabase | Pro, Tokyo Micro 2개, spend cap 사용 | USD 35 |
| Vercel | Pro 개발자 좌석 2개 | USD 40 |
| Google Cloud/Firebase | production budget 30,000 KRW | 사용량 기준 |
| 승인 한도 | 세금·환율·소규모 초과 사용 포함 | 150,000 KRW |

정상 운영 계획 범위는 월 100,000~130,000 KRW다. 150,000 KRW는 환율과 일시적 사용량을 감안한 승인 상한이며 자동 차단 장치가 아니다. Supabase Pro 전환과 개발·운영 DB 가동은 9월 27일 확인했다. Vercel 플랜·좌석과 실제 청구액은 별도 확인한다. 이전의 2026-11-16은 임시 일정이며 결제 실행일이 아니다. 도메인은 연 50,000 KRW 이내를 계획값으로 두며 신규 구매 완료를 의미하지 않는다.

2026-09-21 공개 요금표 기준 Supabase Pro USD 25에 compute credit USD 10을 반영하고 Micro USD 10을 2개 사용하면 약 USD 35/월이다. Vercel Pro 개발자 2석은 약 USD 40/월이다. 추가 사용량·옵션·세금·환율은 별도이며 현재 계정의 실제 청구액이 아니다. [Supabase 요금](https://supabase.com/pricing), [Vercel 요금](https://vercel.com/pricing)

확인 명령은 결제 계정 ID를 로컬에서 조회한 뒤 실행한다. 실제 ID는 문서나 공개 이슈에 남기지 않는다.

```powershell
gcloud billing projects describe bodeul-dev
gcloud billing projects describe bodeul-prod-110
gcloud billing budgets list `
  --billing-account=<BILLING_ACCOUNT_ID> `
  --filter='displayName:("BoDeul dev monthly budget" OR "BoDeul production monthly budget")'
```

## 점검 metric

Cloud Monitoring에서 다음 metric을 기준으로 본다.

| 영역 | metric | 확인 내용 |
| --- | --- | --- |
| Firestore | `firestore.googleapis.com/document/read_count` | 전체 조회, 실시간 listener 증가 |
| Firestore | `firestore.googleapis.com/document/write_count` | 프로필·지원·심사 메타데이터·알림 job 증가. Core 업무 쓰기가 되살아나지 않는지 확인 |
| Firestore | `firestore.googleapis.com/document/delete_count` | 예상하지 않은 정리 작업 |
| Firestore | `firestore.googleapis.com/storage/data_and_index_storage_bytes` | 데이터와 index 저장량 증가 |
| Cloud Run | `run.googleapis.com/request_count` | Core API 요청 급증과 반복 호출 |
| Cloud Run | `run.googleapis.com/container/instance_count` | 개발 최대 1, production 최대 2 기준 이탈 여부 |
| Cloud Functions | `cloudfunctions.googleapis.com/function/execution_count` | scheduled/callable/trigger 반복 실행 |
| Cloud Storage | `storage.googleapis.com/api/request_count` | 업로드와 관리자 미리보기 반복 |
| Cloud Storage | `storage.googleapis.com/storage/total_bytes` | 매니저 서류와 첨부 파일 누적 |

PostgreSQL 연결 수·DB 크기·느린 쿼리, Realtime 연결/메시지, Vercel 함수·전송량은 각 서비스 대시보드에서 따로 확인한다. 채팅·세션·결제 원본 비용을 Firestore 지표만으로 계산하지 않는다.

현재 배포 workflow의 DB pool은 Preview 인스턴스당 5, Production 인스턴스당 2다. Core 외 관리자·보존 worker·migration 연결까지 합산해 DB 한도를 판단한다.

## 점검 주기

| 시점 | 확인 항목 | 기록 위치 |
| --- | --- | --- |
| 매주 월요일 | 최근 7일 Firestore, Functions, Cloud Run, Storage 추세 | 이상이 있을 때 GitHub Issue |
| 배포·부하 검증 직후 | Cloud Run 요청/5xx, Functions 실행 증가, Kakao Local 쿼터 | 관련 PR 또는 검증 보고서 |
| 매월 첫 영업일 | 최근 30일 metric, budget 도달률, 저장량 | `docs/reports/` 월간 기록 |
| 50% budget 알림 | 서비스별 일간 증가와 반복 job 확인 | 운영 이슈 |
| 80% budget 알림 | 비필수 부하 검증 중단, listener/job/API 호출 점검 | 운영 이슈와 담당자 공유 |
| 100% budget 알림 | 추가 배포·부하 검증 보류, 원인과 다음 한도 결정 | 저장소 소유자 판단 기록 |

## Kakao Local

- 개발·운영이 승인된 같은 REST 키를 사용하므로 쿼터와 키 폐기 영향은 공유한다. Secret Manager 항목 분리를 쿼터 분리로 해석하지 않는다.
- Kakao Developers에서 Local 키워드 검색의 당일 사용량과 제공량을 확인한다.
- Core API 배포 또는 장소 검색 부하 검증 직후에는 쿼터와 429 오류를 함께 확인한다.
- `kakao_local_quota_exceeded`와 Core API 자체 `place_search_rate_limit_exceeded`를 구분한다.
- 일일 제공량이나 정책은 바뀔 수 있으므로 production 전 다시 확인한다.

## 대응 원칙

- budget 알림만으로 Cloud Run, Functions, Firestore를 자동 중단하지 않는다.
- Cloud Run 개발 서비스는 최소 인스턴스 0, 최대 인스턴스 1을 유지한다.
- scheduled Functions 급증 시 중복 배포, 재시도 조건, 대상 문서 상태를 먼저 확인한다.
- Firestore read 급증 시 관리자 전체 조회와 실시간 listener 해제 여부를 우선 확인한다.
- Storage 증가 시 10MB 업로드 제한, 고아 파일, 보존 기간을 확인한다.
- 알림 금액 변경에는 최근 2개월 사용량과 다음 테스트 계획을 함께 기록한다.
- Supabase spend cap을 해제하거나 PITR, Log Drain, custom domain을 추가할 때는 별도 비용 판단을 남긴다.

## 관련 기록

- [Issue 65 비용 모니터링 설정 기록](../reports/issue-65-cost-monitoring-2026-07-16.md)
- [인프라 운영 기준](infrastructure-operations-baseline.md)
- [Kakao Local Core API 경계](../architecture/kakao-local-core-api.md)
- [2026년 Production 운영 전환 계획](production-transition-plan-2026.md)
