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
package io.github.aaravmahajanofficial.auth.jwt

import io.github.aaravmahajanofficial.common.LogSanitizer.sanitizeLogInput
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE
import org.springframework.http.ProblemDetail
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import java.net.URI

@Component
class JwtAuthenticationEntryPoint(private val objectMapper: ObjectMapper) : AuthenticationEntryPoint {

    // Send a clean JSON 401 response instead of redirecting

    private val logger = LoggerFactory.getLogger(JwtAuthenticationEntryPoint::class.java)

    companion object {
        private val UNAUTHORIZED_TYPE = URI.create("https://api.example.com/problems/unauthorized")
    }

    override fun commence(
        request: HttpServletRequest,
        response: HttpServletResponse,
        authException: AuthenticationException,
    ) {
        logger.debug("Unauthorized request: {} to {}", sanitizeLogInput(request.requestURI), authException.message)

        val errorMessage = authException.message ?: "Missing or invalid authorization token."

        val problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED).apply {
            type = UNAUTHORIZED_TYPE
            title = "Unauthorized"
            detail = errorMessage
            instance = URI.create(request.requestURI)
        }

        response.status = HttpStatus.UNAUTHORIZED.value()
        response.contentType = APPLICATION_PROBLEM_JSON_VALUE

        objectMapper.writeValue(response.writer, problemDetail)
    }
}
