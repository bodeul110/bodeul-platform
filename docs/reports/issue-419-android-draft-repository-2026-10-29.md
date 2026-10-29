# 보호자 예약 원문 Android 데이터 계층 검증

기준일: 2026-10-29

## 구현한 내용

- #501을 승인된 `bc98205`에서 `dev`로 squash 병합했다. 병합 커밋은 `2221f0e`이며 #419 전체를 완료 처리하지 않았다.
- Android에서 저장 원문 API의 저장·목록·상세·승인 조회·승인·철회·접수를 호출하는 전용 Repository를 추가했다. 기존 직접 본문 API와 debug 합성 화면은 유지한다.
- 요청 키와 원문을 분리하고 서버 UUID·유효기간·가격·마스킹 표시 계약을 검사한다. 원문을 불러오지 못해도 기존 API로 우회하거나 새 요청 ID를 발급하지 않는다.
- 원문 만료 후 기존 승인 철회와 접수증 재전송 경로를 사용할 수 있도록 승인·철회·접수 입력에서 원문을 제거했다. 접수 전 승인 조건과 실제 정보공유 인가는 서버가 계속 판단한다.
- 요청 시작 계정으로 토큰 취득을 제한하고, 각 전송과 콜백에서 계정 일치를 확인한다. 다른 계정의 늦은 원문·접수증을 반환하지 않는다.

## 변경된 범위

Android의 전용 Repository·도메인 모델, 공용 인증 클라이언트의 계정 지정 실행·배열 응답 처리, 단위 테스트와 문서다. 공용 클라이언트의 기존 객체 응답·HTTP 상태 오류·1 MiB 제한을 보존한다. DB 스키마·서버·관리자 웹·의존성·빌드 버전은 변경하지 않았다.

## 검증

| 검사 | 결과 |
| --- | --- |
| `gradlew.bat assembleDebug testDebugUnitTest :app:verifyReleaseAppCheckClasspath --console=plain` | 통과 |
| Android 단위 테스트 전체 | 402건 통과, 실패·오류·skip 0건 |
| 원문 Repository 신규 단위 테스트 | 31건 통과 |
| 공용 HTTP 객체·배열 응답 신규 단위 테스트 | 6건 통과 |
| 기존 직접 본문 Repository / Coordinator 회귀 | 각각 26건 / 18건 통과 |
| 로컬 환경 혼합 차단 스크립트 | 두 번째 검사에서 중단. `local.properties`의 Debug Realtime 값이 주입한 Gradle 테스트 값보다 우선하므로 이 checkout에서 격리 검사 결과로 사용할 수 없음 |

환경 혼합 차단 스크립트는 원래 로컬 설정 없는 CI 또는 별도 검증 checkout에서 실행하도록 되어 있다. 로컬 설정은 수정하지 않았고 PR preflight의 동일 검사를 확인한다. 이 결과를 로컬 통과로 기록하지 않는다.

단위 검증에는 같은 ID 저장 재시도, 잘못된 참여자·역할·요청·가격 응답, 목록 오류/중복/50건 상한, 마스킹 필드, 원문 없는 철회, 만료 승인으로 기존 접수증 재요청, 전송 후 응답 유실·재시도, 계정 전환 후 콜백 폐기를 포함한다. 전송 응답과 계정 일치 조건은 테스트 대역이며 실제 Firebase 로그인·원격 HTTP·DB·실기기 검증이 아니다.

#501 병합 후 [Core API CI](https://github.com/bodeul110/bodeul-platform/actions/runs/40818688322), [Android preflight](https://github.com/bodeul110/bodeul-platform/actions/runs/40818688256), [CodeQL](https://github.com/bodeul110/bodeul-platform/actions/runs/40818688251), [자동 개발 배포](https://github.com/bodeul110/bodeul-platform/actions/runs/40818688250)가 성공했다. 이는 새 Android 원문 Repository의 원격 종단 검증이 아니다. 실제 DB migration·Functions 배포·파기 apply와 기능 활성화는 실행하지 않았다.

## 남은 범위

- 원문 수신 목록·상대방 지정·환자 검토/승인 화면을 새 데이터 계층에 연결
- 화면에 이미 표시된 원문을 로그아웃·계정 전환 시 정리하고, 프로세스 재시작 후 같은 요청을 선택해 복구
- 실제 개발 DB V24~V27 적용·권한·파기 작업 연결 확인과 활성화 승인
- 환자·보호자 두 계정의 실제 종단 검증과 일반 메뉴 개방

[설계 판단](../design/guardian-booking-android-drafts.md)과 [API 계약](../architecture/guardian-booking-api.md)을 함께 확인한다.
