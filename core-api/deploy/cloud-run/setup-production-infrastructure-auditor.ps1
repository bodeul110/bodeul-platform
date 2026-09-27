[CmdletBinding()]
param(
    [string]$ProjectId = "bodeul-prod-110",
    [Parameter(Mandatory)]
    [string]$ConfirmProjectId,
    [Parameter(Mandatory)]
    [string]$ConfirmProjectNumber,
    [Parameter(Mandatory)]
    [string]$ConfirmEnvironment,
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$OperatorAccount,
    [Parameter(Mandatory)]
    [string]$ConfirmOperatorAccount,
    [Parameter(Mandatory)]
    [ValidateSet("APPLY-PRODUCTION-INFRA-AUDITOR")]
    [string]$ConfirmApply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$ExpectedProjectId = "bodeul-prod-110"
$ProjectNumber = "649312328770"
$EnvironmentName = "production-infrastructure-audit"
$ServiceAccountId = "bodeul-infra-auditor"
$ServiceAccountEmail = "$ServiceAccountId@$ProjectId.iam.gserviceaccount.com"
$PoolId = "github-actions"
$ProviderId = "bodeul-infra-audit-production"
$RoleId = "bodeulProductionInfraAuditor"
$RoleName = "projects/$ProjectId/roles/$RoleId"
$Repository = "bodeul110/bodeul-platform"
$RepositoryId = "1209358990"
$RepositoryOwnerId = "275679915"
$WorkflowRef = "$Repository/.github/workflows/production-infrastructure-audit.yml@refs/heads/master"
$WifSubject = "repo:bodeul110@${RepositoryOwnerId}/bodeul-platform@${RepositoryId}:environment:${EnvironmentName}"
$WifPrincipal = "principal://iam.googleapis.com/projects/$ProjectNumber/locations/global/workloadIdentityPools/$PoolId/subject/$WifSubject"
$IssuerUri = "https://token.actions.githubusercontent.com"
$AttributeMapping = "google.subject=assertion.sub"
$AttributeCondition = "assertion.repository == '$Repository' && assertion.repository_id == '$RepositoryId' && assertion.repository_owner_id == '$RepositoryOwnerId' && assertion.ref == 'refs/heads/master' && assertion.environment == '$EnvironmentName' && assertion.workflow_ref == '$WorkflowRef' && assertion.event_name == 'workflow_dispatch'"
$Permissions = @(
    "resourcemanager.projects.get",
    "resourcemanager.projects.getIamPolicy",
    "serviceusage.services.get",
    "serviceusage.services.use",
    "artifactregistry.repositories.get",
    "artifactregistry.repositories.getIamPolicy",
    "iam.workloadIdentityPools.get",
    "iam.workloadIdentityPoolProviders.get",
    "iam.roles.get",
    "iam.serviceAccounts.get",
    "iam.serviceAccounts.getIamPolicy",
    "iam.serviceAccountKeys.list",
    "secretmanager.secrets.get",
    "secretmanager.secrets.getIamPolicy",
    "secretmanager.versions.get",
    "run.services.get",
    "run.services.getIamPolicy",
    "cloudfunctions.functions.list",
    "datastore.databases.getMetadata",
    "storage.buckets.get",
    "storage.buckets.getIamPolicy",
    "firebase.clients.get",
    "firebase.projects.get",
    "firebaseappcheck.debugTokens.get",
    "firebaseappcheck.playIntegrityConfig.get",
    "firebaseappcheck.recaptchaEnterpriseConfig.get",
    "firebaseappcheck.services.get",
    "firebaseauth.configs.get",
    "monitoring.timeSeries.list",
    "recaptchaenterprise.keys.get"
)

$gcloud = Get-Command gcloud.cmd -ErrorAction SilentlyContinue
if ($null -eq $gcloud) {
    $gcloud = Get-Command gcloud -ErrorAction Stop
}
$script:GcloudExecutable = $gcloud.Source
$script:OperatorAccount = $OperatorAccount

function Invoke-Gcloud {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $script:GcloudExecutable @Arguments "--account=$script:OperatorAccount" 2>$null
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    if ($exitCode -ne 0) {
        $operation = ($Arguments | Select-Object -First 5) -join " "
        throw "gcloud 명령을 완료하지 못했습니다(exit=$exitCode, operation=$operation). 현재 계정 권한과 프로젝트 상태를 확인하세요."
    }
    return @($output)
}

function Get-GcloudResourceState {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $script:GcloudExecutable @Arguments "--account=$script:OperatorAccount" 2>&1
        $exitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    $text = (@($output) | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine
    if ($exitCode -eq 0) {
        return [pscustomobject]@{ Exists = $true; Output = $text }
    }
    if ($text -match '(?i)NOT_FOUND|not found|does not exist') {
        return [pscustomobject]@{ Exists = $false; Output = "" }
    }
    throw "Google Cloud 리소스를 조회하지 못했습니다. 현재 계정 권한을 확인하세요."
}

function ConvertFrom-GcloudJson {
    param(
        [Parameter(Mandatory)]
        [string[]]$Arguments
    )

    $json = (Invoke-Gcloud -Arguments $Arguments) -join [Environment]::NewLine
    if ([string]::IsNullOrWhiteSpace($json)) {
        throw "Google Cloud 리소스 확인 결과가 비어 있습니다."
    }
    return $json | ConvertFrom-Json
}

function Test-ExactStringSet {
    param(
        [Parameter(Mandatory)]
        [string[]]$Actual,
        [Parameter(Mandatory)]
        [string[]]$Expected
    )

    $actualSorted = @($Actual | Sort-Object -Unique)
    $expectedSorted = @($Expected | Sort-Object -Unique)
    $differences = @(Compare-Object -ReferenceObject $expectedSorted -DifferenceObject $actualSorted)
    return $actualSorted.Count -eq $expectedSorted.Count -and $differences.Count -eq 0
}

function Assert-ExactStringSet {
    param(
        [Parameter(Mandatory)]
        [string[]]$Actual,
        [Parameter(Mandatory)]
        [string[]]$Expected,
        [Parameter(Mandatory)]
        [string]$FailureMessage
    )

    if (-not (Test-ExactStringSet -Actual $Actual -Expected $Expected)) {
        throw $FailureMessage
    }
}

if ($ProjectId -ne $ExpectedProjectId -or
    $ConfirmProjectId -ne $ExpectedProjectId -or
    $ConfirmProjectNumber -ne $ProjectNumber -or
    $ConfirmEnvironment -ne $EnvironmentName -or
    $ConfirmOperatorAccount -cne $OperatorAccount -or
    $ConfirmApply -cne "APPLY-PRODUCTION-INFRA-AUDITOR") {
    throw "Production 감사 대상 재입력값이 일치하지 않습니다."
}

$accessToken = (Invoke-Gcloud -Arguments @("auth", "print-access-token", "--quiet")) -join ""
if ([string]::IsNullOrWhiteSpace($accessToken)) {
    throw "지정한 Google Cloud 실행 계정의 access token을 확인하지 못했습니다."
}
$accessToken = $null

$project = ConvertFrom-GcloudJson -Arguments @(
    "projects", "describe", $ProjectId, "--format=json(projectId,projectNumber)", "--quiet"
)
if ($project.projectId -ne $ProjectId -or $project.projectNumber.ToString() -ne $ProjectNumber) {
    throw "gcloud가 확인한 Production 프로젝트가 고정 대상과 일치하지 않습니다."
}

$serviceAccountState = Get-GcloudResourceState -Arguments @(
    "iam", "service-accounts", "describe", $ServiceAccountEmail,
    "--project=$ProjectId", "--format=value(email)", "--quiet"
)
if ($serviceAccountState.Exists) {
    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "update", $ServiceAccountEmail,
        "--project=$ProjectId",
        "--display-name=BoDeul production infrastructure auditor",
        "--description=Metadata-only production infrastructure audit identity",
        "--quiet"
    ) | Out-Null
}
else {
    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "create", $ServiceAccountId,
        "--project=$ProjectId",
        "--display-name=BoDeul production infrastructure auditor",
        "--description=Metadata-only production infrastructure audit identity",
        "--quiet"
    ) | Out-Null
}

