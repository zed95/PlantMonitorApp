package com.example.plantmonitorapp
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

class ConnectionViewModel(): ViewModel()
{
    private val _connectionSts = MutableStateFlow(DeviceConnectionSts.NOT_CONNECTED)
    val connectionSts = _connectionSts.asStateFlow()
    private val _navigateToDashboard = Channel<Unit>(Channel.BUFFERED)
    val navigateToDashboard = _navigateToDashboard.receiveAsFlow()

    private var brokerCollectJob: Job? = null

    private var connectionJob: Job? = null

    fun deviceConnect(device: NsdServiceInfo) = viewModelScope.launch()
    {
        connectionJob?.cancel()

        if(!SocketManager.isConnectionActive())
        {
            _connectionSts.value = DeviceConnectionSts.CONNECTING
            val result = SocketManager.ConnectToDevice(device)
            _connectionSts.value = result
            if (result == DeviceConnectionSts.CONNECTED)
            {
                _navigateToDashboard.trySend(Unit)
            }
        }
        else
        {
            _connectionSts.value = DeviceConnectionSts.CONNECTED
            _navigateToDashboard.trySend(Unit)
        }
    }

    fun processConnectionStatusUpdate(status: DeviceConnectionSts?)
    {
        if(status != null)
        {
            _connectionSts.value = status
        }
        else
        {
            // unverified status results unknown status
            _connectionSts.value = DeviceConnectionSts.UNKNOWN
        }
    }

    fun openCommsChannels() = viewModelScope.launch {
        if (brokerCollectJob?.isActive == true) return@launch
        XDevMessageBroker.initChannels()
        brokerCollectJob = viewModelScope.launch {
            XDevMessageBroker.messages.collect { msg ->
                when (msg) {
                    is BrokerMessage.DeviceConnectionStatus -> processConnectionStatusUpdate(msg.status)
                    else -> Unit
                }
            }
        }
    }

//    suspend fun openCommsChannels()
//    {
//        if(!initialised)
//        {
//            XDevMessageBroker.initChannels()
//            initialised = true
//
//            XDevMessageBroker.messages.collect { msg ->
//                when (msg) {
//                    is BrokerMessage.DeviceConnectionStatus -> processConnectionStatusUpdate(msg.status)
//                    else -> Unit
//                }
//            }
//        }
//    }

//    fun closeCommsChannels()
//    {
//        XDevMessageBroker.closeChannels()
//    }

    fun closeCommsChannels() = viewModelScope.launch {
        brokerCollectJob?.cancel()
        brokerCollectJob = null
        XDevMessageBroker.closeChannels()
    }

    fun clearDisconnectState()
    {
        _connectionSts.value = DeviceConnectionSts.NOT_CONNECTED
    }

    fun deviceDisconnect() = viewModelScope.launch()
    {
        println("Here")
        XDevMessageBroker.outChannel.trySend(
            XDevMessageBroker.constructParameterlessRequest(
                OutCommands.OUTCMD_DEVICE_DASHBOARD_DATA_DISABLE.id))
        delay(50)
        println("Here1")
        SocketManager.Disconnect()
        println("Here2")
        closeCommsChannels()
        println("Here3")
        _connectionSts.value = DeviceConnectionSts.NOT_CONNECTED
        println("Here4")
    }
}