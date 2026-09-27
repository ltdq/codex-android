package com.cy.codex.app

import com.cy.codex.protocol.protocol.v2.UserVerificationErrorDetails
import com.cy.codex.protocol.protocol.v2.UserVerificationFailureReason
import com.cy.codex.protocol.protocol.v2.UserVerificationProof
import com.cy.codex.protocol.protocol.v2.UserVerificationCancellationReason
import com.cy.codex.protocol.protocol.v2.UserVerificationUnavailableReason
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure half of the Android Keystore provider (user-verification/…/credential.rs and
 * codex-rs/app-server-protocol/…/user_verification.rs); Keystore and BiometricPrompt stay out of the JVM.
 */
class UserVerificationKeystoreTest {

    private fun spkiOf(keyPair: KeyPair): ByteArray = keyPair.public.encoded

    private fun softwareKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    // --- credential encoding ---

    @Test
    fun `credential id is the unpadded base64url of the spki digest`() {
        val spki = byteArrayOf(1, 2, 3, 4, 5)
        val expected =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(spki))
        val credentialId = credentialIdFromSpki(spki)
        assertEquals(expected, credentialId)
        assertFalse(credentialId.contains('='), "the wire encoding is unpadded")
        assertFalse(credentialId.contains('+') || credentialId.contains('/'))
    }

    @Test
    fun `key info pins the upstream algorithm name`() {
        val keyPair = softwareKeyPair()
        val info = keyInfoFromSpki(spkiOf(keyPair))
        assertEquals("ecdsaP256Sha256X962", info.algorithm)
        assertEquals(
            Base64.getUrlEncoder().withoutPadding().encodeToString(spkiOf(keyPair)),
            info.publicKey,
        )
        assertEquals(credentialIdFromSpki(spkiOf(keyPair)), info.credentialId)
    }

    // --- challenge validation ---

    @Test
    fun `challenge decoding accepts one to 4096 bytes`() {
        assertEquals(listOf<Byte>(0, 1, 2, 3), decodeChallenge("AAECAw").toList())
        // 5462 unpadded base64url chars are exactly 4096 bytes.
        assertEquals(4096, decodeChallenge("A".repeat(5462)).size)
    }

    @Test
    fun `challenge decoding rejects empty and oversized input`() {
        for (bad in listOf("", "A".repeat(5463), "A".repeat(5464))) {
            val error = assertFailsWith<UserVerificationException> { decodeChallenge(bad) }
            assertTrue(
                error.details is UserVerificationErrorDetails.InvalidRequest,
                "challenge of unexpected size must read as a malformed request: $bad",
            )
        }
    }

    @Test
    fun `challenge decoding rejects non base64url input`() {
        for (bad in listOf("not base64!", "AA=A", "++//")) {
            val error = assertFailsWith<UserVerificationException> { decodeChallenge(bad) }
            assertEquals(
                UserVerificationErrorDetails.InvalidRequest("invalidParams"),
                error.details,
            )
        }
    }

    @Test
    fun `the request factory decodes the challenge once`() {
        val request = userVerificationRequest("AAECAw", "Title", "Description")
        assertEquals(listOf<Byte>(0, 1, 2, 3), request.challenge.toList())
        assertEquals("Title", request.title)
        assertEquals("Description", request.description)
    }

    // --- signature encoding ---

    @Test
    fun `signatures are der encoded and unpadded base64url`() {
        val keyPair = softwareKeyPair()
        val challenge = byteArrayOf(9, 8, 7, 6)
        val encoded = signChallenge(keyPair.private, challenge)
        assertFalse(encoded.contains('='))
        val der = Base64.getUrlDecoder().decode(encoded)

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(keyPair.public)
        verifier.update(challenge)
        assertTrue(verifier.verify(der), "the raw challenge must verify against the DER signature")
    }

    @Test
    fun `signing rejects an out of range challenge`() {
        val keyPair = softwareKeyPair()
        assertFailsWith<UserVerificationException> { signChallenge(keyPair.private, ByteArray(0)) }
        assertFailsWith<UserVerificationException> {
            signChallenge(keyPair.private, ByteArray(4097))
        }
    }

    // --- closed error mapping ---

    @Test
    fun `biometric prompt codes map onto native failure signals`() {
        assertEquals(UserVerificationNativeFailure.UserCancelled, biometricFailureOf(10))
        assertEquals(UserVerificationNativeFailure.Interrupted, biometricFailureOf(5))
        assertEquals(UserVerificationNativeFailure.Timeout, biometricFailureOf(3))
        assertEquals(UserVerificationNativeFailure.AuthenticationFailed, biometricFailureOf(7))
        assertEquals(UserVerificationNativeFailure.BiometricsUnavailable, biometricFailureOf(11))
        assertEquals(UserVerificationNativeFailure.ProviderUnavailable, biometricFailureOf(12))
        assertEquals(UserVerificationNativeFailure.ServiceError, biometricFailureOf(15))
        assertEquals(
            UserVerificationNativeFailure.ServiceError,
            biometricFailureOf(-1),
            "an unknown code must not read as a user decision",
        )
    }

    @Test
    fun `native failures map onto the closed error set`() {
        val expected =
            mapOf(
                UserVerificationNativeFailure.InvalidParams to
                    UserVerificationErrorDetails.InvalidRequest("invalidParams"),
                UserVerificationNativeFailure.CredentialMissing to
                    UserVerificationErrorDetails.Unavailable(
                        UserVerificationUnavailableReason.CredentialMissing,
                    ),
                UserVerificationNativeFailure.BiometricsUnavailable to
                    UserVerificationErrorDetails.Unavailable(
                        UserVerificationUnavailableReason.BiometricsUnavailable,
                    ),
                UserVerificationNativeFailure.ProviderUnavailable to
                    UserVerificationErrorDetails.Unavailable(
                        UserVerificationUnavailableReason.ProviderUnavailable,
                    ),
                UserVerificationNativeFailure.UserCancelled to
                    UserVerificationErrorDetails.Cancelled(
                        UserVerificationCancellationReason.UserCancelled,
                    ),
                UserVerificationNativeFailure.Interrupted to
                    UserVerificationErrorDetails.Cancelled(
                        UserVerificationCancellationReason.Interrupted,
                    ),
                UserVerificationNativeFailure.AuthenticationFailed to
                    UserVerificationErrorDetails.Failed(
                        UserVerificationFailureReason.AuthenticationFailed,
                    ),
                UserVerificationNativeFailure.Timeout to
                    UserVerificationErrorDetails.Failed(UserVerificationFailureReason.Timeout),
                UserVerificationNativeFailure.ProviderError to
                    UserVerificationErrorDetails.Failed(UserVerificationFailureReason.ProviderError),
                UserVerificationNativeFailure.ServiceError to
                    UserVerificationErrorDetails.Failed(UserVerificationFailureReason.ServiceError),
            )
        assertEquals(expected.keys, UserVerificationNativeFailure.entries.toSet())
        for ((failure, details) in expected) {
            assertEquals(details, userVerificationError(failure).details, "mapping of $failure")
        }
    }

    @Test
    fun `only device level failures report an unavailable reason`() {
        assertEquals(
            UserVerificationUnavailableReason.CredentialMissing,
            unavailableReasonOf(UserVerificationNativeFailure.CredentialMissing),
        )
        assertEquals(
            UserVerificationUnavailableReason.BiometricsUnavailable,
            unavailableReasonOf(UserVerificationNativeFailure.BiometricsUnavailable),
        )
        assertEquals(
            UserVerificationUnavailableReason.ProviderUnavailable,
            unavailableReasonOf(UserVerificationNativeFailure.ProviderUnavailable),
        )
        assertNull(unavailableReasonOf(UserVerificationNativeFailure.UserCancelled))
        assertNull(unavailableReasonOf(UserVerificationNativeFailure.Timeout))
    }

    // --- provider seam ---

    @Test
    fun `ensure key is idempotent and reports the same credential`() = runTest {
        val provider = FakeUserVerificationProvider()
        val first = provider.ensureKey()
        val second = provider.ensureKey()
        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.credential.credentialId, second.credential.credentialId)
        assertEquals(first.credential, provider.status().credential)
    }

    @Test
    fun `verify signs the raw challenge and delete clears the credential`() = runTest {
        val provider = FakeUserVerificationProvider()
        provider.ensureKey()
        val request = userVerificationRequest("AAECAw", "Title", "Description")
        val proof = provider.verify(request)
        assertEquals(provider.status().credential?.credentialId, proof.credentialId)

        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(provider.publicKey)
        verifier.update(request.challenge)
        assertTrue(verifier.verify(Base64.getUrlDecoder().decode(proof.signature)))

        provider.delete()
        assertNull(provider.status().credential)
        assertFailsWith<UserVerificationException> { provider.verify(request) }
            .also { assertEquals(UserVerificationUnavailableReason.CredentialMissing, (it.details as UserVerificationErrorDetails.Unavailable).reason) }
    }

    @Test
    fun `deleting an absent credential is a no-op`() = runTest {
        val provider = FakeUserVerificationProvider()
        assertNull(provider.delete().deletedCredentialId)
    }
}

