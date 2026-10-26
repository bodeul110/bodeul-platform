# 인프라 운영 기준선

기준일: 2026-09-27

9월 27일 [개발·운영 분리 실행 기록](../reports/dev-production-separation-2026-09-27.md)을 반영했다. 배포·DB 권한·백업과 최초 관리자 MFA 로그인은 각각 확인한 범위만 뜻하며, 전체 업무 종단 검증이나 출시 승인은 아니다. 조직·명칭 이전의 당시 증거는 [명칭 기준](resource-naming.md)에 보존한다.

## 개발 인프라 기준선

| 범위 | 기준 |
| --- | --- |
| Google Cloud 조직·결제 | 개발·운영 프로젝트 모두 공식 `bodeul.kr` 소속. 공용 `bodeul-billing` 연결·결제 활성 유지. [이전 기록](google-cloud-organization-migration.md) |
| 관리자 웹 | 별도 저장소 Next.js, dev Preview / master Production. 운영 전용 DB 연결과 최초 개인 관리자 MFA 후 대시보드 진입 확인 |
| Core API | Cloud Run `bodeul-core-api-preview` / `bodeul-core-api`, Spring Boot. 양쪽 health 200과 무인증 401 확인 |
| 공용 DB | Supabase Pro, Tokyo `bodeul-db-dev` / `bodeul-db-prod`, 양쪽 V23·실패 0. migration/core/admin/retention 역할 분리 |
| Firebase | `bodeul-dev`, Auth·Storage·Functions·FCM 유지. Firestore Core 업무 문서 client 쓰기 차단, 인증 프로필·지원·매니저 서류 메타데이터 유지 |
| Kakao | Local REST 키는 Secret Manager, 호출은 Core API 뒤에서 수행 |
| production | DB 재개·V23·격리 복원·Realtime 인가·Core API 배포·관리자 DB 연결 완료. 주요 업무·교차 환경 정상 token 거부·MFA/App Check 강제는 남음 |

## 배포 원칙

- 관리자 웹은 `bodeul-admin-web` 저장소와 Vercel이 소유한다.
- Core API와 DB migration은 메인 저장소가 소유한다.
- GitHub Actions는 WIF를 사용하고 장기 서비스 계정 JSON을 만들지 않는다.
- runtime과 migration 자격 증명을 분리한다.
- 기본 브랜치는 두 저장소 모두 `master`다. 기능은 `dev` 대상 squash, `dev → master` 출시는 merge commit이다. 개발 Core는 dev push/수동 배포, 운영 Core와 양쪽 DB migration은 보호된 수동 workflow로 분리한다.
- Preview 성공을 production 완료로 기록하지 않는다.
- 배포 후 health, 무인증 경계, 오류 로그와 비밀값 비노출을 확인한다.

## 변경 전후 점검

| 변경 | 필수 확인 |
| --- | --- |
| Core API | Gradle check, Cloud Run smoke test, DB pool과 Secret Manager 참조 |
| DB migration | preview migration, 검증 SQL, advisor, rollback·소유자 |
| 관리자 서버 | test/lint/Next build/Vite build, Preview 401·403·200 |
| Firebase Rules | emulator 또는 rules test, Android/관리자 영향 |
| App Check | observe 지표, 정상 실기기·웹 요청, rollback |
| source of truth | backfill, row 비교, 쓰기 주체, 장애 복구 |

예약·세션·리포트·후속 처리·채팅·읽음·위치의 Core 데이터 계약은 PostgreSQL 단일 쓰기다. 기존 매니저 위치 경로는 기본 OFF이며 환자 중심 위치 기능은 별도 구현·검증 대상이다. 현재 매칭 배정은 관리자 서버의 admin-only 함수가 담당한다. Firestore Rules는 해당 Core 업무 문서의 클라이언트 쓰기를 차단한다.

## 비밀값

- DB URL, 비밀번호, Firebase token, Kakao REST 키 원문을 소스·문서·로그에 적지 않는다.
- 관리자 Preview DB URL은 Vercel Preview에만 둔다.
- Core API DB URL과 Kakao 키는 Google Secret Manager에 둔다.
- production DB 자격 증명은 개발값과 분리한다. Kakao REST 키만 명시 승인에 따라 공유하며 환경별 Secret Manager 항목에 보관한다. 쿼터·폐기 영향도 공유한다.

## 종료된 자산

Oracle Node preview, 메인 `api/`, 메인 `admin-web/`과 관리자 Firebase Hosting workflow는 과거 전환 검증 자산이다. 현재 배포나 운영 후보로 사용하지 않는다. Git 이력과 보고서만 보존한다. Firebase Hosting site와 관리자 배포 전용 WIF·서비스 계정·GitHub Environment도 2026-07-17에 제거했다.

## 남은 운영 게이트

- 사용할 주소·도메인, 운영자와 실제 출시 일정 확인
- Cloud Run·Vercel production rollback 리허설
- 관리자 웹 App Check·MFA 강제와 복구, 주요 업무별 권한·감사 검증
- production Core의 정상 Firebase 인증·Kakao 호출·개발 token 거부와 실제 Realtime 소켓 검증
- 최신 schema 기준 production 자동 파기·고지·정책 대조 (과거 fixture 결과와 구분)
- 비용·오류율·연결 수 알림 구성

#429의 가용성 차단은 9월 24일 해소 기록으로 종료됐다. 양쪽 DB V23, 운영 Core 배포와 관리자 DB 연결을 남은 준비 작업으로 다시 분류하지 않는다. 실기기 검증은 이번 문서 정리에서 수행하지 않았다.

상세 구조는 [현재 인프라 구성도](../architecture/infra-overview.md)와 [Production 인프라 기본값](production-infrastructure-defaults.md)을 따른다.
