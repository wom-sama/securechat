/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.security;
import javax.crypto.KeyAgreement;
import java.security.*;

public final class Keys {
    private Keys() {}

    public static KeyPair genEd25519() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Ed25519 keygen failed", e);
        }
    }

    public static KeyPair genX25519() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("X25519");
            return kpg.generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("X25519 keygen failed", e);
        }
    }

    public static byte[] signEd25519(PrivateKey priv, byte[] msg) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(priv);
            s.update(msg);
            return s.sign();
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
            throw new RuntimeException("Ed25519 sign failed", e);
        }
    }

    public static boolean verifyEd25519(PublicKey pub, byte[] msg, byte[] sig) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initVerify(pub);
            s.update(msg);
            return s.verify(sig);
        } catch (InvalidKeyException | NoSuchAlgorithmException | SignatureException e) {
            throw new RuntimeException("Ed25519 verify failed", e);
        }
    }

    public static byte[] x25519SharedSecret(PrivateKey myPriv, PublicKey theirPub) {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("X25519");
            ka.init(myPriv);
            ka.doPhase(theirPub, true);
            return ka.generateSecret();
        } catch (IllegalStateException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new RuntimeException("X25519 ECDH failed", e);
        }
    }
}
