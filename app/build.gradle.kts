import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    jacoco
}

// Firebase is optional at build time: the app runs in local-only mode until
// google-services.json (from the Firebase console) is placed next to this file.
val firebaseConfigured = file("google-services.json").exists()
if (firebaseConfigured) {
    apply(plugin = "com.google.gms.google-services")
}

// Web client ID for Google Sign-In (Firebase console -> Authentication -> Google).
// Read from local.properties or, failing that, a Gradle property (gradle.properties).
val webClientId: String = run {
    val props = Properties()
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { stream -> props.load(stream) }
    props.getProperty("webClientId")
        ?: (project.findProperty("webClientId") as String?)
        ?: ""
}

android {
    namespace = "com.nuelto.camperexperience"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nuelto.camperexperience"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        vectorDrawables { useSupportLibrary = true }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig.buildConfigField("String", "WEB_CLIENT_ID", "\"$webClientId\"")

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true // Robolectric
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Coverage gate: 100% line coverage on everything that can run on the JVM. Excluded:
// code needing a real device/backend (MapLibre GL surface, Play services location,
// Firebase/Firestore, Credential Manager) — that's covered by the emulator workflow.
jacoco {
    toolVersion = "0.8.13"
}

// Without this, coverage of classes loaded through Robolectric's classloader is dropped.
tasks.withType<Test>().configureEach {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val coverageExcludes = listOf(
    "com/nuelto/camperexperience/BuildConfig*",
    "com/nuelto/camperexperience/FirebaseBackendKt*",
    "com/nuelto/camperexperience/data/FirebaseAuthRepository*",
    "com/nuelto/camperexperience/data/Firestore*",
    "com/nuelto/camperexperience/location/**",
    "com/nuelto/camperexperience/ui/map/**",
)

val coverageClassDirs = layout.buildDirectory.dir("tmp/kotlin-classes/debug").map { dir ->
    fileTree(dir) { exclude(coverageExcludes) }
}
val coverageExecData =
    layout.buildDirectory.file("outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec")

val coverageReport = tasks.register<JacocoReport>("coverageReport") {
    group = "verification"
    description = "JaCoCo coverage report for debug unit tests."
    dependsOn("testDebugUnitTest")
    classDirectories.setFrom(coverageClassDirs)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(coverageExecData)
    reports {
        xml.required = true
        html.required = true
    }
}

tasks.register<JacocoCoverageVerification>("coverageVerify") {
    group = "verification"
    description = "Fails unless line coverage is 100% (excluded: device-only code)."
    dependsOn("testDebugUnitTest", coverageReport)
    classDirectories.setFrom(coverageClassDirs)
    sourceDirectories.setFrom(files("src/main/java"))
    executionData.setFrom(coverageExecData)
    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "1.00".toBigDecimal()
            }
        }
    }
}

// Mutation testing (PIT) over the JVM-pure logic: domain, in-memory data layer,
// formatting, and the ViewModels that don't need Robolectric. Compose UI is out of
// scope for PIT (Robolectric classloaders don't survive pitest's minions).
val pitest: Configuration by configurations.creating

tasks.register<JavaExec>("pitest") {
    group = "verification"
    description = "PIT mutation testing over the JVM-pure logic (threshold 80%)."
    dependsOn("testDebugUnitTest")
    classpath = pitest
    mainClass.set("org.pitest.mutationtest.commandline.MutationCoverageReport")
    val cpFile = layout.buildDirectory.file("pitest/classpath.txt")
    doFirst {
        val testClasspath = tasks.named<Test>("testDebugUnitTest").get().classpath
        cpFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(testClasspath.filter { it.exists() }.joinToString("\n") { it.absolutePath })
        }
    }
    args(
        "--classPathFile", cpFile.get().asFile.absolutePath,
        "--targetClasses",
        listOf(
            "com.nuelto.camperexperience.domain.*",
            "com.nuelto.camperexperience.data.InMemory*",
            "com.nuelto.camperexperience.ui.FormatKt",
            "com.nuelto.camperexperience.ui.triplist.TripListViewModel",
            "com.nuelto.camperexperience.ui.settings.SettingsViewModel",
        ).joinToString(","),
        "--excludedClasses", "*\$Companion",
        "--targetTests",
        listOf(
            "com.nuelto.camperexperience.domain.*",
            "com.nuelto.camperexperience.data.*",
            "com.nuelto.camperexperience.ui.FormatTest",
            "com.nuelto.camperexperience.ui.triplist.TripListViewModelTest",
            "com.nuelto.camperexperience.ui.settings.SettingsViewModelTest",
        ).joinToString(","),
        "--sourceDirs", "src/main/java",
        "--reportDir", layout.buildDirectory.dir("reports/pitest").get().asFile.absolutePath,
        "--timestampedReports", "false",
        "--outputFormats", "HTML,XML",
        "--mutationThreshold", "80",
        "--threads", "4",
    )
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.maplibre.compose)
    // OpenGL runtime — the Vulkan one draws a blank map on emulators (gfxstream).
    runtimeOnly(libs.maplibre.compose.runtime)
    implementation(libs.play.services.location)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services)
    implementation(libs.googleid)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    pitest(libs.pitest.command.line)
}
