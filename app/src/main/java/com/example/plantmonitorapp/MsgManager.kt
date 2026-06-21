package com.example.plantmonitorapp

import androidx.activity.result.ActivityResultLauncher
import java.nio.ByteBuffer
import java.nio.ByteOrder

const val SOP: Byte = 0x7E    // Start of packet identifier
const val EOP: Byte = 0X7F    // End of packet identifier
const val ESCAPE: Byte = 0x7D
const val SOP_STUFF: Byte  = 0x5E
const val EOP_STUFF: Byte  = 0x5F
const val ESCAPE_STUFF: Byte = 0x5D
const val  NON_SOP_BYTE: Byte = 0x7E
const val  NON_EOP_BYTE: Byte = 0x7F
const val NON_ESCAPE_BYTE: Byte = 0x7D
const val XDVEMSG_WIFI_CONNECT: Byte = 100    // connect to wifi command identifier
const val XDEVMSG_RSP_ESP32_WIFI_STS: Byte = 101

const val XDEVMSG_REQUEST_CONNECT_STS: Byte = 102
const val RSP_CONNECT_STS: Byte = 0x05
// destuffing error codes
const val ERR_BUF_OVERFLOW: Byte =   -2
const val ERR_INVALID_ESCAPE: Byte = -3
const val ERR_ESCAPE_AT_END: Byte =  -4


enum class RecurrentEventId(val id: UByte)
{
    RECURR_EVNT_DSP_TEMP_XDEV       (0U),   // request to periodically feed temperature data to xdev display
    RECURR_EVNT_DSP_HUM_XDEV        (1U),   // request to periodically feed humidity data to xdev display
    RECURR_EVNT_DSP_LUX_XDEV        (2U),   // request to periodically feed lux data to xdev display.
    RECURR_EVNT_CANCEL_XDEV_EVNTS   (3U),   // cancel all xdev recurring events. typically done when device disconnects from ESP32
    RECURR_EVNT_MONITOR_TEMP_HUM    (4U),   // temperature and humidity monitoring
    RECURR_EVNT_DSP_SOILM_SEN1      (5U),   // request to periodically send soil moisture sensor 1 data for display
    RECURR_EVNT_DSP_SOILM_SEN2      (6U),  // request to periodically send soil moisture sensor 2 data for display
    RECURR_EVNT_SOILM_1_XDEV        (7U),  // Request for soil moisture sensor 1 data from phone
    RECURR_EVNT_SOILM_2_XDEV        (8U),  // Request for soil moisture sensor 2 data from phone
    RECURR_EVNT_VENTILATION         (9U),  // Request to turn on ventilation for the enclosure
    RECURR_EVNT_ENV_METRICS_XDEV    (10U);  // Request for plant environmental conditions

    companion object {
        private val map = RecurrentEventId.entries.associateBy { it.id }
        fun fromId(id: UByte): RecurrentEventId? = map[id]
    }
}

enum class RecurrentEventParamId(val id: UByte)
{
    RECURR_EVNT_PARAM_ENABLED(0U),
    RECURR_EVNT_PARAM_PERIOD(1U);
}

/***************************************************************************************************
 * Constructs a packet requesting that an ESP32 connect to a Wi-Fi network.
 *
 * The packet payload contains:
 * - The SSID length as a 32-bit integer.
 * - The password length as a 32-bit integer.
 * - The SSID string bytes.
 * - The password string bytes.
 *
 * The payload size is calculated automatically and encoded in the packet
 * header. A checksum is then computed over the packet header and payload and
 * appended to the packet.
 *
 * Before transmission, the packet is byte-stuffed to escape any
 * protocol-reserved values and is framed with start-of-packet (SOP) and
 * end-of-packet (EOP) markers.
 *
 * Note: The SSID and password are encoded using the platform's default
 * character set via `String.toByteArray()`.
 *
 * @param ssid The Wi-Fi network SSID to connect to.
 * @param password The password associated with the specified SSID.
 * @return A byte array containing the fully encoded, checksummed, stuffed,
 * and framed Wi-Fi connection request packet.
 **************************************************************************************************/
