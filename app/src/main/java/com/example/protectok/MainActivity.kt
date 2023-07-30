package com.example.protectok

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.metrics.Event
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.startActivityForResult
import androidx.lifecycle.MutableLiveData
import androidx.media3.common.util.Util
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
    public lateinit var context_main: Context;
    var mBluetoothAdapter: BluetoothAdapter? = null
    var mDevices: Set<BluetoothDevice>? = null

    private val bSocket: BluetoothSocket? = null
    private val mRemoteDevice: BluetoothDevice? = null

    var targetDevice: BluetoothDevice? = null
    var socket: BluetoothSocket? = null
    private var mOutputStream: OutputStream? = null
    private var mInputStream: InputStream? = null

    var onBT = false
    var sendByte = ByteArray(4)
    var tvBT: TextView? = null
//    var asyncDialog: ProgressDialog? = null

    private val REQUEST_ENABLE_BT = 1
//    val progressState = MutableLiveData<String>()

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
        val btn_find = findViewById<Button>(R.id.btn_find_bluetooth)

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
            }
        }

        btn_find.setOnClickListener {
            scanDevice()
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

    fun scanDevice(){
        //Progress State Text
//        progressState.postValue("device 스캔 중...")

        //리시버 등록
        registerBluetoothReceiver()

        //블루투스 기기 검색 시작
        val bluetoothAdapter = mBluetoothAdapter
        foundDevice = false
        if (ActivityCompat.checkSelfPermission(
                context_main,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        bluetoothAdapter?.startDiscovery()
    }


    fun registerBluetoothReceiver(){
        //intentfilter
        val stateFilter = IntentFilter()
        stateFilter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED) //BluetoothAdapter.ACTION_STATE_CHANGED : 블루투스 상태변화 액션
        stateFilter.addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
        stateFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED) //연결 확인
        stateFilter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED) //연결 끊김 확인
        stateFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        stateFilter.addAction(BluetoothDevice.ACTION_FOUND) //기기 검색됨
        stateFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED) //기기 검색 시작
        stateFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED) //기기 검색 종료
        stateFilter.addAction(BluetoothDevice.ACTION_PAIRING_REQUEST)
        mBluetoothStateReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent) {
                val action = intent.action //입력된 action
                if (action != null) {
                    Log.d("Bluetooth action", action)
                }
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                var name: String? = null
                if (device != null) {
                    if (ActivityCompat.checkSelfPermission(
                            context_main,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        return
                    }
                    name = device.name //broadcast를 보낸 기기의 이름을 가져온다.
                }
                when (action) {
                    BluetoothAdapter.ACTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(
                            BluetoothAdapter.EXTRA_STATE,
                            BluetoothAdapter.ERROR
                        )
                        when (state) {
                            BluetoothAdapter.STATE_OFF -> {
                            }
                            BluetoothAdapter.STATE_TURNING_OFF -> {
                            }
                            BluetoothAdapter.STATE_ON -> {
                            }
                            BluetoothAdapter.STATE_TURNING_ON -> {
                            }
                        }
                    }
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        // 디바이스 연결 성공할 경우
                        Toast.makeText(context_main, "디바이스 연결 성공", Toast.LENGTH_SHORT).show()
                    }
                    BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        //디바이스가 연결 해제될 경우
                        connected.postValue(false)
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    }
                    BluetoothDevice.ACTION_FOUND -> {
                        Toast.makeText(context_main, "블루투스를 탐색", Toast.LENGTH_SHORT).show()
                        if (!foundDevice) {
                            val device_name = device!!.name
                            val device_Address = device.address
                            //블루투스 기기 이름의 앞글자가 "RNM"으로 시작하는 기기만을 검색한다
                            if (device_name != null && device_name.length > 4) {
                                if (device_name.substring(0, 3) == "RNM") {
                                    targetDevice = device
                                    foundDevice = true
                                    //찾은 디바이스에 연결한다.
                                    connectToTargetedDevice(targetDevice)
                                }
                            }
                        }
                    }
                    BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                        if (!foundDevice) {
                            //Toast massage
                            Toast.makeText(context_main, "디바이스를 찾을 수 없습니다. 다시 시도해 주세요.", Toast.LENGTH_SHORT).show()
                            //Progress 해제
//                            inProgress.postValue(Event(false))
                        }
                    }

                }
            }
        }
        //리시버 등록
        context_main.applicationContext.registerReceiver(
            mBluetoothStateReceiver,
            stateFilter
        )

    }

    fun unregisterReceiver(){
        if(mBluetoothStateReceiver!=null) {
            context_main.applicationContext.unregisterReceiver(mBluetoothStateReceiver)
            mBluetoothStateReceiver = null
        }
    }

    private fun connectToTargetedDevice(targetedDevice: BluetoothDevice?) {
        //Progress state text
//        progressState.postValue("${targetDevice?.name}에 연결중..")

        val thread = Thread {
            //선택된 기기의 이름을 갖는 bluetooth device의 object
            //SPP_UUID
            val uuid = UUID.fromString("00001101-0000-1000-8000-0080551a34fb")
            try {
                // 소켓 생성
                if (ActivityCompat.checkSelfPermission(
                        this,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    return@Thread
                }
                socket = targetedDevice?.createRfcommSocketToServiceRecord(uuid)
                //Connect
                socket?.connect()

                /**
                 * After Connect Device
                 */
                //연결 상태
                connected.postValue(true)
                //output, input stream을 열어 송/수신
                mOutputStream = bleSocket?.outputStream
                mInputStream = bleSocket?.inputStream
                // 데이터 수신 시작
                beginListenForData()

            } catch (e: java.lang.Exception) {
                // 블루투스 연결 중 오류 발생
                e.printStackTrace()
//                connectError.postValue(Event(true))
                try {
                    socket?.close()
                }
                catch(e: IOException) {
                    e.printStackTrace()
                }
            }

            //연결 thread를 수행한다
            thread?.start()
        }
    }

    /**
     * 블루투스 데이터 송신
     * String sendTxt를 byte array로 바꾸어 전송할 수 있다.
     * val byteArr = sendTxt.toByteArray(Charset.defaultCharset())
     * sendByteData(byteArr)
     */
    fun sendByteData(data: ByteArray) {
        Thread {
            try {
                mOutputStream?.write(data) // 프로토콜 전송
            } catch (e: Exception) {
                // 문자열 전송 도중 오류가 발생한 경우.
                e.printStackTrace()
            }
        }.run()
    }

    /**
     * 블루투스 데이터 수신 Listener
     */
    fun beginListenForData() {
        val mWorkerThread = Thread {
            while (!Thread.currentThread().isInterrupted) {
                try {
                    val bytesAvailable = mInputStream?.available()
                    if (bytesAvailable != null) {
                        if (bytesAvailable > 0) { //데이터가 수신된 경우
                            val packetBytes = ByteArray(bytesAvailable)
                            mInputStream?.read(packetBytes)
                            /**
                             * 한 버퍼 처리
                             */
                            // Byte -> String
                            val s = String(packetBytes,Charsets.UTF_8)
                            //수신 String 출력
                            putTxt.postValue(s)

                            /**
                             * 한 바이트씩 처리
                             */
                            for (i in 0 until bytesAvailable) {
                                val b = packetBytes[i]
                                Log.d("inputData", String.format("%02x", b))
                            }
                        }
                    }
                } catch (e: UnsupportedEncodingException) {
                    e.printStackTrace()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
        //데이터 수신 thread 시작
        mWorkerThread.start()
    }
}