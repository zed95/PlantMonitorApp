package com.example.plantmonitorapp

import com.example.plantmonitorapp.SocketManager.dashboardCh
import com.example.plantmonitorapp.SocketManager.devicePingSts
import com.example.plantmonitorapp.SocketManager.isActive
import com.example.plantmonitorapp.SocketManager.packetChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlin.Byte
import kotlin.collections.mutableListOf

enum class CrossDevicePackets(val id: Int) {

    XDEVMSG_RSP_CONNECT_STS(103),
    XDEVMSG_START(0),
    XDEVMSG_CONNECT_STATUS(XDEVMSG_START.id),
    XDEVMSG_TEMP_DATA_REQ(1),
    XDEVMSG_LIVE_TEMP_DATA(2),
    XDEVMSG_MAX_TEMP_DATA(3),
    XDEVMSG_MIN_TEMP_DATA(4),
    XDEVMSG_HUM_DATA_REQ(5),
    XDEVMSG_LIVE_HUM_DATA(6),
    XDEVMSG_MAX_HUM_DATA(7),
    XDEVMSG_MIN_HUM_DATA(8),
    XDEVMSG_SOILM1_DATA_REQ(9),
    XDEVMSG_LIVE_SOILM1_DATA(10),
    XDEVMSG_MAX_SOILM1_DATA(11),
    XDEVMSG_MIN_SOILM1_DATA(12),
    XDEVMSG_SOILM2_DATA_REQ(13),
    XDEVMSG_LIVE_SOILM2_DATA(14),
    XDEVMSG_MAX_SOILM2_DATA(15),
    XDEVMSG_MIN_SOILM2_DATA(16),
    XDEVMSG_TEMP_THRSH_DAT_REQ(17),
    XDEVMSG_TEMP_THRSH_DAT(18),
    XDEVMSG_MAX_T_ACT_IMP_TH(19),
    XDEVMSG_MAX_T_ACT_TRIG_TH(20),
    XDEVMSG_MIN_T_ACT_IMP_TH(21),
    XDEVMSG_MIN_T_ACT_TRIG_TH(22),
    XDEVMSG_MAX_H_ACT_IMP_TH(23),
    XDEVMSG_MAX_H_ACT_TRIG_TH(24),
    XDEVMSG_MIN_H_ACT_IMP_TH(25),
    XDEVMSG_MIN_H_ACT_TRIG_TH(26),
    XDEVMSG_MAX_SM1_ACT_IMP_TH(27),
    XDEVMSG_MAX_SM1_ACT_TRIG_TH(28),
    XDEVMSG_MIN_SM1_ACT_IMP_TH(29),
    XDEVMSG_MIN_SM1_ACT_TRIG_TH (30),
    XDEVMSG_RECURR_EVNT_REQUEST(31),
    XDEVMSG_ENV_METRICS(32),
    XDEVMSG_SETTINGS_UPDATE(33),
    XDEVMSG_GET_TEMP_SETTINGS(34),
    XDEVMSG_GET_TEMP_SETTINGS_REPLY(35),
    XDEVMSG_GET_ENV_THRESHOLDS(36),
    XDEVMSG_ENV_THRESHOLDS_REPLY(37);


    companion object {
        private val map = CrossDevicePackets.entries.associateBy { it.id }
        fun fromId(id: Int): CrossDevicePackets? = map[id]
    }


}

enum class OutCommands(val id: Int)
{
    OUTCMD_DEVICE_DASHBOARD_DATA_ENABLE(0),
    OUTCMD_DEVICE_DASHBOARD_DATA_DISABLE(1),
    OUTCMD_REQUEST_ENV_THRESHOLDS(2);

    companion object {
        private val map = OutCommands.entries.associateBy { it.id }
        fun fromId(id: Int): OutCommands? = map[id]
    }
}

/***************************************************************************************************
 * Represents all message types emitted by the communication layer after
 * decoding incoming packets.
 *
 * This sealed class defines a strongly-typed hierarchy of domain messages
 * used to transport environmental metric and threshold data through the
 * application. Each subclass represents a specific category of decoded data.
 *
 * Using a sealed class ensures exhaustive `when` expressions when handling
 * broker messages and provides type safety across the messaging pipeline.
 **************************************************************************************************/
