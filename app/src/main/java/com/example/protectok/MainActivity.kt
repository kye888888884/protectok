package com.example.protectok

import android.Manifest
import android.app.ProgressDialog
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
import java.io.InputStream
import java.io.OutputStream

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

class MainActivity : ComponentActivity() {

    var mBluetoothAdapter: BluetoothAdapter? = null
    var mDevices: Set<BluetoothDevice>? = null
    private val bSocket: BluetoothSocket? = null
    private val mOutputStream: OutputStream? = null
    private val mInputStream: InputStream? = null
    private val mRemoteDevice: BluetoothDevice? = null
    var onBT = false
    var sendByte = ByteArray(4)
    var tvBT: TextView? = null
    var asyncDialog: ProgressDialog? = null
    private val REQUEST_ENABLE_BT = 1
    val progressState = MutableLiveData<String>()
//    val context : Context = MainActivity.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!hasPermissions(this, PERMISSIONS)) {
            requestPermissions(PERMISSIONS, REQUEST_ALL_PERMISSION)
        }

        val text_tel = findViewById<TextView>(R.id.text_tel)
        val btn_tel = findViewById<Button>(R.id.btn_tel)
        val btn_active = findViewById<Button>(R.id.btn_active_bluetooth)

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
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
                } else {
                    Toast.makeText(this, "블루투스가 이미 활성화되어 있습니다", Toast.LENGTH_SHORT).show()
                }
            }
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
        progressState.postValue("device 스캔 중...")

        //리시버 등록
        registerBluetoothReceiver()

        //블루투스 기기 검색 시작
        val bluetoothAdapter = mBluetoothAdapter
        foundDevice = false
        if (ActivityCompat.checkSelfPermission(
                this,
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
                            Util.showNotification("디바이스를 찾을 수 없습니다. 다시 시도해 주세요.")
                            //Progress 해제
                            inProgress.postValue(Event(false))
                        }
                    }

                }
            }
        }
        //리시버 등록
        context.applicationContext().registerReceiver(
            mBluetoothStateReceiver,
            stateFilter
        )

    }

    fun unregisterReceiver(){
        if(mBluetoothStateReceiver!=null) {
            context.applicationContext().unregisterReceiver(mBluetoothStateReceiver)
            mBluetoothStateReceiver = null
        }
    }
}