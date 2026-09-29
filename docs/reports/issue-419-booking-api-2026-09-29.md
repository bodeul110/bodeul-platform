# #419 보호자 예약 승인 API 연결 검증

기준일: 2026-09-29

## 구현한 내용

환자 본인 미리보기·승인·철회와 지정 보호자의 예약 생성 API를 추가했다. 활성 Firebase 계정과 현재 DB 역할을 확인하고 서버가 계산한 본문·가격 fingerprint를 최신 승인 ID·버전과 비교한다. 승인 행 잠금부터 예약 저장까지 같은 트랜잭션으로 처리한다. 보호자에게는 최소 접수증만 반환하며 예약 후 정보공유 동의를 자동 발급하지 않는다.

## 변경된 범위

- `core-api/` 인증 보조 조회, 예약 정규화 공유, 승인·생성 서비스와 HTTP 계약
- V24 기존 읽기 권한을 이용한 계정 삭제 영향도 승인·감사 집계
- 단위·HTTP·격리 PostgreSQL 테스트와 [API 계약](../architecture/guardian-booking-api.md)
- 관리자 웹 UI·테이블 권한, Android, Firebase Rules, 의존성 버전은 변경하지 않음

## 검증

- 로컬 `core-api check`: 529건 성공, 실패·건너뜀 없음
- 기존 환자 직접 예약·보호자 수정/취소 차단 회귀 테스트 포함
- PostgreSQL 실검증: 기존 저장 계층 23건과 실제 예약 연동 10건, 총 33건 통과. 일회용 localhost PostgreSQL 17에서 Core runtime role로 실행
- 예약 연동 10건: 정상 저장·정보공유 차단, 동시 중복 생성, 전체 rollback, 생성 후 철회 대기, 생성이 먼저 완료된 재승인 rollback, 철회 후 생성 거부, 본문/재승인 불일치, 철회 후 최소 접수증 재전송, 쓰기 트랜잭션 강제와 당사자별 삭제 영향도 집계
- 코드 커밋 `4bfba53`의 [Core API CI](https://github.com/bodeul110/bodeul-platform/actions/runs/36572895305): check·컨테이너 빌드·Firestore Emulator·migration contract 통과
- [preflight](https://github.com/bodeul110/bodeul-platform/actions/runs/36572895411) 통과. CodeQL workflow의 scope만 통과했으며 Android/JS 분석 job은 변경 범위상 건너뜀
- 변경 문서의 상대 링크와 `git diff --check` 통과
- 로컬 Docker 엔진이 실행 중이지 않아 로컬 PostgreSQL 실행은 하지 않음

## 남은 범위

기능 플래그는 기본 OFF다. 실제 개발·운영 DB에는 V24를 적용하지 않았고 배포·환경 활성화·실기기 검증도 실행하지 않았다. 환자에게 요청 본문을 전달하는 기능과 Android 확인·승인·보호자 예약 UI, 개발 Cloud Run의 Firebase 사용자 조회 권한 및 DEV 종단 검증이 남아 있다. 승인 감사의 실제 삭제·보존 작업도 기존 #348 경계로 분리한다. #419 전체 완료나 운영 보호자 예약 허용을 뜻하지 않는다.
