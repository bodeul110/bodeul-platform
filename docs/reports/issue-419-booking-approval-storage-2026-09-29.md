# #419 보호자 예약 생성 승인 저장 검증

기준일: 2026-09-29

## 구현 범위

- [기존 정책](../architecture/adult-patient-guardian-booking-authorization.md)을 유지하고 V24/JDBC에 요청별 현재 승인·감사 snapshot을 추가했다.
- 최초 승인 중복은 복합 PK, 재승인·철회 경합은 버전 조건부 갱신으로 거부한다. 상태와 감사는 한 SQL 문이므로 감사 저장 실패도 함께 취소된다.
- `lockCurrent`는 쓰기 트랜잭션을 요구한다. 아직 예약 생성 API나 Android에 연결하지 않았다.
- 서버 private schema의 Core runtime만 접근하며 관리자 웹, 공개 역할, 정보공유, Realtime 권한은 확대하지 않는다.

## 검증

- 로컬 `core-api check`: 474건, 실패·오류·건너뜀 0건. 별도 태그의 PostgreSQL 테스트는 이 수에 포함하지 않는다.
- `yq e '.' .github/workflows/core-api.yml`, Bash 구문 검사, `git diff --check` 통과.
- 로컬 Docker engine에 연결할 수 없어 실제 PostgreSQL 검증은 GitHub CI의 PostgreSQL 17 격리 DB에서 실행했다. `3de30b8`의 [Core API CI](https://github.com/bodeul110/bodeul-platform/actions/runs/36560272413)에서 check·컨테이너 빌드·Firestore emulator·migration-contract가 통과했다.
- 격리 DB에서 Flyway V1~V24 적용, JDBC 저장·동시 최초 승인·재승인/철회 경합·양방향 잠금 대기·감사 실패와 호출자 rollback·역할 권한 거부를 확인했다. 이력이 있으면 V24 rollback을 거부하고, 합성 데이터만 비운 뒤에는 V24 객체만 제거되는 것도 통과했다.
- PostgreSQL 태그 테스트와 기존 일반 테스트는 별도 실행이다. 실제 테스트 결과가 CI 로그에 남도록 전용 태스크의 개별 성공·실패·건너뜀 출력을 켰다. 후속 커밋의 최종 CI는 [PR #476](https://github.com/bodeul110/bodeul-platform/pull/476)의 head별 결과로 확인한다.
- 새 `guardianBookingApprovalPostgresTest`는 localhost/127.0.0.1의 고정 테스트 DB만 허용한다. CI는 DB를 새로 만들고 기존 DB가 있으면 중단한다.
- 잘못된 외부 주소를 테스트 설정에 넣으면 DB 접속 전에 태스크가 거부되는 것을 확인했다. 이는 의도한 실패 검증이며 실제 DB 접속 실패가 아니다.

## 남은 범위

- 실제 DEV·production DB에 V24 적용하지 않음. 배포·운영 데이터·Android·실기기 변경 없음.
- API의 인증·활성 역할·성인 본인 확인, 서버 본문 fingerprint, 승인 잠금과 실제 예약 INSERT의 단일 트랜잭션, Android 승인 화면 및 종단 검증이 남는다.
- 기존 보호자 생성 403과 수정·취소 제한은 유지한다. 이 저장 기반을 #419 전체 완료로 보지 않는다.

선택 이유·대안·리스크는 [저장 경계](../architecture/guardian-booking-approval-storage.md)를 따른다.
