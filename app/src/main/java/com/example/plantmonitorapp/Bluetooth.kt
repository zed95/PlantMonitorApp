import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.COMPANION_DEVICE_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresPermission
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.plantmonitorapp.SOP
import com.example.plantmonitorapp.msgConnectEsp32ToWifi
import com.example.plantmonitorapp.msgRequestWifiConnectSts
import com.example.plantmonitorapp.wifiConnectSts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.UUID
import java.util.concurrent.Executor
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume


enum class xDevCommPacketReadState {
    WAIT_FOR_SOP,
    READ_HEADER,
    READ_PAYLOAD,
    VALIDATE
}

enum class BondingStatus(val id: Int) {
    SUCCESS(0),
    FAILED(1),
    DEVICE_SELECT_CANCELLED(2)
}

class AppBluetoothManager(val context: Context)
{
    private var btDev: BluetoothDevice ?= null
    private lateinit var btSocket: BluetoothSocket
    private lateinit var uuid: UUID
    private lateinit var bondReceiver: BroadcastReceiver
    val btManager: BluetoothManager = context.getSystemService(BluetoothManager::class.java)

    // once bluetooth manager is not used anymore, clear information associated with old connection
    fun bluetoothConnectionCleanup()
    {
        btSocket.close()
        uuid = UUID.randomUUID()
        unregisterReceivers()
    }


