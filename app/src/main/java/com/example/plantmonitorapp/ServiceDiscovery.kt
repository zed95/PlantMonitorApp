package com.example.plantmonitorapp

import android.app.Application
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper
import android.service.controls.ControlsProviderService.TAG
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.AndroidViewModel
import java.util.concurrent.Executors

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

    /***********************************************************************************************
     * Starts network service discovery for the configured service type.
     *
     * If a discovery session is already running, this method logs a warning and
     * returns without starting a new discovery to prevent duplicate listeners.
     *
     * When discovery begins, a {@link NsdManager.DiscoveryListener} is registered
     * to receive service discovery events. For each discovered service whose type
     * matches the expected service type, a service info callback is registered to
     * resolve additional service details.
     *
     * Discovery lifecycle events and errors are logged for diagnostic purposes.
     *
     * This method must be paired with {@link #stopServiceDiscovery()} to properly
     * release system resources.
     **********************************************************************************************/
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
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    /***********************************************************************************************
     * Creates a {@link NsdManager.ServiceInfoCallback} used to receive updates
     * for resolved network service information.
     *
     * The returned callback handles service resolution events, including updates
     * to service details and loss notifications. When a service is successfully
     * resolved or updated, the service information is logged and added to the
     * list of discovered devices.
     *
     * Service loss events are currently ignored, as losing a service does not
     * impact the current discovery workflow.
     *
     * @return a new {@link NsdManager.ServiceInfoCallback} instance
     **********************************************************************************************/
    private fun createServiceInfoCallback() = object: NsdManager.ServiceInfoCallback
    {
        override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
            TODO("Not yet implemented")
        }

        override fun onServiceInfoCallbackUnregistered() {
            TODO("Not yet implemented")
        }

        override fun onServiceLost() {
            // on service lost tells android that the service is gone by either device disconnecting or something else
            // I can ignore it for now because losing the service does not affect me
        }

        override fun onServiceUpdated(serviceInfo: NsdServiceInfo) {
            Log.e(TAG, "Resolve Succeeded. $serviceInfo")

            addDevice(serviceInfo)
        }
    }

    /***********************************************************************************************
     * Restarts network service discovery and schedules periodic rediscovery.
     *
     * This method stops any currently running service discovery, immediately starts
     * a new discovery session, and then schedules itself to run again after a fixed
     * delay. This creates a repeating discovery cycle to keep the list of available
     * services up to date.
     *
     * This is done to ensure that the service advertising by the physical device remains active.
     * There were cases where the service disappeared for extended periods of time. What this
     * meant was that the user could not select the device to connect to because it didn't appear
     * on the device list despite being available. This is a service discovery quirt of Android
     * and it was found that periodically restarting the service discovery fixed this problem.
     *
     * The restart is posted on the main thread using a {@link Handler} tied to the
     * main {@link Looper}.
     *
     * ⚠️ This method will continue rescheduling itself indefinitely until service
     * discovery is explicitly stopped elsewhere.
     **********************************************************************************************/
    fun restartServiceDiscovery()
    {
        stopServiceDiscovery()
        startDiscovery()

        Handler(Looper.getMainLooper()).postDelayed({
            restartServiceDiscovery()
        }, 1500)
    }

    /***********************************************************************************************
     * Stops any ongoing network service discovery and cleans up related resources.
     *
     * If a service discovery listener is currently registered, this method attempts
     * to stop service discovery with the {@link NsdManager}. Any exceptions thrown
     * during this process are caught and ignored to ensure safe cleanup.
     *
     * After stopping discovery, the stored discovery listener reference is cleared.
     *
     * This method also unregisters all previously registered service info callbacks,
     * ignoring any exceptions that may occur during unregistration, and clears the
     * internal callback list to prevent memory leaks and stale references.
     *
     * This function is safe to call multiple times.
     **********************************************************************************************/
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

    /***********************************************************************************************
     * Selects the given network service device as the current active device.
     *
     * Updates the internal backing field as well as the publicly exposed
     * selected device reference to reflect the newly chosen {@link NsdServiceInfo}.
     *
     * The information from the selected device will be used for connection to the physical
     * device advertising the service.
     *
     * Calling this method replaces any previously selected device.
     *
     * @param device the network service device to select
     **********************************************************************************************/
    fun selectDevice(device: NsdServiceInfo) {
        _selectedDevice = device
        selectedDevice = _selectedDevice
    }

    /***********************************************************************************************
     * Adds a newly discovered network service device to the discovered device list.
     *
     * The device is added only if no existing device in the list shares the same
     * first host address. This prevents duplicate entries for devices that resolve
     * to the same network endpoint.
     *
     * @param device the network service device to add
     **********************************************************************************************/
    fun addDevice(device: NsdServiceInfo) {
        if(discoveredDevices.none { it.hostAddresses.first() == device.hostAddresses.first() })
        {
            discoveredDevices.add(device)
        }
    }

    /***********************************************************************************************
     * Clears all currently discovered network service devices.
     *
     * Removes every entry from the internal discovered device list, resetting it
     * to an empty state. Any previously discovered devices will need to be
     * rediscovered before they can be used again.
     **********************************************************************************************/
    fun clearDevices() {
        discoveredDevices.clear()
    }
}

