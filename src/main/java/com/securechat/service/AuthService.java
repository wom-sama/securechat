/* AuthService.java */
package com.securechat.service;

import com.securechat.dao.UserDAO;
import com.securechat.security.*;
import org.bson.Document;
import java.util.UUID;
import java.security.KeyPair;

public class AuthService {
    private final UserDAO userDAO = new UserDAO();
    private final ProfileSecurity profileSec = new ProfileSecurity();
    // Cấu hình Lockout
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_TIME_MS = 5*60* 1000; // 5 Phút (Để test bạn có thể sửa thành 30s)
    private final CaptchaProvider captchaProvider = new CaptchaProvider();
    
    public record UserProfileDTO(String username, String email, String fullName, String address, String gender) {}

    public void register(String username, char[] password, 
                         String email, String fullName, String address, String gender,
                         String captchaId, String captchaAnswer) { // <-- Params mới
        
        // 1. [QUAN TRỌNG] Xác thực CAPTCHA ĐẦU TIÊN
        // Nếu là Bot -> Chặn ngay, không tốn tài nguyên DB check user hay mã hóa gì cả.
        if (!captchaProvider.verify(captchaId, captchaAnswer)) {
            throw new SecurityException("CAPTCHA verification failed! Wrong answer or expired.");
        }

        // 2. Kiểm tra user tồn tại
        if (userDAO.findByUsername(username) != null) {
            throw new IllegalArgumentException("Username already exists");
        }

        // 3. Xử lý Password & Key (Giữ nguyên)
        byte[] pwdSalt = Rand.bytes(16);
        int pwdIters = 200_000;
        byte[] pwdHash = PBKDF2.deriveKey(password, pwdSalt, pwdIters, 32);

        KeyPair signKP = Keys.genEd25519();
        KeyPair ecdhKP = Keys.genX25519();

        var signBlob = KeyProtector.protect(KeyProtector.privEncoded(signKP.getPrivate()), password);
        var ecdhBlob = KeyProtector.protect(KeyProtector.privEncoded(ecdhKP.getPrivate()), password);

        // 4. Mã hóa thông tin cá nhân
        String encEmail = profileSec.encrypt(email);
        String encFullName = profileSec.encrypt(fullName);
        String encAddress = profileSec.encrypt(address);
        String encGender = profileSec.encrypt(gender);

        // 5. Tạo Document và lưu (Giữ nguyên)
        Document doc = new Document()
                .append("username", username)
                .append(UserDAO.FIELD_ENC_EMAIL, encEmail)
                .append(UserDAO.FIELD_ENC_FULLNAME, encFullName)
                .append(UserDAO.FIELD_ENC_ADDRESS, encAddress)
                .append(UserDAO.FIELD_ENC_GENDER, encGender)
                .append("pwdSaltB64", B64.enc(pwdSalt))
                .append("pwdHashB64", B64.enc(pwdHash))
                .append("pwdIters", pwdIters)
                .append("failedAttempts", 0)
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
    
    // [MỚI] Hàm để Client lấy câu hỏi Captcha
    public CaptchaProvider.CaptchaChallenge getCaptchaChallenge() {
        return captchaProvider.createChallenge();
    }

    // [MỚI] Hàm lấy Profile và tự động giải mã
    public UserProfileDTO getProfile(String username) {
        Document doc = userDAO.getUserProfile(username);
        if (doc == null) return null;

        String plainEmail = profileSec.decrypt(doc.getString(UserDAO.FIELD_ENC_EMAIL));
        String plainName = profileSec.decrypt(doc.getString(UserDAO.FIELD_ENC_FULLNAME));
        String plainAddr = profileSec.decrypt(doc.getString(UserDAO.FIELD_ENC_ADDRESS));
        String plainGender = profileSec.decrypt(doc.getString(UserDAO.FIELD_ENC_GENDER));

        return new UserProfileDTO(username, plainEmail, plainName, plainAddr, plainGender);
    }


    
    public Session login(String username, char[] password) {
        Document u = userDAO.findByUsername(username);
        if (u == null) throw new IllegalArgumentException("User not found");

        // --- [LOGIC 1] KIỂM TRA KHÓA TÀI KHOẢN ---
        long currentTime = System.currentTimeMillis();
        if (u.containsKey("lockoutUntil")) {
            long unlockTime = u.getLong("lockoutUntil");
            if (currentTime < unlockTime) {
                long secondsLeft = (unlockTime - currentTime) / 1000;
                throw new IllegalArgumentException("Account locked! Try again in " + secondsLeft + "s.");
            }
        }

        // --- [LOGIC 2] CHECK PASSWORD ---
        byte[] salt = B64.dec(u.getString("pwdSaltB64"));
        int iters = u.getInteger("pwdIters");
        byte[] expected = B64.dec(u.getString("pwdHashB64"));
        byte[] actual = PBKDF2.deriveKey(password, salt, iters, 32);

        if (!PBKDF2.constantTimeEquals(expected, actual)) {
            // -- [LOGIC 3] XỬ LÝ KHI SAI PASS --
            userDAO.incrementFailedAttempts(username);
            
            // Check xem đã vượt quá giới hạn chưa
            int currentFails = u.getInteger("failedAttempts", 0) + 1; // +1 vì vừa sai thêm phát nữa
            if (currentFails >= MAX_ATTEMPTS) {
                userDAO.lockAccount(username, currentTime + LOCK_TIME_MS);
                throw new IllegalArgumentException("Too many failed attempts. Account locked for 5 minutes.");
            }
            
            int attemptsLeft = MAX_ATTEMPTS - currentFails;
            throw new IllegalArgumentException("Invalid password. " + attemptsLeft + " attempts remaining.");
        }

        // --- [LOGIC 4] ĐĂNG NHẬP THÀNH CÔNG -> RESET ---
        userDAO.resetFailedAttempts(username);
        
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
    
    public void changePassword(String username, char[] oldPass, char[] newPass) {
        // 1. Xác thực mật khẩu cũ (Logic giống Login)
        Document u = userDAO.findByUsername(username);
        if (u == null) throw new IllegalArgumentException("User not found");

        byte[] salt = B64.dec(u.getString("pwdSaltB64"));
        int iters = u.getInteger("pwdIters");
        byte[] expected = B64.dec(u.getString("pwdHashB64"));
        byte[] actual = PBKDF2.deriveKey(oldPass, salt, iters, 32);

        if (!PBKDF2.constantTimeEquals(expected, actual)) {
            throw new IllegalArgumentException("Mật khẩu cũ không đúng!");
        }

        // 2. Giải mã Private Keys bằng mật khẩu CŨ
        var signBlobOld = new KeyProtector.ProtectedBlob(
                u.getString("signPrivEncB64"), u.getString("signPrivIvB64"),
                u.getString("signPrivSaltKdfB64"), u.getInteger("signPrivKdfIters"));
        var ecdhBlobOld = new KeyProtector.ProtectedBlob(
                u.getString("ecdhPrivEncB64"), u.getString("ecdhPrivIvB64"),
                u.getString("ecdhPrivSaltKdfB64"), u.getInteger("ecdhPrivKdfIters"));

        byte[] signPrivBytes = KeyProtector.unprotect(signBlobOld, oldPass);
        byte[] ecdhPrivBytes = KeyProtector.unprotect(ecdhBlobOld, oldPass);

        // 3. Tạo Hash cho mật khẩu MỚI
        byte[] newSalt = Rand.bytes(16);
        byte[] newHash = PBKDF2.deriveKey(newPass, newSalt, iters, 32);

        // 4. Mã hóa lại Private Keys bằng mật khẩu MỚI
        var signBlobNew = KeyProtector.protect(signPrivBytes, newPass);
        var ecdhBlobNew = KeyProtector.protect(ecdhPrivBytes, newPass);

        // 5. Lưu xuống DB
        userDAO.updateUserCredentials(username, 
                B64.enc(newSalt), B64.enc(newHash),
                signBlobNew.ciphertextB64(), signBlobNew.ivB64(), signBlobNew.saltB64(), signBlobNew.iters(),
                ecdhBlobNew.ciphertextB64(), ecdhBlobNew.ivB64(), ecdhBlobNew.saltB64(), ecdhBlobNew.iters()
        );
    }
    public void updateProfileInfo(String username, String email, String fullName, String address, String gender) {
    // Mã hóa lại toàn bộ trước khi lưu
    String encEmail = profileSec.encrypt(email);
    String encFullName = profileSec.encrypt(fullName);
    String encAddress = profileSec.encrypt(address);
    String encGender = profileSec.encrypt(gender);
    userDAO.updateFullProfile(username, encEmail, encFullName, encAddress, encGender);
}

// Hàm cập nhật Avatar
public void updateAvatar(String username, byte[] imageBytes) {
    if (imageBytes == null || imageBytes.length == 0) return;
    // Chuyển ảnh sang Base64 rồi mã hóa AES System Key
    String base64Img = B64.enc(imageBytes);
    String encAvatar = profileSec.encrypt(base64Img);
    userDAO.updateAvatar(username, encAvatar);
}

//  Hàm lấy Avatar (Trả về bytes ảnh để hiển thị)
public byte[] getUserAvatar(String username) {
    String encAvatar = userDAO.getEncryptedAvatar(username);
    if (encAvatar == null) return null;
    
    try {
        // Giải mã AES -> Base64 -> Bytes
        String base64Img = profileSec.decrypt(encAvatar);
        return B64.dec(base64Img);
    } catch (Exception e) {
        return null;
    }
}
}