package com.example.plantmonitorapp
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.plantmonitorapp.ui.theme.BackgroundGrey
import com.example.plantmonitorapp.ui.theme.CustomSilver
import com.example.plantmonitorapp.ui.theme.ElevatedGreen
import com.example.plantmonitorapp.ui.theme.ElevatedGrey

@Composable
fun DeviceDashboard(serviceViewModel: ServiceViewModel)
{

    // connect to device selected
    LaunchedEffect(Unit)
    {
        SocketManager.ConnectToDevice(serviceViewModel.selectedDevice)
    }

    val temp = EnvInfoElement("Temperature", Icons.Filled.Thermostat, Color(0xFFFF7070))
    val hum = EnvInfoElement("Humidity", Icons.Filled.Cloud, Color(0xFF68ADFF))
    val moist = EnvInfoElement("Soil Moisture", Icons.Filled.WaterDrop, Color(0xFF003FFF))
    val name = serviceViewModel.selectedDevice.serviceName
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
                            text = "20.00",
                            color = CustomSilver,
                            fontSize = 30.sp,
                            modifier = Modifier.offset(x = 20.dp)
                        )
                    }
                }
            }
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
