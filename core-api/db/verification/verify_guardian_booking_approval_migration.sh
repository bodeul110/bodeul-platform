#!/usr/bin/env bash
set -euo pipefail

export PGHOST="${PGHOST:-127.0.0.1}"
export PGPORT="${PGPORT:-5432}"
export PGUSER="${PGUSER:-postgres}"
export PGPASSWORD="${PGPASSWORD:-postgres}"
database="bodeul_guardian_booking_test"

# 기존·원격 DB에 테스트 데이터를 쓰지 않는다. 동명의 DB가 있으면 createdb에서 중단한다.
if [[ "$PGHOST" != "127.0.0.1" && "$PGHOST" != "localhost" ]]; then
    echo "로컬 격리 PostgreSQL에서만 실행할 수 있습니다." >&2
    exit 1
fi
createdb "$database"
psql --dbname postgres --set ON_ERROR_STOP=1 <<'SQL'
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'service_role') then
        create role service_role nologin;
    end if;
end;
$$;
SQL
psql --dbname "$database" --set ON_ERROR_STOP=1 --file db/bootstrap/001_database_access.sql
psql --dbname "$database" --set ON_ERROR_STOP=1 --file db/bootstrap/002_database_access_hardening.sql
psql --dbname "$database" --set ON_ERROR_STOP=1 --file db/bootstrap/004_retention_runtime.sql

export MIGRATION_DB_JDBC_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${database}?sslmode=disable"
export MIGRATION_DB_USERNAME="$PGUSER"
export MIGRATION_DB_PASSWORD="$PGPASSWORD"
SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_BASELINE_VERSION=0 \
    ./gradlew migrateDatabase --console=plain

export BOOKING_TEST_DB_URL="jdbc:postgresql://${PGHOST}:${PGPORT}/${database}"
export BOOKING_TEST_DB_USER="$PGUSER"
export BOOKING_TEST_DB_PASSWORD="$PGPASSWORD"
./gradlew guardianBookingApprovalPostgresTest --console=plain

# 전용 DB에 만든 합성 데이터만 비우고 빈 schema rollback을 검사한다. 실제 이력 삭제 절차가 아니다.
psql --dbname "$database" --set ON_ERROR_STOP=1 <<'SQL'
truncate bodeul.guardian_booking_approval_events, bodeul.guardian_booking_approvals;
SQL
psql --dbname "$database" --set ON_ERROR_STOP=1 --file db/rollback/V24__remove_guardian_booking_approvals.sql
psql --dbname "$database" --set ON_ERROR_STOP=1 <<'SQL'
do $$
begin
    if to_regclass('bodeul.guardian_booking_approvals') is not null
            or to_regclass('bodeul.guardian_booking_approval_events') is not null
            or to_regclass('bodeul.guardian_sharing_consents') is null
            or to_regclass('bodeul.appointment_requests') is null then
        raise exception 'V24 rollback의 객체 경계가 일치하지 않습니다.';
    end if;
end;
$$;
SQL
