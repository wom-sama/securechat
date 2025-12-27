/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package com.securechat.ui;

import com.securechat.model.DecryptedMessage;
import com.securechat.service.AuthService;
import com.securechat.service.ChatService;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class ChatForm extends JFrame {
    private final AuthService.Session session;
    private final ChatService chat = new ChatService();
    
    private final AuthService authService = new AuthService(); 
    private Timer heartbeatTimer;

    private final JTextField txtTo = new JTextField(16);
    private final JTextField txtMsg = new JTextField(24);
    private final JButton btnSend = new JButton("Send");
    private final JButton btnRefresh = new JButton("Refresh Inbox");
    private final JTextArea area = new JTextArea();

    public ChatForm(AuthService.Session session) {
        super("SecureChat - " + session.username());
        this.session = session;

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(680, 420);
        setLocationRelativeTo(null);

        area.setEditable(false);

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("To:"));
        top.add(txtTo);
        top.add(new JLabel("Message:"));
        top.add(txtMsg);
        top.add(btnSend);
        top.add(btnRefresh);

        add(top, BorderLayout.NORTH);
        add(new JScrollPane(area), BorderLayout.CENTER);

        btnSend.addActionListener(e -> onSend());
        btnRefresh.addActionListener(e -> loadInbox());

        startHeartbeat();
        loadInbox();
    }
    private void startHeartbeat() {
        heartbeatTimer = new Timer(3000, e -> checkSessionStatus());
        heartbeatTimer.start();
    }
    
    private void checkSessionStatus() {
        new SwingWorker<Boolean, Void>() {
            @Override
            protected Boolean doInBackground() {
                return authService.isSessionValid(session.username(), session.sessionId());
            }

            @Override
            protected void done() {
                try {
                    boolean isValid = get();
                    if (!isValid) {
                        heartbeatTimer.stop();
                        JOptionPane.showMessageDialog(ChatForm.this, 
                                "Tài khoản của bạn đã được đăng nhập ở nơi khác!\nỨng dụng sẽ tự đăng xuất.",
                                "Session Expired",
                                JOptionPane.WARNING_MESSAGE);
                        
                        new LoginForm().setVisible(true);
                        dispose();
                    }
                } catch (Exception ex) {
                    System.out.println("Heartbeat check failed: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void onSend() {
    String to = txtTo.getText().trim();
    String msg = txtMsg.getText();

    btnSend.setEnabled(false);

    new SwingWorker<Void, Void>() {
        @Override
        protected Void doInBackground() throws Exception {
            chat.sendMessage(session, to, msg);
            return null;
        }

        @Override
        protected void done() {
            btnSend.setEnabled(true);
            try {
                get();
                txtMsg.setText("");
                loadInbox(); 
            } catch (Exception ex) {
                area.append("\n[SEND FAILED] " + ex.getMessage() + "\n");
            }
        }
    }.execute();
    }

    private void loadInbox() {
        new SwingWorker<List<DecryptedMessage>, Void>() {

        @Override
        protected List<DecryptedMessage> doInBackground() throws Exception {
            return chat.loadInbox(session, 30);
        }

        @Override
        protected void done() {
            try {
                List<DecryptedMessage> inbox = get();
                area.setText("");
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

                for (DecryptedMessage m : inbox) {
                    String time = df.format(new Date(m.ts));
                    area.append("[" + time + "] " + m.from + " -> " + m.to + "\n");
                    area.append("  " + m.plaintext + "\n");
                    area.append("  Signature: " +
                            (m.signatureValid ? "VALID" : "INVALID") + "\n\n");
                }
            } catch (Exception ex) {
                area.append("\n[LOAD FAILED] " + ex.getMessage() + "\n");
            }
        }
    }.execute();
    }
}
