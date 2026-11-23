import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.security.MessageDigest

/*
 * Windows (desktop) version of School Fee Manager.
 * The screens, business rules, import/export and repository are the SAME source files as the
 * Android app (app/src/main/java); only the platform parts below are desktop-specific:
 *   data/JdbcFeeStore.kt   SQLite through JDBC instead of Room (same schema → backups are interchangeable)
 *   ui/DesktopPlatform.kt  file dialogs, printing via the browser
 *   util/DesktopPdf.kt     report PDFs
 *   desktop/Main.kt        window
 */
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

val appVersion = "1.1.0"
version = appVersion

/** Android-only files of the shared source folder. */
val androidOnly = listOf(
    "com/school/feemanager/MainActivity.kt",
    "com/school/feemanager/FeeApp.kt",
    "com/school/feemanager/data/AppDatabase.kt",
    "com/school/feemanager/data/Migrations.kt",
    "com/school/feemanager/data/RoomFeeStore.kt",
    "com/school/feemanager/ui/AndroidPlatform.kt",
    "com/school/feemanager/util/Printer.kt",
    "com/school/feemanager/util/PdfExporter.kt",
)

sourceSets {
    main {
        kotlin {
            srcDir("../app/src/main/java")
            exclude(androidOnly)
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("org.jetbrains.androidx.navigation:navigation-compose:2.8.0-alpha10")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    // Room annotations only (the DAO interfaces are shared with Android); the database is accessed with JDBC
    implementation("androidx.room:room-common:2.6.1")
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
    testImplementation(compose.uiTest)
}

compose.desktop {
    application {
        mainClass = "com.school.feemanager.desktop.MainKt"
        jvmArgs += listOf("-Xmx1g")
    }
}

tasks.test {
    // the shared tests' schema files and the original workbook
    systemProperty("schemaDir", rootProject.file("app/schemas/com.school.feemanager.data.AppDatabase").absolutePath)
}

// ============================================================================ Windows package
// `./gradlew :desktop:packageWindows` builds, on Linux or Windows (needs NSIS "makensis" on the PATH):
//   build/windows/SchoolFeeManager-<version>-Setup.exe          installer (per user, no admin rights)
//   build/windows/SchoolFeeManager-<version>-Windows-portable.zip  unzip and run SchoolFeeManager.exe
// The Windows Java runtime comes from the jdk4py wheel on PyPI (OpenJDK 25, Windows x64), checked by SHA-256.

// The Windows build of the Skia graphics engine (same version as this machine's, see `gradlew :desktop:dependencies`)
val windowsSkiko by configurations.creating { isTransitive = false }
dependencies { windowsSkiko("org.jetbrains.skiko:skiko-awt-runtime-windows-x64:0.8.18") }

val jdk4pyUrl = "https://files.pythonhosted.org/packages/ca/14/4baef08289d39b90bf31229b194b517c46e4b50dc5037ce54fa23db5fbb6/jdk4py-25.0.5.1-py3-none-win_amd64.whl"
val jdk4pySha256 = "faeb2e188e003a67c0b91a17a00f95ae0cf0e2f0803892d1e44c410eca0a08db"
val runtimeWheel = layout.buildDirectory.file("downloads/jdk4py-25.0.5.1-win_amd64.whl")
val windowsDir = layout.buildDirectory.dir("windows")
val imageDir = layout.buildDirectory.dir("windows/image/SchoolFeeManager")

val downloadWindowsRuntime by tasks.registering {
    description = "Downloads the Windows x64 Java runtime (jdk4py wheel) and verifies its SHA-256."
    outputs.file(runtimeWheel)
    doLast {
        val f = runtimeWheel.get().asFile
        fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        if (f.exists() && sha(f) == jdk4pySha256) return@doLast
        f.parentFile.mkdirs()
        URI(jdk4pyUrl).toURL().openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
        val actual = sha(f)
        if (actual != jdk4pySha256) { f.delete(); throw GradleException("Java runtime download has SHA-256 $actual, expected $jdk4pySha256") }
    }
}

val windowsImage by tasks.registering(Sync::class) {
    description = "Assembles the Windows program folder: app\\*.jar + runtime\\ (Java) + README."
    dependsOn(tasks.jar, downloadWindowsRuntime)
    into(imageDir)
    from(tasks.jar) { into("app") }
    // all libraries, with the Windows build of the Skia graphics engine instead of this machine's
    from(configurations.runtimeClasspath.map { cp -> cp.filter { !it.name.startsWith("skiko-awt-runtime-") } }) { into("app") }
    from(windowsSkiko) { into("app") }
    doFirst {
        // the Windows runtime must match the skiko version the app was compiled against
        val used = configurations.runtimeClasspath.get().map { it.name }.first { it.startsWith("skiko-awt-runtime-") }.substringAfterLast('-').removeSuffix(".jar")
        val win = windowsSkiko.single().name.substringAfterLast('-').removeSuffix(".jar")
        if (used != win) throw GradleException("skiko $used is used but the Windows runtime is $win – update windowsSkiko")
    }
    from(downloadWindowsRuntime.map { zipTree(runtimeWheel.get().asFile) }) {
        include("jdk4py/java-runtime/**")
        eachFile { path = "runtime/" + path.removePrefix("jdk4py/java-runtime/") }
        includeEmptyDirs = false
    }
    from("packaging/README.txt")
    preserve { include("SchoolFeeManager.exe") }
}

fun makensis(vararg args: String) = listOf("makensis", "-V2", "-INPUTCHARSET", "UTF8") + args

val windowsLauncher by tasks.registering(Exec::class) {
    description = "Builds SchoolFeeManager.exe (starts the bundled Java runtime)."
    dependsOn(windowsImage)
    inputs.file("packaging/launcher.nsi"); inputs.file("packaging/SchoolFeeManager.ico")
    outputs.file(imageDir.map { it.file("SchoolFeeManager.exe") })
    commandLine(makensis(
        "-DOUT=${imageDir.get().file("SchoolFeeManager.exe").asFile.absolutePath}",
        "-DICON=${file("packaging/SchoolFeeManager.ico").absolutePath}",
        "-DVERSION=$appVersion",
        file("packaging/launcher.nsi").absolutePath,
    ))
}

val windowsInstaller by tasks.registering(Exec::class) {
    description = "Builds SchoolFeeManager-<version>-Setup.exe."
    dependsOn(windowsLauncher)
    val out = windowsDir.map { it.file("SchoolFeeManager-$appVersion-Setup.exe") }
    outputs.file(out)
    doFirst {
        val sizeKb = imageDir.get().asFile.walkTopDown().filter { it.isFile }.sumOf { it.length() } / 1024
        commandLine(makensis(
            "-DOUT=${out.get().asFile.absolutePath}",
            "-DSRC=${imageDir.get().asFile.absolutePath}",
            "-DICON=${file("packaging/SchoolFeeManager.ico").absolutePath}",
            "-DVERSION=$appVersion",
            "-DSIZE_KB=$sizeKb",
            file("packaging/installer.nsi").absolutePath,
        ))
    }
}

val windowsPortableZip by tasks.registering(Zip::class) {
    description = "Zips the program folder (portable version, no installation)."
    dependsOn(windowsLauncher)
    from(imageDir.map { it.asFile.parentFile })
    destinationDirectory.set(windowsDir)
    archiveFileName.set("SchoolFeeManager-$appVersion-Windows-portable.zip")
}

tasks.register("packageWindows") {
    group = "distribution"
    description = "Windows installer (.exe) and portable .zip"
    dependsOn(windowsInstaller, windowsPortableZip)
}
