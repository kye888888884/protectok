package com.example.protectok

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.ContentValues.TAG
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.metrics.Event
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.annotation.RequiresApi
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.util.Util
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.protectok.ui.theme.ProtectokTheme
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.UnsupportedEncodingException
import java.util.UUID

const val REQUEST_ALL_PERMISSION = 1
val PERMISSIONS = arrayOf(
    android.Manifest.permission.BLUETOOTH,
    android.Manifest.permission.BLUETOOTH_ADMIN,
    android.Manifest.permission.BLUETOOTH_SCAN,
    android.Manifest.permission.BLUETOOTH_CONNECT,
    android.Manifest.permission.ACCESS_COARSE_LOCATION,
    android.Manifest.permission.ACCESS_FINE_LOCATION,
    android.Manifest.permission.READ_PHONE_STATE,
    android.Manifest.permission.CALL_PHONE,
)
var foundDevice:Boolean = false
private var mBluetoothStateReceiver: BroadcastReceiver? = null
var mBluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()



private var connected: MutableLiveData<Boolean> = MutableLiveData(false)
//private var connectError: MutableLiveData<Event> = MutableLiveData(Event(false))
private var putTxt: MutableLiveData<String> = MutableLiveData("")
private var bleSocket: BluetoothSocket? = null
private var thread: Thread? = null

class MainActivity : ComponentActivity() {
    lateinit var context_main: Context;
    var mBluetoothAdapter: BluetoothAdapter? = null

    private var devicesArr = ArrayList<BluetoothDevice>()
    private val REQUEST_ENABLE_BT = 1

    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var recyclerViewAdapter: RecyclerViewAdapter

    var text_data: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        context_main = this
        setContentView(R.layout.activity_main)

        if (!hasPermissions(this, PERMISSIONS)) {
            requestPermissions(PERMISSIONS, REQUEST_ALL_PERMISSION)
        }

        val text_tel = findViewById<TextView>(R.id.text_tel)
        val btn_tel = findViewById<Button>(R.id.btn_tel)
        val btn_active = findViewById<Button>(R.id.btn_active_bluetooth)
        val btn_connect = findViewById<Button>(R.id.btn_connect_bluetooth)
        text_data = findViewById(R.id.text_data)

        viewManager = LinearLayoutManager(this)
        recyclerViewAdapter = RecyclerViewAdapter(devicesArr, this)
        val recyclerView = findViewById<RecyclerView> (R.id.view_bluetooth_list).apply {
            layoutManager = viewManager
            adapter = recyclerViewAdapter
        }

        btn_tel.setOnClickListener {
            var intent = Intent(Intent.ACTION_DIAL)
            intent.data = Uri.parse("tel:${text_tel.text.toString()}")
            if(intent.resolveActivity(packageManager) != null){
                startActivity(intent)
            }
        }

