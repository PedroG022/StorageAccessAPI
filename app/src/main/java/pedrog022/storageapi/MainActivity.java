package pedrog022.storageapi;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;

import java.io.IOException;
import java.util.Arrays;

import pedrog022.storageapi.databinding.ActivityMainBinding;
import pedrog022.storageapi.storageaccess.FileProvider;
import pedrog022.storageapi.storageaccess.StorageAccess;

public class MainActivity extends AppCompatActivity {
    private FileProvider fileProvider;
    private String filepath;
    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        filepath = "StorageAccess";

        StorageAccess storageAccess =
                new StorageAccess(this, new String[]{filepath});

        storageAccess.init();

        fileProvider = storageAccess.getFileProvider();

        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        getInfo();
        binding.button.setOnClickListener(v -> executeTests());
    }

    public void append(String text) {
        binding.textLog.setText(binding.textLog.getText().toString()
                .concat("\n").concat(text));
    }

    public void appendError(Exception e) {
        append(String.format("An error occurrred! %s. Check the logs.", e.getMessage()));
        e.printStackTrace();
    }

    public void executeTests() {
        //TODO: The \n problem happens while reading the file
        //Folder exists
        append("Verifying folder existence and type");

        if (fileProvider.exists(filepath)) {
            append("Path exists, verifying type");

            if (fileProvider.isFolder(filepath))
                append("Ok, path is a directory");
            else append("Path is a file!");
        } else {
            append("Folder doesn't exists, trying to create it");

            try {
                fileProvider.createFolder(filepath);
                append("Folder created!");
            } catch (Exception e) {
                appendError(e);
            }
        }

        //List contents
        try {
            append("Listing folder content: " + Arrays.toString(fileProvider.getFolderContent(filepath)));
        } catch (Exception e) {
            appendError(e);
        }

        //Create a file
        append("Creating a test file");
        try {
            fileProvider.createFile(filepath, "Test.txt");

            append("Testing file existence");
            if (fileProvider.exists(filepath + "/Test.txt"))
                append("File created successfully!");
            else append("Error creating file");
        } catch (Exception e) {
            appendError(e);
        }

        //TODO: @SDKCase2: "\n" is being added to the end of the file
        //Write file
        append("Writing to the created file");
        try {
            fileProvider.writeFile(filepath + "/Test.txt", "Hello World");
            append("Reading file content");
            append("Content: " + fileProvider.readFile(filepath + "/Test.txt"));
        } catch (IOException e) {
            appendError(e);
        }

        //TODO: @SDKCase2: File is duplicated if it already exists
        //TODO: @SDKCase2: Copied file have one exceeding byte (?)
        //TODO: @SDKCase0: #FIXED (Caused by the base path not being added to the file path)  Destination directory "StorageAccess" cannot be created
        //Copy file
        append("Copying file");
        try {
            fileProvider.copyFile(filepath + "/Test.txt", filepath + "/TestCopy.txt");
            append("Reading copy file content");
            append("Content: " + fileProvider.readFile(filepath + "/TestCopy.txt"));
        } catch (Exception e) {
            appendError(e);
        }

        //Create a folder
        append("Creating a folder");
        try {
            fileProvider.createFolder(filepath + "/Folder");
            append("Testing new folder existence");
            if (fileProvider.exists(filepath + "/Folder"))
                append("Subfolder created successfully!");
            else append("Error creating folder!");
        } catch (Exception e) {
            appendError(e);
        }

        //Rename file
        append("Renaming copy file");
        try {
            fileProvider.renameFile(filepath + "/TestCopy.txt", "TestRenamed.txt");

            append("Testing renamed file existence");
            if (fileProvider.exists(filepath + "/TestRenamed.txt"))
                append("File renamed successfully!");
            else append("Error renaming file");

            append("Reading file content");
            append("Content: " + fileProvider.readFile(filepath + "/TestRenamed.txt"));

        } catch (Exception e) {
            appendError(e);
        }

    }


    public void getInfo() {
        String infoText =
                "-------------------------------------------------------\n" +
                        "SDK: %s\n" +
                        "SDK Case: %s\n" +
                        "Target: %s\n" +
                        "-------------------------------------------------------";

        infoText = String.format(infoText,
                Build.VERSION.SDK_INT,
                StorageAccess.SDK_CASE(),
                filepath);

        binding.textInfo.setText(infoText);
    }
}