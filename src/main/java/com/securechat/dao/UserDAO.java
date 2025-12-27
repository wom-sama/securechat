/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.dao;

import com.securechat.config.AppConfig;
import com.securechat.config.MongoProvider;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import org.bson.Document;

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
        col().updateOne(
            Filters.eq("username", username), 
            Updates.set("sessionId", sessionId)
        );
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
}