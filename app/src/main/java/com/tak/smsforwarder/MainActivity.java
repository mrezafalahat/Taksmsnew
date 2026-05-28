package com.takpack.smsforwarder;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private WebView webView;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);

        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new Bridge(), "AndroidBridge");
        webView.loadUrl("file:///android_asset/index.html");

        requestPermissionsOnStart();
    }

    private void requestPermissionsOnStart() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}, 1201);
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1202);
            }
        }
    }

    private boolean batteryOk() {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    public class Bridge {
        @JavascriptInterface
        public boolean hasSmsPermission() {
            if (Build.VERSION.SDK_INT < 23) return true;
            return checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED;
        }

        @JavascriptInterface
        public boolean isBatteryUnrestricted() {
            return batteryOk();
        }

        @JavascriptInterface
        public void openBatterySettings() {
            runOnUiThread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 23) {
                        Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } else {
                        startActivity(new Intent(Settings.ACTION_SETTINGS));
                    }
                } catch(Exception e) {
                    try {
                        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                    } catch(Exception ignored) {}
                }
            });
        }

        @JavascriptInterface
        public void setEnabled(boolean enabled) {
            DataStore.setEnabled(MainActivity.this, enabled);
        }

        @JavascriptInterface
        public boolean isEnabled() {
            return DataStore.isEnabled(MainActivity.this);
        }

        @JavascriptInterface
        public String getRules() {
            return DataStore.getRules(MainActivity.this);
        }

        @JavascriptInterface
        public boolean saveRule(String ruleJson) {
            return DataStore.saveRule(MainActivity.this, ruleJson);
        }

        @JavascriptInterface
        public boolean deleteRule(String id) {
            return DataStore.deleteRule(MainActivity.this, id);
        }

        @JavascriptInterface
        public String getSms() {
            return DataStore.getSms(MainActivity.this);
        }

        @JavascriptInterface
        public void clearSms() {
            DataStore.clearSms(MainActivity.this);
        }

        @JavascriptInterface
        public String getStatus() {
            try {
                JSONObject o = new JSONObject();
                o.put("permission", hasSmsPermission());
                o.put("enabled", isEnabled());
                o.put("battery", isBatteryUnrestricted());
                o.put("rulesCount", DataStore.getRulesArray(MainActivity.this).length());
                o.put("smsCount", DataStore.getSmsArray(MainActivity.this).length());
                return o.toString();
            } catch(Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public void addTestSms() {
            DataStore.addSms(MainActivity.this, "0098100099", "کد تایید بانک ملت 12345 است.", System.currentTimeMillis());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.postDelayed(() -> webView.evaluateJavascript("window.App && App.refresh && App.refresh();", null), 300);
        }
    }
}
