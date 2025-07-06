val h2_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val mongo_version: String by project
val postgres_version: String by project
val koin_version: String by project
val ktor_version: String by project
val koin_annotations_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    id("io.ktor.plugin") version "2.3.12"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10"
}

group = "com.trill.message"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

repositories {
    mavenCentral()
    maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencies {
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-websockets:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("io.ktor:ktor-server-openapi:$ktor_version")
    implementation("io.ktor:ktor-server-call-logging:$ktor_version")
    implementation("io.ktor:ktor-server-swagger:$ktor_version")

    //bcrypt for password hashing
    implementation("org.mindrot:jbcrypt:0.4")

    // JWt dependencies
    implementation("io.ktor:ktor-server-auth:$ktor_version")
    implementation("io.ktor:ktor-server-auth-jwt:$ktor_version")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")

    // Koin for dependency injection
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")

    // MongoDB driver
    implementation("org.mongodb:mongodb-driver-sync:$mongo_version")
    implementation("org.mongodb:mongodb-driver-core:$mongo_version")
    implementation("org.mongodb:bson:$mongo_version")

    // Other dependencies
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")
    implementation("com.ucasoft.ktor:ktor-simple-cache:0.53.4")
    implementation("com.ucasoft.ktor:ktor-simple-redis-cache:0.53.4")

    // Logging
    implementation("ch.qos.logback:logback-classic:$logback_version")

    // Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // Testing
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

configurations.all {
    resolutionStrategy {
        force("io.ktor:ktor-server-core:$ktor_version")
        force("io.ktor:ktor-http:$ktor_version")
        force("io.ktor:ktor-utils:$ktor_version")
        force("io.ktor:ktor-websockets:$ktor_version")
        force("io.ktor:ktor-events:$ktor_version")
        force("io.ktor:ktor-serialization:$ktor_version")
        force("io.ktor:ktor-http-cio:$ktor_version")
        force("io.ktor:ktor-network:$ktor_version")
        force("io.ktor:ktor-io:$ktor_version")
        force("io.ktor:ktor-server-host-common:$ktor_version")
    }
}
