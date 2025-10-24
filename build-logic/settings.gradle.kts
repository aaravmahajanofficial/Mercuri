plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create(
            "libs",
            Action {
                from(files("../gradle/libs.versions.toml"))
            },
        )
    }
}

rootProject.name = "build-logic"
