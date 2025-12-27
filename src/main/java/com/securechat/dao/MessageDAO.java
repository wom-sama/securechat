/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.dao;

import com.securechat.config.AppConfig;
import com.securechat.config.MongoProvider;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MessageDAO {
    private MongoCollection<Document> col() {
        return MongoProvider.db().getCollection(AppConfig.COL_MESSAGES);
    }

    public void insertMessage(Document msgDoc) {
        col().insertOne(msgDoc);
    }

    public List<Document> findInbox(String username, int limit) {
        List<Document> out = new ArrayList<>();
        col().find(Filters.eq("to", username))
                .sort(Sorts.descending("ts"))
                .limit(limit)
                .into(out);
        return out;
    }
}