$serviceAccount = ConvertFrom-GcloudJson -Arguments @(
    "iam", "service-accounts", "describe", $ServiceAccountEmail,
    "--project=$ProjectId", "--format=json(email,disabled)", "--quiet"
)
$userManagedKeys = ConvertFrom-GcloudJson -Arguments @(
    "iam", "service-accounts", "keys", "list",
    "--iam-account=$ServiceAccountEmail", "--project=$ProjectId",
    "--managed-by=user", "--format=json(name,keyType)", "--quiet"
)
$userManagedKeyCount = if ($null -eq $userManagedKeys) { 0 } else { @($userManagedKeys).Count }
$serviceAccountDisabled = $serviceAccount.PSObject.Properties.Name -contains "disabled" -and
    $serviceAccount.disabled -eq $true
if ($serviceAccount.email -ne $ServiceAccountEmail -or
    $serviceAccountDisabled -or
    $userManagedKeyCount -ne 0) {
    throw "Production 인프라 감사 서비스 계정이 활성 상태 또는 keyless 기준과 일치하지 않습니다."
}

$roleState = Get-GcloudResourceState -Arguments @(
    "iam", "roles", "describe", $RoleId,
    "--project=$ProjectId", "--format=value(name)", "--quiet"
)
$roleArguments = @(
    "--project=$ProjectId",
    "--title=BoDeul Production Infrastructure Auditor",
    "--description=Metadata-only production infrastructure readiness audit",
    "--permissions=$($Permissions -join ',')",
    "--stage=GA",
    "--quiet"
)
if (-not $roleState.Exists) {
    Invoke-Gcloud -Arguments (@("iam", "roles", "create", $RoleId) + $roleArguments) | Out-Null
}

