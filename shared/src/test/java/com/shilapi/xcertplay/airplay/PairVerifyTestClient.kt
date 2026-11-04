package com.shilapi.xcertplay.airplay

/** A real signed controller exchange for protocol and media fixtures; no production auth bypass. */
internal class PairVerifyTestClient(
    val pairings: PairingStore = PairingStore(),
    private val controller: AirPlayIdentity = AirPlayIdentity.generate(),
) {
    private var ephemeral = AirPlayCrypto.x25519Generate()
    private var secret: ByteArray? = null
    val controllerId: String get() = controller.pairingId
    val shared: ByteArray get() = checkNotNull(secret).copyOf()

    init { pairings.save(controller.pairingId, controller.publicKey) }

    fun m1(): ByteArray {
        ephemeral = AirPlayCrypto.x25519Generate()
        secret = null
        return Tlv8Codec.encode(listOf(
            Tlv8Item(6, byteArrayOf(1)),
            Tlv8Item(3, ephemeral.publicKey),
        ))
    }

    fun m3(m2: ByteArray, badSignature: Boolean = false): ByteArray {
        val response = Tlv8Codec.decode(m2)
        check(response[7] == null && response[6]?.single() == 2.toByte())
        val serverPublic = checkNotNull(response[3])
        val shared = AirPlayCrypto.x25519Shared(ephemeral.privateKey, serverPublic)
        secret = shared
        val encryptionKey = AirPlayCrypto.hkdfSha512(shared,
            "Pair-Verify-Encrypt-Salt".asciiBytes(), "Pair-Verify-Encrypt-Info".asciiBytes())
        // Receiving a valid encrypted M2 also checks that the two sides derived the same secret.
        AirPlayCrypto.chachaOpen(encryptionKey, AirPlayCrypto.nonceLabel("PV-Msg02"),
            checkNotNull(response[5]))
        val identifier = controller.pairingId.asciiBytes()
        val signature = AirPlayCrypto.ed25519Sign(controller.privateKey,
            concatBytes(ephemeral.publicKey, identifier, serverPublic))
        if (badSignature) signature[0] = (signature[0].toInt() xor 1).toByte()
        val proof = Tlv8Codec.encode(listOf(Tlv8Item(1, identifier), Tlv8Item(10, signature)))
        return Tlv8Codec.encode(listOf(
            Tlv8Item(6, byteArrayOf(3)),
            Tlv8Item(5, AirPlayCrypto.chachaSeal(encryptionKey,
                AirPlayCrypto.nonceLabel("PV-Msg03"), proof)),
        ))
    }

    fun authenticate(verify: PairVerify): ByteArray {
        val m4 = Tlv8Codec.decode(verify.handle(m3(verify.handle(m1()))))
        check(m4[7] == null && m4[6]?.single() == 4.toByte())
        check(verify.isVerified && verify.verifiedControllerId == controllerId)
        return shared
    }

    fun controlCipher(): ControlCipher = ControlCipher(
        AirPlayCrypto.hkdfSha512(shared, "Control-Salt".asciiBytes(),
            "Control-Read-Encryption-Key".asciiBytes()),
        AirPlayCrypto.hkdfSha512(shared, "Control-Salt".asciiBytes(),
            "Control-Write-Encryption-Key".asciiBytes()),
    )
}
