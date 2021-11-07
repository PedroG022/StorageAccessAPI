package pedrog022.storageapi.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.storage.StorageManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

public class Utils {
    public static void log(String text, Object... arguments) {
        Log.d("dbg", String.format(text, arguments));
    }

    public static void toast(Context context, String text) {
        Toast.makeText(context, text, Toast.LENGTH_SHORT).show();
    }

    public static void showError(Context context, String message) {
        toast(context, String.format("An error occurred: %s", message));
    }

    @RequiresApi(api = Build.VERSION_CODES.Q)
    public static Uri getPathUri(Context context, String path) {

        StorageManager sm = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        Intent intent = sm.getPrimaryStorageVolume().createOpenDocumentTreeIntent();

        String startDir = path.replaceAll("/", "%2F");
        Uri uri = intent.getParcelableExtra("android.provider.extra.INITIAL_URI");

        String scheme = uri.toString();
        scheme = scheme.replace("/root/", "/document/");
        scheme += "%3A" + startDir;

        return Uri.parse(scheme);
    }

    public static Uri getOpenIntentPathUri(String path) {
        String base = "content://com.android.externalstorage.documents/tree/primary%3A";
        path = base + path.replaceAll("/", "%2F");
        return Uri.parse(path);
    }

    public static class Errors {
        public static final String PERMISSIONS_DENIED = "Permissions were denied. Please, access the app's configuration and allow the permissions, otherwise, you won't be able to apply the textures!";
    }
}