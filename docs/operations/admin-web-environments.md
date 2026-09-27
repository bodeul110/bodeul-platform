# 관리자 웹 환경 기준

기준일: 2026-09-27. 웹 배포·DB 기반 구성과 실제 업무 검증 완료를 구분한다.

## 현재 환경

| 환경 | 용도 | 사이트 상단 표시 | 확인 범위 |
| --- | --- | --- | --- |
| 로컬 개발 서버 | 개발과 단위 검증 | 개발 환경 / 로컬 실행 · Local | 개발자가 비공개 로컬 파일로 설정 주입 |
| Vercel Preview | PR·개발 DB 검증 | 개발 환경 / 미리보기 배포 · Preview | 실제 로그인 화면의 개발 표시 확인. 과거 DB 실연동 결과와 이번 표시 검증은 구분 |
| Vercel Production | 운영 배포 대상 | 운영 환경 / 운영 배포 · Production | 최초 개인 SUPER_ADMIN의 MFA 후 대시보드 진입 확인. 주요 업무 전체 검증은 미완료 |
| 배포 정보 누락·알 수 없는 빌드 | 환경 판별 불가 | 환경 확인 필요 | 운영으로 추정하지 않고 빌드 설정 확인 |

기본 Production 주소는 [관리자 웹](https://bodeul-admin-web-iota.vercel.app/)이다. 환경 표시는 로그인·세션 확인·2차 인증·로그인 후 관리 화면의 상단에 유지한다.

**운영 환경 표시는 웹 빌드의 배포 대상만 뜻한다.** DB 연결 성공, 운영 데이터 사용, 관리자 권한이나 출시 완료를 증명하지 않는다. Vercel Production을 별도의 "공유용 target"과 "실제 production"으로 나누어 설명하지 않고, 같은 배포 대상 안에서 웹 게시와 업무 개방 상태를 구분한다.

두 저장소는 `dev`를 개발, `master`를 운영 브랜치로 사용한다. 관리자 웹의 개발 고정 주소는 [dev Preview](https://bodeul-admin-web-git-dev-bodeul110.vercel.app)이며, 운영 연결 준비가 끝나기 전 개발 산출물을 Production으로 승격하지 않는다. 실제 적용 증거는 [개발·운영 분리 실행 기록](../reports/dev-production-separation-2026-09-27.md)을 따른다.

## 확인된 상태와 남은 검증

| 항목 | 확인 내용 | 아직 완료로 볼 수 없는 범위 |
| --- | --- | --- |
| 웹 배포·환경 표시 | 2026-09-27 웹 PR #77의 dev → master 병합 후 Production `30d3661` 배포 `READY`, Functions `hnd1`. 환경 표시와 MFA 후 대시보드 진입 확인 | 배정·서류 심사·결제 등 주요 업무의 정상·거부·감사 흐름 |
| 운영 Firebase 설정 | 개발과 분리된 프로젝트와 웹 설정을 사용. App Check 클라이언트 활성화·서버 관찰 설정은 반영 | 인증된 요청의 `VALID` 확인, 강제 전환과 rollback |
| 운영 로그인 계정 | 2026-09-27 개인 Auth 계정의 이메일 인증·TOTP 등록·MFA 재로그인 확인. PostgreSQL ADMIN 1건·활성 SUPER_ADMIN 1건·역할 변경 감사 1건 등록 후 사용자가 대시보드 접속 확인 | 전체 관리자 MFA 강제, 추가 운영자·비상 복구와 업무별 권한 검증 |
| 운영 DB | 2026-09-27 Pro의 `bodeul-db-prod` Healthy, 검증된 백업 후 Flyway V23 적용. 관리자 전용 LOGIN·TLS 연결·조회 성공. 연결 제한 5, 기존 runtime role 상속, 테이블 직접 쓰기 권한 0건 확인 | 실제 관리자 인증 후 Vercel 서버의 DB 업무 요청 |
| 운영 DB 연결 설정 | 2026-09-27 새 운영 자격 증명을 Vercel Production `ADMIN_DATABASE_URL`에만 sensitive 형식으로 등록하고 메타데이터 재조회. 개발값 재사용 없음 | 인증·인가·업무 smoke test |
| 운영 HTTP 경계 | access-context·가이드·결제 조회에 무인증·잘못된 형식·가짜 token 9건 모두 401. JSON·no-store·token 비노출 확인 | 정상 서명 token의 환경 간 거부와 관리자 업무 성공 |

9월 21일의 DB 일시정지 기록은 당시 상태이며 현재 상태가 아니다. DB 서비스 로그인과 사람의 관리자 역할은 별도다. 9월 27일 전용 DB 연결을 준비한 뒤 명시 승인된 최초 개인 관리자만 등록했다. 로그인 화면의 선행 Firestore 사용자 조회는 웹 PR #76·#77에서 제거했으며 현재 진입 인가는 Next.js의 Firebase token 검증과 PostgreSQL ADMIN·세부 역할을 따른다.

## 환경변수 경계

| 종류 | 예 | 노출 범위 |
| --- | --- | --- |
| 배포 표시값 | `NEXT_PUBLIC_BODEUL_DEPLOYMENT_ENV` | 빌드 설정이 `VERCEL_ENV`에서 자동 주입. 비밀값이나 연결 정보 없음 |
| 브라우저 공개 설정 | `NEXT_PUBLIC_FIREBASE_*` | 번들에 포함 가능 |
| 서버 설정 | `FIREBASE_PROJECT_ID` | Next.js 서버만 사용 |
| 서버 비밀값 | `ADMIN_DATABASE_URL` | Vercel server runtime만 사용 |

DB URL, 비밀번호, Firebase Admin 자격 증명을 `NEXT_PUBLIC_*` 또는 `VITE_*`로 만들지 않는다. Firebase ID token은 요청 Authorization 헤더로 전달하고 로그에 남기지 않는다.

별도의 표시용 환경변수를 수동 등록하지 않는다. `NODE_ENV=production`은 Preview 최적화 빌드에도 해당하므로 판별 기준으로 쓰지 않는다. 배포 후 별칭만 바꾸면 빌드 시 표시값이 남으므로 대상 환경으로 다시 빌드·배포한다.

## DB 연결 기준

- Supabase transaction pooler 6543 포트를 사용한다.
- 관리자 애플리케이션 pool은 1, DB role connection limit은 5다.
- Supabase Root CA를 명시하고 `rejectUnauthorized`를 유지한다.
- `bodeul_admin_service`는 제한된 조회와 허용된 업무 함수 실행에 사용한다. "조회 전용"은 테이블 직접 쓰기를 허용하지 않는다는 뜻이며, 배정·감사·결제 등 인가된 함수 내부의 쓰기까지 없다는 뜻은 아니다.
- 임시 검증 계정과 row는 승인된 검증 뒤 정리한다. 최초 운영 ADMIN·SUPER_ADMIN과 감사 기록은 실제 운영 주체의 영구 등록이며 테스트 fixture가 아니다.
- PR마다 test, lint, Next.js build, Vite rollback build와 CodeQL을 통과시킨다.
- Preview와 Production은 각각 다른 Supabase 프로젝트·자격 증명을 사용하고, Vercel Functions는 두 DB와 같은 Tokyo `hnd1`에서 실행한다.

## 로그인·권한 확인 순서

1. 접속 주소와 상단의 개발/운영 표시가 의도한 환경인지 확인한다.
2. 해당 Firebase 프로젝트에 등록된 계정으로 로그인한다. 개발 계정이 운영에도 자동 생성되는 것은 아니다.
3. Next.js 서버가 Firebase ID token을 검증한 뒤 PostgreSQL `app_users`의 `ADMIN` 진입 자격과 활성 세부 역할을 확인한다.
4. MFA와 App Check, 역할별 API 인가, 감사 기록을 각각 검증한다. Firebase 계정 등록이나 화면 표시만으로 통과 처리하지 않는다.

## production 출시 전 남은 확인

1. 적용된 V23과 검증된 백업을 기준으로 추가 변경 필요 여부를 확인한다. DB 복원이나 개발 데이터 복사는 자동 수행하지 않는다.
2. 운영 전용 DB 자격 증명 등록과 테이블 직접 쓰기 제한은 확인했다. 실제 인증 요청에서 허용 함수별 인가와 개발 token 거부를 확인한다.
3. 최종 사용할 도메인과 Firebase Auth authorized domain, App Check 허용 도메인을 대조한다. 기본 도메인의 웹 게시 완료와 custom domain 연결은 별개다.
4. 최초 운영 관리자 등록·MFA 로그인과 별도로 전체 MFA 강제·복구, App Check, 업무 감사와 긴급 권한 회수를 검증한다.
5. 무인증·비관리자 거부, 관리자 조회·업무 변경, DB 장애·충돌과 배포 rollback을 검증한다.
6. live 배포·장애 대응 담당과 backup/restore 절차를 확인한다.

개발 Preview의 비밀번호나 프로젝트를 production에 복사하지 않는다. 설정·DB 연결 오류로 요청이 거부되는 것은 준비 미완료 상태이며, 이를 정상 업무 검증으로 기록하지 않는다.

## 배포 책임

관리자 웹 build·Vercel 배포와 웹 전용 환경변수는 `bodeul-admin-web` 저장소가 소유한다. 두 브랜치는 PR, `lint-and-build`, CodeQL, Vercel 체크와 대화 해결을 요구한다. 기능 PR은 `dev`에 squash merge하고, `dev → master` 출시 PR은 merge commit으로 반영해 Production 자동 배포를 진행한다. 메인 저장소는 관리자 Firebase Hosting workflow나 관리자 배포 secret을 소유하지 않는다. 공용 DB migration과 Firebase Rules 변경은 메인 저장소에서 수행하고 관리자 웹 영향 여부를 함께 기록한다.

## 관련 문서

- [환경 표시 구현·검증 기록](../reports/admin-web-environment-display-2026-09-21.md)
- [관리자 권한 QA 체크리스트](admin-access-qa-checklist.md)
- [웹 저장소의 환경 표시 판정](https://github.com/bodeul110/bodeul-admin-web/blob/master/docs/nextjs-admin-server.md#사이트-배포-환경-표시)
- [관리자 웹 구조](../architecture/admin-web-architecture.md)
- [관리자 웹 저장소 분리 기록](admin-web-repository-split.md)
- [목표 인프라 구조](../architecture/target-infrastructure.md)
- [Production 인프라 기본값](production-infrastructure-defaults.md)
