package com.cy.codex.app

import android.os.CancellationSignal
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.cy.codex.protocol.protocol.v2.UserVerificationErrorDetails
import com.cy.codex.protocol.protocol.v2.UserVerificationFailureReason
import com.cy.codex.protocol.protocol.v2.UserVerificationProof
import com.cy.codex.protocol.protocol.v2.UserVerificationUnavailableReason
import com.cy.codex.protocol.protocol.v2.UserVerificationCancellationReason
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Android Keystore P-256 credential behind a BiometricPrompt gate (user-verification/…/lib.rs and
 * credential.rs). No backend registration, no elicitation handling — the caller owns both.
 */

internal data class UserVerificationRequest(
    val challenge: ByteArray,
    val title: String,
    val description: String,
)

internal data class UserVerificationKeyInfo(
    val credentialId: String,
    val algorithm: String,
    /** Unpadded base64url of the SubjectPublicKeyInfo DER (user-verification/…/credential.rs). */
    val publicKey: String,
)

internal data class UserVerificationKeyCreation(
    val created: Boolean,
    val credential: UserVerificationKeyInfo,
)

internal data class UserVerificationKeyDeletion(
    val deletedCredentialId: String?,
)

internal data class UserVerificationStatus(
    val credential: UserVerificationKeyInfo?,
    val unavailableReason: UserVerificationUnavailableReason?,
    val unavailableMessage: String?,
)

/**
 * Mirrors `UserVerificationProvider` (user-verification/…/lib.rs). The Rust request guard for
 * cancellation and identity pinning does not cross the boundary; the reducer correlates attempts.
 */
internal interface UserVerificationProvider {
    /** Reads local readiness without creating credentials or prompting. */
    suspend fun status(): UserVerificationStatus

    /** Creates a key only if none exists; success says nothing about server enrollment. */
    suspend fun ensureKey(): UserVerificationKeyCreation

    /** Removes the local key idempotently; backend revocation belongs to the caller. */
    suspend fun delete(): UserVerificationKeyDeletion

    suspend fun verify(request: UserVerificationRequest): UserVerificationProof
}

/** One failed local operation; [details] is the only vocabulary the wire allows. */
internal class UserVerificationException(
    val details: UserVerificationErrorDetails,
    message: String = "",
) : Exception(message)

/**
 * Platform outcomes folded onto the closed wire set (codex-rs/app-server-protocol/…/user_verification.rs);
 * vendor codes never cross the boundary.
 */
internal enum class UserVerificationNativeFailure {
    InvalidParams,
    CredentialMissing,
    BiometricsUnavailable,
    ProviderUnavailable,
    UserCancelled,
    Interrupted,
    AuthenticationFailed,
    Timeout,
    ProviderError,
    ServiceError,
}

/** An exhaustive `when` keeps the set from growing without a wire value. */
internal fun userVerificationError(
    failure: UserVerificationNativeFailure,
    message: String = "",
): UserVerificationException =
    UserVerificationException(
        details =
            when (failure) {
                UserVerificationNativeFailure.InvalidParams ->
                    UserVerificationErrorDetails.InvalidRequest("invalidParams")
                UserVerificationNativeFailure.CredentialMissing ->
                    UserVerificationErrorDetails.Unavailable(
                        UserVerificationUnavailableReason.CredentialMissing,
                    )
                UserVerificationNativeFailure.BiometricsUnavailable ->
                    UserVerificationErrorDetails.Unavailable(
                        UserVerificationUnavailableReason.BiometricsUnavailable,
                    )
                UserVerificationNativeFailure.ProviderUnavailable ->
                    UserVerificationErrorDetails.Unavailable(
                        UserVerificationUnavailableReason.ProviderUnavailable,
                    )
                UserVerificationNativeFailure.UserCancelled ->
                    UserVerificationErrorDetails.Cancelled(
                        UserVerificationCancellationReason.UserCancelled,
                    )
                UserVerificationNativeFailure.Interrupted ->
                    UserVerificationErrorDetails.Cancelled(
                        UserVerificationCancellationReason.Interrupted,
                    )
                UserVerificationNativeFailure.AuthenticationFailed ->
                    UserVerificationErrorDetails.Failed(
                        UserVerificationFailureReason.AuthenticationFailed,
                    )
                UserVerificationNativeFailure.Timeout ->
                    UserVerificationErrorDetails.Failed(UserVerificationFailureReason.Timeout)
                UserVerificationNativeFailure.ProviderError ->
                    UserVerificationErrorDetails.Failed(UserVerificationFailureReason.ProviderError)
                UserVerificationNativeFailure.ServiceError ->
                    UserVerificationErrorDetails.Failed(UserVerificationFailureReason.ServiceError)
            },
        message = message,
    )

