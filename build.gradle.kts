plugins {
    application
}

group = "io.github.phlekies"
version = "0.2.0"

repositories {
    mavenCentral()
}

val javafxVersion = "21.0.12"
val javafxModules = listOf("base", "graphics", "controls")

// JavaFX publica un JAR nativo por plataforma: se elige el del sistema que compila.
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
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial"))
}

application {
    mainClass = "UI.Launcher"
    applicationName = "smart-home-simulator"
}

// `gradlew run` arranca con JavaFX en el module-path, que es la configuración soportada oficialmente.
// Solo se usan los JAR de la plataforma: los JAR "vacíos" sin clasificador duplicarían los módulos.
tasks.named<JavaExec>("run") {
    val javafxJars = configurations.runtimeClasspath.get()
        .filter { it.name.startsWith("javafx-") && it.name.endsWith("-$javafxPlatform.jar") }
    classpath = sourceSets.main.get().runtimeClasspath.filter { !it.name.startsWith("javafx-") }
    jvmArgumentProviders.add(CommandLineArgumentProvider {
        listOf("--module-path", javafxJars.asPath, "--add-modules", "javafx.controls")
    })
}
