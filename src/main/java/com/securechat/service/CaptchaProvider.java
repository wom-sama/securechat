/* CaptchaProvider.java */
package com.securechat.service;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CaptchaProvider {
    
    // Lưu trữ tạm thời: Map<CaptchaID, CorrectAnswer>
    // Dùng ConcurrentHashMap để an toàn đa luồng
    private static final Map<String, String> captchaStore = new ConcurrentHashMap<>();
    private static final SecureRandom random = new SecureRandom();

    // Record để trả về cho Client hiển thị
    public record CaptchaChallenge(String id, String question) {}

    /**
     * Bước 1: Tạo ra một câu đố.
     * Client sẽ gọi hàm này trước khi hiển thị Form đăng ký.
     * @return 
     */
    public CaptchaChallenge createChallenge() {
        int a = random.nextInt(10) + 1; // 1 -> 10
        int b = random.nextInt(10) + 1;
        
        // Giả lập câu đố toán học cho đơn giản
        String question = "What is " + a + " + " + b + " ?";
        String answer = String.valueOf(a + b);
        
        String id = UUID.randomUUID().toString();
        
        // Lưu đáp án đúng vào bộ nhớ Server
        captchaStore.put(id, answer);
        
        return new CaptchaChallenge(id, question);
    }

    /**
     * Bước 2: Verify (Được gọi bởi AuthService)
     * Nguyên tắc: Kiểm tra xong là XÓA ngay (One-time use) để tránh hacker dùng lại token cũ.
     * @param captchaId
     * @param userAnswer
     * @return 
     */
    public boolean verify(String captchaId, String userAnswer) {
        if (captchaId == null || userAnswer == null) return false;
        
        // Lấy đáp án đúng từ kho
        String correctAnswer = captchaStore.get(captchaId);
        
        if (correctAnswer == null) {
            // Không tìm thấy ID -> Có thể do hết hạn hoặc ID giả mạo
            return false; 
        }

        // Xóa ngay lập tức dù đúng hay sai (Cơ chế One-Time Token)
        captchaStore.remove(captchaId);

        return correctAnswer.equals(userAnswer.trim());
    }
}