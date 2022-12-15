import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.20"
}

group = "com.helpscout.demo"
version = "0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://s3-us-west-2.amazonaws.com/dynamodb-local/release")
}

dependencies {
    implementation("com.amazonaws:aws-lambda-java-events:3.11.0")
    implementation("com.amazonaws:aws-lambda-java-core:1.2.2")
    implementation("com.amazonaws:aws-java-sdk-dynamodb:1.12.364")
    implementation("com.amazonaws:aws-lambda-java-events-sdk-transformer:3.1.0")

    implementation("org.slf4j:slf4j-simple:2.0.5")
    implementation("io.github.microutils:kotlin-logging:3.0.4")

    implementation("com.fasterxml.jackson:jackson-bom:2.14.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    testImplementation(kotlin("test"))
    testImplementation("org.mockito:mockito-inline:4.8.0")
    testImplementation("org.assertj:assertj-core:3.23.1")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.1")
    testImplementation("org.mockito.kotlin:mockito-kotlin:4.1.0")
    testImplementation("com.amazonaws:DynamoDBLocal:1.15.0")

}

val copyDynamoDbNativeLibs by tasks.creating(Copy::class) {
    from(configurations.testRuntimeClasspath) {
        include("*.dylib")
        include("*.so")
        include("*.dll")
    }
    into("$buildDir/libs")
}

tasks.withType<Test> {
    useJUnitPlatform()
    dependsOn.add(copyDynamoDbNativeLibs)
    doFirst { systemProperty("java.library.path", "$buildDir/libs") }
}


tasks.withType<JavaCompile> {
    sourceCompatibility = "17"
//    Lambda currently supports max Java 11
    targetCompatibility = "11"
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

// build jar with all deps for deployment
tasks.withType<Jar> {
    val dependencies = configurations
        .runtimeClasspath
        .get()
        .map {
            zipTree(it)
        }
    from(dependencies)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
