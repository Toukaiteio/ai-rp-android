package asia.dys.rp;

import android.content.Context;
import android.util.Base64;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;

/** Stores the already-encrypted values produced by the web Vault. */
public final class NativeStorage {
    public interface FilePicker {
        void open(String requestId, String accept);
    }

    private final File root;
    private final FilePicker filePicker;

    NativeStorage(Context context, FilePicker filePicker) {
        root = new File(context.getFilesDir(), "ai-rp");
        this.filePicker = filePicker;
    }

    private File scopeDir(String scope) {
        if (!"data".equals(scope) && !"local".equals(scope)) throw new IllegalArgumentException("bad scope");
        File dir = new File(root, scope);
        if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("cannot create storage");
        return dir;
    }

    private String filename(String key) {
        if (key == null || key.length() > 512) throw new IllegalArgumentException("bad key");
        return Base64.encodeToString(key.getBytes(StandardCharsets.UTF_8), Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING) + ".enc";
    }

    @JavascriptInterface
    public synchronized String storageGet(String scope, String key) throws Exception {
        File file = new File(scopeDir(scope), filename(key));
        if (!file.isFile()) return null;
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    @JavascriptInterface
    public synchronized void storageSet(String scope, String key, String value) throws Exception {
        File dir = scopeDir(scope);
        File target = new File(dir, filename(key));
        File temp = new File(dir, target.getName() + ".tmp");
        try (FileOutputStream output = new FileOutputStream(temp, false)) {
            output.write(value.getBytes(StandardCharsets.UTF_8));
            output.getFD().sync();
        }
        if (!temp.renameTo(target)) throw new IllegalStateException("cannot commit storage");
    }

    @JavascriptInterface
    public synchronized void storageRemove(String scope, String key) {
        File file = new File(scopeDir(scope), filename(key));
        if (file.exists() && !file.delete()) throw new IllegalStateException("cannot remove storage");
    }

    @JavascriptInterface
    public synchronized String storageKeys(String scope) throws Exception {
        File[] files = scopeDir(scope).listFiles((dir, name) -> name.endsWith(".enc"));
        ArrayList<String> keys = new ArrayList<>();
        if (files != null) for (File file : files) {
            String encoded = file.getName().substring(0, file.getName().length() - 4);
            byte[] decoded = Base64.decode(encoded, Base64.URL_SAFE | Base64.NO_WRAP);
            keys.add(new String(decoded, StandardCharsets.UTF_8));
        }
        Collections.sort(keys);
        return new JSONArray(keys).toString();
    }

    @JavascriptInterface
    public synchronized void storageClear(String scope) {
        File[] files = scopeDir(scope).listFiles();
        if (files != null) for (File file : files) if (!file.delete()) throw new IllegalStateException("cannot clear storage");
    }

    @JavascriptInterface
    public void pickFileNative(String requestId, String accept) {
        if (filePicker != null) filePicker.open(requestId, accept);
    }
}
