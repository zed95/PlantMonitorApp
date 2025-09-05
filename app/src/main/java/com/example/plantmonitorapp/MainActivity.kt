package com.example.plantmonitorapp
import BluetoothViewModel
import BluetoothViewModelFactory
import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.IconButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import com.example.plantmonitorapp.ui.theme.PlantMonitorAppTheme

enum class DeviceSetupState {
    Idle,
    StartPairing,
    RequestBluetoothEnable,
    BondingFailed,
    ConnectionFailed,
    WifiCredentialsInput,
    SendWifiCredentials,
    RemoteDevWifiConnectSuccess,
    RemoteDevWifiConnectFailed,
}


class MainActivity : ComponentActivity() {

    private lateinit var pairingLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val btViewModel: BluetoothViewModel by viewModels {
        BluetoothViewModelFactory(applicationContext)
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initLauncher(this)
        setContent {
            PlantMonitorAppTheme {
            MyScreen(btViewModel, pairingLauncher)

            }
        }
    }

    // launcher for bluetooth has to be registered on creation
    fun initLauncher(activity: ComponentActivity)
    {
        // Initialize the ActivityResultLauncher
        pairingLauncher = activity.registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
            // Handle the result of the activity here
            when (result.resultCode) {
                RESULT_OK -> {/* do nothing */}
                else -> { /* Handle cancellation or failure if necessary */ }
            }
        }
    }
}

fun printMsg(str: String)
{
    println(str)
}

@Composable
fun MyButton(modifier: Modifier)
{
    Button(onClick = {printMsg("Button Clicked!")}, modifier = modifier)
    {
        Text("Click Me")
    }
}

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun SetupNewDevice(viewModel: BluetoothViewModel, modifier: Modifier,
                    pairingLauncher: ActivityResultLauncher<IntentSenderRequest>)
{
    val context = LocalContext.current
    val isPairing = viewModel.isPairing
    var subSsid by remember { mutableStateOf("") }
    var subPassword by remember { mutableStateOf("") }
    var setupState by rememberSaveable { mutableStateOf(DeviceSetupState.Idle) }
    var displayConfirmationPrompt by rememberSaveable { mutableStateOf(false) }

    when(setupState)
    {
        DeviceSetupState.Idle ->
        {
            // do nothing
        }
        DeviceSetupState.StartPairing ->
        {
            viewModel.startPairing(pairingLauncher)
        }
        DeviceSetupState.RequestBluetoothEnable ->
        {
            InfoDialog(onConfirmation = {setupState = DeviceSetupState.Idle},
            dialogTitle = "Bluetooth Disabled",
            dialogText = "This application requires bluetooth to setup new plant monitor devices. Please enable bluetooth and try again.",
            icon = Icons.Default.Info)
        }
        DeviceSetupState.BondingFailed ->
        {
            InfoDialog(onConfirmation = {setupState = DeviceSetupState.Idle},
                dialogTitle = "Pairing Failed",
                dialogText = "Failed to pair with selected device. Try again or select a different device.",
                icon = Icons.Default.Error)
        }
        DeviceSetupState.ConnectionFailed ->
        {
            InfoDialog(onConfirmation = {setupState = DeviceSetupState.Idle},
                dialogTitle = "Failed To Connect",
                dialogText = "Failed to establish a Bluetooth connection with the selected device. Try again or select a different device.",
                icon = Icons.Default.Error)
        }
        DeviceSetupState.WifiCredentialsInput ->
        {
            CredentialsDialog(
                onDismissRequest = {displayConfirmationPrompt = true},
                onConfirmation = { ssid, password ->
                    subSsid = ssid
                    subPassword = password
                    setupState = DeviceSetupState.SendWifiCredentials
                }
            )

            if(displayConfirmationPrompt)
            {
                ConfirmChoiceDialog(
                    onDismissRequest = { displayConfirmationPrompt = false},
                    onConfirmation =
                        {
                        setupState = DeviceSetupState.Idle
                        displayConfirmationPrompt = false
                        },
                    dialogTitle = "Confirm Action",
                    dialogText = "Are you sure you want to end the setup process? You will have to start again.",
                    icon = Icons.Default.Info
                )
            }
        }
        DeviceSetupState.SendWifiCredentials ->
        {
            LoadingDialog("Connecting device to network...")
            viewModel.deviceWifiConnectSequence(subSsid, subPassword)
        }
        DeviceSetupState.RemoteDevWifiConnectFailed ->
        {
            InfoDialog(onConfirmation = {setupState = DeviceSetupState.WifiCredentialsInput},
                dialogTitle = "Device Connect Failed",
                dialogText = "Device failed to connect to the WiFi network.",
                icon = Icons.Default.Dangerous)
        }
        DeviceSetupState.RemoteDevWifiConnectSuccess ->
        {
            InfoDialog(onConfirmation = {setupState = DeviceSetupState.Idle},
                dialogTitle = "Device Connected",
                dialogText = "Device successfully connected to the WiFi network",
                icon = Icons.Default.CheckCircle)
        }

    }

    Button(
        onClick = {
            if(setupState == DeviceSetupState.Idle)
            {
                setupState = DeviceSetupState.StartPairing
            }
                  },

        modifier = modifier)
    {
        Text("Add Plant Monitor")
    }

    if (isPairing) {
        // Loading spinner overlay
        CircularProgressIndicator(
            modifier = Modifier.width(64.dp),
            color = MaterialTheme.colorScheme.secondary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }


    // One-time Toasts
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                BluetoothEvent.RequestBluetoothEnable -> setupState = DeviceSetupState.RequestBluetoothEnable
                BluetoothEvent.DeviceBondingFailed -> setupState = DeviceSetupState.BondingFailed
                BluetoothEvent.DeviceSelectionCancelled -> setupState = DeviceSetupState.Idle

                BluetoothEvent.DeviceBonded ->
                    Toast.makeText(context, "Device Bonded", Toast.LENGTH_LONG).show()
                BluetoothEvent.ConnectionSuccess ->
                {
                    Toast.makeText(context, "Connection Success", Toast.LENGTH_LONG).show()
                    setupState = DeviceSetupState.WifiCredentialsInput
                }

                BluetoothEvent.ConnectionFailed -> setupState = DeviceSetupState.ConnectionFailed
                BluetoothEvent.RemoteDeviceWifiConnectFailed -> setupState = DeviceSetupState.RemoteDevWifiConnectFailed
                BluetoothEvent.RemoteDeviceWifiConnectSuccess -> setupState = DeviceSetupState.RemoteDevWifiConnectSuccess
            }
        }
    }
}

