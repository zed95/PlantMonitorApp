package com.example.testproject

import java.nio.ByteBuffer

const val SOP: Byte = 0x00    // Start of packet identifier
const val wifiConnect: Byte = 0x01    // connect to wifi command identifier
const val wifiConnectSts: Byte = 0x02
const val testChecksum: Byte = 0x11

fun msgConnectEsp32ToWifi(ssid: String, password: String): ByteArray
{
    val msg = mutableListOf<Byte>()
    val numSsidLenBytes = Int.SIZE_BYTES
    val nunPasswordLenBytes = Int.SIZE_BYTES
    val numChecksumLenBytes = Byte.SIZE_BYTES
    val payloadSize = ssid.length + password.length + numSsidLenBytes + nunPasswordLenBytes + numChecksumLenBytes
    // converts the ssid length to byte buffer holding 4 bytes that make up ssid length, then to a byte array and finally to a list of bytes
    val ssidSizeBytes = ByteBuffer.allocate(4).putInt(ssid.length).array().toList()
    val passwordSizeBytes = ByteBuffer.allocate(4).putInt(password.length).array().toList()
    val payloadSizeBytes = ByteBuffer.allocate(4).putInt(payloadSize).array().toList()

    msg.add(SOP)
    msg.add(wifiConnect)
    msg.addAll(payloadSizeBytes)
    msg.addAll(ssidSizeBytes)
    msg.addAll(passwordSizeBytes)
    msg.addAll(ssid.toByteArray().toList())
    msg.addAll(password.toByteArray().toList())
    msg.add(testChecksum)

    return msg.toByteArray()
}

fun msgRequestWifiConnectSts(): ByteArray
{
    val msg = mutableListOf<Byte>()
    val numChecksumLenBytes = Byte.SIZE_BYTES
    val payloadSize =  numChecksumLenBytes
    val payloadSizeBytes = ByteBuffer.allocate(4).putInt(payloadSize).array().toList()

    msg.add(SOP)
    msg.add(wifiConnectSts)
    msg.addAll(payloadSizeBytes)
    msg.add(testChecksum)

    return msg.toByteArray()
}

fun bytesToInt(bytes: ByteArray, offset: Int): Int
{
    var result: Int = -1

    bytes[offset].toInt()

    return  result
}