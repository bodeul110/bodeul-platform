# 보호자 예약 Android 승인 연결 검증

기준일: 2026-10-28

## 구현한 내용

#419의 후속으로 기존 #477 API를 호출하는 Android Repository, 승인 모델, Coordinator와 전체 본문 검토 Binder를 추가했다. 요청별 PostgreSQL 환자·보호자 ID와 역할을 같은 Firebase ID token의 `/api/auth/me` 응답으로 재확인하고, 기존 App Check 인증 경계를 사용한다.

환자는 정규화된 본문과 두 확인 항목을 검토한 뒤 승인할 수 있다. 보호자는 유효한 승인으로 접수하며 결과는 최소 접수 코드만 표시한다. 조회 실패를 미승인으로 처리하지 않고, 생성 응답 유실 뒤에는 동일 본문·승인·요청 ID로만 재시도한다. 중복 클릭, 이전 화면의 지연 응답과 철회 후 권한 확대도 차단한다.

## 변경된 범위

- Android 모델·Repository·Coordinator·Binder·문자열·레이아웃 및 단위 테스트
- debug 전용 합성 승인 화면과 계측 테스트. 외부 intent로 실제 원문이나 계정 정보를 받지 않음
- API 문서의 Android 준비 상태, 구현 현황과 이 설계·검증 기록

Core API·DB schema·데이터·기능 플래그·배포·의존성·관리자 웹은 변경하지 않았다. 정상 앱 메뉴와 release 진입점도 추가하지 않았다. API client가 실제 서버를 호출할 준비와 실제 앱 기능 개방은 구분한다.

## 검증

| 검증 | 결과 |
| --- | --- |
| `gradlew.bat assembleDebug testDebugUnitTest` | 성공. 전체 단위 테스트 351건, 실패·오류·건너뜀 0건. 이 변경의 신규 테스트는 Repository 16건·Coordinator 14건 |
| `gradlew.bat :app:verifyReleaseAppCheckClasspath` | 성공. release App Check provider 의존성 경계 유지 |
| `connectedDebugAndroidTest`의 `GuardianBookingPreviewTest` 지정 실행 | Android 11/API 30 소형 AVD에서 4건 통과. 환자 확인→승인→보호자 접수, 철회 후 접수 차단, 화면 재생성·재진입 상태 폐기 |
| 화면 직접 확인 | 720×1280, 320 dpi에서 전체 본문 스크롤과 확인란·버튼 표시 확인. 합성 데이터만 사용 |

단위 테스트는 인증된 실제 HTTP 통신이 아니라 JSON 요청·응답 및 오류 계약을 검증한다. 계측 테스트는 인메모리 합성 Repository를 사용하며 원격 계정·예약·DB 쓰기 검증이 아니다. ADB에 실기기가 없어 실제 휴대폰 검증은 하지 않았다. 검증용 AVD는 read-only·스냅샷 저장 없이 실행했다.

## 남은 범위

1. 예약 원문을 지정 환자에게 안전하게 전달하고 보호자가 같은 요청으로 접수할 수 있는 서버 저장·조회·알림 경로. 현재 승인 저장소에는 원문이 없다.
2. 환자가 실제 상대방을 확인할 수 있는 검증된 표시 정보. 합성 화면의 UUID 표시는 사용자용 상대방 확인을 대신하지 않는다.
3. 비활성 보호자의 승인을 환자가 조회·철회하는 경계. 현재 GET은 양쪽 활성 계정을 확인하므로 새 화면 조회가 막힐 수 있다. 이번 클라이언트는 권한 오류를 우회하지 않는다.
4. 실제 DEV migration 이력·runtime 계정 상태 조회 권한 확인, 승인된 기능 활성화와 두 계정의 인증·동시성·재시도·정보공유 차단 종단 검증.
5. 로그아웃·계정 전환·프로세스 종료 후 복구를 포함한 일반 사용자 진입 연결. 운영 적용은 별도다.

#419 전체 완료나 운영 개방을 의미하지 않는다. 상세 선택 근거는 [Android 연결 설계](../design/guardian-booking-android.md), 서버 계약은 [승인 API](../architecture/guardian-booking-api.md)를 따른다.
