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
package io.github.aaravmahajanofficial

import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON
import org.springframework.test.web.servlet.ResultActionsDsl

open class ProblemResponseAssertions {

    fun assertProblem(
        result: ResultActionsDsl,
        expectedType: String,
        expectedStatus: Int,
        expectedTitle: String,
        expectedDetail: String? = null,
        expectedInstance: String? = null,
    ) {
        result.andExpect {
            status { isEqualTo(expectedStatus) }
            content { contentType(APPLICATION_PROBLEM_JSON) }
            jsonPath("$.type") { value(expectedType) }
            jsonPath("$.status") { value(expectedStatus) }
            jsonPath("$.title") { value(expectedTitle) }

            if (expectedDetail != null) {
                jsonPath("$.detail") { value(expectedDetail) }
            } else {
                jsonPath("$.detail") { exists() }
            }

            if (expectedInstance != null) {
                jsonPath("$.instance") { value(expectedInstance) }
            } else {
                jsonPath("$.instance") { exists() }
            }
        }
    }

    fun assertMethodNotAllowed(result: ResultActionsDsl, instance: String? = null) {
        assertProblem(
            result = result,
            expectedStatus = 405,
            expectedType = "https://api.example.com/problems/method-not-allowed",
            expectedTitle = "Method Not Allowed",
            expectedInstance = instance,
        )
    }

    fun assertBadRequest(result: ResultActionsDsl, instance: String? = null) {
        assertProblem(
            result = result,
            expectedStatus = 400,
            expectedType = "https://api.example.com/problems/malformed-json",
            expectedTitle = "Malformed JSON",
            expectedDetail = "Invalid or malformed JSON payload.",
            expectedInstance = instance,
        )

        result.andExpect {
            jsonPath("$.cause") { exists() }
        }
    }

    fun assertUnauthorized(result: ResultActionsDsl, detail: String, instance: String? = null) {
        assertProblem(
            result = result,
            expectedStatus = 401,
            expectedType = "https://api.example.com/problems/unauthorized",
            expectedTitle = "Authentication Failed",
            expectedDetail = detail,
            expectedInstance = instance,
        )
    }

    fun assertConflict(result: ResultActionsDsl, detail: String, instance: String? = null) {
        assertProblem(
            result = result,
            expectedStatus = 409,
            expectedType = "https://api.example.com/problems/conflict",
            expectedTitle = "Resource Conflict",
            expectedDetail = detail,
            expectedInstance = instance,
        )
    }

    fun assertUnsupportedMediaType(result: ResultActionsDsl, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/unsupported-media-type",
            expectedStatus = 415,
            expectedTitle = "Unsupported Media Type",
            expectedInstance = instance,
        )

        result.andExpect {
            jsonPath("$.mediaType") { exists() }
            jsonPath("$.supported") { exists() }
        }
    }

    fun assertUnprocessableEntity(result: ResultActionsDsl, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/validation",
            expectedStatus = 422,
            expectedTitle = "Validation Failed",
            expectedDetail = "One or more fields failed validation.",
            expectedInstance = instance,
        )
    }

    fun assertInternalServerError(result: ResultActionsDsl, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/internal-server-error",
            expectedStatus = 500,
            expectedTitle = "Internal Server Error",
            expectedDetail = "An unexpected error occurred.",
            expectedInstance = instance,
        )
    }
}
