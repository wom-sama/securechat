/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.security;

import java.util.Base64;

public final class B64 {
    private B64() {}
    public static String enc(byte[] b) { return Base64.getEncoder().encodeToString(b); }
    public static byte[] dec(String s) { return Base64.getDecoder().decode(s); }
}