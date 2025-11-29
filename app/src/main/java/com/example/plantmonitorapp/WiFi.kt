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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.PrintWriter
import java.net.Socket

enum class DeviceConnectionSts {
    DISCONNECTED,
    CONNECTING,
    CONNECTED
}

object SocketManager: ViewModel()
{
    lateinit var socket: Socket
    var connectionSts by mutableStateOf(DeviceConnectionSts.DISCONNECTED)
    var isActive = false
    val packetChannel = Channel<MutableList<Byte>>(capacity = Channel.UNLIMITED)

    fun ConnectToDevice(devInfo: NsdServiceInfo)
    {
        connectionSts = DeviceConnectionSts.CONNECTING
        CoroutineScope(Dispatchers.IO).launch()
        {
            if(Connect(devInfo.hostAddresses.first().toString(), devInfo.port))
            {
                connectionSts = DeviceConnectionSts.CONNECTED
                startReading()
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
                              packetChannel.send(buffer)
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
                handlePacket(packet)
            }
        }
    }

    fun handlePacket(buffer: MutableList<Byte>)
    {
        var byteBuf = ByteArray(512)
        byteBuf = buffer.toByteArray()
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
                for(i in 0 until  bufIdx)
                {
                    // send to appropriate part of system for the packet to be utilised
                    println("Buf Bytes: ${byteBuf[i]}")
                }

            }
        }
    }
}