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
package io.github.aaravmahajanofficial.config

import io.github.aaravmahajanofficial.auth.jwt.JwtAuthenticationFilter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig {

    companion object {
        private const val BCRYPT_STRENGTH = 12
    }

    @Bean
    fun securityFilterChain(
        httpSecurity: HttpSecurity,
        jwtAuthenticationFilter: JwtAuthenticationFilter,
        authenticationEntryPoint: AuthenticationEntryPoint,
    ): SecurityFilterChain {
        httpSecurity
            .csrf { csrfConfig -> csrfConfig.disable() }
            .sessionManagement { sessionConfig -> sessionConfig.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling { exceptionConfig -> exceptionConfig.authenticationEntryPoint(authenticationEntryPoint) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers("/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
                    .permitAll()
                    .anyRequest() // all other endpoints require proper authentication
                    .authenticated()
            }
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter::class.java,
            ) // run custom filter first, otherwise Spring Security would try to authenticate user with other mechanisms
        return httpSecurity.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder(BCRYPT_STRENGTH)
}
