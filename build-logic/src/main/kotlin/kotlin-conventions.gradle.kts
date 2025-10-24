import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    id("org.jetbrains.kotlin.jvm")
}

private val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

kotlin {
    val kotlinVersion = libs.findVersion("kotlinLanguage").get().requiredVersion
    val jvmTargetVersion = libs.findVersion("jvmTarget").get().requiredVersion

    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(jvmTargetVersion))
    }

    compilerOptions {
        apiVersion.set(KotlinVersion.fromVersion(kotlinVersion))
        languageVersion.set(KotlinVersion.fromVersion(kotlinVersion))
        jvmTarget.set(JvmTarget.fromTarget(jvmTargetVersion))

        allWarningsAsErrors.set(true)
        progressiveMode.set(true)
        optIn.add("kotlin.RequiresOptIn")
        jvmDefault.set(JvmDefaultMode.ENABLE)
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(platform(libs.findLibrary("kotlin-bom").get()))
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    compileOnly(libs.findLibrary("jetbrains-annotations").get())
}

tasks.withType<Test> {
    useJUnitPlatform()
}
