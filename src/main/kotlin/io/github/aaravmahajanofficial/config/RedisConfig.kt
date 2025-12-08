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

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * RedisTemplate configured explicitly with String serializers to avoid Spring’s default JDK serialization.
 *
 * Why this matters:
 * - Ensures all keys and values are stored as UTF-8 strings (human-readable, interoperable, safe).
 * - Prevents accidental storage of domain objects using Java serialization, which is brittle and insecure.
 * - Guarantees predictable serialization across languages/services and avoids deserialization vulnerabilities.
 *
 * This template should be preferred over the default RedisTemplate to enforce consistent and secure
 * serialization behavior across the application.
 *
 * See https://docs.spring.io/spring-data/redis/reference/redis/template.html#redis:serializer
 */
@Configuration
class RedisConfig {

    @Bean
    fun redisTemplate(redisConnectionFactory: RedisConnectionFactory): StringRedisTemplate {
        val template = StringRedisTemplate()
        template.connectionFactory = redisConnectionFactory
        return template
    }
}
