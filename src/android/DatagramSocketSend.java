package org.apache.cordova.dgram;

import android.util.Log;

import org.apache.cordova.CallbackContext;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import java.nio.charset.StandardCharsets;

class DatagramSocketSend implements Runnable {
    private DatagramSocket datagramSocket;
    private CallbackContext callbackContext;
    private String message;
    private String address;
    private int port;

    public DatagramSocketSend(
        final DatagramSocket datagramSocket,
        final CallbackContext callbackContext, 
        final String message,
        final String address, 
        final int port
    ) {
        this.datagramSocket = datagramSocket;
        this.callbackContext = callbackContext;
        this.message = message;
        this.address = address;
        this.port = port;
    }

    public void run() {
        try {
            if (this.datagramSocket == null || this.datagramSocket.isClosed()) {
                Log.d(Dgram.TAG, "Attempted to send message but socket is closed.");
                return;
            }

            final byte[] bytes = this.message.getBytes(StandardCharsets.UTF_8);
            final DatagramPacket packet = new DatagramPacket(
                    bytes,
                    bytes.length,
                    InetAddress.getByName(this.address),
                    this.port
            );
            // The send is wrapped in this thread to prevent NetworkOnMainThreadException
            this.datagramSocket.send(packet);
            this.callbackContext.success();
        } catch (Exception e) {
            Log.e(Dgram.TAG, "Send exception: " + e.toString(), e);
            this.callbackContext.error("Exception sending message: " + e.toString());
        }
    }
}
