import java.util.Properties
import java.net.URI
import groovy.json.JsonSlurper
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.provider.Property
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

plugins {
    alias(libs.plugins.android.application)
}

if (listOf("google-services.json", "src/debug/google-services.json", "src/release/google-services.json")
        .any { file(it).exists() }) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// 저장소에 남기지 않을 로컬 설정은 local.properties를 우선 읽는다.
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

fun localOrGradleProperty(name: String): String {
    val localValue = localProperties.getProperty(name)?.trim().orEmpty()
    if (localValue.isNotEmpty()) {
        return localValue
    }
    return providers.gradleProperty(name).orNull?.trim().orEmpty()
}

fun localGradleOrEnvironmentProperty(name: String, environmentName: String): String {
    return localOrGradleProperty(name)
        .ifEmpty { providers.environmentVariable(environmentName).orNull?.trim().orEmpty() }
}

fun isReleaseArtifactTaskName(taskName: String): Boolean {
    val leafName = taskName.substringAfterLast(':').lowercase()
    if (
        leafName in setOf(
            "assemble",
            "build",
            "builddependents",
            "buildneeded",
            "bundle",
            "validatereleasesigning"
        )
    ) {
        return true
    }

    if (!leafName.contains("release")) {
        return false
    }

    return listOf(
        "assemble",
        "bundle",
        "install",
        "makeapk",
        "package",
        "publish",
        "sign",
        "signing",
        "upload",
        "validate"
    ).any(leafName::startsWith)
}

data class ReleaseSigningSettings(
    val storeFilePath: String,
    val keyAlias: String,
    val storePassword: String,
    val keyPassword: String
) {
    fun missingInputNames(): List<String> = buildList {
        if (storeFilePath.isEmpty()) {
            add("bodeulReleaseStoreFile 또는 BODEUL_RELEASE_STORE_FILE")
        }
        if (keyAlias.isEmpty()) {
            add("bodeulReleaseKeyAlias 또는 BODEUL_RELEASE_KEY_ALIAS")
        }
        if (storePassword.isEmpty()) {
            add("BODEUL_RELEASE_STORE_PASSWORD")
        }
        if (keyPassword.isEmpty()) {
            add("BODEUL_RELEASE_KEY_PASSWORD")
        }
    }
}

abstract class BuildFeaturesAccessor @Inject constructor(
    val buildFeatures: BuildFeatures
)

abstract class VerifyEnvironmentBoundary : DefaultTask() {
    @get:Input abstract val production: Property<Boolean>
    @get:Input abstract val coreApiUrl: Property<String>
    @get:Input abstract val supabaseUrl: Property<String>
    @get:Input abstract val supabaseKey: Property<String>
    @get:InputFile @get:Optional @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val firebaseConfig: RegularFileProperty

    @TaskAction
    fun verify() {
        val release = production.get()
        val project = if (release) "bodeul-prod-110" else "bodeul-dev"
        val number = if (release) "649312328770" else "533563500316"
        val ref = if (release) "aoijbzgozbopsxzrasbb" else "parpdzttloacinyvhwmx"
        val core = try { URI(coreApiUrl.get()) } catch (_: Exception) { null }
        val localHost = core?.host in setOf("localhost", "127.0.0.1", "10.0.2.2")
        val allowedCloudHosts = if (release) {
            setOf("bodeul-core-api-649312328770.asia-northeast1.run.app")
        } else {
            setOf("bodeul-core-api-preview-cyvvxy3kia-an.a.run.app",
                "bodeul-core-api-preview-533563500316.asia-northeast1.run.app")
        }
        val cloud = core?.scheme == "https" && core.host in allowedCloudHosts && core.port == -1
        val local = !release && localHost && core?.scheme in setOf("http", "https")
        if (core == null || core.userInfo != null || core.query != null || core.fragment != null
            || core.path !in setOf("", "/") || (!cloud && !local)) {
            throw GradleException("Core API 주소가 빌드 환경과 일치하지 않습니다.")
        }

        val realtimeUrl = supabaseUrl.get().trimEnd('/')
        val key = supabaseKey.get()
        if (release || realtimeUrl.isNotEmpty() || key.isNotEmpty()) {
            if (realtimeUrl != "https://$ref.supabase.co" || !key.startsWith("sb_publishable_")) {
                throw GradleException("Supabase URL과 publishable key가 빌드 환경과 일치하지 않습니다.")
            }
        }

        if (!firebaseConfig.isPresent) {
            if (release) throw GradleException("운영 Firebase 설정은 app/src/release/google-services.json에 별도로 필요합니다.")
            logger.lifecycle("개발 Firebase 설정이 없어 Mock/CI 빌드로 검증했습니다.")
            return
        }
        val info = try {
            val document = JsonSlurper().parse(firebaseConfig.get().asFile) as Map<*, *>
            document["project_info"] as Map<*, *>
        } catch (_: Exception) {
            throw GradleException("Firebase 설정 파일 형식이 올바르지 않습니다.")
        }
        if (info["project_id"] != project || info["project_number"].toString() != number
            || info["storage_bucket"] !in setOf("$project.firebasestorage.app", "$project.appspot.com")) {
            throw GradleException("Firebase 인증·Storage 설정이 빌드 환경과 일치하지 않습니다.")
        }
        logger.lifecycle("Firebase·Core API·Realtime의 빌드 환경 경계를 확인했습니다.")
    }
}

