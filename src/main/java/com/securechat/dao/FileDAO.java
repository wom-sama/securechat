package com.securechat.dao;

import com.securechat.config.MongoProvider;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import org.bson.Document;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class FileDAO {
    private static final String COL_FILES = "file_store";

    public FileDAO() {
        // [QUAN TRỌNG] Tạo chỉ mục TTL (Time To Live) cho MongoDB
        // File sẽ tự động bị xóa sau khi field "expireAt" đến hạn
        try {
            MongoCollection<Document> col = MongoProvider.db().getCollection(COL_FILES);
            IndexOptions indexOptions = new IndexOptions().expireAfter(0L, TimeUnit.SECONDS);
            col.createIndex(Indexes.ascending("expireAt"), indexOptions);
        } catch (Exception e) {
            // Index có thể đã tồn tại, bỏ qua lỗi
        }
    }

    private MongoCollection<Document> col() {
        return MongoProvider.db().getCollection(COL_FILES);
    }

    public void saveFile(String fileId, String encryptedBase64, long ttlSeconds) {
        Document doc = new Document("_id", fileId)
                .append("data", encryptedBase64)
                .append("uploadedAt", System.currentTimeMillis());
        
        // Nếu có TTL, tính thời gian hết hạn
        if (ttlSeconds > 0) {
            doc.append("expireAt", new Date(System.currentTimeMillis() + (ttlSeconds * 1000)));
        }
        
        col().insertOne(doc);
    }

    public String getFileContent(String fileId) {
        Document doc = col().find(Filters.eq("_id", fileId)).first();
        return (doc != null) ? doc.getString("data") : null;
    }
}