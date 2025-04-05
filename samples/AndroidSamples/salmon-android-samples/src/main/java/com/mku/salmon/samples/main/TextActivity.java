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
import com.mku.salmon.streams.ProviderType;
import com.mku.salmon.streams.AesStream;

import java.io.IOException;

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

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        setContentView(R.layout.activity_text);

        password = findViewById(R.id.TEXT_PASSWORD);

        encryptButton = findViewById(R.id.ENCRYPT_BUTTON);
        encryptButton.setOnClickListener((e) -> {
            encryptText();
        });

        decryptButton = findViewById(R.id.DECRYPT_BUTTON);
        decryptButton.setOnClickListener((e) -> {
            decryptText();
        });

        plainText = findViewById(R.id.PLAIN_TEXT);
        encryptedText = findViewById(R.id.ENCRYPTED_TEXT);
        outputText = findViewById(R.id.OUTPUT_TEXT);

        password.setText(defaultPassword);
        plainText.setText(text);
        initialize();
    }

    private void initialize() {
        AndroidFileSystem.initialize(this);
        AesStream.setAesProviderType(ProviderType.Default);
    }

    private void encryptText() {
        encryptedText.setText("");
        outputText.setText("");

        // generate an encryption key from the text password
        key = SamplesCommon.getKeyFromPassword(password.getText().toString());

        try {
            String encText = TextSample.encryptText(text, key);
            encryptedText.setText(encText);
        } catch (IOException e) {
            e.printStackTrace();
            outputText.append(e.getMessage() + "\n");
        }
    }

    private void decryptText() {
        try {
            String decText = TextSample.decryptText(encryptedText.getText().toString(), key);
            outputText.append("Decrypted Text: " + "\n" + decText + "\n");
        } catch (IOException e) {
            e.printStackTrace();
            outputText.append(e.getMessage() + "\n");
        }
    }
}