abstract class VerifyReleaseAppCheckClasspath : DefaultTask() {
    @get:Input
    abstract val rootComponent: Property<ResolvedComponentResult>

    @TaskAction
    fun verifyProviderBoundary() {
        val pending = ArrayDeque<ResolvedComponentResult>()
        val visited = mutableSetOf<ComponentIdentifier>()
        val modules = mutableSetOf<String>()
        val unresolved = mutableListOf<String>()
        pending.add(rootComponent.get())

        while (pending.isNotEmpty()) {
            val component = pending.removeFirst()
            if (!visited.add(component.id)) {
                continue
            }

            (component.id as? ModuleComponentIdentifier)?.let { identifier ->
                modules.add("${identifier.group}:${identifier.module}")
            }

            component.dependencies.forEach { dependency ->
                when (dependency) {
                    is ResolvedDependencyResult -> pending.add(dependency.selected)
                    is UnresolvedDependencyResult -> unresolved.add(dependency.attempted.displayName)
                }
            }
        }

        if (unresolved.isNotEmpty()) {
            throw GradleException(
                "릴리스 런타임 의존성 일부를 해석하지 못했습니다: ${unresolved.distinct().joinToString(", ")}"
            )
        }

        val debugProvider = "com.google.firebase:firebase-appcheck-debug"
        val playIntegrityProvider = "com.google.firebase:firebase-appcheck-playintegrity"
        if (debugProvider in modules) {
            throw GradleException("릴리스 런타임에 Firebase App Check debug provider가 포함되어 있습니다.")
        }
        if (playIntegrityProvider !in modules) {
            throw GradleException("릴리스 런타임에 Firebase App Check Play Integrity provider가 없습니다.")
        }

        logger.lifecycle("릴리스 App Check provider 의존성 경계를 확인했습니다.")
    }
}

val releaseArtifactRequested = gradle.startParameter.taskNames.any(::isReleaseArtifactTaskName)
if (releaseArtifactRequested && gradle.startParameter.excludedTaskNames.isNotEmpty()) {
    throw GradleException("릴리스 산출물 작업에서는 -x 또는 --exclude-task 옵션을 사용할 수 없습니다.")
}
val buildFeatures = objects.newInstance<BuildFeaturesAccessor>().buildFeatures
if (releaseArtifactRequested && buildFeatures.configurationCache.active.get()) {
    throw GradleException(
        "릴리스 서명 비밀값이 Gradle 구성 캐시에 저장되지 않도록 " +
            "릴리스 산출물은 --no-configuration-cache 옵션으로 빌드해야 합니다."
    )
}

// 암호는 추적 파일이나 Gradle 속성으로 받지 않고 릴리스 요청 시 환경변수에서만 읽는다.
val releaseSigningSettings = if (releaseArtifactRequested) {
    ReleaseSigningSettings(
        storeFilePath = localGradleOrEnvironmentProperty(
            "bodeulReleaseStoreFile",
            "BODEUL_RELEASE_STORE_FILE"
        ),
        keyAlias = localGradleOrEnvironmentProperty(
            "bodeulReleaseKeyAlias",
            "BODEUL_RELEASE_KEY_ALIAS"
        ),
        storePassword = providers.environmentVariable("BODEUL_RELEASE_STORE_PASSWORD").orNull.orEmpty(),
        keyPassword = providers.environmentVariable("BODEUL_RELEASE_KEY_PASSWORD").orNull.orEmpty()
    )
} else {
    null
}

val releaseStoreFile = releaseSigningSettings
    ?.takeIf { it.missingInputNames().isEmpty() }
    ?.let { rootProject.file(it.storeFilePath) }
    ?.takeIf { it.isFile }

