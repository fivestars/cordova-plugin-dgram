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
    private static final String TAG = Dgram.class.getSimpleName();

    private static final String OPEN_ACTION = "open";
    private static final String LISTEN_ACTION = "listen";
    private static final String SEND_ACTION = "send";
    private static final String CLOSE_ACTION = "close";

    // It is expected that these are opened and closed in lockstep but
    // because Cordova does not accept a function as parameter in the 
    // JSONArray the open is a two step process.
    private DatagramSocket datagramSocket;
    private DatagramSocketListener datagramSocketListener;

    @Override
    public boolean execute(final String action, final JSONArray data,
                           final CallbackContext callbackContext) throws JSONException {
        Log.e(TAG, "Call to execute " + action + " " + data.toString());

        if (datagramSocket == null && !action.equals(OPEN_ACTION)) {
            callbackContext.error("DatagramSocket has not been opened!");
            return true;
        }

        switch (action) {
            case OPEN_ACTION:
                openSocket(action, data, callbackContext);
                break;

            case LISTEN_ACTION:
                startListening(callbackContext);
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
                closeSocket();
                callbackContext.success();
                break;

            default:
                return false;
        }

        return true;
    }

    private void openSocket(String action, JSONArray data, CallbackContext callbackContext) throws JSONException {
        closeSocket();
        final int port = data.getInt(0);
        final boolean isBroadcast = data.getBoolean(1);
        try {
            open(port, isBroadcast);
            callbackContext.success();
        } catch (SocketException e) {
            Log.e(TAG, "Attempting open socket failed with: " + e.toString(), e);
            callbackContext.error("'" + e.toString() + "'");
        }
    }

    private void open(final int port, final boolean isBroadcast) throws SocketException {
        this.datagramSocket = new DatagramSocket(port);
        this.datagramSocket.setBroadcast(isBroadcast);
    }

    private void startListening(CallbackContext callbackContext) {
        try {
            closeListener();
            this.datagramSocketListener = new DatagramSocketListener(
                this.datagramSocket,
                callbackContext
            );
            this.datagramSocketListener.start();
        } catch (SocketException e) {
            Log.e(TAG, "Attempting to start listening failed with: " + e.toString(), e);
            callbackContext.error("'" + e.toString() + "'");
        }
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
