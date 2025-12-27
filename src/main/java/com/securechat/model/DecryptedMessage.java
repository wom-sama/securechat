/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.model;

/**
 *
 * @author ADMIN
 */
public class DecryptedMessage {
    public String from;
    public String to;
    public long ts;
    public String plaintext;
    public boolean signatureValid;
}