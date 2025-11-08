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

import jakarta.persistence.Column
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.UuidGenerator
import java.util.UUID

@MappedSuperclass
abstract class BaseEntity(
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    @Column(columnDefinition = "UUID", nullable = false, updatable = false)
    var id: UUID? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true // checking the same instance eg: println(u1.equals(u1))
        // val u1 = User(), val u2 = User() => if "exactly" same runtime class (no subclass/proxy), check ids
        if (other == null || this::class != other::class) return false
        other as BaseEntity
        return id != null && id == other.id
    }

    // val u1 = User(), val u2 = User()
    // val set = mutableSetOf(u1)
    // Kotlin by default, checks the memory addresses (which are unique), so would return u1 != u2
    override fun hashCode(): Int = id?.hashCode() ?: 0
}
