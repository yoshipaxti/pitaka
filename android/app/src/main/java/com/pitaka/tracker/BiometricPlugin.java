package com.pitaka.tracker;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Unlocks Pitaka's app lock with the phone's fingerprint or face unlock. On Android 11 and newer the phone's own
 * screen lock (PIN, pattern, password) is offered too, so a forgotten Pitaka PIN does not lock the owner out.
 */
@CapacitorPlugin(name = "Biometric")
public class BiometricPlugin extends Plugin {

    private int authenticators() {
        return Build.VERSION.SDK_INT >= 30
            ? BiometricManager.Authenticators.BIOMETRIC_WEAK | BiometricManager.Authenticators.DEVICE_CREDENTIAL
            : BiometricManager.Authenticators.BIOMETRIC_WEAK;
    }

    @PluginMethod
    public void available(PluginCall call) {
        int r = BiometricManager.from(getContext()).canAuthenticate(authenticators());
        JSObject o = new JSObject();
        o.put("available", r == BiometricManager.BIOMETRIC_SUCCESS);
        o.put("deviceCredential", Build.VERSION.SDK_INT >= 30);
        call.resolve(o);
    }

    @PluginMethod
    public void verify(PluginCall call) {
        String reason = call.getString("reason", "Unlock Pitaka");
        FragmentActivity activity = getActivity();
        activity.runOnUiThread(() -> {
            BiometricPrompt prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        JSObject o = new JSObject();
                        o.put("verified", true);
                        call.resolve(o);
                    }

                    @Override
                    public void onAuthenticationError(int code, @NonNull CharSequence msg) {
                        // Cancelled, "Use PIN" tapped, too many attempts…: the PIN keypad stays on screen.
                        call.reject(msg.toString(), String.valueOf(code));
                    }
                });
            BiometricPrompt.PromptInfo.Builder info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Pitaka")
                .setSubtitle(reason)
                .setAllowedAuthenticators(authenticators());
            if (Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("Use PIN");
            try {
                prompt.authenticate(info.build());
            } catch (Exception e) {
                call.reject(e.getMessage() != null ? e.getMessage() : "Fingerprint unlock is not available");
            }
        });
    }
}
