/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.config;
import io.github.cdimascio.dotenv.Dotenv;
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
}