/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.security;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public final class PBKDF2 {
    private PBKDF2() {}

    public static byte[] deriveKey(char[] password, byte[] salt, int iters, int keyLenBytes) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, iters, keyLenBytes * 8);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return skf.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("PBKDF2 failed", e);
        }
    }

    public static boolean constantTimeEquals(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }
}