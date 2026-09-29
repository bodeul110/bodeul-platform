# 보호자 예약 생성 승인 저장 경계

기준일: 2026-09-29

## 설계 판단

- 작업 목적: #419의 순수 승인 판정을 요청별 최신 상태와 추가 전용 감사 이력으로 저장하고 동시 변경 유실을 막는다.
- 선택한 방식: 기존 Flyway 경계의 V24와 JDBC Repository를 추가한다. 최초 저장은 요청 키의 유일 제약, 변경은 버전 조건부 갱신을 사용하고 상태와 이벤트를 한 SQL 문에서 저장한다.
- 대안: 정보공유 동의 테이블 재사용, JVM 잠금, 상태와 감사의 개별 저장을 검토했다.
- 선택 이유: 현재 MVP에서는 기존 PostgreSQL의 트랜잭션으로 충분하다. 정보공유와 예약 생성은 다른 권한이며 JVM 잠금은 여러 Cloud Run 인스턴스의 경합을 막지 못한다.
- 리스크: 저장 계층만으로 활성 계정·역할·성인 본인 확인이나 예약 생성까지 보장하지 않는다. API와 실제 예약 INSERT는 후속 작업이며 기존 보호자 생성 403을 유지한다.

## 이번 범위

- 서버 전용 `bodeul` schema에 현재 승인과 감사 이벤트를 분리한다. 예약 본문·건강정보 원문은 저장하지 않는다.
- `(patient_user_id, guardian_user_id, client_request_id)`당 현재 행 한 개와 단조 증가 버전을 유지한다. 철회 후에도 삭제하거나 버전을 초기화하지 않는다.
- Core runtime만 현재 상태의 SELECT/INSERT/UPDATE와 이벤트 SELECT/INSERT를 가진다. 관리자 서버·Supabase 공개 역할의 권한과 Realtime 계약은 바꾸지 않는다.
- 예약 생성 시 사용할 최신 상태 잠금은 쓰기 가능한 Spring 트랜잭션 안에서만 허용한다. 아직 이 메서드를 예약 API에 연결하지 않는다.
- 실제 개발·운영 DB 적용, 배포, Android 변경은 하지 않는다. 격리 PostgreSQL의 저장·경합·권한·rollback 검증을 별도 CI에 둔다.

## 남은 연결

환자 승인 API의 인증·활성 역할 재확인, 서버의 본문 fingerprint·정책 버전·시각 결정, 승인 잠금부터 실제 예약 INSERT까지 동일 트랜잭션 유지, 재시도 응답의 정보공유 제한, Android 승인 화면과 DEV 종단 검증이 남아 있다. API를 켜기 전에 새 감사 데이터의 계정 삭제 영향도 집계와 보관 경계도 연결한다.

정책 계약은 [성인 환자·보호자 예약 생성 승인](adult-patient-guardian-booking-authorization.md)을 따른다. 행 잠금의 트랜잭션 수명은 [PostgreSQL 17 문서](https://www.postgresql.org/docs/17/explicit-locking.html#LOCKING-ROWS)를 기준으로 검증한다.
