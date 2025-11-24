package com.example.plantmonitorapp

import java.nio.ByteBuffer

const val SOP: Byte = 0x7E    // Start of packet identifier
const val EOP: Byte = 0X7F    // End of packet identifier
const val ESCAPE: Byte = 0x7D
const val SOP_STUFF: Byte  = 0x5E
const val EOP_STUFF: Byte  = 0x5F
const val ESCAPE_STUFF: Byte = 0x5D
const val  NON_SOP_BYTE: Byte = 0x7E
const val  NON_EOP_BYTE: Byte = 0x7F
const val NON_ESCAPE_BYTE: Byte = 0x7D
const val wifiConnect: Byte = 0x01    // connect to wifi command identifier
const val REQUEST_ESP32_WIFI_STS: Byte = 0x02
const val REQUEST_RSP_ESP32_WIFI_STS: Byte = 0x03
const val testChecksum: Byte = 0x11

// destuffing error codes
const val ERR_BUF_OVERFLOW: Byte =   -2
const val ERR_INVALID_ESCAPE: Byte = -3
const val ERR_ESCAPE_AT_END: Byte =  -4

fun msgConnectEsp32ToWifi(ssid: String, password: String): ByteArray
{
    val msg = mutableListOf<Byte>()
    val stuffedMsg = mutableListOf<Byte>()
    val numSsidLenBytes = Int.SIZE_BYTES
    val nunPasswordLenBytes = Int.SIZE_BYTES
    val numChecksumLenBytes = Byte.SIZE_BYTES
    val payloadSize = ssid.length + password.length + numSsidLenBytes + nunPasswordLenBytes + numChecksumLenBytes
    // converts the ssid length to byte buffer holding 4 bytes that make up ssid length, then to a byte array and finally to a list of bytes
    val ssidSizeBytes = ByteBuffer.allocate(4).putInt(ssid.length).array().toList()
    val passwordSizeBytes = ByteBuffer.allocate(4).putInt(password.length).array().toList()
    val payloadSizeBytes = ByteBuffer.allocate(4).putInt(payloadSize).array().toList()
    var checksum: Byte = 0

    msg.add(wifiConnect)
    msg.addAll(payloadSizeBytes)
    msg.addAll(ssidSizeBytes)
    msg.addAll(passwordSizeBytes)
    msg.addAll(ssid.toByteArray().toList())
    msg.addAll(password.toByteArray().toList())
    // get checksum for header and payload bytes and add to packet
    checksum = calcChecksum(msg.toByteArray(), msg.size)
    msg.add(checksum)

    // stuff ID, Payload, Checksum
    stuffedMsg.addAll(stuffPacket(msg.toByteArray(), msg.size).toList())
    // add SOP and EOP
    stuffedMsg.add(0, SOP) // insert SOP at beginning of list
    stuffedMsg.add(EOP) // add end of packet identifier

    return stuffedMsg.toByteArray()
}

fun msgRequestWifiConnectSts(): ByteArray
{
    val msg = mutableListOf<Byte>()
    val numChecksumLenBytes = Byte.SIZE_BYTES
    val payloadSize =  numChecksumLenBytes
    val payloadSizeBytes = ByteBuffer.allocate(4).putInt(payloadSize).array().toList()
    val stuffedMsg = mutableListOf<Byte>()
    var checksum: Byte = 0

    msg.add(REQUEST_ESP32_WIFI_STS)
    msg.addAll(payloadSizeBytes)
    // get checksum for header and payload bytes and add to packet
    checksum = calcChecksum(msg.toByteArray(), msg.size)
    msg.add(checksum)

    // stuff ID, Payload, Checksum
    stuffedMsg.addAll(stuffPacket(msg.toByteArray(), msg.size).toList())
    // add SOP and EOP
    stuffedMsg.add(0, SOP) // insert SOP at beginning of list
    stuffedMsg.add(EOP) // add end of packet identifier

    return stuffedMsg.toByteArray()
}

fun bytesToInt(bytes: ByteArray, offset: Int): Int
{
    var result: Int = -1

    bytes[offset].toInt()

    return  result
}

/*
* Byte in Kotlin is signed (–128..127). In C, uint8_t is unsigned (0..255).
* That’s why we do (data[i].toInt() and 0xFF) → converts signed byte to an unsigned 0–255 value.
* We keep sum as Int to avoid overflow while adding.
* At the end, we apply two’s complement: ~sum + 1 → in Kotlin: (sum.inv() + 1).
* & 0xFF ensures the result is clamped to 8 bits before casting back to Byte.
* */
fun calcChecksum(data: ByteArray, len: Int): Byte {
    var sum = 0

    for (i in 0 until len) {
        sum = (sum + (data[i].toInt() and 0xFF)) and 0xFF
    }

    val checksum = ((sum.inv() + 1) and 0xFF)
    println("checksum: $checksum")
    return checksum.toByte()
}


fun stuffPacket(packet: ByteArray, len: Int): ByteArray
{
    val tmpBuf = mutableListOf<Byte>()

    for(i in 0 until len)
    {
        if(packet[i] == SOP)
        {
            tmpBuf.add(ESCAPE)
            tmpBuf.add(SOP_STUFF)
        }
        else if(packet[i] == EOP)
        {
            tmpBuf.add(ESCAPE)
            tmpBuf.add(EOP_STUFF)
        }
        else if(packet[i] == ESCAPE)
        {
            tmpBuf.add(ESCAPE)
            tmpBuf.add(ESCAPE_STUFF)
        }
        else
        {
            tmpBuf.add(packet[i])
        }
    }

    return tmpBuf.toByteArray()
}

fun unstuffPacket(packet: ByteArray, len: Int):  Int
{
    val tmpBuf = mutableListOf<Byte>()
    var errorCode: Byte = 0
    var newLen = 0
    var i = 0

    while((i < len) && (errorCode == 0.toByte()))
    {
        if(packet[i] == ESCAPE)
        {
            // this is the last byte in the packet
            if(i == (len - 1))
            {
                // report error
                errorCode = ERR_ESCAPE_AT_END
            }
            else
            {
                if(packet[i + 1] == SOP_STUFF)
                {
                    tmpBuf.add(NON_SOP_BYTE)
                    i++
                }
                else if(packet[i + 1] == EOP_STUFF)
                {
                    tmpBuf.add(NON_EOP_BYTE)
                    i++
                }
                else if(packet[i + 1] == ESCAPE_STUFF)
                {
                    tmpBuf.add(NON_ESCAPE_BYTE)
                    i++
                }
                else
                {
                    errorCode = ERR_INVALID_ESCAPE
                }
            }
        }
        else
        {
            tmpBuf.add(packet[i])
        }

        i++
    }

    if(errorCode == 0.toByte())
    {
        for(x in 0 .. tmpBuf.lastIndex)
        {
            packet[x] = tmpBuf[x]
        }

        newLen = tmpBuf.size
    }
    else
    {
        newLen = errorCode.toInt()
    }

    return newLen
}

fun RemSopEop(packet: ByteArray)
{
    val tmpBuf = mutableListOf<Byte>()

    // remove SOP and EOP
    tmpBuf.addAll(packet.toList())
    tmpBuf.removeAt(0)
    tmpBuf.removeAt(tmpBuf.lastIndex)

    // reorganise the array with SOP and EOP removed
    for(x in 0 .. tmpBuf.lastIndex)
    {
        packet[x] = tmpBuf[x]
    }
}
