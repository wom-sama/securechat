/* ChatService.java */
package com.securechat.service;

import com.securechat.dao.FileDAO;
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
import java.util.Date;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ChatService {

    private final UserDAO userDAO = new UserDAO();
    private final MessageDAO messageDAO = new MessageDAO();
    private final FileDAO fileDAO = new FileDAO();

    public void sendMessage(AuthService.Session sender, String toUser, String plaintext, long ttlSeconds) {
        Document receiverDoc = userDAO.findByUsername(toUser);
        if (receiverDoc == null) {
            throw new IllegalArgumentException("Receiver not found");
        }
        Document senderDoc = userDAO.findByUsername(sender.username());

        long ts = System.currentTimeMillis();

        Date expireAt = null;
        if (ttlSeconds > 0) {
            expireAt = new Date(ts + (ttlSeconds * 1000));
        }

        userDAO.addContact(sender.username(), toUser);
        userDAO.addContact(toUser, sender.username()); 

        byte[] digestInput = Canonical.digestInput(sender.username(), toUser, ts, plaintext);
        byte[] digest = SHA256.hash(digestInput);
        byte[] sig = Keys.signEd25519(sender.signPriv(), digest);

        String payload
                = "from=" + sender.username() + "\n"
                + "to=" + toUser + "\n"
                + "ts=" + ts + "\n"
                + "msg=" + plaintext + "\n"
                + "digestB64=" + B64.enc(digest) + "\n"
                + "sigB64=" + B64.enc(sig) + "\n";
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        PublicKey bobEcdhPub = KeyProtector.decodeX25519Public(B64.dec(receiverDoc.getString("ecdhPubB64")));
        createAndSaveMessage(sender.username(), toUser, null, ts, expireAt, payloadBytes, bobEcdhPub);

        PublicKey aliceEcdhPub = KeyProtector.decodeX25519Public(B64.dec(senderDoc.getString("ecdhPubB64")));
        createAndSaveMessage(sender.username(), sender.username(), toUser, ts, expireAt, payloadBytes, aliceEcdhPub);
    }

    private void createAndSaveMessage(String from, String to, String originalTo, long ts, Date expireAt, byte[] payloadBytes, PublicKey recipientPub) {
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

        if (expireAt != null) {
            doc.append("expireAt", expireAt);
        }

        if (originalTo != null) {
            doc.append("originalTo", originalTo);
        }

        messageDAO.insertMessage(doc);
    }

    public List<String> getRecentContacts(String myUser) {
        List<String> saved = userDAO.getSavedContacts(myUser);
        List<String> fromMsgs = messageDAO.getContactsFromMessages(myUser);
        return Stream.concat(saved.stream(), fromMsgs.stream())
                .distinct()
                .collect(Collectors.toList());
    }

    public List<DecryptedMessage> loadConversation(AuthService.Session session, String partner, int limit) {
        List<Document> docs = messageDAO.findConversation(session.username(), partner, limit);
        List<DecryptedMessage> out = new ArrayList<>();
        for (Document d : docs) {
            DecryptedMessage dm = new DecryptedMessage();
            try {
                String from = d.getString("from");
                String originalTo = d.getString("originalTo");
                String realTo = (originalTo != null) ? originalTo : d.getString("to");
                long ts = d.getLong("ts");
                PublicKey ephPub = KeyProtector.decodeX25519Public(B64.dec(d.getString("ephPubB64")));
                byte[] iv = B64.dec(d.getString("ivB64"));
                byte[] ct = B64.dec(d.getString("ciphertextB64"));
                byte[] shared = Keys.x25519SharedSecret(session.ecdhPriv(), ephPub);
                byte[] aesKey = HKDF.deriveAes256(shared, iv, "SecureChat msg key".getBytes(StandardCharsets.UTF_8));
                byte[] aad = Canonical.aad(from, realTo, ts);
                byte[] payloadBytes = AesGcm.decrypt(aesKey, iv, ct, aad);
                String payload = new String(payloadBytes, StandardCharsets.UTF_8);
                String msg = extract(payload, "msg");
                String sigB64 = extract(payload, "sigB64");
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

    private static String extract(String payload, String key) {
        String[] lines = payload.split("\n");
        for (String line : lines) {
            int idx = line.indexOf('=');
            if (idx > 0) {
                String k = line.substring(0, idx).trim();
                if (k.equals(key)) {
                    return line.substring(idx + 1).trim();
                }
            }
        }
        return "";
    }

    public boolean checkUserExists(String username) {
        return userDAO.findByUsername(username) != null;
    }

    public List<String> checkNewMessages(String myUser, long lastCheckTime) {
        return messageDAO.getSendersSince(myUser, lastCheckTime);
    }

    // [CẬP NHẬT] Xóa liên hệ thì xóa luôn cả tin nhắn
    public void removeSavedContact(String myUsername, String partnerUsername) {
        // 1. Xóa khỏi danh sách bạn bè đã lưu
        userDAO.removeContact(myUsername, partnerUsername);
        
        // 2. Xóa sạch tin nhắn liên quan đến người này (trong hộp thư của mình)
        messageDAO.deleteConversation(myUsername, partnerUsername);
    }
    
    private void sendInternal(AuthService.Session sender, String toUser, String payloadContent, long ttlSeconds) {
        Document receiverDoc = userDAO.findByUsername(toUser);
        if (receiverDoc == null) throw new IllegalArgumentException("Receiver not found");
        Document senderDoc = userDAO.findByUsername(sender.username());

        long ts = System.currentTimeMillis();
        Date expireAt = (ttlSeconds > 0) ? new Date(ts + (ttlSeconds * 1000)) : null;

        userDAO.addContact(sender.username(), toUser);
        userDAO.addContact(toUser, sender.username()); 

        // Ký tên vào nội dung (Digital Signature)
        byte[] digestInput = Canonical.digestInput(sender.username(), toUser, ts, payloadContent);
        byte[] digest = SHA256.hash(digestInput);
        byte[] sig = Keys.signEd25519(sender.signPriv(), digest);

        String payload = "from=" + sender.username() + "\n"
                + "to=" + toUser + "\n"
                + "ts=" + ts + "\n"
                + "msg=" + payloadContent + "\n" // <-- Payload ở đây
                + "digestB64=" + B64.enc(digest) + "\n"
                + "sigB64=" + B64.enc(sig) + "\n";
        
        byte[] payloadBytes = payload.getBytes(StandardCharsets.UTF_8);

        // Mã hóa E2E cho người nhận (Bob)
        PublicKey bobEcdhPub = KeyProtector.decodeX25519Public(B64.dec(receiverDoc.getString("ecdhPubB64")));
        createAndSaveMessage(sender.username(), toUser, null, ts, expireAt, payloadBytes, bobEcdhPub);

        // Mã hóa E2E cho chính mình (Alice - để đồng bộ trên nhiều thiết bị)
        PublicKey aliceEcdhPub = KeyProtector.decodeX25519Public(B64.dec(senderDoc.getString("ecdhPubB64")));
        createAndSaveMessage(sender.username(), sender.username(), toUser, ts, expireAt, payloadBytes, aliceEcdhPub);
    }

    // --- [MỚI] HÀM GỬI FILE AN TOÀN ---
    public void sendFile(AuthService.Session sender, String toUser, byte[] fileBytes, String fileName, long ttlSeconds) {
        // 1. Tạo khóa ngẫu nhiên cho file này (FileKey)
        byte[] fileKey = Rand.bytes(32); // AES-256
        byte[] fileIv = Rand.bytes(12);
        
        // 2. Mã hóa file
        byte[] encryptedFileBytes = AesGcm.encrypt(fileKey, fileIv, fileBytes, null);
        String encryptedBlob = B64.enc(encryptedFileBytes);
        
        // 3. Lưu file lên Server (Server chỉ thấy đống rác encryptedBlob)
        String fileId = UUID.randomUUID().toString();
        fileDAO.saveFile(fileId, encryptedBlob, ttlSeconds);
        
        // 4. Tạo bản tin đặc biệt chứa chìa khóa
        // Format: [FILE]:FileID | Key_Base64 | IV_Base64 | FileName
        String specialMsg = "[FILE]:" + fileId + "|" + B64.enc(fileKey) + "|" + B64.enc(fileIv) + "|" + fileName;
        
        // 5. Gửi bản tin này đi (nó sẽ được mã hóa E2E lần nữa bởi sendInternal)
        sendInternal(sender, toUser, specialMsg, ttlSeconds);
    }

    // --- [MỚI] HÀM TẢI VÀ GIẢI MÃ FILE ---
    // Hàm này sẽ được gọi từ UI khi user click vào tin nhắn [FILE]
    public byte[] downloadAndDecryptFile(String metadataProtocolString) {
        // Input: [FILE]:FileID|Key|IV|Name
        try {
            String content = metadataProtocolString.substring(7); // Bỏ "[FILE]:"
            String[] parts = content.split("\\|");
            if (parts.length < 3) throw new IllegalArgumentException("Inva  lid file format");
            
            String fileId = parts[0];
            byte[] fileKey = B64.dec(parts[1]);
            byte[] fileIv = B64.dec(parts[2]);
            
            // 1. Tải blob từ server
            String encryptedBlob = fileDAO.getFileContent(fileId);
            if (encryptedBlob == null) throw new IllegalArgumentException("File not found on server (maybe expired)");
            
            // 2. Giải mã
            byte[] encryptedBytes = B64.dec(encryptedBlob);
            return AesGcm.decrypt(fileKey, fileIv, encryptedBytes, null);
            
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Download failed: " + e.getMessage());
        }
    }
}