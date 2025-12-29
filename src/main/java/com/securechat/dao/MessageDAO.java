/* MessageDAO.java - Bản thêm TTL Index (Tự hủy) */
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
            
            // 1. Index cho hiệu năng Query (Giữ nguyên)
            c.createIndex(Indexes.compoundIndex(
                Indexes.ascending("to"), 
                Indexes.descending("ts")
            ), new IndexOptions().name("idx_inbox"));
            
            c.createIndex(Indexes.compoundIndex(
                Indexes.ascending("from"), 
                Indexes.descending("ts")
            ), new IndexOptions().name("idx_sent"));

            // [MỚI] 2. TTL Index (Time-To-Live) cho tính năng Tự hủy
            // expireAfter(0L, TimeUnit.SECONDS) nghĩa là: Xóa ngay khi thời gian hiện tại > giá trị trường "expireAt"
            IndexOptions ttlOptions = new IndexOptions()
                    .name("idx_auto_delete")
                    .expireAfter(0L, TimeUnit.SECONDS); 
            
            c.createIndex(Indexes.ascending("expireAt"), ttlOptions);
            
        } catch (Exception e) {
            // Lưu ý: Nếu Index đã tồn tại với cấu hình khác, MongoDB sẽ báo lỗi.
            // Khi demo, nếu muốn đổi thời gian, bạn cần vào Atlas xóa index "idx_auto_delete" đi để app tạo lại.
            System.out.println("Index creation warning: " + e.getMessage());
        }
    }

    public void insertMessage(Document msgDoc) {
        col().insertOne(msgDoc);
    }

    // --- CÁC HÀM QUERY CŨ GIỮ NGUYÊN ---

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

    public List<Document> findConversation(String myUsername, String partnerUsername, int limit) {
        List<Document> out = new ArrayList<>();
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
    
    public List<String> getSendersSince(String myUsername, long lastCheckTime) {
        return col().distinct("from", 
                Filters.and(
                    Filters.eq("to", myUsername),
                    Filters.gt("ts", lastCheckTime)
                ), String.class)
                .into(new ArrayList<>());
    }
}