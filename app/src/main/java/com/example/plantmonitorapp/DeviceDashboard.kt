package com.example.plantmonitorapp
import android.app.Dialog
import android.net.nsd.NsdServiceInfo
import android.util.Log
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plantmonitorapp.ui.theme.BackgroundGrey
import com.example.plantmonitorapp.ui.theme.CustomSilver
import com.example.plantmonitorapp.ui.theme.ElevatedGrey
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.sql.Connection

@Composable
fun DeviceDashboard(selectedDevice: NsdServiceInfo,
                    dashboardViewModel: DeviceDashboardViewModel = viewModel())
{
    val name = selectedDevice.serviceName
    val isConneting = dashboardViewModel.isConnecting.collectAsStateWithLifecycle(false)
    val connectionSts = dashboardViewModel.connectionSts.collectAsStateWithLifecycle(
        DeviceConnectionSts.NOT_CONNECTED)

    // connect to device selected
    LaunchedEffect(Unit)
    {
        dashboardViewModel.deviceConnect(selectedDevice)
        dashboardViewModel.initialise()
    }

    DisposableEffect(Unit) {
        onDispose {
            XDevMessageBroker.outChannel.trySend(OutCommands.OUTCMD_DEVICE_DASHBOARD_DATA_DISABLE.id)

            //call device dashboard viewmodel to deinitialise

        }
    }


    // This places the button in the center of the screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
    ) {
        TopPanel(name)
        dashboardViewModel.temp.ElementImplement()
        dashboardViewModel.hum.ElementImplement()
        dashboardViewModel.moist1.ElementImplement()
        dashboardViewModel.moist2.ElementImplement()

        when(connectionSts.value)
        {
            DeviceConnectionSts.DISCONNECTED -> Unit
            DeviceConnectionSts.NOT_CONNECTED -> ConnectingDialog()
            DeviceConnectionSts.CONNECTED -> dashboardViewModel.requestDashboardInfo()
            else -> Unit
        }

    }


}

@Composable
fun ConnectingDialog()
{
    Dialog(onDismissRequest = {}) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Connecting...")
            }
        }
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

                DeviceConnectionSts.NOT_CONNECTED,
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
                DeviceConnectionSts.NOT_CONNECTED,
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

class DeviceDashboardViewModel(): ViewModel()
{
    private var initialised = false
    private val _isConnecting = MutableStateFlow(false)
    val isConnecting = _isConnecting.asStateFlow()
    private val _connectionSts = MutableStateFlow(DeviceConnectionSts.NOT_CONNECTED)
    val connectionSts = _connectionSts.asStateFlow()
    val temp = EnvInfoElement<Float>("Temperature", Icons.Filled.Thermostat, Color(0xFFFF7070), "\u2103")
    val hum = EnvInfoElement<Float>("Humidity", Icons.Filled.Cloud, Color(0xFF68ADFF), "%")
    val moist1 = EnvInfoElement<UShort>("Soil Moisture 1", Icons.Filled.WaterDrop, Color(0xFF003FFF), "%")
    val moist2 = EnvInfoElement<UShort>("Soil Moisture 2", Icons.Filled.WaterDrop, Color(0xFF003FFF), "%")

    fun requestDashboardInfo() = viewModelScope.launch()
    {
        XDevMessageBroker.outChannel.send(OutCommands.OUTCMD_DEVICE_DASHBOARD_DATA_ENABLE.id)
        XDevMessageBroker.outChannel.send(OutCommands.OUTCMD_REQUEST_ENV_THRESHOLDS.id)
    }

    suspend fun initialise()
    {
        if(!initialised)
        {
            XDevMessageBroker.initChannels()
            initialised = true

            XDevMessageBroker.messages.collect { msg ->
                when(msg)
                {
                    is BrokerMessage.EnvMetricTemp -> temp.updateEnvMetrics(msg.current, msg.high, msg.low)
                    is BrokerMessage.EnvMetricHum -> hum.updateEnvMetrics(msg.current, msg.high, msg.low)
                    is BrokerMessage.EnvMetricSoilM1 -> moist1.updateEnvMetrics(msg.current, msg.high, msg.low)
                    is BrokerMessage.EnvMetricSoilM2 -> moist2.updateEnvMetrics(msg.current, msg.high, msg.low)
                    is BrokerMessage.EnvThresholdsTemp -> temp.updateThresholds(msg.maxThAct, msg.maxThImp, msg.minThAct, msg.minThImp)
                    is BrokerMessage.EnvThresholdsHum -> hum.updateThresholds(msg.maxThAct, msg.maxThImp, msg.minThAct, msg.minThImp)
                    is BrokerMessage.EnvThresholdsMoisture1 -> moist1.updateThresholds(msg.maxThAct, msg.maxThImp, msg.minThAct, msg.minThImp)
                    is BrokerMessage.EnvThresholdsMoisture2 -> moist2.updateThresholds(msg.maxThAct, msg.maxThImp, msg.minThAct, msg.minThImp)
                }
            }

        }
    }

    suspend fun deviceConnect(device: NsdServiceInfo)
    {
        _isConnecting.value = true
        if(!SocketManager.isConnectionActive())
        {
            _connectionSts.value = SocketManager.ConnectToDevice(device)
        }
        else
        {
            _connectionSts.value = DeviceConnectionSts.CONNECTED
        }
        _isConnecting.value = false
    }

}