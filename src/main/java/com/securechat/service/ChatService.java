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
        Document receiverDoc = userDAO.findByUsername(toUser);
        if (receiverDoc == null) throw new IllegalArgumentException("Receiver not found");
        
        Document senderDoc = userDAO.findByUsername(sender.username());

        long ts = System.currentTimeMillis();
        byte[] digestInput = Canonical.digestInput(sender.username(), toUser, ts, plaintext);
        byte[] digest = SHA256.hash(digestInput);
        byte[] sig = Keys.signEd25519(sender.signPriv(), digest);
        
        // Payload chung
        String payload =
                "from=" + sender.username() + "\n" +
                "to=" + toUser + "\n" +
                "ts=" + ts + "\n" +
                "msg=" + plaintext + "\n" +
                "digestB64=" + B64.enc(digest) + "\n" +
                "sigB64=" + B64.enc(sig) + "\n";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        // --- BẢN 1: Gửi cho Người nhận (Bob) ---
        PublicKey bobEcdhPub = KeyProtector.decodeX25519Public(B64.dec(receiverDoc.getString("ecdhPubB64")));
        createAndSaveMessage(sender.username(), toUser, null, ts, payloadBytes, bobEcdhPub);
        // --- BẢN 2: Gửi cho Chính mình (Alice - để lưu history) ---
        PublicKey aliceEcdhPub = KeyProtector.decodeX25519Public(B64.dec(senderDoc.getString("ecdhPubB64")));
        createAndSaveMessage(sender.username(), sender.username(), toUser, ts, payloadBytes, aliceEcdhPub);
    }
    
    private void createAndSaveMessage(String from, String to, String originalTo, long ts, byte[] payloadBytes, PublicKey recipientPub) {
        KeyPair eph = Keys.genX25519();
        byte[] shared = Keys.x25519SharedSecret(eph.getPrivate(), recipientPub);
        byte[] iv = Rand.bytes(12);
        byte[] aesKey = HKDF.deriveAes256(shared, iv, "SecureChat msg key".getBytes(StandardCharsets.UTF_8));
        
        String realTo = (originalTo != null) ? originalTo : to;
        byte[] aad = Canonical.aad(from, realTo, ts);
        
        byte[] ct = AesGcm.encrypt(aesKey, iv, payloadBytes, aad);
        
        Document doc = new Document()
                .append("from", from)
                .append("to", to)
                .append("ts", ts)
                .append("ephPubB64", B64.enc(KeyProtector.pubEncoded(eph.getPublic())))
                .append("ivB64", B64.enc(iv))
                .append("ciphertextB64", B64.enc(ct))
                .append("encAlg", "AES/GCM")
                .append("kexAlg", "X25519")
                .append("sigAlg", "Ed25519");
        
        if (originalTo != null) {
            doc.append("originalTo", originalTo); // Đánh dấu đây là tin nhắn trong mục "Đã gửi"
        }
        
        messageDAO.insertMessage(doc);
    }

    // [CẬP NHẬT] Load hội thoại
    public List<DecryptedMessage> loadConversation(AuthService.Session session, String partner, int limit) {
        List<Document> docs = messageDAO.findConversation(session.username(), partner, limit);
        List<DecryptedMessage> out = new ArrayList<>();

        for (Document d : docs) {
            DecryptedMessage dm = new DecryptedMessage();
            try {
                // Lấy thông tin gốc từ DB
                String from = d.getString("from");
                // Nếu có originalTo, tức là tin này do mình gửi đi. Nếu không, là tin mình nhận.
                String originalTo = d.getString("originalTo");
                String realTo = (originalTo != null) ? originalTo : d.getString("to");
                
                long ts = d.getLong("ts");

                PublicKey ephPub = KeyProtector.decodeX25519Public(B64.dec(d.getString("ephPubB64")));
                byte[] iv = B64.dec(d.getString("ivB64"));
                byte[] ct = B64.dec(d.getString("ciphertextB64"));

                // Decrypt (Dùng khóa của mình vì bản ghi này được mã hóa cho mình - dù là inbox hay sent)
                byte[] shared = Keys.x25519SharedSecret(session.ecdhPriv(), ephPub);
                byte[] aesKey = HKDF.deriveAes256(shared, iv, "SecureChat msg key".getBytes(StandardCharsets.UTF_8));

                byte[] aad = Canonical.aad(from, realTo, ts);
                byte[] payloadBytes = AesGcm.decrypt(aesKey, iv, ct, aad);
                String payload = new String(payloadBytes, StandardCharsets.UTF_8);

                String msg = extract(payload, "msg");
                String sigB64 = extract(payload, "sigB64");

                // Verify
                Document senderDoc = userDAO.findByUsername(from);
                if (senderDoc != null) {
                    PublicKey senderSignPub = KeyProtector.decodeEd25519Public(B64.dec(senderDoc.getString("signPubB64")));
                    byte[] digestInput = Canonical.digestInput(from, realTo, ts, msg);
                    byte[] digest = SHA256.hash(digestInput);
                    dm.signatureValid = Keys.verifyEd25519(senderSignPub, digest, B64.dec(sigB64));
                }

                dm.from = from;
                dm.to = realTo;
                dm.ts = ts;
                dm.plaintext = msg;
                out.add(dm);

            } catch (Exception ex) {
                dm.plaintext = "(DECRYPT ERROR)";
                dm.signatureValid = false;
                dm.from = d.getString("from");
                dm.ts = d.getLong("ts");
                out.add(dm);
            }
        }
        return out;
    }
    
    public List<String> getRecentContacts(String myUser) {
        return messageDAO.getRecentContacts(myUser);
    }

    private static String extract(String payload, String key) {
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
    //kiem tra user ton tai
    public boolean checkUserExists(String username) {
    return userDAO.findByUsername(username) != null;
}
    // Kiểm tra xem có tin nhắn mới nào từ sau lần check cuối không
    public List<String> checkNewMessages(String myUser, long lastCheckTime) {
        return messageDAO.getSendersSince(myUser, lastCheckTime);
    }
}
