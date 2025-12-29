/* MessageDAO.java */
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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
            c.createIndex(Indexes.compoundIndex(Indexes.ascending("to"), Indexes.descending("ts")), new IndexOptions().name("idx_inbox"));
            c.createIndex(Indexes.compoundIndex(Indexes.ascending("from"), Indexes.descending("ts")), new IndexOptions().name("idx_sent"));

            IndexOptions ttlOptions = new IndexOptions()
                    .name("idx_auto_delete")
                    .expireAfter(0L, TimeUnit.SECONDS); 
            
            c.createIndex(Indexes.ascending("expireAt"), ttlOptions);
            
        } catch (Exception e) {
            System.out.println("Index creation warning: " + e.getMessage());
        }
    }

    public void insertMessage(Document msgDoc) {
        col().insertOne(msgDoc);
    }

    public List<String> getContactsFromMessages(String myUsername) {
        List<String> senders = col().distinct("from", Filters.eq("to", myUsername), String.class).into(new ArrayList<>());
        List<String> receivers = col().distinct("originalTo", Filters.eq("from", myUsername), String.class).into(new ArrayList<>());
        return Stream.concat(senders.stream(), receivers.stream())
                     .distinct()
                     .filter(u -> !u.equals(myUsername)) 
                     .collect(Collectors.toList());
    }

    public List<Document> findConversation(String myUsername, String partnerUsername, int limit) {
        List<Document> out = new ArrayList<>();
        col().find(Filters.and(
                    Filters.eq("to", myUsername),
                    Filters.or(Filters.eq("from", partnerUsername), Filters.eq("originalTo", partnerUsername))
                ))
                .sort(Sorts.ascending("ts")) 
                .limit(limit)
                .into(out);
        return out;
    }
    
    // [MỚI] Xóa toàn bộ cuộc hội thoại giữa mình và đối phương (Chỉ xóa bản copy của mình)
    public void deleteConversation(String myUsername, String partnerUsername) {
        col().deleteMany(Filters.and(
            Filters.eq("to", myUsername), // Quan trọng: Chỉ xóa tin trong hộp thư của mình
            Filters.or(
                Filters.eq("from", partnerUsername),      // Tin họ gửi đến
                Filters.eq("originalTo", partnerUsername) // Tin mình gửi đi (bản lưu)
            )
        ));
    }
    
    public List<String> getSendersSince(String myUsername, long lastCheckTime) {
        return col().distinct("from", 
                Filters.and(Filters.eq("to", myUsername), Filters.gt("ts", lastCheckTime)), String.class)
                .into(new ArrayList<>());
    }
}