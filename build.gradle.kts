plugins {
	java
	id("org.springframework.boot") version "4.0.1"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "ru.vibeart"
version = "0.2.1-SNAPSHOT"
description = "REST API backend for VibeArt project"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
    // Базовый Spring и веб-сервер
    implementation("org.springframework.boot:spring-boot-starter-web")
    // JPA/Hibernate ORM
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // Драйвер PostgreSQL
    runtimeOnly("org.postgresql:postgresql")
    // Хранилище Minio (S3)
    implementation("io.minio:minio:9.0.3")
    // Обработка изображений
    implementation("net.coobird:thumbnailator:0.4.21")
    // Обмен сообщениями между сервисами по протоколу AMQP (RabbitMQ)
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    // Безопасность, настройка фильтров и авторизация
    implementation("org.springframework.boot:spring-boot-starter-security")
    // JWT токены
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
    // Валидация входящих данных
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Преобразование DTO и сущностей
    implementation("org.modelmapper:modelmapper:3.2.6")
    // OpenAPI/Swagger
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    // Тесты
	testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
