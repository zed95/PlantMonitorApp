package com.example.plantmonitorapp
import android.util.MutableInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiBad
import androidx.compose.material.icons.filled.SignalWifiStatusbar4Bar
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.NetworkWifi3Bar
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.sharp.SignalWifiStatusbar4Bar
import androidx.compose.material.icons.twotone.SignalWifiStatusbar4Bar
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantmonitorapp.SocketManager.dashboardCh
import com.example.plantmonitorapp.SocketManager.devicePingSts
import com.example.plantmonitorapp.ui.theme.BackgroundGrey
import com.example.plantmonitorapp.ui.theme.CustomSilver
import com.example.plantmonitorapp.ui.theme.ElevatedGreen
import com.example.plantmonitorapp.ui.theme.ElevatedGrey
import kotlinx.coroutines.channels.Channel

@Composable
fun DeviceDashboard(serviceViewModel: ServiceViewModel)
{
    var packet = listOf<Byte>()
    var dst = -1
    var type = -1
    val temp = remember {EnvInfoElement("Temperature", Icons.Filled.Thermostat, Color(0xFFFF7070), "\u2103")}
    val hum = remember {EnvInfoElement("Humidity", Icons.Filled.Cloud, Color(0xFF68ADFF), "%")}
    val moist = remember { EnvInfoElement("Soil Moisture", Icons.Filled.WaterDrop, Color(0xFF003FFF), "%") }
    val name = serviceViewModel.selectedDevice.serviceName


    // connect to device selected
    LaunchedEffect(Unit)
    {
        if(SocketManager.ConnectToDevice(serviceViewModel.selectedDevice) == DeviceConnectionSts.CONNECTED)
        {
        }

        SocketManager.dashboardPktFlow.collect { chPacket ->
            packet = chPacket
            dst = EnvInfoElement.getPktDestination(packet)
            type = EnvInfoElement.getDataInfoType(packet)
            when(dst)
            {
                1 ->
                {
                    temp.UpdateVal(packet, type)
                }

                2 ->
                {
                    hum.UpdateVal(packet, type)
                }

                3 ->
                {
                    moist.UpdateValShort(packet, type)
                }

                4 ->
                {

                }
            }
        }
    }
    // This places the button in the center of the screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
    ) {
        TopPanel(name)
        temp.ElementImplement()
        hum.ElementImplement()
        moist.ElementImplement()
    }


}

@Composable
fun DeviceDashBaordName(name: String)
{
    Row(modifier = Modifier
        .fillMaxWidth()
        .padding(top = 40.dp),
        horizontalArrangement = Arrangement.Center)
    {
        Text(text = name,
            color = CustomSilver,
            fontSize = 40.sp
        )
    }
}

