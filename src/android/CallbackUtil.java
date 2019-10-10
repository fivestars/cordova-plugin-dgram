package org.apache.cordova.dgram;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;


public class CallbackUtil {
    private static final String TAG = CallbackUtil.class.getSimpleName();

    /**
     * Helper function to make sure JavaScript side of cordova keeps the callback reference
     * alive. This allows the same JavaScript function to be used mutliple times via the
     * CallbackContext stored in this plugin
     */
    public static void emitPluginResult(CallbackContext callback, PluginResult result) {
        if (callback == null || result == null) {
            Log.d(TAG, "The emitPluginResult helper was called with null parameter(s).");
            return;
        }
        result.setKeepCallback(true);
        callback.sendPluginResult(result);
    }

}