/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.security;

import java.nio.charset.StandardCharsets;

public final class Canonical {
    private Canonical() {}

    // Chuỗi canonical đơn giản: dùng delimiter không mơ hồ
    public static byte[] digestInput(String from, String to, long ts, String msg) {
        String s = from + "|" + to + "|" + ts + "|" + msg;
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] aad(String from, String to, long ts) {
        String s = from + "|" + to + "|" + ts;
        return s.getBytes(StandardCharsets.UTF_8);
    }
}