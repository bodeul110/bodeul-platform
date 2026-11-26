#requires -Version 5.1
[CmdletBinding()]
param(
    [string] $Account,
    [switch] $Apply,
    [string] $ConfirmProject
)

$ErrorActionPreference = 'Stop'
$project = 'bodeul-dev'
$region = 'asia-northeast3'
$repo = 'bodeul-retention-dev'
$deploy = "$repo-deploy@$project.iam.gserviceaccount.com"
$build = "$repo-build@$project.iam.gserviceaccount.com"
$run = "$repo-run@$project.iam.gserviceaccount.com"
$identity = 'principal://iam.googleapis.com/projects/533563500316/locations/global/workloadIdentityPools/github-actions/subject/bodeul-retention-dev-419:1209358990'
$roles = [ordered]@{
    bodeulRetentionDevStage = @('cloudfunctions.functions.sourceCodeSet', 'cloudfunctions.operations.get')
    bodeulRetentionDevRelease = @('cloudfunctions.functions.get', 'cloudfunctions.functions.update')
}

if (-not $Apply) {
    Write-Output "대상: $project / $region / cleanupExpiredData"
    foreach ($entry in $roles.GetEnumerator()) { Write-Output "역할 정의만 생성: $($entry.Key) = $($entry.Value -join ', ')" }
    Write-Output "WIF 단일 주체 → $deploy / workloadIdentityUser"
    Write-Output "배포 계정 → $build, $run / 각 계정의 serviceAccountUser"
    Write-Output "빌드 계정 → $repo 저장소의 artifactregistry.writer"
    Write-Output '조회용 계획입니다. 역할 정의를 배포자에게 부여하거나 함수·WIF 상태를 바꾸지 않습니다.'
    return
}
if ($ConfirmProject -cne $project) { throw '적용할 때에는 -ConfirmProject bodeul-dev를 입력하세요.' }
if (-not $Account) {
    $Account = Read-Host '이 프로젝트의 기존 IAM 담당자 이메일 (모르면 Enter를 눌러 변경 없이 종료)'
    if ([string]::IsNullOrWhiteSpace($Account)) { Write-Output '변경 없이 종료했습니다.'; return }
}
if ($Account -notmatch '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+$') {
    throw '예시 문구 대신 기존 권한을 보유한 실제 계정 이메일을 입력하세요.'
}

function Invoke-CloudJson ([string[]] $Parts) {
    $raw = & gcloud @Parts "--account=$Account" "--project=$project" --quiet --format=json
    if ($LASTEXITCODE -ne 0) { throw "Google Cloud 요청 실패: $($Parts[0..1] -join ' '). 동일 명령을 재실행하기 전에 원인을 확인하세요." }
    if ($raw) { ConvertFrom-Json -InputObject ($raw -join [Environment]::NewLine) }
}
function Assert-Role ($Role, [string[]] $Expected) {
    if ($Role.deleted -or $Role.stage -cne 'GA' -or
        (($Role.includedPermissions | Sort-Object) -join ',') -cne (($Expected | Sort-Object) -join ',')) {
        throw "기존 역할 정의가 다릅니다. 덮어쓰지 않습니다: $($Role.name)"
    }
}
function Has-Binding ($Policy, [string] $Role, [string] $Member) {
    return @($Policy.bindings | Where-Object { $_.role -ceq $Role -and -not $_.condition -and $_.members -ccontains $Member }).Count -gt 0
}

# 권한을 연결하기 전에 대상 리소스와 접근 가능한 정책을 모두 확인한다.
$p = Invoke-CloudJson @('projects', 'describe', $project)
if ([string]$p.projectNumber -cne '533563500316') { throw '개발 프로젝트 번호가 다릅니다.' }
$provider = Invoke-CloudJson @('iam', 'workload-identity-pools', 'providers', 'describe', $repo, '--location=global', '--workload-identity-pool=github-actions')
if ($provider.disabled -ne $true -or $provider.state -cne 'ACTIVE' -or
    $provider.attributeMapping.'google.subject' -cne "'bodeul-retention-dev-419:' + assertion.repository_id") {
    throw '준비 단계에서는 지정한 WIF가 비활성이고 주체 매핑이 일치해야 합니다.'
}
foreach ($email in @($deploy, $build, $run)) {
    $sa = Invoke-CloudJson @('iam', 'service-accounts', 'describe', $email)
    if ($sa.projectId -cne $project -or $sa.email -cne $email -or $sa.disabled) { throw "전용 계정이 아닙니다: $email" }
}
$ar = Invoke-CloudJson @('artifacts', 'repositories', 'describe', $repo, "--location=$region")
if ($ar.name -cne "projects/$project/locations/$region/repositories/$repo" -or $ar.format -cne 'DOCKER') { throw '전용 Docker 저장소가 아닙니다.' }

$bindings = @(
    @{ Cmd = @('iam', 'service-accounts'); Target = $deploy; Role = 'roles/iam.workloadIdentityUser'; Member = $identity },
    @{ Cmd = @('iam', 'service-accounts'); Target = $build; Role = 'roles/iam.serviceAccountUser'; Member = "serviceAccount:$deploy" },
    @{ Cmd = @('iam', 'service-accounts'); Target = $run; Role = 'roles/iam.serviceAccountUser'; Member = "serviceAccount:$deploy" },
    @{ Cmd = @('artifacts', 'repositories'); Target = $repo; Flags = @("--location=$region"); Role = 'roles/artifactregistry.writer'; Member = "serviceAccount:$build" }
)
foreach ($b in $bindings) {
    $b.Policy = Invoke-CloudJson ($b.Cmd + @('get-iam-policy', $b.Target) + @($b.Flags | Where-Object { $_ }))
}
$allRoles = @(Invoke-CloudJson @('iam', 'roles', 'list', '--show-deleted'))
foreach ($id in $roles.Keys) {
    $present = @($allRoles | Where-Object { $_.name -ceq "projects/$project/roles/$id" })
    if ($present.Count) { Assert-Role (Invoke-CloudJson @('iam', 'roles', 'describe', $id)) $roles[$id] }
}
foreach ($id in $roles.Keys) {
    if (-not ($allRoles | Where-Object { $_.name -ceq "projects/$project/roles/$id" })) {
        $null = Invoke-CloudJson @('iam', 'roles', 'create', $id, "--title=$id", '--stage=GA', "--permissions=$($roles[$id] -join ',')")
    }
    Assert-Role (Invoke-CloudJson @('iam', 'roles', 'describe', $id)) $roles[$id]
}
foreach ($b in $bindings) {
    $flags = @($b.Flags | Where-Object { $_ })
    if (-not (Has-Binding $b.Policy $b.Role $b.Member)) {
        $argsToAdd = $b.Cmd + @('add-iam-policy-binding', $b.Target, "--member=$($b.Member)", "--role=$($b.Role)", '--condition=None') + $flags
        $null = Invoke-CloudJson $argsToAdd
    }
    $actual = Invoke-CloudJson ($b.Cmd + @('get-iam-policy', $b.Target) + $flags)
    if (-not (Has-Binding $actual $b.Role $b.Member)) { throw "설정 후 권한이 확인되지 않습니다: $($b.Target) / $($b.Role)" }
}
Write-Output '역할 정의 2개와 리소스 권한 4건을 확인했습니다. 함수 배포·WIF 활성화·개인 권한 변경은 하지 않았습니다.'
