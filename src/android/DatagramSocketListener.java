package org.apache.cordova.dgram;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONObject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;

import java.nio.charset.StandardCharsets;

class DatagramSocketListener extends Thread {
    private DatagramSocket datagramSocket;
    private CallbackContext callbackContext;

    DatagramSocketListener(
        final DatagramSocket datagramSocket,
        final CallbackContext callbackContext
    ) {
        this.datagramSocket = datagramSocket;
        this.callbackContext = callbackContext;
    }

    public void run() {
        // The field size sets a theoretical limit of 65,535 bytes (8 byte header + 65,527 bytes of data)
        // for a UDP datagram. However the actual limit for the data length, which is imposed by the
        // underlying IPv4 protocol, is 65,507 bytes (65,535 − 8 byte UDP header − 20 byte IP header).
        // For now allowing 10 megabytes which seems plenty large.
        byte[] data = new byte[1024*10];
        DatagramPacket datagramPacket = new DatagramPacket(data, data.length);
        emitMessageResult("listener started", "", 0);

        while (true) {
            try {
                if (this.datagramSocket == null ||  this.datagramSocket.isClosed()) {
                    Log.d(Dgram.TAG, "Exiting message loop because socket is closed.");
                    return;
                }

                // Reset the length in case we receive an incomplete DatagramPacket
                datagramPacket.setLength(data.length);
                this.datagramSocket.receive(datagramPacket);
                String message = new String(data, 0, datagramPacket.getLength(), StandardCharsets.UTF_8);
                String address = datagramPacket.getAddress().getHostAddress();
                int port = datagramPacket.getPort();
                Log.d(Dgram.TAG, "Received message " + message + " from " + address + " and port " + port);
                emitMessageResult(message, address, port);
            } catch (Exception e) {
                Log.e(Dgram.TAG, "Exception in listener:" + e.toString());
                emitMessageErrorResult(e);
            }
        }
    }

    private void emitMessageResult(String message, String address, int port) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("message", message);
            payload.put("address", address);
            payload.put("port", port);
            CallbackUtil.emitPluginResult(this.callbackContext, new PluginResult(PluginResult.Status.OK, payload));
        } catch (Exception e) {
            Log.e(Dgram.TAG, "Exception emitting message:" + e.toString());
        }
    }

    private void emitMessageErrorResult(Exception e) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("error", e.toString());
            CallbackUtil.emitPluginResult(this.callbackContext, new PluginResult(PluginResult.Status.ERROR, payload));
        } catch (Exception exception) {
            Log.e(Dgram.TAG, "Exception emitting message:" + e.toString());
        }
    }
}