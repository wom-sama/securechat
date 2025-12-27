/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Arrays;


public final class HKDF {
    private HKDF() {}

    // HKDF-Extract(salt, IKM)
    private static byte[] extract(byte[] salt, byte[] ikm) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        return mac.doFinal(ikm); // PRK
    }

    // HKDF-Expand(PRK, info, L)
    private static byte[] expand(byte[] prk, byte[] info, int len) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));

        byte[] t = new byte[0];
        byte[] okm = new byte[0];
        int counter = 1;

        while (okm.length < len) {
            mac.reset();
            mac.update(t);
            mac.update(info);
            mac.update((byte) counter);
            t = mac.doFinal();

            int remaining = len - okm.length;
            byte[] toAdd = (remaining >= t.length) ? t : Arrays.copyOf(t, remaining);

            byte[] newOkm = new byte[okm.length + toAdd.length];
            System.arraycopy(okm, 0, newOkm, 0, okm.length);
            System.arraycopy(toAdd, 0, newOkm, okm.length, toAdd.length);
            okm = newOkm;

            counter++;
        }
        return okm;
    }

    public static byte[] deriveAes256(byte[] sharedSecret, byte[] salt, byte[] info) {
        try {
            byte[] prk = extract(salt, sharedSecret);
            return expand(prk, info, 32); // 32 bytes = AES-256
        } catch (Exception e) {
            throw new RuntimeException("HKDF failed", e);
        }
    }
}