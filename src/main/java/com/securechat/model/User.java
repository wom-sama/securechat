/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.model;

/**
 *
 * @author ADMIN
 */
public class User {
    public String username;

    // password hash (PBKDF2)
    public String pwdSaltB64;
    public String pwdHashB64;
    public int pwdIters;

    // signing keys (Ed25519): public in clear, private encrypted by password-derived key
    public String signPubB64;
    public String signPrivEncB64;
    public String signPrivIvB64;
    public String signPrivSaltKdfB64;
    public int signPrivKdfIters;

    // ECDH keys (X25519)
    public String ecdhPubB64;
    public String ecdhPrivEncB64;
    public String ecdhPrivIvB64;
    public String ecdhPrivSaltKdfB64;
    public int ecdhPrivKdfIters;
}
