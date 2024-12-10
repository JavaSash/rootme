import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
	id("org.springframework.boot") version "3.0.2"
	id("io.spring.dependency-management") version "1.1.4"
	val kotlinVersion = "1.8.22"
	kotlin("jvm") version kotlinVersion
	kotlin("plugin.spring") version kotlinVersion
	kotlin("plugin.jpa") version kotlinVersion
	kotlin("plugin.allopen") version kotlinVersion
}

allOpen {
	annotations("javax.persistence.Entity")
}

group = "ru.stepenko"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility  = JavaVersion.VERSION_17

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.jetbrains.kotlin:kotlin-reflect:1.6.0")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.6.0")
	implementation("io.github.microutils:kotlin-logging-jvm:3.0.4")

	implementation("org.postgresql:postgresql:42.3.1")
	implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.13.3")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

//	implementation("org.springframework.boot:spring-boot-starter-thymeleaf:3.0.4")
//	Logging

//	testImplementation("ch.qos.logback:logback-classic:1.4.4")
//Tests
	testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "17"
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}
