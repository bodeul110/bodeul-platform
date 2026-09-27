# Firebase 설정

기준일: 2026-09-27 (개발·운영 설정 분리, 과거 스키마 예시 포함)

현재 업무 원본은 PostgreSQL이다. 아래 Firestore 예약·세션·리포트 예시와 예약 알림 Functions 흐름은 legacy 비교·운영 도구 설명이며 신규 Core 예약 경로가 아니다. 현재 권한은 [Rules 경계](../../security/firebase-rules-validation.md), 업무 계약은 [예약 Core API](../../architecture/appointment-core-api.md), DB 버전은 [migration 목록](../../architecture/database-migration-catalog.md)을 우선한다. 개발·운영 리소스에 배포·seed·reset을 실행하는 것은 별도 승인 작업이다.

## 현재 프로젝트 상태

- Android 패키지명: `com.example.bodeul`
- Firebase 설정 파일: 개발은 `app/src/debug/google-services.json`, 운영은 `app/src/release/google-services.json`. 기존 `app/google-services.json`은 개발용 호환 경로다.
- 인증: Firebase Authentication
- 업무 데이터 저장소: Supabase PostgreSQL
- Firebase 데이터 범위: 인증·FCM 토큰, Storage, 전환 기간 legacy 읽기 자료
- Functions: `functions/index.js` 집계 파일과 `functions/src/` 기능별 모듈
- Firebase 설정이 없으면 앱은 자동으로 목업 모드로 동작한다.
- 인증 프로필·지원·매니저 심사 메타데이터와 Firebase 결합 job을 유지한다. Core 예약·세션을 Firestore로 새로 확장하지 않는다.
- 관리자 웹은 별도 저장소의 Next.js/Vercel이 소유한다. 루트 `firebase.json`은 Functions, Firestore, Storage와 emulator 설정만 관리한다.

## 소셜 로그인 로컬 설정

민감한 키는 `local.properties`에만 넣는다.

```properties
naverClientId=발급받은_클라이언트_ID
naverClientName=보들
kakaoNativeAppKey=발급받은_네이티브_앱_키
# 다른 개발 서버가 필요할 때만 Debug 기본 주소를 덮어쓴다.
bodeulCoreApiBaseUrl=http://10.0.2.2:8080
bodeulSupabaseDebugUrl=https://parpdzttloacinyvhwmx.supabase.co
bodeulSupabaseDebugPublishableKey=개발_Supabase_publishable_key
```

- Debug 빌드는 별도 설정이 없어도 `https://bodeul-core-api-preview-cyvvxy3kia-an.a.run.app`을 사용한다. 이 공개 서비스 주소에는 비밀값이 없다.
- `bodeulCoreApiBaseUrl`은 Debug 전용 호환 속성이다. 개발 Cloud Run 또는 `localhost`·`127.0.0.1`·에뮬레이터 `10.0.2.2`만 허용하며 운영 주소를 넣으면 빌드를 중단한다.
- Release는 공통/Debug 값을 상속하지 않는다. `bodeulCoreApiReleaseBaseUrl=https://bodeul-core-api-649312328770.asia-northeast1.run.app`, `bodeulSupabaseReleaseUrl=https://aoijbzgozbopsxzrasbb.supabase.co`, `bodeulSupabaseReleasePublishableKey`를 별도로 지정한다. 이 URL의 설정 가능 여부와 실제 운영 서비스 배포 완료는 구분한다.
- Release 설정은 각각 `BODEUL_CORE_API_RELEASE_BASE_URL`, `BODEUL_SUPABASE_RELEASE_URL`, `BODEUL_SUPABASE_RELEASE_PUBLISHABLE_KEY` 환경변수로도 주입할 수 있다.
- 빌드 전 Firebase 프로젝트 번호·Storage와 Core API·Realtime 환경을 검사한다. 운영은 별도 Firebase 파일과 Realtime 설정을 필수로 요구하며, Firebase 파일이 없는 Debug/CI의 Mock 컴파일은 유지한다. 서버용 Supabase secret/service-role key는 앱에 넣지 않는다.
- 네이버 클라이언트 시크릿은 Android 앱에 포함하지 않는다.
- 현재 앱의 네이버 로그인 버튼은 `naver_login_enabled=false`로 숨겨져 있으며, 서버 중계형 OAuth 흐름이 확정될 때 다시 연다.
- `kakaoNativeAppKey`에는 Kakao Developers에서 `com.example.bodeul` 패키지명과 현재 서명 키 해시를 연결한 Android 플랫폼 전용 네이티브 앱 키를 사용한다. 추적되는 `gradle.properties`에는 실제 키를 넣지 않는다.
- 카카오 로컬 REST API 키는 Android에 넣지 않고 Core API의 Google Secret Manager에 저장한다.
- Supabase publishable key는 private Realtime 연결 식별용 공개 키이며 DB 접속 비밀값이 아니다. Android는 Firebase ID token으로 private 채널 인가를 받고 Supabase Data API 쓰기는 사용하지 않는다.

