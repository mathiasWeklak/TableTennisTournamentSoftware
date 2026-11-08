

plugins {
    id("java")
    application
    jacoco
}

version = "1.1.0"

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
        attributes["Main-Class"] = "controller.TournamentController"
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

// ---------------------------------------------------------------------------------------------
// Native packages for end users (jpackage): the app plus a minimal Java runtime, no Java needed.
//
//   ./gradlew jpackage                         -> app image for the current system (build/dist)
//   ./gradlew jpackage -PinstallerType=msi     -> Windows installer (needs the WiX Toolset)
//   ./gradlew jpackage -PinstallerType=deb     -> Linux package (also: rpm)
//   ./gradlew jpackage -PinstallerType=dmg     -> macOS disk image
//
// jpackage can only build packages for the system it runs on; see .github/workflows/release.yml.
// ---------------------------------------------------------------------------------------------

val appName = "TT-Turniersoftware"
val jpackageInput = layout.buildDirectory.dir("jpackage/input")
val distDir = layout.buildDirectory.dir("dist")

val prepareJpackageInput by tasks.registering(Copy::class) {
    dependsOn("createExecutableJar")
    from(layout.buildDirectory.file("libs/TableTennisTournamentSoftware.jar"))
    from(licenseFile)
    into(jpackageInput)
}

tasks.register<Exec>("jpackage") {
    group = "distribution"
    description = "Builds a native package with a bundled Java runtime for the current system."
    dependsOn(prepareJpackageInput)
    val launcher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(25) }
    val installerType = (findProperty("installerType") as String?) ?: "app-image"
    val osName = System.getProperty("os.name").lowercase()
    val windows = osName.contains("win")
    val mac = osName.contains("mac")
    // resolved at configuration time: no access to "project" while the task runs
    val iconPath = project.file(when {
        windows -> "packaging/icons/app.ico"
        mac -> "packaging/icons/app.icns"
        else -> "packaging/icons/app.png"
    }).absolutePath
    val appVersion = project.version.toString()
    val licensePath = licenseFile.absolutePath
    val inputPath = jpackageInput.get().asFile.absolutePath
    val destDir = distDir.get().asFile

    doFirst {
        destDir.deleteRecursively() // jpackage refuses to overwrite an existing package
        val jdkBin = launcher.get().metadata.installationPath.dir("bin").asFile
        executable = File(jdkBin, if (windows) "jpackage.exe" else "jpackage").absolutePath
        val arguments = mutableListOf(
            "--type", installerType,
            "--name", appName,
            "--app-version", appVersion,
            "--vendor", appName,
            "--description", "Turniersoftware für Tischtennis: Schweizer System, Jeder gegen Jeden, Gruppen + KO-Runde",
            "--copyright", "CC BY-NC-SA 4.0",
            "--input", inputPath,
            "--main-jar", "TableTennisTournamentSoftware.jar",
            "--main-class", "controller.TournamentController",
            "--icon", iconPath,
            "--dest", destDir.absolutePath,
            "--add-modules", "java.base,java.desktop",
            "--jlink-options", "--strip-debug --no-header-files --no-man-pages --strip-native-commands"
        )
        if (installerType != "app-image") {
            arguments += "--license-file"
            arguments += licensePath
            when {
                windows -> arguments += listOf(
                    "--win-menu", "--win-menu-group", appName, "--win-shortcut", "--win-dir-chooser",
                    // fixed id, so a newer version replaces an installed older one
                    "--win-upgrade-uuid", "6b7c4e2a-3f1d-4c8e-9a5b-2d0f6e8a1c34"
                )
                mac -> arguments += listOf("--mac-package-name", appName)
                else -> arguments += listOf(
                    "--linux-shortcut", "--linux-package-name", "tt-turniersoftware",
                    "--linux-app-category", "Office", "--linux-menu-group", "Office"
                )
            }
        }
        args = arguments
    }
}
