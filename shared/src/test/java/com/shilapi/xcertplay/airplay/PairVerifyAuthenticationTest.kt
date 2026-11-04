package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class PairVerifyAuthenticationTest {
    private fun verifier(client: PairVerifyTestClient) =
        PairVerify(AirPlayIdentity.generate(), client.pairings)

    private fun assertRejected(response: ByteArray) {
        assertNotNull(Tlv8Codec.decode(response)[7])
    }

    @Test fun temporaryExchangeDoesNotPublishAuthenticatedMaterial() {
        val client = PairVerifyTestClient()
        val verify = verifier(client)
        val m2 = verify.handle(client.m1())
        assertFalse(verify.isVerified)
        assertNull(verify.shared)
        assertNull(verify.controlKeys)
        assertNull(verify.verifiedControllerId)
        val m4 = verify.handle(client.m3(m2))
        assertNull(Tlv8Codec.decode(m4)[7])
        assertTrue(verify.isVerified)
        assertArrayEquals(client.shared, verify.shared)
        assertNotNull(verify.controlKeys)
        assertEquals(client.controllerId, verify.verifiedControllerId)
    }

    @Test fun failedProofRequiresAFreshChallengeRatherThanRetryingTheOldProof() {
        val client = PairVerifyTestClient()
        val verify = verifier(client)
        val m2 = verify.handle(client.m1())
        val valid = client.m3(m2)
        assertRejected(verify.handle(client.m3(m2, badSignature = true)))
        assertNull(verify.shared)
        assertNull(verify.controlKeys)
        assertFalse(verify.isVerified)
        assertRejected(verify.handle(valid))
        assertFalse(verify.isVerified)
        client.authenticate(verify)
        assertTrue(verify.isVerified)
    }

    @Test fun unknownControllerCannotAcquireAuthenticatedMaterial() {
        val client = PairVerifyTestClient()
        val verify = verifier(client)
        val m3 = client.m3(verify.handle(client.m1()))
        client.pairings.clear()
        assertRejected(verify.handle(m3))
        assertFalse(verify.isVerified)
        assertNull(verify.shared)
        assertNull(verify.controlKeys)
    }

    @Test fun badTagAndMalformedTlvClearPendingChallenges() {
        for (malformed in listOf(false, true)) {
            val client = PairVerifyTestClient()
            val verify = verifier(client)
            val valid = client.m3(verify.handle(client.m1()))
            val bad = if (malformed) byteArrayOf(6, 2, 1) else valid.copyOf().also {
                it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
            }
            assertRejected(verify.handle(bad))
            assertNull(verify.shared)
            assertRejected(verify.handle(valid))
            assertFalse(verify.isVerified)
        }
    }

    @Test fun aNewChallengeInvalidatesThePreviousPendingExchange() {
        val client = PairVerifyTestClient()
        val verify = verifier(client)
        val oldProof = client.m3(verify.handle(client.m1()))
        verify.handle(client.m1())
        assertRejected(verify.handle(oldProof))
        assertNull(verify.shared)
        client.authenticate(verify)
    }

    @Test fun anotherHandshakeCannotReplaceAnEstablishedIdentityOrKeys() {
        val client = PairVerifyTestClient()
        val verify = verifier(client)
        val secret = client.authenticate(verify)
        val keys = checkNotNull(verify.controlKeys)
        val another = PairVerifyTestClient(client.pairings)
        assertRejected(verify.handle(another.m1()))
        assertRejected(verify.handle(byteArrayOf(6, 2, 1)))
        assertTrue(verify.isVerified)
        assertEquals(client.controllerId, verify.verifiedControllerId)
        assertArrayEquals(secret, verify.shared)
        assertArrayEquals(keys.readKey, verify.controlKeys!!.readKey)
        assertArrayEquals(keys.writeKey, verify.controlKeys!!.writeKey)
    }

    @Test fun publishedSharedSecretIsADefensiveCopy() {
        val client = PairVerifyTestClient()
        val verify = verifier(client)
        val expected = client.authenticate(verify)
        verify.shared!![0] = (expected[0].toInt() xor 1).toByte()
        assertArrayEquals(expected, verify.shared)
    }
}
