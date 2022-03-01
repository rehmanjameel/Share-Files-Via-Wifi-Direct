package org.codebase.sharefilesviawifip2p

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.*
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.android.synthetic.main.activity_main.*
import org.codebase.sharefilesviawifip2p.broadcastreceiver.WIFIDirectBroadCastReceiver
import org.codebase.sharefilesviawifip2p.filepickerhelper.FilePickerHelper
import java.io.File
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
lateinit var receivedMessageText: TextView
@SuppressLint("StaticFieldLeak")
lateinit var messageEditText: TextInputEditText
private var fileUri: String? = ""

@RequiresApi(Build.VERSION_CODES.JELLY_BEAN)

class MainActivity : AppCompatActivity() {

    val REQUEST_ID_MULTIPLE_PERMISSIONS = 1
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiChannel : WifiP2pManager.Channel? = null
    private var broadcastReceiver: BroadcastReceiver? = null
    private var intentFilter: IntentFilter? = null

//    private lateinit var socket: Socket
    private val peersList: ArrayList<WifiP2pDevice> = ArrayList()
    private lateinit var deviceNameArray: Array<String>
    lateinit var pickedFilePath: TextView
    private lateinit var deviceArray: Array<WifiP2pDevice>

    private lateinit var clientClass: ClientClass
    private lateinit var serverConnection: ServerConnection

