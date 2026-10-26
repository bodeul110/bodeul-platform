# Preview 병원 가이드 V1 전환

기준일: 2026-10-26

## 목적과 범위

개발 DB의 `서울내과병원 / 내과` 가이드 한 건을 코드 없는 7단계 계약(v0)에서
`LEGACY_CORE_*` 코드가 있는 7단계 계약(v1)으로 전환한다. 이 작업은
[이슈 #490](https://github.com/bodeul110/bodeul-platform/issues/490)의 실제 연동 시연 차단 원인을 해소하기 위한 preview 전용 유지보수 작업이다.

- 대상 환경: `dev` 브랜치의 `bodeul-dev` preview DB
- 대상 가이드: `서울내과병원 / 내과` 정확히 한 건
- 보존 항목: 각 단계의 제목, 설명, 추가 필드
- 추가·변경 항목: `code`, `order`, `step_contract_version`, DB trigger가 증가시키는 `revision`과 `updated_at`
- 제외 항목: production DB, 다른 병원 가이드, 기존 동행 세션 snapshot

특정 개발 데이터 변경을 Flyway migration으로 만들면 나중에 production에도 적용될 수 있고,
현재 preview DB에 적용되지 않은 다른 migration까지 함께 실행될 수 있다. 따라서 기존의 보호된
`Core API DB Migration` workflow 안에 별도 maintenance 동작을 두고, 이 동작에서는 Flyway를 실행하지 않는다.

## 안전 조건

workflow와 실행기는 다음 조건을 모두 확인하며 하나라도 다르면 변경 없이 실패한다.

- Git ref가 `refs/heads/dev`이다.
- `target=preview`, 프로젝트 확인값이 `bodeul-dev`이다.
- Environment의 Supabase project ref와 입력값이 개발 ref `parpdzttloacinyvhwmx`와 일치한다.
- 병원·진료과 확인값이 `서울내과병원/내과`이다.
- 대상 행이 정확히 한 건이고 계약 버전이 v0 또는 이미 준비된 v1이다.
- v0은 정확히 7단계이며 제목은 비어 있지 않은 문자열, 설명은 문자열이다.
- v0 제목 순서는 승인된 기존 기준인 `환자 접수 → 접수 등록 → 진료 접수 → 진료 완료 → 수납 처리 → 약국 방문 → 환자 귀가(서비스 종료)`와 정확히 일치한다.
- 기존 `code`가 없고, 기존 `order`가 있다면 배열 위치와 일치한다.
- 단일 트랜잭션과 행 잠금 안에서 revision 및 원본 JSON이 그대로인지 재확인한다.
- 변경 뒤 revision이 1 증가하고 DB의 단계·미디어 v1 검증 함수를 모두 통과한다.
- seed와 다른 fixture 작업을 같은 workflow 실행에 섞지 않는다.

## 실행 순서

PR이 `dev`에 병합된 뒤 먼저 읽기 전용 상태 조회를 실행한다. 이 workflow는
`core-api-migration-preview` GitHub Environment 승인이 있어야 DB secret을 사용할 수 있다.

```powershell
gh workflow run core-api-migration.yml `
  --repo bodeul110/bodeul-platform `
  --ref dev `
  -f target=preview `
  -f confirm_target=preview `
  -f confirm_database_project_ref=parpdzttloacinyvhwmx `
  -f hospital_guide_v1_action=status `
  -f confirm_hospital_guide_v1_project=bodeul-dev `
  -f confirm_hospital_guide_v1_target='서울내과병원/내과'
```

예상 상태는 `contractVersion=0`, `stepCount=7`, `promotable=true`, `ready=false`이다.
대상이 이 형태와 다르면 원인을 확인하기 전에는 `apply`를 실행하지 않는다.

상태가 예상과 일치할 때 같은 입력에서 action만 `apply`로 바꾼다.

```powershell
gh workflow run core-api-migration.yml `
  --repo bodeul110/bodeul-platform `
  --ref dev `
  -f target=preview `
  -f confirm_target=preview `
  -f confirm_database_project_ref=parpdzttloacinyvhwmx `
  -f hospital_guide_v1_action=apply `
  -f confirm_hospital_guide_v1_project=bodeul-dev `
  -f confirm_hospital_guide_v1_target='서울내과병원/내과'
```

첫 적용의 예상 결과는 `affectedRows=1`, `contractVersion=1`, `ready=true`이다.
이미 정확한 v1 상태에서 재실행하면 `affectedRows=0`, `ready=true`로 끝나며 revision을 다시 올리지 않는다.

## 적용 뒤 시연 검증

병원 가이드가 바뀌어도 기존 동행 세션의 snapshot은 의도적으로 바뀌지 않는다. 따라서 적용 뒤에는
반드시 새 예약과 새 배정을 생성한다.

1. 새 환자 예약을 접수한다.
2. 관리자 경로로 새 매니저 배정을 만든다.
3. 신규 세션의 source가 `HOSPITAL_GUIDE_STEP_CODE_V1`이고 계약 버전이 1인지 확인한다.
4. 매니저 앱에서 `canAdvance=true`, `blockedReason=null`인지 확인하고 1단계에서 2단계로 진행한다.
5. 환자 진행 현황이 다음 조회 주기 안에 같은 단계로 반영되는지 확인한다.

## 실패와 복구

사전 조건, 동시 변경, DB 검증 중 하나라도 실패하면 트랜잭션을 rollback한다. 자동 downgrade는 제공하지 않는다.
기존 세션 snapshot은 이 작업의 대상이 아니므로 기존에 막힌 세션은 그대로 남는다. production 적용이 필요해지면
별도 데이터 검토와 승인 절차를 새로 만든다.
