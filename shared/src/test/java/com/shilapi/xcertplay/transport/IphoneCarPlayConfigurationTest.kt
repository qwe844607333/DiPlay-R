package com.shilapi.xcertplay.transport

import android.hardware.usb.UsbConfiguration
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [26],
    manifest = Config.NONE,
    shadows = [
        IphoneCarPlayConfigurationTest.Device::class,
        IphoneCarPlayConfigurationTest.Configuration::class,
        IphoneCarPlayConfigurationTest.Interface::class,
        IphoneCarPlayConfigurationTest.Endpoint::class,
        IphoneCarPlayConfigurationTest.Connection::class,
    ],
)
class IphoneCarPlayConfigurationTest {

    @Test
    fun fallbacksToDeviceInterfacesWhenPlatformConfigurationsEmpty() {
        val device = Shadow.newInstanceOf(UsbDevice::class.java).also {
            Shadow.extract<Device>(it).interfaces = listOf(
                newInterface(0, 0xff, 0xfe, 2, listOf(endpoint(0x04), endpoint(0x85))),
                newInterface(1, 2, 13, 0, emptyList()),
                newInterface(2, 0xff, 0xfd, 1, emptyList()),
            )
        }
        val connection = Shadow.newInstanceOf(UsbDeviceConnection::class.java)

        val config = IphoneCarPlayConfiguration.find(device, connection)
        assertNotNull("Configuration should be found through fallback", config)
        assertEquals(1, config!!.id)
        assertNotNull(IphoneCarPlayConfiguration.usbMuxInterface(config))
        val endpoints = IphoneCarPlayConfiguration.usbMuxEndpoints(IphoneCarPlayConfiguration.usbMuxInterface(config)!!)
        assertNotNull(endpoints)
        assertEquals(0x04, endpoints!!.first.address)
        assertEquals(0x85, endpoints.second.address)
    }

    @Test
    fun findsConfigurationDirectlyFromDeviceWithoutConnection() {
        val device = Shadow.newInstanceOf(UsbDevice::class.java).also {
            Shadow.extract<Device>(it).interfaces = listOf(
                newInterface(0, 0xff, 0xfe, 2, listOf(endpoint(0x04), endpoint(0x85))),
                newInterface(1, 2, 13, 0, emptyList()),
            )
        }
        val config = IphoneCarPlayConfiguration.find(device, null)
        assertNotNull(config)
        assertEquals(1, config!!.id)
    }

    private fun newInterface(
        id: Int,
        clazz: Int,
        subclass: Int,
        protocol: Int,
        endpoints: List<UsbEndpoint>,
    ): UsbInterface = Shadow.newInstanceOf(UsbInterface::class.java).also {
        Shadow.extract<Interface>(it).apply {
            this.number = id
            this.classCode = clazz
            this.subclass = subclass
            this.protocol = protocol
            this.pipes = endpoints
        }
    }

    private fun endpoint(address: Int): UsbEndpoint =
        Shadow.newInstanceOf(UsbEndpoint::class.java).also {
            Shadow.extract<Endpoint>(it).endpointAddress = address
        }

    @Implements(UsbDevice::class)
    class Device {
        var interfaces = emptyList<UsbInterface>()
        @Implementation fun getConfigurationCount(): Int = 0
        @Implementation fun getConfiguration(index: Int): UsbConfiguration = throw IndexOutOfBoundsException()
        @Implementation fun getInterfaceCount(): Int = interfaces.size
        @Implementation fun getInterface(index: Int): UsbInterface = interfaces[index]
    }

    @Implements(UsbConfiguration::class)
    class Configuration {
        var interfaces = emptyList<UsbInterface>()
        @Implementation fun getId(): Int = 1
        @Implementation fun getInterfaceCount(): Int = interfaces.size
        @Implementation fun getInterface(index: Int): UsbInterface = interfaces[index]
    }

    @Implements(UsbInterface::class)
    class Interface {
        var number = 0
        var classCode = 0
        var subclass = 0
        var protocol = 0
        var pipes = emptyList<UsbEndpoint>()
        @Implementation fun getId(): Int = number
        @Implementation fun getAlternateSetting(): Int = 0
        @Implementation fun getInterfaceClass(): Int = classCode
        @Implementation fun getInterfaceSubclass(): Int = subclass
        @Implementation fun getInterfaceProtocol(): Int = protocol
        @Implementation fun getEndpointCount(): Int = pipes.size
        @Implementation fun getEndpoint(index: Int): UsbEndpoint = pipes[index]
    }

    @Implements(UsbEndpoint::class)
    class Endpoint {
        var endpointAddress = 0
        @Implementation fun getAddress(): Int = endpointAddress
        @Implementation fun getDirection(): Int = endpointAddress and 0x80
        @Implementation fun getType(): Int = UsbConstants.USB_ENDPOINT_XFER_BULK
    }

    @Implements(UsbDeviceConnection::class)
    class Connection {
        @Implementation fun getRawDescriptors(): ByteArray? = null
        @Implementation fun controlTransfer(
            requestType: Int, request: Int, value: Int, index: Int,
            buffer: ByteArray?, length: Int, timeout: Int,
        ): Int = -1
        @Implementation fun close() {}
    }
}
