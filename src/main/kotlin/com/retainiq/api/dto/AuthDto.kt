/**
 * Data transfer objects for the `/v1/auth/token` OAuth2 endpoint.
 */
package com.retainiq.api.dto

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * OAuth2 token request (client credentials grant).
 *
 * @property grantType must be "client_credentials"
 * @property clientId the tenant UUID
 * @property clientSecret the shared secret
 */
data class TokenRequest(
    @JsonProperty("grant_type")
    val grantType: String,
    @JsonProperty("client_id")
    val clientId: String,
    @JsonProperty("client_secret")
    val clientSecret: String
)

/**
 * OAuth2 token response.
 *
 * @property accessToken the JWT bearer token
 * @property tokenType always "Bearer"
 * @property expiresIn token validity in seconds
 */
data class TokenResponse(
    @JsonProperty("access_token")
    val accessToken: String,
    @JsonProperty("token_type")
    val tokenType: String = "Bearer",
    @JsonProperty("expires_in")
    val expiresIn: Long
)
