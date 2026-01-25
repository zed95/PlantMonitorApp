import android.Manifest
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
import android.content.IntentSender
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresPermission
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

enum class ConnectionStatus()
{
    NOT_CONNECTED,
    DISCONNECTED,
    CONNECTED,
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
    var connectionStatus = ConnectionStatus.NOT_CONNECTED

    var buffer = ByteArray(512)

    private var gattCallback = object: BluetoothGattCallback()
    {
        /*******************************************************************************************
         * Called when the connection state to the remote GATT server changes.
         *
         * When a successful connection is established, this callback stores the
         * [BluetoothGatt] instance and initiates service discovery. If the connection
         * is lost or closed, connection state and bonding signals are updated to
         * reflect the failure.
         *
         * @param gatt The GATT client associated with the connection.
         * @param status Status of the connection change.
         * @param newState The new connection state (connected or disconnected).
         *
         * @throws SecurityException If the required BLUETOOTH_CONNECT permission
         * has not been granted.
         ******************************************************************************************/
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if ((newState == BluetoothProfile.STATE_CONNECTED) && (gatt != null)) {
                // Attempts to discover services after successful connection.
                btGatt = gatt
                btGatt?.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                bleConnectSignal.complete(BondingStatus.FAILED)
                connectionStatus = ConnectionStatus.DISCONNECTED
            }
        }

        /*******************************************************************************************
         * Called when GATT service discovery completes.
         *
         * This callback logs all discovered services, locates the required application
         * service and characteristic, enables notifications via the CCCD descriptor,
         * optionally requests the maximum supported MTU size, and updates connection
         * state signaling based on the outcome.
         *
         * If any required service, characteristic, or descriptor operation fails,
         * the connection is marked as unsuccessful.
         *
         * @param gatt The GATT client invoking the callback.
         * @param status Status of the service discovery operation.
         *
         * @throws SecurityException If the required BLUETOOTH_CONNECT permission
         * has not been granted.
         ******************************************************************************************/
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
                        // update connection status
                        connectionStatus = ConnectionStatus.CONNECTED
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

        /*******************************************************************************************
         * Called when the negotiated ATT MTU size for the connection changes.
         *
         * This callback is triggered after a successful or failed MTU exchange
         * request. The resulting MTU size and whether the operation succeeded
         * are logged for diagnostic purposes.
         *
         * @param gatt The GATT client invoking the callback.
         * @param mtu The new ATT MTU size.
         * @param status Status of the MTU change operation.
         ******************************************************************************************/
        override fun onMtuChanged(gatt: BluetoothGatt?, mtu: Int, status: Int) {
            Log.w("BluetoothGattCallback", "ATT MTU changed to $mtu, success: ${status == BluetoothGatt.GATT_SUCCESS}")
        }

