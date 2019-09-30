package org.apache.cordova.dgram;

import android.util.Log;
import android.util.SparseArray;
import android.util.Base64;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class Dgram extends CordovaPlugin {
    private static final String TAG = Dgram.class.getSimpleName();

    SparseArray<DatagramSocket> m_datagramSockets;
    SparseArray<DatagramSocketListener> m_datagramSocketListeners;
    ArrayBlockingQueue<String> m_javascriptQueue;
    SendJavascript m_sendJavascript;

    public UdpPlugin() {
        m_datagramSockets = new SparseArray<DatagramSocket>();
        m_datagramSocketListeners = new SparseArray<DatagramSocketListener>();
        m_javascriptQueue = new ArrayBlockingQueue<String>(1000);
        m_sendJavascript = new SendJavascript();
        m_sendJavascript.start();
    }

    private class SendJavascript extends Thread {
        public void run() {
            final String CORDOVA_REQUIRE = "cordova.require('cordova-plugin-dgram.dgram').";
            final int JAVASCRIPT_SLEEP = 50;

            while(true) {
                try {
                    String javascript = UdpPlugin.this.m_javascriptQueue.take();
                    UdpPlugin.this.webView.sendJavascript(CORDOVA_REQUIRE + javascript);
                    Thread.sleep(JAVASCRIPT_SLEEP);
                } catch (InterruptedException e) {
                    Log.d(TAG, e.toString());
                }
            }
        }
    }

    private class DatagramSocketListener extends Thread {
        int m_datagramSocketId;
        DatagramSocket m_datagramSocket;

        public DatagramSocketListener(
                int datagramSocketId,
                DatagramSocket datagramSocket
        ) {
            this.m_datagramSocketId = datagramSocketId;
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
                    UdpPlugin.this.m_javascriptQueue.put("_onMessage("
                            + this.m_datagramSocketId + ","
                            + "'" + message + "',"
                            + "'" + address + "',"
                            + port + ");");
                } catch (InterruptedException e) {
                    Log.d(TAG, e.toString());
                } catch (Exception e) {
                    Log.d(TAG, "Receive exception:" + e.toString());

                    try {
                        UdpPlugin.this.m_javascriptQueue.put("_onError("
                                + this.m_datagramSocketId
                                + ",'receive','"
                                + e.toString() + "');");
                    } catch (Exception innerException) {
                        Log.e(TAG, "Receive exception:" + innerException.toString());
                    } finally {
                        return;
                    }
                }
            }
        }
    }

    private class DatagramSocketSend implements Runnable {
        String m_message;
        String m_address;
        int m_port;
        int m_datagramSocketId;
        DatagramSocket m_datagramSocket;

        public DatagramSocketSend(
                final String message,
                final String address,
                final int port,
                final int datagramSocketId,
                final DatagramSocket datagramSocket
        ) {
            this.m_message = message;
            this.m_address = address;
            this.m_port = port;
            this.m_datagramSocketId = datagramSocketId;
            this.m_datagramSocket = datagramSocket;
        }

        public void run() {
            try {
                if (this.m_datagramSocket.isClosed()) {
                    Log.d(TAG, "Trying to send but socket closed");
                    return;
                }

                // Threaded send to prevent NetworkOnMainThreadException
                final byte[] bytes = this.m_message.getBytes("UTF-8");
                final DatagramPacket packet = new DatagramPacket(
                        bytes,
                        bytes.length,
                        InetAddress.getByName(this.m_address),
                        this.m_port
                );
                this.m_datagramSocket.send(packet);
                UdpPlugin.this.m_javascriptQueue.put("_onSend(" + this.m_datagramSocketId + ");");
            } catch (InterruptedException e) {
                Log.d(TAG, e.toString());
            } catch (Exception e) {
                Log.e(TAG, "Send exception: " + e.toString(), e);

                try {
                    UdpPlugin.this.m_javascriptQueue.put("_onError("
                            + this.m_datagramSocketId
                            + ",'send','"
                            + e.toString() + "');");
                } catch (Exception innerException) {
                    Log.e(TAG, "Send exception: " + e.toString());
                }
            }
        }
    }

    private void open(
            final int datagramSocketId,
            final int port,
            final boolean isBroadcast
    ) throws SocketException {
        DatagramSocket datagramSocket = new DatagramSocket(port);
        datagramSocket.setBroadcast(isBroadcast);
        m_datagramSockets.put(datagramSocketId, datagramSocket);
    }

    private void startListening(
            final int datagramSocketId,
            final DatagramSocket datagramSocket
    ) {
        closeListener(datagramSocketId);
        DatagramSocketListener datagramSocketListener = new DatagramSocketListener(
                datagramSocketId,
                datagramSocket
        );
        m_datagramSocketListeners.put(datagramSocketId, datagramSocketListener);
        datagramSocketListener.start();
    }

    private void close(
            final int datagramSocketId,
            final DatagramSocket datagramSocket
    ) {
        if (datagramSocket != null) {
            if (!datagramSocket.isClosed()) {
                datagramSocket.close();
            }

            m_datagramSockets.remove(datagramSocketId);
            closeListener(datagramSocketId);
        }
    }

    private void closeListener(
            final int datagramSocketId
    ) {
        final DatagramSocketListener datagramSocketListener = m_datagramSocketListeners.get(datagramSocketId);

        if (datagramSocketListener != null) {
            datagramSocketListener.interrupt();
            m_datagramSocketListeners.remove(datagramSocketId);
        }
    }

    @Override
    public boolean execute(
            final String action,
            final JSONArray data,
            final CallbackContext callbackContext
    ) throws JSONException {
        final int datagramSocketId = data.getInt(0);
        DatagramSocket datagramSocket = m_datagramSockets.get(datagramSocketId);
        Log.d(TAG, "Call to execute " + action + " " + data.toString());

        if (!(action.equals("open") || action.equals("listen") || action.equals("send") || action.equals("close"))) {
            // Returning false results in an INVALID_ACTION error,
            // which translates to an error callback in the JavaScript
            return false;
        }

        if (datagramSocket == null && !action.equals("open")) {
            callbackContext.error("DatagramSocket has not been opened!");
            return true;
        }

        try {
            if (action.equals("open")) {
                close(datagramSocketId, datagramSocket);
                final int port = data.getInt(1);
                final boolean isBroadcast = data.getInt(2) == 1;
                open(datagramSocketId, port, isBroadcast);
                callbackContext.success();
            } else if (action.equals("listen")) {
                startListening(datagramSocketId, datagramSocket);
                callbackContext.success();
            } else if (action.equals("send")) {
                final String message = data.getString(1);
                final String address = data.getString(2);
                final int port = data.getInt(3);
                cordova.getThreadPool().execute(new DatagramSocketSend(
                        message,
                        address,
                        port,
                        datagramSocketId,
                        datagramSocket
                ));
                callbackContext.success();
            } else if (action.equals("close")) {
                close(datagramSocketId, datagramSocket);
                callbackContext.success();
            }
        } catch (Exception e) {
            Log.e(TAG, "Attempting " + action + " failed with: " + e.toString(), e);
            callbackContext.error("'" + e.toString() + "'");
        }

        return true;
    }
}
