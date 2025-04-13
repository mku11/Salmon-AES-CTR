package com.mku.salmon.samples.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.fs.file.IFile;
import com.mku.fs.file.WSFile;
import com.mku.salmon.samples.R;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.samples.utils.AndroidFileChooser;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmonfs.drive.AesDrive;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class WebServiceDriveActivity extends AppCompatActivity {
    public static final int REQUEST_IMPORT_FILES = 1002;
    public static final int REQUEST_EXPORT_FILES = 1003;
    private EditText wsURL;
    private EditText wsUserName;
    private TextInputEditText wsPassword;
    private EditText drivePath;
    private TextInputEditText password;
    private Button createDriveButton;
    private Button openDriveButton;
    private Button importFilesButton;
    private Button listFilesButton;
    private Button exportFilesButton;
    private Button closeDriveButton;
    private EditText outputText;
    private AesDrive wsDrive;

    private static final String defaultPassword = "test123";
    private static final String defaultWsServicePath = "";
    private static final String defaultUserName = "user";
    private static final String defaultWsPassword = "password";
    private static final String defaultDrivePath = "/example_drive_" + System.currentTimeMillis();

    private final Executor executor = Executors.newCachedThreadPool();

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.activity_web_service_drive);

        wsURL = findViewById(R.id.WEB_SERVICE_LOCATION);
        wsURL.setText(defaultWsServicePath);
        wsUserName = findViewById(R.id.WEB_SERVICE_USER);
        wsUserName.setText(defaultUserName);
        wsPassword = findViewById(R.id.WEB_SERVICE_PASSWORD);
        wsPassword.setText(defaultWsPassword);
        drivePath = findViewById(R.id.DRIVE_PATH);
        drivePath.setText(defaultDrivePath);
        password = findViewById(R.id.TEXT_PASSWORD);
        password.setText(defaultPassword);

        createDriveButton = findViewById(R.id.CREATE_DRIVE_BUTTON);
        createDriveButton.setOnClickListener((e) -> {
			executor.execute(() -> {
				createDrive();
			});
        });

        openDriveButton = findViewById(R.id.OPEN_DRIVE_BUTTON);
        openDriveButton.setOnClickListener((e) -> {
			executor.execute(() -> {
				openDrive();
			});
        });

        importFilesButton = findViewById(R.id.IMPORT_FILES_BUTTON);
        importFilesButton.setOnClickListener((e) -> {
            AndroidFileChooser.openFilesystem(this, REQUEST_IMPORT_FILES, false, true);
        });

        listFilesButton = findViewById(R.id.LIST_DRIVE_BUTTON);
        listFilesButton.setOnClickListener((e) -> {
			executor.execute(() -> {
				listFiles();
			});
        });
        exportFilesButton = findViewById(R.id.EXPORT_FILES_BUTTON);
        exportFilesButton.setOnClickListener((e) -> {
            AndroidFileChooser.openFilesystem(this, REQUEST_EXPORT_FILES, true);
        });

        closeDriveButton = findViewById(R.id.CLOSE_DRIVE_BUTTON);
        closeDriveButton.setOnClickListener((e) -> {
			executor.execute(() -> {
				closeDrive();
			});
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
	
	public void clearLog() {
        runOnUiThread(() -> {
            outputText.setText("");
        });
    }

    private void initialize() {
        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);
    }

    public void createDrive() {
        clearLog();
        
            try {
                IFile driveDir = new WSFile(drivePath.getText().toString(),
                        wsURL.getText().toString(),
                        new WSFile.Credentials(wsUserName.getText().toString(),
                                wsPassword.getText().toString()));
                if (!driveDir.exists()) {
                    driveDir.mkdir();
                }
                wsDrive = DriveSample.createDrive(driveDir, password.getText().toString(), this::log);
            } catch (Exception e) {
                e.printStackTrace();
                log(e.getMessage());
            }
        
    }

    public void openDrive() {
        clearLog();
        
            try {
                IFile driveDir = new WSFile(drivePath.getText().toString(),
                        wsURL.getText().toString(),
                        new WSFile.Credentials(wsUserName.getText().toString(),
                                wsPassword.getText().toString()));
                wsDrive = DriveSample.openDrive(driveDir, password.getText().toString(), this::log);
            } catch (Exception e) {
                e.printStackTrace();
                log(e.getMessage());
            }
        
    }

    public void importFiles(IFile[] filesToImport) {
        
            try {
                DriveSample.importFiles(wsDrive, filesToImport, this::log);
            } catch (Exception e) {
                e.printStackTrace();
                log(e.getMessage());
            }
        
    }

    public void listFiles() {
        
            try {
                DriveSample.listFiles(wsDrive, this::log);
            } catch (Exception e) {
                e.printStackTrace();
                log(e.getMessage());
            }
        
    }

    public void exportFiles(IFile exportDir) {
            try {
                DriveSample.exportFiles(wsDrive, exportDir, this::log);
            } catch (Exception e) {
                e.printStackTrace();
                log(e.getMessage());
            }
    }

    public void closeDrive() {
        DriveSample.closeDrive(wsDrive, this::log);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        android.net.Uri uri = data.getData();
        switch (requestCode) {
            case REQUEST_IMPORT_FILES:
                IFile[] filesToImport = AndroidFileChooser.getFiles(this, data);
				executor.execute(() -> {
					importFiles(filesToImport);
				});
                break;
            case REQUEST_EXPORT_FILES:
                AndroidFileChooser.setUriPermissions(this, data, uri);
                IFile exportDir = AndroidFileSystem.getRealFile(uri.toString(), true);
				executor.execute(() -> {
					exportFiles(exportDir);
				});
                break;
        }
    }
}