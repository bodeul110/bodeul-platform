# 인프라 개요

기준일: 2026-09-27

## 런타임

| 영역 | 구현 | 배포 |
| --- | --- | --- |
| Android | Java + XML | 로컬·실기기, GitHub Android Preflight |
| 관리자 웹/서버 | 별도 저장소 React + Next.js | Vercel dev Preview·master Production. 운영 DB 연결·최초 관리자 MFA 로그인 확인 |
| 사용자 Core API | Java 21 + Spring Boot | Cloud Run Tokyo 개발·운영 독립 서비스 배포 |
| 공용 DB | PostgreSQL | Supabase Pro Tokyo 개발·운영 별도 프로젝트, 양쪽 V23 |
| 실시간 전달 | Supabase Realtime private Broadcast | 양쪽 Firebase 허용 목록·인가 설정 적용. 운영 실소켓 검증은 별도 |
| 인증·푸시·파일 | Firebase Auth, FCM, Storage | `bodeul-dev`, `bodeul-prod-110` 분리 |
| Firebase 결합 로직 | Functions v2, 예약 파기 작업 | Firebase `asia-northeast3` |

## 요청 경계

- 관리자 요청은 Next.js Route Handler가 인증·인가하고 PostgreSQL에 직접 접근한다.
- 사용자·매니저 요청은 Spring Core API가 인증·인가하고 PostgreSQL에 접근한다.
- 두 서버는 서로를 호출하지 않는다.
- 클라이언트는 PostgreSQL에 직접 연결하지 않는다.
- 클라이언트의 Realtime 구독은 커밋 알림용이며 조회와 명령은 서버 API를 사용한다.
- Firebase ID token은 두 서버에서 검증하고 DB role은 서버별로 분리한다.

## 배포와 비밀값

- 관리자 Preview 비밀값은 Vercel Preview environment에만 둔다.
- Core API DB URL과 Kakao REST 키는 Google Secret Manager에서 Cloud Run에 주입한다.
- GitHub Actions 배포는 장기 JSON key 대신 WIF를 사용한다.
- DB migration 자격 증명은 runtime 서비스에 전달하지 않는다.
- 예약 파기 함수는 Supavisor transaction mode와 `bodeul_retention_service`를 사용하며 Core·관리자 DB 자격 증명을 재사용하지 않는다. DB URL과 Supabase CA는 별도 Secret으로 주입한다.
- 운영 DB·migration·Vercel Production 자격 증명을 분리해 등록했다. Kakao REST 키는 승인된 공유 예외이며 개발·운영 프로젝트의 별도 Secret Manager 항목에 보관한다.

## 저장소 경계

메인 저장소는 Android, Core API, DB migration, Firebase Rules·Functions와 공용 문서를 담당한다. 관리자 UI·Next.js 서버·Vercel 배포는 별도 `bodeul-admin-web` 저장소가 담당한다. 과거 Node API와 메인 저장소 관리자 웹 중복본은 2026-07-17 제거했다.

## 현재 리스크

- 최초 관리자 로그인과 별개인 역할별 업무·감사·MFA 복구 검증
- 양쪽 V23 적용 이후 새 migration의 환경별 적용·백업 추적. [migration 목록](database-migration-catalog.md)과 대상 DB 이력을 대조

- Firestore와 PostgreSQL 병행 도메인의 데이터 불일치
- 자동 파기는 개발 PostgreSQL·Storage·Firestore 격리 fixture 리허설 기록이 있으나 production 적용과 현재 정책·처리방침 대조는 별도임
- 최종 서비스 도메인·추가 운영자·출시 일정 확인
- 관리자 App Check 미강제
- production DB restore는 완료했지만 Cloud Run·Vercel rollback 리허설 미완료
- 역할 동기화와 감사 로그의 확장 필요

상세 흐름은 [현재 인프라 구성도](infra-overview.md), 목표와 전환 조건은 [목표 인프라 구조](target-infrastructure.md), 환경별 적용·미확인 범위는 [9월 27일 실행 기록](../reports/dev-production-separation-2026-09-27.md)을 따른다.
