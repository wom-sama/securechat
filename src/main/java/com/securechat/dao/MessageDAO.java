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
import com.mongodb.client.model.Indexes; 
import com.mongodb.client.model.IndexOptions; 
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

public class MessageDAO {
    
     public MessageDAO() {
        createIndexes();
    }
    
    private MongoCollection<Document> col() {
        return MongoProvider.db().getCollection(AppConfig.COL_MESSAGES);
    }
    //tao index
    private void createIndexes() {
        try {
            MongoCollection<Document> c = col();
            c.createIndex(Indexes.compoundIndex(
                Indexes.ascending("to"), 
                Indexes.descending("ts")
            ), new IndexOptions().name("idx_inbox"));
            c.createIndex(Indexes.compoundIndex(
                Indexes.ascending("from"), 
                Indexes.descending("ts")
            ), new IndexOptions().name("idx_sent"));
            
        } catch (Exception e) {
            System.out.println("Index creation warning: " + e.getMessage());
        }
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
