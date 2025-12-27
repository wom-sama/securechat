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

        loadInbox();
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
                loadInbox(); // ok vì loadInbox cũng sẽ sửa tiếp
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
