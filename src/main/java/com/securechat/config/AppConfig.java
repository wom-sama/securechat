/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.config;
import io.github.cdimascio.dotenv.Dotenv;
import java.nio.charset.StandardCharsets;
/**
 *
 * @author ADMIN
 */
public final class AppConfig {
    private AppConfig() {}
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing() 
            .load();

    public static final String DB_NAME = dotenv.get("DB_NAME", "securechat");
    public static final String COL_USERS = "users";
    public static final String COL_MESSAGES = "messages";

    public static String getUri() {
        return dotenv.get("MONGODB_URI");
    }
    
    public static byte[] getProfileKey() {
        String k = dotenv.get("PROFILE_SECRET_KEY");
        // Fallback cho demo nếu quên config trong .env (Chỉ dùng lúc dev)
        if (k == null || k.length() < 32) {
             k = "DEFAULT_MASTER_KEY_32_BYTES_LONG!!"; 
        }
        // Cắt đúng 32 byte để dùng cho AES-256
        return k.substring(0, 32).getBytes(StandardCharsets.UTF_8);
    }
}