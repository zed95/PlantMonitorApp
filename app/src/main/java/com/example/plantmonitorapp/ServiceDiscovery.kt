package com.example.plantmonitorapp

import android.app.Application
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor
import java.util.concurrent.Executors

const val mServiceName = "myEsp32"

lateinit var nsdManager: NsdManager

// Instantiate a new DiscoveryListener
val discoveryListener = object : NsdManager.DiscoveryListener {

    // Called as soon as service discovery begins.
    override fun onDiscoveryStarted(regType: String) {
        Log.d(TAG, "Service discovery started")
    }

    override fun onServiceFound(service: NsdServiceInfo) {
        val executor = Executors.newSingleThreadExecutor()
        // A service was found! Do something with it.
        Log.d(TAG, "Service discovery success$service")
        when {
            service.serviceType != "_plantMonitor._tcp" -> // Service type is the string containing the protocol and
                // transport layer for this service.
                Log.d(TAG, "Unknown Service Type: ${service.serviceType}")
            service.serviceName == mServiceName -> // The name of the service tells the user what they'd be
                // connecting to. It could be "Bob's Chat App".
                Log.d(TAG, "Same machine: $mServiceName")
            service.serviceName.contains("myEsp32") -> nsdManager.registerServiceInfoCallback(service, executor, nsdServiceInfoCallback)
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
        TODO("Not yet implemented")
    }
}