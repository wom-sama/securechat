package com.securechat.ui;

import com.securechat.service.AuthService;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.ExecutionException;

public class LoginForm extends JFrame {
    private final AuthService auth = new AuthService();

    private final JTextField txtUser = new JTextField();
    private final JPasswordField txtPass = new JPasswordField();
    private final JButton btnLogin = new JButton("Đăng nhập");
    private final JButton btnRegister = new JButton("Đăng ký tài khoản");
    private final JLabel lblStatus = new JLabel(" ", SwingConstants.CENTER);

    public LoginForm() {
        super("SecureChat - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(400, 480);
        setLocationRelativeTo(null);
        setResizable(false);

        // Panel chính dùng BorderLayout
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);
        setContentPane(mainPanel);

        // --- 1. HEADER (Tiêu đề) ---
        JPanel headerPanel = new JPanel(new GridLayout(2, 1));
        headerPanel.setBackground(new Color(240, 248, 255)); // Màu xanh nhạt
        headerPanel.setBorder(new EmptyBorder(30, 0, 30, 0));
        
        JLabel lblTitle = new JLabel("SecureChat", SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 32));
        lblTitle.setForeground(new Color(0, 102, 204));
        
        JLabel lblSubtitle = new JLabel("============================", SwingConstants.CENTER);
        lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        lblSubtitle.setForeground(Color.GRAY);
        
        headerPanel.add(lblTitle);
        headerPanel.add(lblSubtitle);
        mainPanel.add(headerPanel, BorderLayout.NORTH);

        // --- 2. FORM (Ô nhập liệu) ---
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setBackground(Color.WHITE);
        formPanel.setBorder(new EmptyBorder(20, 40, 20, 40)); 

        // Style chung cho Input
        Dimension inputSize = new Dimension(Integer.MAX_VALUE, 40);
        Font inputFont = new Font("Segoe UI", Font.PLAIN, 14);

        // Username
        JLabel lblUser = new JLabel("Tên đăng nhập:");
        lblUser.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblUser.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        txtUser.setMaximumSize(inputSize);
        txtUser.setPreferredSize(new Dimension(300, 40));
        txtUser.setFont(inputFont);
        txtUser.setAlignmentX(Component.LEFT_ALIGNMENT);
        // Padding bên trong text field
        txtUser.setBorder(BorderFactory.createCompoundBorder(
            txtUser.getBorder(), 
            BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        // Password
        JLabel lblPass = new JLabel("Mật khẩu:");
        lblPass.setFont(new Font("Segoe UI", Font.BOLD, 12));
        lblPass.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        txtPass.setMaximumSize(inputSize);
        txtPass.setPreferredSize(new Dimension(300, 40));
        txtPass.setFont(inputFont);
        txtPass.setAlignmentX(Component.LEFT_ALIGNMENT);
        txtPass.setBorder(BorderFactory.createCompoundBorder(
            txtPass.getBorder(), 
            BorderFactory.createEmptyBorder(5, 5, 5, 5)));

        // Thêm vào form
        formPanel.add(lblUser);
        formPanel.add(Box.createVerticalStrut(5));
        formPanel.add(txtUser);
        formPanel.add(Box.createVerticalStrut(15));
        formPanel.add(lblPass);
        formPanel.add(Box.createVerticalStrut(5));
        formPanel.add(txtPass);
        formPanel.add(Box.createVerticalStrut(25));

        // --- 3. BUTTONS ---
        btnLogin.setMaximumSize(inputSize);
        btnLogin.setBackground(new Color(0, 120, 215)); // Màu xanh Windows
        btnLogin.setForeground(Color.BLUE);
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnLogin.setFocusPainted(false);
        btnLogin.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnLogin.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnRegister.setMaximumSize(inputSize);
        btnRegister.setBackground(Color.WHITE);
        btnRegister.setForeground(new Color(0, 100, 0)); // Màu xanh lá đậm
        btnRegister.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        btnRegister.setBorder(BorderFactory.createLineBorder(new Color(200, 200, 200)));
        btnRegister.setFocusPainted(false);
        btnRegister.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRegister.setAlignmentX(Component.LEFT_ALIGNMENT);

        formPanel.add(btnLogin);
        formPanel.add(Box.createVerticalStrut(10));
        
        // Separator có chữ "hoặc"
        JPanel sepPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        sepPanel.setBackground(Color.WHITE);
        sepPanel.add(new JLabel("- hoặc -"));
        sepPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        sepPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
        
        formPanel.add(sepPanel);
        formPanel.add(Box.createVerticalStrut(10));
        formPanel.add(btnRegister);

        mainPanel.add(formPanel, BorderLayout.CENTER);

        // --- 4. FOOTER (Status) ---
        lblStatus.setPreferredSize(new Dimension(getWidth(), 30));
        lblStatus.setForeground(Color.RED);
        lblStatus.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        mainPanel.add(lblStatus, BorderLayout.SOUTH);

        // --- EVENTS ---
        btnRegister.addActionListener(e -> new RegisterDialog(this).setVisible(true));

        ActionListener loginAction = e -> doLoginProcess();
        btnLogin.addActionListener(loginAction);
        txtPass.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) doLoginProcess();
            }
        });
    }

    private void doLoginProcess() {
        if (!btnLogin.isEnabled()) return;
        String u = txtUser.getText().trim();
        char[] pw = txtPass.getPassword();

        if (u.isEmpty() || pw.length == 0) {
            lblStatus.setText("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        // Disable UI
        btnLogin.setEnabled(false);
        btnRegister.setEnabled(false);
        txtUser.setEnabled(false);
        txtPass.setEnabled(false);
        btnLogin.setText("Đang xác thực...");
        lblStatus.setText(" ");

        new SwingWorker<AuthService.Session, Void>() {
            @Override
            protected AuthService.Session doInBackground() throws Exception {
                return auth.login(u, pw);
            }

            @Override
            protected void done() {
                try {
                    var session = get();
                    // Login thành công
                    new ChatForm(session).setVisible(true);
                    dispose();
                } catch (InterruptedException | ExecutionException ex) {
                    // Login thất bại -> Reset UI
                    btnLogin.setEnabled(true);
                    btnRegister.setEnabled(true);
                    txtUser.setEnabled(true);
                    txtPass.setEnabled(true);
                    btnLogin.setText("Đăng nhập");
                    
                    String msg = ex.getMessage();
                    if (ex.getCause() != null) msg = ex.getCause().getMessage();
                    
                    lblStatus.setText("Lỗi: " + msg);
                    txtPass.requestFocus(); 
                }
            }
        }.execute();
    }
}