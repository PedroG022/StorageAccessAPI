package pedrog022.storageapi.storageaccess;

import static android.app.Activity.RESULT_CANCELED;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import pedrog022.storageapi.utils.Utils;

public class PermissionUtil {
    public PermissionUtil() {

    }

    public void handleApiPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (!Normal.isWritePermissionGranted(context)) {
                AlertDialog dialog = permissionsDialog(context,
                        () -> ActivityCompat.requestPermissions((Activity) context,
                                new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 720)
                        , () -> Utils.toast(context, Utils.Errors.PERMISSIONS_DENIED)).create();
                dialog.show();
            } else {
                permissionsOk();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Scoped.areAllNeededPermissionsOk(context)) {
                Scoped.askForMissingPermissions(context);
            } else {
                permissionsOk();
            }
        } else {
            permissionsOk();
        }
    }

    public static class Normal {
        //For android >= M <= Q
        private static boolean isWritePermissionGranted(Context context) {
            return ContextCompat.checkSelfPermission(context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }

        public static void onActivityResultOverride(Context context, int requestCode, @NonNull int[] grantResults) {
            if (requestCode == 720) {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    permissionsOk();
                } else {
                    Utils.showError(context, Utils.Errors.PERMISSIONS_DENIED);
                }
            }
        }
    }

    public static class Scoped {
        private static final ArrayList<String> requiredStoragePermissions = new ArrayList<>();
        private static final HashMap<String, ActivityResultLauncher<Intent>> permissionsAndLaunchers = new HashMap<>();

        public static void init(Context context, ArrayList<String> paths) {
            if (!requiredStoragePermissions.isEmpty())
                requiredStoragePermissions.clear();

            requiredStoragePermissions.addAll(paths);
            registerLaunchers(context);
        }

        //Android >= Q
        private static ActivityResultLauncher<Intent> registerLauncher(Context context, String permission) {
            return ((AppCompatActivity) context).registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                            android_r_permissions(context, result.getData().getData(), requiredStoragePermissions.indexOf(permission));
                        } else if (result.getResultCode() == RESULT_CANCELED) {
                            Utils.showError(context, Utils.Errors.PERMISSIONS_DENIED);
                        }
                    });
        }

        private static void registerLaunchers(Context context) {
            for (String permission : requiredStoragePermissions) {
                permissionsAndLaunchers.put(permission, registerLauncher(context, permission));
            }
        }

        private static void android_r_permissions(Context context, Uri uri, int code) {
            String granted = Uri.decode(uri.getPath()).substring(14);

            if (!requiredStoragePermissions.contains(granted))
                Utils.toast(context, String.format("The selected path is incorrect! Please try again selecting the right path: %s!", requiredStoragePermissions.get(code)));

            DocumentFile locationDir = DocumentFile.fromTreeUri(context, uri);
            context.grantUriPermission(context.getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            }

            SharedPreferences.Editor editor = context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit();
            editor.putString(requiredStoragePermissions.get(code), Objects.requireNonNull(locationDir).getUri().toString());

            editor.apply();
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        private static boolean areAllNeededPermissionsOk(Context context) {
            boolean allOk = true;

            for (String permission : requiredStoragePermissions) {

                if (isFilePermissionGranted(context, permission)) {
                    allOk = false;
                }
            }

            return allOk;
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        private static boolean isFilePermissionGranted(Context context, String path) {

            ContentResolver resolver = context.getContentResolver();
            List<UriPermission> permissions = resolver.getPersistedUriPermissions();

            boolean found_permission = false;

            for (UriPermission permission : permissions) {
                String decodedUri = Uri.decode(permission.getUri().toString());

                if (decodedUri.contains(path)) {
                    found_permission = true;
                    break;
                }
            }

            return found_permission;
        }

        private static boolean dialogShown = false;

        @RequiresApi(api = Build.VERSION_CODES.Q)
        private static void askForMissingPermissions(Context context) {
            int missingPermissions = 0;

            for (String permission : requiredStoragePermissions) {
                if (!isFilePermissionGranted(context, permission)) {
                    missingPermissions++;
                }
            }

            if (missingPermissions > 0) {
                if (!dialogShown) {
                    AlertDialog.Builder permissions_dialog = permissionsDialog(context, () -> {
                        for (String permission : requiredStoragePermissions) {
                            if (!isFilePermissionGranted(context, permission)) {
                                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                                intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, Utils.getPathUri(context, permission));
                                permissionsAndLaunchers.get(permission).launch(intent);
                            }
                        }

                        dialogShown = true;
                    }, () -> Utils.toast(context, Utils.Errors.PERMISSIONS_DENIED));

                    permissions_dialog.show();
                }
            }
        }
    }

    private static AlertDialog.Builder permissionsDialog(Context context, Runnable positive, Runnable negative) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);

        builder.setTitle("Storage permissions needed!");
        builder.setMessage("Storage permissions needed to work!");
        builder.setCancelable(false);
        builder.setPositiveButton("Allow", (dialog, which) -> positive.run());
        builder.setNegativeButton("Deny", (dialog, which) -> negative.run());

        return builder;
    }

    private static void permissionsOk() {
        Log.d("dbg", "Permissions are ok.");
    }
}