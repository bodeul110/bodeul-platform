# Production DB migration 사전 점검

기준 범위 확인: 2026-09-21. 현재 소스 migration은 V23까지지만 이 readiness 도구의 도메인별 영향 집계는 V14·V15 가이드 전환 중심이다. 통과했다고 V16~V23 업무 계약·권한·데이터 이관 전체가 검증된 것은 아니다. [Migration 목록](../architecture/database-migration-catalog.md)과 각 버전 검증을 함께 적용한다.

실제 적용 상태는 2026-09-27 개발·운영 모두 V23·실패 이력 0건이다. 운영 V23 백업·격리 복원도 통과했다. [환경 분리 실행 기록](../reports/dev-production-separation-2026-09-27.md)을 현재 상태로 보고 아래 8월 실행 기록과 구분한다.

## 작업 목적

Production PostgreSQL migration 전후에 연결 대상, 현재 schema 버전, 실패 이력과 영향 건수를 쓰기 없이 확인한다.

## 선택한 방식

- `Core API Production DB Migration Readiness`, `Core API DB Migration`의 production 실행과 `Production PostgreSQL Backup and Restore Rehearsal`이 같은 연결 대상 검증 계약을 사용한다.
- `core-api-migration-production` Environment의 migration 자격 증명과 `PRODUCTION_SUPABASE_PROJECT_REF` 변수를 사용한다.
- 실행자는 `bodeul-prod-110`, Supabase project ref와 현재 `master` commit SHA 40자를 다시 입력한다.
- Java 실행기는 네트워크 연결 전에 PGJDBC `Driver.parseURL`이 최종 해석한 host와 username이 Environment의 Supabase project ref를 가리키는지 확인한다.
- migration과 backup workflow는 Flyway 또는 dump보다 먼저 `verifyDatabaseConnectionTarget`을 실행한다. 이 `--target-only` 경로는 DB 비밀번호를 읽지 않고 DB 네트워크에도 접속하지 않는다.
- readiness workflow만 읽기 전용 DB 집계를 수행한다. backup workflow는 손상되거나 불완전한 schema도 보존할 수 있도록 전체 readiness query를 실행하지 않는다.
- direct 연결은 `db.<project-ref>.supabase.co`만 허용한다. pooler 연결은 `*.pooler.supabase.com` host와 `.<project-ref>` username suffix가 함께 일치해야 한다.
- migration login은 direct에서 정확히 `bodeul_migrator`, pooler에서 정확히 `bodeul_migrator.<project-ref>`만 허용한다.
- 최종 database는 `postgres`, direct port는 `5432`, pooler port는 `5432` 또는 `6543`만 허용한다. URL query가 별도 username을 바꾸거나 password를 공급하면 거부한다.
- 현재 Core API와 migration 연결 계약에 맞춰 최종 `sslmode`는 명시적인 `require`만 허용한다. 누락, 기본값과 `disable`은 거부한다.
- JDBC URL의 정확한 형태는 `jdbc:postgresql://<single-host>:<explicit-port>/postgres?sslmode=require`이다. percent encoding, 추가·중복 query, userinfo, fragment와 multi-host authority는 PGJDBC 파싱 전에 거부한다.
- DB 연결 후에는 `autoCommit=false`, JDBC read-only와 `SET TRANSACTION READ ONLY`를 모두 적용하고 성공·실패와 관계없이 rollback한다.
- Flyway 최신 성공 version과 실패 이력 건수, 병원 가이드 수, 전체·활성 동행 세션 수만 집계한다.
- V13에서는 V14가 실제 변경할 `firestore_id is null and current_step_order between 0 and 7` 행을 집계한다.
- V14·V15에서는 migration 이후에도 `guide_snapshot_source='UNRESOLVED_LEGACY'`인 행을 별도 집계한다.

## 대안

- Supabase SQL Editor에서 사람이 직접 조회할 수 있지만 연결 대상, 쿼리 원문과 결과 기록이 실행자마다 달라진다.
- 기존 migration workflow에 사전 점검 모드를 넣을 수 있지만 읽기 작업과 Flyway apply 경계가 섞이고 입력 실수의 영향 범위가 커진다.
- 백업 workflow에 점검을 붙일 수 있지만 백업 WIF와 객체 저장소 권한이 불필요하게 결합된다.

## 선택 이유

현재 운영 DB에는 아직 사용자 트래픽이 없지만 migration 자격 증명은 실제 쓰기 권한을 가진다. 따라서 현재 규모에서도 연결 대상을 네트워크 접속 전에 고정하고, apply workflow와 같은 승인·동시 실행 경계를 공유하면서 실행 내용은 읽기와 rollback으로 제한하는 편이 안전하다.

