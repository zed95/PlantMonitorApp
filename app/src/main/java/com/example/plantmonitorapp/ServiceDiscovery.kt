package com.example.plantmonitorapp

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import java.net.InetAddress
import java.util.concurrent.Executor
import java.util.concurrent.Executors

const val mServiceName = "myEsp32"


class ServiceViewModel(application: Application) : AndroidViewModel(application) {
    val serviceType = "_plantMonitor._tcp."
    private val nsdManager = application.getSystemService(NsdManager::class.java)
    private val nsdExecutor = Executors.newSingleThreadExecutor()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    val discoveredDevices = mutableStateListOf<NsdServiceInfo>()
    private val callbacks = mutableListOf<NsdManager.ServiceInfoCallback>()

    // Selected device
    private lateinit var _selectedDevice: NsdServiceInfo
    lateinit var selectedDevice: NsdServiceInfo

    fun startDiscovery()
    {
        if (discoveryListener != null) {
            Log.w(TAG, "Discovery already running")
            return
        }

        val callback = createServiceInfoCallback()

        discoveryListener = object : NsdManager.DiscoveryListener {

            // Called as soon as service discovery begins.
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                // A service was found! Do something with it.
                Log.d(TAG, "Service discovery success$service")

                if(service.serviceType.contains(serviceType))
                {
                    nsdManager.registerServiceInfoCallback(service, nsdExecutor, callback)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(TAG, "service lost: $service")
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                nsdManager.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    private fun createServiceInfoCallback() = object: NsdManager.ServiceInfoCallback
    {
        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
            TODO("Not yet implemented")
        }

        override fun onServiceInfoCallbackUnregistered() {
            TODO("Not yet implemented")
        }

        override fun onServiceLost() {
            TODO("Not yet implemented")
        }

        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
            Log.e(TAG, "Resolve Succeeded. $serviceInfo")

            addDevice(serviceInfo)
        }
    }

    fun restartServiceDiscovery()
    {
        stopServiceDiscovery()
        startDiscovery()

        Handler(Looper.getMainLooper()).postDelayed({
            restartServiceDiscovery()
        }, 1500)
    }

    fun stopServiceDiscovery()
    {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch(_: Exception) {}
        }
        discoveryListener = null

        callbacks.forEach { cb ->
            try { nsdManager.unregisterServiceInfoCallback(cb) } catch(_: Exception) {}
        }
        callbacks.clear()
    }

    fun selectDevice(device: NsdServiceInfo) {
        _selectedDevice = device
        selectedDevice = _selectedDevice
    }

    // Optional: add device to the list
    fun addDevice(device: NsdServiceInfo) {
//        if (!discoveredDevices.contains(device)) {
//            discoveredDevices.add(device)
//        }

        if(discoveredDevices.none { it.hostAddresses.first() == device.hostAddresses.first() })
        {
            discoveredDevices.add(device)
        }
    }

    // Optional: clear devices
    fun clearDevices() {
        discoveredDevices.clear()
    }
}