val kakaoNativeAppKey = localOrGradleProperty("kakaoNativeAppKey")
val bodeulCoreApiBaseUrl = localOrGradleProperty("bodeulCoreApiBaseUrl")
val bodeulCoreApiDebugBaseUrl = localOrGradleProperty("bodeulCoreApiDebugBaseUrl")
val effectiveBodeulCoreApiDebugBaseUrl = bodeulCoreApiBaseUrl.ifEmpty { bodeulCoreApiDebugBaseUrl }
val bodeulCoreApiReleaseBaseUrl = localGradleOrEnvironmentProperty(
    "bodeulCoreApiReleaseBaseUrl", "BODEUL_CORE_API_RELEASE_BASE_URL"
)
check(effectiveBodeulCoreApiDebugBaseUrl.isNotEmpty()) { "Debug Core API 주소가 비어 있습니다." }
val bodeulLegacyManagerLocationEnabled =
    localOrGradleProperty("bodeulLegacyManagerLocationEnabled").equals("true", ignoreCase = true)
val bodeulSupabaseDebugUrl = localOrGradleProperty("bodeulSupabaseDebugUrl")
    .ifEmpty { localOrGradleProperty("bodeulSupabaseUrl") }
val bodeulSupabaseDebugKey = localOrGradleProperty("bodeulSupabaseDebugPublishableKey")
    .ifEmpty { localOrGradleProperty("bodeulSupabasePublishableKey") }
val bodeulSupabaseReleaseUrl = localGradleOrEnvironmentProperty(
    "bodeulSupabaseReleaseUrl", "BODEUL_SUPABASE_RELEASE_URL"
)
val bodeulSupabaseReleaseKey = localGradleOrEnvironmentProperty(
    "bodeulSupabaseReleasePublishableKey", "BODEUL_SUPABASE_RELEASE_PUBLISHABLE_KEY"
)
val naverClientId = localOrGradleProperty("naverClientId")
val naverClientName = localOrGradleProperty("naverClientName")
    .ifEmpty { "보들" }