$role = ConvertFrom-GcloudJson -Arguments @(
    "iam", "roles", "describe", $RoleId, "--project=$ProjectId", "--format=json", "--quiet"
)
$rolePermissionsMatch = Test-ExactStringSet -Actual @($role.includedPermissions) -Expected $Permissions
$roleDeleted = $role.PSObject.Properties.Name -contains "deleted" -and $role.deleted -eq $true
$roleNeedsUpdate = $role.name -ne $RoleName -or
    $role.title -ne "BoDeul Production Infrastructure Auditor" -or
    $role.description -ne "Metadata-only production infrastructure readiness audit" -or
    $role.stage -ne "GA" -or
    $roleDeleted -or
    -not $rolePermissionsMatch
if ($roleState.Exists -and $roleNeedsUpdate) {
    Invoke-Gcloud -Arguments (@("iam", "roles", "update", $RoleId) + $roleArguments) | Out-Null
    $role = ConvertFrom-GcloudJson -Arguments @(
        "iam", "roles", "describe", $RoleId, "--project=$ProjectId", "--format=json", "--quiet"
    )
}
$roleDeleted = $role.PSObject.Properties.Name -contains "deleted" -and $role.deleted -eq $true
if ($role.name -ne $RoleName -or $role.stage -ne "GA" -or $roleDeleted) {
    throw "Production 인프라 감사 custom role 상태가 기대값과 일치하지 않습니다."
}
Assert-ExactStringSet -Actual @($role.includedPermissions) -Expected $Permissions `
    -FailureMessage "Production 인프라 감사 custom role 권한이 정확한 allowlist와 일치하지 않습니다."

$poolState = Get-GcloudResourceState -Arguments @(
    "iam", "workload-identity-pools", "describe", $PoolId,
    "--location=global", "--project=$ProjectId", "--format=value(name)", "--quiet"
)
if (-not $poolState.Exists) {
    throw "기존 github-actions Workload Identity Pool을 찾을 수 없습니다."
}
$pool = ConvertFrom-GcloudJson -Arguments @(
    "iam", "workload-identity-pools", "describe", $PoolId,
    "--location=global", "--project=$ProjectId", "--format=json(name,state)", "--quiet"
)
if ($pool.state -ne "ACTIVE") {
    throw "기존 github-actions Workload Identity Pool이 활성 상태가 아닙니다."
}

$providerState = Get-GcloudResourceState -Arguments @(
    "iam", "workload-identity-pools", "providers", "describe", $ProviderId,
    "--workload-identity-pool=$PoolId", "--location=global", "--project=$ProjectId",
    "--format=value(name)", "--quiet"
)
$providerArguments = @(
    "--workload-identity-pool=$PoolId",
    "--location=global",
    "--project=$ProjectId",
    "--issuer-uri=$IssuerUri",
    "--attribute-mapping=$AttributeMapping",
    "--attribute-condition=$AttributeCondition",
    "--display-name=BoDeul production infra audit",
    "--description=GitHub Actions metadata-only production infrastructure audit",
    "--no-disabled",
    "--quiet"
)
if ($providerState.Exists) {
    $existingProvider = ConvertFrom-GcloudJson -Arguments @(
        "iam", "workload-identity-pools", "providers", "describe", $ProviderId,
        "--workload-identity-pool=$PoolId", "--location=global", "--project=$ProjectId",
        "--format=json", "--quiet"
    )
    $existingProviderDisabled = $existingProvider.PSObject.Properties.Name -contains "disabled" -and
        $existingProvider.disabled -eq $true
    $existingAllowedAudienceCount = if ($existingProvider.oidc.PSObject.Properties.Name -contains "allowedAudiences") {
        @($existingProvider.oidc.allowedAudiences).Count
    }
    else {
        0
    }
    $existingMappingCount = @($existingProvider.attributeMapping.PSObject.Properties).Count
    $providerNeedsUpdate = $existingProvider.attributeCondition -ne $AttributeCondition -or
        $existingProvider.state -ne "ACTIVE" -or
        $existingProvider.oidc.issuerUri -ne $IssuerUri -or
        $existingProviderDisabled -or
        $existingAllowedAudienceCount -ne 0 -or
        $existingMappingCount -ne 1 -or
        $existingProvider.attributeMapping.'google.subject' -ne "assertion.sub"
    if ($providerNeedsUpdate) {
        Invoke-Gcloud -Arguments (
            @("iam", "workload-identity-pools", "providers", "update-oidc", $ProviderId) +
            $providerArguments
        ) | Out-Null
    }
}
else {
    Invoke-Gcloud -Arguments (
        @("iam", "workload-identity-pools", "providers", "create-oidc", $ProviderId) +
        $providerArguments
    ) | Out-Null
}

$provider = ConvertFrom-GcloudJson -Arguments @(
    "iam", "workload-identity-pools", "providers", "describe", $ProviderId,
    "--workload-identity-pool=$PoolId", "--location=global", "--project=$ProjectId",
    "--format=json", "--quiet"
)
$providerDisabled = $provider.PSObject.Properties.Name -contains "disabled" -and $provider.disabled -eq $true
$mappingProperties = @($provider.attributeMapping.PSObject.Properties)
$allowedAudiences = if ($provider.oidc.PSObject.Properties.Name -contains "allowedAudiences") {
    @($provider.oidc.allowedAudiences)
}
else {
    @()
}
$allowedAudienceCount = @($allowedAudiences).Count
$mappingPropertyCount = @($mappingProperties).Count
if ($provider.attributeCondition -ne $AttributeCondition -or
    $provider.state -ne "ACTIVE" -or
    $provider.oidc.issuerUri -ne $IssuerUri -or
    $providerDisabled -or
    $allowedAudienceCount -ne 0 -or
    $mappingPropertyCount -ne 1 -or
    $provider.attributeMapping.'google.subject' -ne "assertion.sub") {
    throw "Production 인프라 감사 WIF provider 계약이 기대값과 일치하지 않습니다."
}

$projectPolicy = ConvertFrom-GcloudJson -Arguments @(
    "projects", "get-iam-policy", $ProjectId, "--format=json", "--quiet"
)
$auditProjectBindings = @($projectPolicy.bindings | Where-Object {
    @($_.members) -contains "serviceAccount:$ServiceAccountEmail"
})
$auditProjectBindingCount = @($auditProjectBindings).Count
if ($auditProjectBindingCount -eq 0) {
    Invoke-Gcloud -Arguments @(
        "projects", "add-iam-policy-binding", $ProjectId,
        "--member=serviceAccount:$ServiceAccountEmail",
        "--role=$RoleName",
        "--condition=None",
        "--quiet"
    ) | Out-Null
    $projectPolicy = ConvertFrom-GcloudJson -Arguments @(
        "projects", "get-iam-policy", $ProjectId, "--format=json", "--quiet"
    )
    $auditProjectBindings = @($projectPolicy.bindings | Where-Object {
        @($_.members) -contains "serviceAccount:$ServiceAccountEmail"
    })
    $auditProjectBindingCount = @($auditProjectBindings).Count
}
$auditProjectConditionPresent = $auditProjectBindingCount -eq 1 -and
    $auditProjectBindings[0].PSObject.Properties.Name -contains "condition"
if ($auditProjectBindingCount -ne 1 -or
    $auditProjectBindings[0].role -ne $RoleName -or
    $auditProjectConditionPresent) {
    throw "감사 서비스 계정의 project role이 전용 custom role 하나로 제한되지 않았습니다."
}

$serviceAccountPolicy = ConvertFrom-GcloudJson -Arguments @(
    "iam", "service-accounts", "get-iam-policy", $ServiceAccountEmail,
    "--project=$ProjectId", "--format=json", "--quiet"
)
$serviceAccountBindings = @(@($serviceAccountPolicy.bindings) | Where-Object { $null -ne $_ })
$serviceAccountBindingCount = @($serviceAccountBindings).Count
if ($serviceAccountBindingCount -eq 0) {
    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "add-iam-policy-binding", $ServiceAccountEmail,
        "--project=$ProjectId",
        "--member=$WifPrincipal",
        "--role=roles/iam.workloadIdentityUser",
        "--condition=None",
        "--quiet"
    ) | Out-Null
    $serviceAccountPolicy = ConvertFrom-GcloudJson -Arguments @(
        "iam", "service-accounts", "get-iam-policy", $ServiceAccountEmail,
        "--project=$ProjectId", "--format=json", "--quiet"
    )
    $serviceAccountBindings = @(@($serviceAccountPolicy.bindings) | Where-Object { $null -ne $_ })
    $serviceAccountBindingCount = @($serviceAccountBindings).Count
}
$wifMembers = @()
if ($serviceAccountBindingCount -eq 1) {
    $wifMembers = @($serviceAccountBindings[0].members)
}
$wifMemberCount = @($wifMembers).Count
$wifConditionPresent = $serviceAccountBindingCount -eq 1 -and
    $serviceAccountBindings[0].PSObject.Properties.Name -contains "condition"
if ($serviceAccountBindingCount -ne 1 -or
    $serviceAccountBindings[0].role -ne "roles/iam.workloadIdentityUser" -or
    $wifMemberCount -ne 1 -or
    $wifMembers[0] -ne $WifPrincipal -or
    $wifConditionPresent) {
    throw "감사 서비스 계정 impersonation이 단일 exact subject로 제한되지 않았습니다."
}

Write-Host "Production 인프라 metadata-only 감사 ID와 WIF 구성을 완료했습니다."
