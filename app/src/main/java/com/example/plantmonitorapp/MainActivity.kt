package com.example.plantmonitorapp
import BluetoothViewModel
import BluetoothViewModelFactory
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdServiceInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FloatingActionButtonElevation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.plantmonitorapp.ui.theme.BackgroundGreen
import com.example.plantmonitorapp.ui.theme.BackgroundGrey
import com.example.plantmonitorapp.ui.theme.CustomGold
import com.example.plantmonitorapp.ui.theme.CustomSilver
import com.example.plantmonitorapp.ui.theme.ElevatedGreen
import com.example.plantmonitorapp.ui.theme.ElevatedGrey
import com.example.plantmonitorapp.ui.theme.PlantMonitorAppTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

lateinit var appContext: Context


class MainActivity : ComponentActivity() {

    private lateinit var pairingLauncher: ActivityResultLauncher<IntentSenderRequest>
    private val btViewModel: BluetoothViewModel by viewModels {
        BluetoothViewModelFactory(applicationContext)
    }

    private val nsdServiceViewModel: ServiceViewModel by viewModels()

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initLauncher(this)
//        nsdServiceViewModel.startDiscovery()
        nsdServiceViewModel.restartServiceDiscovery()
        setContent {
            PlantMonitorAppTheme {

            MyScreen(btViewModel, pairingLauncher)

            }

            appContext = this
        }
    }

    // when app loses focus
    override fun onPause() {
        super.onPause()
        // stop discovery process to help nsd service discovery work better
        nsdServiceViewModel.stopServiceDiscovery()
    }

    // when app is fully invisible
    override fun onStop() {
        super.onStop()
        // stop discovery process to help nsd service discovery work better
        nsdServiceViewModel.stopServiceDiscovery()
    }

    override fun onDestroy() {
        nsdServiceViewModel.stopServiceDiscovery()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
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

@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
@Composable
fun SetupNewDevice(viewModel: BluetoothViewModel,
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
//                initNsdDiscoveryListener(context)
        }

    }

//    ElevatedButton(
//        colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFFCFD2CF), contentColor = Color.Black),
//        elevation = ButtonDefaults.buttonElevation(10.dp),
//        onClick = {
//            if(setupState == DeviceSetupState.Idle)
//            {
//                setupState = DeviceSetupState.StartPairing
//            }
//                  },
//
//        )
//    {
//        Text("Add Plant Monitor")
//    }

    ExtendedFloatingActionButton(
        icon = { Icon(Icons.Filled.Add, "Extended floating action button.") },
        elevation = FloatingActionButtonDefaults.elevation(10.dp),
        containerColor = CustomSilver,
        text = { Text(text = "New Device") },
        onClick = {
            if(setupState == DeviceSetupState.Idle)
            {
                setupState = DeviceSetupState.StartPairing
            }
        },
    )

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
    val navController = rememberNavController()
    val serviceViewModel: ServiceViewModel = viewModel()

    NavHost(navController = navController, startDestination = "DeviceSelection",
        enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(400)) },
        exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(400)) },
        popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(400)) },
        popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(400)) })
    {
        composable("DeviceSelection")
        {
            DeviceSelectionScreen(viewModel, serviceViewModel, pairingLauncher, navController)
        }

        composable("DeviceDashboard")
        {
            DeviceDashboard(serviceViewModel)
        }
    }

}
@Composable
@RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
fun DeviceSelectionScreen(viewModel: BluetoothViewModel,
                          serviceViewModel: ServiceViewModel,
                          pairingLauncher: ActivityResultLauncher<IntentSenderRequest>,
                          navController: NavHostController)
{
    // This places the button in the center of the screen
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundGrey)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),   // optional spacing from the top
            contentAlignment = Alignment.Center
        )
        {
            Image(
                painter = painterResource(id = R.drawable.start_screen_logo_grey),
                contentDescription = "App logo",
                modifier = Modifier
                    .size(240.dp)
                    .padding(top = 16.dp),
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(),   // optional spacing from the top
            contentAlignment = Alignment.Center
        )
        {
            // Label
            Text(
                text = "Available Devices",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = CustomGold,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth(),   // optional spacing from the top
            contentAlignment = Alignment.Center
        )
        {
            ElevatedCard(
                shape = RoundedCornerShape(10.dp),
                elevation = CardDefaults.cardElevation(10.dp),
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .heightIn(max = 400.dp) // Maximum height
            )
            {
                DeviceList( serviceViewModel, { navController.navigate("DeviceDashboard") })
            }
        }
        Row(modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center)
        {
            SetupNewDevice(viewModel, pairingLauncher)
        }
    }
}


