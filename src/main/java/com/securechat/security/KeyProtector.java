/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.security;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.*;

public final class KeyProtector {
    private KeyProtector() {}

    // ----- Encode keys -----
    public static byte[] pubEncoded(PublicKey k) { return k.getEncoded(); }      // X.509
    public static byte[] privEncoded(PrivateKey k) { return k.getEncoded(); }    // PKCS#8

    // ----- Decode keys -----
    public static PublicKey decodeEd25519Public(byte[] x509) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(x509));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static PrivateKey decodeEd25519Private(byte[] pkcs8) {
        try {
            return KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static PublicKey decodeX25519Public(byte[] x509) {
        try {
            return KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(x509));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public static PrivateKey decodeX25519Private(byte[] pkcs8) {
        try {
            return KeyFactory.getInstance("X25519").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    // ----- Protect private key with password (PBKDF2 -> AES-GCM) -----
    public static ProtectedBlob protect(byte[] privateKeyPkcs8, char[] password) {
        byte[] salt = Rand.bytes(16);
        int iters = 200_000;
        byte[] kek = PBKDF2.deriveKey(password, salt, iters, 32);

        byte[] iv = Rand.bytes(12);
        byte[] ct = AesGcm.encrypt(kek, iv, privateKeyPkcs8, null);

        return new ProtectedBlob(B64.enc(ct), B64.enc(iv), B64.enc(salt), iters);
    }

    public static byte[] unprotect(ProtectedBlob blob, char[] password) {
        byte[] salt = B64.dec(blob.saltB64);
        byte[] iv = B64.dec(blob.ivB64);
        byte[] ct = B64.dec(blob.ciphertextB64);

        byte[] kek = PBKDF2.deriveKey(password, salt, blob.iters, 32);
        return AesGcm.decrypt(kek, iv, ct, null);
    }

    public record ProtectedBlob(String ciphertextB64, String ivB64, String saltB64, int iters) {}
}