    fun registerReceivers() {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_UUID)
        }
        context.registerReceiver(bondReceiver, filter)
    }

    fun unregisterReceivers()
    {
        context.unregisterReceiver(bondReceiver)
    }

    fun isBtEnabled(): Boolean {
       return btManager.adapter.isEnabled
    }


    suspend fun send(msg: ByteArray): Boolean
    {
        var result = false

        withContext(Dispatchers.IO) {
            try {
                val outputStream = btSocket.outputStream
                outputStream.write(msg)
                outputStream.flush()
                result = true
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return result
    }

    suspend fun read(msgBytes: ByteArray): Int
    {
        var bytesRead = 0

        withContext(Dispatchers.IO) {
            try {
                val inputStream = btSocket.inputStream
                if(inputStream.available() > 0)
                {
                    bytesRead = inputStream.read(msgBytes,0,1)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return bytesRead
    }

    suspend fun readBytes(msgBytesDst: ByteArray, idx: Int, numBytes: Int): Int
    {
        var bytesRead = 0

        withContext(Dispatchers.IO) {
            try {
                val inputStream = btSocket.inputStream
                if(inputStream.available() >= numBytes)
                {
                    bytesRead = inputStream.read(msgBytesDst,idx,numBytes)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        return bytesRead
    }

    // Compare selected bluetooth device and check the paired devices list to see if it's already bonded
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun checkDeviceBonded(selectedDevice: BluetoothDevice?) : Boolean
    {
        val adapter: BluetoothAdapter = btManager.adapter
        val bondedDevices: Set<BluetoothDevice>? = adapter.bondedDevices
        val alreadyBonded = bondedDevices.orEmpty().any { it.address == selectedDevice?.address }

        return alreadyBonded
    }


    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun connectPairedDev(): Boolean
    {
        var connectSts = false

        try {
            withContext(Dispatchers.IO) {
                if(!::btSocket.isInitialized)
                {
                    btSocket = btDev!!.createInsecureRfcommSocketToServiceRecord(uuid)
                }

                if(!btSocket.isConnected)
                {
                    btSocket.connect()
                }
                connectSts = true
            }
            // success
        } catch (e: IOException) {
            e.printStackTrace()
            try {
                btSocket.close()
            } catch (ce: IOException) {
                ce.printStackTrace()
            }
        }

        return connectSts
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun pairDevice(pairingLauncher: ActivityResultLauncher<IntentSenderRequest>): BondingStatus = suspendCancellableCoroutine { cont ->
        bondReceiver = object : BroadcastReceiver()
        {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)
                        if (bondState == BluetoothDevice.BOND_BONDED && device != null) {
                            btDev = device
                            btDev?.fetchUuidsWithSdp()
                        }
                    }
                    // Warning!! - This will trigger for any device uuid action and if btDev is not initialised
                    // an exception will occur. I have to modify code to only receive this when btDev is
                    // already initialised
                    BluetoothDevice.ACTION_UUID -> {
                        val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                        if(btDev != null)
                        {
                            if (device?.address == btDev?.address) {
                                val uuids = intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID,ParcelUuid::class.java)
                                    ?.filterIsInstance<ParcelUuid>()

                                // don't do anything with empty list
                                if(!uuids.isNullOrEmpty())
                                {
                                    uuids.forEach { Log.d("BT", "UUID: ${it.uuid}") }
                                    val devUuid = uuids.first()
                                    uuid = devUuid.uuid
                                    // stop listening for bluetooth broadcasts after device paired and uuid obtained
                                    context?.unregisterReceiver(this)
                                    cont.resume(BondingStatus.SUCCESS)
                                }
                                else
                                {
                                    cont.resume(BondingStatus.FAILED)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Register the receiver and launch pairing
        registerReceivers()
        // If coroutine is cancelled, clean up receiver
        cont.invokeOnCancellation { context.unregisterReceiver(bondReceiver) }

        val deviceFilter: BluetoothDeviceFilter = BluetoothDeviceFilter.Builder()
            // Match only Bluetooth devices whose name matches the pattern.
            .setNamePattern(Pattern.compile("PlantMon"))
            .build()
        val pairingRequest: AssociationRequest = AssociationRequest.Builder().addDeviceFilter(deviceFilter).build()
        val deviceManager = context.getSystemService(COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        val executor = Executor { it.run() }
        val callback = object : CompanionDeviceManager.Callback() {
            // Called when a device is found. Launch the IntentSender so the user
            // can select the device they want to pair with.
            override fun onAssociationPending(intentSender: IntentSender) {
                val request = IntentSenderRequest.Builder(intentSender).build()
                pairingLauncher.launch(request)

            }

            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onAssociationCreated(associationInfo: AssociationInfo) {
                btDev = associationInfo.associatedDevice?.bluetoothDevice

                // selected device already bonded?
                if(checkDeviceBonded(btDev))
                {
                    // obtain uuid from it
                    btDev?.fetchUuidsWithSdp()
                }
                else{
                    // bond with selected device
                    btDev?.createBond()
                }
            }

            override fun onFailure(errorMessage: CharSequence?) {
                context.unregisterReceiver(bondReceiver)
                // To handle the failure.
                cont.resume(BondingStatus.DEVICE_SELECT_CANCELLED)
            }
        }

        deviceManager.associate(pairingRequest, executor, callback)
    }
}


sealed class BluetoothEvent {

    object RequestBluetoothEnable: BluetoothEvent()
    object DeviceSelectionCancelled: BluetoothEvent()
    object DeviceBondingFailed: BluetoothEvent()
    object DeviceBonded : BluetoothEvent()
    object ConnectionSuccess : BluetoothEvent()
    object ConnectionFailed : BluetoothEvent()
    object RemoteDeviceWifiConnectFailed : BluetoothEvent()
    object RemoteDeviceWifiConnectSuccess : BluetoothEvent()
}

class BluetoothViewModel(context: Context) : ViewModel() {

    private val btManager = AppBluetoothManager(context)
    var connected by mutableStateOf(false)
        private set
    var isPairing by mutableStateOf(false)
        private set

    private val _events = MutableSharedFlow<BluetoothEvent>()
    val events = _events.asSharedFlow()

    suspend fun btSend(msg: ByteArray): Boolean
    {
        return btManager.send(msg)
    }


    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    fun startPairing(pairingLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        viewModelScope.launch {
            isPairing = true
            try {

                if(btManager.isBtEnabled())
                {
                    when(btManager.pairDevice(pairingLauncher)) // suspend function
                    {
                        BondingStatus.FAILED ->
                        {
                            _events.emit(BluetoothEvent.DeviceBondingFailed)
                        }
                        BondingStatus.DEVICE_SELECT_CANCELLED ->
                        {
                            _events.emit(BluetoothEvent.DeviceSelectionCancelled)
                        }
                        BondingStatus.SUCCESS ->
                        {
                            _events.emit(BluetoothEvent.DeviceBonded)
                            // Runs only after pairing is done
                            if(btManager.connectPairedDev())
                            {
                                _events.emit(BluetoothEvent.ConnectionSuccess)
                            }
                            else
                            {
                                _events.emit(BluetoothEvent.ConnectionFailed)
                            }

                        }
                    }
                }
                // needs enabling
                else
                {
                    _events.emit(BluetoothEvent.RequestBluetoothEnable)
                }

            }
            catch (e: Exception) {
                connected = false
                _events.emit(BluetoothEvent.ConnectionFailed)
            }
            finally {
                isPairing = false
            }
        }
    }

    suspend fun waitForWifiConnection(): Boolean {
        return withTimeoutOrNull(10_000) { // 10 seconds
            while (true) {
                if (getDeviceWifiConnectSts()) {
                    return@withTimeoutOrNull true
                }
                delay(500) // poll every second
            }
            // Not reachable
            false
        } ?: false
    }

    // attempts to send request for wifi connect status from plant monitor and then listens for response
    // done as if the size fo the response is variable.
    suspend fun getDeviceWifiConnectSts(): Boolean
    {
        val buffer = ByteArray(512)
        var connected = false

        if(btSend(msgRequestWifiConnectSts()))
        {
            delay(500)
            if(readPacket(buffer))
            {
                // received packet is connect sts and sts is connected
                if(buffer[1] == wifiConnectSts && buffer[6] == 1.toByte())
                {
                    connected = true
                }
            }
        }

        return connected
    }


    // add timeout mechanism to this function
    suspend fun readPacket(buffer: ByteArray): Boolean
    {
        var packetReadState = xDevCommPacketReadState.WAIT_FOR_SOP
        var readComplete = false
        var bufIdx = 0
        var payloadSize = 0

        while(!readComplete && coroutineContext.isActive)
        {
            when(packetReadState)
            {
                xDevCommPacketReadState.WAIT_FOR_SOP ->
                {
                    if((btManager.readBytes(buffer, bufIdx, 1) == 1))
                    {
                        // proceed to next stage when SOP is found
                        if(buffer[bufIdx] == SOP)
                        {
                            bufIdx++
                            packetReadState = xDevCommPacketReadState.READ_HEADER
                        }
                    }
                }
                xDevCommPacketReadState.READ_HEADER -> {
                    if((btManager.readBytes(buffer, bufIdx, 5) == 5))
                    {
                        // point to the payload size bytes
                        bufIdx++
                        // convert payload size bytes to payload size value
//                        payloadSize = bytesToInt(buffer, bufIdx)
                        payloadSize = buffer[bufIdx].toInt()
                        println("Payload Size: $payloadSize")
                        // point to dst of payload data
                        bufIdx += Int.SIZE_BYTES
                        packetReadState = xDevCommPacketReadState.READ_PAYLOAD
                    }
                }
                xDevCommPacketReadState.READ_PAYLOAD ->
                {
                    if((btManager.readBytes(buffer, bufIdx, payloadSize) == payloadSize))
                    {
                        packetReadState = xDevCommPacketReadState.VALIDATE
                    }
                }
                xDevCommPacketReadState.VALIDATE ->
                {
                    // do checksum recalculation to check for corruption
                    readComplete = true
                }
            }
        }

        return readComplete
    }

    fun deviceWifiConnectSequence(ssid: String, password: String)
    {
        // launch coroutine
        CoroutineScope(Dispatchers.Main).launch()
        {
            // run this inside IO coroutine context since we're dealing with bluetooth socket
            withContext(Dispatchers.IO)
            {
                if(btSend(msgConnectEsp32ToWifi(ssid, password)))
                {
                    if(waitForWifiConnection())
                    {
                        _events.emit(BluetoothEvent.RemoteDeviceWifiConnectSuccess)
                    }
                    else
                    {
                        _events.emit(BluetoothEvent.RemoteDeviceWifiConnectFailed)
                    }
                }
                else
                {
                    _events.emit(BluetoothEvent.RemoteDeviceWifiConnectFailed)
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // unregister receivers tied to activity to prevent memory leaks and errors associated with them
        btManager.unregisterReceivers()
    }
}


class BluetoothViewModelFactory(val context: Context

) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(BluetoothViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return BluetoothViewModel(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}