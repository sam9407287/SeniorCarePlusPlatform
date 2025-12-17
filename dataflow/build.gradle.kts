plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    application
}

dependencies {
    // Shared Models
    implementation(project(":shared-models"))
    // Apache Beam
    implementation("org.apache.beam:beam-sdks-java-core:2.54.0")
    implementation("org.apache.beam:beam-runners-google-cloud-dataflow-java:2.54.0")
    implementation("org.apache.beam:beam-runners-direct-java:2.54.0")
    implementation("org.apache.beam:beam-sdks-java-io-google-cloud-platform:2.54.0")
    
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    
    // Google Cloud
    implementation("com.google.cloud:google-cloud-pubsub:1.127.0")
    implementation("com.google.cloud:google-cloud-bigquery:2.38.2")
    
    // Redis
    implementation("redis.clients:jedis:5.1.0")
    implementation("org.apache.commons:commons-pool2:2.12.0")
    
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.4.14")
    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    
    // Jackson for JSON
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")
    
    // Testing
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("org.apache.beam:beam-runners-direct-java:2.54.0")
}

application {
    mainClass.set("com.seniorcare.dataflow.MainKt")
}

// 打包為 fat JAR 用於 Dataflow
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.seniorcare.dataflow.MainKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
}

