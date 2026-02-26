

plugins {
    id("java")
    application
    jacoco
}

val licenseFile = project.file("LICENSE")

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("controller.TournamentController")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.register<Jar>("createExecutableJar") {
    manifest {
        attributes["Main-Class"] = "controller.TournamentSoftware"
    }

    from(sourceSets.main.get().output) {
        include("/**")
    }
    from(configurations.runtimeClasspath.get().files) {
        into("lib")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("TableTennisTournamentSoftware.jar")
    destinationDirectory.set(file("build/libs"))
}

tasks.getByName("build").dependsOn("createExecutableJar")

tasks.test {
    useJUnitPlatform()
    jvmArgs = listOf("-XX:+EnableDynamicAgentLoading")
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jar {
    from(licenseFile)
}