class EnvInfoElement(private val title: String,
                     private val icon: ImageVector,
                     private val iconColor: Color,
                     private val units: String,
)
{

    private var currentVal = mutableStateOf<Any>(0.0f)
    private var maxVal = mutableStateOf<Any>(0.0f)
    private var minVal = mutableStateOf<Any>(0.0f)

    private var maxThImp = mutableStateOf<Any>(0.0f)
    private var maxThAct = mutableStateOf<Any>(0.0f)
    private var minThImp = mutableStateOf<Any>(0.0f)
    private var minThAct = mutableStateOf<Any>(0.0f)

    @Composable
    fun ElementImplement()
    {
        ElevatedCard(
            shape = RoundedCornerShape(10.dp),
            elevation = CardDefaults.cardElevation(10.dp),
            modifier = Modifier
                .fillMaxWidth(1.0f)
                .height(100.dp)
                .padding(top = 10.dp)
        )
        {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(ElevatedGrey),   // optional spacing from the top

            )
            {
                Row(modifier = Modifier.fillMaxWidth())
                {
                    Box(
                        modifier = Modifier.fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    )
                    {
                        Icon(
                            imageVector = icon,
                            contentDescription = "Environmental Symbol",
                            tint = iconColor,
                            modifier = Modifier
                                .size(50.dp)
                                .padding(start = 4.dp)
                        )
                    }

                    // Title and value representing current environmental metric
                    Column(modifier = Modifier.width(150.dp))
                    {
                        Text(
                            text = title,
                            color = CustomSilver,
                            fontSize = 20.sp,
                            modifier = Modifier.offset(y = 10.dp)
                        )
                        Spacer(modifier = Modifier.height(15.dp))   // spacing between texts
                        Text(
                            text = (
                                when(val v = currentVal.value)
                                {
                                    is Float -> {"%.2f".format(currentVal.value) + units}
                                    else -> {"${currentVal.value}$units"}
                                }),

                            color = CustomSilver,
                            fontSize = 30.sp,
                            modifier = Modifier.offset(x = 20.dp)
                        )
                    }

                    Column(modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 5.dp)) {
                        Text(
                            text = (
                                    when(val v = maxThAct.value)
                                    {
                                        is Float -> {"%.2f".format(v) + units}
                                        else -> {"${v}$units"}
                                    }),

                            color = CustomSilver,
                            fontSize = 15.sp,
                        )

                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "Upper threshold",
                            tint = iconColor,
                            modifier = Modifier
                                .size(15.dp)
                        )

                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = "Lower threshold",
                            tint = iconColor,
                            modifier = Modifier
                                .size(15.dp)
                        )

                        Text(
                            text = (
                                    when(val v = minThAct.value)
                                    {
                                        is Float -> {"%.2f".format(v) + units}
                                        else -> {"${v}$units"}
                                    }),

                            color = CustomSilver,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }

    fun UpdateVal(pkt: List<Byte>, pktInfoType: Int)
    {
        val dataBytes = (pkt[5].toInt() and 0xFF)        or
                ((pkt[6].toInt() and 0xFF) shl 8)  or
                ((pkt[7].toInt() and 0xFF) shl 16) or
                ((pkt[8].toInt() and 0xFF) shl 24)


        when(pktInfoType)
        {
            1 ->
            {
                currentVal.value = Float.fromBits(dataBytes)
            }

            2 ->
            {
                maxVal.value = Float.fromBits(dataBytes)
            }

            3 ->
            {
                minVal.value = Float.fromBits(dataBytes)
            }

            4 ->
            {
                maxThImp.value = Float.fromBits(dataBytes)
            }

            5 ->
            {
                maxThAct.value = Float.fromBits(dataBytes)
            }

            6 ->
            {
                minThImp.value = Float.fromBits(dataBytes)
            }

            7 ->
            {
                minThAct.value = Float.fromBits(dataBytes)
            }
        }

    }

    fun UpdateValShort(pkt: List<Byte>, pktInfoType: Int)
    {
        val value = (pkt[5].toInt() and 0xFF) or ((pkt[6].toInt() and 0xFF) shl 8)


        when(pktInfoType)
        {
            1 ->
            {
                currentVal.value = value.toShort()
            }

            2 ->
            {
                maxVal.value = value.toShort()
            }

            3 ->
            {
                minVal.value = value.toShort()
            }

            4 ->
            {
                maxThImp.value = value.toShort()
            }

            5 ->
            {
                maxThAct.value = value.toShort()
            }

            6 ->
            {
                minThImp.value = value.toShort()
            }

            7 ->
            {
                minThAct.value = value.toShort()
            }
        }

    }

    companion object {
        // specifies which environmental data instance the packet belongs to
        fun getPktDestination(packet: List<Byte>): Int
        {
            var dst = -1
            println("PACKET ID: ${packet[0].toInt()}")
            when(CrossDevicePackets.fromId(packet[0].toInt()))
            {
                CrossDevicePackets.XDEVMSG_PLANT_MON_CONNECT_STS_RSP ->
                {
                    devicePingSts = ConnectionAliveSts.RSP_RECEIVED
                }

                CrossDevicePackets.XDEVMSG_START -> TODO()
                CrossDevicePackets.XDEVMSG_WIFI_SSID -> TODO()
                CrossDevicePackets.XDEVMSG_WIFI_PSWD -> TODO()
                CrossDevicePackets.XDEVMSG_CONNECT_NETWORK -> TODO()
                CrossDevicePackets.XDEVMSG_CONNECT_STATUS -> TODO()
                CrossDevicePackets.XDEVMSG_TEMP_DATA_REQ -> TODO()

                CrossDevicePackets.XDEVMSG_MAX_T_ACT_IMP_TH,
                CrossDevicePackets.XDEVMSG_MAX_T_ACT_TRIG_TH,
                CrossDevicePackets.XDEVMSG_MIN_T_ACT_IMP_TH,
                CrossDevicePackets.XDEVMSG_MIN_T_ACT_TRIG_TH,
                CrossDevicePackets.XDEVMSG_LIVE_TEMP_DATA,
                CrossDevicePackets.XDEVMSG_MAX_TEMP_DATA,
                CrossDevicePackets.XDEVMSG_MIN_TEMP_DATA -> {
                    dst = 1
                }
                CrossDevicePackets.XDEVMSG_HUM_DATA_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_MAX_H_ACT_IMP_TH,
                CrossDevicePackets.XDEVMSG_MAX_H_ACT_TRIG_TH,
                CrossDevicePackets.XDEVMSG_MIN_H_ACT_IMP_TH,
                CrossDevicePackets.XDEVMSG_MIN_H_ACT_TRIG_TH,
                CrossDevicePackets.XDEVMSG_LIVE_HUM_DATA,
                CrossDevicePackets.XDEVMSG_MAX_HUM_DATA,
                CrossDevicePackets.XDEVMSG_MIN_HUM_DATA -> {
                    dst = 2
                }
                CrossDevicePackets.XDEVMSG_LUX_DATA_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_LIVE_LUX_DATA -> TODO()
                CrossDevicePackets.XDEVMSG_MAX_LUX_DATA -> TODO()
                CrossDevicePackets.XDEVMSG_MIN_LUX_DATA -> TODO()
                CrossDevicePackets.XDEVMSG_SOILM1_DATA_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_MAX_SM1_ACT_IMP_TH,
                CrossDevicePackets.XDEVMSG_MAX_SM1_ACT_TRIG_TH,
                CrossDevicePackets.XDEVMSG_MIN_SM1_ACT_IMP_TH,
                CrossDevicePackets.XDEVMSG_MIN_SM1_ACT_TRIG_TH,
                CrossDevicePackets.XDEVMSG_LIVE_SOILM1_DATA,
                CrossDevicePackets.XDEVMSG_MAX_SOILM1_DATA,
                CrossDevicePackets.XDEVMSG_MIN_SOILM1_DATA -> {
                    dst = 3
                }
                CrossDevicePackets.XDEVMSG_SOILM2_DATA_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_LIVE_SOILM2_DATA,
                CrossDevicePackets.XDEVMSG_MAX_SOILM2_DATA,
                CrossDevicePackets.XDEVMSG_MIN_SOILM2_DATA -> {
                    dst = 4
                }
                CrossDevicePackets.XDEVMSG_TEMP_THRSH_DAT_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_TEMP_THRSH_DAT -> TODO()
                CrossDevicePackets.XDEVMSG_MULTI_PKT_REQUEST -> TODO()
                CrossDevicePackets.XDEVMSG_MULTI_PKT_REQUEST_REPLY -> TODO()
                null ->
                {}
            }

            return dst
        }

        // specifies whether the information type is current value, min/max value, thresholds value ect
        fun getDataInfoType(packet: List<Byte>): Int
        {
            var type = -1

            when(CrossDevicePackets.fromId(packet[0].toInt()))
            {
                CrossDevicePackets.XDEVMSG_PLANT_MON_CONNECT_STS_RSP ->
                {
                    devicePingSts = ConnectionAliveSts.RSP_RECEIVED
                }

                CrossDevicePackets.XDEVMSG_LIVE_TEMP_DATA,
                CrossDevicePackets.XDEVMSG_LIVE_HUM_DATA,
                CrossDevicePackets.XDEVMSG_LIVE_SOILM1_DATA,
                CrossDevicePackets.XDEVMSG_LIVE_SOILM2_DATA ->
                {
                    type = 1
                }

                CrossDevicePackets.XDEVMSG_MAX_TEMP_DATA,
                CrossDevicePackets.XDEVMSG_MAX_HUM_DATA,
                CrossDevicePackets.XDEVMSG_MAX_SOILM1_DATA,
                CrossDevicePackets.XDEVMSG_MAX_SOILM2_DATA ->
                {
                    type = 2
                }


                CrossDevicePackets.XDEVMSG_MIN_TEMP_DATA,
                CrossDevicePackets.XDEVMSG_MIN_HUM_DATA,
                CrossDevicePackets.XDEVMSG_MIN_SOILM1_DATA,
                CrossDevicePackets.XDEVMSG_MIN_SOILM2_DATA -> {
                    type = 3
                }


                CrossDevicePackets.XDEVMSG_START -> TODO()
                CrossDevicePackets.XDEVMSG_WIFI_SSID -> TODO()
                CrossDevicePackets.XDEVMSG_WIFI_PSWD -> TODO()
                CrossDevicePackets.XDEVMSG_CONNECT_NETWORK -> TODO()
                CrossDevicePackets.XDEVMSG_CONNECT_STATUS -> TODO()
                CrossDevicePackets.XDEVMSG_TEMP_DATA_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_HUM_DATA_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_LUX_DATA_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_LIVE_LUX_DATA -> TODO()
                CrossDevicePackets.XDEVMSG_MAX_LUX_DATA -> TODO()
                CrossDevicePackets.XDEVMSG_MIN_LUX_DATA -> TODO()
                CrossDevicePackets.XDEVMSG_SOILM1_DATA_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_SOILM2_DATA_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_TEMP_THRSH_DAT_REQ -> TODO()
                CrossDevicePackets.XDEVMSG_TEMP_THRSH_DAT -> TODO()
                CrossDevicePackets.XDEVMSG_MULTI_PKT_REQUEST -> TODO()
                CrossDevicePackets.XDEVMSG_MULTI_PKT_REQUEST_REPLY -> TODO()

                CrossDevicePackets.XDEVMSG_MAX_T_ACT_IMP_TH,
                CrossDevicePackets.XDEVMSG_MAX_H_ACT_IMP_TH,
                CrossDevicePackets.XDEVMSG_MAX_SM1_ACT_IMP_TH -> {
                    type = 4
                }

                CrossDevicePackets.XDEVMSG_MAX_T_ACT_TRIG_TH,
                CrossDevicePackets.XDEVMSG_MAX_H_ACT_TRIG_TH,
                CrossDevicePackets.XDEVMSG_MAX_SM1_ACT_TRIG_TH -> {
                    type = 5
                }

                CrossDevicePackets.XDEVMSG_MIN_T_ACT_IMP_TH,
                CrossDevicePackets.XDEVMSG_MIN_H_ACT_IMP_TH,
                CrossDevicePackets.XDEVMSG_MIN_SM1_ACT_IMP_TH -> {
                    type = 6
                }

                CrossDevicePackets.XDEVMSG_MIN_T_ACT_TRIG_TH,
                CrossDevicePackets.XDEVMSG_MIN_H_ACT_TRIG_TH,
                CrossDevicePackets.XDEVMSG_MIN_SM1_ACT_TRIG_TH -> {
                    type = 7
                }
                null -> {}
            }

            return type
        }
    }
}

@Composable
fun TopPanel(deviceName: String)
{
    ElevatedCard(
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(10.dp),
        modifier = Modifier
            .fillMaxWidth(1.0f)
            .heightIn(min = 100.dp, max = 100.dp) // Maximum height
    )
    {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(ElevatedGrey),   // optional spacing from the top

        )
        {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
                horizontalArrangement = Arrangement.Center)
            {

                Text(text = deviceName,
                    color = CustomSilver,
                    fontSize = 40.sp
                )

                TopPanelDevConnectStsIcon()
            }
        }
    }
}

@Composable
fun TopPanelDevConnectStsIcon()
{
    val devConnectSts = SocketManager.connectionSts

    Icon(
        imageVector =
            when(devConnectSts)
            {
                DeviceConnectionSts.CONNECTED ->
                {
                    Icons.Outlined.Wifi
                }

                DeviceConnectionSts.CONNECTING ->
                {
                    Icons.Filled.Wifi
                }

                DeviceConnectionSts.DISCONNECTED ->
                {
                    Icons.Filled.WifiOff
                }
            },
        contentDescription = "Arrow",
        tint =
            when(devConnectSts)
            {
                DeviceConnectionSts.CONNECTED ->
                {
                    Color.Green
                }

                DeviceConnectionSts.CONNECTING ->
                {
                    Color.Yellow
                }

                DeviceConnectionSts.DISCONNECTED ->
                {
                    Color.Red
                }
            },
        modifier = Modifier
            .size(40.dp)
            .padding(start = 4.dp)
    )
}
