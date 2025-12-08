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
package io.github.aaravmahajanofficial.auth.token

import io.github.aaravmahajanofficial.common.BaseEntity
import io.github.aaravmahajanofficial.users.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant
import java.util.UUID

@Suppress("LongParameterList")
@Entity
@Table(name = "refresh_tokens")
class RefreshToken(
    @Column(nullable = false, unique = true)
    var jti: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: User,

    @Column(name = "token_hash", unique = true, nullable = false)
    var tokenHash: String,

    @Column(name = "expires_at", columnDefinition = "TIMESTAMPTZ", nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var revoked: Boolean = false,

    @Column(name = "device_info")
    var deviceInfo: String? = null,

    @Column(name = "ip_address", length = 45)
    var ipAddress: String? = null,

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ", nullable = false)
    var updatedAt: Instant? = null,
) : BaseEntity()
