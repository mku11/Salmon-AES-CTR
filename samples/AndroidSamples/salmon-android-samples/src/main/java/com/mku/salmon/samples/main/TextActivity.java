package com.mku.salmon.samples.main;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.mku.android.fs.file.AndroidFileSystem;
import com.mku.salmon.samples.R;
import com.mku.salmon.samples.samples.SamplesCommon;
import com.mku.salmon.samples.samples.TextSample;
import com.mku.salmon.streams.AesStream;
import com.mku.salmon.streams.ProviderType;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class TextActivity extends AppCompatActivity {
    private TextInputEditText password;
    private Button encryptButton;
    private Button decryptButton;

    private EditText plainText;
    private EditText encryptedText;
    private EditText outputText;

    private static final String defaultPassword = "test123";
    private static final String text = "This is a plain text that will be encrypted";

    private byte[] key;
	
	private final Executor executor = Executors.newCachedThreadPool();

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_text);

        password = findViewById(R.id.TEXT_PASSWORD);

        encryptButton = findViewById(R.id.ENCRYPT_BUTTON);
        encryptButton.setOnClickListener((e) -> {
			executor.execute(() -> {
				encryptText();
			});
        });

        decryptButton = findViewById(R.id.DECRYPT_BUTTON);
        decryptButton.setOnClickListener((e) -> {
			executor.execute(() -> {
				decryptText();
			});
        });

        plainText = findViewById(R.id.PLAIN_TEXT);
        encryptedText = findViewById(R.id.ENCRYPTED_TEXT);
        outputText = findViewById(R.id.OUTPUT_TEXT);

        password.setText(defaultPassword);
        plainText.setText(text);
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
	
	public void clearEncText() {
        runOnUiThread(() -> {
            encryptedText.setText("");
        });
    }

    private void initialize() {
        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);
    }

    private void encryptText() {
		clearEncText();
        clearLog();

        // generate an encryption key from the text password
        key = SamplesCommon.getKeyFromPassword(password.getText().toString());

        try {
            String encText = TextSample.encryptText(text, key);
            runOnUiThread(()->{
                encryptedText.setText(encText);
            });
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }

    private void decryptText() {
        try {
            String decText = TextSample.decryptText(encryptedText.getText().toString(), key);
            log("Decrypted Text: " + "\n" + decText);
        } catch (Exception e) {
            e.printStackTrace();
            log(e.getMessage());
        }
    }
}