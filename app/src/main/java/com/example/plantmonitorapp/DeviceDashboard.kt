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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
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
    val temp = remember {EnvInfoElement("Temperature", Icons.Filled.Thermostat, Color(0xFFFF7070))}
    val hum = remember {EnvInfoElement("Humidity", Icons.Filled.Cloud, Color(0xFF68ADFF))}
    val moist = remember { EnvInfoElement("Soil Moisture", Icons.Filled.WaterDrop, Color(0xFF003FFF)) }
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
                    moist.UpdateVal(packet, type)
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
                     private val iconColor: Color
)
{

    private var currentVal = mutableFloatStateOf(0.0f)
    private var maxVal = mutableFloatStateOf(0.0f)
    private var minVal = mutableFloatStateOf(0.0f)

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
                            contentDescription = "Arrow",
                            tint = iconColor,
                            modifier = Modifier
                                .size(50.dp)
                                .padding(start = 4.dp)
                        )
                    }

                    Column(modifier = Modifier.fillMaxWidth())
                    {
                        Text(
                            text = title,
                            color = CustomSilver,
                            fontSize = 20.sp,
                            modifier = Modifier.offset(y = 10.dp)
                        )
                        Spacer(modifier = Modifier.height(15.dp))   // spacing between texts
                        Text(
                            text = "${currentVal.floatValue}",
                            color = CustomSilver,
                            fontSize = 30.sp,
                            modifier = Modifier.offset(x = 20.dp)
                        )
                    }
                }
            }
        }
    }

    fun UpdateVal(pkt: List<Byte>, pktInfoType: Int)
    {
        val dataBytes = (pkt[2].toInt() and 0xFF)        or
                ((pkt[3].toInt() and 0xFF) shl 8)  or
                ((pkt[4].toInt() and 0xFF) shl 16) or
                ((pkt[5].toInt() and 0xFF) shl 24)


        when(pktInfoType)
        {
            1 ->
            {
                currentVal.floatValue = Float.fromBits(dataBytes)
                println("currentVal.floatValue: ${currentVal.floatValue}")
            }

            2 ->
            {
                maxVal.floatValue = Float.fromBits(dataBytes)
            }

            3 ->
            {
                minVal.floatValue = Float.fromBits(dataBytes)
            }
        }

    }

    companion object {
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
                CrossDevicePackets.XDEVMSG_LIVE_TEMP_DATA,
                CrossDevicePackets.XDEVMSG_MAX_TEMP_DATA,
                CrossDevicePackets.XDEVMSG_MIN_TEMP_DATA -> {
                    dst = 1
                }
                CrossDevicePackets.XDEVMSG_HUM_DATA_REQ -> TODO()
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
