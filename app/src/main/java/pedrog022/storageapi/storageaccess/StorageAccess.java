package pedrog022.storageapi.storageaccess;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.documentfile.provider.DocumentFile;

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import pedrog022.storageapi.utils.Utils;

public class StorageAccess {
    private static final String base = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
    private static final int SDK = Build.VERSION.SDK_INT;
    private final Context context;
    private final PermissionUtil permissionUtil = new PermissionUtil();
    private final String[] directories;

    /*--------------------------------------------------------------------------------------------*/
    /*--------------------------------------------------------------------------------------------*/
    /*--------------------------------------------------------------------------------------------*/

    public static int SDK_CASE() {
        if (SDK >= Build.VERSION_CODES.M && SDK < Build.VERSION_CODES.Q) {
            return 1;
        } else if (SDK >= Build.VERSION_CODES.Q) {
            return 2;
        } else {
            return 0;
        }
    }

    public StorageAccess(Context context, String[] directories) {
        this.context = context;
        this.directories = directories;
    }

    public void init() {
        ArrayList<String> sanitizedEntriesList = new ArrayList<>();

        for (String entry : directories) {
            String newEntry = entry;

            if (entry.startsWith("/"))
                newEntry = newEntry.substring(1);

            if (entry.endsWith("/"))
                newEntry = newEntry.substring(0, newEntry.length() - 1);

            sanitizedEntriesList.add(newEntry);
        }

        if (SDK >= Build.VERSION_CODES.Q) {
            PermissionUtil.Scoped.init(context, sanitizedEntriesList);
        }

        permissionUtil.handleApiPermissions(context);
    }

    public FileProvider getFileProvider() {
        switch (SDK_CASE()) {
            case 1:
                return new LollipopFiles(context);
            case 2:
                return new QFiles().init(context);
            case 0:
            default:
                return new KitKatFiles();
        }
    }

    public void downloadFile(String fileUrl, String destinationPath, boolean overwriteIfExists,
                             Runnable onComplete, Runnable onError) {
        FileProvider provider = getFileProvider();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());

        String fileName = destinationPath.substring(destinationPath.lastIndexOf("/") + 1);
        String folder = destinationPath.substring(0, destinationPath.lastIndexOf("/"));