## 콘솔에서 먼저 할 일

1. Firebase 프로젝트 생성
2. Android 앱 등록
3. `com.example.bodeul` 패키지명으로 SHA-1, SHA-256 등록
4. 개발·운영 프로젝트의 설정을 각각 `app/src/debug/google-services.json`, `app/src/release/google-services.json`에 배치
5. Authentication의 `Email/Password` 활성화
6. Firestore 생성
7. `firestore.rules`, `firestore.indexes.json` 배포
8. 관리자 웹 Firebase Web app과 Auth domain은 별도 저장소의 환경 기준으로 관리

## 관리자 웹 연결

관리자 웹 build와 배포는 [bodeul-admin-web](https://github.com/bodeul110/bodeul-admin-web) 저장소에서 수행한다. 메인 저장소는 관리자 Hosting을 배포하지 않는다. Firebase Web config, Auth domain, App Check와 관리자 DB 접속 기준은 [관리자 웹 환경 기준](../admin-web-environments.md)을 따른다.

## 유지 데이터와 legacy 컬렉션 예시

`users`, 지원·심사·알림 경로와 전환된 Core 업무의 비교 자료를 구분한다. 예시 JSON이 현재 클라이언트 쓰기 허용을 뜻하지 않는다.

### `users`

```json
{
  "name": "김보들",
  "email": "manager@bodeul.app",
  "phone": "010-0000-0003",
  "role": "MANAGER",
  "managerDocumentSummary": "요양보호사 자격증 제출 완료",
  "managerAvailabilitySummary": "평일 09:00-18:00 활동 가능"
}
```

### `appointmentRequests`

```json
{
  "patientUserId": "patient-uid",
  "guardianUserId": "guardian-uid",
  "hospitalName": "서울안과병원",
  "departmentName": "안과",
  "appointmentAt": "2026-04-22 10:30",
  "appointmentAtEpochMillis": 1776811800000,
  "appointmentDateKey": "2026-04-22",
  "meetingPlace": "본관 1층 로비",
  "specialNotes": "신분증과 복용 약 정보를 확인해 주세요.",
  "reminderStages": ["D7", "D3", "D1"],
  "status": "REQUESTED",
  "managerUserId": null
}
```

### `companionSessions`

```json
{
  "appointmentRequestId": "request-doc-id",
  "managerUserId": "manager-uid",
  "currentStepOrder": 2,
  "currentStatus": "MEETING",
  "guardianUpdate": "환자분을 만나 병원으로 이동 중입니다.",
  "locationSummary": "본관 접수처로 이동 중입니다.",
  "fieldPhotoNote": "접수표 확인 사진 업로드 예정입니다.",
  "medicationNote": "처방전 수령 예정입니다."
}
```

### `hospitalGuides`

```json
{
  "hospitalName": "서울안과병원",
  "departmentName": "안과",
  "steps": [
    {
      "order": 1,
      "title": "환자 확인",
      "description": "환자와 보호자 정보를 먼저 확인합니다."
    },
    {
      "order": 2,
      "title": "접수 진행",
      "description": "접수 창구에서 예약 정보를 확인합니다."
    }
  ]
}
```

### `sessionReports`

```json
{
  "sessionId": "session-doc-id",
  "summary": "진료 요약",
  "treatmentNotes": "진료 메모",
  "medicationNotes": "복약 메모",
  "nextVisitAt": "2026-04-29 10:00"
}
```

### `appointmentFollowUps`

> `supportEscalationStatus`, `supportEscalatedAt`은 기존 문서 읽기 호환을 위한 legacy 필드다. 현재 seed와 앱은 이 필드를 새로 저장하지 않는다.

```json
{
  "requestId": "request-doc-id",
  "reviewRatingCode": "SATISFIED",
  "reviewSavedAt": "2026-04-23T13:20:00Z",
  "settlementFollowUpStatus": "CONFIRMED",
  "settlementFollowUpNote": "현장 결제 확인 완료",
  "settlementFollowUpSavedAt": "2026-04-23T13:25:00Z",
  "supportEscalationStatus": "NONE",
  "supportEscalatedAt": null
}
```

### `supportInquiries`

```json
{
  "managerUserId": "manager-uid",
  "managerName": "김보들",
  "categoryCode": "PAYMENT",
  "title": "정산 문의",
  "body": "출금 신청 가능 시점을 확인하고 싶습니다.",
  "statusCode": "ANSWERED",
  "createdAt": "2026-04-23T09:00:00Z",
  "responseText": "다음 영업일에 확인 가능합니다.",
  "respondedAt": "2026-04-23T11:00:00Z",
  "respondedByName": "운영 관리자"
}
```

### `adminSettlementRecords`

```json
{
  "requestId": "request-doc-id",
  "statusCode": "CONFIRMED",
  "note": "사용자 확인 완료",
  "handledByName": "운영 관리자",
  "handledAt": "2026-04-23T13:40:00Z"
}
```

### `adminEmergencyIssues`

> 기존 운영 기록 조회용 legacy 컬렉션이다. 현재 seed와 관리자 앱은 새 문서를 만들거나 상태를 변경하지 않는다.

```json
{
  "requestId": "request-doc-id",
  "statusCode": "MONITORING",
  "note": "현장 연락 유지 중",
  "handledByName": "운영 관리자",
  "handledAt": "2026-04-23T13:45:00Z"
}
```

### `adminActionNotifications`

> 아래 `EMERGENCY` 예시는 legacy 조회 호환용이다. 관리자 카드는 이 소스의 읽음·해결·재오픈 액션을 노출하지 않는다.

```json
{
  "sourceType": "EMERGENCY",
  "level": "WARNING",
  "requestId": "request-doc-id",
  "title": "긴급 이슈 확인 필요",
  "body": "관리자 확인이 필요한 긴급 후속 알림입니다.",
  "state": "unread",
  "priority": "immediate",
  "filterKeys": ["unread", "unresolved"],
  "createdAt": "2026-04-23T13:45:00Z"
}
```

### `adminAuditLogs`

```json
{
  "sourceType": "SETTLEMENT",
  "requestId": "request-doc-id",
  "actionSummary": "정산 후속 확인 저장",
  "note": "사용자 문의 확인 후 완료 처리",
  "actorName": "운영 관리자",
  "createdAt": "2026-04-23T13:50:00Z"
}
```

### `adminActionDeliveries`

> `EMERGENCY` 전달 기록은 기존 데이터 조회용으로만 유지하며 신규 전달·푸시를 생성하지 않는다.

```json
{
  "notificationId": "notification-doc-id",
  "sourceType": "EMERGENCY",
  "trigger": "notification_created",
  "channel": "operations_feed",
  "status": "confirmed",
  "state": "delivered",
  "priority": "monitoring",
  "filterKeys": ["completed"],
  "slaStatus": "completed",
  "attemptCount": 1,
  "maxAttemptCount": 1,
  "requestId": "request-doc-id",
  "title": "긴급 이슈 확인 필요",
  "body": "관리자 운영 피드에 노출",
  "note": "후속 알림 전달 기록",
  "confirmedAt": 1776951901000,
  "slaDueAt": 1776951901000,
  "createdAt": "2026-04-23T13:45:00Z",
  "processedAt": "2026-04-23T13:45:01Z"
}
```

### `adminActionDeliveryJobs`

> `EMERGENCY` 작업 예시는 legacy 조회 호환용이며 신규 작업은 생성하지 않는다. 과거 `PENDING`·`FAILED` 작업이 남아 있으면 Functions가 작업 또는 연결 전달 기록의 `sourceType`을 확인해 provider 호출 전에 job을 `SKIPPED`(`skipReason=legacy_emergency_disabled`)로 종료하고, 연결 전달 기록도 `status=skipped`로 맞춘다.

```json
{
  "deliveryId": "delivery-doc-id",
  "notificationId": "notification-doc-id",
  "sourceType": "EMERGENCY",
  "trigger": "notification_created",
  "channel": "app_push",
  "recipientRole": "ADMIN",
  "recipientUserIds": [],
  "messagePreview": "긴급 이슈 확인 필요 - 관리자 확인이 필요한 긴급 후속 알림입니다.",
  "state": "PENDING",
  "deliveryAttempts": 0,
  "maxAttempts": 3,
  "lastDeliverySource": "",
  "lastError": "",
  "queuedAt": "2026-04-24T09:15:00Z",
  "updatedAt": "2026-04-24T09:15:00Z"
}
```

## Legacy D-7 / D-3 / D-1 알림 구조

아래 Functions는 Firestore `appointmentRequests`를 읽는다. PostgreSQL에만 있는 새 예약의 알림이 이 경로로 자동 생성된다고 간주하지 않는다.

현재는 `Cloud Functions`가 세 단계로 동작한다.

> 보호자 발송 안전 경계: Firebase Admin SDK는 Firestore·Storage Rules를 우회한다. 기존 Functions에는 PostgreSQL 예약별 동의를 발송 직전에 확인할 최소권한 연결이 없으므로 채팅·위치·예약 리마인더의 보호자 발송은 fail-closed로 중단한다. 환자·매니저 발송만 유지하며, 보호자 알림은 Core API 동의 판정을 사용하는 dispatcher를 별도로 구현한 뒤 다시 연다.

1. 매일 오전 9시에 `appointmentRequests`를 읽고 `appointmentReminderJobs` 작업 문서를 생성
2. 10분마다 `appointmentReminderJobs`를 읽고 재검증 후 발송 또는 시뮬레이션 처리
3. `appointmentRequests`가 취소 / 삭제 / 일정 변경되면 남아 있는 알림 작업을 즉시 `SKIPPED` 처리

### `appointmentReminderJobs`

```json
{
  "appointmentRequestId": "request-doc-id",
  "reminderStage": "D3",
  "templateKey": "appointment_d3",
  "channel": "KAKAO_ALIMTALK",
  "state": "PENDING",
  "reminderDateKey": "2026-04-19",
  "appointmentDateKey": "2026-04-22",
  "recipientUserIds": ["patient-uid"],
  "messagePreview": "서울안과병원 안과 예약이 3일 남았습니다. 보호자 연락처, 만남 장소, 이동 경로를 다시 확인해 주세요."
}
```

### 2026-06-19 `clientSupportRequests`

환자와 보호자의 문의 접수는 매니저 전용 `supportInquiries`와 분리해 `clientSupportRequests` 컬렉션으로 저장한다.

```json
{
  "userId": "patient-or-guardian-uid",
  "userName": "이용자 이름",
  "userRole": "PATIENT",
  "appointmentRequestId": "request-doc-id",
  "category": "progress",
  "title": "현재 진행 상태 문의",
  "body": "실시간 위치 갱신 시각을 다시 확인하고 싶습니다.",
  "status": "RECEIVED",
  "createdAt": "2026-06-19T14:20:00Z",
  "responseText": "",
  "respondedAt": null,
  "respondedByName": ""
}
```

- 클라이언트 읽기: 본인(`userId`)만 허용. 관리자 업무는 서버 인가 경유
- 생성: 환자/보호자 본인만 허용
- 클라이언트 수정/삭제: 거부. 관리자 업무는 서버 인가 경유

현재 단계에서는 작업 문서 생성, 큐 처리, 실제 발송 또는 시뮬레이션 기록, 예약 변경 시 정리까지 구현되어 있다.

### 상태 전이

- `PENDING`: 발송 대기
- `PROCESSING`: 워커가 선점한 상태
- `SENT`: 실제 발송 완료
- `SIMULATED`: 연동값이 없어 데모 발송으로 처리
- `SKIPPED`: 일정 변경, 취소, 수신 번호 없음 등으로 건너뜀
- `FAILED`: 발송 오류, 재시도 대상

### 환경 변수

실제 발송을 붙이려면 Functions 환경 변수에 아래 값을 넣는다.

```properties
KAKAO_ALIMTALK_ENDPOINT=https://your-provider.example.com/messages
KAKAO_ALIMTALK_API_KEY=발급받은_API_KEY
KAKAO_ALIMTALK_SENDER_KEY=발급받은_발신프로필키
KAKAO_ALIMTALK_AUTH_SCHEME=Bearer
```

- 값이 모두 없으면 알림은 `SIMULATED` 상태로 처리된다.
- 현재 payload는 공통 JSON 어댑터 형태라, 실제 대행사 스펙에 맞춰 필드명만 마지막에 조정하면 된다.

## 관리자 후속 알림 푸시 큐

관리자 후속 알림 푸시는 `adminActionDeliveryJobs` 큐를 통해 처리한다.

1. 앱이 `adminActionNotifications`, `adminActionDeliveries`, `adminActionDeliveryJobs`를 함께 저장
2. `deliverAdminActionDeliveryJobs`가 5분마다 `PENDING`, `FAILED` 작업을 선점
3. 연동값이 없으면 `SIMULATED`, 있으면 `SENT`, 수신 관리자 없음이면 `SKIPPED`, 오류면 `FAILED`
4. Functions가 작업 결과를 다시 `adminActionDeliveries`에 반영
5. 읽음 처리 시점에는 앱이 별도 `confirmed` 전달 기록을 남겨 SLA를 종료

### 환경 변수

```properties
ADMIN_PUSH_ENDPOINT=https://your-provider.example.com/admin-push
ADMIN_PUSH_API_KEY=발급받은_API_KEY
ADMIN_PUSH_AUTH_SCHEME=Bearer
```

- 값이 없으면 관리자 푸시는 `SIMULATED`로 처리되고 전달 기록 메모에 시뮬레이션 문구를 남긴다.
- 현재 payload는 `title`, `body`, `recipients[]`, `metadata` 공통 JSON 어댑터 형태다.

## 과거 Firestore 예약 연동 순서

다음은 legacy 알림 경로를 설명하는 기록이다. 현재 Android 예약은 Core API를 호출하며 Firestore 예약 직접 쓰기는 Rules에서 거부한다.

1. 앱에서 예약 생성
2. `REQUESTED` 상태에서는 앱에서 같은 요청을 수정하거나 취소 가능
3. `MATCHED` 상태에서는 수정은 막고 취소만 허용
4. `MATCHED` 요청을 취소하면 연결된 `companionSessions.currentStatus`도 `CANCELED`로 정리
5. Firestore에 `appointmentRequests` 저장 또는 수정
6. 매일 오전 9시 `syncAppointmentReminderJobs` 실행
7. 조건에 맞는 요청의 `appointmentReminderJobs` 생성
8. 10분마다 `deliverAppointmentReminderJobs`가 큐를 읽어 재검증 후 발송
9. 예약이 바뀌면 `cleanupAppointmentReminderJobs`가 기존 대기 작업을 정리
10. 관리자 계정은 `dispatchAppointmentReminderJobs` callable로 수동 발송도 가능

## Legacy 예약 알림 점검 항목

격리된 legacy fixture를 별도 승인해 검증할 때만 사용한다. 현재 Core 예약 검증은 [내부 테스트 가이드](../internal-test-guide.md)를 따른다.

1. `google-services.json`이 `app/` 아래에 있는지 확인
2. 이메일 로그인과 현재 활성화된 Google/Kakao 로그인 키를 로컬에 입력
3. Firestore Rules와 Indexes 배포
4. 환자, 보호자, 매니저 계정 생성
5. 예약 생성 후 `appointmentAtEpochMillis`, `appointmentDateKey`가 저장되는지 확인
6. Functions 배포 후 `appointmentReminderJobs`가 생성되는지 확인
7. 연동값이 없으면 작업이 `SIMULATED`로 바뀌는지 확인
8. 연동값이 있으면 `SENT` 또는 `FAILED`로 기록되는지 확인
9. 예약을 취소하거나 시간을 바꾸면 기존 대기 작업이 `SKIPPED`로 바뀌는지 확인
10. `REQUESTED` 요청 카드에서는 수정 / 취소, `MATCHED` 요청 카드에서는 취소 버튼만 보이는지 확인
11. `MATCHED` 요청을 취소하면 연결된 세션이 `CANCELED`로 바뀌고 매니저가 다시 가용 상태로 보이는지 확인

## 데모 계정

환경별 검증 계정은 비공개로 전달한다. 과거 seed의 고정 기본값을 현재 로그인 정보로 안내하거나 실제 운영 계정에 재사용하지 않는다.

## 개발용 기준선 초기화

Firebase 전용 절차이며 PostgreSQL 업무 데이터를 초기화하지 않는다. 대상·백업·dry-run 확인과 명시적 apply 승인 없이 실행하지 않는다.

- Firestore를 비우고 `users`, `hospitalGuides`만 기준선으로 다시 맞추는 절차는 [reset-baseline.md](reset-baseline.md)에 정리했다.
- 실행 스크립트는 [reset-firestore-baseline.js](../../../tools/firebase/reset-firestore-baseline.js)이며, `tools/firebase` 폴더에서 `npm run reset:baseline:dry-run`, `npm run reset:baseline:apply`로 사용할 수 있다.
- 기준선만으로 화면 검증이 어려울 때는 [seed-sample-service-data.js](../../../tools/firebase/seed-sample-service-data.js)로 `npm run seed:sample:dry-run`, `npm run seed:sample:apply`를 실행해 예약/세션/후속 처리 샘플을 함께 주입할 수 있다.
- 샘플 seed는 MVP에서 제외한 SOS 상태와 `adminEmergencyIssues`를 새로 만들지 않는다. 기존 legacy 문서는 삭제하지 않으며 운영 리포트에서 선택적으로 읽는다.
- 이 스크립트는 배포 대상인 `functions/`가 아니라 운영 도구 디렉터리에서 관리한다.
- 이 스크립트는 `Firebase Authentication`은 삭제하지 않고, 기준선 Auth 계정을 확인한 뒤 기존 Auth UID에 맞춰 `users/{uid}` 문서를 다시 만든다.
- 백업 구조 점검과 현재 상태 diff가 필요할 때는 `npm run validate:backup -- --file ...`, `npm run diff:state -- --file ...`로 관리 대상 컬렉션 변화를 비교할 수 있다.
- 샘플 데이터를 넣은 뒤 역할별 화면 진입 가능 여부는 `npm run check:readiness`로, 전체 상태를 HTML로 남길 때는 `npm run report:ops -- --file ...`로 확인할 수 있다.
- 점검부터 리포트 생성까지 한 번에 실행하려면 `npm run workflow:ops -- --file ...`를 사용하면 된다.
- Firebase 점검과 Android 빌드/테스트까지 한 번에 확인하는 로컬 프리플라이트는 `npm run preflight:local -- --file ...`로 실행할 수 있다.
- 실제 앱 화면을 운영 리포트에 붙이려면 `npm run capture:app -- --screen-id ... --title ...` 또는 `npm run capture:app -- --preset manager-home`처럼 증적 파일을 만든 뒤 `--app-evidence` 옵션으로 `report:ops`, `workflow:ops`, `preflight:local`에 전달한다.
- CI에서는 `npm run preflight:ci` 또는 [.github/workflows/android-preflight.yml](../../../.github/workflows/android-preflight.yml)로 같은 점검 루틴을 재사용한다. Firebase 운영 점검을 요구하지 않으면 Android 빌드/테스트만 수행한다.
- GitHub Actions의 Firebase 운영 점검은 사용자 refresh token이 아니라 GitHub OIDC와 Google Cloud WIF로 전용 서비스 계정을 가장한다.
- `google-github-actions/auth`가 실행마다 30분짜리 OAuth access token을 만들고, [firebase-toolkit.js](../../../tools/firebase/lib/firebase-toolkit.js)는 `GOOGLE_OAUTH_ACCESS_TOKEN`을 우선 사용한다.
- WIF provider는 `bodeul110/bodeul-platform`, 저장소 ID, `master`, `android-preflight.yml`, `workflow_dispatch`를 모두 만족하는 토큰만 허용한다.
- 전용 서비스 계정은 개발 프로젝트의 Firestore 읽기, Firebase Auth 읽기와 API 사용 권한만 갖는다.
- GitHub Actions 설정은 [configure-actions-firebase.js](../../../tools/github/configure-actions-firebase.js)로 반영한다. 이 도구는 사용자 토큰을 올리지 않고 WIF provider·서비스 계정 변수와 정적 Firebase 설정만 관리한다.
- 실제 `workflow_dispatch`까지 성공시키려면 `.github/workflows/android-preflight.yml`이 원격 기본 브랜치에도 있어야 한다.
- 운영 도구 전체 목록은 [tools.md](tools.md)에 정리했다.

## 2026-05-05 내부 테스트 빠른 시작 메모

- 기획/내부 QA용 계정, 더미 데이터, 역할별 테스트 순서는 [내부 테스트 가이드](../internal-test-guide.md)를 기준으로 본다.
- 다음은 영향 범위를 확인하는 dry-run 예시다. 실제 재설정이 필요하면 대상·백업·승인을 확인한 뒤 각 apply를 따로 실행한다.

```powershell
cd D:\BoDeul\tools\firebase
npm run reset:baseline:dry-run
npm run seed:sample:dry-run
npm run seed:manager-docs:dry-run
```

- `check:state`, `check:readiness`, `preflight:local` 같은 운영 점검 명령은 `firebaseOauthClientSecret` 또는 `FIREBASE_OAUTH_CLIENT_SECRET` 설정이 없으면 실행되지 않는다.

## 2026-05-04 관리자 서류 Storage 설정 메모

- 관리자 웹은 Firebase Storage 버킷 `bodeul-dev.firebasestorage.app`을 사용한다.
- 서류 원본 기본 경로 규약은 `manager-documents/{managerUserId}/{documentKey}/{fileName}` 이다.
- 신규 `documentKey`는 `license`, `nursingLicense`만 사용하고 한 제출에는 정확히 1종만 둔다.
- `healthCertificate`는 실제 간호사 면허의 legacy key이므로 신규 쓰기를 막고 `nursingLicense`로 이관한다. `idCard`, `criminalRecord`는 신규 수집하지 않는다.
- 관리자 웹은 `license`, `nursingLicense`만 현재 자격 증빙으로 읽고 legacy key는 이관 확인에만 사용한다.
- [storage.rules](../../../storage.rules) 기준 권한은 아래와 같다.
  - 관리자 브라우저: 직접 읽기 불가. 관리자 서버가 세부 역할·사유·감사를 확인한 뒤 인라인으로 중계
  - 매니저 본인: 본인 경로 읽기/쓰기 가능
  - 그 외 사용자: 접근 불가
- 매니저 증빙 업로드 형식은 `image/jpeg`, `image/png`, `image/webp`만 허용한다. 관리자 웹이 격리된 PDF 렌더러를 제공하기 전까지 매니저 증빙 PDF 신규 제출은 차단하며, 기존 PDF는 이미지로 다시 제출한다.
- 업로드 최대 크기는 `10MB`다.
- [firebase.json](../../../firebase.json)에 `storage.rules` 연결을 추가했으므로, 실제 프로젝트 반영 시에는 `firebase deploy --only storage`로 별도 배포해야 한다.
- `users/{uid}.managerDocumentFiles` 메타데이터가 있으면 관리자 웹이 해당 `fullPath`를 우선 사용하고, 메타데이터가 없으면 위 폴더 규약으로 파일을 탐색한다.
## 2026-05-04 매니저 앱 서류 업로드 연동 메모

- 매니저 앱은 `ManagerProfileActivity`에서 SAF `OpenDocument`로 JPEG, PNG, WebP 이미지를 선택하고, `FirebaseManagerDocumentStorageUploader`가 `manager-documents/{managerUserId}/{documentKey}/{timestamp-fileName}` 경로로 업로드한다.
- 업로드 직후 `FirebaseManagerRepository.saveManagerDocumentFileMetadata()`가 `users/{uid}` 문서에 `managerDocumentFiles`, `managerDocumentFilePaths`, 레거시 경로 필드를 함께 저장한다.
- Storage 업로드만 성공하고 Firestore 메타데이터 저장이 실패할 수 있으므로, 운영 점검 시에는 `users/{uid}.managerDocumentFiles`와 실제 Storage 경로가 같이 있는지 확인하는 절차가 필요하다.
- 요약 없는 최초 파일 초안은 `NOT_SUBMITTED`를 유지한다. 이미 제출·심사된 자료를 교체하면 `PENDING_REVIEW`로 전환되고 Functions가 새 `SUBMITTED` 이력을 남기므로, 관리자 웹은 새 제출 버전을 기존 심사 대기 목록에서 확인한다.
## 2026-05-04 매니저 서류 Storage 점검 메모

- 운영 도구 [check-manager-document-storage.js](../../../tools/firebase/check-manager-document-storage.js)를 추가해 `users/{uid}.managerDocumentFiles`와 `manager-documents/` 실제 Storage 객체의 일치 여부를 점검할 수 있게 했다.
- 기본 명령은 `cd D:\BoDeul\tools\firebase && npm run check:manager-storage` 이고, 결과 JSON은 `tools/firebase/reports/manager-document-storage-check-YYYYMMDD-HHMMSS.json`에 남긴다.
- `--strict` 옵션으로 누락 객체/경로 불일치를 실패 조건으로 둘 수 있다.
- 고아 파일 정리는 `npm run cleanup:manager-storage:dry-run` -> `npm run cleanup:manager-storage:apply` 순서로 실행한다.
- 실제 삭제는 `--apply`가 있어야만 수행되고, 누락 객체나 경로 불일치가 있으면 기본적으로 차단한다.
- 정말 예외적으로 강제 삭제가 필요할 때만 `--delete-orphans --apply --force`를 수동으로 사용한다.
- `seed-manager-document-storage-sample.js`로 `manager@bodeul.app`의 canonical 자격 증빙 이미지 1종을 Storage와 Firestore에 함께 올려 실제 관리자 웹 미리보기 데이터를 검증할 수 있다.
- Firebase 운영 도구의 Firestore 부분 업데이트는 [firebase-toolkit.js](../../../tools/firebase/lib/firebase-toolkit.js)에서 `updateMask.fieldPaths`를 함께 붙여 문서 전체 덮어쓰기를 피한다.

## 2026-05-04 App Check 1단계 메모

- 현재 단계는 `클라이언트 App Check 토큰 발급 준비`와 `Functions enforcement 전환 스위치 추가`까지 반영한 상태다.
- 아직 Firebase Console enforcement를 바로 켜지 않은 이유는 Android 앱, 관리자 웹, 개발용 디버그 토큰을 먼저 안정화해야 하기 때문이다.
- 2026-06-25 기준 세부 전환 순서와 롤백 기준은 [App Check 적용 로드맵](../app-check-enforcement-roadmap.md)을 기준으로 한다.

### Android 앱

- [BodeulApplication.java](../../../app/src/main/java/com/example/bodeul/BodeulApplication.java) 시작 시 App Check를 설치한다.
- `debug` 변형:
  - [app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java](../../../app/src/debug/java/com/example/bodeul/firebase/AppCheckInstaller.java)
  - Debug provider 사용
- `release` 변형:
  - [app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java](../../../app/src/release/java/com/example/bodeul/firebase/AppCheckInstaller.java)
  - Play Integrity provider 사용
- Firebase Console에서 Android 앱을 App Check 대상으로 등록한 뒤, 디버그 실행 시 logcat에 출력되는 debug token을 allowlist에 등록해야 한다.
- Core API 요청 전 `FirebaseAppCheck.getAppCheckToken(false)`를 호출하고, 발급된 token은 URL이 아니라 `X-Firebase-AppCheck` 헤더로 보낸다.
- token 발급 실패 시 observe 단계에서는 헤더를 생략해 기존 fallback을 유지한다. 서버 enforce는 실기기 `valid` 관측 후에만 전환한다.

### 관리자 웹

- 관리자 App Check 코드는 별도 `bodeul-admin-web` 저장소가 소유한다.
- production 전에는 reCAPTCHA Enterprise provider, Preview의 `VALID` 요청과 custom backend 검증을 확인한다.
- 개발 debug token과 provider 설정을 production으로 복사하지 않는다.

### Functions callable

- [functions/index.js](../../../functions/index.js)의 callable 함수들은 `CALLABLE_FUNCTIONS_OPTIONS`를 공통 사용한다.
- `ENABLE_APPCHECK_ENFORCEMENT=true` 환경 변수로만 `enforceAppCheck`를 켜게 했다.
- 즉 지금 배포해도 기본값은 기존과 동일하고, 클라이언트 준비가 끝나면 환경 변수만으로 enforcement 전환이 가능하다.
- 이 값은 변경 뒤 Functions 재배포가 필요하다. 환경 파일에는 다른 비밀값이 섞일 수 있으므로 Git에 커밋하지 않는다.

### Spring Core API

- `BODEUL_APP_CHECK_MODE=off|observe|enforce`로 custom backend 검증 단계를 전환한다. 기본값은 `off`, Cloud Run preview는 `observe`다.
- Java Admin SDK 9.10.0에는 App Check token 검증 API가 없어 Spring Security JWT decoder로 공식 JWKS, RS256, `typ=JWT`, issuer, 만료, audience, app ID를 검증한다.
- `FIREBASE_PROJECT_NUMBER`는 issuer와 audience 고정에 사용하며 비밀값이 아니다.
- observe 로그에는 `app_check_verdict`, 검증된 `app_id`, 요청 경로만 남기고 token 원문은 남기지 않는다.
- Firebase ID token과 PostgreSQL role 인가는 App Check와 별도로 계속 적용한다.

### 2026-06-19 관리자 문의 화면 메모

- 관리자 화면은 `supportInquiries`와 `clientSupportRequests`를 함께 읽어 최신 문의 현황을 한 번에 보여준다.
- 이용자 문의 응답도 Firestore에 바로 저장되며, 환자/보호자 문의와 매니저 문의를 같은 운영 흐름에서 추적한다.

## 카카오 병원/약국 실좌표 검색 메모
- 카카오 모빌리티 기본 SDK만으로는 병원/약국 키워드의 실좌표 검색을 안정적으로 처리하기 어렵다.
- Debug 빌드는 저장소에 기록된 개발 Core API Preview 주소를 기본으로 사용한다. 다른 서버를 사용할 때만 `local.properties`에서 덮어쓴다.

```properties
bodeulCoreApiBaseUrl=http://10.0.2.2:8080
```

- Android는 Firebase ID token과 발급된 App Check token으로 `GET /api/places/search`를 호출하고, Core API가 Kakao Local REST API key를 사용한다.
- REST API key는 Google Secret Manager에만 저장하고 저장소, APK, 로그에 넣지 않는다.
- 조회 결과는 Core API에서 6시간 캐시하며 앱도 예약 진행 화면의 좌표 결과를 6시간 재사용한다.
- Core API 주소가 없거나 검색에 실패하면 `hospitalGuides`와 직접 입력 fallback을 사용한다.
- Android에는 `kakaoRestApiKey`를 두지 않는다. Core API 실패 시 로컬 병원 목록 또는 기본 지도 안내를 사용한다.

## 안심 채팅 첨부 제한
- 허용 형식: 파일 시그니처와 MIME이 일치하는 PDF, JPEG, PNG
- 최대 크기: `10MB`
- Storage 경로: `companion-chat-attachments/{sessionId}/{clientMessageId}/{index-sha256.ext}`
- 첨부 메타데이터 원본: PostgreSQL `bodeul.companion_chat_attachments`
- Android 업로드·다운로드: Core API 서버 중계. 클라이언트가 Storage 객체 URL을 직접 사용하지 않는다.
- 보호자 권한 판정: 예약별 PostgreSQL `CHAT`과 `ATTACHMENT` 동의를 모두 확인한다.
- Storage Rules의 legacy 직접 접근은 관계만 있는 보호자에게 허용하지 않는다. 환자·매니저·관리자 기존 경계도 운영 앱의 주 경로로 사용하지 않는다.
