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
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        DeviceDashBaordName(name!!)
        temp.ElementImplement()
        hum.ElementImplement()
        moist.ElementImplement()
    }

    SocketManager.ConnectToDevice(serviceViewModel.selectedDevice)
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