/** Which failures mean the device cannot verify at all. */
internal fun unavailableReasonOf(
    failure: UserVerificationNativeFailure,
): UserVerificationUnavailableReason? =
    when (failure) {
        UserVerificationNativeFailure.CredentialMissing ->
            UserVerificationUnavailableReason.CredentialMissing
        UserVerificationNativeFailure.BiometricsUnavailable ->
            UserVerificationUnavailableReason.BiometricsUnavailable
        UserVerificationNativeFailure.ProviderUnavailable ->
            UserVerificationUnavailableReason.ProviderUnavailable
        else -> null
    }

/**
 * BiometricPrompt ERROR_* codes folded into the closed set; unknown codes are a service fault,
 * never a user decision.
 */
internal fun biometricFailureOf(code: Int): UserVerificationNativeFailure =
    when (code) {
        1 -> UserVerificationNativeFailure.ProviderError // ERROR_HW_UNAVAILABLE
        2 -> UserVerificationNativeFailure.AuthenticationFailed // ERROR_UNABLE_TO_PROCESS
        3 -> UserVerificationNativeFailure.Timeout // ERROR_TIMEOUT
        4 -> UserVerificationNativeFailure.AuthenticationFailed // ERROR_NO_SPACE
        5 -> UserVerificationNativeFailure.Interrupted // ERROR_CANCELED
        7 -> UserVerificationNativeFailure.AuthenticationFailed // ERROR_LOCKOUT
        8 -> UserVerificationNativeFailure.ProviderError // ERROR_VENDOR
        9 -> UserVerificationNativeFailure.AuthenticationFailed // ERROR_LOCKOUT_PERMANENT
        10 -> UserVerificationNativeFailure.UserCancelled // ERROR_USER_CANCELED
        11 -> UserVerificationNativeFailure.BiometricsUnavailable // ERROR_NO_BIOMETRICS
        12 -> UserVerificationNativeFailure.ProviderUnavailable // ERROR_HW_NOT_PRESENT
        14 -> UserVerificationNativeFailure.BiometricsUnavailable // ERROR_NO_DEVICE_CREDENTIAL
        15 -> UserVerificationNativeFailure.ServiceError // ERROR_SECURITY_UPDATE_REQUIRED
        17 -> UserVerificationNativeFailure.ProviderUnavailable // ERROR_UNSUPPORTED
        else -> UserVerificationNativeFailure.ServiceError
    }

private const val ChallengeMaxBytes = 4096
private const val SignatureAlgorithm = "SHA256withECDSA"
private const val CredentialAlgorithm = "ecdsaP256Sha256X962"
private const val AndroidKeyStore = "AndroidKeyStore"

/** The wire challenge is 1–4096 raw bytes; a sign outside that range is a malformed request. */
internal fun validateChallengeBytes(challenge: ByteArray): ByteArray {
    if (challenge.isEmpty() || challenge.size > ChallengeMaxBytes) {
        throw userVerificationError(
            UserVerificationNativeFailure.InvalidParams,
            "challenge must decode to 1 to $ChallengeMaxBytes bytes",
        )
    }
    return challenge
}

/** Unpadded base64url to raw bytes; padding is refused (codex-rs/app-server/…/user_verification_adapter.rs). */
internal fun decodeChallenge(challenge: String): ByteArray {
    if (challenge.contains('=')) {
        throw userVerificationError(
            UserVerificationNativeFailure.InvalidParams,
            "challenge must be unpadded base64url",
        )
    }
    val bytes =
        runCatching { Base64.getUrlDecoder().decode(challenge) }.getOrElse {
            throw userVerificationError(
                UserVerificationNativeFailure.InvalidParams,
                "challenge is not base64url",
            )
        }
    return validateChallengeBytes(bytes)
}

internal fun userVerificationRequest(
    challenge: String,
    title: String,
    description: String,
): UserVerificationRequest = UserVerificationRequest(decodeChallenge(challenge), title, description)

/** credentialId = unpadded base64url of SHA-256 over the SPKI DER (user-verification/…/credential.rs). */
internal fun credentialIdFromSpki(spkDer: ByteArray): String =
    Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(spkDer))

/** The enroll metadata of one SPKI DER key; the algorithm string is the upstream literal. */
internal fun keyInfoFromSpki(spkDer: ByteArray): UserVerificationKeyInfo =
    UserVerificationKeyInfo(
        credentialId = credentialIdFromSpki(spkDer),
        algorithm = CredentialAlgorithm,
        publicKey = Base64.getUrlEncoder().withoutPadding().encodeToString(spkDer),
    )

