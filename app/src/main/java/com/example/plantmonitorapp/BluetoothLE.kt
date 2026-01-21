import android.Manifest
import android.app.Service
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.companion.AssociationInfo
import android.companion.AssociationRequest
import android.companion.BluetoothDeviceFilter
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Context.COMPANION_DEVICE_SERVICE
import android.content.Intent
import android.content.IntentSender
import android.os.Binder
import android.os.IBinder
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
import com.example.plantmonitorapp.EOP
import com.example.plantmonitorapp.REQUEST_RSP_ESP32_WIFI_STS
import com.example.plantmonitorapp.RemSopEop
import com.example.plantmonitorapp.SOP
import com.example.plantmonitorapp.calcChecksum
import com.example.plantmonitorapp.msgConnectEsp32ToWifi
import com.example.plantmonitorapp.unstuffPacket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.Executor
import java.util.regex.Pattern
import kotlin.coroutines.coroutineContext


enum class xDevCommPacketReadState {
    WAIT_FOR_SOP,
    READ_HEADER,
    READ_PAYLOAD,
    WAIT_EOP,
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
    private var btGatt: BluetoothGatt? = null
    private var plantMonCharacteristic: BluetoothGattCharacteristic? = null
    private var service: BluetoothGattService? = null
    val btManager: BluetoothManager = context.getSystemService(BluetoothManager::class.java)
    var bleConnectSignal = CompletableDeferred<BondingStatus>()
    var bleReadSignal = CompletableDeferred<Boolean>()
    var buffer = ByteArray(512)