android {
    namespace = "com.example.bodeul"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.bodeul"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        manifestPlaceholders["kakaoScheme"] = "kakao$kakaoNativeAppKey"
        resValue("string", "kakao_native_app_key", kakaoNativeAppKey)
        resValue("string", "naver_client_id", naverClientId)
        resValue("string", "naver_client_name", naverClientName)
        // 네이버 클라이언트 시크릿은 앱에 포함하지 않고 서버 중계가 준비될 때까지 로그인을 비활성화한다.
        resValue("bool", "naver_login_enabled", "false")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseSigningSettings != null && releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseSigningSettings.storePassword
                keyAlias = releaseSigningSettings.keyAlias
                keyPassword = releaseSigningSettings.keyPassword
            }
        }
    }

    buildTypes {
        debug {
            resValue("string", "bodeul_supabase_url", bodeulSupabaseDebugUrl)
            resValue("string", "bodeul_supabase_publishable_key", bodeulSupabaseDebugKey)
            // 공개 Preview 주소를 기본으로 사용하되 local.properties로 다른 개발 서버를 선택할 수 있다.
            resValue(
                "string",
                "bodeul_core_api_base_url",
                effectiveBodeulCoreApiDebugBaseUrl
            )
            // 기존 매니저 위치 공유는 명시적으로 활성화한 개발 환경에서만 검증한다.
            resValue(
                "bool",
                "bodeul_legacy_manager_location_enabled",
                bodeulLegacyManagerLocationEnabled.toString()
            )
        }
        release {
            // 운영 앱이 개발 서버를 바라보지 않도록 Preview 기본값을 상속하지 않는다.
            resValue("string", "bodeul_core_api_base_url", bodeulCoreApiReleaseBaseUrl)
            resValue("string", "bodeul_supabase_url", bodeulSupabaseReleaseUrl)
            resValue("string", "bodeul_supabase_publishable_key", bodeulSupabaseReleaseKey)
            // 환자 단말 위치 계약이 준비되기 전에는 운영에서 기존 매니저 위치 경로를 강제로 닫는다.
            resValue("bool", "bodeul_legacy_manager_location_enabled", "false")
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        // defaultConfig의 resValue를 사용하므로 명시적으로 활성화한다.
        resValues = true
        // 무통장입금 신규 선택을 디버그 빌드로 제한하는 데 사용한다.
        buildConfig = true
    }

    compileOptions {
        // 현재 빌드 JDK와 맞춰 Java 컴파일 경고를 줄인다.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val verifyDebugEnvironment = tasks.register<VerifyEnvironmentBoundary>("verifyDebugEnvironment") {
    group = "verification"
    production.set(false)
    coreApiUrl.set(effectiveBodeulCoreApiDebugBaseUrl)
    supabaseUrl.set(bodeulSupabaseDebugUrl)
    supabaseKey.set(bodeulSupabaseDebugKey)
    val config = file("src/debug/google-services.json").takeIf { it.isFile }
        ?: file("google-services.json").takeIf { it.isFile }
    if (config != null) firebaseConfig.set(config)
}

val verifyReleaseEnvironment = tasks.register<VerifyEnvironmentBoundary>("verifyReleaseEnvironment") {
    group = "verification"
    production.set(true)
    coreApiUrl.set(bodeulCoreApiReleaseBaseUrl)
    supabaseUrl.set(bodeulSupabaseReleaseUrl)
    supabaseKey.set(bodeulSupabaseReleaseKey)
    val config = file("src/release/google-services.json")
    if (config.isFile) firebaseConfig.set(config)
}

tasks.matching { it.name == "preDebugBuild" }.configureEach { dependsOn(verifyDebugEnvironment) }
tasks.matching { it.name == "preReleaseBuild" }.configureEach { dependsOn(verifyReleaseEnvironment) }

val validateReleaseSigning = tasks.register("validateReleaseSigning") {
    group = "verification"
    description = "릴리스 서명 입력값과 키 저장소 파일을 검증합니다."
    notCompatibleWithConfigurationCache("릴리스 서명 비밀값을 구성 캐시에 저장하지 않습니다.")

    doLast {
        val settings = releaseSigningSettings ?: throw GradleException(
            "릴리스 서명 설정을 구성할 수 없습니다. " +
                "validateReleaseSigning 또는 전체 릴리스 작업 이름을 사용하고 " +
                "--no-configuration-cache 옵션을 지정하세요."
        )
        val missingInputNames = settings.missingInputNames()
        if (missingInputNames.isNotEmpty()) {
            throw GradleException(
                "릴리스 서명 입력값이 누락되었습니다: ${missingInputNames.joinToString(", ")}"
            )
        }

        val configuredStoreFile = rootProject.file(settings.storeFilePath)
        if (!configuredStoreFile.isFile) {
            throw GradleException(
                "릴리스 키 저장소 파일을 찾을 수 없습니다. " +
                    "bodeulReleaseStoreFile 또는 BODEUL_RELEASE_STORE_FILE 값을 확인하세요."
            )
        }
        if (android.signingConfigs.findByName("release") == null) {
            throw GradleException("릴리스 서명 구성이 적용되지 않았습니다. 전체 릴리스 작업 이름으로 다시 실행하세요.")
        }

        logger.lifecycle("릴리스 서명 입력값과 키 저장소 파일을 확인했습니다.")
    }
}

tasks.matching { it.name == "preReleaseBuild" || isReleaseArtifactTaskName(it.name) }
    .configureEach {
        if (name != validateReleaseSigning.name) {
            dependsOn(validateReleaseSigning)
            doFirst {
                if (android.buildTypes.getByName("release").signingConfig == null) {
                    throw GradleException(
                        "릴리스 서명 구성이 없어 산출물을 만들 수 없습니다. " +
                            "전체 릴리스 작업 이름과 --no-configuration-cache 옵션을 사용하세요."
                    )
                }
            }
        }
    }

dependencies {
    implementation(platform(libs.firebase.bom))

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.material)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.functions)
    implementation(libs.firebase.messaging)
    implementation(libs.firebase.storage)
    implementation(libs.googleid)
    implementation(libs.kakao.user)
    implementation(libs.kakao.map)
    implementation(libs.naver.oauth)
    implementation(libs.okhttp)
    debugImplementation(libs.firebase.appcheck.debug)
    testImplementation(libs.junit4)
    testImplementation(libs.json.test)
    testImplementation(libs.arch.core.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}

val verifyReleaseAppCheckClasspath = tasks.register<VerifyReleaseAppCheckClasspath>(
    "verifyReleaseAppCheckClasspath"
) {
    group = "verification"
    description = "릴리스 런타임에 Play Integrity만 포함되고 App Check debug provider가 없는지 확인합니다."
}

configurations.configureEach {
    if (name == "releaseRuntimeClasspath") {
        val releaseRootComponent = incoming.resolutionResult.rootComponent
        verifyReleaseAppCheckClasspath.configure {
            rootComponent.set(releaseRootComponent)
        }
    }
}

tasks.matching { it.name == "preReleaseBuild" }.configureEach {
    dependsOn(verifyReleaseAppCheckClasspath)
}
