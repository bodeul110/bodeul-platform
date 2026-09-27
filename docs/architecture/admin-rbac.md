# 관리자 세부 역할과 민감정보 감사 계약

계약 기준일: 2026-08-29. 운영 적용 범위 갱신: 2026-09-27

운영 V23 적용 후 명시 승인된 최초 개인 관리자 1명의 `ADMIN`·활성 `SUPER_ADMIN`, 역할 변경 감사 1건과 TOTP 로그인·대시보드 진입을 확인했다. 최초 bootstrap 이후 계정 추가·회수는 아래 업무 함수와 MFA 경계를 따른다. 전역 MFA 강제 모드와 복구·역할별 업무 검증까지 완료한 것은 아니다. [실행 기록](../reports/dev-production-separation-2026-09-27.md)을 참고한다.

초기에는 빠른 구현을 우선했기 때문에 모든 선택 근거가 사전에 정리되지는 않았다.
현재는 구현된 구조를 기준으로 선택 이유, 대안, 단점, 전환 조건을 정리하고 있다.

## 작업 목적

Firebase와 `app_users.role == ADMIN`만으로 모든 관리자 기능을 허용하던 경계를 업무별 최소권한으로 좁히고, 민감정보 원문 접근과 권한 변경을 PostgreSQL에 추가 전용으로 기록한다.

## 선택한 방식

- Firebase Auth와 `app_users.ADMIN`은 관리자 영역 진입 자격으로만 사용한다.
- 실제 업무 권한은 PostgreSQL `admin_role_assignments`의 `SUPER_ADMIN`, `OPERATIONS`, `DEVELOPER`로 판정한다.
- 역할이 없거나 회수됐으면 관리자 API는 fail-closed로 거부한다.
- 민감정보 원문 조회는 `SUPER_ADMIN`과 `OPERATIONS`만 사유를 남긴 뒤 허용한다.
- 매니저 심사 원본은 `license` 또는 `nursingLicense` 자격 증빙 1종으로 최소화한다. 신분증·범죄경력 원본은 신규 수집하지 않고, legacy `healthCertificate`는 `nursingLicense` 이관 전용으로만 처리한다.
- 별도 다운로드 기능은 `SUPER_ADMIN`이 2인 승인으로 받은 최대 60분 break-glass 권한이 있을 때만 허용한다. 관리자별 미회수 권한은 하나만 유지하며 재발급 시 기존 권한을 먼저 종료하고, 회수는 요청한 활성·미만료 grant ID만 종료한다.
- 조회, 원문 조회, 다운로드, 수정, 삭제와 권한 변경은 `admin_access_audits`에 `ALLOWED`, `DENIED`, `FAILED`로 기록하고 애플리케이션 DB role의 직접 INSERT는 허용하지 않는다. 인증된 관리자의 역할 변경·긴급 접근·매니저 심사 입력 거부와 처리 실패도 비밀값 없이 결과 코드만 기록하며, 이 감사 기록 자체가 실패하면 요청은 fail-closed한다.
- 감사기록은 기존 자동 파기 작업이 1년 경과 후보를 먼저 집계하고 500건 단위로 삭제한다. V20은 실행·월간 보고에 관리자 감사 후보 수와 삭제 수를 추가한다. 배포는 반드시 DB V20을 먼저 적용한 뒤 Functions를 올린다. V20 DB가 기존 20개와 신규 22개 집계 payload를 모두 받으므로 이 순서의 전환 구간은 호환된다. 새 Functions를 구 DB에 먼저 올리는 순서는 새 함수와 22개 키 계약이 없으므로 지원하지 않는다.
- 매니저 배정 DB 함수도 `SUPER_ADMIN` 또는 `OPERATIONS` 세부 역할이 없으면 같은 트랜잭션에서 거부한다.

## 역할별 허용 범위

