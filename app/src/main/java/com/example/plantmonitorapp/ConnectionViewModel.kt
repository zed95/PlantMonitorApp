package com.example.plantmonitorapp
import android.net.nsd.NsdServiceInfo
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConnectionViewModel(): ViewModel()
{
    private var initialised = false
    private val _connectionSts = MutableStateFlow(DeviceConnectionSts.NOT_CONNECTED)
    val connectionSts = _connectionSts.asStateFlow()

    suspend fun deviceConnect(device: NsdServiceInfo)
    {
        if(!SocketManager.isConnectionActive())
        {
            _connectionSts.value = DeviceConnectionSts.CONNECTING
            _connectionSts.value = SocketManager.ConnectToDevice(device)
        }
        else
        {
            _connectionSts.value = DeviceConnectionSts.CONNECTED
        }
    }

    suspend fun initialise()
    {
        if(!initialised)
        {
            XDevMessageBroker.initChannels()
            initialised = true
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

    fun clearDisconnectState()
    {
        _connectionSts.value = DeviceConnectionSts.NOT_CONNECTED
    }

    fun deviceDisconnect()
    {
        XDevMessageBroker.outChannel.trySend(
            XDevMessageBroker.constructParameterlessRequest(
                OutCommands.OUTCMD_DEVICE_DASHBOARD_DATA_DISABLE.id))
    }
}