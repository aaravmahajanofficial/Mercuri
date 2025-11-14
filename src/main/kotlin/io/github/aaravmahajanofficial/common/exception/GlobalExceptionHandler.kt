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
package io.github.aaravmahajanofficial.common.exception

import io.github.aaravmahajanofficial.common.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing?>> {
        val errors = ex.bindingResult.allErrors.filterIsInstance<FieldError>()
            .associate { it.field to (it.defaultMessage ?: "Invalid Value") }

        logger.warn("Validation: {}", errors)

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION_FAILED", errors))
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeException(ex: HttpMediaTypeNotSupportedException): ResponseEntity<ApiResponse<Nothing?>> {
        logger.warn("Unsupported media type: {}", ex.contentType)
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).body(
            ApiResponse.error(
                "UNSUPPORTED_MEDIA_TYPE",
                mapOf("error" to "Unsupported media type: ${ex.contentType}"),
            ),
        )
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowedException(
        ex: HttpRequestMethodNotSupportedException,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        logger.warn("Method not allowed: {}", ex.message)
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(
            ApiResponse.error(
                "METHOD_NOT_ALLOWED",
                mapOf("error" to "Method not allowed: ${ex.message}"),
            ),
        )
    }

    @ExceptionHandler(ResourceConflictException::class)
    fun handleResourceConflictException(ex: ResourceConflictException): ResponseEntity<ApiResponse<Nothing?>> {
        logger.warn("Resource conflict: {}", ex.message)
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
            ApiResponse.error(
                "RESOURCE_CONFLICT",
                mapOf("error" to "Resource conflict: ${ex.message}"),
            ),
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedJson(ex: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing?>> {
        logger.warn("Malformed JSON request: {}", ex.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
            ApiResponse.error(
                "MALFORMED_JSON",
                mapOf("error" to "Malformed JSON request: ${ex.message}"),
            ),
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception): ResponseEntity<ApiResponse<Nothing?>> {
        logger.error("Unexpected error occurred", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ApiResponse.error(
                    "INTERNAL_SERVER_ERROR",
                    mapOf("error" to "An unexpected error occurred"),
                ),
            )
    }
}
