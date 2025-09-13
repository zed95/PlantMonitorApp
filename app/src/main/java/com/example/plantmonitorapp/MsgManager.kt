package com.example.testproject

import java.nio.ByteBuffer

const val SOP: Byte = 0x7E    // Start of packet identifier
const val EOP: Byte = SOP     // End of packet identifier
const val SOP_ESCAPE: Byte = 0x7D
const val SOP_STUFF: Byte  = 0x5E
const val ESCAPE_STUFF: Byte = 0x5D
const val  NON_SOP_BYTE: Byte = 0x7E
const val NON_ESCAPE_BYTE: Byte = 0x7D
const val wifiConnect: Byte = 0x01    // connect to wifi command identifier
const val wifiConnectSts: Byte = 0x02
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
    return checksum.toByte()
}


fun stuffPacket(packet: ByteArray, len: Int): ByteArray
{
    val tmpBuf = mutableListOf<Byte>()

    for(i in 0 until len)
    {
        if(packet[i] == SOP)
        {
            tmpBuf.add(SOP_ESCAPE)
            tmpBuf.add(SOP_STUFF)
        }
        else if(packet[i] == SOP_ESCAPE)
        {
            tmpBuf.add(SOP_ESCAPE)
            tmpBuf.add(ESCAPE_STUFF)
        }
        else
        {
            tmpBuf.add(packet[i])
        }
    }

    return tmpBuf.toByteArray()
}

fun unstuffPacket(packet: ByteArray, len: Int): Pair<ByteArray?, Byte>
{
    val tmpBuf = mutableListOf<Byte>()
    var errorCode: Byte = 0
    var i = 0

    while((i < len) && (errorCode == 0.toByte()))
    {
        if(packet[i] == SOP_ESCAPE)
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

    return if (errorCode == 0.toByte()) Pair(tmpBuf.toByteArray(), 0)
    else Pair(null, errorCode)
}
