/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.ui;
import com.securechat.service.AuthService;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class LoginForm extends JFrame {
    private final AuthService auth = new AuthService();

    private final JTextField txtUser = new JTextField(18);
    private final JPasswordField txtPass = new JPasswordField(18);
    private final JButton btnLogin = new JButton("Login");
    private final JButton btnRegister = new JButton("Register");
    private final JLabel lbl = new JLabel(" ");

    public LoginForm() {
        super("SecureChat - Login");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(380, 220);
        setLocationRelativeTo(null);

        JPanel p = new JPanel(new GridLayout(0, 2, 8, 8));
        p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        p.add(new JLabel("Username:"));
        p.add(txtUser);
        p.add(new JLabel("Password:"));
        p.add(txtPass);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btns.add(btnRegister);
        btns.add(btnLogin);
        
       ActionListener loginAction = e -> doLoginProcess();

        btnLogin.addActionListener(loginAction);
        
        txtPass.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    doLoginProcess();
                }
            }
        });

        add(p, BorderLayout.CENTER);
        add(btns, BorderLayout.SOUTH);
        add(lbl, BorderLayout.NORTH);

        btnLogin.addActionListener(e -> onLogin());
        btnRegister.addActionListener(e -> onRegister());
    }
    
    

    private void onRegister() {
         String u = txtUser.getText().trim();
    char[] pw = txtPass.getPassword();

    btnRegister.setEnabled(false);
    btnLogin.setEnabled(false);
    lbl.setText("Registering (crypto + NoSQL)...");

    new SwingWorker<Void, Void>() {

        @Override
        protected Void doInBackground() throws Exception {
            auth.register(u, pw);
            return null;
        }

        @Override
        protected void done() {
            btnRegister.setEnabled(true);
            btnLogin.setEnabled(true);
            try {
                get();
                lbl.setText("Registered OK. Now login.");
            } catch (Exception ex) {
                lbl.setText("Register failed: " + ex.getMessage());
            }
        }
    }.execute();
    }

    private void onLogin() {
    String u = txtUser.getText().trim();
    char[] pw = txtPass.getPassword();
    btnLogin.setEnabled(false);
    btnRegister.setEnabled(false);
    lbl.setText("Verifying...");
    new SwingWorker<AuthService.Session, Void>() {
        @Override
        protected AuthService.Session doInBackground() throws Exception {
            return auth.login(u, pw);
        }

        @Override
        protected void done() {
            try {
                var session = get(); 
                new ChatForm(session).setVisible(true);
                dispose(); 
            } catch (Exception ex) {
                btnLogin.setEnabled(true);
                btnRegister.setEnabled(true);
                lbl.setText("Login failed: " + ex.getMessage());
            }
        }
    }.execute();
}
    private void doLoginProcess() {
        if (!btnLogin.isEnabled()) return;

        String u = txtUser.getText().trim();
        char[] pw = txtPass.getPassword();

        btnLogin.setEnabled(false);
        btnRegister.setEnabled(false);
        txtUser.setEnabled(false); 
        txtPass.setEnabled(false);
        lbl.setText("Verifying...");

        new SwingWorker<AuthService.Session, Void>() {
            @Override
            protected AuthService.Session doInBackground() throws Exception {
                return auth.login(u, pw);
            }

            @Override
            protected void done() {
                try {
                    var session = get();
                    new ChatForm(session).setVisible(true);
                    dispose();
                } catch (Exception ex) {
                    btnLogin.setEnabled(true);
                    btnRegister.setEnabled(true);
                    txtUser.setEnabled(true);
                    txtPass.setEnabled(true);
                    lbl.setText("Login failed: " + ex.getMessage());
                    txtPass.requestFocus(); 
                }
            }
        }.execute();
    }
    
    
    
}
