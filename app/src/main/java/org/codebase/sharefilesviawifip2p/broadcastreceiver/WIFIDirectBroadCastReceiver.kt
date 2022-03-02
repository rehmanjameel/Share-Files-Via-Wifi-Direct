package org.codebase.sharefilesviawifip2p.broadcastreceiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.NetworkInfo
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.android.synthetic.main.activity_main.*
import org.codebase.sharefilesviawifip2p.MainActivity

class WIFIDirectBroadCastReceiver(private val manager: WifiP2pManager,
                                  private val channel: WifiP2pManager.Channel,
                                  private val activity: MainActivity) : BroadcastReceiver() {

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    override fun onReceive(context: Context?, intent: Intent?) {
        val action: String = intent?.action!!

        when(action) {
            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                // Check to see if Wi-Fi is enabled and notify appropriate activity
            }
            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                // Call WifiP2pManager.requestPeers() to get a list of current peers
                manager.requestPeers(channel, activity.peerListListener)
            }
            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                // Respond to new connection or disconnections
                val networkInfo: NetworkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO)!!
                if (networkInfo.isConnected) {
                    manager.requestConnectionInfo(channel, activity.connectionInfoListener)
                } else {
                    activity.connectionStatusId.text = "Not connected"
                }
            }
            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Respond to this device's wifi state changing
            }

        }
    }
}