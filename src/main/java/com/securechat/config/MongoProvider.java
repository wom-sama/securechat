/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;

public final class MongoProvider {
    private static MongoClient client;

    private MongoProvider() {}

    public static synchronized MongoDatabase db() {
        if (client == null) {
            client = MongoClients.create(AppConfig.getUri());
        }
        return client.getDatabase(AppConfig.DB_NAME);
    }

    public static synchronized void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }
}
