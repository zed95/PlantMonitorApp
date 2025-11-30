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

object SocketManager: ViewModel()
{
    lateinit var socket: Socket
    var connectionSts by mutableStateOf(DeviceConnectionSts.DISCONNECTED)
    var devicePingSts by mutableStateOf(ConnectionAliveSts.NO_RSP)
    var isActive = false
    val packetChannel = Channel<MutableList<Byte>>(capacity = Channel.UNLIMITED)
    val txPacketCh = Channel<MutableList<Byte>>(capacity = Channel.UNLIMITED)

    fun ConnectToDevice(devInfo: NsdServiceInfo)
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

    fun handlePacket(buffer: MutableList<Byte>)
    {
        var byteBuf = buffer.toByteArray()
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
                when(byteBuf[0])
                {
                    RSP_CONNECT_STS ->
                    {
                        devicePingSts = ConnectionAliveSts.RSP_RECEIVED
                    }
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
                if(retryCnt <= 3)
                {
                    retryCnt++
                    connectionSts = DeviceConnectionSts.CONNECTING
                    checkConnectionSts()
                }
                else
                {
                    isActive = false
                    socket.close()
                    connectionSts = DeviceConnectionSts.DISCONNECTED
                }
            }
        }
    }
}