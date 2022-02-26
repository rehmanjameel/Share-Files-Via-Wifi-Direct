package org.codebase.sharefilesviawifip2p

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import org.codebase.sharefilesviawifip2p.broadcastreceiver.WIFIDirectBroadCastReceiver


class MainActivity : AppCompatActivity() {

    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiChannel : WifiP2pManager.Channel? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private var intentFilter: IntentFilter? = null

    private val peersList: ArrayList<WifiP2pDevice> = ArrayList()
    private lateinit var deviceNameArray: Array<String>
    private lateinit var deviceArray: Array<WifiP2pDevice>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initialWork()

        wifiOnOffButtonId.setOnClickListener {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            wifiLauncher.launch(intent)
        }

        deviceDiscoverButtonId.setOnClickListener {
            discoverPeers()
        }
    }

    private fun discoverPeers() {
        wifiP2pManager?.discoverPeers(wifiChannel, object : WifiP2pManager.ActionListener{
            override fun onSuccess() {
                connectionStatusId.text = "Discovery Started"
            }

            override fun onFailure(reasonCode: Int) {
                connectionStatusId.text = "Discovery Not Started"
                Log.e("Failure Code", reasonCode.toString())
            }

        })
    }

    private fun initialWork() {
        wifiP2pManager = applicationContext.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
        wifiChannel = wifiP2pManager?.initialize(this, Looper.getMainLooper(), null)

        wifiChannel?.also { channel ->
            broadcastReceiver = WIFIDirectBroadCastReceiver(wifiP2pManager!!, channel, this)
        }

        intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }
    }

    val peerListListener: WifiP2pManager.PeerListListener = WifiP2pManager.PeerListListener {wifiP2pDeviceList ->
        if (!wifiP2pDeviceList.equals(peersList)) {
            peersList.clear()
            peersList.addAll(wifiP2pDeviceList.deviceList)

            deviceNameArray = arrayOf(arrayOf(wifiP2pDeviceList.deviceList.size).toString())
            Log.e("Device", deviceNameArray.toString())
            val index = 0

            for (device: WifiP2pDevice in wifiP2pDeviceList.deviceList) {
                deviceNameArray[index] = device.deviceName
                deviceArray[index] = device
            }

            val adapter: ArrayAdapter<String> = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1, deviceNameArray)
            devicesListViewId.adapter = adapter

            if (peersList.size == 0) {
                connectionStatusId.text = "No Device Found"
                return@PeerListListener
            }
        }
    }

    val wifiLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {launchResult ->
        if (launchResult.resultCode == Activity.RESULT_OK) {
            val intentData: Intent? = launchResult.data
            Log.e("Result", intentData.toString())
        }
    }

    /* register the broadcast receiver with the intent values to be matched */
    override fun onResume() {
        super.onResume()
        broadcastReceiver?.also { receiver ->
            registerReceiver(receiver, intentFilter)
        }
    }

    /* unregister the broadcast receiver */
    override fun onPause() {
        super.onPause()
        broadcastReceiver?.also { receiver ->
            unregisterReceiver(receiver)
        }
    }

}