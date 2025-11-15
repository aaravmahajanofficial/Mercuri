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
package io.github.aaravmahajanofficial.common

import java.time.Instant

sealed class ApiResponse<out T> {

    data class Success<T>(val data: T, val meta: Meta = Meta()) : ApiResponse<T>()
    data class Error(val error: ErrorDetails, val meta: Meta = Meta()) : ApiResponse<Nothing>()

    data class Meta(val timeStamp: Instant = Instant.now())

    data class ErrorDetails(val code: String, val details: Map<String, String>? = null)

    companion object {
        fun <T> success(data: T): ApiResponse<T> = Success(data)

        fun error(code: String, details: Map<String, String>?): ApiResponse<Nothing> =
            Error(ErrorDetails(code, details))
    }
}
