// Opdracht 9 - Bluetooth app to connect to HC-05 and send commands to control traffic lights via an arduino device
// Written by: Nina Schrauwen
package com.example.opdracht_9
// Define imports
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

    // Define bluetooth, socket and output stream variables
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Get BluetoothAdapter using BluetoothManager
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Check bluetooth permissions and then connect to HC-05
        requestBluetoothPermissions()
        connectToHC05()

        // Find buttons by their IDs
        // In activity_main.xml this button is made for the purpose of sending the default mode signal
        val defaultModeButton: Button = findViewById(R.id.button_default_mode)
        // In activity_main.xml this button is made for the purpose of sending the stoplight mode signal
        val stoplightModeButton: Button = findViewById(R.id.button_stoplight_mode)
        // In activity_main.xml this button is made for the purpose of reconnecting to the hc-05 in case of disconnection
        val reconnect_hc05Button: Button = findViewById(R.id.reconnect_hc05)

        // Set button click listener for the default mode button
        defaultModeButton.setOnClickListener {
            // If there is a bluetooth connection established then send the default mode command along with a toast
            if(bluetoothSocket?.isConnected == true){
                println("Default Mode Button Clicked")
                sendCommand("DEFAULT")
                Toast.makeText(this, "Default Mode Command Sent", Toast.LENGTH_SHORT).show()
                // If there is no bluetooth connection established then make it known via a toast
            } else {
                Toast.makeText(this, "Bluetooth Device Not Connected", Toast.LENGTH_SHORT).show()
            }
        }

        // Set button click listener for the stoplight mode button
        stoplightModeButton.setOnClickListener {
            // If there is a bluetooth connection established then send the stoplight mode command along with a toast
            if(bluetoothSocket?.isConnected == true) {
                println("Stoplight Mode Button Clicked")
                sendCommand("STOPLIGHT")
                Toast.makeText(this, "Stoplight Mode Command Sent", Toast.LENGTH_SHORT).show()
                // If there is no bluetooth connection established then make it known via a toast
            } else {
                Toast.makeText(this, "Bluetooth Device Not Connected", Toast.LENGTH_SHORT).show()
            }
        }

        // Set button click listener to try to reconnect to HC-05
        reconnect_hc05Button.setOnClickListener {
            reconnectToHC05()
        }

    } // End of OnCreate function

    // Function to connect via bluetooth to the hc-05 device
    private fun connectToHC05() {
        // Check for bluetooth permissions, when it is not allowed show this via a toast
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Bluetooth Connect Permission Denied", Toast.LENGTH_LONG).show()
            return
        }

        // Check for paired devices and find the HC-05 device
        val pairedDevices: Set<BluetoothDevice> = bluetoothAdapter.bondedDevices
        val hc05Device = pairedDevices.find { it.name == "HC-05" }

        // If the HC-05 device is not paired then show this via a toast
        if (hc05Device == null) {
            Toast.makeText(this, "HC-05 not paired. Please pair your HC-05 module.", Toast.LENGTH_LONG).show()
            return
        }

        // Start a thread to try to connect to the HC-05 device
        Thread {
            try {
                //This is the standard UUID for Serial Port Profile (SPP) Bluetooth connections
                val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                // Create a BluetoothSocket to connect to the HC-05 device
                bluetoothSocket = hc05Device.createRfcommSocketToServiceRecord(uuid)

                // Cancel discovery to improve connection success
                bluetoothAdapter.cancelDiscovery()

                // Connect to the HC-05 device via the BluetoothSocket and assign the output stream
                bluetoothSocket?.connect()
                outputStream = bluetoothSocket?.outputStream

                // Show a toast when the connection is successful and start the keep alive function
                runOnUiThread {
                    Toast.makeText(this, "Connected to HC-05", Toast.LENGTH_SHORT).show()
                    Log.d("BluetoothConnection", "Connection successful")
                    startKeepAlive()
                }
                // If the connection fails log it and close the socket
            } catch (e: IOException) {
                runOnUiThread {
                    Toast.makeText(this, "Connection Failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
                Log.e("BluetoothConnection", "Connection Failed: ${e.message}", e)
                try {
                    bluetoothSocket?.close()
                } catch (closeException: IOException) {
                    Log.e("BluetoothConnection", "Could not close socket", closeException)
                }
            }
        }.start()
    }


    // Check the bluetooth permissions and request them if they are not granted
    // These permissions are needed to make a connection via the bluetooth on my phone with the hc-05 device
    private fun requestBluetoothPermissions() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT),
                1
            )
        }
    }

    // Function to send commands to the HC-05 device
    private fun sendCommand(command: String) {
        try {
            // If there is a bluetooth connection send the command and put a new line character after it
            if (bluetoothSocket?.isConnected == true) {
                outputStream?.write((command + "\n").toByteArray())
                // Make sure the output stream is clean after sending the command
                outputStream?.flush()
                Log.d("BluetoothConnection", "Sent command: $command")
                // If bluetooth is not connected log the command that couldn't be send and make a toast about the device being disconnected
            } else {
                Log.e("BluetoothConnection", "Cannot send command $command: Socket is disconnected")
                Toast.makeText(this, "Bluetooth Device Disconnected", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to Send Command $command: ${e.message}", Toast.LENGTH_LONG).show()
            Log.e("BluetoothConnection", "Failed to send command: ${e.message}", e)
            reconnectToHC05() // Attempt to reconnect to the device
        }
    }

    // Function to try and reconnect to the hc-05 device
    private fun reconnectToHC05() {
        Thread {
            try {
                bluetoothSocket?.close() // Close the old socket
                Thread.sleep(3000)       // 3-second delay before reconnecting
                // Make 3 attempts to reconnect to the device
                for (i in 1..3) {
                    try {
                        // Log which attempt it is on
                        Log.d("BluetoothConnection", "Reconnection attempt $i")
                        // Call the original connect hc-05 function to attempt to reconnect
                        connectToHC05()
                        // If the bluetooth is reconnected make a toast the device is reconnected
                        if (bluetoothSocket?.isConnected == true) {
                            runOnUiThread {
                                Toast.makeText(this, "Reconnected to HC-05", Toast.LENGTH_SHORT).show()
                            }
                            return@Thread
                        }
                        // Failed to establish a connection, log the attempt it is on and why it failed
                    } catch (e: IOException) {
                        Log.e("BluetoothConnection", "Reconnection attempt $i failed: ${e.message}")
                        Thread.sleep(3000) // Wait 3 seconds before retrying
                    }
                }
                // Show a toast regarding the failed attempt of 3 tries to reconnect to the device
                runOnUiThread {
                    Toast.makeText(this, "Failed to reconnect after 3 attempts", Toast.LENGTH_LONG).show()
                }
                // If the reconnection can't be tried log the reason why it failed
            } catch (e: IOException) {
                Log.e("BluetoothConnection", "Reconnection failed: ${e.message}")
            }
        }.start()
    }

    // Function to maintain a bluetooth connection by sending a "PING" command periodically
    private fun startKeepAlive() {
        // Create a handler tied to the main thread (UI thread) for scheduling repeated tasks
        val keepAliveHandler = android.os.Handler(Looper.getMainLooper())
        // Define a runnable task to send a "PING" command at regular intervals
        val keepAliveRunnable = object : Runnable {
            override fun run() {
                // Send a "PING" command as a simple signal to keep the device connection active
                // The "PING" command does not perform any specific action but ensures the connection is maintained
                sendCommand("PING")

                // Schedule the next execution of this task after a 10-second delay
                // The 10-second interval balances keeping the connection alive without interfering with other commands
                keepAliveHandler.postDelayed(this, 10000)
            }
        }
        // Start the keep-alive process by posting the initial runnable task
        keepAliveHandler.post(keepAliveRunnable)
    }

    // Called when the activity is destroyed, ensuring resources are cleaned up
    override fun onDestroy() {
        super.onDestroy()
        try {
            // Close the Bluetooth socket to release the connection
            bluetoothSocket?.close()
            // Log the exception if the socket fails to close properly
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

}