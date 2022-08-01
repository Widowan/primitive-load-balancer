plugins {
    id("java")
    application
}

group = "dev.wido"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.microhttp:microhttp:0.8")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>() {
    options.compilerArgs.add("-Xlint:all")
}

application {
    mainClass.set("dev.wido.Main")
}
