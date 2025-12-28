/* ChatForm.java - Bản Fix Offline Message + Notification Leak */
package com.securechat.ui;

import com.securechat.model.DecryptedMessage;
import com.securechat.service.AuthService;
import com.securechat.service.ChatService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class ChatForm extends JFrame {
    private final AuthService.Session session;
    private final ChatService chatService = new ChatService();
    private final AuthService authService = new AuthService();
    
    private Timer heartbeatTimer;
    private Timer pollingTimer; 
    private long lastCheckTime; 
    
    private String currentPartner = null;

    // [MỚI] Set chứa danh sách những người có tin nhắn chưa đọc
    private final Set<String> unreadSenders = new HashSet<>();

    private final DefaultListModel<String> contactListModel = new DefaultListModel<>();
    private final JList<String> contactList = new JList<>(contactListModel);
    
    private final JPanel chatAreaPanel = new JPanel();
    private final JScrollPane chatScrollPane = new JScrollPane(chatAreaPanel);
    private final JTextField txtMsg = new JTextField();
    private final JButton btnSend = new JButton("Gửi");
    private final JLabel lblCurrentPartner = new JLabel("Chọn một người để bắt đầu chat");
    private final JTextField txtNewContact = new JTextField(); 

    public ChatForm(AuthService.Session session) {
        super("SecureChat - " + session.username());
        this.session = session;
        
        // [QUAN TRỌNG] Chỉ Dispose, không Exit JVM để ta còn xử lý logout logic
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        setSize(900, 600);
        setLocationRelativeTo(null);

        // --- LAYOUT SETUP (Giữ nguyên) ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(250); 

        JPanel leftPanel = new JPanel(new BorderLayout());
        JPanel myInfoPanel = new JPanel(new GridLayout(2, 1));
        myInfoPanel.setBorder(new EmptyBorder(15, 15, 15, 15)); 
        myInfoPanel.setBackground(new Color(230, 240, 255));
        
        JLabel lblMyName = new JLabel("Tôi: " + session.username());
        lblMyName.setFont(new Font("Segoe UI", Font.BOLD, 16)); 
        lblMyName.setForeground(new Color(0, 102, 204));
        myInfoPanel.add(lblMyName);

        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.add(new JLabel("Chat với người mới: "), BorderLayout.NORTH);
        searchPanel.add(txtNewContact, BorderLayout.CENTER);
        JButton btnAddContact = new JButton("+");
        searchPanel.add(btnAddContact, BorderLayout.EAST);
        myInfoPanel.add(searchPanel);
        leftPanel.add(myInfoPanel, BorderLayout.NORTH);

        // Custom Renderer sử dụng Set unreadSenders
        contactList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contactList.setFixedCellHeight(40);
        contactList.setCellRenderer(new ContactRenderer());
        leftPanel.add(new JScrollPane(contactList), BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        headerPanel.setBackground(Color.WHITE);
        headerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        lblCurrentPartner.setFont(new Font("Segoe UI", Font.BOLD, 16));
        headerPanel.add(lblCurrentPartner);
        rightPanel.add(headerPanel, BorderLayout.NORTH);

        chatAreaPanel.setLayout(new BoxLayout(chatAreaPanel, BoxLayout.Y_AXIS));
        chatAreaPanel.setBackground(new Color(245, 245, 245));
        rightPanel.add(chatScrollPane, BorderLayout.CENTER);

        JPanel footerPanel = new JPanel(new BorderLayout(10, 10));
        footerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        txtMsg.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSend.setBackground(new Color(0, 120, 215));
        btnSend.setForeground(Color.WHITE);
        
        footerPanel.add(txtMsg, BorderLayout.CENTER);
        footerPanel.add(btnSend, BorderLayout.EAST);
        rightPanel.add(footerPanel, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        add(splitPane);

        // --- EVENTS ---
        contactList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                String selected = contactList.getSelectedValue();
                if (selected != null) {
                    // Xóa trạng thái unread khi click vào
                    if (unreadSenders.contains(selected)) {
                        unreadSenders.remove(selected);
                        contactList.repaint(); // Vẽ lại để mất chấm đỏ
                    }
                    switchToChat(selected);
                    txtNewContact.setText(""); 
                }
            }
        });
        
        btnAddContact.addActionListener(e -> {
            String newMate = txtNewContact.getText().trim();
            if (newMate.isEmpty() || newMate.equals(session.username())) return;
            btnAddContact.setEnabled(false); 
            new SwingWorker<Boolean, Void>() {
                @Override protected Boolean doInBackground() { return chatService.checkUserExists(newMate); }
                @Override protected void done() {
                    btnAddContact.setEnabled(true);
                    try {
                        if (get()) { switchToChat(newMate); txtNewContact.setText(""); }
                        else JOptionPane.showMessageDialog(ChatForm.this, "User not found!", "Error", JOptionPane.ERROR_MESSAGE);
                    } catch (Exception ex) { }
                }
            }.execute();
        });

        btnSend.addActionListener(e -> onSend());
        txtMsg.addActionListener(e -> onSend()); 

        // --- STARTUP LOGIC ---
        // 1. Kiểm tra tin nhắn Offline (Tính từ lần logout cuối cùng)
        checkOfflineMessages();
        
        // 2. Bắt đầu các Timer
        startHeartbeat();       
        startMessagePolling();  
        loadContactList();
    }
    
    // [FIX] Ghi đè dispose để dọn dẹp sạch sẽ khi thoát
    @Override
    public void dispose() {
        // 1. Dừng Timers ngay lập tức để không hiện thông báo rác
        if (heartbeatTimer != null) heartbeatTimer.stop();
        if (pollingTimer != null) pollingTimer.stop();
        
        // 2. Lưu thời gian logout để lần sau check tin nhắn offline
        new Thread(() -> authService.logout(session.username())).start();
        
        super.dispose();
        // Nếu đây là cửa sổ cuối cùng, thoát JVM (hoặc quay về Login thì tùy logic gọi)
    }

    // [MỚI] Logic check tin nhắn khi Offline
    private void checkOfflineMessages() {
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                long lastLogout = authService.getLastLogoutTime(session.username());
                // Nếu chưa từng logout (lần đầu dùng), thì coi như xem từ đầu
                return chatService.checkNewMessages(session.username(), lastLogout); 
            }
            @Override
            protected void done() {
                try {
                    List<String> offlineSenders = get();
                    if (!offlineSenders.isEmpty()) {
                        unreadSenders.addAll(offlineSenders);
                        contactList.repaint(); // Cập nhật chấm đỏ
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }.execute();
    }

    // Renderer kiểm tra trong Set
    private class ContactRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String username = (String) value;
            lbl.setText(username);
            lbl.setBorder(new EmptyBorder(0, 10, 0, 10));
            
            // Check trong Set unread
            if (unreadSenders.contains(username)) {
                lbl.setText(username + " ●");
                lbl.setForeground(isSelected ? Color.WHITE : Color.RED);
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            } else {
                lbl.setForeground(isSelected ? Color.WHITE : Color.BLACK);
            }
            return lbl;
        }
    }

    private void loadContactList() {
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() { return chatService.getRecentContacts(session.username()); }
            @Override protected void done() {
                try {
                    List<String> contacts = get();
                    String currentSelection = contactList.getSelectedValue();
                    contactListModel.clear();
                    for (String c : contacts) contactListModel.addElement(c);
                    if (currentSelection != null && contacts.contains(currentSelection)) {
                        contactList.setSelectedValue(currentSelection, true);
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }.execute();
    }
    
    private void switchToChat(String partner) {
        this.currentPartner = partner;
        lblCurrentPartner.setText("Đang chat với: " + partner);
        loadConversation();
    }
    
    private void loadConversation() {
        if (currentPartner == null) return;
        new SwingWorker<List<DecryptedMessage>, Void>() {
            @Override protected List<DecryptedMessage> doInBackground() { return chatService.loadConversation(session, currentPartner, 50); }
            @Override protected void done() { try { updateChatUI(get()); } catch (Exception e) { e.printStackTrace(); } }
        }.execute();
    }

    private void updateChatUI(List<DecryptedMessage> msgs) {
        // [FIX] Cập nhật UI thông minh hơn: Chỉ vẽ lại nếu có tin nhắn mới thực sự
        // Ở đây đơn giản hóa bằng cách so sánh số lượng
        int currentCount = chatAreaPanel.getComponentCount() - 1; // Trừ đi cái Glue ở đầu
        // Mỗi tin nhắn gồm 2 component (Bubble + Strut)
        if (currentCount > 0 && msgs.size() * 2 == currentCount) return;

        JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
        boolean isAtBottom = vertical.getValue() >= (vertical.getMaximum() - vertical.getVisibleAmount() - 50);

        chatAreaPanel.removeAll();
        chatAreaPanel.add(Box.createVerticalGlue()); 
        
        for (DecryptedMessage m : msgs) {
            addMessageBubble(m);
        }
        chatAreaPanel.revalidate();
        chatAreaPanel.repaint();

        if (isAtBottom) SwingUtilities.invokeLater(() -> vertical.setValue(vertical.getMaximum()));
    }
    
    private void addMessageBubble(DecryptedMessage m) {
        boolean isMe = m.from.equals(session.username());
        JPanel rowPanel = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT));
        rowPanel.setBackground(new Color(245, 245, 245));
        rowPanel.setOpaque(false);
        rowPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 100)); 
        
        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12));
        bubble.setBackground(isMe ? new Color(220, 248, 198) : Color.WHITE);
        bubble.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true));
        
        JLabel lblContent = new JLabel("<html><body style='width: 200px'>" + m.plaintext + "</body></html>");
        lblContent.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        String statusText = m.signatureValid ? "[Valid]" : "[Invalid]";
        Color statusColor = m.signatureValid ? new Color(0, 128, 0) : Color.RED;
        
        JLabel lblMeta = new JLabel(statusText); 
        lblMeta.setFont(new Font("SansSerif", Font.BOLD, 10));
        lblMeta.setForeground(statusColor);
        
        JLabel lblTime = new JLabel(new SimpleDateFormat("HH:mm").format(new Date(m.ts)));
        lblTime.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lblTime.setForeground(Color.GRAY);
        
        JPanel metaPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        metaPanel.setOpaque(false);
        metaPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        metaPanel.add(lblTime);
        metaPanel.add(lblMeta);

        bubble.add(lblContent);
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(metaPanel);
        
        rowPanel.add(bubble);
        chatAreaPanel.add(rowPanel);
        chatAreaPanel.add(Box.createVerticalStrut(10)); 
    }

    private void onSend() {
        if (currentPartner == null) { JOptionPane.showMessageDialog(this, "Vui lòng chọn người nhận trước!"); return; }
        String text = txtMsg.getText().trim();
        if (text.isEmpty()) return;
        txtMsg.setText("");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() { chatService.sendMessage(session, currentPartner, text); return null; }
            @Override protected void done() { try { get(); loadConversation(); loadContactList(); } catch (Exception e) { JOptionPane.showMessageDialog(ChatForm.this, "Lỗi: " + e.getMessage()); } }
        }.execute();
    }
    
    // --- REAL-TIME & NOTIFICATION ---
    private void startMessagePolling() {
        lastCheckTime = System.currentTimeMillis();
        pollingTimer = new Timer(2000, e -> {
            if (currentPartner != null) loadConversation();
            checkNotifications();
        });
        pollingTimer.start();
    }
    
    private void checkNotifications() {
        long now = System.currentTimeMillis();
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() { return chatService.checkNewMessages(session.username(), lastCheckTime); }
            @Override protected void done() {
                try {
                    List<String> senders = get();
                    boolean needRefresh = false;
                    for (String sender : senders) {
                        // Nếu tin nhắn đến từ người KHÔNG phải người đang chat
                        if (currentPartner == null || !currentPartner.equals(sender)) {
                            // [FIX] Tin nhắn đầu tiên cũng hiện Toast và chấm đỏ
                            if (!unreadSenders.contains(sender)) {
                                showToast("Tin nhắn mới từ: " + sender);
                                unreadSenders.add(sender);
                                needRefresh = true;
                            }
                        }
                    }
                    if (!senders.isEmpty()) {
                        lastCheckTime = now;
                        // Nếu người mới chưa có trong list -> Load lại list
                        loadContactList();
                    }
                    if (needRefresh) contactList.repaint();
                    
                } catch (Exception e) { }
            }
        }.execute();
    }
    
    private void showToast(String msg) {
        JWindow toast = new JWindow();
        toast.setBackground(new Color(40, 40, 40, 220)); 
        JLabel lbl = new JLabel(msg);
        lbl.setForeground(Color.WHITE);
        lbl.setBorder(new EmptyBorder(10, 20, 10, 20));
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        toast.add(lbl);
        toast.pack();
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        toast.setLocation(scr.width - toast.getWidth() - 20, scr.height - toast.getHeight() - 50);
        toast.setAlwaysOnTop(true);
        toast.setVisible(true);
        new Timer(5000, e -> { toast.dispose(); ((Timer)e.getSource()).stop(); }).start();
    }
    
    private void startHeartbeat() { heartbeatTimer = new Timer(3000, e -> checkSessionStatus()); heartbeatTimer.start(); }
    
    private void checkSessionStatus() {
         new SwingWorker<Boolean, Void>() {
            @Override protected Boolean doInBackground() { return authService.isSessionValid(session.username(), session.sessionId()); }
            @Override protected void done() { try { if (!get()) { 
                // [FIX] Dọn dẹp sạch sẽ khi bị đá
                dispose(); 
                JOptionPane.showMessageDialog(null, "Phiên đăng nhập hết hạn!"); 
                new LoginForm().setVisible(true); 
            } } catch (Exception e) { } }
        }.execute();
    }
}