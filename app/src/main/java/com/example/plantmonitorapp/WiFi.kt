package com.example.plantmonitorapp

import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
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



object SocketManager: ViewModel()
{
    lateinit var socket: Socket
    var connectionSts by mutableStateOf(DeviceConnectionSts.DISCONNECTED)
    var devicePingSts by mutableStateOf(ConnectionAliveSts.NO_RSP)
    var isActive = false  // indicates whether connection is active or not
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

    /***********************************************************************************************
     * Establishes a TCP connection to a remote server.
     *
     * This function switches to the [Dispatchers.IO] coroutine context to perform
     * a blocking socket connection. If the connection succeeds, the internal socket
     * reference is initialized and the connection is marked as active.
     *
     * @param ip The server IP address or host name. Any leading "/" will be removed
     *           before attempting to connect.
     * @param port The TCP port number on the server.
     * @return `true` if the connection was successfully established, `false` if an
     *         error occurred during connection.
     **********************************************************************************************/
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

    /***********************************************************************************************
     * Asynchronously disconnects from the remote server.
     *
     * This function launches a coroutine on [Dispatchers.IO] to close the active
     * socket connection. If a connection is currently active, the socket is closed
     * and all internal connection and ping states are reset to their disconnected
     * values.
     *
     * Calling this function when no connection is active has no effect.
     **********************************************************************************************/
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

    /***********************************************************************************************
     * Starts asynchronously reading and decoding incoming data from the socket.
     *
     * This function launches a coroutine on [Dispatchers.IO] that continuously reads
     * bytes from the socket input stream while the connection is active. Incoming
     * data is processed using a simple state machine to detect packet boundaries,
     * identified by start-of-packet (SOP) and end-of-packet (EOP) markers.
     *
     * When a complete packet is received, the collected bytes are sent to
     * [packetChannel] for further processing. The read buffer is cleared and the
     * reader resets to wait for the next packet.
     *
     * Any I/O exceptions encountered during reading are caught and logged. The
     * reading loop terminates automatically when the connection becomes inactive.
     **********************************************************************************************/
    fun startReading() = CoroutineScope(Dispatchers.IO).launch {
        val reader = socket.getInputStream()
        // stores byte extracted from input stream
        var byte: Byte = 0
        // stores bytes from input stream for processing
        val buffer = mutableListOf<Byte>()
        // stores packet read state
        var packetReadState = xDevCommPacketReadState.WAIT_FOR_SOP

        // also start coroutine to decode packets that will be reconstructed
        decodePacket()

        try {
            // connection active
            while (isActive) {
              when (packetReadState)
              {
                  xDevCommPacketReadState.WAIT_FOR_SOP ->
                  {
                      // loop until no available bytes or state hasn't changed
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

    /***********************************************************************************************
     * Starts asynchronously decoding incoming packets from the packet channel.
     *
     * This function launches a coroutine on [Dispatchers.IO] that continuously
     * receives packets from [packetChannel] and then forwarded to [handlePacket] for
     * higher-level processing.
     *
     * Packet decoding continues until the channel is closed or the coroutine is
     * cancelled.
     **********************************************************************************************/
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

    /***********************************************************************************************
     * Decodes and processes a received communication packet.
     *
     * This function takes a raw packet buffer, removes protocol framing bytes
     * (start-of-packet and end-of-packet), performs byte unstuffing, and validates
     * the packet using a checksum. Only packets that successfully pass all protocol
     * checks are processed further.
     *
     * Once validated, the packet type is determined from the message identifier and
     * routed to the appropriate subsystem. Status and telemetry packets are
     * forwarded to [dashboardCh], while connection-related packets update internal
     * connection state.
     *
     * Packets with invalid framing, unstuffing errors, or checksum failures are
     * silently discarded.
     *
     * @param buffer A mutable list of bytes representing a raw received packet,
     *               including SOP and EOP markers.
     **********************************************************************************************/
    suspend fun handlePacket(buffer: MutableList<Byte>)
    {
        val byteBuf = buffer.toByteArray()
        var bufSize = buffer.size
        // remove SOP and EOP and update size index
        RemSopEop(byteBuf)
        bufSize = bufSize - 2
        // unstuff packet and assign unstuffed packet length to bufSize
        bufSize = unstuffPacket(byteBuf, bufSize)

        // after unstuffing bufSize (size of packet indicator) also carries error coding so check must be made
        if(bufSize >= 0)
        {
            // checksum checks out
            if(calcChecksum(byteBuf, bufSize) == 0.toByte())
            {
                dashboardCh.send(byteBuf.toMutableList())


            }
        }
    }

    /***********************************************************************************************
     * Starts asynchronously transmitting outgoing packets to the socket.
     *
     * This function launches a coroutine on [Dispatchers.IO] that continuously
     * listens for packets on [txPacketCh] and writes them to the socket output
     * stream while the connection is active.
     *
     * Packet transmission stops automatically when the connection becomes
     * inactive or the coroutine is cancelled. Any I/O exceptions encountered
     * during writing are caught and logged.
     **********************************************************************************************/
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

    /***********************************************************************************************
     * Checks whether the remote device is responsive.
     *
     * This function sends a connection-status request packet and suspends while
     * waiting for a response from the device. The request is transmitted via
     * [txPacketCh], and the function monitors [devicePingSts] for a response flag
     * update.
     *
     * The wait operation is bounded by a timeout. If a valid response is received
     * within the timeout window, the function returns `true`; otherwise, it returns
     * `false`.
     *
     * @return `true` if the device responds within the timeout period, `false` if
     *         the request times out or no response is received.
     **********************************************************************************************/
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

    /***********************************************************************************************
     * Continuously monitors the connection health of the remote device.
     *
     * This function launches a coroutine on [Dispatchers.IO] that periodically
     * sends connection-status requests to verify that the device remains
     * responsive. Successful responses reset the retry cycle and schedule the
     * next health check after a fixed delay.
     *
     * If the device fails to respond, the function retries the check up to a
     * defined number of attempts. When the retry limit is exceeded, the
     * connection is considered lost and [Disconnect] is invoked.
     *
     * Monitoring continues while the connection is active or until the coroutine
     * is cancelled.
     **********************************************************************************************/
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