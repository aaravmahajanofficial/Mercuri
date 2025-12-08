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

import org.springframework.http.HttpStatus
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
                jsonPath("$.detail") { isNotEmpty() }
            }

            if (expectedInstance != null) {
                jsonPath("$.instance") { value(expectedInstance) }
            } else {
                jsonPath("$.instance") { isNotEmpty() }
            }
        }
    }

    fun assertMethodNotAllowed(result: ResultActionsDsl, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/method-not-allowed",
            expectedTitle = "Method Not Allowed",
            expectedInstance = instance,
            expectedStatus = HttpStatus.METHOD_NOT_ALLOWED.value(),
        )
    }

    fun assertBadRequest(result: ResultActionsDsl, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/malformed-json",
            expectedTitle = "Malformed JSON",
            expectedDetail = "Invalid or malformed JSON payload.",
            expectedInstance = instance,
            expectedStatus = HttpStatus.BAD_REQUEST.value(),
        )

        result.andExpect {
            jsonPath("$.cause") { exists() }
        }
    }

    fun assertUnauthorized(result: ResultActionsDsl, detail: String, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/unauthorized",
            expectedTitle = "Authentication Failed",
            expectedDetail = detail,
            expectedInstance = instance,
            expectedStatus = HttpStatus.UNAUTHORIZED.value(),
        )
    }

    fun assertInvalidToken(result: ResultActionsDsl, instance: String?) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/invalid-token",
            expectedTitle = "Invalid Token",
            expectedDetail = "The provided token is invalid or has expired.",
            expectedInstance = instance,
            expectedStatus = HttpStatus.UNAUTHORIZED.value(),
        )
    }

    fun assertForbidden(result: ResultActionsDsl, title: String, detail: String, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/forbidden",
            expectedTitle = title,
            expectedDetail = detail,
            expectedInstance = instance,
            expectedStatus = HttpStatus.FORBIDDEN.value(),
        )
    }

    fun assertConflict(result: ResultActionsDsl, title: String, detail: String, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/conflict",
            expectedTitle = title,
            expectedDetail = detail,
            expectedInstance = instance,
            expectedStatus = HttpStatus.CONFLICT.value(),
        )
    }

    fun assertUnsupportedMediaType(result: ResultActionsDsl, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/unsupported-media-type",
            expectedTitle = "Unsupported Media Type",
            expectedInstance = instance,
            expectedStatus = HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
        )

        result.andExpect {
            jsonPath("$.mediaType") { exists() }
            jsonPath("$.supported") { exists() }
        }
    }

    fun assertUnprocessableContent(result: ResultActionsDsl, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/validation",
            expectedTitle = "Validation Failed",
            expectedDetail = "One or more fields failed validation.",
            expectedInstance = instance,
            expectedStatus = HttpStatus.UNPROCESSABLE_CONTENT.value(),
        )
    }

    fun assertInternalServerError(result: ResultActionsDsl, title: String, instance: String? = null) {
        assertProblem(
            result = result,
            expectedType = "https://api.example.com/problems/internal-server-error",
            expectedTitle = title,
            expectedDetail = "An unexpected error occurred.",
            expectedInstance = instance,
            expectedStatus = HttpStatus.INTERNAL_SERVER_ERROR.value(),
        )
    }
}