sealed class BrokerMessage
{
    data class EnvMetricTemp(val current: Float,
                             val high: Float,
                             val low: Float) : BrokerMessage()

    data class EnvMetricHum(val current: Float,
                            val high: Float,
                            val low: Float) : BrokerMessage()

    data class EnvMetricSoilM1(val current: UShort,
                               val high: UShort,
                               val low: UShort) : BrokerMessage()

    data class EnvMetricSoilM2(val current: UShort,
                               val high: UShort,
                               val low: UShort) : BrokerMessage()

    data class EnvThresholdsTemp(val maxThAct: Float,
                                 val maxThImp: Float,
                                 val minThAct: Float,
                                 val minThImp: Float) : BrokerMessage()

    data class EnvThresholdsHum(val maxThAct: Float,
                                 val maxThImp: Float,
                                 val minThAct: Float,
                                 val minThImp: Float) : BrokerMessage()

    data class EnvThresholdsMoisture1(val maxThAct: UShort,
                                      val maxThImp: UShort,
                                      val minThAct: UShort,
                                      val minThImp: UShort) : BrokerMessage()

    data class EnvThresholdsMoisture2(val maxThAct: UShort,
                                      val maxThImp: UShort,
                                      val minThAct: UShort,
                                      val minThImp: UShort) : BrokerMessage()

    data class DeviceConnectionStatus(val status: DeviceConnectionSts?) : BrokerMessage()
}

object XDevMessageBroker
{
    private val  _messages = MutableSharedFlow<BrokerMessage>()
    val messages = _messages.asSharedFlow()
    val outChannel = Channel<MutableList<Byte>>(capacity = Channel.UNLIMITED)
    val inChannel = Channel<MutableList<Byte>>(capacity = Channel.UNLIMITED)

    /***************************************************************************************************
     * Starts the background coroutines responsible for processing incoming and
     * outgoing communication channels.
     *
     * Two coroutines are launched on the `Dispatchers.IO` dispatcher:
     * - One coroutine continuously processes outgoing messages.
     * - One coroutine continuously processes incoming messages.
     *
     * These coroutines execute independently and run concurrently with the
     * calling thread.
     *
     * Note: This function does not retain references to the launched coroutines
     * and therefore cannot directly monitor, cancel, or await their completion.
     **************************************************************************************************/
    fun initChannels()
    {
        CoroutineScope(Dispatchers.IO).launch()
        {
            processOutgoing()
        }

        CoroutineScope(Dispatchers.IO).launch()
        {
            processIncoming()
        }
    }

    /***************************************************************************************************
     * Processes outgoing commands received from the outbound command channel.
     *
     * This coroutine continuously consumes commands from `outChannel` and
     * translates them into protocol packets that are forwarded to the transmit
     * packet channel for delivery.
     *
     * For each recognized command, the appropriate packet is constructed and
     * sent to `SocketManager.txPacketCh`. Unrecognized commands are ignored.
     *
     * This function suspends while waiting for commands to become available and
     * while sending packets to the transmit channel.
     **************************************************************************************************/
    private suspend fun processOutgoing()  {
        for (packet in outChannel)  {

            when(OutCommands.fromId(packet[0].toInt()))
            {
                OutCommands.OUTCMD_DEVICE_DASHBOARD_DATA_ENABLE -> {
                    SocketManager.txPacketCh.send(
                        ConstructRecurrentEventRequest(
                            RecurrentEventId.RECURR_EVNT_ENV_METRICS_XDEV.id,
                            RecurrentEventParamId.RECURR_EVNT_PARAM_ENABLED.id,
                            1.toUInt()).toMutableList()
                    )
                }

                OutCommands.OUTCMD_DEVICE_DASHBOARD_DATA_DISABLE -> {
                    SocketManager.txPacketCh.send(
                        ConstructRecurrentEventRequest(
                            RecurrentEventId.RECURR_EVNT_ENV_METRICS_XDEV.id,
                            RecurrentEventParamId.RECURR_EVNT_PARAM_ENABLED.id,
                            0.toUInt()).toMutableList()
                    )
                }

                OutCommands.OUTCMD_REQUEST_ENV_THRESHOLDS ->
                {
                    SocketManager.txPacketCh.send(ConstructEnvThresholdsRequest().toMutableList())
                }

                else -> {}
            }
            // construct packet
            // send to wifi for transmission
        }
    }

