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
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.ForeignKey
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table
import org.hibernate.annotations.CurrentTimestamp
import org.hibernate.annotations.OnDelete
import org.hibernate.annotations.OnDeleteAction
import org.hibernate.annotations.UpdateTimestamp
import java.time.Instant

@Suppress("LongParameterList")
@Entity
@Table(name = "user_addresses")
class UserAddress(

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var addressType: AddressType,

    @Column(name = "full_name", nullable = false, length = 200)
    var fullName: String,

    @Column(name = "phone_number", nullable = false, length = 20)
    var phoneNumber: String,

    @Column(name = "address_line1", nullable = false)
    var addressLine1: String,

    @Column(name = "address_line2")
    var addressLine2: String,

    @Column(nullable = false, length = 100)
    var city: String,

    @Column(nullable = false, length = 100)
    var state: String,

    @Column(name = "postal_code", nullable = false, length = 20)
    var postalCode: String,

    @Column(nullable = false, length = 100)
    var country: String,

    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,

    @CurrentTimestamp
    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
    var createdAt: Instant? = null,

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    var updatedAt: Instant? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = ForeignKey(name = "fk_user_addresses_user_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    var user: User? = null,

) : BaseEntity() {

    override fun toString(): String =
        "UserAddress(id=$id, fullName=$fullName, city=$city, state=$state, postalCode=$postalCode)"
}
