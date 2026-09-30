plugins {
    application
    jacoco
}

group = "io.github.phlekies"
version = "0.4.0"

repositories {
    mavenCentral()
}

val javafxVersion = "21.0.12"
val javafxModules = listOf("base", "graphics", "controls")

// JavaFX publishes one native JAR per platform: pick the one of the machine running the build.
val javafxPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arm = System.getProperty("os.arch") in setOf("aarch64", "arm64")
    when {
        os.contains("win") -> "win"
        os.contains("mac") -> if (arm) "mac-aarch64" else "mac"
        else -> if (arm) "linux-aarch64" else "linux"
    }
}

dependencies {
    javafxModules.forEach { implementation("org.openjfx:javafx-$it:$javafxVersion:$javafxPlatform") }

    // UI theme and icons. Their own JavaFX dependency is excluded so the version above wins.
    implementation("io.github.mkpaz:atlantafx-base:2.1.0") { exclude(group = "org.openjfx") }
    implementation("org.kordamp.ikonli:ikonli-javafx:12.4.0") { exclude(group = "org.openjfx") }
    implementation("org.kordamp.ikonli:ikonli-material2-pack:12.4.0") { exclude(group = "org.openjfx") }

    // Project files are stored as JSON.
    implementation("tools.jackson.core:jackson-databind:3.2.3")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    // Headless JavaFX platform, so the UI tests run on CI machines without a display.
    testRuntimeOnly("org.testfx:openjfx-monocle:21.0.2") { exclude(group = "org.openjfx") }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
}

application {
    mainClass = "io.github.phlekies.smarthome.Launcher"
    applicationName = "smart-home-simulator"
}

// `gradlew run` starts with JavaFX on the module path, the officially supported setup.
// Only the platform JARs are used: the empty classifier-less JARs would duplicate the modules.
tasks.named<JavaExec>("run") {
    val javafxJars = configurations.runtimeClasspath.get()
        .filter { it.name.startsWith("javafx-") && it.name.endsWith("-$javafxPlatform.jar") }
    classpath = sourceSets.main.get().runtimeClasspath.filter { !it.name.startsWith("javafx-") }
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("--module-path", javafxJars.asPath, "--add-modules", "javafx.controls")
    })
}

tasks.test {
    useJUnitPlatform()
    systemProperty("glass.platform", "Monocle")
    systemProperty("monocle.platform", "Headless")
    systemProperty("prism.order", "sw")
    systemProperty("java.awt.headless", "true")
    testLogging {
        events("failed", "skipped")
        showStandardStreams = false
    }
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = true
        html.required = true
    }
}
