package com.example.plantmonitorapp

import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.navigation.NavHostController
import com.example.plantmonitorapp.SocketManager.handlePacket
import com.example.plantmonitorapp.SocketManager.isActive
import com.example.plantmonitorapp.SocketManager.packetChannel
import com.example.plantmonitorapp.SocketManager.socket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.PrintWriter
import java.net.Socket

enum class DeviceConnectionSts {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

enum class ConnectionAliveSts
{
    NO_RSP,
    PING_SENT,
    AWAIT_RESPONSE,
    RSP_RECEIVED
}

enum class CrossDevicePackets(val id: Int) {

    XDEVMSG_PLANT_MON_CONNECT_STS_RSP(5),
    XDEVMSG_START(20),
    XDEVMSG_WIFI_SSID(XDEVMSG_START.id),
    XDEVMSG_WIFI_PSWD(21),
    XDEVMSG_CONNECT_NETWORK(22),
    XDEVMSG_CONNECT_STATUS(23),
    XDEVMSG_TEMP_DATA_REQ(24),
    XDEVMSG_LIVE_TEMP_DATA(25),
    XDEVMSG_MAX_TEMP_DATA(26),
    XDEVMSG_MIN_TEMP_DATA(27),
    XDEVMSG_HUM_DATA_REQ(28),
    XDEVMSG_LIVE_HUM_DATA(29),
    XDEVMSG_MAX_HUM_DATA(30),
    XDEVMSG_MIN_HUM_DATA(31),
    XDEVMSG_LUX_DATA_REQ(32),
    XDEVMSG_LIVE_LUX_DATA(33),
    XDEVMSG_MAX_LUX_DATA(34),
    XDEVMSG_MIN_LUX_DATA(35),
    XDEVMSG_SOILM1_DATA_REQ(36),
    XDEVMSG_LIVE_SOILM1_DATA(37),
    XDEVMSG_MAX_SOILM1_DATA(38),
    XDEVMSG_MIN_SOILM1_DATA(39),
    XDEVMSG_SOILM2_DATA_REQ(40),
    XDEVMSG_LIVE_SOILM2_DATA(41),
    XDEVMSG_MAX_SOILM2_DATA(42),
    XDEVMSG_MIN_SOILM2_DATA(43),
    XDEVMSG_TEMP_THRSH_DAT_REQ(44),
    XDEVMSG_TEMP_THRSH_DAT(45),
    XDEVMSG_MULTI_PKT_REQUEST(46),
    XDEVMSG_MULTI_PKT_REQUEST_REPLY(47);

    companion object {
        private val map = CrossDevicePackets.entries.associateBy { it.id }
        fun fromId(id: Int): CrossDevicePackets? = map[id]
    }
}

object SocketManager: ViewModel()
{
    lateinit var socket: Socket
    var connectionSts by mutableStateOf(DeviceConnectionSts.DISCONNECTED)
    var devicePingSts by mutableStateOf(ConnectionAliveSts.NO_RSP)
    var isActive = false
    val packetChannel = Channel<MutableList<Byte>>(capacity = Channel.UNLIMITED)
    val txPacketCh = Channel<MutableList<Byte>>(capacity = Channel.UNLIMITED)
    val dashboardCh = Channel<MutableList<Byte>>(capacity = Channel.UNLIMITED)
    val dashboardPktFlow = dashboardCh.receiveAsFlow()

    fun ConnectToDevice(devInfo: NsdServiceInfo): DeviceConnectionSts
    {
        connectionSts = DeviceConnectionSts.CONNECTING
        CoroutineScope(Dispatchers.IO).launch()
        {
            if(Connect(devInfo.hostAddresses.first().toString(), devInfo.port))
            {
                connectionSts = DeviceConnectionSts.CONNECTED
                startReading()
                startOutStream()
                isConnectionAlive()
            }
            else
            {
                connectionSts = DeviceConnectionSts.DISCONNECTED
            }
        }

        return connectionSts
    }

