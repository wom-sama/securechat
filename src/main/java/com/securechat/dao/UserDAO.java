/* UserDAO.java */
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
        if (doc != null) return doc.getString("sessionId");
        return null;
    }

    public void updateLastLogout(String username) {
        col().updateOne(Filters.eq("username", username), Updates.set("lastLogout", System.currentTimeMillis()));
    }

    public long getLastLogout(String username) {
        Document doc = col().find(Filters.eq("username", username))
                            .projection(new Document("lastLogout", 1))
                            .first();
        if (doc != null && doc.containsKey("lastLogout")) return doc.getLong("lastLogout");
        return 0L; 
    }

    // [MỚI] Thêm một người vào danh sách liên hệ (Sử dụng addToSet để không trùng lặp)
    public void addContact(String myUsername, String partnerUsername) {
        col().updateOne(
            Filters.eq("username", myUsername),
            Updates.addToSet("contacts", partnerUsername)
        );
    }

    // [MỚI] Lấy danh sách liên hệ đã lưu
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
        col().updateOne(
            Filters.eq("username", myUsername),
            Updates.pull("contacts", partnerUsername) // $pull: Kéo phần tử ra khỏi mảng
        );
    }
}