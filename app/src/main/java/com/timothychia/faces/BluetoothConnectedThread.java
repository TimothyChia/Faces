package com.timothychia.faces;

import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

// based on https://developer.android.com/guide/topics/connectivity/bluetooth.html
public class BluetoothConnectedThread extends Thread {

    private static final String TAG = "BT_Connected_T_Debug";

    // made these public to make anonymous subclass less verbose.
    public final BluetoothSocket mmSocket;
    public final InputStream mmInStream;
    public final OutputStream mmOutStream;
    public byte[] mmBuffer; // mmBuffer store for the stream

    public final Handler mHandler;

    public BluetoothConnectedThread(BluetoothSocket socket, Handler handler) {
        mmSocket = socket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        mHandler = handler;

        // Get the input and output streams; using temp objects because
        // member streams are final.
        try {
            tmpIn = socket.getInputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating input stream", e);
        }
        try {
            tmpOut = socket.getOutputStream();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when creating output stream", e);
        }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
    }

    public void run() {
        mmBuffer = new byte[1024];
        int numBytes; // bytes returned from read()

//             Keep listening to the InputStream until an exception occurs.
        while (true) {
            try {
                Log.d(TAG, "Attempting to read");

//                     Read from the InputStream.
                numBytes = mmInStream.read(mmBuffer);

//                     testing by constantly printing the read buffer
                String s = new String(mmBuffer);
                Log.d(TAG, s);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        // this will run in the main thread

                    }
                });

//                    // Send the obtained bytes to the UI activity.
//                    Message readMsg = mHandler.obtainMessage(
//                            MessageConstants.MESSAGE_READ, numBytes, -1,
//                            mmBuffer);
//                    readMsg.sendToTarget();
            } catch (IOException e) {
                Log.d(TAG, "Input stream was disconnected", e);
                break;
            }
        }
    }

    // Call this from the main activity to send data to the remote device.
    public void write(byte[] bytes) {
        try {
            mmOutStream.write(bytes);

//                // Share the sent message with the UI activity.
//                Message writtenMsg = mHandler.obtainMessage(
//                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
//                writtenMsg.sendToTarget();
        } catch (IOException e) {
            Log.e(TAG, "Error occurred when sending data", e);

            // Send a failure message back to the activity.
//            Message writeErrorMsg =
//                    mHandler.obtainMessage(BluetoothTest.MessageConstants.MESSAGE_TOAST);
//            Bundle bundle = new Bundle();
//            bundle.putString("toast",
//                    "Couldn't send data to the other device");
//            writeErrorMsg.setData(bundle);
//            mHandler.sendMessage(writeErrorMsg);
        }
    }

    // Call this method from the main activity to shut down the connection.
    public void cancel() {
        try {
            mmSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "Could not close the connect socket", e);
        }
    }
}