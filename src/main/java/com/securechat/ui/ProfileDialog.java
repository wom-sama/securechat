package com.securechat.ui;

import com.securechat.service.AuthService;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.ExecutionException;

public class ProfileDialog extends JDialog {
    private final AuthService authService = new AuthService();
    private final String username;
    
    // UI Components for Info
    private final JTextField txtEmail = new JTextField(20);
    private final JTextField txtFullname = new JTextField(20);
    private final JTextField txtAddress = new JTextField(20);
    private final JComboBox<String> cboGender = new JComboBox<>(new String[]{"Nam", "Nữ", "Khác"});
    private final JLabel lblAvatar = new JLabel();
    private final JButton btnUploadAvatar = new JButton("Đổi ảnh");

    // UI Components for Password
    private final JPasswordField txtOldPass = new JPasswordField(20);
    private final JPasswordField txtNewPass = new JPasswordField(20);
    private final JPasswordField txtConfirm = new JPasswordField(20);

    public ProfileDialog(JFrame parent, String username) {
        super(parent, "Hồ sơ cá nhân: " + username, true);
        this.username = username;
        setSize(500, 480);
        setLocationRelativeTo(parent);
        
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Thông tin chung", createInfoPanel());
        tabs.addTab("Bảo mật & Mật khẩu", createSecurityPanel());
        
        add(tabs);
        
        loadProfileData(); // Load dữ liệu khi mở form
    }

    private JPanel createInfoPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // --- Avatar Section ---
        JPanel pnlAvatar = new JPanel(new FlowLayout(FlowLayout.CENTER));
        lblAvatar.setPreferredSize(new Dimension(80, 80));
        // Icon mặc định ban đầu
        lblAvatar.setIcon(ImageUtils.createCircularAvatar(null, 80));
        
        pnlAvatar.add(lblAvatar);
        pnlAvatar.add(btnUploadAvatar);
        
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        p.add(pnlAvatar, gbc);

        // --- Fields ---
        gbc.gridwidth = 1;
        addField(p, gbc, 1, "Họ tên:", txtFullname);
        addField(p, gbc, 2, "Email:", txtEmail);
        addField(p, gbc, 3, "Địa chỉ:", txtAddress);
        
        gbc.gridx = 0; gbc.gridy = 4; gbc.weightx = 0;
        p.add(new JLabel("Giới tính:"), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(cboGender, gbc);

        // --- Save Button ---
        JButton btnSaveInfo = new JButton("Lưu thông tin");
        btnSaveInfo.setBackground(new Color(0, 102, 204));
        btnSaveInfo.setForeground(Color.BLUE);
        btnSaveInfo.setFont(new Font("Segoe UI", Font.BOLD, 12));
        
        gbc.gridx = 1; gbc.gridy = 5; gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        p.add(btnSaveInfo, gbc);

        // --- Events ---
        btnUploadAvatar.addActionListener(e -> onUploadAvatar());
        btnSaveInfo.addActionListener(e -> onSaveInfo());

        return p;
    }

    private JPanel createSecurityPanel() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(new EmptyBorder(20, 20, 20, 20));
        GridBagConstraints gbc = new GridBagConstraints(); 
        gbc.insets = new Insets(8, 5, 8, 5); 
        gbc.fill = GridBagConstraints.HORIZONTAL;
        
        addField(p, gbc, 0, "Mật khẩu cũ:", txtOldPass);
        addField(p, gbc, 1, "Mật khẩu mới:", txtNewPass);
        addField(p, gbc, 2, "Xác nhận:", txtConfirm);
        
        JButton btnChangePass = new JButton("Đổi mật khẩu");
        btnChangePass.setBackground(new Color(200, 50, 50));
        btnChangePass.setForeground(Color.BLUE);
        
        gbc.gridx = 1; gbc.gridy = 3; gbc.anchor = GridBagConstraints.EAST;
        gbc.fill = GridBagConstraints.NONE;
        p.add(btnChangePass, gbc);
        
        btnChangePass.addActionListener(e -> onChangePass());
        return p;
    }

    private void addField(JPanel p, GridBagConstraints gbc, int y, String label, Component comp) {
        gbc.gridx = 0; gbc.gridy = y; gbc.weightx = 0;
        p.add(new JLabel(label), gbc);
        gbc.gridx = 1; gbc.weightx = 1;
        p.add(comp, gbc);
    }

    // --- LOGIC ---
    private void loadProfileData() {
        new SwingWorker<Void, Void>() {
            AuthService.UserProfileDTO profile;
            byte[] avatarBytes;
            @Override protected Void doInBackground() {
                profile = authService.getProfile(username);
                avatarBytes = authService.getUserAvatar(username);
                return null;
            }
            @Override protected void done() {
                if (profile != null) {
                    txtEmail.setText(profile.email());
                    txtFullname.setText(profile.fullName());
                    txtAddress.setText(profile.address());
                    cboGender.setSelectedItem(profile.gender());
                }
                lblAvatar.setIcon(ImageUtils.createCircularAvatar(avatarBytes, 80));
            }
        }.execute();
    }

    private void onUploadAvatar() {
        JFileChooser ch = new JFileChooser();
        ch.setFileFilter(new FileNameExtensionFilter("Image files", "jpg", "png", "jpeg"));
        if (ch.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = ch.getSelectedFile();
            if (f.length() > 2 * 1024 * 1024) { // Max 2MB avatar
                JOptionPane.showMessageDialog(this, "Ảnh quá lớn (>2MB).");
                return;
            }
            try {
                byte[] bytes = Files.readAllBytes(f.toPath());
                // Preview ngay lập tức
                lblAvatar.setIcon(ImageUtils.createCircularAvatar(bytes, 80));
                // Upload ngầm
                new SwingWorker<Void, Void>(){
                    @Override protected Void doInBackground() { authService.updateAvatar(username, bytes); return null; }
                }.execute();
            } catch (IOException ex) {}
        }
    }

    private void onSaveInfo() {
        String email = txtEmail.getText();
        String name = txtFullname.getText();
        String addr = txtAddress.getText();
        String gender = cboGender.getSelectedItem().toString();
        
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                authService.updateProfileInfo(username, email, name, addr, gender);
                return null;
            }
            @Override protected void done() { JOptionPane.showMessageDialog(ProfileDialog.this, "Cập nhật thành công!"); }
        }.execute();
    }
    
    private void onChangePass() {
        char[] oldP = txtOldPass.getPassword();
        char[] newP = txtNewPass.getPassword();
        char[] confP = txtConfirm.getPassword();
        
        if (newP.length == 0 || !new String(newP).equals(new String(confP))) {
            JOptionPane.showMessageDialog(this, "Mật khẩu mới không khớp hoặc bị trống!");
            return;
        }
        
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() {
                authService.changePassword(username, oldP, newP);
                return null;
            }
            @Override protected void done() {
                try {
                    get();
                    JOptionPane.showMessageDialog(ProfileDialog.this, "Đổi mật khẩu thành công!");
                } catch (InterruptedException | ExecutionException e) {
                    JOptionPane.showMessageDialog(ProfileDialog.this, "Lỗi: " + e.getMessage());
                }
            }
        }.execute();
    }
}