/** A [UserVerificationProvider] over a JVM software key, routed through the pure functions under test. */
private class FakeUserVerificationProvider : UserVerificationProvider {

    private var keyPair: KeyPair? = null

    val publicKey get() = keyPair!!.public

    override suspend fun status(): UserVerificationStatus =
        UserVerificationStatus(
            credential = keyPair?.let { keyInfoFromSpki(it.public.encoded) },
            unavailableReason = null,
            unavailableMessage = null,
        )

    override suspend fun ensureKey(): UserVerificationKeyCreation {
        val existing = keyPair
        if (existing != null) {
            return UserVerificationKeyCreation(
                created = false,
                credential = keyInfoFromSpki(existing.public.encoded),
            )
        }
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec("secp256r1"))
        val fresh = generator.generateKeyPair()
        keyPair = fresh
        return UserVerificationKeyCreation(
            created = true,
            credential = keyInfoFromSpki(fresh.public.encoded),
        )
    }

    override suspend fun delete(): UserVerificationKeyDeletion {
        val removed = keyPair?.let { keyInfoFromSpki(it.public.encoded) }
        keyPair = null
        return UserVerificationKeyDeletion(deletedCredentialId = removed?.credentialId)
    }

    override suspend fun verify(request: UserVerificationRequest): UserVerificationProof {
        val pair = keyPair ?: throw userVerificationError(UserVerificationNativeFailure.CredentialMissing)
        val info = keyInfoFromSpki(pair.public.encoded)
        return UserVerificationProof(
            credentialId = info.credentialId,
            signature = signChallenge(pair.private, request.challenge),
        )
    }
}
