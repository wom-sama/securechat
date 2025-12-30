/* ProfileSecurity.java */
package com.securechat.service;

import com.securechat.config.AppConfig;
import com.securechat.security.AesGcm;
import com.securechat.security.B64;
import com.securechat.security.Rand;
import java.nio.charset.StandardCharsets;

public class ProfileSecurity {
    
    // Format lưu trong DB: "IV_BASE64:CIPHERTEXT_BASE64"
    private static final String SEPARATOR = ":";

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) return "";

        byte[] iv = Rand.bytes(12);
        
        byte[] masterKey = AppConfig.getProfileKey();

        byte[] ct = AesGcm.encrypt(masterKey, iv, plaintext.getBytes(StandardCharsets.UTF_8), null);

        return B64.enc(iv) + SEPARATOR + B64.enc(ct);
    }

    public String decrypt(String encryptedPackage) {
        if (encryptedPackage == null || encryptedPackage.isEmpty()) return "";
        if (!encryptedPackage.contains(SEPARATOR)) return "(Error: Invalid Format)";

        try {
            String[] parts = encryptedPackage.split(SEPARATOR);
            String ivB64 = parts[0];
            String ctB64 = parts[1];

            byte[] iv = B64.dec(ivB64);
            byte[] ct = B64.dec(ctB64);
            byte[] masterKey = AppConfig.getProfileKey();
            byte[] pt = AesGcm.decrypt(masterKey, iv, ct, null);
            return new String(pt, StandardCharsets.UTF_8);

        } catch (Exception e) {
            return "(Decryption Failed)"; 
        }
    }
}