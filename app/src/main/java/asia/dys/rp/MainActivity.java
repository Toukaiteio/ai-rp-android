package asia.dys.rp;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private static final String APP_URL = "https://rp.dys.asia/";
    private static final int FILE_PICKER_REQUEST = 4101;
    private static final int MAX_FILE_BYTES = 32 * 1024 * 1024;
    private WebView webView;
    private String pendingFileRequest;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // The web app owns its own top bar. Do not reserve a second Android status-bar row.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setVerticalScrollBarEnabled(false);
        webView.setHorizontalScrollBarEnabled(false);
        webView.setOverScrollMode(WebView.OVER_SCROLL_NEVER);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                // Older deployments still have a scrollable VaultGate. Keep the native
                // shell stable there too; the rule automatically stops applying after unlock.
                view.evaluateJavascript(
                        "(function(){var s=document.getElementById('android-viewport-fix');" +
                                "if(!s){s=document.createElement('style');s.id='android-viewport-fix';" +
                                "s.textContent='body:has(input[type=\\\"password\\\"]){overflow:hidden!important;}';" +
                                "document.head.appendChild(s);}" +
                                "window.__androidFilePicker=window.__androidFilePicker||{};" +
                                "window.AndroidBridge={" +
                                "storageGet:function(a,b){return AndroidNative.storageGet(a,b)}," +
                                "storageSet:function(a,b,c){return AndroidNative.storageSet(a,b,c)}," +
                                "storageRemove:function(a,b){return AndroidNative.storageRemove(a,b)}," +
                                "storageKeys:function(a){return AndroidNative.storageKeys(a)}," +
                                "storageClear:function(a){return AndroidNative.storageClear(a)}," +
                                "pickFile:function(accept){return new Promise(function(resolve,reject){" +
                                "var id=String(Date.now())+'-'+String(Math.random());" +
                                "window.__androidFilePicker[id]={resolve:resolve,reject:reject};" +
                                "AndroidNative.pickFileNative(id,accept||'*/*');});}" +
                                "};})()", null);
            }
        });
        webView.addJavascriptInterface(new NativeStorage(this, this::openFilePicker), "AndroidNative");
        setContentView(webView);
        if (state == null) webView.loadUrl(APP_URL);
        else webView.restoreState(state);
    }

    private void openFilePicker(String requestId, String accept) {
        runOnUiThread(() -> {
            if (pendingFileRequest != null) {
                resolveFilePicker(pendingFileRequest, null, "Another file selection is already in progress");
            }
            pendingFileRequest = requestId;
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            List<String> types = acceptedMimeTypes(accept);
            if (types.size() == 1 && !"*/*".equals(types.get(0))) {
                intent.setType(types.get(0));
            } else {
                intent.setType("*/*");
                if (!types.isEmpty()) intent.putExtra(Intent.EXTRA_MIME_TYPES, types.toArray(new String[0]));
            }
            startActivityForResult(intent, FILE_PICKER_REQUEST);
        });
    }

    private static List<String> acceptedMimeTypes(String accept) {
        ArrayList<String> result = new ArrayList<>();
        if (accept == null) return result;
        for (String raw : accept.split(",")) {
            String value = raw.trim().toLowerCase();
            if (value.contains("/")) result.add(value);
            else if (value.endsWith(".json")) result.add("application/json");
            else if (value.endsWith(".png")) result.add("image/png");
            else if (value.endsWith(".jpg") || value.endsWith(".jpeg")) result.add("image/jpeg");
            else if (value.endsWith(".epub")) result.add("application/epub+zip");
        }
        return result;
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_PICKER_REQUEST || pendingFileRequest == null) return;
        String requestId = pendingFileRequest;
        pendingFileRequest = null;
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            resolveFilePicker(requestId, null, "USER_CANCELLED");
            return;
        }
        try {
            Uri uri = data.getData();
            String name = displayName(uri);
            String type = getContentResolver().getType(uri);
            byte[] bytes = readFile(uri);
            JSONObject result = new JSONObject();
            result.put("name", name);
            result.put("type", type == null ? "application/octet-stream" : type);
            result.put("data", Base64.encodeToString(bytes, Base64.NO_WRAP));
            resolveFilePicker(requestId, result.toString(), null);
        } catch (Exception error) {
            resolveFilePicker(requestId, null, "FILE_READ_FAILED");
        }
    }

    private String displayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        String path = uri.getLastPathSegment();
        return path == null ? "file" : path;
    }

    private byte[] readFile(Uri uri) throws Exception {
        try (InputStream input = getContentResolver().openInputStream(uri); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IllegalStateException("empty file");
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_BYTES) throw new IllegalStateException("file too large");
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        }
    }

    private void resolveFilePicker(String requestId, String result, String error) {
        if (webView == null) return;
        String payload = result == null ? "null" : JSONObject.quote(result);
        String errorText = error == null ? "null" : JSONObject.quote(error);
        webView.evaluateJavascript("(function(){var p=window.__androidFilePicker&&window.__androidFilePicker[" +
                JSONObject.quote(requestId) + "];if(!p)return;delete window.__androidFilePicker[" +
                JSONObject.quote(requestId) + "];if(" + errorText + "){p.reject(new Error(" + errorText + "));}else{p.resolve(JSON.parse(" + payload + "));}})()", null);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
