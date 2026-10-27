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
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.validation)
    implementation(libs.spring.boot.security)
    implementation(libs.mybatis.spring.boot)
    implementation(libs.spring.boot.flyway)
    runtimeOnly(libs.flyway.mysql)
    runtimeOnly(libs.mysql.connector)
    implementation(libs.kotlin.reflect)
    implementation(libs.jackson.kotlin)

    testImplementation(libs.spring.boot.webmvc.test)
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
    useJUnitPlatform { excludeTags("database") }
}

tasks.register<Test>("databaseTest") {
    description = "Verifies Flyway and MyBatis against a disposable MySQL 8.4 database."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform { includeTags("database") }
    outputs.upToDateWhen { false }
}
