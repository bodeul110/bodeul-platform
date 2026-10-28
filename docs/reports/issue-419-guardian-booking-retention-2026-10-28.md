# 보호자 예약 원문 파기 작업 연결

## 구현한 내용

#419의 서버 원문 파기 함수를 기존 Functions 일일 보존 작업에 연결했다. `RETENTION_GUARDIAN_BOOKING_ENABLED`는 기본 OFF다. ON에서도 dry-run은 후보 집계만 하며, 기존 apply 제어를 통과해야 원문을 파기한다.

V27은 기존 파기 실행 이력에 활성 점검 여부와 후보·파기·잔여 건수를 추가한다. 새 키가 없는 이전 실행기도 허용하며, 월간 보고는 활성 실행 수와 마지막 잔여 보고값을 제공한다. 파기 전에 V27 집계 계약을 확인하므로 V26만 있는 환경에서 삭제 후 집계 저장에 실패하는 순서를 피한다.

## PR 검토 반영

#501의 응답 유실 지적에 따라 파기와 집계 저장을 같은 DB 트랜잭션으로 묶었다. 실행 ID로 `APPLY`·`RUNNING` 행을 잠그고 원문 제거와 누적 건수 증가를 함께 커밋한다. 완료·실패 기록은 이 값을 덮어쓰지 않는다. 집계 없이 파기하던 이전 함수의 runtime 실행 권한도 회수했으며 rollback은 이를 복구한다.

커밋 후 응답 유실, 종료 기록 생략, 같은 실행·새 실행 재시도, 외부 트랜잭션 rollback 검증을 추가했다. 이 수정의 로컬 Core API 599건·Functions 98건은 통과했다. 실제 PostgreSQL 재검증은 수정 커밋의 CI에서 확인하며 아래 결과는 수정 전 기록이다.

## 변경된 범위

- Functions: 새 경로의 조회·500건 배치·최대 10회 반복·잔여 재조회·실패 단계·재시도 및 건수 집계.
- Core API: V27 migration·이력 보존형 rollback·격리 PostgreSQL 검증. 예약 API·원문·권한은 변경하지 않는다.
- 관리자 웹: 기존 월간 집계 필드·조회 권한을 유지하고 새 숫자 필드만 추가한다. 웹 소스와 원문 접근 권한은 변경하지 않는다.
- 판단 근거와 적용 순서: [보호자 예약 원문 정기 파기](../design/guardian-booking-retention.md).

## 초기 검증 기록

- Java 21 `core-api check`: 598건 통과, 실패·오류·skip 0건.
- Node 22.23.4 `npm --prefix functions test`: 98건 통과, 별도 Firestore/Storage emulator가 필요한 3건은 일반 단위 실행에서 skip.
- 새 Functions 테스트 12건: 기본 OFF·이전 집계 호환, dry-run, 배치·상한·잠금 잔여, 미적용 DB, 부분 실패 후 재시도, 마지막 조회 실패, SQL 값 바인딩, 비정상 건수 거부, 월간 보고.
- CI 전용 PostgreSQL 17: 보호자 예약 승인·원문·파기 계약 57건 통과(신규 파기 집계·권한·rollback 11건 포함). V27 실제 적용·역할별 권한·이력 보존 rollback과 재적용을 확인했다. 일반 `check`의 문자열 계약 테스트와 구분한다.
- Node 22 실행기와 실제 PostgreSQL 연동 1건 통과: 만료된 합성 원문 503건과 유효 원문 1건으로 OFF·dry-run 무변경, 배치 파기, 0건 재실행, 월간 집계, 직접 조회·쓰기 차단을 확인했다. 다른 도메인의 파기와 Firebase·Storage는 대체 객체를 사용하므로 전체 운영 파기 E2E 성공을 뜻하지 않는다.
- `e6fd3bb`의 [Core API CI](https://github.com/bodeul110/bodeul-platform/actions/runs/40678377438), Firebase Rules emulator, preflight, JavaScript CodeQL 통과. GitHub YAML 파싱, shell·Node 테스트 문법과 `git diff --check`도 통과했다. CI의 합성 데이터는 실행별 새 로컬 DB에만 생성했다.

## 남은 범위

실제 개발·운영 DB migration, Functions 배포, 환경변수 변경, 원문 삭제 apply, 예약 기능 활성화는 실행하지 않았다. 원문 조회 만료와 물리 파기 시각은 다르며, 기존 일일 스케줄과 배치 한도로 인한 지연을 즉시 삭제 완료로 표시하지 않는다.

환경별 V24~V27 적용·권한 확인과 합성 원문 dry-run·파기·재실행 검증, Android 수신·상대방 지정·계정 전환 복구, 실제 두 계정 연동이 남는다. 원문 파기 후의 최소 식별 기록 삭제는 계정 삭제·보존 절차의 별도 범위다.
