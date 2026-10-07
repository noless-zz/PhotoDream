package com.noam.photodream.onedrive;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Small encrypted key/value store for the OneDrive tokens.
 * The AES key lives in the Android Keystore and never leaves the phone,
 * so a copied prefs file (e.g. from a backup) cannot be read.
 */
public final class SecureStore {

    private static final String KEYSTORE = "AndroidKeyStore";

    private final SharedPreferences sp;
    private final String alias;

    /** The OneDrive token store. */
    SecureStore(Context context) {
        this(context, "onedrive_secure", "photodream_onedrive");
    }

    /** A separate store (own prefs file and Keystore key), e.g. for the AI service keys. */
    public SecureStore(Context context, String file, String alias) {
        sp = context.getApplicationContext().getSharedPreferences(file, Context.MODE_PRIVATE);
        this.alias = alias;
    }

    public void put(String key, String value) {
        if (value == null) {
            sp.edit().remove(key).apply();
            return;
        }
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] ct = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
            String packed = Base64.encodeToString(c.getIV(), Base64.NO_WRAP) + ":"
                    + Base64.encodeToString(ct, Base64.NO_WRAP);
            sp.edit().putString(key, packed).apply();
        } catch (Exception e) {
            throw new IllegalStateException("Could not encrypt token", e);
        }
    }

    /** @return the value, or null if missing or unreadable (e.g. key was reset). */
    public String get(String key) {
        String packed = sp.getString(key, null);
        if (packed == null) return null;
        try {
            String[] parts = packed.split(":", 2);
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] ct = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public void clear() {
        sp.edit().clear().apply();
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (ks.containsAlias(alias)) {
            return ((KeyStore.SecretKeyEntry) ks.getEntry(alias, null)).getSecretKey();
        }
        KeyGenerator gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        gen.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return gen.generateKey();
    }
}
