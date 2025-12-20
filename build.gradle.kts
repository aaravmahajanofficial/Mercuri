/*
 * Copyright 2025 Aarav Mahajan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
import kotlinx.kover.gradle.plugin.dsl.AggregationType
import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    base
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kover)

    id("quality-conventions")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

dependencies {
    // Bundled Essentials
    implementation(libs.bundles.kotlin.essentials)
    implementation(libs.bundles.spring.web.stack)

    // Persistence
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.flyway)
    implementation(libs.spring.boot.starter.data.redis)
    implementation(libs.flyway.postgres)
    runtimeOnly(libs.postgresql)

    // Security & Ops
    implementation(libs.spring.boot.starter.actuator)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.jackson)

    // Testing
    testImplementation(libs.bundles.testing.suite)
    testRuntimeOnly(libs.junit.launcher)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

kover {
    reports {
        filters {
            excludes {
                classes("io.github.aaravmahajanofficial.ApplicationKt")
                classes("io.github.aaravmahajanofficial.config.*")
                classes("io.github.aaravmahajanofficial.db.migration.*")

                classes(
                    "io.github.aaravmahajanofficial.common.BaseEntity",
                    "io.github.aaravmahajanofficial.common.LogSanitizer",
                    "io.github.aaravmahajanofficial.common.exception.model.*",
                    "*Dto",
                    "*Mappers*",
                )
            }
        }
        verify {
            rule("Minimum Line Coverage") {
                bound {
                    minValue = 80
                    coverageUnits = CoverageUnit.LINE
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                }
            }

            rule("Branch Coverage") {
                bound {
                    minValue = 70
                    coverageUnits = CoverageUnit.BRANCH
                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
                }
            }

            // Would be implemented later
//            rule("Per-Class") {
//                groupBy = GroupingEntityType.CLASS
//
//                bound {
//                    minValue = 70
//                    coverageUnits = CoverageUnit.LINE
//                    aggregationForGroup = AggregationType.COVERED_PERCENTAGE
//                }
//            }
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
