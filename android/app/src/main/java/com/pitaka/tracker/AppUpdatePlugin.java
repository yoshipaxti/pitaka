package com.pitaka.tracker;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.content.FileProvider;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Lets the web app update itself: report the installed version, download a newer APK, and hand it to Android's installer.
 * Android still shows its own "Update this app?" screen; it also refuses any APK not signed with the same key as the installed app.
 */
@CapacitorPlugin(name = "AppUpdate")
public class AppUpdatePlugin extends Plugin {

    private static final String APK_HOST = "https://github.com/yoshipaxti/pitaka/releases/";

    private File apkFile() {
        File dir = new File(getContext().getCacheDir(), "updates");
        dir.mkdirs();
        return new File(dir, "pitaka-update.apk");
    }

    @PluginMethod
    public void info(PluginCall call) {
        try {
            PackageInfo p = getContext().getPackageManager().getPackageInfo(getContext().getPackageName(), 0);
            JSObject r = new JSObject();
            r.put("versionCode", Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode);
            r.put("versionName", p.versionName);
            call.resolve(r);
        } catch (Exception e) {
            call.reject("Could not read the app version", e);
        }
    }

    /** GitHub answers release links with a redirect to its download server, so follow up to 5 https hops by hand. */
    private static HttpURLConnection open(String url) throws Exception {
        URL u = new URL(url);
        for (int hop = 0; hop < 5; hop++) {
            HttpURLConnection c = (HttpURLConnection) u.openConnection();
            c.setInstanceFollowRedirects(false);
            c.setConnectTimeout(15000);
            c.setReadTimeout(30000);
            c.setRequestProperty("Cache-Control", "no-cache");
            int code = c.getResponseCode();
            if (code >= 300 && code < 400) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                u = new URL(u, loc);
                if (!"https".equals(u.getProtocol())) throw new Exception("Insecure download link");
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new Exception("Server answered " + code);
            }
            return c;
        }
        throw new Exception("Too many redirects");
    }

    /** Reads a small text file (the version.json of the latest release). */
    @PluginMethod
    public void fetchText(PluginCall call) {
        String url = call.getString("url", "");
        if (!url.startsWith(APK_HOST)) {
            call.reject("Updates only come from the Pitaka releases page");
            return;
        }
        new Thread(() -> {
            HttpURLConnection c = null;
            try {
                c = open(url);
                StringBuilder sb = new StringBuilder();
                try (InputStream in = c.getInputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) > 0 && sb.length() < 65536) sb.append(new String(buf, 0, n, "UTF-8"));
                }
                JSObject r = new JSObject();
                r.put("text", sb.toString());
                call.resolve(r);
            } catch (Exception e) {
                call.reject(e.getMessage() != null ? e.getMessage() : "Could not check for updates", e);
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    @PluginMethod
    public void download(PluginCall call) {
        String url = call.getString("url", "");
        if (!url.startsWith(APK_HOST)) {
            call.reject("Updates only come from the Pitaka releases page");
            return;
        }
        new Thread(() -> {
            HttpURLConnection c = null;
            File out = apkFile();
            try {
                c = open(url);
                long total = c.getContentLengthLong(), done = 0;
                int lastPct = -1;
                try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        os.write(buf, 0, n);
                        done += n;
                        int pct = total > 0 ? (int) (done * 100 / total) : -1;
                        if (pct != lastPct) {
                            lastPct = pct;
                            JSObject ev = new JSObject();
                            ev.put("percent", pct);
                            notifyListeners("progress", ev);
                        }
                    }
                }
                JSObject r = new JSObject();
                r.put("bytes", done);
                call.resolve(r);
            } catch (Exception e) {
                out.delete();
                call.reject(e.getMessage() != null ? e.getMessage() : "Download failed", e);
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    @PluginMethod
    public void install(PluginCall call) {
        File f = apkFile();
        if (!f.exists()) {
            call.reject("Download the update first");
            return;
        }
        // Android 8+: the user must allow Pitaka to install apps once. Send them to that switch, then they tap Install again.
        if (Build.VERSION.SDK_INT >= 26 && !getContext().getPackageManager().canRequestPackageInstalls()) {
            Intent s = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName()));
            s.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(s);
            call.reject("Allow Pitaka to install updates, then tap Install again", "PERMISSION");
            return;
        }
        Uri uri = FileProvider.getUriForFile(getContext(), getContext().getPackageName() + ".fileprovider", f);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(i);
        call.resolve();
    }
}
