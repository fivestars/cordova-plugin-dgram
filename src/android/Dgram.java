package org.apache.cordova.dgram;

import android.util.Log;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.DatagramSocket;
import java.net.SocketException;

public class Dgram extends CordovaPlugin {
    public static final String TAG = Dgram.class.getSimpleName();

    private static final String OPEN_ACTION = "open";
    private static final String LISTEN_ACTION = "listen";
    private static final String SEND_ACTION = "send";
    private static final String CLOSE_ACTION = "close";

    // It is expected that these are opened and closed in lockstep but
    // because Cordova does not accept a function as a value in the
    // JSONArray the open is a two step process (first open, then listen).
    private DatagramSocket datagramSocket;
    private DatagramSocketListener datagramSocketListener;

    @Override
    public boolean execute(
        final String action, 
        final JSONArray data,
        final CallbackContext callbackContext
    ) throws JSONException {
        Log.d(TAG, "Call to execute " + action + " " + data.toString());

        if (datagramSocket == null && !action.equals(OPEN_ACTION)) {
            callbackContext.error("DatagramSocket is not set!");
            return true;
        }

        switch (action) {
            case OPEN_ACTION:
                this.openSocket(action, data, callbackContext);
                break;

            case LISTEN_ACTION:
                this.startListening(callbackContext);
                break;

            case SEND_ACTION:
                final String message = data.getString(0);
                final String address = data.getString(1);
                final int port = data.getInt(2);
                
                cordova.getThreadPool().execute(new DatagramSocketSend(
                    this.datagramSocket,
                    callbackContext,
                    message,
                    address,
                    port
                ));
                break;

            case CLOSE_ACTION:
                this.closeSocket();
                callbackContext.success();
                break;

            default:
                return false;
        }

        return true;
    }

    private void openSocket(
        final String action, 
        final JSONArray data, 
        final CallbackContext callbackContext
    ) throws JSONException {
        this.closeSocket();
        final int port = data.getInt(0);
        final boolean isBroadcast = data.getBoolean(1);
        try {
            this.open(port, isBroadcast);
            callbackContext.success();
        } catch (SocketException e) {
            Log.e(TAG, "Attempting open socket failed with: " + e.toString(), e);
            callbackContext.error("'" + e.toString() + "'");
        }
    }

    private void open(
        final int port, 
        final boolean isBroadcast
    ) throws SocketException {
        this.datagramSocket = new DatagramSocket(port);
        this.datagramSocket.setBroadcast(isBroadcast);
    }

    private void startListening(
        final CallbackContext callbackContext
    ) {
        try {
            this.closeListener();
            this.datagramSocketListener = new DatagramSocketListener(
                this.datagramSocket,
                callbackContext
            );
            this.datagramSocketListener.start();
        } catch (Exception e) {
            Log.e(TAG, "Attempting to start listening failed with: " + e.toString(), e);
            callbackContext.error("'" + e.toString() + "'");
        }
    }

    private void closeSocket() {
        try {
            if (this.datagramSocket != null) {
                if (!this.datagramSocket.isClosed()) {
                    this.datagramSocket.close();
                }

                this.datagramSocket = null;
                this.closeListener();
            }
        } catch (Exception e) {
            Log.e(TAG, "Attempting to close socket failed: " + e.toString(), e);
        }
    }

    private void closeListener() {
        if (this.datagramSocketListener != null) {
            this.datagramSocketListener.interrupt();
            this.datagramSocketListener = null;
        }
    }

}