fun msgConnectEsp32ToWifi(ssid: String, password: String): ByteArray
{
    val msg = mutableListOf<Byte>()
    val stuffedMsg = mutableListOf<Byte>()
    val numSsidLenBytes = Int.SIZE_BYTES
    val nunPasswordLenBytes = Int.SIZE_BYTES
    val numChecksumLenBytes = Byte.SIZE_BYTES
    val payloadSize = ssid.length + password.length + numSsidLenBytes + nunPasswordLenBytes + numChecksumLenBytes
    // converts the ssid length to byte buffer holding 4 bytes that make up ssid length, then to a byte array and finally to a list of bytes
    val ssidSizeBytes = IntToList(ssid.length)
    val passwordSizeBytes = IntToList(password.length)
    val payloadSizeBytes = IntToList(payloadSize)
    var checksum: Byte = 0

    msg.add(XDVEMSG_WIFI_CONNECT)
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

/*
* Byte in Kotlin is signed (–128..127). In C, uint8_t is unsigned (0..255).
* That’s why we do (data[i].toInt() and 0xFF) → converts signed byte to an unsigned 0–255 value.
* We keep sum as Int to avoid overflow while adding.
* At the end, we apply two’s complement: ~sum + 1 → in Kotlin: (sum.inv() + 1).
* & 0xFF ensures the result is clamped to 8 bits before casting back to Byte.
* */

/***************************************************************************************************
 * Calculates an 8-bit two's-complement checksum for a sequence of bytes.
 *
 * The checksum is computed by summing all bytes in the specified range as
 * unsigned 8-bit values, retaining only the least significant 8 bits of the
 * running total. The two's complement of the final sum is then returned.
 *
 * When the returned checksum is added to the original data bytes, the least
 * significant 8 bits of the resulting sum equal zero.
 *
 * @param data The byte array containing the data to checksum.
 * @param len The number of bytes from `data` to include in the calculation.
 * @return The calculated 8-bit two's-complement checksum.
 **************************************************************************************************/
fun calcChecksum(data: ByteArray, len: Int): Byte {
    var sum = 0

    for (i in 0 until len) {
        sum = (sum + (data[i].toInt() and 0xFF)) and 0xFF
    }

    val checksum = ((sum.inv() + 1) and 0xFF)
    println("checksum: $checksum")
    return checksum.toByte()
}

/***************************************************************************************************
 * Applies byte-stuffing to a packet to ensure that protocol-reserved bytes
 * are not interpreted as packet framing or escape characters during
 * transmission.
 *
 * Any occurrence of the start-of-packet (SOP), end-of-packet (EOP), or
 * escape (ESCAPE) byte within the packet data is replaced with a two-byte
 * escape sequence. All other bytes are copied unchanged.
 *
 * The resulting stuffed packet may be larger than the original packet if
 * one or more reserved bytes are encountered.
 *
 * @param packet The packet data to be encoded.
 * @param len The number of bytes from `packet` to process.
 * @return A new byte array containing the byte-stuffed packet data.
 **************************************************************************************************/
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

/***************************************************************************************************
 * Removes byte-stuffing from a packet and reconstructs the original packet
 * data in place.
 *
 * Escaped protocol-reserved bytes are converted back to their original values
 * according to the packet encoding rules. The unstuffed data is copied back
 * into the supplied packet buffer starting at index `0`.
 *
 * The function validates all escape sequences encountered during processing.
 * An error is reported if:
 * - An escape byte appears as the final byte of the packet.
 * - An escape byte is followed by an invalid stuffing value.
 *
 * Note: This function modifies the supplied `packet` buffer in place. The
 * size of the underlying `ByteArray` is not changed; only the unstuffed bytes
 * are copied back into the existing buffer.
 *
 * @param packet The packet buffer containing stuffed packet data.
 * @param len The number of valid bytes in `packet` to process.
 * @return The length of the unstuffed packet on success. On failure, returns
 * an error code indicating the reason the packet could not be unstuffed.
 **************************************************************************************************/
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

/***************************************************************************************************
 * Removes the start-of-packet (SOP) and end-of-packet (EOP) framing bytes
 * from a packet in place.
 *
 * The first byte and last byte of the provided packet are assumed to be the
 * protocol framing markers and are removed before the remaining packet data
 * is copied back into the original array.
 *
 * Note: The SOP and EOP markers are not included in the size of the overall payload data and
 *       therefore when removed, the size does not need to be recalculated. The caller of the
 *       function is responsible however for accounting for the removed SOP and EOP bytes.
 *
 * Note: This function modifies the contents of the provided `packet` array in place. Because the
 *        caller and callee reference the same `ByteArray` instance, the changes are visible to
 *        the caller.
 *
 * @param packet The packet buffer containing SOP and EOP framing bytes.
 **************************************************************************************************/
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

/***************************************************************************************************
 * Constructs a request packet for querying the device connection status.
 *
 * The packet contains:
 * - A packet identifier indicating a connection status request.
 * - A payload size field set to `1`, representing the checksum-only payload.
 * - A checksum calculated over the packet contents prior to checksum insertion.
 *
 * Once assembled, the packet is byte-stuffed to escape any protocol-reserved
 * values and is framed with start-of-packet (SOP) and end-of-packet (EOP)
 * markers to create the final transmission-ready message.
 *
 * @return A byte array containing the fully encoded, checksummed, and framed
 * connection status request packet.
 **************************************************************************************************/
fun PktConnectSts(): ByteArray
{
    val tmpBuf = mutableListOf<Byte>()
    val checksumSize: Byte = 1;
    val payloadSize: Int =  checksumSize.toInt()
    val payloadSizeBytes = IntToList((payloadSize))
    var checksum: Byte = 0;
    val stuffedMsg = mutableListOf<Byte>()

    tmpBuf.add(XDEVMSG_REQUEST_CONNECT_STS)
    tmpBuf.addAll(payloadSizeBytes)
    checksum = calcChecksum(tmpBuf.toByteArray(), tmpBuf.size)
    tmpBuf.add(checksum)

    stuffedMsg.addAll(stuffPacket(tmpBuf.toByteArray(), tmpBuf.size).toList())
    stuffedMsg.add(0, SOP) // insert SOP at beginning of list
    stuffedMsg.add(EOP) // add end of packet identifier

    return stuffedMsg.toByteArray()
}

/***************************************************************************************************
 * Constructs a recurrent event request packet for transmission to a connected
 * device.
 *
 * The request payload contains:
 * - The recurrent event identifier.
 * - The event parameter identifier.
 * - A 32-bit parameter value encoded in little-endian byte order.
 *
 * The payload size is calculated automatically and inserted into the packet
 * header. A checksum is then computed over the complete packet contents
 * (excluding framing bytes) and appended to the packet.
 *
 * Before transmission, the packet is byte-stuffed to escape any
 * protocol-reserved values and is framed with start-of-packet (SOP) and
 * end-of-packet (EOP) markers.
 *
 * @param evntId Identifier of the recurrent event being requested or configured.
 * @param paramId Identifier of the parameter associated with the event.
 * @param value Unsigned 32-bit value to set the event parameter to.
 * @return A byte array containing the fully encoded, checksummed, and framed
 * request packet ready for transmission.
 **************************************************************************************************/
fun ConstructRecurrentEventRequest(evntId: UByte, paramId: UByte, value: UInt): ByteArray
{
    val tmpBuf = mutableListOf<Byte>()
    val payloadBuf = mutableListOf<Byte>()
    var checksum: Byte = 0;
    val stuffedMsg = mutableListOf<Byte>()

    // Packet ID
    tmpBuf.add(CrossDevicePackets.XDEVMSG_RECURR_EVNT_REQUEST.id.toByte())

    // get size of payload, convert to bytes and push to packet buffer
    payloadBuf.add(evntId.toByte())
    payloadBuf.add(paramId.toByte())
    payloadBuf.addAll(IntToList(value.toInt()))
    payloadBuf.add(checksum)    // placeholder for real checksum
    tmpBuf.addAll(IntToList(payloadBuf.size))
    // remove checksum, add payload to packet buffer, recalculate checksum of packet then append it back
    payloadBuf.removeAt(payloadBuf.lastIndex)
    tmpBuf.addAll(payloadBuf)
    checksum = calcChecksum(tmpBuf.toByteArray(), tmpBuf.size)
    tmpBuf.add(checksum)

    // stuff packet, then add SOP and EOP
    stuffedMsg.addAll(stuffPacket(tmpBuf.toByteArray(), tmpBuf.size).toList())
    stuffedMsg.add(0, SOP) // insert SOP at beginning of list
    stuffedMsg.add(EOP) // add end of packet identifier

    return stuffedMsg.toByteArray()
}

/***************************************************************************************************
 * Constructs a request packet for retrieving environmental threshold values
 * from a connected device.
 *
 * The packet contains:
 * - A packet identifier indicating a "Get Environmental Thresholds" request.
 * - A payload size field set to `1`, representing the checksum-only payload.
 * - A checksum calculated over the packet contents prior to checksum insertion.
 *
 * After the packet is assembled, byte stuffing is applied to escape any
 * protocol-reserved values. Start-of-packet (SOP) and end-of-packet (EOP)
 * markers are then added to produce the final framed message.
 *
 * @return A byte array containing the fully encoded and framed request packet
 * ready for transmission.
 **************************************************************************************************/
fun ConstructEnvThresholdsRequest(): ByteArray
{
    val tmpBuf = mutableListOf<Byte>()
    var checksum: Byte = 0;
    val stuffedMsg = mutableListOf<Byte>()

    // Packet ID
    tmpBuf.add(CrossDevicePackets.XDEVMSG_GET_ENV_THRESHOLDS.id.toByte())
    // payload size = 1 = only checksum byte
    tmpBuf.addAll(IntToList(1))
    checksum = calcChecksum(tmpBuf.toByteArray(), tmpBuf.size)
    tmpBuf.add(checksum)

    // stuff packet, then add SOP and EOP
    stuffedMsg.addAll(stuffPacket(tmpBuf.toByteArray(), tmpBuf.size).toList())
    stuffedMsg.add(0, SOP) // insert SOP at beginning of list
    stuffedMsg.add(EOP) // add end of packet identifier

    return stuffedMsg.toByteArray()
}

/***************************************************************************************************
 * Converts a 32-bit integer into a list of 4 bytes in little-endian order.
 *
 * The least significant byte is placed first in the resulting list and the
 * most significant byte is placed last.
 *
 * For example, the integer `0x12345678` is converted to:
 * `[0x78, 0x56, 0x34, 0x12]`.
 *
 * @param intVal The integer value to convert.
 * @return A list containing the 4 bytes that represent the integer in
 * little-endian byte order.
 **************************************************************************************************/
fun IntToList(intVal: Int): List<Byte>
{
    return ByteBuffer.allocate(4)
                     .order(ByteOrder.LITTLE_ENDIAN)
                     .putInt(intVal)
                     .array()
                     .toList()
}

fun ConstructDeviceConnectionStatusPacket(status: Byte): List<Byte> {
    val tmpBuf = mutableListOf<Byte>()
    tmpBuf.addAll(0, IntToList(CrossDevicePackets.XDEVMSG_CONNECT_STATUS.id))
    tmpBuf.add(status)

    return tmpBuf
}
