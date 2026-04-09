package com.example.plantmonitorapp

import com.example.plantmonitorapp.SocketManager.dashboardCh
import com.example.plantmonitorapp.SocketManager.devicePingSts
import com.example.plantmonitorapp.SocketManager.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

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
    XDEVMSG_ENV_METRICS(32);

    companion object {
        private val map = CrossDevicePackets.entries.associateBy { it.id }
        fun fromId(id: Int): CrossDevicePackets? = map[id]
    }
}

object XDevMessageBroker
{
    private val  _messages = MutableSharedFlow<List<Byte>>()
    val messages = _messages.asSharedFlow()

    suspend fun onRawMessage(msg: MutableList<Byte>)
    {
        // determine message type
        when(CrossDevicePackets.fromId(msg[0].toInt()))
        {
            CrossDevicePackets.XDEVMSG_RSP_CONNECT_STS ->
            {
                devicePingSts = ConnectionAliveSts.RSP_RECEIVED
            }

            CrossDevicePackets.XDEVMSG_START -> TODO()
            CrossDevicePackets.XDEVMSG_CONNECT_STATUS -> TODO()
            CrossDevicePackets.XDEVMSG_TEMP_DATA_REQ -> TODO()
            CrossDevicePackets.XDEVMSG_LIVE_TEMP_DATA,
            CrossDevicePackets.XDEVMSG_MAX_TEMP_DATA,
            CrossDevicePackets.XDEVMSG_MIN_TEMP_DATA -> {

            }
            CrossDevicePackets.XDEVMSG_HUM_DATA_REQ -> TODO()
            CrossDevicePackets.XDEVMSG_LIVE_HUM_DATA,
            CrossDevicePackets.XDEVMSG_MAX_HUM_DATA,
            CrossDevicePackets.XDEVMSG_MIN_HUM_DATA -> {

            }
            CrossDevicePackets.XDEVMSG_SOILM1_DATA_REQ -> TODO()
            CrossDevicePackets.XDEVMSG_LIVE_SOILM1_DATA,
            CrossDevicePackets.XDEVMSG_MAX_SOILM1_DATA,
            CrossDevicePackets.XDEVMSG_MIN_SOILM1_DATA -> {

            }
            CrossDevicePackets.XDEVMSG_SOILM2_DATA_REQ -> TODO()
            CrossDevicePackets.XDEVMSG_LIVE_SOILM2_DATA,
            CrossDevicePackets.XDEVMSG_MAX_SOILM2_DATA,
            CrossDevicePackets.XDEVMSG_MIN_SOILM2_DATA -> {

            }
            CrossDevicePackets.XDEVMSG_TEMP_THRSH_DAT_REQ -> TODO()
            CrossDevicePackets.XDEVMSG_TEMP_THRSH_DAT -> TODO()

            CrossDevicePackets.XDEVMSG_MAX_T_ACT_IMP_TH,
            CrossDevicePackets.XDEVMSG_MAX_T_ACT_TRIG_TH,
            CrossDevicePackets.XDEVMSG_MIN_T_ACT_IMP_TH,
            CrossDevicePackets.XDEVMSG_MIN_T_ACT_TRIG_TH,
            CrossDevicePackets.XDEVMSG_MAX_H_ACT_IMP_TH,
            CrossDevicePackets.XDEVMSG_MAX_H_ACT_TRIG_TH,
            CrossDevicePackets.XDEVMSG_MIN_H_ACT_IMP_TH,
            CrossDevicePackets.XDEVMSG_MIN_H_ACT_TRIG_TH,
            CrossDevicePackets.XDEVMSG_MAX_SM1_ACT_IMP_TH,
            CrossDevicePackets.XDEVMSG_MAX_SM1_ACT_TRIG_TH,
            CrossDevicePackets.XDEVMSG_MIN_SM1_ACT_IMP_TH ,
            CrossDevicePackets.XDEVMSG_MIN_SM1_ACT_TRIG_TH -> {

            }

            CrossDevicePackets.XDEVMSG_RECURR_EVNT_REQUEST -> TODO()
            CrossDevicePackets.XDEVMSG_ENV_METRICS -> unpackEnvMetrics(msg)
            null -> {}
        }

    }

    fun unpackEnvMetrics(msg: MutableList<Byte>)
    {
        val tempDataMsg = BrokerMessage.EnvMetricTemp(
            current = msg[5].toFloat(),
            high = msg[5].toFloat(),
            low = msg[5].toFloat()
            // this won't work becuase it uses only a single byte
            // not to convert using 4 bytes
        )
    }

}

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
}
