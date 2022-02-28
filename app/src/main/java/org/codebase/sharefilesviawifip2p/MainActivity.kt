package org.codebase.sharefilesviawifip2p

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*
import org.codebase.sharefilesviawifip2p.broadcastreceiver.WIFIDirectBroadCastReceiver
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
@SuppressLint("StaticFieldLeak")
lateinit var messageText: TextView

class MainActivity : AppCompatActivity() {

    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiChannel : WifiP2pManager.Channel? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private var intentFilter: IntentFilter? = null

    private lateinit var socket: Socket
    private val peersList: ArrayList<WifiP2pDevice> = ArrayList()
    private lateinit var deviceNameArray: Array<String>
    private lateinit var deviceArray: Array<WifiP2pDevice>

    private lateinit var clientClass: ClientClass
    private lateinit var serverConnection: ServerConnection

    var isHost = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        messageText = findViewById(R.id.receivedMessageId)
        checkPermissions()

        initialWork()

        wifiOnOffButtonId.setOnClickListener {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            wifiLauncher.launch(intent)
        }

        deviceDiscoverButtonId.setOnClickListener {
            discoverPeers()
        }

        devicesListViewId.setOnItemClickListener { adapterView, view, i, l ->
            val device: WifiP2pDevice = deviceArray[i]
            val wifiP2pConfig = WifiP2pConfig()
            wifiP2pConfig.deviceAddress = device.deviceAddress
            wifiChannel.also { channel ->
                wifiP2pManager?.connect(channel, wifiP2pConfig, object :WifiP2pManager.ActionListener{
                    override fun onSuccess() {
                        connectionStatusId.text = "${device.deviceAddress} is connected"
                    }

                    override fun onFailure(reason: Int) {
                        connectionStatusId.text = "Connection Failed $reason"
                    }
                })

            }
        }

        sendMessageButtonId.setOnClickListener {
            val executorService: ExecutorService = Executors.newSingleThreadExecutor()
            val message = messageEditTextId.text.toString()

            executorService.execute(Runnable {
                if (message.isNotEmpty() && isHost) {
                    serverConnection.writeMessage(message.encodeToByteArray())
                } else if (message.isNotEmpty() && !isHost) {
                    clientClass.writeMessage(message.encodeToByteArray())
                } else {
                    messageEditTextId.error = "Enter Message First"
                }
            })

        }
    }

    private fun checkPermissions(){
        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

                    ActivityCompat.requestPermissions(this,
            arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION), 1)
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
            deviceArray = arrayOf(wifiP2pDeviceList.deviceList as WifiP2pDevice)
            Log.e("Device", deviceNameArray.toString())
            Log.e("Device", deviceArray.size.toString())
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

    val connectionInfoListener = WifiP2pManager.ConnectionInfoListener { wifiP2pInfo ->
        val groupOwnerAddress: InetAddress = wifiP2pInfo.groupOwnerAddress

        if (wifiP2pInfo.groupFormed && wifiP2pInfo.isGroupOwner) {
            connectionStatusId.text = "Host"
            isHost = true
            serverConnection = ServerConnection()
            serverConnection.start()
        } else if (wifiP2pInfo.groupFormed) {
            connectionStatusId.text = "Client"
            isHost = false

            clientClass = ClientClass(groupOwnerAddress)
            clientClass.start()
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

    //Server Connection Class
    class ServerConnection() : Thread() {
        var serverSocket : ServerSocket? = null
        var socket: Socket? = null
        var inputStream: InputStream? = null
        var outPutStream: OutputStream? = null

        fun writeMessage(byteArray: ByteArray) {
            try {
                outPutStream!!.write(byteArray)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        override fun run() {
            try {
                serverSocket = ServerSocket(8888)
                socket = serverSocket!!.accept()
                inputStream = socket!!.getInputStream()
                outPutStream = socket!!.getOutputStream()

            } catch (e: IOException) {
                e.printStackTrace()
            }

            val executorService : ExecutorService = Executors.newSingleThreadExecutor()
            val handler = Handler(Looper.getMainLooper())

            executorService.execute(Runnable {
                val buffer = ByteArray(1024)
                var bytes : Int

                var isSocket = true

                while (isSocket) {
                    try {
                        bytes = inputStream?.read(buffer) ?: 0
                        if (bytes > 0) {
                            val finalBytes: Int = bytes
                            handler.post(Runnable {
                                val tempMessage = String(buffer, 0, finalBytes)
                                messageText.text = tempMessage
                            })
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }
                }
            })
        }
    }

    //Create the client Class
    class ClientClass(hostAddress: InetAddress) : Thread() {
        var hostAdd: String = hostAddress.hostAddress!!

        private var inputStream: InputStream? = null
        private var outPutStream: OutputStream? = null

        val socket = Socket()

        fun writeMessage(byteArray: ByteArray) {
            try {
                outPutStream!!.write(byteArray)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        override fun run() {

            try {
                socket.connect(InetSocketAddress(hostAdd, 8888), 500)
                inputStream = socket.getInputStream()
                outPutStream = socket.getOutputStream()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val executor: ExecutorService = Executors.newSingleThreadExecutor()

            val handler = Handler(Looper.getMainLooper())

            executor.execute(Runnable {
                val buffer = ByteArray(1024)

                var bytesRead: Int

                var isSocket = true
                while (isSocket) {
                    try {
                        bytesRead = inputStream?.read(buffer) ?: 0
                        if (bytesRead > 0) {
                            val finalBytes = bytesRead
                            handler.post {
                                val tempMessage = String(buffer, 0, finalBytes)
                                messageText.text = tempMessage
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        isSocket = false
                    }
                }
            })
        }
    }

}