## 리스크

- 집계가 0이어도 V14·V15 적용과 rollback 가능성을 증명하지는 않는다.
- V13의 대상 건수는 migration 예상 변경량이고, V14 이후의 미확정 snapshot 건수와 의미가 다르다.
- migration Environment 관리자가 `PRODUCTION_SUPABASE_PROJECT_REF`를 잘못 등록하면 실행기는 닫힌 방식으로 중단되지만 올바른 값을 자동 복구하지 않는다.
- 현재 DB 내부에는 배포 대상 project를 독립적으로 증명하는 immutable marker가 없다. 따라서 세 workflow 모두 PGJDBC가 최종 해석한 host, port, database와 username 검증에 의존하며, Environment ref와 연결 정보가 함께 잘못 설정되면 같은 잘못된 대상을 일관되게 가리킬 수 있는 잔여 리스크가 있다.
- 이 점검은 백업 생성, 격리 복원, Flyway 실행과 Core API 배포를 수행하지 않는다.

## Workflow 입력과 Environment 설정

| 구분 | 이름 | 기준 |
|---|---|---|
| 입력 | `confirm_project` | `bodeul-prod-110` |
| 입력 | `confirm_database_project_ref` | Environment 변수와 같은 Supabase project ref |
| 입력 | `confirm_commit` | workflow가 실행되는 현재 `master` commit SHA 40자 |
| Environment variable | `PRODUCTION_SUPABASE_PROJECT_REF` | 영문 소문자와 숫자로 된 20자 project ref |
| Environment secret | `MIGRATION_DB_JDBC_URL` | Production Supabase direct 또는 pooler JDBC URL |
| Environment secret | `MIGRATION_DB_USERNAME` | migration 사용자명. pooler는 project ref suffix 필요 |
| Environment secret | `MIGRATION_DB_PASSWORD` | migration 비밀번호 |

workflow와 Java 실행기는 접속 문자열, 사용자명과 Supabase project ref 원문을 로그와 오류에 출력하지 않는다.

## Workflow별 검증 범위

| Workflow | 대상 검증 | DB 접속 | schema 집계 | 쓰기 작업 |
|---|---|---:|---:|---:|
| `Core API Production DB Migration Readiness` | 입력·Environment 확인 후 Java target 검증 | 읽기 전용 | 수행 | 없음 |
| `Core API DB Migration` production | 입력·Environment 확인 후 Java target 검증 | Flyway 단계에서 수행 | apply 후 별도 계약 검증 | migration 수행 |
| `Production PostgreSQL Backup and Restore Rehearsal` | 입력·Environment 확인 후 Java target 검증 | dump 단계에서 수행 | 수행하지 않음 | 원본 DB에는 없음 |

preview migration도 `dev`와 개발 project ref를 확인한다. 운영 자격 증명·project ref를 재사용하지 않는다. 정확한 입력은 현재 `core-api-migration.yml`과 [환경 전환 기준](dev-production-branch-transition-plan.md)을 따른다.

## 현재 검증 상태

- mock 단위 테스트와 Core API 전체 검증으로 `--target-only` 인자 제한과 비밀번호 없는 실행 설정, direct·pooler 판별, 다른 project 차단, 비정상 입력 차단, 읽기 전용 트랜잭션, rollback, version별 query와 열 계약을 확인한다.
- 2026-08-26 Production Supabase project를 재개하고 `ACTIVE_HEALTHY` 상태를 확인했다.
- [migration 전 run 32980526711](https://github.com/bodeul110/Bodeul/actions/runs/32980526711)은 Flyway V13, 실패 이력과 V14 backfill 후보 0건을 확인했다.
- 최신 backup·restore 성공 뒤 [migration run 32981200371](https://github.com/bodeul110/Bodeul/actions/runs/32981200371)에서 V14·V15와 계정 삭제 영향도 계약 검증을 통과했다.
- [migration 후 run 32981484159](https://github.com/bodeul110/Bodeul/actions/runs/32981484159)은 Flyway V15와 실패 이력 0건을 확인했다.
- [migration 후 backup·restore run 32981633994](https://github.com/bodeul110/Bodeul/actions/runs/32981633994)에서 새 V15 복구 지점을 격리 복원한 뒤 비공개 GCS에 보관했다.
- 이후 migration에도 사전 점검과 최신 backup·restore 증적 없이는 production migration workflow를 실행하지 않는다.

상세 결과는 [Production DB V15 migration·복원 검증](../reports/production-db-migration-readiness-2026-08-26.md)을 따른다.
