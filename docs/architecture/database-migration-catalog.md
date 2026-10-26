# PostgreSQL Migration 목록

소스 확인일: 2026-09-29. 실제 환경 적용 증거 확인일: 2026-09-27.

DDL의 원본은 메인 저장소의 [Flyway migration](../../core-api/src/main/resources/db/migration/)이다. 관리자 웹은 같은 계약을 사용하지만 migration을 중복 소유하지 않는다. 아래 버전은 **저장소에 있는 코드**이며 개발·운영 DB에 전부 적용됐다는 뜻이 아니다.

| 버전 | 변경 |
| --- | --- |
| V1 | Firebase UID와 업무 사용자·역할 연결 |
| V2 | 병원 가이드 |
| V3 | 예약 요청 read model |
| V4 | 예약 운영 원본과 쓰기 경계 |
| V5 | 동행 세션·리포트·후속 처리 |
| V6 | Core 동행 쓰기 |
| V7 | Core 예약 후속 처리 쓰기 |
| V8 | 채팅·읽음·위치·첨부 메타데이터 |
| V9 | 읽음 FK 인덱스 |
| V10 | Realtime 변경 Broadcast |
| V11 | 최소 권한 Broadcast 발행 함수 |
| V12 | 동행 실시간 상태 PostgreSQL 이전 |
| V13 | 파기 job·첨부 삭제 claim·집계 |
| V14 | 생성 시점 병원 가이드 snapshot 고정 |
| V15 | 삭제 실행 없는 계정 영향도 집계 |
| V16 | 문진 전 확인 |
| V17 | 성인 환자 보호자 정보공유 동의 |
| V18 | 현장 종료 `CARE_ENDED`와 최종 완료 `COMPLETED` 분리 |
| V19 | 예약 공개 코드 |
| V20 | 관리자 세부 역할·접근 감사 |
| V21 | 가이드 영상 메타데이터 검증 |
| V22 | 무통장입금 상세 원장·이벤트·제한 전이 함수 |
| V23 | 관리자 무통장입금 조회 함수 |
| V24 | 보호자 예약 생성 승인 최신 상태·추가 전용 감사. 소스 추가, 실제 환경 미적용 |
| V25 | 보호자 기준 예약 승인 감사 집계 인덱스. 소스 추가, 실제 환경 미적용 |

## 적용 상태 확인

- 대상 프로젝트와 DB를 먼저 식별하고 `flyway_schema_history`의 성공 버전을 읽기 전용으로 확인한다. 앱 commit, migration 소스 버전과 실제 DB 버전을 별도로 기록한다.
- 9월 27일 양쪽 DB V23·실패 이력 0건을 확인했다. 운영 V16~V23 적용 run `36310121342`, V23 백업·격리 복원 run `36316057495`의 [실행 기록](../reports/dev-production-separation-2026-09-27.md)을 현재 적용 근거로 사용한다. V15 복원·9월 21일 일시정지는 당시 이력이다.
- V24는 [#419 저장 기반](guardian-booking-approval-storage.md) 후속이다. 코드 추가나 CI 격리 DB 검증을 실제 DEV·production 적용으로 기록하지 않는다.
- V25는 [#477 API 연결](guardian-booking-api.md#감사-집계-인덱스)의 보호자 감사 집계를 지원한다. V24 checksum은 유지하며 rollback은 새 인덱스만 제거한다.
- [production 사전 점검](../operations/production-database-migration-readiness.md)은 연결·V14/V15 영향도에 한정된 검사다. 성공만으로 V16~V23 적용·호환성을 보장하지 않는다.
- [bootstrap](../../core-api/db/bootstrap/), Realtime RLS와 [검증 SQL](../../core-api/db/verification/)도 함께 확인한다. Flyway 버전만 맞추고 환경별 인증·권한 설정을 생략하지 않는다.
- 적용은 [Core API 런북](../operations/core-api-infrastructure-runbook.md)의 수동 workflow 경계를 따른다. 전체 DB reset이나 다른 환경에 대한 일괄 push를 하지 않는다.

## 복구와 소유권

[rollback SQL](../../core-api/db/rollback/)은 명시된 사전 조건을 만족할 때만 사용한다. 모든 migration의 무손실 자동 역전이 가능한 것은 아니다. production 변경 전에 복원 가능한 백업, 쓰기 중단 범위와 호환 가능한 앱 버전을 확보한다. V18 이후 종료 데이터나 V22 결제 이벤트가 있는 경우의 fail-closed 조건은 각 SQL과 도메인 계약을 우선한다.

이 목록은 버전을 중복 정의하려는 문서가 아니라 기존의 V15 중심 설명에서 현행 계약으로 이동하기 위한 색인이다. 실제 적용·삭제·복구는 이번 문서 갱신 범위에 포함하지 않는다.