@Composable
fun InfoDialog(
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = "Example Icon")
        },
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = {
            // do nothing
        },
        confirmButton = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center)
            {
                TextButton(onClick = { onConfirmation() }
                ) {
                    Text("Ok")
                }
            }

        }
    )
}

@Composable
fun LoadingDialog(message: String)
{
    Dialog(onDismissRequest = {})
    {
        Card(modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .padding(25.dp),
            shape = RoundedCornerShape(16.dp))
        {
            Text (
                text = message,
                textAlign = TextAlign.Center,
            )

            // Loading spinner overlay
            CircularProgressIndicator(
                modifier = Modifier
                    .width(32.dp)
                    .align(Alignment.CenterHorizontally),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

        }
    }
}

@Composable
fun CredentialsDialog(onDismissRequest: () -> Unit,
                      onConfirmation: (ssid: String, password: String) -> Unit)
{
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var show  by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = {onDismissRequest()})
    {
        Card(modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .padding(25.dp),
            shape = RoundedCornerShape(16.dp))
        {

            Text (
                text = "Connect plant monitor to WiFi network" ,
                textAlign = TextAlign.Center,
            )

            SsidInput(ssid) { ssid = it }
            PasswordInput(password) { password = it }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End)
            {
                TextButton(onClick = {onDismissRequest()}) {
                    Text("Cancel")
                }

                TextButton(onClick = { onConfirmation(ssid, password)
                    show = true}
                ) {
                    Text("Submit")
                }
            }

            if(show)
            {
                // Loading spinner overlay
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(32.dp)
                        .align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

        }
    }
}

@Composable
fun PasswordInput(password: String, onPasswordChange: (String) -> Unit)
{
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    OutlinedTextField(
        value = password,
        onValueChange = { onPasswordChange(it)},
        label = { Text("Password") },
        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            val image = if (passwordVisible)
                Icons.Default.Visibility
            else Icons.Default.VisibilityOff

            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                Icon(imageVector = image, contentDescription = "Toggle password visibility")
            }
        }
    )
}

@Composable
fun SsidInput(ssid: String, onSsidChange: (String) -> Unit)
{
    OutlinedTextField(
        value = ssid,
        onValueChange = { onSsidChange(it) },
        label = { Text("SSID") }
    )
}

@Composable
fun ConfirmChoiceDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector,
) {
    AlertDialog(
        icon = {
            Icon(icon, contentDescription = "Example Icon")
        },
        title = {
            Text(text = dialogTitle)
        },
        text = {
            Text(text = dialogText)
        },
        onDismissRequest = {
            onDismissRequest()
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirmation()
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onDismissRequest()
                }
            ) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun ConnectingDialog(
    onDismissRequest: () -> Unit,
    onConfirmation: () -> Unit,
    dialogTitle: String,
    dialogText: String,
    icon: ImageVector,)
{
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var show  by rememberSaveable { mutableStateOf(false) }

    Dialog(onDismissRequest = {onDismissRequest()})
    {
        Card(modifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .padding(25.dp),
            shape = RoundedCornerShape(16.dp))
        {

            Text (
                text = "Connect plant monitor to WiFi network" ,
                textAlign = TextAlign.Center,
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End)
            {
                TextButton(onClick = {onDismissRequest()}) {
                    Text("Cancel")
                }

            }

            if(show)
            {
                // Loading spinner overlay
                CircularProgressIndicator(
                    modifier = Modifier
                        .width(32.dp)
                        .align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }

        }
    }
}


@Composable
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun MyScreen(viewModel: BluetoothViewModel, pairingLauncher: ActivityResultLauncher<IntentSenderRequest>) {
    // This places the button in the center of the screen
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Red),
        contentAlignment = Alignment.Center
    ) {
        MyButton(Modifier.align(Alignment.BottomStart))

        SetupNewDevice(viewModel, Modifier.align(Alignment.BottomEnd), pairingLauncher)
    }

}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    PlantMonitorAppTheme {
        Greeting("Android")
    }
}