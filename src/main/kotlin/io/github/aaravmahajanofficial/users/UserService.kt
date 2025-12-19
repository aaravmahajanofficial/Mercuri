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
package io.github.aaravmahajanofficial.users

import io.github.aaravmahajanofficial.auth.mappers.toProfileDto
import io.github.aaravmahajanofficial.common.exception.model.ResourceNotFoundException
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserService(private val userRepository: UserRepository) {
    fun getUserProfile(userId: UUID): UserProfileDto {
        val user = userRepository.findByIdOrNull(userId) ?: throw ResourceNotFoundException("User", "id", userId)
        return user.toProfileDto()
    }
}
