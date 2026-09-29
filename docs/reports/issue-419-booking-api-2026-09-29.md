# #419 보호자 예약 승인 API 연결 검증

기준일: 2026-09-29

## 구현한 내용

환자 본인 미리보기·승인·철회와 지정 보호자의 예약 생성 API를 추가했다. 활성 Firebase 계정과 현재 DB 역할을 확인하고 서버가 계산한 본문·가격 fingerprint를 최신 승인 ID·버전과 비교한다. 승인 행 잠금부터 예약 저장까지 같은 트랜잭션으로 처리한다. 보호자에게는 최소 접수증만 반환하며 예약 후 정보공유 동의를 자동 발급하지 않는다.

## 변경된 범위

- `core-api/` 인증 보조 조회, 예약 정규화 공유, 승인·생성 서비스와 HTTP 계약
- V24 기존 읽기 권한을 이용한 계정 삭제 영향도 승인·감사 집계
- 후속 V25의 보호자 기준 감사 조회 인덱스와 인덱스 전용 rollback. 기존 V24 원문·권한 유지
- 단위·HTTP·격리 PostgreSQL 테스트와 [API 계약](../architecture/guardian-booking-api.md)
- 관리자 웹 UI·테이블 권한, Android, Firebase Rules, 의존성 버전은 변경하지 않음

## 최초 구현 검증

- 로컬 `core-api check`: 529건 성공, 실패·건너뜀 없음
- 기존 환자 직접 예약·보호자 수정/취소 차단 회귀 테스트 포함
- PostgreSQL 실검증: 기존 저장 계층 23건과 실제 예약 연동 10건, 총 33건 통과. 일회용 localhost PostgreSQL 17에서 Core runtime role로 실행
- 예약 연동 10건: 정상 저장·정보공유 차단, 동시 중복 생성, 전체 rollback, 생성 후 철회 대기, 생성이 먼저 완료된 재승인 rollback, 철회 후 생성 거부, 본문/재승인 불일치, 철회 후 최소 접수증 재전송, 쓰기 트랜잭션 강제와 당사자별 삭제 영향도 집계
- 코드 커밋 `4bfba53`의 [Core API CI](https://github.com/bodeul110/bodeul-platform/actions/runs/36572895305): check·컨테이너 빌드·Firestore Emulator·migration contract 통과
- [preflight](https://github.com/bodeul110/bodeul-platform/actions/runs/36572895411) 통과. CodeQL workflow의 scope만 통과했으며 Android/JS 분석 job은 변경 범위상 건너뜀
- 변경 문서의 상대 링크와 `git diff --check` 통과
- 로컬 Docker 엔진이 실행 중이지 않아 로컬 PostgreSQL 실행은 하지 않음

## 리뷰 보완

#477의 보호자 기준 감사 인덱스 누락 지적을 반영해 V25를 추가했다. 인덱스·rollback 계약 검사를 포함한 로컬 Core API 531건은 통과했다. 코드 커밋 `da78928`의 [Core API CI](https://github.com/bodeul110/bodeul-platform/actions/runs/36585287537)에서 격리 PostgreSQL 35건, 컨테이너 빌드·Firestore Emulator·기존 migration 검증을 통과했고 [preflight](https://github.com/bodeul110/bodeul-platform/actions/runs/36585287461)도 성공했다. CodeQL은 scope만 통과했으며 Android/JS 분석은 건너뛰었다.

- 실제 Core runtime 역할에서 합성 감사 10,001건 중 한 보호자의 1건을 집계했다. `ANALYZE` 후 planner 강제 설정 없이 `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`으로 OR 집계의 기존 PK·새 인덱스 `BitmapOr` 사용과 보호자 단독 조건의 새 인덱스 사용을 확인했다. 두 계획 모두 `Seq Scan`이 없었다.
- V25 rollback·재적용을 migration 역할로 실행해 기존 승인·감사 snapshot이 그대로이고, 재생성한 인덱스가 유효하며 migration 역할 소유임을 확인했다. 합성 데이터와 DDL 변경은 테스트 트랜잭션 종료 시 되돌렸다.
- 기존 V24 안전 rollback 검증 앞에서 V25 rollback의 객체 경계도 CI로 확인했다. V24 파일은 `dev` 기준 원문과 동일하다.
- 변경 문서 상대 링크, `git diff --check`, 검증 스크립트 `bash -n` 통과. 기능 활성화·실제 DB migration은 실행하지 않았다.

## 남은 범위

기능 플래그는 기본 OFF다. 실제 개발·운영 DB에는 V24·V25를 적용하지 않았고 배포·환경 활성화·실기기 검증도 실행하지 않았다. 환자에게 요청 본문을 전달하는 기능과 Android 확인·승인·보호자 예약 UI, 개발 Cloud Run의 Firebase 사용자 조회 권한 및 DEV 종단 검증이 남아 있다. 승인 감사의 실제 삭제·보존 작업도 기존 #348 경계로 분리한다. #419 전체 완료나 운영 보호자 예약 허용을 뜻하지 않는다.