    private var gattCallback = object: BluetoothGattCallback()
    {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if ((newState == BluetoothProfile.STATE_CONNECTED) && (gatt != null)) {
                // Attempts to discover services after successful connection.
                btGatt = gatt
                btGatt?.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                bleConnectSignal.complete(BondingStatus.FAILED)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {

            with(gatt)
            {
                Log.w("BluetoothGattCallback", "Discovered ${services.size} services for ${device.address}")
                printGattTable()
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                service = gatt.findService("4fafc201-1fb5-459e-8fcc-c5c9c331914b")

                if(service != null)
                {
                    plantMonCharacteristic = gatt.findCharacteristic("18537c21-4807-45be-94f2-e55b4561b270", service!!)

                    if(plantMonCharacteristic != null)
                    {
                        // Enable Notifications capability (allows plant monitor to notify app with data causing onCharacteristicChanged callback to be triggered)
                        gatt.setCharacteristicNotification(plantMonCharacteristic, true)
                        val cccdUuid = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                        val descriptor = plantMonCharacteristic!!.getDescriptor(cccdUuid)
                        gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)

                        // check largest packet that can be sent
                        val GATT_MAX_MTU_SIZE = 517
                        // Optional MTU request (non-blocking)
                        gatt.requestMtu(GATT_MAX_MTU_SIZE)
                        bleConnectSignal.complete(BondingStatus.SUCCESS)
                    }
                    else
                    {
                        bleConnectSignal.complete(BondingStatus.FAILED)
                    }
                }
                else
                {
                    bleConnectSignal.complete(BondingStatus.FAILED)
                }
            }
            else
            {
                bleConnectSignal.complete(BondingStatus.FAILED)
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.w("BluetoothGattCallback", "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        )
        {
            if(status == BluetoothGatt.GATT_SUCCESS)
            {
                buffer = value.copyOf()
                println("======Read response======")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            buffer = value.copyOf()
            ProcessPacket()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int
        ) {
            if(status == BluetoothGatt.GATT_SUCCESS)
            {
                println("Write to descriptor Successful!")
            }
            else
            {
                println("Write to descriptor NOT Successful!")
            }
        }

        private fun BluetoothGatt.printGattTable() {
            if (services.isEmpty()) {
                Log.i("printGattTable", "No service and characteristic available, call discoverServices() first?")
                return
            }
            services.forEach { service ->
                val characteristicsTable = service.characteristics.joinToString(
                    separator = "n|--",
                    prefix = "|--"
                ) { it.uuid.toString() }
                Log.i("printGattTable", "nService ${service.uuid}nCharacteristics:n$characteristicsTable"
                )
            }
        }

        private fun BluetoothGatt.findCharacteristic(uuidStr: String, service: BluetoothGattService): BluetoothGattCharacteristic?
        {
            val characteristicUuid = UUID.fromString(uuidStr)
            var foundCharacteristic: BluetoothGattCharacteristic? = null
            if (!service.characteristics.isEmpty()) {
                service.characteristics.forEach { characteristic ->
                    if(characteristic.uuid == characteristicUuid)
                    {
                        foundCharacteristic = characteristic
                    }
                }
            }
            return foundCharacteristic
        }

        private fun BluetoothGatt.findService(uuidStr: String): BluetoothGattService?
        {
            var foundService: BluetoothGattService? = null
            val serviceUuid = UUID.fromString(uuidStr)
            if (!services.isEmpty()) {
                services.forEach { service ->
                    if(service.uuid == serviceUuid)
                    {
                        foundService = service
                    }
                }
            }
            return foundService
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun bleRead()
    {
        btGatt?.readCharacteristic(plantMonCharacteristic)
    }


    /*
    * TO-DO
    *
    * THIS NEEDS REDOING BECAUSE IT RELIES ON THE CONNECTION SIGNAL TO  CONTINUE.
    * THERE SHOULD BE ANOTHER MECHANISM BEFORE TO CHECK IF THE CONNECTION IS OKAY OR NOT
    *
    * */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun bleWrite(msg: ByteArray): Int
    {
        var statusCode: Int = BluetoothStatusCodes.ERROR_UNKNOWN

        if(bleConnectSignal.await() == BondingStatus.FAILED)
        {
            println("Device Connect Failed!")
        }
        if(plantMonCharacteristic != null)
        {
            statusCode = btGatt!!.writeCharacteristic(plantMonCharacteristic!!, msg, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        }
        else
        {
            println("Characteristic is null!")
        }

        return statusCode
    }

    fun ProcessPacket()
    {
        var bufIdx = buffer.size

        // check for packet SOP and EOP markers
        if((buffer[0] == SOP) && (buffer[buffer.lastIndex] == EOP))
        {
            // remove SOP and EOP and update size index
            RemSopEop(buffer)
            bufIdx = bufIdx - 2
            // unstuff packet and assign unstuffed packet length to bufIdx
            bufIdx = unstuffPacket(buffer, bufIdx)

            // after unstuffing bufIdx (size of packet indicator) also carries error coding so check must be made
            if(bufIdx >= 0)
            {
                if(calcChecksum(buffer, bufIdx) == 0.toByte())
                {
                    if(buffer[0] == REQUEST_RSP_ESP32_WIFI_STS)
                    {
                        if(buffer[5] == 7.toByte())
                        {
                            bleReadSignal.complete(true)
                        }
                        else
                        {
                            bleReadSignal.complete(false)
                        }
                    }
                }
            }
        }
    }

    // once bluetooth manager is not used anymore, clear information associated with old connection
    fun bluetoothConnectionCleanup()
    {

    }

    fun isBtEnabled(): Boolean {
        return btManager.adapter.isEnabled
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    suspend fun pairDevice(pairingLauncher: ActivityResultLauncher<IntentSenderRequest>): BondingStatus
    {
        try
        {
            bleConnectSignal = CompletableDeferred()    // reset connect signal
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
                    // attempt to connect
                    btGatt = btDev?.connectGatt(context, false, gattCallback)
                }

                override fun onFailure(errorMessage: CharSequence?) {
                    // do nothing here
                }

                override fun onFailure(errorCode: Int, error: CharSequence?) {
                    if((errorCode == CompanionDeviceManager.RESULT_USER_REJECTED) || (errorCode == CompanionDeviceManager.RESULT_CANCELED))
                    {
                        bleConnectSignal.complete(BondingStatus.DEVICE_SELECT_CANCELLED)
                    }
                }
            }

            deviceManager.associate(pairingRequest, executor, callback)
            return bleConnectSignal.await()
        }
        finally
        {
            coroutineContext.job.invokeOnCompletion {
                if (it is CancellationException) {
                    btGatt?.close()
                }
            }
        }
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
                            _events.emit(BluetoothEvent.ConnectionSuccess)
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

    /*
    * TO-DO
    *
    * try removing the delay and the false that give warnings and see if the function still works
    * okay
    * */
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun waitForWifiConnection(): Boolean {
        return withTimeoutOrNull(10_000) { // 10 seconds
            while (true) {
                if(btManager.bleReadSignal.await())
                {
                    return@withTimeoutOrNull true
                }
                else
                {
                    return@withTimeoutOrNull false
                }
                delay(500) // poll every second
            }
            // Not reachable
            false
        } ?: false
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun deviceWifiConnectSequence(ssid: String, password: String)
    {
        // launch coroutine
        CoroutineScope(Dispatchers.Main).launch()
        {
            // run this inside IO coroutine context since we're dealing with bluetooth socket
            withContext(Dispatchers.IO)
            {
                if(btManager.bleWrite(msgConnectEsp32ToWifi(ssid, password)) == BluetoothStatusCodes.SUCCESS)
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


class BluetoothLeService : Service() {

    private val binder = LocalBinder()

    override fun onBind(intent: Intent): IBinder? {
        return binder
    }

    inner class LocalBinder : Binder() {
        fun getService() : BluetoothLeService {
            return this@BluetoothLeService
        }
    }
}