        executor.execute(() -> {
            try {
                if (provider.exists(destinationPath))
                    if (overwriteIfExists)
                        provider.deleteFile(destinationPath);
                    else throw new Exception("Target download file already exits!");

                byte[] fileBytes = getBytes(fileUrl);

                provider.createFile(folder, fileName);
                provider.writeFile(destinationPath, new String(fileBytes));
                handler.post(onComplete);
            } catch (Exception e) {
                Utils.log("An download error occurred");
                e.printStackTrace();
                handler.post(onError);
            }
        });
    }

    private static byte[] getBytes(String url) throws IOException {
        return Jsoup.connect(url)
                .maxBodySize(0)
                .ignoreContentType(true)
                .execute()
                .bodyAsBytes();
    }

    /*--------------------------------------------------------------------------------------------*/
    /*--------------------------------------------------------------------------------------------*/
    /*--------------------------------------------------------------------------------------------*/

    public static class LollipopFiles extends KitKatFiles {
        private final Context context;

        @Override
        public String name() {
            return "LollipopFiles";
        }

        public LollipopFiles(Context context) {
            this.context = context;
        }

        public void onRequestPermissionResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
            PermissionUtil.Normal.onActivityResultOverride(context, requestCode, grantResults);
        }
    }

    public static class QFiles implements FileProvider {
        private Context context;
        private final KitKatFiles wrapper = new KitKatFiles();

        private DocumentFile file(String filePath) {
            filePath = sanitizePath(filePath);
            Uri uri = Utils.getOpenIntentPathUri(filePath);
            return DocumentFile.fromTreeUri(context, uri);
        }

        private String sanitizePath(String path) {
            if (path.startsWith("/"))
                path = path.substring(1);

            if (path.endsWith("/"))
                path = path.substring(0, path.lastIndexOf("/"));

            return path;
        }

        private DocumentFile findFileOnFolder(String filepath) {
            String upperFolder = filepath.substring(0, filepath.lastIndexOf("/"));
            String targetFile = filepath.substring(filepath.lastIndexOf("/") + 1);

            DocumentFile folder = file(upperFolder);

            return folder.findFile(targetFile);
        }

        /*--------------------------------------------------------------------------------------------*/
        /*--------------------------------------------------------------------------------------------*/
        /*--------------------------------------------------------------------------------------------*/

        @Override
        public String name() {
            return "QFiles";
        }

        @Override
        public FileProvider init(Context context) {
            this.context = context;
            return this;
        }

        @Override
        public String readFile(String filepath) throws IOException {
            DocumentFile file = findFileOnFolder(filepath);

            if (findFileOnFolder(filepath) == null)
                throw new FileNotFoundException("File to be read was not found");

            ParcelFileDescriptor pfd = context.getContentResolver()
                    .openFileDescriptor(file.getUri(), "r");

            byte[] data = new byte[(int) pfd.getStatSize()];

            FileDescriptor fd = pfd.getFileDescriptor();
            FileInputStream fileStream = new FileInputStream(fd);
            fileStream.read(data);

            return new String(data);
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void createFolder(String folderPath) throws Exception {
            folderPath = sanitizePath(folderPath);

            String upperFolderPath = folderPath.substring(0, folderPath.lastIndexOf("/"));
            String targetFolder = folderPath.substring(folderPath.lastIndexOf("/") + 1);

            DocumentFile upperFolder = file(upperFolderPath);

            if (upperFolder != null) {
                if (upperFolder.findFile(targetFolder) != null)
                    throw new FileAlreadyExistsException("Folder to be created already exists!");
                else
                    upperFolder.createDirectory(targetFolder);
            } else
                throw new Exception(String.format("The upper folder (%s) is null!", upperFolderPath));
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void createFile(String folderPath, String filename) throws Exception {
            DocumentFile folder = file(folderPath);

            if (folder.findFile(filename) != null)
                throw new FileAlreadyExistsException("Target file already exists!");

            folder.createFile("text", filename);
        }

        @Override
        public String[] getFolderContent(String folderPath) throws Exception {
            DocumentFile folder = file(folderPath);

            if (folder == null)
                throw new Exception(String.format("The upper folder (%s) is null!", folderPath));

            ArrayList<String> filenames = new ArrayList<>();

            DocumentFile[] files = folder.listFiles();
            for (DocumentFile file : files) {
                filenames.add(file.getName());
            }

            return filenames.toArray(new String[0]);
        }

        @Override
        public void deleteFile(String filepath) throws Exception {
            DocumentFile file = findFileOnFolder(filepath);

            if (file != null) {
                file.delete();
            } else throw new FileNotFoundException("Unable to delete file: File not found!");
        }

        @Override
        public void cleanFolder(String folderPath) throws Exception {
            throw new Exception("Not yet implemented or unnecessary!");
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void renameFile(String filepath, String newName) throws Exception {
            DocumentFile file = findFileOnFolder(filepath);

            String folder = filepath.substring(0, filepath.lastIndexOf("/"));

            DocumentFile rootFolder = file(folder);
            if (rootFolder.findFile(newName) != null)
                throw new FileAlreadyExistsException("File to be renamed already exists!");

            file.renameTo(newName);
        }

        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void writeFile(String filepath, String content) throws IOException {
            DocumentFile targetFile = findFileOnFolder(filepath);

            ContentResolver resolver = context.getContentResolver();

            OutputStream outputStream = resolver.openOutputStream(targetFile.getUri());
            outputStream.write(content.getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            outputStream.close();
        }

        @Override
        public boolean isFile(String filepath) {
            return wrapper.isFile(filepath);
        }

        @Override
        public boolean isFolder(String filepath) {
            return wrapper.isFolder(filepath);
        }

        @Override
        public boolean exists(String filePath) {
            return wrapper.exists(filePath);
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void copyFile(String originalFilepath, String newFilepath) throws Exception {
            String targetPath = newFilepath.substring(0, newFilepath.lastIndexOf("/"));
            String newFilename = newFilepath.substring(newFilepath.lastIndexOf("/") + 1);

            DocumentFile targetFolder = file(targetPath);

            if (targetFolder.findFile(newFilename) != null)
                throw new FileAlreadyExistsException("Target copy file already exists!");

            targetFolder.createFile("text", newFilename);
            writeFile(newFilepath, readFile(originalFilepath));
        }
    }

    private static class KitKatFiles implements FileProvider {
        @Override
        public String name() {
            return "KitKatFiles";
        }

        @Override
        public FileProvider init(Context context) {
            return this;
        }

        @Override
        public String readFile(String filepath) throws IOException {
            File targetFile = new File(base + filepath);

            if (!targetFile.exists())
                throw new FileNotFoundException();

            return FileUtils.readFileToString(targetFile, "UTF-8");
        }

        @Override
        public void createFile(String folderPath, String filename) throws Exception {
            File targetFile = new File(base + folderPath + "/" + filename);

            if (!targetFile.createNewFile())
                throw new Exception("File already exists!");
        }

        @Override
        public void createFolder(String folderPath) throws Exception {
            File targetFolder = new File(base + folderPath);

            if (targetFolder.exists())
                throw new Exception("Folder already exists!");

            if (!targetFolder.mkdir())
                throw new Exception("Error creating folder!");
        }

        @Override
        public void deleteFile(String filepath) throws Exception {
            File targetFile = new File(base + filepath);

            if (!targetFile.exists())
                throw new FileNotFoundException();

            if (targetFile.isDirectory()) {
                FileUtils.deleteDirectory(targetFile);
            } else if (!targetFile.delete())
                throw new Exception("Unable to delete file due to an unknown reason!");
        }

        @Override
        public void cleanFolder(String folderPath) throws Exception {
            File targetFile = new File(base + folderPath);

            if (!targetFile.exists())
                throw new FileNotFoundException();

            if (targetFile.isDirectory()) {
                File[] folderContent = targetFile.listFiles();

                if (folderContent != null)
                    for (File file : folderContent) {
                        if (file.isDirectory())
                            FileUtils.deleteDirectory(file);
                        else if (!file.delete())
                            throw new Exception("Unable to delete file due to an unknown reason!");
                    }
            } else throw new Exception("Selected target is a file!");
        }

        @Override
        public void renameFile(String filepath, String newName) throws Exception {
            File originalFile = new File(base + filepath);
            String originalFilePath = filepath.substring(0, filepath.lastIndexOf("/") + 1);

            if (!originalFile.exists())
                throw new FileNotFoundException();

            File newFile = new File(base + originalFilePath.concat(newName));

            if (!originalFile.renameTo(newFile))
                throw new Exception("Unable to rename file due to an unknown reason!");
        }

        @Override
        public void writeFile(String filepath, String content) throws IOException {
            File targetFile = new File(base + filepath);

            if (!targetFile.exists())
                throw new FileNotFoundException();

            FileUtils.writeByteArrayToFile(targetFile, content.getBytes("UTF-8"));
        }

        //Works on SDK 30
        @Override
        public boolean isFile(String filepath) {
            File targetFile = new File(base + filepath);
            return targetFile.isFile();
        }

        //Works on SDK 30
        @Override
        public boolean isFolder(String filepath) {
            File targetFile = new File(base + filepath);
            return targetFile.isDirectory();
        }

        //Works on SDK 30
        @Override
        public boolean exists(String filepath) {
            File targetFile = new File(base + filepath);
            return targetFile.exists();
        }

        @Override
        public void copyFile(String originalFilepath, String newFilepath) throws Exception {
            File originalFile = new File(base + originalFilepath);
            File newFile = new File(base + newFilepath);

            if (!originalFile.exists())
                throw new FileNotFoundException();

            if (newFile.exists())
                throw new Exception("Target file already exists!");

            FileUtils.copyFile(originalFile, newFile);
        }

        public void moveFile(String filepath, String folderPath) throws Exception {
            File originalFile = new File(base + filepath);
            File targetDirectory = new File(base + folderPath);

            if (!originalFile.exists())
                throw new FileNotFoundException("Original file not found!");

            if (!targetDirectory.exists())
                throw new FileNotFoundException("Target directory not found!");

            if (!targetDirectory.isDirectory())
                throw new Exception("Target directory is actually a file!");

            FileUtils.moveToDirectory(originalFile, targetDirectory, false);
        }

        @Override
        public String[] getFolderContent(String folderPath) throws Exception {
            File folder = new File(base + folderPath);

            if (!folder.exists())
                throw new FileNotFoundException();

            if (folder.isFile())
                throw new Exception("The selected path is a file!");

            return folder.list();
        }
    }

}