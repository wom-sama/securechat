/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.dao;

import com.securechat.config.AppConfig;
import com.securechat.config.MongoProvider;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Sorts;
import org.bson.Document;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import java.util.ArrayList;
import java.util.List;

public class MessageDAO {
    
    public MessageDAO() {
        createIndexes();
    }

    private MongoCollection<Document> col() {
        return MongoProvider.db().getCollection(AppConfig.COL_MESSAGES);
    }

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

    public List<String> getRecentContacts(String myUsername) {
        List<String> senders = col().distinct("from", Filters.eq("to", myUsername), String.class)
                                    .into(new ArrayList<>());
        

        List<String> receivers = col().distinct("originalTo", Filters.eq("from", myUsername), String.class)
                                     .into(new ArrayList<>());

        return Stream.concat(senders.stream(), receivers.stream())
                     .distinct()
                     .filter(u -> !u.equals(myUsername)) 
                     .collect(Collectors.toList());
    }


    //  tìm tin nhắn mà "to" = mình, và ("from" = partner HOẶC "originalTo" = partner)
    public List<Document> findConversation(String myUsername, String partnerUsername, int limit) {
        List<Document> out = new ArrayList<>();
        // 1. Tin họ gửi mình: to=Me, from=Partner
        // 2. Tin mình gửi họ (bản copy): to=Me, originalTo=Partner 
        col().find(Filters.and(
                    Filters.eq("to", myUsername), 
                    Filters.or(
                        Filters.eq("from", partnerUsername),
                        Filters.eq("originalTo", partnerUsername)
                    )
                ))
                .sort(Sorts.ascending("ts")) 
                .limit(limit)
                .into(out);
        return out;
    }
}