    var isHost = false

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        receivedMessageText = findViewById(R.id.receivedMessageId)
        messageEditText = findViewById(R.id.messageEditTextId)
        pickedFilePath = findViewById(R.id.pickedFilePathTextId)

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
            messageEditText.setText("")
        }

        //FilePicker Button Click listener
        filePickerButtonId.setOnClickListener {
            filePicker()
        }
    }

    private fun checkPermissions() : Boolean {

        val accessFineLocationPermission: Int = ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_FINE_LOCATION)

        val accessCoarsePermission: Int = ActivityCompat.checkSelfPermission(this,
            Manifest.permission.ACCESS_COARSE_LOCATION)

        val externalStorageReadPermission: Int = ActivityCompat.checkSelfPermission(this,
            Manifest.permission.READ_EXTERNAL_STORAGE)

        val externalStorageWritePermission: Int = ActivityCompat.checkSelfPermission(this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE)

        val listPermissionsNeeded: ArrayList<String> = ArrayList()

        if (accessFineLocationPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (accessCoarsePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (externalStorageReadPermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (externalStorageWritePermission != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (listPermissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toTypedArray(),
                REQUEST_ID_MULTIPLE_PERMISSIONS)
            return false
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            REQUEST_ID_MULTIPLE_PERMISSIONS -> {
                val permissionMap : HashMap<String, Int> = HashMap()
                // Initialize the map with permissions
                permissionMap[Manifest.permission.ACCESS_FINE_LOCATION] = PackageManager.PERMISSION_GRANTED
                permissionMap[Manifest.permission.ACCESS_COARSE_LOCATION] = PackageManager.PERMISSION_GRANTED
                permissionMap[Manifest.permission.READ_EXTERNAL_STORAGE] = PackageManager.PERMISSION_GRANTED
                permissionMap[Manifest.permission.WRITE_EXTERNAL_STORAGE] = PackageManager.PERMISSION_GRANTED

                if (grantResults.isNotEmpty()) {
                    for (i in permissions.indices) {
                        permissionMap[permissions[i]] = grantResults[i]
                    }

                    if (permissionMap[Manifest.permission.ACCESS_FINE_LOCATION] == PackageManager.PERMISSION_GRANTED &&
                        permissionMap[Manifest.permission.ACCESS_COARSE_LOCATION] == PackageManager.PERMISSION_GRANTED &&
                        permissionMap[Manifest.permission.READ_EXTERNAL_STORAGE] == PackageManager.PERMISSION_GRANTED &&
                        permissionMap[Manifest.permission.WRITE_EXTERNAL_STORAGE] == PackageManager.PERMISSION_GRANTED) {

                        Log.d("Permissions", "All permissions granted")
                    } else {
                        Log.d("Permissions", "Some permissions not granted ask again")
                        //permission is denied (this is the first time, when "never ask again" is not checked) so ask again explaining the usage of permission
                        // shouldShowRequestPermissionRationale will return true
                        //show the dialog or snackbar saying its necessary and try again otherwise proceed with setup.

                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION) ||
                            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION) ||
                            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE) ||
                            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                            showDialogOK("Location and Storage Permission required for this app") { dialog, which ->
                                when (which) {
                                    DialogInterface.BUTTON_POSITIVE -> checkPermissions()
                                    DialogInterface.BUTTON_NEGATIVE -> {
                                        dialog.dismiss()
                                    }
                                }
                            }
                        } else {
                            //permission is denied (and never ask again is  checked)
                            //shouldShowRequestPermissionRationale will return false
                            Toast.makeText(this, "Go to settings and enable permissions",
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    //Dialog for permissions if permission not granted
    private fun showDialogOK(message: String, okListener: DialogInterface.OnClickListener) {
        MaterialAlertDialogBuilder(this)
            .setMessage(message)
            .setPositiveButton("OK", okListener)
            .setNegativeButton("Cancel", okListener)
            .create()
            .show()
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

            deviceArray = (arrayOfNulls<WifiP2pDevice>(wifiP2pDeviceList.deviceList.size)) as Array<WifiP2pDevice>

//            deviceArray = arrayOf<WifiP2pDevice>(wifiP2pDeviceList.deviceList.size as WifiP2pDevice)
            Log.e("Device", deviceNameArray.toString())
            Log.e("Device", deviceArray.size.toString())
            val index = 0

            for (device: WifiP2pDevice in wifiP2pDeviceList.deviceList) {
                deviceNameArray[index] = device.deviceName
                deviceArray[index] = device
            }

            val adapter: ArrayAdapter<String> = ArrayAdapter(applicationContext, android.R.layout.simple_list_item_1,
                deviceNameArray)
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
    class ServerConnection : Thread() {
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

                val isSocket = true

                while (isSocket) {
                    try {
                        bytes = inputStream?.read(buffer) ?: 0
                        if (bytes > 0) {
                            val finalBytes: Int = bytes
                            handler.post(Runnable {
                                val tempMessage = String(buffer, 0, finalBytes)
                                receivedMessageText.text = tempMessage
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
                val file = File(fileUri)
                val buffer = ByteArray(1024)

                var fileBytes : ByteArray
                var bytesRead: Int

                var isSocket = true
                while (isSocket) {
                    try {
                        fileBytes = ByteArray(file.length().toInt())
                        bytesRead = inputStream?.read(buffer) ?: 0
                        if (bytesRead > 0) {
                            val finalBytes = bytesRead
                            handler.post {
                                val tempMessage = String(buffer, 0, finalBytes)
                                receivedMessageText.text = tempMessage
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

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private fun filePicker() {
        val mimeTypes: Array<String> = arrayOf("image/*", "video/*", "application/pdf", "audio/*")

        //Check for read file permission
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            // Should we show an explanation?
            // No explanation needed, we can request the permission.
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        } else {
            // create the intent to open file picker, add the desired file types to our picker and select the option that
            // the files be openable on the phone
            val intent = Intent()
                .setType("*/*")
                .setAction(Intent.ACTION_GET_CONTENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)

            activityLauncher.launch(intent)
        }
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    private val activityLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult(),
    ActivityResultCallback { result ->
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                val intentData : Intent? = result.data!!

                if (intentData != null) {
                    val selectedFiles = intentData.data

                    // Helper method, refer to FilePickerHelper getPath() method
                    val selectedPath = FilePickerHelper.getPath(this, selectedFiles!!)
                    Log.e("Path", "$selectedPath")

                    pickedFilePathTextId.text = selectedPath
                    fileUri = selectedPath!!
                }
            }
            RESULT_CANCELED -> {
                Toast.makeText(this, "File choosing cancelled", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Toast.makeText(this, "Error with choosing this file", Toast.LENGTH_SHORT).show()
            }
        }
    })
}