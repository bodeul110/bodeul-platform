#requires -Version 5.1
$ErrorActionPreference = 'Stop'
$scriptPath = Join-Path $PSScriptRoot 'configure-retention-preview-boundaries.ps1'
$retentionIamFixture = @{roles=@{}; policies=@{}; changes=@(); enabled=$false; calls=0}

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
        $result = @{state='ACTIVE'; disabled=(-not $retentionIamFixture.enabled); attributeMapping=@{'google.subject'="'bodeul-retention-dev-419:' + assertion.repository_id"}}
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

$null = & $scriptPath
if ($retentionIamFixture.calls -ne 0) { throw '기본 실행은 클라우드 호출을 하면 안 됩니다.' }
$common = @{Account='fixture@example.invalid'; Apply=$true; ConfirmProject='bodeul-dev'}
$null = & $scriptPath @common
if ($retentionIamFixture.changes.Count -ne 6 -or $retentionIamFixture.roles.Count -ne 2 -or $retentionIamFixture.policies.Count -ne 4) { throw '최초 설정 범위가 다릅니다.' }
$null = & $scriptPath @common
if ($retentionIamFixture.changes.Count -ne 6) { throw '재실행에서 권한을 중복 부여했습니다.' }
$retentionIamFixture.roles['bodeulRetentionDevStage'].includedPermissions += 'unexpected.permission'
$failed = $false
try { $null = & $scriptPath @common } catch { $failed = $_.Exception.Message -like '*덮어쓰지 않습니다*' }
if (-not $failed -or $retentionIamFixture.changes.Count -ne 6) { throw '기존 맞춤 역할 변경을 거부하지 않았습니다.' }
$retentionIamFixture.enabled = $true
$failed = $false
try { $null = & $scriptPath @common } catch { $failed = $_.Exception.Message -like '*비활성*' }
if (-not $failed -or $retentionIamFixture.changes.Count -ne 6) { throw '활성화된 WIF에서 설정을 계속했습니다.' }
Write-Output 'IAM 계획·최초 설정·재실행·기존 역할 충돌·활성 공급자 차단 테스트 통과'
