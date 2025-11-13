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
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest,
    ): ResponseEntity<ApiResponse<Nothing?>> {
        val errors = ex.bindingResult.allErrors.filterIsInstance<FieldError>()
            .associate { it.field to (it.defaultMessage ?: "Invalid Value") }

        logger.warn("Validation: {}", errors)

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION_FAILED", errors, request.requestURI))
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiResponse<Nothing?>> {
        logger.error("Unexpected error occurred", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(
                ApiResponse.error(
                    "INTERNAL_SERVER_ERROR",
                    mapOf("error" to "An unexpected error occured"),
                    request.requestURI,
                ),
            )
    }
}
