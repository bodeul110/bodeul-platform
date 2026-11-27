#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'configure-retention-preview-boundaries.ps1'
$claims = @(
    "assertion.repository_id=='1209358990'"
    "assertion.repository_owner_id=='275679915'"
    "assertion.repository=='bodeul110/bodeul-platform'"
    "assertion.ref=='refs/heads/dev'"
    "assertion.event_name=='workflow_dispatch'"
    "assertion.workflow_ref=='bodeul110/bodeul-platform/.github/workflows/firebase-rules.yml@refs/heads/dev'"
    "assertion.environment=='firebase-retention-preview'"
)
$validProvider = @{
    state='ACTIVE'; disabled=$true
    oidc=@{issuerUri='https://token.actions.githubusercontent.com'}
    attributeMapping=@{'google.subject'="'bodeul-retention-dev-419:' + assertion.repository_id"}
    attributeCondition=($claims -join ' && ')
}
$retentionIamFixture = @{roles=@{}; policies=@{}; changes=@(); provider=$validProvider; calls=0}

# gcloud 명령은 메모리 안의 개발 리소스로만 처리한다.
function gcloud {
    $global:LASTEXITCODE = 0
    $retentionIamFixture.calls++
    $parts = @($args | Where-Object { $_ -notlike '--*' })
    if ($args -cnotcontains '--account=fixture@example.invalid' -or $args -cnotcontains '--project=bodeul-dev') {
        throw '테스트의 고정 대상 밖으로 요청했습니다.'
    }
    $key = $parts -join ' '
    if ($key -eq 'projects describe bodeul-dev') {
        $result = @{projectNumber='533563500316'}
    } elseif ($key -like 'iam workload-identity-pools providers describe *') {
        $result = $retentionIamFixture.provider
    } elseif ($key -like 'iam service-accounts describe *') {
        $result = @{projectId='bodeul-dev'; email=$parts[-1]}
    } elseif ($key -like 'artifacts repositories describe *') {
        $result = @{name='projects/bodeul-dev/locations/asia-northeast3/repositories/bodeul-retention-dev'; format='DOCKER'}
    } elseif ($key -like '* get-iam-policy *') {
        $target = $parts[-1]
        if (-not $retentionIamFixture.policies.ContainsKey($target)) { $retentionIamFixture.policies[$target]=@{bindings=@()} }
        $result = $retentionIamFixture.policies[$target]
    } elseif ($key -eq 'iam roles list') {
        $result = @($retentionIamFixture.roles.Values)
    } elseif ($key -like 'iam roles describe *') {
        $result = $retentionIamFixture.roles[$parts[-1]]
    } elseif ($key -like 'iam roles create *') {
        $id = $parts[-1]
        $perms = ($args | Where-Object { $_ -like '--permissions=*' }) -replace '^--permissions=', ''
        $result = @{name="projects/bodeul-dev/roles/$id"; stage='GA'; includedPermissions=@($perms -split ',')}
        $retentionIamFixture.roles[$id]=$result
        $retentionIamFixture.changes += $key
    } elseif ($key -like '* add-iam-policy-binding *') {
        $target = $parts[-1]
        $member = ($args | Where-Object { $_ -like '--member=*' }) -replace '^--member=', ''
        $role = ($args | Where-Object { $_ -like '--role=*' }) -replace '^--role=', ''
        if ($member -notmatch '^(serviceAccount:bodeul-retention-dev-|principal://)' -or
            $role -notin @('roles/iam.serviceAccountUser','roles/iam.workloadIdentityUser','roles/artifactregistry.writer')) {
            throw '승인 범위를 벗어난 IAM 바인딩입니다.'
        }
        $retentionIamFixture.policies[$target].bindings += @{role=$role; members=@($member)}
        $retentionIamFixture.changes += $key
        $result = $retentionIamFixture.policies[$target]
    } else { throw "예상하지 않은 호출: $key" }
    ConvertTo-Json -InputObject $result -Compress -Depth 8
}

function Assert-TrustRejected ([hashtable] $Provider, [string] $Case) {
    $retentionIamFixture.provider = $Provider
    $before = $retentionIamFixture.calls
    $rejected = $false
    try { $null = & $scriptPath @common }
    catch { $rejected = $_.Exception.Message -like '*WIF 신뢰 설정*' }
    finally { $retentionIamFixture.provider = $validProvider }
    if (-not $rejected -or $retentionIamFixture.calls -ne ($before + 2) -or $retentionIamFixture.changes.Count -ne 0) {
        throw "프로젝트와 공급자 조회 직후 변경 없이 중단하지 않았습니다: $Case"
    }
}

$null = & $scriptPath
if ($retentionIamFixture.calls -ne 0) { throw '기본 실행은 클라우드 호출을 하면 안 됩니다.' }
$common = @{Account='fixture@example.invalid'; Apply=$true; ConfirmProject='bodeul-dev'}
$bad = $validProvider.Clone()
$bad.Remove('attributeCondition')
Assert-TrustRejected $bad '조건 없음'
foreach ($claim in $claims) {
    $bad = $validProvider.Clone()
    $bad.attributeCondition = ($claims | Where-Object { $_ -cne $claim }) -join ' && '
    Assert-TrustRejected $bad "필수 조건 누락: $claim"
    $bad.attributeCondition = $validProvider.attributeCondition.Replace($claim, $claim.Replace("=='", "=='other-"))
    Assert-TrustRejected $bad "필수 조건 값 변경: $claim"
}
$bad = $validProvider.Clone()
$bad.attributeCondition += ' || true'
Assert-TrustRejected $bad '허용 범위를 넓히는 조건 추가'
$bad = $validProvider.Clone()
$bad.oidc = @{issuerUri='https://fixture.invalid'}
Assert-TrustRejected $bad '다른 발급자'
$bad = $validProvider.Clone()
$bad.attributeMapping = @{'google.subject'='assertion.sub'}
Assert-TrustRejected $bad '다른 주체 매핑'

$null = & $scriptPath @common
if ($retentionIamFixture.changes.Count -ne 6 -or $retentionIamFixture.roles.Count -ne 2 -or $retentionIamFixture.policies.Count -ne 4) { throw '최초 설정 범위가 다릅니다.' }
$null = & $scriptPath @common
if ($retentionIamFixture.changes.Count -ne 6) { throw '재실행에서 권한을 중복 부여했습니다.' }
$retentionIamFixture.roles['bodeulRetentionDevStage'].includedPermissions += 'unexpected.permission'
$failed = $false
try { $null = & $scriptPath @common } catch { $failed = $_.Exception.Message -like '*덮어쓰지 않습니다*' }
if (-not $failed -or $retentionIamFixture.changes.Count -ne 6) { throw '기존 맞춤 역할 변경을 거부하지 않았습니다.' }
$retentionIamFixture.provider.disabled = $false
$failed = $false
try { $null = & $scriptPath @common } catch { $failed = $_.Exception.Message -like '*비활성*' }
if (-not $failed -or $retentionIamFixture.changes.Count -ne 6) { throw '활성화된 WIF에서 설정을 계속했습니다.' }
Write-Output 'IAM 계획·최초 설정·재실행·기존 역할 충돌·활성 공급자 차단 및 WIF 신뢰 조건 거부 18건 통과'
