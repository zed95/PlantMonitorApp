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
    val temp = remember {EnvInfoElement<Float>("Temperature", Icons.Filled.Thermostat, Color(0xFFFF7070), "\u2103")}
    val hum = remember {EnvInfoElement<Float>("Humidity", Icons.Filled.Cloud, Color(0xFF68ADFF), "%")}
    val moist1 = remember { EnvInfoElement<UShort>("Soil Moisture 1", Icons.Filled.WaterDrop, Color(0xFF003FFF), "%") }
    val moist2 = remember { EnvInfoElement<UShort>("Soil Moisture 2", Icons.Filled.WaterDrop, Color(0xFF003FFF), "%") }
    val name = serviceViewModel.selectedDevice.serviceName


    // connect to device selected
    LaunchedEffect(Unit)
    {
        if(SocketManager.ConnectToDevice(serviceViewModel.selectedDevice) == DeviceConnectionSts.CONNECTED)
        {
            XDevMessageBroker.outChannel.send(OutCommands.OUTCMD_DEVICE_DASHBOARD_DATA_ENABLE.id)
        }

        XDevMessageBroker.messages.collect { msg ->
            when(msg)
            {
                is BrokerMessage.EnvMetricTemp -> temp.updateEnvMetrics(msg.current, msg.high, msg.low)
                is BrokerMessage.EnvMetricHum -> hum.updateEnvMetrics(msg.current, msg.high, msg.low)
                is BrokerMessage.EnvMetricSoilM1 -> moist1.updateEnvMetrics(msg.current, msg.high, msg.low)
                is BrokerMessage.EnvMetricSoilM2 -> moist2.updateEnvMetrics(msg.current, msg.high, msg.low)
            }

        }
    }

    DisposableEffect(Unit) {
        onDispose {
            XDevMessageBroker.outChannel.trySend(OutCommands.OUTCMD_DEVICE_DASHBOARD_DATA_DISABLE.id)
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
        moist1.ElementImplement()
        moist2.ElementImplement()
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

class EnvInfoElement<T>(private val title: String,
                     private val icon: ImageVector,
                     private val iconColor: Color,
                     private val units: String,
)
{

    private var currentVal = mutableStateOf<T?>(null)
    private var maxVal = mutableStateOf<T?>(null)
    private var minVal = mutableStateOf<T?>(null)

    private var maxThImp = mutableStateOf<T?>(null)
    private var maxThAct = mutableStateOf<T?>(null)
    private var minThImp = mutableStateOf<T?>(null)
    private var minThAct = mutableStateOf<T?>(null)

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

    fun updateEnvMetrics(current: T, max: T, min: T)
    {
        currentVal.value = current
        maxVal.value = max
        minVal.value = min
    }

    fun updateThresholds(maxAct: T, maxImp: T, minAct: T, minImp: T)
    {
        maxThAct.value = maxAct
        maxThImp.value = maxImp
        minThAct.value = minAct
        minThImp.value = minImp
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
