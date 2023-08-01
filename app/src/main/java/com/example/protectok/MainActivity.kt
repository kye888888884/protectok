package com.example.protectok

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.MutableLiveData
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.lang.reflect.Method
import java.nio.charset.Charset
import java.util.UUID
import kotlin.concurrent.thread

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

private var connected: MutableLiveData<Boolean> = MutableLiveData(false)
//private var connectError: MutableLiveData<Event> = MutableLiveData(Event(false))
private var putTxt: MutableLiveData<String> = MutableLiveData("")
private var bleSocket: BluetoothSocket? = null
private var thread: Thread? = null

private val MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")

class MainActivity : ComponentActivity() {
    lateinit var context_main: Context
    var bluetoothAdapter: BluetoothAdapter? = null

    private var devicesArr = ArrayList<BluetoothDevice>()
    private val REQUEST_ENABLE_BT = 1

    private lateinit var viewManager: RecyclerView.LayoutManager
    private lateinit var recyclerViewAdapter: RecyclerViewAdapter

    var text_data: TextView? = null

    private var btSocket: BluetoothSocket? = null
    private var btDevice: BluetoothDevice? = null

    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var workerThread: Thread? = null

    private var readBuffer: ByteArray? = null
    private var readBufferPosition: Int = 0

    private var onBT: Boolean = false

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
        val btn_scan = findViewById<Button>(R.id.btn_scan_bluetooth)
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
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null) {
                Toast.makeText(this, "블루투스를 지원하지 않는 기기입니다", Toast.LENGTH_SHORT).show()
            } else {
                if (!bluetoothAdapter!!.isEnabled) {
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

        btn_scan.setOnClickListener {
            getPairedDevices()
            recyclerViewAdapter.notifyDataSetChanged()
        }

    }

    private fun showMessage(mainActivity: MainActivity, s: String) {
        Toast.makeText(mainActivity, s, Toast.LENGTH_SHORT).show()
    }

    private fun setBluetoothFindButton() {
        val scanBtn: Button = findViewById(R.id.btn_scan_bluetooth)
        if (bluetoothAdapter != null) {
            scanBtn.isEnabled = bluetoothAdapter!!.isEnabled != false
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
        bluetoothAdapter?.let {
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

    private fun createBluetoothSocket(device: BluetoothDevice): BluetoothSocket {
        if (Build.VERSION.SDK_INT >= 10) {
            try {
                val m: Method = device.javaClass.getMethod(
                    "createRfcommSocketToServiceRecord",
                    UUID::class.java
                )
                return m.invoke(device, MY_UUID) as BluetoothSocket
            } catch (e: Exception) {
                Log.e("Bluetooth", "Could not create Insecure RFComm Connection", e)
            }
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
        }
        Log.d("Bluetooth", "Could not create Insecure RFComm Connection")
        return device.createRfcommSocketToServiceRecord(MY_UUID)
    }

    // 디바이스에 연결
    public fun connectDevice(deviceAddress: String) {
        bluetoothAdapter?.let { adapter ->
            showMessage(this, deviceAddress)
            // 기기 검색을 수행 중이라면 취소
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
            btDevice = null
            for (tempDevice: BluetoothDevice in devicesArr) {
                if (tempDevice.address == deviceAddress) {
                    btDevice = tempDevice
                    break
                }
            }

            // 디바이스가 없으면 종료
            if (btDevice == null) {
                showMessage(this, "디바이스를 찾을 수 없습니다.")
                return
            }

            Log.d("Bluetooth", "Tried connecting to " + btDevice.toString())

            try {
                // 소켓 생성
                btSocket = createBluetoothSocket(btDevice!!)
                btSocket!!.connect()
                Log.d("Bluetooth", btSocket.toString())

                // 데이터 송수신 스트림 획득
                outputStream = btSocket!!.outputStream
                inputStream = btSocket!!.inputStream
                receiveData()

                onBT = true
            } catch (e: Exception) { // 연결에 실패할 경우 호출됨
                showMessage(this, e.toString())
                Log.d("Bluetooth", e.toString())
                return
            }
        }
    }

    // 데이터 수신
    public fun receiveData() {
        val handler = Handler()

        // 데이터를 수신하기 위한 버퍼를 생성
        readBufferPosition = 0
        readBuffer = ByteArray(1024)

        // 데이터를 수신하기 위한 쓰레드 생성
        workerThread = thread(false) {
            Log.d("Thread", "Thread Created")
            while (!Thread.currentThread().isInterrupted) {
                try {
                    // 수신된 데이터가 있는지 확인
                    var byteAvailable: Int = inputStream!!.available()
                    // 데이터가 수신된 경우
                    if (byteAvailable > 0) {
                        var bytes: ByteArray = ByteArray(byteAvailable)
                        // 입력 스트림에서 바이트 단위로 읽어 옴
                        inputStream!!.read(bytes)
                        // 입력 스트림 바이트를 한 바이트씩 읽어 옴
                        for (i: Int in 1..byteAvailable) {
                            var tempByte: Byte = bytes[i - 1]
                            // 개행문자를 기준으로 받음(한줄)
                            if (tempByte == '\n'.code.toByte()) {
                                // readBuffer 배열을 encodedBytes로 복사
                                var encodedBytes: ByteArray = ByteArray(readBufferPosition)
                                System.arraycopy(
                                    readBuffer,
                                    0,
                                    encodedBytes,
                                    0,
                                    encodedBytes.size
                                )
                                // 인코딩 된 바이트 배열을 문자열로 변환
                                var text = String(encodedBytes, Charset.forName("UTF-8"))
                                readBufferPosition = 0
                                handler.post(Runnable {
                                    // 텍스트 뷰에 출력
                                    // text_data?.append(text + "\n")
                                    Log.d("Protectok", text)
                                })
                            } else {
                                readBuffer!![readBufferPosition++] = tempByte
                            }
                        }
                    }
                } catch (e: IOException) {
                    showMessage(this, e.toString())
                }
            }

            try {
                // 매 초마다 수신
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                showMessage(this, e.toString())
            }
        }
        workerThread!!.start()
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