    /***************************************************************************************************
     * Processes incoming packets received from the inbound packet channel.
     *
     * This coroutine continuously consumes packets from `inChannel`, determines
     * the packet type from the packet identifier, and dispatches the packet to
     * the appropriate handler.
     *
     * Connection status response packets update the internal connection state,
     * while environmental data packets are unpacked and processed by their
     * respective handler functions.
     *
     * This function suspends while waiting for packets to become available from
     * the channel.
     *
     * Processing continues until `inChannel` is closed or the coroutine is
     * cancelled.
     **************************************************************************************************/
    private suspend fun processIncoming() {
        for (packet in inChannel) {
            // determine message type
            when(CrossDevicePackets.fromId(packet[0].toInt()))
            {
                CrossDevicePackets.XDEVMSG_CONNECT_STATUS ->
                {
                    _messages.emit(BrokerMessage.DeviceConnectionStatus(
                        DeviceConnectionSts.fromCode(packet[4])
                    ))
                }
                CrossDevicePackets.XDEVMSG_RSP_CONNECT_STS ->
                {

                }

                CrossDevicePackets.XDEVMSG_RECURR_EVNT_REQUEST -> TODO()
                CrossDevicePackets.XDEVMSG_ENV_METRICS -> unpackEnvMetrics(packet)
                CrossDevicePackets.XDEVMSG_ENV_THRESHOLDS_REPLY -> unpackEnvThresholds(packet)
                else -> {}
            }


            // broadcast to relevant section
        }
    }

    /***************************************************************************************************
     * Unpacks environmental metrics from a received packet and publishes the
     * decoded values to the message stream.
     *
     * The packet payload is expected to contain:
     * - Temperature metrics (current, high, and low values).
     * - Humidity metrics (current, high, and low values).
     * - Soil moisture sensor 1 metrics (current, high, and low values).
     * - Soil moisture sensor 2 metrics (current, high, and low values).
     *
     * Temperature and humidity values are decoded as floating-point numbers,
     * while soil moisture values are decoded as unsigned 16-bit integers.
     *
     * Each metric group is converted into a corresponding `BrokerMessage`
     * instance and emitted to `_messages` for downstream consumers.
     *
     * Note: This function assumes that `msg` contains a valid environmental
     * metrics packet and that all expected fields are present at their defined
     * byte offsets.
     *
     * @param msg The packet containing encoded environmental metrics data.
     **************************************************************************************************/
    suspend fun unpackEnvMetrics(msg: MutableList<Byte>)
    {
        val tempDataMsg = BrokerMessage.EnvMetricTemp(
            current = bytesToFloat(msg, 5),
            high = bytesToFloat(msg, 9),
            low = bytesToFloat(msg, 13)
        )
        _messages.emit(tempDataMsg)

        val humDataMsg = BrokerMessage.EnvMetricHum(
            current = bytesToFloat(msg, 17),
            high = bytesToFloat(msg, 21),
            low = bytesToFloat(msg, 25)
        )
        _messages.emit(humDataMsg)

        val soilM1Msg = BrokerMessage.EnvMetricSoilM1(
            current = bytesToUshort(msg, 29),
            high =  bytesToUshort(msg, 31),
            low = bytesToUshort(msg, 33)
        )
        _messages.emit(soilM1Msg)

        val soilM2Msg = BrokerMessage.EnvMetricSoilM2(
            current = bytesToUshort(msg, 35),
            high =  bytesToUshort(msg, 37),
            low = bytesToUshort(msg, 39)
        )
        _messages.emit(soilM2Msg)
    }

