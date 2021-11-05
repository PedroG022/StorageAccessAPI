package pedrog022.storageapi.storageaccess;

import android.content.Context;

import java.io.IOException;

public interface FileProvider {
    String name();

    FileProvider init(Context context);

    String readFile(String filepath) throws IOException;

    void createFile(String folderPath, String filename) throws Exception;

    void createFolder(String folderPath) throws Exception;

    void moveFile(String filepath, String folderPath) throws Exception;

    void deleteFile(String filepath) throws Exception;

    void cleanFolder(String folderPath) throws Exception;

    void renameFile(String filepath, String newName) throws Exception;

    void writeFile(String filepath, String content) throws IOException;

    boolean isFile(String filepath);

    boolean isFolder(String filepath);

    boolean exists(String filePath);

    void copyFile(String originalFilepath, String newFilepath) throws Exception;

    String[] getFolderContent(String folderPath) throws Exception;
}