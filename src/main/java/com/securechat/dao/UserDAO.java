package com.securechat.dao;

import com.securechat.config.AppConfig;
import com.securechat.config.MongoProvider;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;
import java.util.List;
import java.util.ArrayList;

public class UserDAO {

    public static final String FIELD_ENC_EMAIL = "enc_email";
    public static final String FIELD_ENC_FULLNAME = "enc_fullname";
    public static final String FIELD_ENC_ADDRESS = "enc_address";
    public static final String FIELD_ENC_GENDER = "enc_gender";
    public static final String FIELD_ENC_AVATAR = "enc_avatar";

    private MongoCollection<Document> col() {
        return MongoProvider.db().getCollection(AppConfig.COL_USERS);
    }

    public Document findByUsername(String username) {
        return col().find(Filters.eq("username", username)).first();
    }

    public void insertUser(Document userDoc) {
        col().insertOne(userDoc);
    }

    public void updateSessionId(String username, String sessionId) {
        col().updateOne(Filters.eq("username", username), Updates.set("sessionId", sessionId));
    }

    public String getSessionId(String username) {
        Document doc = col().find(Filters.eq("username", username))
                .projection(new Document("sessionId", 1))
                .first();
        if (doc != null) {
            return doc.getString("sessionId");
        }
        return null;
    }

    public void updateLastLogout(String username) {
        col().updateOne(Filters.eq("username", username), Updates.set("lastLogout", System.currentTimeMillis()));
    }

    public long getLastLogout(String username) {
        Document doc = col().find(Filters.eq("username", username))
                .projection(new Document("lastLogout", 1))
                .first();
        if (doc != null && doc.containsKey("lastLogout")) {
            return doc.getLong("lastLogout");
        }
        return 0L;
    }

    public void addContact(String myUsername, String partnerUsername) {
        col().updateOne(Filters.eq("username", myUsername), Updates.addToSet("contacts", partnerUsername));
    }

    public List<String> getSavedContacts(String myUsername) {
        Document doc = col().find(Filters.eq("username", myUsername))
                .projection(new Document("contacts", 1))
                .first();
        if (doc != null && doc.containsKey("contacts")) {
            return doc.getList("contacts", String.class);
        }
        return new ArrayList<>();
    }

    public void removeContact(String myUsername, String partnerUsername) {
        col().updateOne(Filters.eq("username", myUsername), Updates.pull("contacts", partnerUsername));
    }

    // --- CÁC HÀM XỬ LÝ LOCKOUT ---
    // 1. Tăng số lần nhập sai lên 1
    public void incrementFailedAttempts(String username) {
        col().updateOne(
                Filters.eq("username", username),
                Updates.inc("failedAttempts", 1) // Toán tử $inc: cộng dồn
        );
    }

    // 2. Reset số lần sai về 0 (khi đăng nhập thành công hoặc hết hạn khóa)
    public void resetFailedAttempts(String username) {
        col().updateOne(
                Filters.eq("username", username),
                Updates.combine(
                        Updates.set("failedAttempts", 0),
                        Updates.unset("lockoutUntil") // Xóa trường khóa đi
                )
        );
    }

    public void lockAccount(String username, long unlockTime) {
        col().updateOne(
                Filters.eq("username", username),
                Updates.set("lockoutUntil", unlockTime)
        );
    }

    public void updateUserCredentials(String username, String newSalt, String newHash,
            String signEnc, String signIv, String signSalt, int signIter,
            String ecdhEnc, String ecdhIv, String ecdhSalt, int ecdhIter) {
        col().updateOne(
                Filters.eq("username", username),
                Updates.combine(
                        Updates.set("pwdSaltB64", newSalt),
                        Updates.set("pwdHashB64", newHash),
                        // Cập nhật Signing Key mới
                        Updates.set("signPrivEncB64", signEnc),
                        Updates.set("signPrivIvB64", signIv),
                        Updates.set("signPrivSaltKdfB64", signSalt),
                        Updates.set("signPrivKdfIters", signIter),
                        // Cập nhật ECDH Key mới
                        Updates.set("ecdhPrivEncB64", ecdhEnc),
                        Updates.set("ecdhPrivIvB64", ecdhIv),
                        Updates.set("ecdhPrivSaltKdfB64", ecdhSalt),
                        Updates.set("ecdhPrivKdfIters", ecdhIter)
                )
        );
    }

    public void updateUserProfile(String username, String encEmail, String encFullName, String encAddress, String encGender) {
        col().updateOne(
                Filters.eq("username", username),
                Updates.combine(
                        Updates.set(FIELD_ENC_EMAIL, encEmail),
                        Updates.set(FIELD_ENC_FULLNAME, encFullName),
                        Updates.set(FIELD_ENC_ADDRESS, encAddress),
                        Updates.set(FIELD_ENC_GENDER, encGender)
                )
        );
    }

    /**
     * @param username
     * @return
     */
    public Document getUserProfile(String username) {
        return col().find(Filters.eq("username", username))
                .projection(new Document(FIELD_ENC_EMAIL, 1)
                        .append(FIELD_ENC_FULLNAME, 1)
                        .append(FIELD_ENC_ADDRESS, 1)
                        .append(FIELD_ENC_GENDER, 1))
                .first();
    }

    public void updateFullProfile(String username, String encEmail, String encFullName, String encAddress, String encGender) {
        col().updateOne(
                Filters.eq("username", username),
                Updates.combine(
                        Updates.set(FIELD_ENC_EMAIL, encEmail),
                        Updates.set(FIELD_ENC_FULLNAME, encFullName),
                        Updates.set(FIELD_ENC_ADDRESS, encAddress),
                        Updates.set(FIELD_ENC_GENDER, encGender)
                )
        );
    }

// Hàm lưu avatar
    public void updateAvatar(String username, String encAvatarBase64) {
        col().updateOne(Filters.eq("username", username), Updates.set(FIELD_ENC_AVATAR, encAvatarBase64));
    }

// Hàm lấy avatar
    public String getEncryptedAvatar(String username) {
        Document doc = col().find(Filters.eq("username", username)).projection(new Document(FIELD_ENC_AVATAR, 1)).first();
        return (doc != null) ? doc.getString(FIELD_ENC_AVATAR) : null;
    }
}
