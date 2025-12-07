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

import org.springframework.stereotype.Service
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKey

@Service
class RefreshTokenHashService(private val secretKey: SecretKey) {

    fun hash(token: String): String {
        // Ensure the secret and token are converted to a ByteArray using a consistent encoding, e.g., UTF-8
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKey)

        // Perform the HMAC hashing
        val bytes = mac.doFinal(token.toByteArray(Charsets.UTF_8))

        // Encode the result to URL-safe Base64 string without padding
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}
