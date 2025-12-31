/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.security;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public final class AesGcm {
    private AesGcm() {}

    public static byte[] encrypt(byte[] key32, byte[] iv12, byte[] plaintext, byte[] aad) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec k = new SecretKeySpec(key32, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv12);
            c.init(Cipher.ENCRYPT_MODE, k, spec);
            if (aad != null) c.updateAAD(aad);
            return c.doFinal(plaintext);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            throw new RuntimeException("AES-GCM encrypt failed", e);
        }
    }

    public static byte[] decrypt(byte[] key32, byte[] iv12, byte[] ciphertext, byte[] aad) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec k = new SecretKeySpec(key32, "AES");
            GCMParameterSpec spec = new GCMParameterSpec(128, iv12);
            c.init(Cipher.DECRYPT_MODE, k, spec);
            if (aad != null) c.updateAAD(aad);
            return c.doFinal(ciphertext);
        } catch (InvalidAlgorithmParameterException | InvalidKeyException | NoSuchAlgorithmException | BadPaddingException | IllegalBlockSizeException | NoSuchPaddingException e) {
            throw new RuntimeException("AES-GCM decrypt failed", e);
        }
    }
}
