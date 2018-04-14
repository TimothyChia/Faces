package com.timothychia.faces;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

import java.io.IOException;
import java.util.UUID;

// based on https://developer.android.com/guide/topics/connectivity/bluetooth.html
// probably would have been better to make this in a different file and anonymously subclass
public class BluetoothConnectThread extends Thread {
    private final BluetoothSocket mmSocket;
    private final BluetoothDevice mmDevice;
    private UUID mUUID;
    private static final String TAG = "BT_Connect_T_Debug";


    public BluetoothConnectThread(BluetoothDevice device, UUID uuid) {
        // Use a temporary object that is later assigned to mmSocket
        // because mmSocket is final.
        BluetoothSocket tmp = null;
        mmDevice = device;
        mUUID = uuid;

        try {
            // Get a BluetoothSocket to connect with the given BluetoothDevice.
            // MY_UUID is the app's UUID string, also used in the server code.
            tmp = device.createRfcommSocketToServiceRecord(mUUID);
        } catch (IOException e) {
            Log.e(TAG, "Socket's create() method failed", e);
        }
        mmSocket = tmp;
    }

    public BluetoothSocket getMmSocket() {
        return mmSocket;
    }

    public void run() {
        // Cancel discovery because it otherwise slows down the connection.
        BluetoothAdapter.getDefaultAdapter().cancelDiscovery();

        Log.d(TAG,Thread.currentThread().getName());
        Log.d(TAG,"connecThread priority : "
                + Thread.currentThread().getPriority() );

        try {
            // Connect to the remote device through the socket. This call blocks
            // until it succeeds or throws an exception.
            mmSocket.connect();
        } catch (IOException connectException) {
            // Unable to connect; close the socket and return.
            try {
                mmSocket.close();
            } catch (IOException closeException) {
                Log.e(TAG, "Could not close the client socket", closeException);
            }
            return;
        }

        // The connection attempt succeeded. Perform work associated with
        // the connection in a separate thread.
        // Make this call by overriding run()
    }

    // Closes the client socket and causes the thread to finish.
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the client socket", e);
        }
    }
}