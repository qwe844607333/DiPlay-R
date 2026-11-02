package com.shilapi.xcertplay.usbbt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream

class SdpClientTest {

    @Test
    fun testParseResponseAndExtractRfcommChannelFromRealSdpServer() {
        val identity = UsbBtIdentity(name = "CarPlay-Test", iap2RfcommChannel = 5)
        val records = SdpCodec.defaultRecords(identity)
        val server = SdpServer(records)

        // Build a query for iAP2 Accessory UUID (as an accessory / server record)
        val pattern = DataElement.Sequence(DataElement.Uuid.of128(UsbBtIdentity.IAP2_ACCESSORY_UUID)).encode()
        val attrRanges = DataElement.Sequence(
            DataElement.UInt(4, 0x0000FFFFL)
        ).encode()

        val reqPayload = ByteArrayOutputStream()
        reqPayload.write(pattern)
        reqPayload.write(0x02)
        reqPayload.write(0x00)
        reqPayload.write(attrRanges)
        reqPayload.write(0x00)

        val request = ByteArrayOutputStream()
        request.write(SdpCodec.SERVICE_SEARCH_ATTRIBUTE_REQUEST)
        request.write(0x00) // TID = 0x0001
        request.write(0x01)
        val params = reqPayload.toByteArray()
        request.write((params.size ushr 8) and 0xFF)
        request.write(params.size and 0xFF)
        request.write(params)

        val response = server.handle(request.toByteArray(), peerMtu = 1000)
        assertNotNull(response)

        val parsedDirect = SdpClient.parseResponse(response, 0x0001)
        assertNotNull(parsedDirect)

        val channel = SdpClient.extractRfcommChannel(parsedDirect!!)
        assertEquals(5, channel)
    }

    @Test
    fun testExtractRfcommScannerDirect() {
        // Simulate arbitrary nested payload containing UUID16 RFCOMM (0x09 0x00 0x03) and channel 7 (0x08 0x07)
        val payload = byteArrayOf(
            0x35, 0x10,
            0x35, 0x0E,
            0x09, 0x00, 0x01, // L2CAP
            0x09, 0x00, 0x03, // RFCOMM
            0x08, 0x07,       // Channel 7
        )
        val parsed = SdpClient.ParsedResponse(payload)
        val channel = SdpClient.extractRfcommChannel(parsed)
        assertEquals(7, channel)
    }

    @Test
    fun testExtractRfcommChannelWithLargeDes0x36() {
        // Outer DES with 2-byte length (tag 0x36): length = 300
        val dummyPadding = ByteArray(280)
        val rfcommBlock = byteArrayOf(
            0x09, 0x00, 0x03, // RFCOMM UUID
            0x08, 0x03,       // Channel 3
        )
        val inner = dummyPadding + rfcommBlock
        val len = inner.size
        val out = byteArrayOf(
            0x36.toByte(),
            ((len ushr 8) and 0xFF).toByte(),
            (len and 0xFF).toByte(),
        ) + inner

        val parsed = SdpClient.ParsedResponse(out)
        val channel = SdpClient.extractRfcommChannel(parsed)
        assertEquals(3, channel)
    }
}
