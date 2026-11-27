package com.monicahq.android.instance

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What an instance said about itself when it was checked, kept for the sign-in that follows. */
data class InstanceConfiguration(
    val issuer: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    val revocationEndpoint: String?,
    val clientId: String,
    val scope: String,
    val registrationEnabled: Boolean,
)

/** `GET {base}/api/v1/status`. */
@Serializable
internal data class StatusDocument(
    val status: String,
    val version: String? = null,
    val instance: String? = null,
    val mobile: MobileDocument? = null,
)

/** The `mobile` member of the status: how the apps sign in to this instance. */
@Serializable
internal data class MobileDocument(
    @SerialName("api_version") val apiVersion: Int,
    @SerialName("client_id") val clientId: String,
    val scope: String,
    @SerialName("registration_enabled") val registrationEnabled: Boolean,
    @SerialName("minimum_android_version") val minimumAndroidVersion: String? = null,
)

/** `GET {base}/.well-known/oauth-authorization-server` (RFC 8414). */
@Serializable
internal data class AuthorizationServerDocument(
    val issuer: String,
    @SerialName("authorization_endpoint") val authorizationEndpoint: String,
    @SerialName("token_endpoint") val tokenEndpoint: String,
    @SerialName("revocation_endpoint") val revocationEndpoint: String? = null,
    @SerialName("code_challenge_methods_supported") val codeChallengeMethods: List<String> = emptyList(),
)