//@Composable
//fun DeviceList(devices: List<NsdServiceInfo>, onDeviceSelected: (NsdServiceInfo) -> Unit)
//{
//    val listState = rememberLazyListState()
//    var selectedItem by remember{ mutableStateOf<NsdServiceInfo?>(null) }
//
//    LazyColumn(state = listState,
//               modifier = Modifier.fillMaxWidth().
//               height(300.dp).
//               clip(RoundedCornerShape(10.dp)).
//               background(ElevatedGrey)
//    )
//
//
//    {
//        items(items = devices)
//        { item ->
//
//            Row(modifier = Modifier
//                .fillMaxWidth()
//                .padding(top = 16.dp, start = 20.dp, end = 10.dp)
//                .selectable(selected = (selectedItem?.serviceName == item.serviceName), onClick = {selectedItem = item})
//                .clickable(            onClick = {selectedItem = item
//                    println("Selected Item: ${selectedItem?.serviceName}")
//                    onDeviceSelected(selectedItem!!)},
//                    interactionSource = remember { MutableInteractionSource() },
//                    indication = ripple(bounded = true, color = Color.Black)
//                ),
//                horizontalArrangement = Arrangement.Absolute.SpaceBetween,
//                verticalAlignment = Alignment.CenterVertically,)
//            {
//                Text(
//                    text = item.serviceName,
//                    color = CustomSilver,
//                    fontSize = 20.sp,
//                    fontWeight = FontWeight.SemiBold,
//                    textAlign = TextAlign.Center,
//                    modifier = Modifier.padding(vertical = 5.dp)
//                )
//
//                Icon(
//                    imageVector = Icons.AutoMirrored.Filled.ArrowRight,
//                    contentDescription = "Arrow",
//                    tint = CustomSilver,
//                    modifier = Modifier
//                        .size(40.dp)
//                        .padding(start = 4.dp)
//                )
//            }
//
//            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = CustomGold)
//        }
//
//        // selectedItem holds the item string that was selected. If I work backwards I can get its index in the list
//        // if I pass the actual List of all available devices advertising plant monitor service I can then display
//        // the list of all the devices select one and connect to it and start displaying the information.
//    }
//}

@Composable
fun DeviceList(viewModel: ServiceViewModel, onDeviceSelected: () -> Unit)
{
    val listState = rememberLazyListState()
    var selectedItem by remember{ mutableStateOf<NsdServiceInfo?>(null) }
    val devices = viewModel.discoveredDevices

    LazyColumn(state = listState,
        modifier = Modifier.fillMaxWidth().
        height(300.dp).
        clip(RoundedCornerShape(10.dp)).
        background(ElevatedGrey)
    )
    {
        items(items = devices)
        { item ->

            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, start = 20.dp, end = 10.dp)
                .selectable(selected = (selectedItem?.serviceName == item.serviceName), onClick = {selectedItem = item})
                .clickable(            onClick = {selectedItem = item
                    println("Selected Item: ${selectedItem?.serviceName}")
                    viewModel.selectDevice(item)
                    onDeviceSelected()},
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, color = Color.Black)
                ),
                horizontalArrangement = Arrangement.Absolute.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,)
            {
                Text(
                    text = item.serviceName,
                    color = CustomSilver,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 5.dp)
                )

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowRight,
                    contentDescription = "Arrow",
                    tint = CustomSilver,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(start = 4.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp), thickness = 1.dp, color = CustomGold)
        }

        // selectedItem holds the item string that was selected. If I work backwards I can get its index in the list
        // if I pass the actual List of all available devices advertising plant monitor service I can then display
        // the list of all the devices select one and connect to it and start displaying the information.
    }
}
