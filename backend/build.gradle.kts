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
    // 내 악보 불러오기(SHT-07)의 PDF 쪽 수·암호·손상 검사. Apache-2.0.
    implementation(libs.pdfbox)

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

// Bundle DATA-owned profiles, not independently maintained backend note ranges.
tasks.processResources {
    from("../data/src/playback/profiles") { into("catalog/playback-profiles") }
    from("../data/src/scores/score_data.schema.json") { into("catalog") }
}
