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

import io.github.aaravmahajanofficial.common.LogSanitizer.sanitizeLogInput
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI

@RestControllerAdvice
class GlobalExceptionHandler {

    companion object {
        private val validationType = URI.create("https://api.example.com/problems/validation")
        private val mediaTypeType = URI.create("https://api.example.com/problems/unsupported-media-type")
        private val methodNotAllowedType = URI.create("https://api.example.com/problems/method-not-allowed")
        private val conflictType = URI.create("https://api.example.com/problems/conflict")
        private val malformedJsonType = URI.create("https://api.example.com/problems/malformed-json")
        private val internalType = URI.create("https://api.example.com/problems/internal-server-error")
    }

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    // 422 Unprocessable Content
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException, request: HttpServletRequest): ProblemDetail {
        val fieldErrors = ex.bindingResult.fieldErrors.map { error ->
            mapOf(
                "field" to error.field,
                "message" to (error.defaultMessage ?: "Invalid Value"),
                "code" to (error.code ?: "Invalid"),
            )
        }

        logger.warn(
            "Validation failed at request {} -> {}",
            sanitizeLogInput(request.requestURL),
            sanitizeLogInput(fieldErrors),
        )

        return ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT).apply {
            type = validationType
            title = "Validation Failed"
            detail = "One or more fields failed validation."
            instance = URI.create(request.requestURL.toString())

            setProperty("validationErrors", fieldErrors)
        }
    }

    // 415 Media Type Not Supported
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeException(ex: HttpMediaTypeNotSupportedException, request: HttpServletRequest): ProblemDetail {
        logger.warn(
            "Unsupported media type {} at {}",
            sanitizeLogInput(ex.contentType),
            sanitizeLogInput(request.requestURL),
        )

        return ProblemDetail.forStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE).apply {
            type = mediaTypeType
            title = "Unsupported Media Type"
            detail = "The media type is not supported: ${ex.contentType}"
            instance = URI.create(request.requestURL.toString())

            setProperty("mediaType", ex.contentType)
            setProperty("supported", ex.supportedMediaTypes.map { it.toString() })
        }
    }

    // 405 Method Not Allowed
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowedException(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ProblemDetail {
        logger.warn("Method {} not allowed at {}", sanitizeLogInput(ex.method), sanitizeLogInput(request.requestURL))

        return ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED).apply {
            type = methodNotAllowedType
            title = "Method Not Allowed"
            detail = ex.message ?: "This HTTP method is not allowed."
            instance = URI.create(request.requestURL.toString())

            setProperty("rejectedMethod", ex.method)
            setProperty("allowedMethods", ex.supportedHttpMethods?.map { it.name() })
        }
    }

    // 409 Conflict
    @ExceptionHandler(ResourceConflictException::class)
    fun handleResourceConflictException(ex: ResourceConflictException, request: HttpServletRequest): ProblemDetail {
        logger.warn("Resource conflict at {} -> {}", sanitizeLogInput(request.requestURL), sanitizeLogInput(ex.message))

        return ProblemDetail.forStatus(HttpStatus.CONFLICT).apply {
            type = conflictType
            title = "Resource Conflict"
            detail = ex.message ?: "A resource conflict occurred."
            instance = URI.create(request.requestURL.toString())
        }
    }

    // 400 Malformed JSON
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedJson(ex: HttpMessageNotReadableException, request: HttpServletRequest): ProblemDetail {
        logger.warn("Malformed JSON at {} -> {}", sanitizeLogInput(request.requestURL), sanitizeLogInput(ex.message))

        return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            type = malformedJsonType
            title = "Malformed JSON"
            detail = "Invalid or malformed JSON payload."
            instance = URI.create(request.requestURL.toString())

            setProperty("cause", ex.mostSpecificCause.message)
        }
    }

    // 500 Internal Server Error - Missing default role
    @ExceptionHandler(DefaultRoleNotFoundException::class)
    fun handleMissingRole(ex: DefaultRoleNotFoundException, request: HttpServletRequest): ProblemDetail {
        logger.error(
            "Missing default role on {}: {}",
            sanitizeLogInput(request.requestURL),
            sanitizeLogInput(ex.message),
        )

        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            type = internalType
            title = "System Configuration Error"
            detail = "Missing required default role."
            instance = URI.create(request.requestURL.toString())

            setProperty("role", "Default role missing")
        }
    }

    // 500 Internal Server Error (Catch-all)
    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception, request: HttpServletRequest): ProblemDetail {
        logger.error(
            "Unexpected error occurred on {}: {}",
            sanitizeLogInput(request.requestURL),
            sanitizeLogInput(ex.message),
            ex,
        )

        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            type = internalType
            title = "Internal Server Error"
            detail = "An unexpected error occurred."
            instance = URI.create(request.requestURL.toString())
        }
    }
}
