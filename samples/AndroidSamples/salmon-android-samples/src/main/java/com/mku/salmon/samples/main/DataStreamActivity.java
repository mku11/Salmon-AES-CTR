package com.mku.salmon.samples.main;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.convert.BitConverter;
import com.mku.salmon.Generator;
import com.mku.salmon.samples.R;
import com.mku.salmon.samples.samples.DataStreamSample;
import com.mku.salmon.samples.samples.SamplesCommon;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.ProviderType;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class DataStreamActivity extends AppCompatActivity {
    private TextInputEditText password;
    private Spinner dataSize;
    private EditText outputText;

    private Button encryptButton;
    private Button decryptButton;

    private static final String defaultPassword = "test123";

    private byte[] key;
    private byte[] data;
    private byte[] encData;
    private byte[] nonce;

    private final Executor executor = Executors.newCachedThreadPool();

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);

        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);

        setContentView(R.layout.activity_data_stream);

        password = findViewById(R.id.TEXT_PASSWORD);
        dataSize = findViewById(R.id.DATA_SIZE);

        encryptButton = findViewById(R.id.ENCRYPT_BUTTON);
        encryptButton.setOnClickListener((e) -> {
			executor.execute(() -> {
				encryptDataStream();
			});
        });

        decryptButton = findViewById(R.id.DECRYPT_BUTTON);
        decryptButton.setOnClickListener((e) -> {
			executor.execute(() -> {
				decryptDataStream();
			});
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
	
	public void clearLog() {
        runOnUiThread(() -> {
            outputText.setText("");
        });
    }

    public void initialize() {
        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);
    }

    public void encryptDataStream() {
        clearLog();

        // generate a key
        log("generating keys and random data...");
        key = SamplesCommon.getKeyFromPassword(password.getText().toString());

        // Always request a new random secure nonce!
        // if you want to you can embed the nonce in the header data
        // see Encryptor implementation
        nonce = Generator.getSecureRandomBytes(8); // 64 bit nonce
        log("Created nonce: " + BitConverter.toHex(nonce));

        // generate random data
        data = SamplesCommon.generateRandomData(
                Integer.parseInt((String) dataSize.getSelectedItem()) * 1024 * 1024);

        try {
            log("starting encryption...");
            encData = DataStreamSample.encryptDataStream(data, key, nonce, this::log);
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }

    public void decryptDataStream() {

        try {
            log("starting decryption...");
            byte[] decData = DataStreamSample.decryptDataStream(encData, key, nonce, this::log);
            log("done");
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }

    }
}
