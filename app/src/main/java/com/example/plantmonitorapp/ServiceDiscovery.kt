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
import java.net.InetAddress
import java.util.concurrent.Executor
import java.util.concurrent.Executors

const val mServiceName = "myEsp32"
const val serviceType = "_plantMonitor._tcp."

lateinit var nsdManager: NsdManager

fun initNsdDiscoveryListener(context: Context, serviceViewModel: ServiceViewModel)
{
    val nsdServiceInfoCallback = object : NsdManager.ServiceInfoCallback
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

            serviceViewModel.addDevice(serviceInfo)
        }
    }

    val discoveryListener = object : NsdManager.DiscoveryListener {

        // Called as soon as service discovery begins.
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            val executor = Executors.newSingleThreadExecutor()
            // A service was found! Do something with it.
            Log.d(TAG, "Service discovery success$service")

            if(service.serviceType == serviceType)
            {
                nsdManager.registerServiceInfoCallback(service, executor, nsdServiceInfoCallback)
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

            // Retry later to avoid hammering the service
            Handler(Looper.getMainLooper()).postDelayed({
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, this)
            }, 1000)  // 1 second delay
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
            nsdManager.stopServiceDiscovery(this)
        }
    }

    nsdManager = context.getSystemService(NsdManager::class.java)
    nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

}

// Instantiate a new DiscoveryListener
