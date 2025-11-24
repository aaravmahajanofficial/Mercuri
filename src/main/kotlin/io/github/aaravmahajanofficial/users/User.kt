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

import io.github.aaravmahajanofficial.common.BaseEntity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Suppress("LongParameterList", "ktlint:standard:backing-property-naming")
@Entity
@Table(name = "users")
class User(
    @Column(unique = true, nullable = false, length = 255)
    var email: String,

    @Column(name = "password_hash", nullable = false, length = 255)
    var passwordHash: String,

    @Column(name = "first_name", nullable = false, length = 100)
    var firstName: String,

    @Column(name = "last_name", nullable = false, length = 100)
    var lastName: String,

    @Column(name = "phone_number", nullable = false, length = 20)
    var phoneNumber: String,

    @Column(name = "email_verified", nullable = false)
    var emailVerified: Boolean = false,

    @Column(name = "phone_verified", nullable = false)
    var phoneVerified: Boolean = false,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: UserStatus = UserStatus.ACTIVE,

    @Column(name = "last_login_at", columnDefinition = "TIMESTAMPTZ")
    var lastLoginAt: Instant? = null,

    @CreationTimestamp
    @Column(name = "created_at", columnDefinition = "TIMESTAMPTZ", nullable = false, updatable = false)
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", columnDefinition = "TIMESTAMPTZ", nullable = false)
    var updatedAt: Instant? = null,

) : BaseEntity() {

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "user_role",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")],
    )
    @OnDelete(action = OnDeleteAction.CASCADE)
    protected var _roles: MutableSet<Role> = mutableSetOf()
    val roles: Set<Role> get() = _roles

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL], orphanRemoval = true)
    protected var _addresses: MutableSet<UserAddress> = mutableSetOf()
    val addresses: Set<UserAddress> get() = _addresses

    fun fullName(): String = listOf(firstName, lastName).joinToString(" ")

    fun addRole(role: Role) {
        _roles.add(role)
    }

    fun removeRole(role: Role) {
        _roles.remove(role)
    }

    fun addAddress(address: UserAddress) {
        _addresses.add(address)
        address.user = this
    }

    fun removeAddress(address: UserAddress) {
        _addresses.remove(address)
        address.user = null
    }

    override fun toString(): String = "User(id=$id, email=$email, fullName=${fullName()} status=$status)"
}