        /*******************************************************************************************
         * Called when a characteristic read operation completes.
         *
         * If the read is successful, the received characteristic value is copied
         * into the internal buffer for later processing.
         *
         * @param gatt The GATT client invoking the callback.
         * @param characteristic The characteristic that was read.
         * @param value The value of the characteristic.
         * @param status Status of the read operation.
         ******************************************************************************************/
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
            }
        }

        /*******************************************************************************************
         * Called when a characteristic value change notification or indication is received.
         *
         * The updated characteristic value is copied into the internal buffer and
         * immediately processed as an incoming BLE data packet.
         *
         * @param gatt The GATT client invoking the callback.
         * @param characteristic The characteristic whose value has changed.
         * @param value The updated characteristic value.
         ******************************************************************************************/
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            buffer = value.copyOf()
            processPacket()
        }

        /*******************************************************************************************
         * Called when a descriptor write operation completes.
         *
         * This callback logs whether the descriptor write (commonly used to enable
         * notifications or indications) completed successfully.
         *
         * @param gatt The GATT client invoking the callback.
         * @param descriptor The descriptor that was written.
         * @param status Status of the descriptor write operation.
         ******************************************************************************************/
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

        /*******************************************************************************************
         * Logs the GATT service and characteristic hierarchy for this [BluetoothGatt] instance.
         *
         * This function prints each discovered GATT service and its associated characteristics
         * in a readable, tree-like format using log output. If no services are available, a
         * message is logged indicating that service discovery may not have been performed yet.
         *
         * Intended for debugging and inspection purposes after calling `discoverServices()`.
         ******************************************************************************************/
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

        /*******************************************************************************************
         * Searches the given [BluetoothGattService] for a characteristic matching the provided UUID string.
         *
         * The function iterates through the service’s available GATT characteristics and compares
         * each characteristic's UUID to the supplied UUID. If a matching characteristic is found,
         * it is returned.
         *
         * @param uuidStr A string representation of the UUID of the desired GATT characteristic.
         * @param service The [BluetoothGattService] to search within.
         * @return The matching [BluetoothGattCharacteristic] if found, or `null` if no characteristic
         * with the specified UUID exists in the provided service.
         ******************************************************************************************/
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

        /*******************************************************************************************
         * Searches this [BluetoothGatt] instance for a service matching the given UUID string.
         *
         * The function iterates through the list of discovered GATT services and compares each
         * service's UUID to the provided UUID. If a matching service is found, it is returned.
         *
         * @param uuidStr A string representation of the UUID of the desired GATT service.
         * @return The matching [BluetoothGattService] if found, or `null` if no service with the
         * specified UUID exists.
         ******************************************************************************************/
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

    /***********************************************************************************************
     * Writes a byte array to the configured BLE characteristic.
     *
     * This function sends the provided payload to the `plantMonCharacteristic`
     * using a default GATT write operation. If the characteristic is not
     * initialized, the write is skipped and an error status is returned.
     *
     * @param msg The byte array payload to write to the BLE characteristic.
     * @return A [BluetoothStatusCodes] value indicating the result of the write
     * operation.
     *
     * @throws SecurityException If the required BLUETOOTH_CONNECT permission
     * has not been granted.
     **********************************************************************************************/
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun bleWrite(msg: ByteArray): Int
    {
        var statusCode: Int = BluetoothStatusCodes.ERROR_UNKNOWN

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

    /***********************************************************************************************
     * Processes an incoming BLE data packet stored in the internal buffer.
     *
     * This function validates packet framing by checking for SOP and EOP markers,
     * removes framing bytes, performs packet unstuffing, and verifies the packet
     * checksum. If the packet corresponds to a Wi-Fi status response from the
     * ESP32, the result is propagated by completing the `bleReadSignal`.
     *
     * Packet processing is silently ignored if framing, unstuffing, or checksum
     * validation fails. Currently only a generic failure status is returned when
     * a failure is detected during processing.
     **********************************************************************************************/
    private fun processPacket()
    {
        var packetLen = buffer.size

        // check for packet SOP and EOP markers
        if((buffer[0] == SOP) && (buffer[buffer.lastIndex] == EOP))
        {
            // remove SOP and EOP and update size index
            RemSopEop(buffer)
            packetLen = packetLen - 2
            // unstuff packet and assign unstuffed packet length to packetLen
            packetLen = unstuffPacket(buffer, packetLen)

            // after unstuffing packetLen (size of packet indicator) also carries error coding so check must be made
            if(packetLen >= 0)
            {
                if(calcChecksum(buffer, packetLen) == 0.toByte())
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
    /***********************************************************************************************
     * Cleans up Bluetooth connection resources.
     *
     * Intended to release and reset any active GATT connections, cached devices,
     * or internal state associated with an ongoing or previous Bluetooth session.
     * This method should be called when the connection is no longer needed or
     * when shutting down Bluetooth-related operations.
     **********************************************************************************************/
    fun bluetoothConnectionCleanup()
    {

    }

    /***********************************************************************************************
     * Indicates whether Bluetooth is currently enabled on the device.
     *
     * @return `true` if the Bluetooth adapter is enabled, `false` otherwise.
     **********************************************************************************************/
    fun isBtEnabled(): Boolean {
        return btManager.adapter.isEnabled
    }

    /***********************************************************************************************
     * Initiates pairing and connection to a compatible BLE device.
     *
     * This function uses the Companion Device Manager to discover and associate
     * with a Bluetooth device whose name matches the expected pattern. Once a
     * device is selected and associated by the user, a GATT connection is
     * initiated and the function suspends until the connection result is known.
     *
     * The pairing flow may be canceled by the user, in which case an appropriate
     * [BondingStatus] is returned.
     *
     * @param pairingLauncher An [ActivityResultLauncher] used to present the system
     * device selection UI to the user.
     * @return A [BondingStatus] indicating the outcome of the pairing and connection
     * process.
     *
     * @throws SecurityException If the required BLUETOOTH_CONNECT permission
     * has not been granted.
     **********************************************************************************************/
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
    object LostConnectionWithBleDevice : BluetoothEvent()
}

class BluetoothViewModel(context: Context) : ViewModel() {

    private val btManager = AppBluetoothManager(context)
    private val _events = MutableSharedFlow<BluetoothEvent>()
    val events = _events.asSharedFlow()

    /***********************************************************************************************
     * Starts the Bluetooth pairing and connection flow.
     *
     * This function launches a coroutine in the ViewModel scope to initiate
     * pairing via the provided launcher. It emits UI events reflecting the
     * outcome of the pairing process, including success, failure, user
     * cancellation, or a request to enable Bluetooth.
     *
     * Pairing state is tracked internally to prevent concurrent operations.
     *
     * @param pairingLauncher An [ActivityResultLauncher] used to display the
     * system device selection UI.
     *
     * @throws SecurityException If the required BLUETOOTH_CONNECT permission
     * has not been granted.
     **********************************************************************************************/
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    fun startPairing(pairingLauncher: ActivityResultLauncher<IntentSenderRequest>) {
        viewModelScope.launch {
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
                _events.emit(BluetoothEvent.ConnectionFailed)
            }
            finally {
            }
        }
    }

    /***********************************************************************************************
     * Suspends until a Wi-Fi connection status response is received from the
     * remote BLE device or a timeout occurs.
     *
     * This function waits for a BLE response signal indicating whether the
     * remote device successfully connected to Wi-Fi. The wait is bounded
     * by a fixed timeout to prevent indefinite suspension.
     *
     * @return `true` if the remote device reports a successful Wi-Fi connection
     * within the timeout window, or `false` otherwise.
     *
     * @throws SecurityException If the required BLUETOOTH_CONNECT permission
     * has not been granted.
     **********************************************************************************************/
    @RequiresPermission(value = "android.permission.BLUETOOTH_CONNECT")
    suspend fun waitForWifiConnection(): Boolean {
        return withTimeoutOrNull(10_000) { // 10 seconds
            if(btManager.bleReadSignal.await())
            {
                return@withTimeoutOrNull true
            }
            else
            {
                return@withTimeoutOrNull false
            }
        } ?: false
    }

    /***********************************************************************************************
     * Initiates the BLE-based Wi-Fi connection sequence on the remote device.
     *
     * This function sends Wi-Fi credentials to the connected BLE device and
     * waits for a confirmation response. UI events are emitted to reflect
     * success, failure, or loss of the BLE connection during the process.
     *
     * Bluetooth operations are executed on an IO dispatcher, while lifecycle
     * coordination is managed from the main thread.
     *
     * @param ssid The SSID of the Wi-Fi network to connect to.
     * @param password The password for the specified Wi-Fi network.
     *
     * @throws SecurityException If the required BLUETOOTH_CONNECT permission
     * has not been granted.
     **********************************************************************************************/
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun deviceWifiConnectSequence(ssid: String, password: String)
    {
        // launch coroutine
        CoroutineScope(Dispatchers.Main).launch()
        {
            // run this inside IO coroutine context since we're dealing with bluetooth socket
            withContext(Dispatchers.IO)
            {
                if(btManager.connectionStatus == ConnectionStatus.CONNECTED)
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
                else
                {
                    _events.emit(BluetoothEvent.LostConnectionWithBleDevice)
                }

            }
        }
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