    suspend fun Connect(ip: String, port: Int): Boolean
    {
        return withContext(Dispatchers.IO)
        {
            try {
                // Connect to the server
                socket = Socket(ip.removePrefix("/"), port)
                isActive = true
                true
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    fun Disconnect() = CoroutineScope(Dispatchers.IO).launch()
    {
        if(isActive)
        {
            socket.close()
            connectionSts = DeviceConnectionSts.DISCONNECTED
            devicePingSts = ConnectionAliveSts.NO_RSP
            isActive = false
        }
    }

    fun startReading() = CoroutineScope(Dispatchers.IO).launch {
        val reader = socket.getInputStream()
        var byte: Byte = 0
        val buffer = mutableListOf<Byte>()
        var packetReadState = xDevCommPacketReadState.WAIT_FOR_SOP

        decodePacket()

        try {
            while (isActive) {
              when (packetReadState)
              {
                  xDevCommPacketReadState.WAIT_FOR_SOP ->
                  {
                      while((reader.available() > 0) && packetReadState == xDevCommPacketReadState.WAIT_FOR_SOP)
                      {
                          byte = reader.read().toByte()
                          if(byte == SOP)
                          {
                              buffer.add(byte)
                              packetReadState = xDevCommPacketReadState.WAIT_EOP
                          }
                      }
                  }

                  xDevCommPacketReadState.WAIT_EOP ->
                  {
                      while(reader.available() > 0 && packetReadState == xDevCommPacketReadState.WAIT_EOP)
                      {
                          byte = reader.read().toByte()
                          buffer.add(byte)
                          if(byte == EOP)
                          {
                              packetChannel.send(buffer.toMutableList())
                              buffer.clear()
                              packetReadState = xDevCommPacketReadState.WAIT_FOR_SOP
                          }
                      }
                  }

                  xDevCommPacketReadState.READ_HEADER -> {}
                  xDevCommPacketReadState.READ_PAYLOAD -> {}
                  xDevCommPacketReadState.VALIDATE -> {}
              }

            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun decodePacket()
    {
        CoroutineScope(Dispatchers.IO).launch {
            for (packet in packetChannel) {
                for(i in 0 until  packet.size)
                {
                    // send to appropriate part of system for the packet to be utilised
                    println("Buf Bytes: ${packet[i]}")
                }
                handlePacket(packet)
            }
        }
    }

    suspend fun handlePacket(buffer: MutableList<Byte>)
    {
        val byteBuf = buffer.toByteArray()
        var bufIdx = buffer.size
        // remove SOP and EOP and update size index
        RemSopEop(byteBuf)
        bufIdx = bufIdx - 2
        // unstuff packet and assign unstuffed packet length to bufIdx
        bufIdx = unstuffPacket(byteBuf, bufIdx)

        // after unstuffing bufIdx (size of packet indicator) also carries error coding so check must be made
        if(bufIdx >= 0)
        {
            if(calcChecksum(byteBuf, bufIdx) == 0.toByte())
            {

                when(CrossDevicePackets.fromId(byteBuf[0].toInt()))
                {
                    CrossDevicePackets.XDEVMSG_PLANT_MON_CONNECT_STS_RSP ->
                    {
                        devicePingSts = ConnectionAliveSts.RSP_RECEIVED
                    }

                    CrossDevicePackets.XDEVMSG_START -> TODO()
                    CrossDevicePackets.XDEVMSG_WIFI_SSID -> TODO()
                    CrossDevicePackets.XDEVMSG_WIFI_PSWD -> TODO()
                    CrossDevicePackets.XDEVMSG_CONNECT_NETWORK -> TODO()
                    CrossDevicePackets.XDEVMSG_CONNECT_STATUS -> TODO()
                    CrossDevicePackets.XDEVMSG_TEMP_DATA_REQ -> TODO()
                    CrossDevicePackets.XDEVMSG_LIVE_TEMP_DATA,
                    CrossDevicePackets.XDEVMSG_MAX_TEMP_DATA,
                    CrossDevicePackets.XDEVMSG_MIN_TEMP_DATA -> {
                        dashboardCh.send(byteBuf.toMutableList())
                    }
                    CrossDevicePackets.XDEVMSG_HUM_DATA_REQ -> TODO()
                    CrossDevicePackets.XDEVMSG_LIVE_HUM_DATA,
                    CrossDevicePackets.XDEVMSG_MAX_HUM_DATA,
                    CrossDevicePackets.XDEVMSG_MIN_HUM_DATA -> {
                        dashboardCh.send(byteBuf.toMutableList())
                    }
                    CrossDevicePackets.XDEVMSG_LUX_DATA_REQ -> TODO()
                    CrossDevicePackets.XDEVMSG_LIVE_LUX_DATA -> TODO()
                    CrossDevicePackets.XDEVMSG_MAX_LUX_DATA -> TODO()
                    CrossDevicePackets.XDEVMSG_MIN_LUX_DATA -> TODO()
                    CrossDevicePackets.XDEVMSG_SOILM1_DATA_REQ -> TODO()
                    CrossDevicePackets.XDEVMSG_LIVE_SOILM1_DATA,
                    CrossDevicePackets.XDEVMSG_MAX_SOILM1_DATA,
                    CrossDevicePackets.XDEVMSG_MIN_SOILM1_DATA -> {
                        dashboardCh.send(byteBuf.toMutableList())
                    }
                    CrossDevicePackets.XDEVMSG_SOILM2_DATA_REQ -> TODO()
                    CrossDevicePackets.XDEVMSG_LIVE_SOILM2_DATA,
                    CrossDevicePackets.XDEVMSG_MAX_SOILM2_DATA,
                    CrossDevicePackets.XDEVMSG_MIN_SOILM2_DATA -> {
                        dashboardCh.send(byteBuf.toMutableList())
                    }
                    CrossDevicePackets.XDEVMSG_TEMP_THRSH_DAT_REQ -> TODO()
                    CrossDevicePackets.XDEVMSG_TEMP_THRSH_DAT -> TODO()
                    CrossDevicePackets.XDEVMSG_MULTI_PKT_REQUEST -> TODO()
                    CrossDevicePackets.XDEVMSG_MULTI_PKT_REQUEST_REPLY -> TODO()
                    null -> {}
                }
            }
        }
    }

    fun startOutStream() = CoroutineScope(Dispatchers.IO).launch()
    {
        val writer = socket.getOutputStream()

        try {
            while (isActive) {
                for (packet in txPacketCh) {
                    writer.write(packet.toByteArray())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun checkConnectionSts(): Boolean {

        devicePingSts = ConnectionAliveSts.AWAIT_RESPONSE
        // construct packet and send it to output stream
        txPacketCh.send(PktConnectSts().toMutableList())
        // wait response flag to change
        return withTimeoutOrNull(10_000) { // 10 seconds

            while (true) {
                if (devicePingSts == ConnectionAliveSts.RSP_RECEIVED) {
                    return@withTimeoutOrNull true
                }
                delay(10) // important: allows coroutine to be cancelled + timeout to work
            }
            // Not reachable
            false
        } ?: false
    }

    fun isConnectionAlive() = CoroutineScope(Dispatchers.IO).launch()
    {
        var retryCnt = 0

        while(isActive)
        {
            if(checkConnectionSts())
            {
                println("Ping Received")
                // send another request 5 seconds from now
                delay(5000)
            }
            // device did not reply
            else
            {
                if(retryCnt < 3)
                {
                    retryCnt++
                    connectionSts = DeviceConnectionSts.CONNECTING
                    checkConnectionSts()
                }
                else
                {
                    Disconnect()
                }
            }
        }
    }
}