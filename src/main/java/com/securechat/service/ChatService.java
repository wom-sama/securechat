/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.service;

import com.securechat.dao.MessageDAO;
import com.securechat.dao.UserDAO;
import com.securechat.model.DecryptedMessage;
import com.securechat.security.*;
import org.bson.Document;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

public class ChatService {
    private final UserDAO userDAO = new UserDAO();
    private final MessageDAO messageDAO = new MessageDAO();

    public void sendMessage(AuthService.Session sender, String toUser, String plaintext) {
        Document receiver = userDAO.findByUsername(toUser);
        if (receiver == null) throw new IllegalArgumentException("Receiver not found");

        PublicKey receiverEcdhPub = KeyProtector.decodeX25519Public(B64.dec(receiver.getString("ecdhPubB64")));

        long ts = System.currentTimeMillis();

        // 1) SIGN FIRST
        byte[] digestInput = Canonical.digestInput(sender.username(), toUser, ts, plaintext);
        byte[] digest = SHA256.hash(digestInput);
        byte[] sig = Keys.signEd25519(sender.signPriv(), digest);

        // Build payload (simple text format for demo; thực tế có thể JSON)
        String payload =
                "from=" + sender.username() + "\n" +
                "to=" + toUser + "\n" +
                "ts=" + ts + "\n" +
                "msg=" + plaintext + "\n" +
                "digestB64=" + B64.enc(digest) + "\n" +
                "sigB64=" + B64.enc(sig) + "\n";

        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        // 2) ECDH (ephemeral) + HKDF -> AES key
        KeyPair eph = Keys.genX25519();
        byte[] shared = Keys.x25519SharedSecret(eph.getPrivate(), receiverEcdhPub);

        byte[] iv = Rand.bytes(12);
        byte[] aesKey = HKDF.deriveAes256(shared, iv, "SecureChat msg key".getBytes(StandardCharsets.UTF_8));

        // 3) ENCRYPT (AES-GCM), bind AAD to metadata
        byte[] aad = Canonical.aad(sender.username(), toUser, ts);
        byte[] ct = AesGcm.encrypt(aesKey, iv, payloadBytes, aad);

        Document msgDoc = new Document()
                .append("from", sender.username())
                .append("to", toUser)
                .append("ts", ts)
                .append("ephPubB64", B64.enc(KeyProtector.pubEncoded(eph.getPublic())))
                .append("ivB64", B64.enc(iv))
                .append("ciphertextB64", B64.enc(ct))
                .append("encAlg", "AES/GCM")
                .append("kexAlg", "X25519")
                .append("sigAlg", "Ed25519");

        messageDAO.insertMessage(msgDoc);
    }

    public List<DecryptedMessage> loadInbox(AuthService.Session receiver, int limit) {
        List<Document> docs = messageDAO.findInbox(receiver.username(), limit);
        List<DecryptedMessage> out = new ArrayList<>();

        for (Document d : docs) {
            try {
                String from = d.getString("from");
                String to = d.getString("to");
                long ts = d.getLong("ts");

                PublicKey ephPub = KeyProtector.decodeX25519Public(B64.dec(d.getString("ephPubB64")));
                byte[] iv = B64.dec(d.getString("ivB64"));
                byte[] ct = B64.dec(d.getString("ciphertextB64"));

                // Derive AES key from ECDH shared secret
                byte[] shared = Keys.x25519SharedSecret(receiver.ecdhPriv(), ephPub);
                byte[] aesKey = HKDF.deriveAes256(shared, iv, "SecureChat msg key".getBytes(StandardCharsets.UTF_8));

                byte[] aad = Canonical.aad(from, to, ts);
                byte[] payloadBytes = AesGcm.decrypt(aesKey, iv, ct, aad);
                String payload = new String(payloadBytes, StandardCharsets.UTF_8);

                // Parse payload (demo parser)
                String msg = extract(payload, "msg");
                String sigB64 = extract(payload, "sigB64");

                // Verify signature using sender public key from DB
                Document senderDoc = userDAO.findByUsername(from);
                if (senderDoc == null) throw new IllegalStateException("Sender missing");

                PublicKey senderSignPub = KeyProtector.decodeEd25519Public(B64.dec(senderDoc.getString("signPubB64")));

                byte[] digestInput = Canonical.digestInput(from, to, ts, msg);
                byte[] digest = SHA256.hash(digestInput);
                boolean ok = Keys.verifyEd25519(senderSignPub, digest, B64.dec(sigB64));

                DecryptedMessage dm = new DecryptedMessage();
                dm.from = from;
                dm.to = to;
                dm.ts = ts;
                dm.plaintext = msg;
                dm.signatureValid = ok;
                out.add(dm);

            } catch (Exception ex) {
                // Nếu decrypt/verify fail, vẫn cho hiện “(failed)” để demo tấn công/sửa DB
                DecryptedMessage dm = new DecryptedMessage();
                dm.from = d.getString("from");
                dm.to = d.getString("to");
                dm.ts = d.getLong("ts");
                dm.plaintext = "(DECRYPT FAILED)";
                dm.signatureValid = false;
                out.add(dm);
            }
        }
        return out;
    }

    private static String extract(String payload, String key) {
        // format: key=value
        String[] lines = payload.split("\n");
        for (String line : lines) {
            int idx = line.indexOf('=');
            if (idx > 0) {
                String k = line.substring(0, idx).trim();
                if (k.equals(key)) return line.substring(idx + 1).trim();
            }
        }
        return "";
    }
}
