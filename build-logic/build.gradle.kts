import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `kotlin-dsl`
}

kotlin {
    val kotlinVersion = libs.versions.kotlinLanguage
    val jvmTargetVersion = libs.versions.jvmTarget

    jvmToolchain {
        languageVersion.set(jvmTargetVersion.map(JavaLanguageVersion::of))
    }

    compilerOptions {
        apiVersion.set(kotlinVersion.map(KotlinVersion::fromVersion))
        languageVersion.set(kotlinVersion.map(KotlinVersion::fromVersion))
        jvmTarget.set(jvmTargetVersion.map(JvmTarget::fromTarget))

        allWarningsAsErrors.set(true)
        progressiveMode.set(true)
        optIn.add("kotlin.RequiresOptIn")
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

dependencies {
    implementation(plugin(libs.plugins.kotlin.jvm))
    implementation(plugin(libs.plugins.spotless))
    implementation(plugin(libs.plugins.detekt))
}

// Helper function that transforms a Gradle Plugin alias from a
// Version Catalog into a valid dependency notation for buildSrc
// See https://docs.gradle.org/current/userguide/version_catalogs.html#sec:buildsrc-version-catalog
fun plugin(plugin: Provider<PluginDependency>) =
    plugin.map { "${it.pluginId}:${it.pluginId}.gradle.plugin:${it.version}" }
