package com.mku.salmon.samples.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.fs.file.File;
import com.mku.fs.file.IFile;
import com.mku.fs.file.WSFile;
import com.mku.salmon.samples.R;
import com.mku.salmon.samples.samples.DriveSample;
import com.mku.salmon.samples.utils.AndroidFileChooser;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;
import com.mku.salmonfs.drive.AesDrive;

import java.io.IOException;

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
            createDrive();
        });

        openDriveButton = findViewById(R.id.OPEN_DRIVE_BUTTON);
        openDriveButton.setOnClickListener((e) -> {
            openDrive();
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

    private void initialize() {
        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);
    }

    public void createDrive() {
        new Thread(() -> {
            try {
                IFile driveDir = new WSFile(drivePath.getText().toString(),
                        wsURL.getText().toString(),
                        new WSFile.Credentials(wsUserName.getText().toString(),
                                wsPassword.getText().toString()));
                if (!driveDir.exists()) {
                    boolean res = driveDir.mkdir();
                    if(!res) {
                        runOnUiThread(() -> {
                            outputText.append("Could not create directory" + "\n");
                        });
                        return;
                    }
                }
                wsDrive = DriveSample.createDrive(driveDir, password.getText().toString(),
                        (msg) -> {
                            runOnUiThread(() -> {
                                outputText.append(msg + "\n");
                            });
                        });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    outputText.append(e.getMessage() + "\n");
                });
            }
        }).start();
    }

    public void openDrive() {
        new Thread(() -> {
            try {
                IFile driveDir = new WSFile(drivePath.getText().toString(),
                        wsURL.getText().toString(),
                        new WSFile.Credentials(wsUserName.getText().toString(),
                                wsPassword.getText().toString()));
                wsDrive = DriveSample.openDrive(driveDir, password.getText().toString(),
                        (msg) -> {
                            runOnUiThread(() -> {
                                outputText.append(msg + "\n");
                            });
                        });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    outputText.append(e.getMessage() + "\n");
                });
            }
        }).start();
    }

    public void importFiles(IFile[] filesToImport) {
        new Thread(() -> {
            try {
                DriveSample.importFiles(wsDrive, filesToImport,
                        (msg) -> {
                            runOnUiThread(() -> {
                                outputText.append(msg + "\n");
                            });
                        });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    outputText.append(e.getMessage() + "\n");
                });
            }
        }).start();
    }

    public void listFiles() {
        new Thread(() -> {
            try {
                DriveSample.listFiles(wsDrive, (msg) -> {
                    runOnUiThread(() -> {
                        outputText.append(msg + "\n");
                    });
                });
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    outputText.append(e.getMessage() + "\n");
                });
            }
        }).start();
    }

    public void exportFiles(IFile exportDir) {
        new Thread(() -> {
            try {
                DriveSample.exportFiles(wsDrive, exportDir,
                        (msg) -> {
                            runOnUiThread(() -> {
                                outputText.append(msg + "\n");
                            });
                        });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    outputText.append(e.getMessage() + "\n");
                });
            }
        }).start();
    }

    public void closeDrive() {
        DriveSample.closeDrive(wsDrive, (msg) -> {
            runOnUiThread(() -> {
                outputText.append(msg + "\n");
            });
        });
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
                importFiles(filesToImport);
                break;
            case REQUEST_EXPORT_FILES:
                AndroidFileChooser.setUriPermissions(this, data, uri);
                IFile exportDir = AndroidFileChooser.getFile(this, uri.toString(), true);
                exportFiles(exportDir);
                break;
        }
    }
}