package com.securechat.service;

import com.securechat.dao.UserDAO;
import com.securechat.security.*;
import org.bson.Document;
import java.util.UUID;
import java.security.KeyPair;

public class AuthService {
    private final UserDAO userDAO = new UserDAO();

    public void register(String username, char[] password) {
        if (userDAO.findByUsername(username) != null) {
            throw new IllegalArgumentException("Username already exists");
        }

        byte[] pwdSalt = Rand.bytes(16);
        int pwdIters = 200_000;
        byte[] pwdHash = PBKDF2.deriveKey(password, pwdSalt, pwdIters, 32);

        KeyPair signKP = Keys.genEd25519();
        KeyPair ecdhKP = Keys.genX25519();

        var signBlob = KeyProtector.protect(KeyProtector.privEncoded(signKP.getPrivate()), password);
        var ecdhBlob = KeyProtector.protect(KeyProtector.privEncoded(ecdhKP.getPrivate()), password);

        Document doc = new Document()
                .append("username", username)
                .append("pwdSaltB64", B64.enc(pwdSalt))
                .append("pwdHashB64", B64.enc(pwdHash))
                .append("pwdIters", pwdIters)
                .append("signPubB64", B64.enc(KeyProtector.pubEncoded(signKP.getPublic())))
                .append("signPrivEncB64", signBlob.ciphertextB64())
                .append("signPrivIvB64", signBlob.ivB64())
                .append("signPrivSaltKdfB64", signBlob.saltB64())
                .append("signPrivKdfIters", signBlob.iters())
                .append("ecdhPubB64", B64.enc(KeyProtector.pubEncoded(ecdhKP.getPublic())))
                .append("ecdhPrivEncB64", ecdhBlob.ciphertextB64())
                .append("ecdhPrivIvB64", ecdhBlob.ivB64())
                .append("ecdhPrivSaltKdfB64", ecdhBlob.saltB64())
                .append("ecdhPrivKdfIters", ecdhBlob.iters());

        userDAO.insertUser(doc);
    }

    public Session login(String username, char[] password) {
        Document u = userDAO.findByUsername(username);
        if (u == null) throw new IllegalArgumentException("User not found");

        byte[] salt = B64.dec(u.getString("pwdSaltB64"));
        int iters = u.getInteger("pwdIters");
        byte[] expected = B64.dec(u.getString("pwdHashB64"));
        byte[] actual = PBKDF2.deriveKey(password, salt, iters, 32);

        if (!PBKDF2.constantTimeEquals(expected, actual)) {
            throw new IllegalArgumentException("Invalid password");
        }
        String newSessionId = UUID.randomUUID().toString();
        userDAO.updateSessionId(username, newSessionId);

        var signBlob = new KeyProtector.ProtectedBlob(
                u.getString("signPrivEncB64"),
                u.getString("signPrivIvB64"),
                u.getString("signPrivSaltKdfB64"),
                u.getInteger("signPrivKdfIters")
        );
        var ecdhBlob = new KeyProtector.ProtectedBlob(
                u.getString("ecdhPrivEncB64"),
                u.getString("ecdhPrivIvB64"),
                u.getString("ecdhPrivSaltKdfB64"),
                u.getInteger("ecdhPrivKdfIters")
        );

        byte[] signPrivPkcs8 = KeyProtector.unprotect(signBlob, password);
        byte[] ecdhPrivPkcs8 = KeyProtector.unprotect(ecdhBlob, password);

        var signPriv = KeyProtector.decodeEd25519Private(signPrivPkcs8);
        var ecdhPriv = KeyProtector.decodeX25519Private(ecdhPrivPkcs8);

        var signPub = KeyProtector.decodeEd25519Public(B64.dec(u.getString("signPubB64")));
        var ecdhPub = KeyProtector.decodeX25519Public(B64.dec(u.getString("ecdhPubB64")));

        return new Session(username, signPub, signPriv, ecdhPub, ecdhPriv, newSessionId);
    }
    
    public boolean isSessionValid(String username, String mySessionId) {
        String dbSessionId = userDAO.getSessionId(username);
        return mySessionId != null && mySessionId.equals(dbSessionId);
    }
    
    // [MỚI] Các hàm hỗ trợ Last Logout
    public void logout(String username) {
        userDAO.updateLastLogout(username);
    }
    
    public long getLastLogoutTime(String username) {
        return userDAO.getLastLogout(username);
    }

    public record Session(
            String username,
            java.security.PublicKey signPub,
            java.security.PrivateKey signPriv,
            java.security.PublicKey ecdhPub,
            java.security.PrivateKey ecdhPriv,
            String sessionId 
    ) {}
}