| 기능 | SUPER_ADMIN | OPERATIONS | DEVELOPER |
| --- | --- | --- | --- |
| 관리자 영역 진입 | 허용 | 허용 | 허용 |
| 예약·매칭·매니저 심사 | 허용 | 허용 | 거부 |
| 마스킹된 운영 정보 | 허용 | 허용 | 거부 |
| 비식별 운영 진단 | 허용 | 제한 | 허용 |
| 민감정보 원문 미리보기 | 사유+감사 | 사유+감사 | 거부 |
| 별도 원본 다운로드 | break-glass 필요 | 거부 | 거부 |
| 역할 부여·회수 | 허용 | 거부 | 거부 |
| 감사 기록 조회 | 허용 | 거부 | 거부 |

## 대안

| 대안 | 판단 |
| --- | --- |
| Firebase `ADMIN` 하나만 유지 | 구현은 단순하지만 개발자와 운영 담당자가 같은 개인정보 권한을 가지므로 제외했다. |
| Firebase custom claims만 사용 | Rules에는 유용하지만 역할 변경 이력, DB 업무 권한과 2인 승인을 한 원본으로 관리하기 어려워 보조 수단으로만 본다. |
| Spring Core API에서 관리자 권한 처리 | 사용자 API와 관리자 배포 경계가 다시 결합되므로 관리자 Next.js 서버가 PostgreSQL을 직접 확인한다. |
| 모든 민감정보 조회를 break-glass로 제한 | 매니저 서류 심사 같은 정상 운영이 지나치게 느려져, 미리보기는 사유·감사로 통제하고 별도 다운로드만 긴급 권한으로 제한한다. |

## 선택 이유

현재 MVP 규모에서도 관리자 계정 탈취나 개발 계정 오사용은 한 번의 사고로 민감 서류 전체에 영향을 줄 수 있다. 별도 권한 서비스는 운영 부담이 크므로 기존 PostgreSQL과 관리자 서버 안에서 세 역할, 짧은 긴급 권한과 추가 전용 감사를 구현하는 것이 현재 규모에 맞다.

## 리스크와 전환 조건

- V20은 기존 ADMIN을 자동으로 세부 역할에 넣지 않는다. 개발 DB에서 최초 `SUPER_ADMIN`을 명시적으로 bootstrap하기 전에는 새 관리자 API가 403을 반환한다.
- 매니저 심사 outbox는 서버가 인증한 관리자 세부 역할을 immutable 작업 payload에 함께 저장한다. DB는 `operation_id`와 64자 `payloadHash`가 있는 `MANAGER_REVIEW`의 `UPDATE / ALLOWED` 감사에서도 actor가 현재 `ADMIN`이고 활성 역할이 `SUPER_ADMIN` 또는 `OPERATIONS`이며, payload의 `actorAdminRole`과 정확히 일치할 때만 기록한다. outbox가 대기하는 동안 역할이 회수되거나 변경되면 자동 전달은 fail-closed로 실패한다. 당시 역할 snapshot만으로 우회 재생하지 않으며, 운영자가 역할 변경 경위와 심사 결과를 확인한 뒤 별도 보정 절차를 결정해야 한다.
- DB 역할과 Firebase Rules 사이에 별도 권한 복제본을 만들지 않는다. Android의 기존 `FirebaseAdminRepository`는 Firebase 연동 모드에서 ADMIN 본인 확인 뒤 대시보드 요청 전에 중단하고 별도 관리자 웹 안내만 표시한다. Mock 데모 모드는 유지한다. 이 차단이 포함된 Android 앱과 이번 Rules는 같은 릴리스 게이트에서 적용하며, 구버전 앱이 남아 있는 동안 Rules만 단독 배포하지 않는다.
- 인라인 미리보기 데이터는 사용자 기기에 도달하므로 완전한 복사 방지는 불가능하다. 사유, 짧은 응답 수명, `no-store`, 감사와 워터마크를 함께 사용한다.
- 자격 증빙 종류 교체는 이전 canonical 원본과 메타데이터 정리가 끝난 뒤 새 revision으로 기록한다. `license`와 `nursingLicense`가 동시에 존재하거나 legacy 경로가 canonical 경로와 불일치하면 심사를 거부한다.
- break-glass 재발급 전 ID는 최신 권한을 가리키지 않는다. 회수 함수는 요청한 ID의 활성·미만료 행만 잠그고 종료하며, 실제 PostgreSQL 검증 SQL은 교체된 이전 ID의 회수가 `false`이고 최신 권한이 유지되는지 확인한다.
- production 최초 역할 배정·MFA와 전용 DB 자격 증명 활성화는 9월 27일 명시 승인 범위에서 완료했다. 추가 운영자·긴급 다운로드 권한을 자동 부여하지 않으며 출시 게이트 #134의 업무별 검증은 유지한다.
- V20 롤백은 migration role의 단일 트랜잭션으로 실행하며, 중간 객체 변경이나 삭제가 실패하면 전체를 되돌린다. 역할 배정, break-glass, 감사 이력이 하나라도 있거나 관리자 감사 파기 집계가 0이 아닌 실행 기록이 있으면 시작 단계에서 중단한다. 실제 롤백은 먼저 감사·권한·집계 증적을 내보내고 Functions를 호환 버전으로 되돌린 뒤, 승인된 별도 정리 절차로 해당 행을 비운 경우에만 실행한다. 롤백 뒤 구 Functions 계약은 기존 20개 키를 받고, 순차 배포 중 남은 22개 키 payload는 관리자 감사 두 값이 모두 0일 때만 받는다. 0이 아닌 값은 저장 열이 없어 유실될 수 있으므로 명시적으로 거부한다.

