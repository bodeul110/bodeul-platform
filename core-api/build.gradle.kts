plugins {
	java
	id("org.springframework.boot") version "3.5.16"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.bodeul"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.security:spring-security-oauth2-jose")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("com.google.firebase:firebase-admin:9.11.0")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.named<Test>("test") {
	useJUnitPlatform {
		excludeTags("firestore-emulator")
		excludeTags("postgres-booking-approval")
	}
}

tasks.register<Test>("guardianBookingApprovalPostgresTest") {
	group = "verification"
	description = "격리 PostgreSQL에서 보호자 예약 승인 저장·경합·권한을 검증합니다."
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	outputs.upToDateWhen { false }
	useJUnitPlatform {
		includeTags("postgres-booking-approval")
	}
	testLogging {
		events("passed", "failed", "skipped")
	}
	doFirst {
		val url = System.getenv("BOOKING_TEST_DB_URL") ?: ""
		if (!Regex("^jdbc:postgresql://(localhost|127\\.0\\.0\\.1):[0-9]{1,5}/bodeul_guardian_booking_test$").matches(url)) {
			throw GradleException("로컬 bodeul_guardian_booking_test 격리 DB에서만 실행할 수 있습니다.")
		}
	}
}

tasks.register<Test>("firestoreEmulatorTest") {
	group = "verification"
	description = "격리 Firestore Emulator에서 계정 삭제 영향도 aggregation 계약을 검증합니다."
	testClassesDirs = sourceSets["test"].output.classesDirs
	classpath = sourceSets["test"].runtimeClasspath
	outputs.upToDateWhen { false }
	useJUnitPlatform {
		includeTags("firestore-emulator")
	}
	doFirst {
		val emulatorHost = System.getenv("FIRESTORE_EMULATOR_HOST")
			?: throw GradleException("FIRESTORE_EMULATOR_HOST가 설정된 격리 환경에서만 실행할 수 있습니다.")
		val localEmulator = Regex("^(localhost|127\\.0\\.0\\.1):[0-9]{1,5}$")
		if (!localEmulator.matches(emulatorHost)) {
			throw GradleException("Firestore Emulator는 localhost 또는 127.0.0.1만 허용합니다.")
		}
	}
}

tasks.register<JavaExec>("migrateDatabase") {
	group = "application"
	description = "migration profile로 Flyway를 실행한 뒤 종료합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.DatabaseMigrationApplication")
}

tasks.register<JavaExec>("verifyAccountDeletionInventory") {
	group = "verification"
	description = "계정 삭제 영향도 함수와 runtime 최소 권한을 읽기 전용으로 검증합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.AccountDeletionInventoryVerificationApplication")
}

tasks.register<JavaExec>("verifyDatabaseMigrationReadiness") {
	group = "verification"
	description = "운영 DB의 Flyway 버전과 V14 백필 규모를 읽기 전용으로 점검합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.DatabaseMigrationReadinessApplication")
}

tasks.register<JavaExec>("verifyDatabaseConnectionTarget") {
	group = "verification"
	description = "DB 접속 없이 운영 Supabase 연결 대상만 검증합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.DatabaseMigrationReadinessApplication")
	args("--target-only")
}

tasks.register<JavaExec>("applyAppointmentRequestsSeed") {
	group = "application"
	description = "검증된 예약 요청 seed를 migration role로 적용합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.AppointmentRequestsSeedApplication")
}

tasks.register<JavaExec>("applyCompanionSessionSeed") {
	group = "application"
	description = "검증된 동행 세션 seed를 migration role로 적용합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.CompanionSessionSeedApplication")
}

tasks.register<JavaExec>("runRetentionFixture") {
	group = "application"
	description = "preview 자동 파기 리허설용 고정 fixture를 준비, 조회 또는 정리합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.RetentionFixtureApplication")
}

tasks.register<JavaExec>("runGuideDeviceFixture") {
	group = "application"
	description = "preview 가이드 실기기 검증용 고정 fixture를 준비, 조회 또는 정리합니다."
	classpath = sourceSets["main"].runtimeClasspath
	mainClass.set("com.bodeul.core.GuideDeviceFixtureApplication")
}
