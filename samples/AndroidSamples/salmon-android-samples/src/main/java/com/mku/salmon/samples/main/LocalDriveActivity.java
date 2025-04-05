package com.mku.salmon.samples.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.fs.file.File;
import com.mku.fs.file.IFile;
import com.mku.salmon.samples.R;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.samples.utils.AndroidFileChooser;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.drive.AesDrive;

import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class LocalDriveActivity extends AppCompatActivity {
    public static final int REQUEST_OPEN_DRIVE = 1000;
    public static final int REQUEST_CREATE_DRIVE = 1001;
    public static final int REQUEST_IMPORT_FILES = 1002;
    public static final int REQUEST_EXPORT_FILES = 1003;
    private TextInputEditText password;
    private Button createDriveButton;
    private Button openDriveButton;
    private Button importFilesButton;
    private Button listFilesButton;
    private Button exportFilesButton;
    private Button closeDriveButton;
    private EditText outputText;
    private AesDrive localDrive;
    private int threads = 1;
    private static final String defaultPassword = "test123";

    private final Executor executor = Executors.newCachedThreadPool();

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.activity_local_drive);

        password = findViewById(R.id.TEXT_PASSWORD);

        createDriveButton = findViewById(R.id.CREATE_DRIVE_BUTTON);
        createDriveButton.setOnClickListener((e) -> {
            AndroidFileChooser.openFilesystem(this, REQUEST_CREATE_DRIVE, true);
        });

        openDriveButton = findViewById(R.id.OPEN_DRIVE_BUTTON);
        openDriveButton.setOnClickListener((e) -> {
            AndroidFileChooser.openFilesystem(this, REQUEST_OPEN_DRIVE, true);
        });

        importFilesButton = findViewById(R.id.IMPORT_FILES_BUTTON);
        importFilesButton.setOnClickListener((e) -> {
            AndroidFileChooser.openFilesystem(this, REQUEST_IMPORT_FILES, false, true);
        });

        listFilesButton = findViewById(R.id.LIST_DRIVE_BUTTON);
        listFilesButton.setOnClickListener((e) -> {
            listFiles();
        });
        exportFilesButton = findViewById(R.id.EXPORT_FILES_BUTTON);
        exportFilesButton.setOnClickListener((e) -> {
            AndroidFileChooser.openFilesystem(this, REQUEST_EXPORT_FILES, true);
        });

        closeDriveButton = findViewById(R.id.CLOSE_DRIVE_BUTTON);
        closeDriveButton.setOnClickListener((e) -> {
            closeDrive();
        });

        outputText = findViewById(R.id.OUTPUT_TEXT);
        password.setText(defaultPassword);

        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);

        initialize();
    }

    public void log(String msg) {
        runOnUiThread(() -> {
            outputText.append(msg + "\n");
        });
    }

    private void initialize() {
        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);
    }

    public void createDrive(IFile driveDir) {
        outputText.setText("");
        executor.execute(() -> {
            try {
                localDrive = DriveSample.createDrive(driveDir, password.getText().toString(), this::log);
            } catch (Exception e) {
                e.printStackTrace();
                log(e.getMessage());
            }
        });
    }

    public void openDrive(IFile dir) {
        outputText.setText("");
        executor.execute(() -> {
            try {
                localDrive = DriveSample.openDrive(dir, password.getText().toString(), this::log);
            } catch (Exception e) {
                e.printStackTrace();
                log(e.getMessage());
            }
        });
    }

    public void importFiles(IFile[] filesToImport) {
        try {
            DriveSample.importFiles(localDrive, filesToImport, threads, this::log);
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }

    public void listFiles() {
        try {
            DriveSample.listFiles(localDrive, this::log);
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }

    public void exportFiles(IFile exportDir) {
        try {
            DriveSample.exportFiles(localDrive, exportDir, threads, this::log);
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }

    private void closeDrive() {
        DriveSample.closeDrive(localDrive, this::log);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        android.net.Uri uri = data.getData();
        AndroidFileChooser.setUriPermissions(this, data, uri);
        switch (requestCode) {
            case REQUEST_OPEN_DRIVE:
                IFile driveDir = AndroidFileChooser.getFile(this, uri.toString(), true);
                openDrive(driveDir);
                break;
            case REQUEST_CREATE_DRIVE:
                IFile newDriveDir = AndroidFileChooser.getFile(this, uri.toString(), true);
                createDrive(newDriveDir);
                break;
            case REQUEST_IMPORT_FILES:
                IFile[] filesToImport = AndroidFileChooser.getFiles(this, data);
                importFiles(filesToImport);
                break;
            case REQUEST_EXPORT_FILES:
                IFile exportDir = AndroidFileChooser.getFile(this, uri.toString(), true);
                exportFiles(exportDir);
                break;
        }
    }
}