## 초기 역할 bootstrap

V20 적용 직후 최초 한 번만 migration role로 대상 `app_users.id`를 확인한 뒤 `SUPER_ADMIN` 행을 직접 추가한다. 이메일이나 Firebase UID를 SQL 파일에 고정하지 않는다. 이후 역할 변경과 회수는 `set_admin_role_assignment`, `revoke_admin_role_assignment` 함수만 사용한다.

```sql
begin;
set local role bodeul_migration;

insert into bodeul.admin_role_assignments (
    admin_user_id,
    admin_role,
    granted_by_admin_user_id,
    grant_reason
) values (
    '<검증한-admin-app-user-uuid>'::uuid,
    'SUPER_ADMIN',
    null,
    '개발 환경 최초 최고 관리자 bootstrap'
);

commit;
```

production 최초 bootstrap은 일반 역할 발급과 구분한다. 9월 27일에는 명시적인 최초 등록 승인, 최신 백업, 대상 계정의 이메일 인증·TOTP 등록, rollback 사전 검증을 거쳐 최초 1명만 등록하고 감사를 남겼다. 두 번째 관리자 이후의 권한 변경은 운영 절차를 따르며, 2인 승인 break-glass를 이 예외로 우회하지 않는다. 위 개발용 SQL을 운영에서 그대로 재실행하지 않는다.

## 계정 발급·변경·회수와 MFA

1. 발급: 개인 Firebase Auth 계정을 만들고 MFA 등록을 확인한 뒤 `app_users.role=ADMIN`과 최소 세부 역할을 순서대로 부여한다. 공용 계정은 만들지 않는다.
2. 변경: `SUPER_ADMIN`이 대상과 10자 이상 사유를 확인하고 `set_admin_role_assignment`만 호출한다. 변경 전후 역할은 감사기록에 남는다.
3. 회수: 팀 이탈·계약 종료 당일 `revoke_admin_role_assignment`로 세부 역할과 활성 break-glass를 먼저 회수하고, Firebase refresh token 폐기와 계정 비활성화, `app_users.ADMIN` 제거를 같은 작업으로 처리한다.
4. MFA: Preview에서 모든 관리자 재로그인과 second-factor claim을 확인할 때까지 `ADMIN_MFA_MODE=observe`를 유지한다. 확인 후 Production을 `enforce`로 바꾸며, MFA 미등록 계정은 세부 역할이 있어도 접근을 거부한다.
5. 비상 복구: MFA 기기 분실은 별도 `SUPER_ADMIN`이 본인 확인 후 복구하고, 복구 과정에서 공용 인증수단이나 장기 break-glass를 만들지 않는다.
