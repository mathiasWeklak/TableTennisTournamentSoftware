

plugins {
    id("java")
    application
}

val licenseFile = project.file("LICENSE")

repositories {
    mavenCentral()
}

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.0-M2")
}

application {
    mainClass.set("controller.TournamentController")
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "21"
    targetCompatibility = "21"
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
}

tasks.jar {
    from(licenseFile)
}