        btn_active.setOnClickListener {
            mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (mBluetoothAdapter == null) {
                Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다", Toast.LENGTH_SHORT).show()
            } else {
                if (!mBluetoothAdapter!!.isEnabled) {
                    val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return@setOnClickListener
                    }
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } else {
                    Toast.makeText(this, "블루투스가 이미 활성화되어 있습니다", Toast.LENGTH_SHORT).show()
                }
                setBluetoothFindButton()
            }
        }

        btn_connect.setOnClickListener {
            getPairedDevices()
            recyclerViewAdapter.notifyDataSetChanged()
        }

    }

    private fun showMessage(mainActivity: MainActivity, s: String) {
        Toast.makeText(mainActivity, s, Toast.LENGTH_SHORT).show()
    }

    private fun setBluetoothFindButton() {
        val scanBtn: Button = findViewById(R.id.btn_scan_bluetooth)
        if (mBluetoothAdapter != null) {
            scanBtn.isEnabled = mBluetoothAdapter!!.isEnabled != false
        }
    }

    private fun hasPermissions(context: Context?, permissions: Array<String>): Boolean {
        for (permission in permissions) {
            if (context?.let { ActivityCompat.checkSelfPermission(it, permission) }
                != PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
        }
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_ALL_PERMISSION -> {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permissions granted!", Toast.LENGTH_SHORT).show()
                } else {
                    requestPermissions(permissions, REQUEST_ALL_PERMISSION)
                    Toast.makeText(this, "Permissions must be granted", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun getPairedDevices() {
        mBluetoothAdapter?.let {
            // 블루투스 활성화 상태라면
            if (it.isEnabled) {
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
                // ArrayAdapter clear
                devicesArr.clear()
                // 페어링된 기기 확인
                val pairedDevices: Set<BluetoothDevice> = it.bondedDevices
                // 페어링된 기기가 존재하는 경우
                if (pairedDevices.isNotEmpty()) {
                    pairedDevices.forEach { device ->
                        // ArrayAdapter에 아이템 추가
                        devicesArr.add(device)
                    }
                } else {
                    showMessage(this, "페어링된 기기가 없습니다.")
                }
            } else {
                showMessage(this, "블루투스가 비활성화 되어 있습니다.")
            }
        }
    }

    // 블루투스에서 서버의 역할을 수행하는 스레드
    class AcceptThread(private val bluetoothAdapter: BluetoothAdapter, context: Context): Thread() {
        private lateinit var serverSocket: BluetoothServerSocket

        companion object {
            private const val TAG = "ACCEPT_THREAD"
            private const val SOCKET_NAME = "server"
            private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        }

        init {
            try {
                // 서버 소켓
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {

                }
                serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord(SOCKET_NAME, MY_UUID)
            } catch(e: Exception) {
                Log.d(TAG, e.message.toString())
            }
        }

        override fun run() {
            var socket: BluetoothSocket? = null
            while(true) {
                try {
                    // 클라이언트 소켓
                    socket = serverSocket.accept()
                } catch (e: IOException) {
                    Log.d(TAG, e.message.toString())
                }

                socket?.let {
                    /* 클라이언트 소켓과 관련된 작업..... */

                    // 더 이상 연결을 수행하지 않는다면 서버 소켓 종료(그래도 연결된 소켓은 작동)
                    serverSocket.close()
                }
                break
            }
        }

        fun cancel() {
            try {
                serverSocket.close()
            } catch (e: IOException) {
                Log.d(TAG, e.message.toString())
            }
        }
    }

    // 디바이스에 연결
    public fun connectDevice(deviceAddress: String) {
        mBluetoothAdapter?.let { adapter ->
            // 기기 검색을 수행중이라면 취소
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            if (adapter.isDiscovering) {
                adapter.cancelDiscovery()
            }

            // 서버의 역할을 수행 할 Device 획득
            val device = adapter.getRemoteDevice(deviceAddress)
            // UUID 선언
            val uuid = UUID.fromString("00001101-0000-1000-8000-008052ba94fb")
            try {
                val thread = ConnectThread(uuid, device, this)
                thread.run()
                showMessage(this, "${device.name}과 연결되었습니다.")
            } catch (e: Exception) { // 연결에 실패할 경우 호출됨
                showMessage(this, e.toString())
//                showMessage(this, "기기의 전원이 꺼져 있습니다. 기기를 확인해주세요.")
                return
            }
        }
    }


}

@SuppressLint("MissingPermission")
class ConnectThread(
    private val myUUID: UUID,
    private val device: BluetoothDevice,
    private val context: MainActivity
) : Thread() {
    companion object {
        private const val TAG = "CONNECT_THREAD"
    }

    // BluetoothDevice 로부터 BluetoothSocket 획득
    private val connectSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
        device.createRfcommSocketToServiceRecord(myUUID)
    }

    override fun run() {
        try {
            // 연결 수행
            connectSocket?.connect()
            connectSocket?.let {
                val connectedThread = ConnectedThread(bluetoothSocket = it, context.text_data)
                connectedThread.start()
            }
        } catch (e: IOException) { // 기기와의 연결이 실패할 경우 호출
            Log.d(TAG, e.message.toString())
            connectSocket?.close()
            throw Exception("연결 실패")
        }
    }

    fun cancel() {
        try {
            connectSocket?.close()
        } catch (e: IOException) {
            Log.d(TAG, e.message.toString())
        }
    }
}

class RecyclerViewAdapter(private val myDataset: ArrayList<BluetoothDevice>, private val context: MainActivity):
    RecyclerView.Adapter<RecyclerViewAdapter.MyViewHolder > () {
    class MyViewHolder(val linearView: LinearLayout):RecyclerView.ViewHolder(linearView)
    override fun onCreateViewHolder(parent: ViewGroup,
                                    viewType: Int):RecyclerViewAdapter.MyViewHolder {
        // create a new view
        val linearView = LayoutInflater.from(parent.context)
            .inflate(R.layout.bluetoothview_item, parent, false) as LinearLayout
        return MyViewHolder(linearView)
    }

    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val itemName: TextView = holder.linearView.findViewById(R.id.item_name)
        val itemConnect: TextView = holder.linearView.findViewById(R.id.item_connect)
//            val itemAddress: TextView = holder.linearView.findViewById(R.id.item_address)
        val contextView = holder.linearView.context
        if (ActivityCompat.checkSelfPermission(
                contextView,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        itemName.text = myDataset[position].name
        itemConnect.setOnClickListener {
            context.connectDevice(myDataset[position].address)
        }
//            itemAddress.text = myDataset[position].address
    }
    override fun getItemCount() = myDataset.size
}

private class ConnectedThread(private val bluetoothSocket: BluetoothSocket,
                              private val text_data: TextView?
) : Thread() {
    private lateinit var inputStream: InputStream
    private lateinit var outputStream: OutputStream

    init {
        try {
            // BluetoothSocket의 InputStream, OutputStream 초기화
            inputStream = bluetoothSocket.inputStream
            outputStream = bluetoothSocket.outputStream
        } catch (e: IOException) {
            Log.d(TAG, e.message.toString())
        }
    }

    override fun run() {
        val buffer = ByteArray(1024)
        var bytes: Int
        Log.d(TAG, "연결 시도")

        while (true) {
            try {
                // 데이터 받기(읽기)
                bytes = inputStream.read(buffer)
                text_data?.text = buffer.toString()
//                Log.d(TAG, bytes.toString())
            } catch (e: Exception) { // 기기와의 연결이 끊기면 호출
                Log.d(TAG, "기기와의 연결이 끊겼습니다.")
                break
            }
        }
    }

    fun write(bytes: ByteArray) {
        try {
            // 데이터 전송
            outputStream.write(bytes)
        } catch (e: IOException) {
            Log.d(TAG, e.message.toString())
        }
    }

    fun cancel() {
        try {
            bluetoothSocket.close()
        } catch (e: IOException) {
            Log.d(TAG, e.message.toString())
        }
    }
}