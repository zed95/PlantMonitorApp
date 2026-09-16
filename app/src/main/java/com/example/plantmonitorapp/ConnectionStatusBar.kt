package com.example.plantmonitorapp

import android.net.nsd.NsdServiceInfo
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plantmonitorapp.ui.theme.BackgroundGrey
import com.example.plantmonitorapp.ui.theme.CustomSilver
import com.example.plantmonitorapp.ui.theme.ElevatedGrey

@Composable
fun DeviceConnectionStatusBar(deviceName: String,
                              connectionViewModel: ConnectionViewModel)
{
    val connectionSts = connectionViewModel.connectionSts.collectAsStateWithLifecycle(
        DeviceConnectionSts.CONNECTED)

    // This places the button in the center of the screen
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BackgroundGrey)
    ) {
        println("Receiving Status: ${connectionSts.value}")
        TopPanel(deviceName, connectionSts.value)

        when(connectionSts.value)
        {
            DeviceConnectionSts.DISCONNECTED -> AlertDisconnect(connectionViewModel)
            DeviceConnectionSts.CONNECTING -> ConnectingDialog()
            DeviceConnectionSts.NOT_CONNECTED -> Unit
            DeviceConnectionSts.CONNECTED -> Unit
            else -> Unit
        }

    }
}

@Composable
fun TopPanel(deviceName: String, connectionSts: DeviceConnectionSts)
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

                TopPanelDevConnectStsIcon(connectionSts)
            }
        }
    }
}

@Composable
fun TopPanelDevConnectStsIcon(connectionSts: DeviceConnectionSts)
{
    Icon(
        imageVector =
            when(connectionSts)
            {
                DeviceConnectionSts.CONNECTED ->
                {
                    Icons.Outlined.Wifi
                }

                DeviceConnectionSts.UNKNOWN,
                DeviceConnectionSts.RECONNECTING,
                DeviceConnectionSts.CONNECTING ->
                {
                    Icons.Filled.Wifi
                }

                DeviceConnectionSts.FAILED_TO_CONNECT,
                DeviceConnectionSts.NOT_CONNECTED,
                DeviceConnectionSts.DISCONNECTED ->
                {
                    Icons.Filled.WifiOff
                }


            },
        contentDescription = "Arrow",
        tint =
            when(connectionSts)
            {
                DeviceConnectionSts.CONNECTED ->
                {
                    Color.Green
                }

                DeviceConnectionSts.UNKNOWN,
                DeviceConnectionSts.RECONNECTING,
                DeviceConnectionSts.CONNECTING ->
                {
                    Color.Yellow
                }
                DeviceConnectionSts.FAILED_TO_CONNECT,
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

@Composable
fun AlertDisconnect(connectionViewModel: ConnectionViewModel)
{
    AlertDialog(
        onDismissRequest = {
            connectionViewModel.clearDisconnectState()
        },
        title = {
            Text("Connection Status")
        },
        text = {
            Text("Lost connection to device.")
        },
        confirmButton = {
            Button(
                onClick = {
                    connectionViewModel.clearDisconnectState()
                }
            ) {
                Text("OK")
            }
        }
    )
}

@Composable
fun AlertFailedToConnect(connectionViewModel: ConnectionViewModel)
{
    AlertDialog(
        onDismissRequest = {
            connectionViewModel.clearDisconnectState()
        },
        title = {
            Text("Connection Status")
        },
        text = {
            Text("Failed to connect to selected device.")
        },
        confirmButton = {
            Button(
                onClick = {
                    connectionViewModel.clearDisconnectState()
                }
            ) {
                Text("OK")
            }
        }
    )
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

class ConnectionStatusBarViewModel(): ViewModel()
{

}