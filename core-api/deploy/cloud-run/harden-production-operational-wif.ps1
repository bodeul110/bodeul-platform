[CmdletBinding()]
param(
    [string]$ProjectId = "bodeul-prod-110",
    [Parameter(Mandatory)]
    [string]$ConfirmProjectId,
    [Parameter(Mandatory)]
    [string]$ConfirmProjectNumber,
    [Parameter(Mandatory)]
    [string]$ConfirmDeployEnvironment,
    [Parameter(Mandatory)]
    [string]$ConfirmBackupEnvironment,
    [Parameter(Mandatory)]
    [ValidateNotNullOrEmpty()]
    [string]$OperatorAccount,
    [Parameter(Mandatory)]
    [string]$ConfirmOperatorAccount,
    [Parameter(Mandatory)]
    [ValidateSet("APPLY-PRODUCTION-OPERATIONAL-WIF-HARDENING")]
    [string]$ConfirmApply
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$OutputEncoding = [System.Text.UTF8Encoding]::new($false)

$ExpectedProjectId = "bodeul-prod-110"
$ProjectNumber = "649312328770"
$PoolId = "github-actions"
$Repository = "bodeul110/bodeul-platform"
$RepositoryId = "1209358990"
$RepositoryOwnerId = "275679915"
$IssuerUri = "https://token.actions.githubusercontent.com"
$DeployEnvironment = "core-api-production"
$BackupEnvironment = "core-api-migration-production"
$AttributeMapping = [ordered]@{
    "google.subject"             = "assertion.sub"
    "attribute.repository"       = "assertion.repository"
    "attribute.repository_owner" = "assertion.repository_owner"
    "attribute.ref"              = "assertion.ref"
    "attribute.environment"      = "assertion.environment"
    "attribute.actor"            = "assertion.actor"
    "attribute.workflow"         = "assertion.workflow"
}
$AttributeMappingArgument = @($AttributeMapping.Keys | ForEach-Object {
    "$_=$($AttributeMapping[$_])"
}) -join ","

function New-ProviderDefinition {
    param(
        [Parameter(Mandatory)]
        [string]$ProviderId,
        [Parameter(Mandatory)]
        [string]$EnvironmentName,
        [Parameter(Mandatory)]
        [string]$WorkflowFile,
        [Parameter(Mandatory)]
        [string]$ServiceAccountId
    )

    $workflowRef = "$Repository/.github/workflows/$WorkflowFile@refs/heads/master"
    $subject = "repo:bodeul110@${RepositoryOwnerId}/bodeul-platform@${RepositoryId}:environment:${EnvironmentName}"
    return [pscustomobject]@{
        ProviderId          = $ProviderId
        EnvironmentName     = $EnvironmentName
        WorkflowRef         = $workflowRef
        ServiceAccountEmail = "$ServiceAccountId@$ProjectId.iam.gserviceaccount.com"
        AttributeCondition  = "assertion.repository == '$Repository' && assertion.repository_id == '$RepositoryId' && assertion.repository_owner_id == '$RepositoryOwnerId' && assertion.ref == 'refs/heads/master' && assertion.environment == '$EnvironmentName' && assertion.workflow_ref == '$workflowRef' && assertion.event_name == 'workflow_dispatch'"
        ExactPrincipal      = "principal://iam.googleapis.com/projects/$ProjectNumber/locations/global/workloadIdentityPools/$PoolId/subject/$subject"
        LegacyPrincipal     = "principalSet://iam.googleapis.com/projects/$ProjectNumber/locations/global/workloadIdentityPools/$PoolId/attribute.environment/$EnvironmentName"
        LegacyExactPrincipal = "principal://iam.googleapis.com/projects/$ProjectNumber/locations/global/workloadIdentityPools/$PoolId/subject/repo:${Repository}:environment:${EnvironmentName}"
    }
}

$ProviderDefinitions = @(
    New-ProviderDefinition `
        -ProviderId "bodeul-core-api-production" `
        -EnvironmentName $DeployEnvironment `
        -WorkflowFile "core-api-production-deploy.yml" `
        -ServiceAccountId "bodeul-core-deployer"
    New-ProviderDefinition `
        -ProviderId "bodeul-db-backup-production" `
        -EnvironmentName $BackupEnvironment `
        -WorkflowFile "postgres-production-backup-restore.yml" `
        -ServiceAccountId "bodeul-db-backup"
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
        $operation = ($Arguments | Select-Object -First 6) -join " "
        throw "gcloud 명령을 완료하지 못했습니다(exit=$exitCode, operation=$operation). 현재 계정 권한과 프로젝트 상태를 확인하세요."
    }
    return @($output)
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

function Test-ExactStringMap {
    param(
        [Parameter(Mandatory)]
        [object]$Actual,
        [Parameter(Mandatory)]
        [System.Collections.IDictionary]$Expected
    )

    $actualProperties = @($Actual.PSObject.Properties)
    if ($actualProperties.Count -ne $Expected.Count) {
        return $false
    }
    foreach ($key in $Expected.Keys) {
        $property = $Actual.PSObject.Properties[$key]
        if ($null -eq $property -or $property.Value.ToString() -cne $Expected[$key].ToString()) {
            return $false
        }
    }
    return $true
}

function Test-BindingHasCondition {
    param(
        [Parameter(Mandatory)]
        [object]$Binding
    )

    if (-not ($Binding.PSObject.Properties.Name -contains "condition")) {
        return $false
    }
    return $null -ne $Binding.condition
}

function Get-ServiceAccountBindings {
    param(
        [Parameter(Mandatory)]
        [string]$ServiceAccountEmail
    )

    $policy = ConvertFrom-GcloudJson -Arguments @(
        "iam", "service-accounts", "get-iam-policy", $ServiceAccountEmail,
        "--project=$ProjectId", "--format=json", "--quiet"
    )
    return @(@($policy.bindings) | Where-Object { $null -ne $_ })
}

function Assert-KnownImpersonationPolicy {
    param(
        [Parameter(Mandatory)]
        [object[]]$Bindings,
        [Parameter(Mandatory)]
        [object]$Definition
    )

    foreach ($binding in $Bindings) {
        if ($binding.role -ne "roles/iam.workloadIdentityUser" -or
            (Test-BindingHasCondition -Binding $binding)) {
            throw "$($Definition.ProviderId) 서비스 계정에 예상하지 않은 impersonation binding이 있습니다. 자동 변경을 중단합니다."
        }
        foreach ($member in @($binding.members)) {
            if ($member -ne $Definition.ExactPrincipal -and
                $member -ne $Definition.LegacyExactPrincipal -and
                $member -ne $Definition.LegacyPrincipal) {
                throw "$($Definition.ProviderId) 서비스 계정에 예상하지 않은 impersonation 주체가 있습니다. 자동 변경을 중단합니다."
            }
        }
    }
}

function Assert-ExactProviderContract {
    param(
        [Parameter(Mandatory)]
        [object]$Provider,
        [Parameter(Mandatory)]
        [object]$Definition
    )

    $providerDisabled = $Provider.PSObject.Properties.Name -contains "disabled" -and
        $Provider.disabled -eq $true
    $allowedAudiences = if ($Provider.oidc.PSObject.Properties.Name -contains "allowedAudiences") {
        @($Provider.oidc.allowedAudiences)
    }
    else {
        @()
    }
    $mappingMatches = Test-ExactStringMap -Actual $Provider.attributeMapping -Expected $AttributeMapping
    if ($Provider.state -ne "ACTIVE" -or
        $providerDisabled -or
        $Provider.oidc.issuerUri -cne $IssuerUri -or
        @($allowedAudiences).Count -ne 0 -or
        $Provider.attributeCondition -cne $Definition.AttributeCondition -or
        -not $mappingMatches) {
        throw "$($Definition.ProviderId) WIF provider 계약이 강화 기준과 정확히 일치하지 않습니다."
    }
}

function Assert-ExactImpersonationPolicy {
    param(
        [Parameter(Mandatory)]
        [object[]]$Bindings,
        [Parameter(Mandatory)]
        [object]$Definition
    )

    if (@($Bindings).Count -ne 1 -or
        $Bindings[0].role -ne "roles/iam.workloadIdentityUser" -or
        (Test-BindingHasCondition -Binding $Bindings[0])) {
        throw "$($Definition.ProviderId) 서비스 계정 impersonation binding이 하나로 제한되지 않았습니다."
    }
    $members = @($Bindings[0].members)
    if (@($members).Count -ne 1 -or $members[0] -cne $Definition.ExactPrincipal) {
        throw "$($Definition.ProviderId) 서비스 계정 impersonation이 exact environment subject 하나로 제한되지 않았습니다."
    }
}

if ($ProjectId -cne $ExpectedProjectId -or
    $ConfirmProjectId -cne $ExpectedProjectId -or
    $ConfirmProjectNumber -cne $ProjectNumber -or
    $ConfirmDeployEnvironment -cne $DeployEnvironment -or
    $ConfirmBackupEnvironment -cne $BackupEnvironment -or
    $ConfirmOperatorAccount -cne $OperatorAccount -or
    $ConfirmApply -cne "APPLY-PRODUCTION-OPERATIONAL-WIF-HARDENING") {
    throw "Production 운영 WIF 강화 대상 재입력값이 일치하지 않습니다."
}

$accessToken = (Invoke-Gcloud -Arguments @("auth", "print-access-token", "--quiet")) -join ""
if ([string]::IsNullOrWhiteSpace($accessToken)) {
    throw "지정한 Google Cloud 실행 계정의 access token을 확인하지 못했습니다."
}
$accessToken = $null

$project = ConvertFrom-GcloudJson -Arguments @(
    "projects", "describe", $ProjectId, "--format=json(projectId,projectNumber)", "--quiet"
)
if ($project.projectId -cne $ExpectedProjectId -or
    $project.projectNumber.ToString() -cne $ProjectNumber) {
    throw "gcloud가 확인한 Production 프로젝트가 고정 대상과 일치하지 않습니다."
}

$pool = ConvertFrom-GcloudJson -Arguments @(
    "iam", "workload-identity-pools", "describe", $PoolId,
    "--location=global", "--project=$ProjectId", "--format=json(name,state)", "--quiet"
)
if ($pool.state -ne "ACTIVE") {
    throw "기존 github-actions Workload Identity Pool이 활성 상태가 아닙니다."
}

$providerSnapshots = @{}
$bindingSnapshots = @{}
foreach ($definition in $ProviderDefinitions) {
    $serviceAccount = ConvertFrom-GcloudJson -Arguments @(
        "iam", "service-accounts", "describe", $definition.ServiceAccountEmail,
        "--project=$ProjectId", "--format=json(email,disabled)", "--quiet"
    )
    $serviceAccountDisabled = $serviceAccount.PSObject.Properties.Name -contains "disabled" -and
        $serviceAccount.disabled -eq $true
    if ($serviceAccount.email -cne $definition.ServiceAccountEmail -or $serviceAccountDisabled) {
        throw "$($definition.ProviderId) 서비스 계정이 없거나 활성 상태가 아닙니다."
    }

    $provider = ConvertFrom-GcloudJson -Arguments @(
        "iam", "workload-identity-pools", "providers", "describe", $definition.ProviderId,
        "--workload-identity-pool=$PoolId", "--location=global", "--project=$ProjectId",
        "--format=json", "--quiet"
    )
    if ($provider.state -ne "ACTIVE") {
        throw "$($definition.ProviderId) WIF provider가 활성 상태가 아니므로 자동 변경을 중단합니다."
    }
    $providerDisabled = $provider.PSObject.Properties.Name -contains "disabled" -and
        $provider.disabled -eq $true
    $allowedAudienceCount = if ($provider.oidc.PSObject.Properties.Name -contains "allowedAudiences") {
        @($provider.oidc.allowedAudiences).Count
    }
    else {
        0
    }
    if ($providerDisabled -or
        $provider.oidc.issuerUri -cne $IssuerUri -or
        $allowedAudienceCount -ne 0) {
        throw "$($definition.ProviderId) WIF provider의 활성 상태, issuer 또는 audience가 기존 운영 계약과 다릅니다. 자동 변경을 중단합니다."
    }
    $providerSnapshots[$definition.ProviderId] = $provider

    $bindings = @(Get-ServiceAccountBindings -ServiceAccountEmail $definition.ServiceAccountEmail)
    Assert-KnownImpersonationPolicy -Bindings $bindings -Definition $definition
    $bindingSnapshots[$definition.ProviderId] = $bindings
}

foreach ($definition in $ProviderDefinitions) {
    $provider = $providerSnapshots[$definition.ProviderId]
    $providerNeedsUpdate = $provider.attributeCondition -cne $definition.AttributeCondition -or
        -not (Test-ExactStringMap -Actual $provider.attributeMapping -Expected $AttributeMapping)
    if ($providerNeedsUpdate) {
        Invoke-Gcloud -Arguments @(
            "iam", "workload-identity-pools", "providers", "update-oidc", $definition.ProviderId,
            "--workload-identity-pool=$PoolId",
            "--location=global",
            "--project=$ProjectId",
            "--attribute-mapping=$AttributeMappingArgument",
            "--attribute-condition=$($definition.AttributeCondition)",
            "--no-disabled",
            "--quiet"
        ) | Out-Null
    }

    $bindings = @($bindingSnapshots[$definition.ProviderId])
    $members = @($bindings | ForEach-Object { @($_.members) })
    if ($members -notcontains $definition.ExactPrincipal) {
        Invoke-Gcloud -Arguments @(
            "iam", "service-accounts", "add-iam-policy-binding", $definition.ServiceAccountEmail,
            "--project=$ProjectId",
            "--member=$($definition.ExactPrincipal)",
            "--role=roles/iam.workloadIdentityUser",
            "--condition=None",
            "--quiet"
        ) | Out-Null
    }
    foreach ($legacyPrincipal in @($definition.LegacyPrincipal, $definition.LegacyExactPrincipal)) {
      if ($members -contains $legacyPrincipal) {
        Invoke-Gcloud -Arguments @(
            "iam", "service-accounts", "remove-iam-policy-binding", $definition.ServiceAccountEmail,
            "--project=$ProjectId",
            "--member=$legacyPrincipal",
            "--role=roles/iam.workloadIdentityUser",
            "--condition=None",
            "--quiet"
        ) | Out-Null
      }
    }

    $verifiedProvider = ConvertFrom-GcloudJson -Arguments @(
        "iam", "workload-identity-pools", "providers", "describe", $definition.ProviderId,
        "--workload-identity-pool=$PoolId", "--location=global", "--project=$ProjectId",
        "--format=json", "--quiet"
    )
    Assert-ExactProviderContract -Provider $verifiedProvider -Definition $definition

    $verifiedBindings = @(Get-ServiceAccountBindings -ServiceAccountEmail $definition.ServiceAccountEmail)
    Assert-ExactImpersonationPolicy -Bindings $verifiedBindings -Definition $definition
}

Write-Host "Production 배포 및 백업 WIF provider와 exact-subject impersonation 강화를 완료했습니다."
