// Opdracht 9 - Android app om via HC-05 Bluetooth commando’s te sturen naar de Arduino om zo het stoplicht te besturen
// Dit sluit aan op de arduino-stoplicht-ontvanger (Arduino)
// Geschreven door: Nina Schrauwen

package com.example.opdracht_9

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import android.Manifest
import android.os.Looper
import android.util.Log
import android.view.WindowManager

class MainActivity : ComponentActivity() {

    // Bluetooth variabelen
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Adapter ophalen via BluetoothManager
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Permissies checken en verbinden met HC-05
        requestBluetoothPermissions()
        connectToHC05()

        // Buttons koppelen aan hun IDs
        val defaultModeButton: Button = findViewById(R.id.button_default_mode)   // naar Default Mode
        val stoplightModeButton: Button = findViewById(R.id.button_stoplight_mode) // naar Stoplight Mode
        val reconnect_hc05Button: Button = findViewById(R.id.reconnect_hc05)    // opnieuw verbinden

        // Default Mode knop
        defaultModeButton.setOnClickListener {
            if (bluetoothSocket?.isConnected == true) {
                sendCommand("DEFAULT")
                Toast.makeText(this, "Default Mode commando verstuurd", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth niet verbonden", Toast.LENGTH_SHORT).show()
            }
        }

        // Stoplight Mode knop
        stoplightModeButton.setOnClickListener {
            if (bluetoothSocket?.isConnected == true) {
                sendCommand("STOPLIGHT")
                Toast.makeText(this, "Stoplight Mode commando verstuurd", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Bluetooth niet verbonden", Toast.LENGTH_SHORT).show()
            }
        }

        // Reconnect knop
        reconnect_hc05Button.setOnClickListener {
            reconnectToHC05()
        }

    }

    // Verbinden met HC-05
    private fun connectToHC05() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth toestemming geweigerd", Toast.LENGTH_LONG).show()
            return
        }

        // Kijken of HC-05 gekoppeld is
        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
        val hc05Device = pairedDevices.find { it.name == "HC-05" }

        if (hc05Device == null) {
            Toast.makeText(this, "HC-05 niet gekoppeld. Koppel het apparaat eerst.", Toast.LENGTH_LONG).show()
            return
        }

        // Verbinden in een aparte thread
        Thread {
            try {
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // standaard SPP UUID
                bluetoothSocket = hc05Device.createRfcommSocketToServiceRecord(uuid)

                bluetoothAdapter.cancelDiscovery()
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream

                runOnUiThread {
                    Toast.makeText(this, "Verbonden met HC-05", Toast.LENGTH_SHORT).show()
                    Log.d("BluetoothConnection", "Verbinding gelukt")
                    startKeepAlive()
                }
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Verbinding mislukt: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("BluetoothConnection", "Verbinding mislukt: ${e.message}", e)
                try { bluetoothSocket?.close() } catch (_: IOException) {}
            }
        }.start()
    }

    // Permissies aanvragen indien nodig
    private fun requestBluetoothPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1)
        }
    }

    // Commando sturen naar HC-05
    private fun sendCommand(command: String) {
        try {
            if (bluetoothSocket?.isConnected == true) {
				// De Arduino kan controleren m.b.v. het new line karakter dat dit het einde van het bericht is
                outputStream?.write((command + "\n").toByteArray())
                outputStream?.flush()
                Log.d("BluetoothConnection", "Commando gestuurd: $command")
            } else {
                Log.e("BluetoothConnection", "Kan niet sturen, socket is niet verbonden")
                Toast.makeText(this, "Bluetooth niet verbonden", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Kon commando $command niet sturen: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("BluetoothConnection", "Fout bij sturen: ${e.message}", e)
            reconnectToHC05()
        }
    }

    // Opnieuw verbinden met HC-05
    private fun reconnectToHC05() {
        Thread {
            try {
                bluetoothSocket?.close()
                Thread.sleep(3000)
                for (i in 1..3) {
                    try {
                        Log.d("BluetoothConnection", "Reconnect poging $i")
                        connectToHC05()
                        if (bluetoothSocket?.isConnected == true) {
                            runOnUiThread {
                                Toast.makeText(this, "Opnieuw verbonden met HC-05", Toast.LENGTH_SHORT).show()
                            }
                            return@Thread
                        }
                    } catch (e: IOException) {
                        Log.e("BluetoothConnection", "Reconnect poging $i mislukt: ${e.message}")
                        Thread.sleep(3000)
                    }
                }
                runOnUiThread {
                    Toast.makeText(this, "Reconnect mislukt na 3 pogingen", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                Log.e("BluetoothConnection", "Reconnect fout: ${e.message}")
            }
        }.start()
    }

    // Houd verbinding actief door elke 10 sec een PING te sturen
    private fun startKeepAlive() {
        val keepAliveHandler = android.os.Handler(Looper.getMainLooper())
        val keepAliveRunnable = object : Runnable {
            override fun run() {
                sendCommand("PING") // simpel signaal om verbinding levend te houden
                keepAliveHandler.postDelayed(this, 10000)
            }
        }
        keepAliveHandler.post(keepAliveRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            bluetoothSocket?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }
}
