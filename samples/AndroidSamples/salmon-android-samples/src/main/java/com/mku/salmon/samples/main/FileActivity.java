package com.mku.salmon.samples.main;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.fs.file.IFile;
import com.mku.salmon.Generator;
import com.mku.salmon.samples.R;
import com.mku.salmon.samples.samples.FileSample;
import com.mku.salmon.samples.samples.SamplesCommon;
import com.mku.salmon.samples.utils.AndroidFileChooser;
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;

import java.io.IOException;


public class FileActivity extends AppCompatActivity {
    private static final int REQUEST_SAVE_FILE = 1000;
    private static final int REQUEST_LOAD_FILE = 1001;
    private TextInputEditText password;
    private EditText plainText;
    private EditText decryptedText;
    private EditText outputText;
    private Button saveButton;
    private Button loadButton;

    private static final String defaultPassword = "test123";
    private String text = "This is a plain text that will be encrypted";

    private byte[] key;
    byte[] integrityKey = null;


    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.activity_file);

        password = findViewById(R.id.TEXT_PASSWORD);

        saveButton = findViewById(R.id.SAVE_BUTTON);
        saveButton.setOnClickListener((e) -> {
            AndroidFileChooser.openFilesystem(this, REQUEST_SAVE_FILE, true);
        });

        loadButton = findViewById(R.id.LOAD_BUTTON);
        loadButton.setOnClickListener((e) -> {
            AndroidFileChooser.openFilesystem(this, REQUEST_LOAD_FILE, false);
        });

        plainText = findViewById(R.id.PLAIN_TEXT);
        plainText.setText(text);
        decryptedText = findViewById(R.id.DECRYPTED_TEXT);
        outputText = findViewById(R.id.OUTPUT_TEXT);
        password.setText(defaultPassword);

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

    public void saveFile(IFile dir) {
        boolean integrity = true;

        outputText.setText("");
        // generate an encryption key from the text password
        key = SamplesCommon.getKeyFromPassword(password.getText().toString());

        // enable integrity (optional)
        byte[] integrityKey = null;
        if (integrity) {
            // generate an HMAC key
            integrityKey = Generator.getSecureRandomBytes(32);
        }

        String filename = "encrypted_data.dat";
        IFile file = dir.getChild(filename);
        if (file.exists()) {
            file.delete();
            file = dir.getChild(filename);
        }

        try {
            FileSample.encryptTextToFile(plainText.getText().toString(), key, integrityKey, file,
                    (msg) -> {
                        log(msg);
                    });
            log("file saved");
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }

    public void loadFile(IFile file) {
        try {
            String decText = FileSample.decryptTextFromFile(key, integrityKey, file, (msg) -> {
                log(msg);
            });
            log("file loaded");
            decryptedText.setText(decText);
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (data == null)
            return;
        android.net.Uri uri = data.getData();
        AndroidFileChooser.setUriPermissions(this, data, uri);
        switch (requestCode) {
            case REQUEST_SAVE_FILE:
                IFile saveDir = AndroidFileChooser.getFile(this, uri.toString(), true);
                saveFile(saveDir);
                break;
            case REQUEST_LOAD_FILE:
                IFile filesToImport = AndroidFileChooser.getFiles(this, data)[0];
                loadFile(filesToImport);
                break;
        }
    }
}