/** The wire signature: ASN.1 DER ECDSA output, unpadded base64url (user-verification/…/credential.rs). */
internal fun encodeSignature(der: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(der)

/**
 * SHA-256 is applied exactly once inside the algorithm — the "hashed once" contract of the
 * macOS provider (platform_macos/provider.rs).
 */
internal fun signChallenge(privateKey: PrivateKey, challenge: ByteArray): String {
    val signer = Signature.getInstance(SignatureAlgorithm)
    signer.initSign(privateKey)
    signer.update(validateChallengeBytes(challenge))
    return encodeSignature(signer.sign())
}

/**
 * Keystore keys are auth-bound per use, so the sign only succeeds inside a prompt that presents
 * the same initialized [Signature].
 */
internal interface UserVerificationBiometricGate {
    /** Hardware/readiness problem, or null when a strong biometric can gate signing. */
    fun failure(): UserVerificationNativeFailure?

    suspend fun authenticate(signature: Signature, title: String, description: String)
}

/** `android.hardware.biometrics.BiometricPrompt` with a strong-biometric CryptoObject gate. */
internal class BiometricPromptGate(
    private val context: () -> android.content.Context?,
) : UserVerificationBiometricGate {

    override fun failure(): UserVerificationNativeFailure? {
        val manager =
            context()
                ?.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
                ?: return UserVerificationNativeFailure.ProviderUnavailable
        val authenticators =
            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
        return when (manager.canAuthenticate(authenticators)) {
            android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS -> null
            android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            android.hardware.biometrics.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                UserVerificationNativeFailure.ProviderUnavailable
            else -> UserVerificationNativeFailure.BiometricsUnavailable
        }
    }

    override suspend fun authenticate(
        signature: Signature,
        title: String,
        description: String,
    ) {
        val ctx =
            context()
                ?: throw userVerificationError(UserVerificationNativeFailure.ProviderUnavailable)
        suspendCancellableCoroutine { continuation ->
            val prompt =
                android.hardware.biometrics.BiometricPrompt.Builder(ctx)
                    .setTitle(title)
                    .setDescription(description)
                    .setAllowedAuthenticators(
                        android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG,
                    )
                    .build()
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            prompt.authenticate(
                android.hardware.biometrics.BiometricPrompt.CryptoObject(signature),
                signal,
                ctx.mainExecutor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult,
                    ) {
                        if (continuation.isActive) continuation.resume(Unit)
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(
                                userVerificationError(biometricFailureOf(errorCode), errString.toString()),
                            )
                        }
                    }

                    // A non-matching finger is not a terminal state; the prompt keeps running.
                    override fun onAuthenticationFailed() = Unit
                },
            )
        }
    }
}

/**
 * Auth-bound EC P-256 key behind [UserVerificationBiometricGate]; encoding and error mapping stay
 * in the pure functions above so they run on the JVM.
 */
internal class AndroidKeystoreUserVerificationProvider(
    private val biometrics: UserVerificationBiometricGate,
    private val keyAlias: String = "codex_user_verification",
) : UserVerificationProvider {

    override suspend fun status(): UserVerificationStatus {
        val problem = biometrics.failure()
        if (problem != null) {
            return UserVerificationStatus(
                credential = null,
                unavailableReason = unavailableReasonOf(problem),
                unavailableMessage = problem.name,
            )
        }
        return UserVerificationStatus(
            credential = loadKeyInfo(),
            unavailableReason = null,
            unavailableMessage = null,
        )
    }

    override suspend fun ensureKey(): UserVerificationKeyCreation {
        val existing = loadKeyInfo()
        if (existing != null) return UserVerificationKeyCreation(created = false, credential = existing)
        val generator = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, AndroidKeyStore)
        generator.initialize(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                // Per-use strong-biometric binding: the key refuses to sign outside a live prompt.
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .build(),
        )
        generator.generateKeyPair()
        val credential =
            loadKeyInfo()
                ?: throw userVerificationError(UserVerificationNativeFailure.ProviderError)
        return UserVerificationKeyCreation(created = true, credential = credential)
    }

    override suspend fun delete(): UserVerificationKeyDeletion {
        val existing = loadKeyInfo() ?: return UserVerificationKeyDeletion(deletedCredentialId = null)
        keyStore().deleteEntry(keyAlias)
        return UserVerificationKeyDeletion(deletedCredentialId = existing.credentialId)
    }

    override suspend fun verify(request: UserVerificationRequest): UserVerificationProof {
        validateChallengeBytes(request.challenge)
        val info = loadKeyInfo() ?: throw userVerificationError(UserVerificationNativeFailure.CredentialMissing)
        val privateKey =
            (keyStore().getEntry(keyAlias, null) as? KeyStore.PrivateKeyEntry)?.privateKey
                ?: throw userVerificationError(UserVerificationNativeFailure.CredentialMissing)
        // The prompt must gate this exact signer instance, so init happens before authentication.
        val signer = Signature.getInstance(SignatureAlgorithm)
        signer.initSign(privateKey)
        biometrics.authenticate(signer, request.title, request.description)
        signer.update(request.challenge)
        return UserVerificationProof(credentialId = info.credentialId, signature = encodeSignature(signer.sign()))
    }

    private fun keyStore(): KeyStore =
        runCatching { KeyStore.getInstance(AndroidKeyStore).apply { load(null) } }
            .getOrElse { throw userVerificationError(UserVerificationNativeFailure.ProviderUnavailable) }

    private fun loadKeyInfo(): UserVerificationKeyInfo? {
        val certificate = keyStore().getCertificate(keyAlias) ?: return null
        return keyInfoFromSpki(certificate.publicKey.encoded)
    }
}
