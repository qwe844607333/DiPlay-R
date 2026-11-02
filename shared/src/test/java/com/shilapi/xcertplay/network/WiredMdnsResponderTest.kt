package com.shilapi.xcertplay.network

import com.shilapi.xcertplay.transport.EthernetIpv6Codec
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class WiredMdnsResponderTest {
    private val hostMac = byteArrayOf(2, 0, 0, 0, 0, 1)
    private val peerMac = byteArrayOf(2, 0, 0, 0, 0, 2)
    private val hostAddress = InetAddress.getByName("fe80::1").address
    private val peerAddress = InetAddress.getByName("fe80::2").address
    private val multicastAddress = InetAddress.getByName("ff02::fb").address
    private val responder = WiredMdnsResponder("DiPlay Test", "fe80::1", 7000, mapOf("features" to "0x1"))

    @Test fun nonQuQueryReturnsUnicastAndMulticastWithValidUdpChecksums() {
        val answers = answer(query("_airplay._tcp.local", qu = false))
        assertEquals(2, answers.size)
        val unicast = requireNotNull(EthernetIpv6Codec.parseIpv6(answers[0]))
        val multicast = requireNotNull(EthernetIpv6Codec.parseIpv6(answers[1]))
        assertArrayEquals(peerMac, unicast.destinationMac)
        assertArrayEquals(peerAddress, unicast.ipv6.copyOfRange(24, 40))
        assertArrayEquals(byteArrayOf(0x33, 0x33, 0, 0, 0, 0xfb.toByte()), multicast.destinationMac)
        assertArrayEquals(multicastAddress, multicast.ipv6.copyOfRange(24, 40))
        assertArrayEquals(unicast.ipv6.copyOfRange(48, unicast.ipv6.size), multicast.ipv6.copyOfRange(48, multicast.ipv6.size))
        for (frame in answers) {
            val packet = requireNotNull(EthernetIpv6Codec.parseIpv6(frame)).ipv6
            assertArrayEquals(hostAddress, packet.copyOfRange(8, 24))
            assertEquals(5353, u16(packet, 40))
            assertEquals(5353, u16(packet, 42))
            assertEquals(255, packet[7].toInt() and 0xff)
            assertEquals(0x8400, u16(packet, 50))
            assertTrue(u16(packet, 54) > 0)
            assertValidChecksum(packet)
        }
    }

    @Test fun quQueryKeepsOnlyTheUnicastReply() {
        val answers = answer(query("_airplay._tcp.local", qu = true))
        assertEquals(1, answers.size)
        val packet = requireNotNull(EthernetIpv6Codec.parseIpv6(answers.single()))
        assertArrayEquals(peerMac, packet.destinationMac)
        assertArrayEquals(peerAddress, packet.ipv6.copyOfRange(24, 40))
        assertValidChecksum(packet.ipv6)
    }

    @Test fun raopAndServiceEnumerationAlsoReachNonQuQuerier() {
        for (service in listOf("_raop._tcp.local", "_services._dns-sd._udp.local")) {
            val answers = answer(query(service, qu = false))
            assertEquals(service, 2, answers.size)
            val packet = requireNotNull(EthernetIpv6Codec.parseIpv6(answers[1])).ipv6
            assertEquals(if (service.startsWith("_services")) 2 else 4, u16(packet, 54))
            assertValidChecksum(packet)
        }
    }

    @Test fun unsupportedServiceLeavesTheNormalForwardingPathUntouched() {
        assertTrue(answer(query("_unrelated._tcp.local", qu = false)).isEmpty())
    }

    @Test fun truncatedDnsQuestionDoesNotGenerateAnAnswer() {
        assertTrue(answer(query("_airplay._tcp.local", qu = false).dropLast(1).toByteArray()).isEmpty())
    }

    @Test fun truncatedUdpTrailingBytesStillAnswersCompleteQuestion() {
        // Query has a padding byte at the end; frame length is shortened by 1 byte so declared UDP len > frame len
        val dnsWithPadding = query("_airplay._tcp.local", qu = true) + byteArrayOf(0)
        val packet = ByteArray(48 + dnsWithPadding.size)
        packet[0] = 0x60
        packet[6] = 17
        peerAddress.copyInto(packet, 8)
        multicastAddress.copyInto(packet, 24)
        putU16(packet, 4, packet.size - 40)
        putU16(packet, 40, 5353)
        putU16(packet, 42, 5353)
        putU16(packet, 44, packet.size - 40) // Stated UDP length includes the padding byte
        dnsWithPadding.copyInto(packet, 48)
        val frame = EthernetIpv6Codec.build(peerMac, hostMac, packet)
        // Pass frame truncated by 1 byte at the end
        val answers = responder.answerFrames(frame, 14, packet.size - 1, hostMac, peerMac)
        assertEquals(1, answers.size)
    }

    private fun answer(dns: ByteArray): List<ByteArray> {
        val packet = ByteArray(48 + dns.size)
        packet[0] = 0x60
        packet[6] = 17
        peerAddress.copyInto(packet, 8)
        multicastAddress.copyInto(packet, 24)
        putU16(packet, 4, packet.size - 40)
        putU16(packet, 40, 5353)
        putU16(packet, 42, 5353)
        putU16(packet, 44, packet.size - 40)
        dns.copyInto(packet, 48)
        val frame = EthernetIpv6Codec.build(peerMac, hostMac, packet)
        return responder.answerFrames(frame, 14, packet.size, hostMac, peerMac)
    }

    private fun query(service: String, qu: Boolean): ByteArray {
        val out = ByteArrayOutputStream()
        val header = ByteArray(12)
        putU16(header, 4, 1)
        out.write(header)
        for (label in service.split('.')) {
            val bytes = label.toByteArray(Charsets.UTF_8)
            out.write(bytes.size)
            out.write(bytes)
        }
        out.write(0)
        out.write(byteArrayOf(0, 12, if (qu) 0x80.toByte() else 0, 1))
        return out.toByteArray()
    }

    private fun assertValidChecksum(packet: ByteArray) {
        val udpLength = u16(packet, 44)
        assertEquals(packet.size - 40, udpLength)
        assertEquals(udpLength, u16(packet, 4))
        assertTrue(u16(packet, 46) != 0)
        val pseudoHeader = packet.copyOfRange(8, 40) +
            byteArrayOf(0, 0, (udpLength ushr 8).toByte(), udpLength.toByte(), 0, 0, 0, 17)
        val bytes = pseudoHeader + packet.copyOfRange(40, packet.size)
        var sum = 0L
        for (index in bytes.indices step 2) {
            sum += ((bytes[index].toInt() and 0xff) shl 8) +
                (if (index + 1 < bytes.size) bytes[index + 1].toInt() and 0xff else 0)
        }
        while (sum > 0xffff) sum = (sum and 0xffff) + (sum ushr 16)
        assertEquals("IPv6 UDP checksum", 0xffffL, sum)
    }

    private fun u16(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 8) or (bytes[offset + 1].toInt() and 0xff)

    private fun putU16(bytes: ByteArray, offset: Int, value: Int) {
        bytes[offset] = (value ushr 8).toByte()
        bytes[offset + 1] = value.toByte()
    }
}
