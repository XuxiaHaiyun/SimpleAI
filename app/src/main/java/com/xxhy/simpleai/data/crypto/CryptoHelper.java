package com.xxhy.simpleai.data.crypto;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * API Key 加密工具。使用 AES/CBC/PKCS5Padding，密钥首次生成后存于 SharedPreferences。
 * 兼容 Android 4.x 全版本（javax.crypto 原生支持）。
 */
public class CryptoHelper {

    private static final String PREFS = "crypto_prefs";
    private static final String KEY_ALIAS = "aes_key";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int KEY_SIZE = 256;

    private final SharedPreferences prefs;
    private SecretKey secretKey;

    public CryptoHelper(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        loadOrCreateKey();
    }

    private void loadOrCreateKey() {
        String stored = prefs.getString(KEY_ALIAS, null);
        if (stored != null) {
            try {
                byte[] keyBytes = Base64.decode(stored, Base64.NO_WRAP);
                secretKey = new SecretKeySpec(keyBytes, ALGORITHM);
            } catch (Exception e) {
                secretKey = generateKey();
            }
        } else {
            secretKey = generateKey();
        }
    }

    private SecretKey generateKey() {
        try {
            KeyGenerator kg = KeyGenerator.getInstance(ALGORITHM);
            kg.init(KEY_SIZE, new SecureRandom());
            SecretKey key = kg.generateKey();
            prefs.edit()
                    .putString(KEY_ALIAS, Base64.encodeToString(key.getEncoded(), Base64.NO_WRAP))
                    .apply();
            return key;
        } catch (Exception e) {
            throw new RuntimeException("生成 AES 密钥失败", e);
        }
    }

    /** 加密明文，返回 Base64(IV + 密文) */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return "";
        }
        try {
            byte[] iv = new byte[16];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes("UTF-8"));
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
            return Base64.encodeToString(combined, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("加密失败", e);
        }
    }

    /** 解密 Base64(IV + 密文) */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return "";
        }
        try {
            byte[] combined = Base64.decode(cipherText, Base64.NO_WRAP);
            byte[] iv = new byte[16];
            byte[] encrypted = new byte[combined.length - 16];
            System.arraycopy(combined, 0, iv, 0, 16);
            System.arraycopy(combined, 16, encrypted, 0, encrypted.length);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec);
            byte[] decrypted = cipher.doFinal(encrypted);
            return new String(decrypted, "UTF-8");
        } catch (Exception e) {
            throw new RuntimeException("解密失败", e);
        }
    }
}
