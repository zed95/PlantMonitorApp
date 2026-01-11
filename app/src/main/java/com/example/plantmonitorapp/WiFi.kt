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
    XDEVMSG_MULTI_PKT_REQUEST_REPLY(47),

    XDEVMSG_MAX_T_ACT_IMP_TH(48),
    XDEVMSG_MAX_T_ACT_TRIG_TH(49),
    XDEVMSG_MIN_T_ACT_IMP_TH(50),
    XDEVMSG_MIN_T_ACT_TRIG_TH(51),

    XDEVMSG_MAX_H_ACT_IMP_TH(52),
    XDEVMSG_MAX_H_ACT_TRIG_TH(53),
    XDEVMSG_MIN_H_ACT_IMP_TH(54),
    XDEVMSG_MIN_H_ACT_TRIG_TH(55),

    XDEVMSG_MAX_SM1_ACT_IMP_TH(56),
    XDEVMSG_MAX_SM1_ACT_TRIG_TH(57),
    XDEVMSG_MIN_SM1_ACT_IMP_TH(58),
    XDEVMSG_MIN_SM1_ACT_TRIG_TH (59);

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
                // determine message type
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

                    CrossDevicePackets.XDEVMSG_MAX_T_ACT_IMP_TH,
                    CrossDevicePackets.XDEVMSG_MAX_T_ACT_TRIG_TH,
                    CrossDevicePackets.XDEVMSG_MIN_T_ACT_IMP_TH,
                    CrossDevicePackets.XDEVMSG_MIN_T_ACT_TRIG_TH,
                    CrossDevicePackets.XDEVMSG_MAX_H_ACT_IMP_TH,
                    CrossDevicePackets.XDEVMSG_MAX_H_ACT_TRIG_TH,
                    CrossDevicePackets.XDEVMSG_MIN_H_ACT_IMP_TH,
                    CrossDevicePackets.XDEVMSG_MIN_H_ACT_TRIG_TH,
                    CrossDevicePackets.XDEVMSG_MAX_SM1_ACT_IMP_TH,
                    CrossDevicePackets.XDEVMSG_MAX_SM1_ACT_TRIG_TH,
                    CrossDevicePackets.XDEVMSG_MIN_SM1_ACT_IMP_TH ,
                    CrossDevicePackets.XDEVMSG_MIN_SM1_ACT_TRIG_TH -> {
                        dashboardCh.send(byteBuf.toMutableList())
                    }
                    null -> {}
                }
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