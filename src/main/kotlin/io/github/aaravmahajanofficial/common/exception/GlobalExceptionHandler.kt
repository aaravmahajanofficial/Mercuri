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
import org.springframework.security.core.AuthenticationException
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
        private val unauthorizedType = URI.create("https://api.example.com/problems/unauthorized")
        private val forbiddenType = URI.create("https://api.example.com/problems/forbidden")
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
            "Validation failed at request {}: {}",
            sanitizeLogInput(request.requestURI),
            sanitizeLogInput(fieldErrors),
        )

        return ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_CONTENT).apply {
            type = validationType
            title = "Validation Failed"
            detail = "One or more fields failed validation."
            instance = URI.create(request.requestURI)

            setProperty("validationErrors", fieldErrors)
        }
    }

    // 415 Media Type Not Supported
    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleMediaTypeException(ex: HttpMediaTypeNotSupportedException, request: HttpServletRequest): ProblemDetail {
        logger.warn(
            "Unsupported media type {} at {}",
            sanitizeLogInput(ex.contentType),
            sanitizeLogInput(request.requestURI),
        )

        return ProblemDetail.forStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE).apply {
            type = mediaTypeType
            title = "Unsupported Media Type"
            detail = "The media type is not supported: ${ex.contentType}"
            instance = URI.create(request.requestURI)

            setProperty("mediaType", ex.contentType)
            setProperty("supported", ex.supportedMediaTypes)
        }
    }

    // 405 Method Not Allowed
    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowedException(
        ex: HttpRequestMethodNotSupportedException,
        request: HttpServletRequest,
    ): ProblemDetail {
        logger.warn("Method {} not allowed at {}", sanitizeLogInput(ex.method), sanitizeLogInput(request.requestURI))

        return ProblemDetail.forStatus(HttpStatus.METHOD_NOT_ALLOWED).apply {
            type = methodNotAllowedType
            title = "Method Not Allowed"
            detail = "The HTTP method used is not allowed for this endpoint."
            instance = URI.create(request.requestURI)

            setProperty("rejectedMethod", ex.method)
            setProperty("allowedMethods", ex.supportedHttpMethods?.map { it.name() })
        }
    }

    // 409 Resource Conflict
    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handleUserAlreadyExistsException(ex: UserAlreadyExistsException, request: HttpServletRequest): ProblemDetail {
        logger.warn(
            "User registration conflict at {}: {}",
            sanitizeLogInput(request.requestURI),
            ex.message,
        )

        return ProblemDetail.forStatus(HttpStatus.CONFLICT).apply {
            type = conflictType
            title = "User Already Exists"
            detail = "That email address is taken. Try another."
            instance = URI.create(request.requestURI)
        }
    }

    // 400 Malformed JSON
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleMalformedJson(ex: HttpMessageNotReadableException, request: HttpServletRequest): ProblemDetail {
        logger.warn("Malformed JSON at {}: {}", sanitizeLogInput(request.requestURI), ex.message)

        return ProblemDetail.forStatus(HttpStatus.BAD_REQUEST).apply {
            type = malformedJsonType
            title = "Malformed JSON"
            detail = "Invalid or malformed JSON payload."
            instance = URI.create(request.requestURI)

            setProperty("cause", "JSON parsing error")
        }
    }

    // 401 Unauthorized
    @ExceptionHandler(AuthenticationException::class)
    fun handleAuthenticationFailed(ex: AuthenticationException, request: HttpServletRequest): ProblemDetail {
        logger.warn(
            "Authentication failed at {}: {}",
            sanitizeLogInput(request.requestURI),
            ex.message,
        )

        return ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED).apply {
            type = unauthorizedType
            title = "Authentication Failed"
            detail = "Invalid email or password. Please check your credentials and try again."
            instance = URI.create(request.requestURI)
        }
    }

    // 403 Forbidden
    @ExceptionHandler(AccountSuspendedException::class)
    fun handleAccountSuspended(ex: AccountSuspendedException, request: HttpServletRequest): ProblemDetail {
        logger.warn("Suspended user attempted login {}: {}", sanitizeLogInput(request.requestURI), ex.message)
        return ProblemDetail.forStatus(HttpStatus.FORBIDDEN).apply {
            type = forbiddenType
            title = "Account Suspended"
            detail = "Your account is currently suspended. Please contact support."
            instance = URI.create(request.requestURI)

            setProperty("isSuspended", true)
        }
    }

    // 403 Forbidden
    @ExceptionHandler(EmailNotVerifiedException::class)
    fun handleAccountSuspended(ex: EmailNotVerifiedException, request: HttpServletRequest): ProblemDetail {
        logger.warn("Login attempt with unverified email at {}: {}", sanitizeLogInput(request.requestURI), ex.message)
        return ProblemDetail.forStatus(HttpStatus.FORBIDDEN).apply {
            type = forbiddenType
            title = "Email Not Verified"
            detail = "You must verify your email address before logging in."
            instance = URI.create(request.requestURI)

            setProperty("requiresVerification", true)
        }
    }

    // 500 Internal Server Error - Missing default role
    @ExceptionHandler(DefaultRoleNotFoundException::class)
    fun handleMissingRole(ex: DefaultRoleNotFoundException, request: HttpServletRequest): ProblemDetail {
        logger.error(
            "Default role configuration missing during request to {}: {}",
            sanitizeLogInput(request.requestURI),
            ex.message,
        )

        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            type = internalType
            title = "System Configuration Error"
            detail = "A required system role is misconfigured. Please contact support."
            instance = URI.create(request.requestURI)
        }
    }

    // 500 Internal Server Error (Catch-all)
    @ExceptionHandler(Exception::class)
    fun handleGeneralException(ex: Exception, request: HttpServletRequest): ProblemDetail {
        logger.error("Unexpected error occurred on {}: {}", sanitizeLogInput(request.requestURI), ex.message, ex)

        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR).apply {
            type = internalType
            title = "Internal Server Error"
            detail = "An unexpected error occurred."
            instance = URI.create(request.requestURI)
        }
    }
}
