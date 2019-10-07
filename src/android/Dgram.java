package org.apache.cordova.dgram;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

import java.net.SocketException;
import java.nio.charset.StandardCharsets;


public class Dgram extends CordovaPlugin {
    private static final String TAG = Dgram.class.getSimpleName();

    private static final String OPEN_ACTION = "open";
    private static final String ON_MESSAGE_ACTION = "onMessage";
    private static final String SEND_ACTION = "send";
    private static final String CLOSE_ACTION = "close";

    private DatagramSocket datagramSocket;
    private DatagramSocketListener datagramSocketListener;

    private CallbackContext onMessageCallback;

    public Dgram() { }

    private class DatagramSocketListener extends Thread {
        DatagramSocket m_datagramSocket;

        DatagramSocketListener(
                DatagramSocket datagramSocket
        ) {
            this.m_datagramSocket = datagramSocket;
        }

        public void run() {
            // The field size sets a theoretical limit of 65,535 bytes (8 byte header + 65,527 bytes of data)
            // for a UDP datagram. However the actual limit for the data length, which is imposed by the
            // underlying IPv4 protocol, is 65,507 bytes (65,535 − 8 byte UDP header − 20 byte IP header).
            // For now allowing 10 megabytes which seems plenty large.
            byte[] data = new byte[1024*10];
            DatagramPacket datagramPacket = new DatagramPacket(data, data.length);
            while (true) {
                try {
                    if (this.m_datagramSocket.isClosed()) {
                        Log.d(TAG, "Exiting message loop because socket is closed.");
                        return;
                    }

                    // Reset the length in case we receive an incomplete DatagramPacket
                    datagramPacket.setLength(data.length);
                    this.m_datagramSocket.receive(datagramPacket);
                    String message = new String(data, 0, datagramPacket.getLength(), "UTF-8");
                    String address = datagramPacket.getAddress().getHostAddress();
                    int port = datagramPacket.getPort();
                    Log.d(TAG, "Received message " + message + " from " + address);
                    if (onMessageCallback != null) {
                        if (sendMessageResult(message, address, port)) return;
                    }
                } catch (Exception e) {
                    Log.d(TAG, "Received exception:" + e.toString());
                    if (sendMessageErrorResult(e)) return;
                }
            }
        }
    }

    private boolean sendMessageResult(String message, String address, int port) {
        JSONObject payload = new JSONObject();

        try {
            payload.put("message", message.trim());
            payload.put("address", address);
            payload.put("port", port);
        } catch (Exception e) {
            PluginResult result = new PluginResult(
                    PluginResult.Status.ERROR,
                    "Error occurred while creating onMessage payload: " + e.toString()
            );
            CallbackUtil.sendPluginResult(onMessageCallback, result);
            return true;
        }

        CallbackUtil.sendPluginResult(onMessageCallback, new PluginResult(PluginResult.Status.OK, payload));
        return false;
    }

    private boolean sendMessageErrorResult(Exception e) {
        try {
            JSONObject payload = new JSONObject();

            try {
                payload.put("error", e.toString());

            } catch (Exception exception) {
                Log.e(TAG, exception.toString());
                PluginResult result = new PluginResult(
                        PluginResult.Status.ERROR,
                        "Error occurred while creating onMessage payload: " + e.toString()
                );
                CallbackUtil.sendPluginResult(onMessageCallback, result);
                return true;
            }
            CallbackUtil.sendPluginResult(onMessageCallback,
                    new PluginResult(PluginResult.Status.ERROR, payload)
            );
        } catch (Exception innerException) {
            Log.e(TAG, "Received exception:" + innerException.toString());
        }
        return false;
    }

    private class DatagramSocketSend implements Runnable {
        CallbackContext sendCallback;
        String message;
        String address;
        int port;

        public DatagramSocketSend(
                CallbackContext callbackContext, final String message,
                final String address,
                final int port
        ) {
            this.sendCallback = callbackContext;
            this.message = message;
            this.address = address;
            this.port = port;
        }

        public void run() {
            try {
                if (datagramSocket.isClosed()) {
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
                datagramSocket.send(packet);
                sendCallback.success();
            } catch (Exception e) {
                Log.e(TAG, "Send exception: " + e.toString(), e);
                sendCallback.error("Send exception: " + e.toString());
            }
        }
    }

    @Override
    public boolean execute(final String action, final JSONArray data,
                           final CallbackContext callbackContext) throws JSONException {
        Log.e(TAG, "Call to execute " + action + " " + data.toString());

        if (!(action.equals(OPEN_ACTION) || action.equals(ON_MESSAGE_ACTION) || action.equals(SEND_ACTION) || action.equals(CLOSE_ACTION))) {
            // Returning false results in an INVALID_ACTION error,
            // which translates to an error callback in the JavaScript
            return false;
        }

        if (datagramSocket == null && !action.equals(OPEN_ACTION)) {
            callbackContext.error("DatagramSocket has not been opened!");
            return true;
        }

        switch (action) {
            case OPEN_ACTION:
                openSocket(action, data, callbackContext);
                break;

            case ON_MESSAGE_ACTION:
                startListening(callbackContext);
                break;

            case SEND_ACTION:
                final String message = data.getString(0);
                final String address = data.getString(1);
                final int sendPort = data.getInt(2);
                cordova.getThreadPool().execute(new DatagramSocketSend(callbackContext,
                        message,
                        address,
                        sendPort
                ));
                break;

            case CLOSE_ACTION:
                closeSocket();
                callbackContext.success();
                break;
        }

        return true;
    }

    private void openSocket(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        closeSocket();
        final int port = data.getInt(0);
        final boolean isBroadcast = data.getInt(1) == 1;
        try {
            open(port, isBroadcast);
        } catch (SocketException e) {
            Log.e(TAG, "Attempting " + action + " failed with: " + e.toString(), e);
            callbackContext.error("'" + e.toString() + "'");
        }
        callbackContext.success();
    }

    private void open(final int port, final boolean isBroadcast) throws SocketException {
        DatagramSocket datagramSocket = new DatagramSocket(port);
        datagramSocket.setBroadcast(isBroadcast);
        this.datagramSocket = datagramSocket;
    }

    private void startListening(CallbackContext callbackContext) {
        closeListener();
        onMessageCallback = callbackContext;
        DatagramSocketListener datagramSocketListener = new DatagramSocketListener(
                datagramSocket
        );
        this.datagramSocketListener = datagramSocketListener;
        datagramSocketListener.start();
    }

    private void closeSocket() {
        if (datagramSocket != null) {
            if (!datagramSocket.isClosed()) {
                datagramSocket.close();
            }

            this.datagramSocket = null;
            closeListener();
        }
    }

    private void closeListener() {
        final DatagramSocketListener datagramSocketListener = this.datagramSocketListener;

        if (datagramSocketListener != null) {
            datagramSocketListener.interrupt();
            this.datagramSocketListener = null;
        }
    }

}
