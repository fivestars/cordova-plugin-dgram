package org.apache.cordova.dgram;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

private class DatagramSocketSend implements Runnable {
    private static final String TAG = DatagramSocketSend.class.getSimpleName();

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
                Log.d(TAG, "Trying to send but socket closed");
                return;
            }

            // Threaded send to prevent NetworkOnMainThreadException
            final byte[] bytes = this.message.getBytes(StandardCharsets.UTF_8);
            final DatagramPacket packet = new DatagramPacket(
                    bytes,
                    bytes.length,
                    InetAddress.getByName(this.address),
                    this.port
            );
            this.datagramSocket.send(packet);
            this.callbackContext.success();
        } catch (Exception e) {
            Log.e(TAG, "Send exception: " + e.toString(), e);
            this.callbackContext.error("Send exception: " + e.toString());
        }
    }
}