    /***************************************************************************************************
     * Unpacks environmental threshold values from a received packet and publishes
     * the decoded thresholds to the message stream.
     *
     * The packet payload is expected to contain threshold information for:
     * - Temperature.
     * - Humidity.
     * - Soil moisture sensor 1.
     * - Soil moisture sensor 2.
     *
     * Each threshold group contains:
     * - Maximum active threshold.
     * - Maximum implemented threshold.
     * - Minimum active threshold.
     * - Minimum implemented threshold.
     *
     * Temperature and humidity thresholds are decoded as floating-point values,
     * while soil moisture thresholds are decoded as unsigned 16-bit integers.
     *
     * Each decoded threshold group is converted into a corresponding
     * `BrokerMessage` instance and emitted to `_messages` for downstream
     * consumers.
     *
     * Note: This function assumes that `msg` contains a valid environmental
     * thresholds packet and that all expected fields are present at their
     * protocol-defined byte offsets.
     *
     * @param msg The packet containing encoded environmental threshold data.
     **************************************************************************************************/
    suspend fun unpackEnvThresholds(msg: MutableList<Byte>)
    {
        val tempThresholds = BrokerMessage.EnvThresholdsTemp(
            maxThAct = bytesToFloat(msg, 5),
            maxThImp = bytesToFloat(msg, 9),
            minThAct = bytesToFloat(msg, 13),
            minThImp = bytesToFloat(msg, 17))
        _messages.emit(tempThresholds)

        val humThresholds = BrokerMessage.EnvThresholdsHum(
            maxThAct = bytesToFloat(msg, 21),
            maxThImp = bytesToFloat(msg, 25),
            minThAct = bytesToFloat(msg, 29),
            minThImp = bytesToFloat(msg, 33))
        _messages.emit(humThresholds)

        val soilMoisture1Thresholds = BrokerMessage.EnvThresholdsMoisture1(
            maxThAct = bytesToUshort(msg, 37),
            maxThImp = bytesToUshort(msg, 41),
            minThAct = bytesToUshort(msg, 45),
            minThImp = bytesToUshort(msg, 49))
        _messages.emit(soilMoisture1Thresholds)

        val soilMoisture2Thresholds = BrokerMessage.EnvThresholdsMoisture2(
            maxThAct = bytesToUshort(msg, 53),
            maxThImp = bytesToUshort(msg, 57),
            minThAct = bytesToUshort(msg, 61),
            minThImp = bytesToUshort(msg, 65))
        _messages.emit(soilMoisture2Thresholds)
    }

    /***************************************************************************************************
     * Converts four bytes from a packet into a 32-bit floating-point value.
     *
     * Four consecutive bytes beginning at the specified index are interpreted as
     * an IEEE 754 single-precision floating-point value encoded in little-endian
     * byte order.
     *
     * Note: This function assumes that at least four bytes are available starting
     * at `idx`. No bounds checking is performed.
     *
     * @param msg The packet containing the encoded floating-point value.
     * @param idx The index of the first byte of the 32-bit value.
     * @return The decoded floating-point value.
     **************************************************************************************************/
    fun bytesToFloat(msg: MutableList<Byte>, idx: Int): Float
    {
        val data32 = (msg[idx].toInt() and 0xFF)         or
                ((msg[idx + 1].toInt() and 0xFF) shl 8)  or
                ((msg[idx + 2].toInt() and 0xFF) shl 16) or
                ((msg[idx + 3].toInt() and 0xFF) shl 24)
        return Float.fromBits(data32)
    }

    /***************************************************************************************************
     * Converts two bytes from a packet into an unsigned 16-bit integer.
     *
     * Two consecutive bytes beginning at the specified index are interpreted as
     * an unsigned 16-bit value encoded in little-endian byte order.
     *
     * Note: This function assumes that at least two bytes are available starting
     * at `idx`. No bounds checking is performed.
     *
     * @param msg The packet containing the encoded unsigned 16-bit value.
     * @param idx The index of the first byte of the 16-bit value.
     * @return The decoded unsigned 16-bit integer.
     **************************************************************************************************/
    fun bytesToUshort(msg: MutableList<Byte>, idx: Int): UShort
    {
        val data16 = (msg[idx].toInt() and 0xFF) or ((msg[idx + 1].toInt() and 0xFF) shl 8)
        return data16.toUShort()
    }

    fun constructParameterlessRequest(outRequest: Int): MutableList<Byte>
    {
        val request = mutableListOf<Byte>()
        request.addAll(0, IntToList(outRequest))
        return request
    }

    }
