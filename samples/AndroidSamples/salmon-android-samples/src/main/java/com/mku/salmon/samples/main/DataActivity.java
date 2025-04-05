package com.mku.salmon.samples.main;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.salmon.Generator;
import com.mku.salmon.samples.R;
import com.mku.salmon.samples.samples.*;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.ProviderType;

import java.io.IOException;

public class DataActivity extends AppCompatActivity {
    private TextInputEditText password;
    private Spinner dataSize;
    private Spinner threads;
    private Spinner integrity;
    private EditText outputText;

    private Button encryptButton;
    private Button decryptButton;

    private static final String defaultPassword = "test123";
    private byte[] key;
    byte[] integrityKey = null;
    private byte[] data;
    private byte[] encData;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        setContentView(R.layout.activity_data);

        password = findViewById(R.id.TEXT_PASSWORD);
        dataSize = findViewById(R.id.DATA_SIZE);
        threads = findViewById(R.id.THREADS);
        integrity = findViewById(R.id.DATA_INTEGRITY);

        encryptButton = findViewById(R.id.ENCRYPT_BUTTON);
        encryptButton.setOnClickListener((e) -> {
            encryptData();
        });

        decryptButton = findViewById(R.id.DECRYPT_BUTTON);
        decryptButton.setOnClickListener((e) -> {
            decryptData();
        });

        outputText = findViewById(R.id.OUTPUT_TEXT);
        password.setText(defaultPassword);

        initialize();
    }

    public void log(String msg) {
        runOnUiThread(() -> {
            outputText.append(msg + "\n");
        });
    }

    public void initialize() {
        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);
    }

    public void encryptData() {
        outputText.setText("");

        // generate an encryption key from the text password
        log("generating keys and random data...");
        key = SamplesCommon.getKeyFromPassword(password.getText().toString());

        if ((integrity.getSelectedItem()).equals("Enable")) {
            // generate an HMAC key
            integrityKey = Generator.getSecureRandomBytes(32);
        }

        // generate random data
        data = SamplesCommon.generateRandomData(
                Integer.parseInt((String) dataSize.getSelectedItem()) * 1024 * 1024);

        try {
            log("starting encryption...");
            encData = DataSample.encryptData(data, key, integrityKey,
                    Integer.parseInt((String) threads.getSelectedItem()), (msg) -> {
                        log(msg);
                    });
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }

    public void decryptData() {
        log("starting decryption...");
        try {
            byte[] decData = DataSample.decryptData(encData, key, integrityKey,
                    Integer.parseInt((String) threads.getSelectedItem()), (msg) -> {
                        log(msg);
                    });
            log("done");
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }

    }
}