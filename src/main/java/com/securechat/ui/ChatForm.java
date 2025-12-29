/* ChatForm.java - Bản Final: Tích hợp xóa Contact */
package com.securechat.ui;

import com.securechat.model.DecryptedMessage;
import com.securechat.service.AuthService;
import com.securechat.service.ChatService;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionListener;
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
    private final Set<String> unreadSenders = new HashSet<>();

    // UI Components
    private final DefaultListModel<ContactItem> contactListModel = new DefaultListModel<>();
    private final JList<ContactItem> contactList = new JList<>(contactListModel);
    private final JPanel chatAreaPanel = new JPanel();
    private final JScrollPane chatScrollPane = new JScrollPane(chatAreaPanel);
    private final JTextField txtMsg = new JTextField();
    private final JButton btnSend = new JButton("Gửi");
    private final JLabel lblCurrentPartner = new JLabel("Chọn một người để bắt đầu chat");
    private final JTextField txtNewContact = new JTextField(); 
    
    private final JComboBox<TTLItem> cboTTL = new JComboBox<>();
    
    // [MỚI] Menu chuột phải
    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem itemDeleteContact = new JMenuItem("Xóa liên hệ");

    private static class TTLItem {
        String label; long seconds;
        TTLItem(String l, long s) { label = l; seconds = s; }
        @Override public String toString() { return label; }
    }
    private static class ContactItem {
        String username; boolean hasUnread;
        ContactItem(String u) { username = u; }
        @Override public String toString() { return username; }
    }

    public ChatForm(AuthService.Session session) {
        super("SecureChat - " + session.username());
        this.session = session;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);

        // --- LAYOUT SETUP ---
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
        
        cboTTL.addItem(new TTLItem("Vĩnh viễn", 0));
        cboTTL.addItem(new TTLItem("1 Phút (Test)", 60));
        cboTTL.addItem(new TTLItem("1 Ngày", 86400));
        cboTTL.addItem(new TTLItem("1 Tháng", 2592000));
        cboTTL.addItem(new TTLItem("1 Năm", 31536000));
        cboTTL.setToolTipText("Tin nhắn tự hủy sau...");
        
        JPanel sendActionPanel = new JPanel(new BorderLayout());
        sendActionPanel.add(cboTTL, BorderLayout.WEST);
        sendActionPanel.add(btnSend, BorderLayout.CENTER);
        footerPanel.add(txtMsg, BorderLayout.CENTER);
        footerPanel.add(sendActionPanel, BorderLayout.EAST);
        rightPanel.add(footerPanel, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        add(splitPane);

        // --- CONTEXT MENU SETUP ---
        contextMenu.add(itemDeleteContact);
        itemDeleteContact.addActionListener(e -> deleteSelectedContact());

        // --- EVENTS ---
        
        // [FIX] MouseListener xử lý cả Click Trái (Chọn) và Phải (Menu)
        contactList.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { checkPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { checkPopup(e); }
            @Override
            public void mouseClicked(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int index = contactList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        ContactItem selected = contactListModel.getElementAt(index);
                        if (unreadSenders.contains(selected.username)) {
                            unreadSenders.remove(selected.username);
                            contactList.repaint();
                        }
                        switchToChat(selected.username);
                        txtNewContact.setText("");
                    }
                }
            }

            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    // Tự động chọn item dưới chuột khi click phải
                    int index = contactList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        contactList.setSelectedIndex(index);
                        contextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
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
                        else JOptionPane.showMessageDialog(ChatForm.this, "Không tìm thấy user!", "Lỗi", JOptionPane.ERROR_MESSAGE);
                    } catch (Exception ex) { }
                }
            }.execute();
        });

        ActionListener sendAction = e -> onSend();
        btnSend.addActionListener(sendAction);
        txtMsg.addActionListener(sendAction); 

        checkOfflineMessages();
        startHeartbeat();       
        startMessagePolling();  
        loadContactList();
    }
    
    // [MỚI] Hàm xử lý xóa contact
    private void deleteSelectedContact() {
        ContactItem selected = contactList.getSelectedValue();
        if (selected == null) return;
        
        int confirm = JOptionPane.showConfirmDialog(this, 
                "Bạn có chắc muốn xóa '" + selected.username + "' khỏi danh sách đã lưu?\n" +
                "(Lịch sử chat cũ nếu còn hạn vẫn sẽ hiển thị)",
                "Xác nhận xóa", JOptionPane.YES_NO_OPTION);
                
        if (confirm == JOptionPane.YES_OPTION) {
            // Xóa UI ngay cho mượt
            contactListModel.removeElement(selected);
            if (currentPartner != null && currentPartner.equals(selected.username)) {
                chatAreaPanel.removeAll();
                chatAreaPanel.repaint();
                lblCurrentPartner.setText("Chọn một người để bắt đầu chat");
                currentPartner = null;
            }
            
            // Gọi Service xóa ngầm trong DB
            new SwingWorker<Void, Void>() {
                @Override protected Void doInBackground() {
                    chatService.removeSavedContact(session.username(), selected.username);
                    return null;
                }
                @Override protected void done() {
                    // Sau khi xóa xong, reload lại list để đảm bảo đồng bộ 
                    // (Nếu vẫn còn tin nhắn cũ chưa xóa thì nó sẽ tự hiện lại do logic getRecentContacts)
                    loadContactList();
                }
            }.execute();
        }
    }

    // ... (Phần logic Chat/Service giữ nguyên) ...
    private void onSend() {
        if (currentPartner == null) { JOptionPane.showMessageDialog(this, "Vui lòng chọn người nhận trước!"); return; }
        String text = txtMsg.getText().trim();
        if (text.isEmpty()) return;
        TTLItem selectedTTL = (TTLItem) cboTTL.getSelectedItem();
        long ttlSeconds = selectedTTL.seconds;
        txtMsg.setText("");
        new SwingWorker<Void, Void>() {
            @Override protected Void doInBackground() { chatService.sendMessage(session, currentPartner, text, ttlSeconds); return null; }
            @Override protected void done() { try { get(); loadConversation(); loadContactList(); } catch (Exception e) { JOptionPane.showMessageDialog(ChatForm.this, "Lỗi: " + e.getMessage()); } }
        }.execute();
    }
    @Override public void dispose() {
        if (heartbeatTimer != null) heartbeatTimer.stop();
        if (pollingTimer != null) pollingTimer.stop();
        new Thread(() -> authService.logout(session.username())).start();
        super.dispose();
    }
    private void checkOfflineMessages() {
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() {
                long lastLogout = authService.getLastLogoutTime(session.username());
                return chatService.checkNewMessages(session.username(), lastLogout); 
            }
            @Override protected void done() {
                try {
                    List<String> offlineSenders = get();
                    if (!offlineSenders.isEmpty()) { unreadSenders.addAll(offlineSenders); contactList.repaint(); }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }.execute();
    }
    private class ContactRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            ContactItem item = (ContactItem) value;
            lbl.setText(item.username);
            lbl.setBorder(new EmptyBorder(0, 10, 0, 10));
            if (unreadSenders.contains(item.username)) {
                lbl.setText(item.username + " ●");
                lbl.setForeground(isSelected ? Color.WHITE : Color.RED);
                lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
            } else { lbl.setForeground(isSelected ? Color.WHITE : Color.BLACK); }
            return lbl;
        }
    }
    private void loadContactList() {
        new SwingWorker<List<String>, Void>() {
            @Override protected List<String> doInBackground() { return chatService.getRecentContacts(session.username()); }
            @Override protected void done() {
                try {
                    List<String> contacts = get();
                    ContactItem currentSelection = contactList.getSelectedValue();
                    String currentName = (currentSelection != null) ? currentSelection.username : null;
                    contactListModel.clear();
                    for (String c : contacts) contactListModel.addElement(new ContactItem(c));
                    if (currentName != null) {
                        for(int i=0; i<contactListModel.size(); i++) {
                            if(contactListModel.get(i).username.equals(currentName)) { contactList.setSelectedIndex(i); break; }
                        }
                    }
                } catch (Exception e) { e.printStackTrace(); }
            }
        }.execute();
    }
    private void switchToChat(String partner) { this.currentPartner = partner; lblCurrentPartner.setText("Đang chat với: " + partner); loadConversation(); }
    private void loadConversation() {
        if (currentPartner == null) return;
        new SwingWorker<List<DecryptedMessage>, Void>() {
            @Override protected List<DecryptedMessage> doInBackground() { return chatService.loadConversation(session, currentPartner, 50); }
            @Override protected void done() { try { updateChatUI(get()); } catch (Exception e) { e.printStackTrace(); } }
        }.execute();
    }
    private void updateChatUI(List<DecryptedMessage> msgs) {
        int currentCount = chatAreaPanel.getComponentCount() - 1; 
        if (currentCount > 0 && msgs.size() * 2 == currentCount) return;
        JScrollBar vertical = chatScrollPane.getVerticalScrollBar();
        boolean isAtBottom = vertical.getValue() >= (vertical.getMaximum() - vertical.getVisibleAmount() - 50);
        chatAreaPanel.removeAll();
        chatAreaPanel.add(Box.createVerticalGlue()); 
        for (DecryptedMessage m : msgs) { addMessageBubble(m); }
        chatAreaPanel.revalidate(); chatAreaPanel.repaint();
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
                        if (currentPartner == null || !currentPartner.equals(sender)) {
                            if (!unreadSenders.contains(sender)) {
                                showToast("Tin nhắn mới từ: " + sender);
                                unreadSenders.add(sender);
                                needRefresh = true;
                            }
                        }
                    }
                    if (!senders.isEmpty()) { lastCheckTime = now; loadContactList(); }
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
            @Override protected void done() { try { if (!get()) { dispose(); JOptionPane.showMessageDialog(null, "Phiên đăng nhập hết hạn!"); new LoginForm().setVisible(true); } } catch (Exception e) { } }
        }.execute();
    }
}