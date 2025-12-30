package com.securechat.ui;

import com.securechat.service.AuthService;
import com.securechat.service.CaptchaProvider.CaptchaChallenge;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.concurrent.ExecutionException;

public class RegisterDialog extends JDialog {
    private final AuthService authService = new AuthService();
    
    // Các trường nhập liệu
    private final JTextField txtUser = new JTextField(20);
    private final JPasswordField txtPass = new JPasswordField(20);
    private final JTextField txtFullname = new JTextField(20);
    private final JTextField txtEmail = new JTextField(20);
    private final JTextField txtAddress = new JTextField(20);
    private final JComboBox<String> cboGender = new JComboBox<>(new String[]{"Nam", "Nữ", "Khác"});
    
    // Phần Captcha
    private final JLabel lblCaptchaQuestion = new JLabel("Đang tải...");
    private final JTextField txtCaptchaAns = new JTextField(10);
    private String currentCaptchaId = "";
    
    private final JButton btnRegister = new JButton("Đăng ký");
    private final JButton btnCancel = new JButton("Hủy");

    public RegisterDialog(Frame parent) {
        super(parent, "Đăng ký tài khoản mới", true);
        setSize(400, 500);
        setLocationRelativeTo(parent);
        
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        setContentPane(panel);
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        // --- ADD FIELDS ---
        addField(panel, gbc, 0, "Username (*):", txtUser);
        addField(panel, gbc, 1, "Password (*):", txtPass);
        addField(panel, gbc, 2, "Họ tên:", txtFullname);
        addField(panel, gbc, 3, "Email:", txtEmail);
        addField(panel, gbc, 4, "Địa chỉ:", txtAddress);
        
        // Gender
        gbc.gridx = 0; gbc.gridy = 5; gbc.weightx = 0;
        panel.add(new JLabel("Giới tính:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        panel.add(cboGender, gbc);
        
        // --- CAPTCHA SECTION ---
        JSeparator sep = new JSeparator();
        gbc.gridx = 0; gbc.gridy = 6; gbc.gridwidth = 2;
        panel.add(sep, gbc);
        
        JPanel pnlCaptcha = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pnlCaptcha.setBorder(BorderFactory.createTitledBorder("Xác thực người thật"));
        lblCaptchaQuestion.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblCaptchaQuestion.setForeground(Color.RED);
        pnlCaptcha.add(lblCaptchaQuestion);
        pnlCaptcha.add(txtCaptchaAns);
        
        JButton btnRefresh = new JButton("↻");
        btnRefresh.setToolTipText("Đổi câu hỏi khác");
        btnRefresh.addActionListener(e -> loadCaptcha());
        pnlCaptcha.add(btnRefresh);
        
        gbc.gridy = 7;
        panel.add(pnlCaptcha, gbc);
        
        // --- BUTTONS ---
        JPanel pnlBtn = new JPanel();
        pnlBtn.add(btnCancel);
        pnlBtn.add(btnRegister);
        gbc.gridy = 8;
        panel.add(pnlBtn, gbc);
        
        // --- ACTIONS ---
        btnCancel.addActionListener(e -> dispose());
        btnRegister.addActionListener(e -> onRegister());
        
        loadCaptcha(); // Tải câu hỏi ngay khi mở form
    }
    
    private void addField(JPanel p, GridBagConstraints gbc, int y, String label, Component comp) {
        gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0; gbc.gridwidth = 1;
        p.add(new JLabel(label), gbc);
        
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(comp, gbc);
    }
    
    private void loadCaptcha() {
        CaptchaChallenge challenge = authService.getCaptchaChallenge();
        this.currentCaptchaId = challenge.id();
        this.lblCaptchaQuestion.setText(challenge.question());
        this.txtCaptchaAns.setText("");
    }
    
    private void onRegister() {
        String u = txtUser.getText().trim();
        String p = new String(txtPass.getPassword());
        String capAns = txtCaptchaAns.getText().trim();
        
        if(u.isEmpty() || p.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username và Password không được để trống!");
            return;
        }
        if(capAns.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Vui lòng nhập kết quả Captcha!");
            return;
        }
        
        btnRegister.setEnabled(false);
        
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                authService.register(u, p.toCharArray(), 
                        txtEmail.getText(), txtFullname.getText(), txtAddress.getText(), 
                        cboGender.getSelectedItem().toString(),
                        currentCaptchaId, capAns);
                return null;
            }
            @Override
            protected void done() {
                btnRegister.setEnabled(true);
                try {
                    get(); // Check exception
                    JOptionPane.showMessageDialog(RegisterDialog.this, "Đăng ký thành công! Hãy đăng nhập.");
                    dispose(); // Đóng form
                } catch (HeadlessException | InterruptedException | ExecutionException e) {
                    JOptionPane.showMessageDialog(RegisterDialog.this, "Lỗi: " + e.getMessage(), "Đăng ký thất bại", JOptionPane.ERROR_MESSAGE);
                    loadCaptcha(); // Reset captcha nếu sai
                }
            }
        }.execute();
    }
}