plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

group = "com.ssafy.eolssu"
version = "0.0.1-SNAPSHOT"
description = "Eolssu backend application"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.spring.boot.webmvc)
    implementation(libs.springdoc.webmvc.ui)
    implementation(libs.spring.boot.actuator)
    runtimeOnly(libs.micrometer.prometheus)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.boot.mail)
    implementation(libs.spring.boot.security.oauth2.resource.server)
    // Argon2PasswordEncoder가 쓰는 Argon2 구현
    implementation(libs.bouncycastle.bcprov)
    implementation(libs.mybatis.spring.boot)
    implementation(libs.spring.boot.flyway)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector)
    implementation(libs.kotlin.reflect)
    implementation(libs.caffeine)
    implementation(libs.jackson.kotlin)
    // 녹음 원본 저장소(S3 호환, Cloudflare R2). 동기 클라이언트만 쓰므로 비동기용 Netty는 뺀다.
    implementation(platform(libs.aws.sdk.bom))
    implementation(libs.aws.sdk.s3) { exclude(group = "software.amazon.awssdk", module = "netty-nio-client") }

    testImplementation(libs.spring.boot.webmvc.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.spring.boot.validation.test)
    testImplementation(libs.kotlin.test.junit5)
    testRuntimeOnly(libs.junit.platform.launcher)
}

kotlin {
    compilerOptions {
        javaParameters.set(true)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform { excludeTags("database", "media") }
}

tasks.register<Test>("mediaTest") {
    description = "Verifies recording file checks with real ffmpeg/ffprobe on PATH."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("media") }
    outputs.upToDateWhen { false }
}

tasks.register<Test>("databaseTest") {
    description = "Verifies Flyway and MyBatis against a disposable MySQL 8.4 database."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("database") }
    outputs.upToDateWhen { false }
}

// S15P31E102-499: mission_id를 읽지 않는 코드를 먼저 배포하는 1차 호환 릴리스.
// 운영 반영·세션 생성/조회 확인 뒤 이 블록 전체를 제거하고 원본 V25를 별도 승인한다.
// 테스트 classpath의 V25는 보존하고 Jenkins/Docker가 쓰는 실행 JAR에서만 제외한다.
tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    exclude("**/db/migration/V25__drop_practice_session_mission.sql")
    doFirst {
        val laterMigrations = fileTree("src/main/resources/db/migration") {
            include("V*.sql")
        }.files.filter {
            Regex("V([0-9]+)__.*\\.sql").matchEntire(it.name)
                ?.groupValues?.get(1)?.toBigInteger()?.let { version -> version > 25.toBigInteger() } == true
        }
        check(laterMigrations.isEmpty()) {
            "V25 호환 릴리스에 V26 이상을 포함할 수 없습니다. " +
                "호환 백엔드 운영 반영을 확인한 뒤 임시 bootJar 블록을 제거하세요: " +
                laterMigrations.map { it.name }.sorted().joinToString()
        }
    }
}
