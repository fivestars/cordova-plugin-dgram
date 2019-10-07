package org.apache.cordova.dgram;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;


public class CallbackUtil {
    /**
     * Helper function to make sure JavaScript side of cordova keeps the callback reference
     * alive. This allows the same JavaScript function to be used mutliple times via the
     * CallbackContext stored in this plugin
     */
    public static void sendPluginResult(CallbackContext callback, PluginResult result) {
        if (callback == null || result == null) {
            return;
        }
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
    }

}