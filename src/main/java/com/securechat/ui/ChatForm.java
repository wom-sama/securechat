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
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.Timer; // Explicit import to avoid confusion

public class ChatForm extends JFrame {

    private final AuthService.Session session;
    private final ChatService chatService = new ChatService();
    private final AuthService authService = new AuthService();

    private Timer heartbeatTimer;
    private Timer pollingTimer;
    private long lastCheckTime;
    private String currentPartner = null;
    private final Set<String> unreadSenders = new HashSet<>();

    private final Map<String, Icon> avatarCache = new HashMap<>();

    // UI Components
    private final DefaultListModel<ContactItem> contactListModel = new DefaultListModel<>();
    private final JList<ContactItem> contactList = new JList<>(contactListModel);
    private final JPanel chatAreaPanel = new JPanel();
    private final JScrollPane chatScrollPane = new JScrollPane(chatAreaPanel);
    private final JTextField txtMsg = new JTextField();

    private final JButton btnSend = new JButton("Gửi");
    private final JButton btnAttach = new JButton("📎");

    private final JLabel lblCurrentPartner = new JLabel("Chọn một người để bắt đầu chat");
    private final JTextField txtNewContact = new JTextField();
    private final JComboBox<TTLItem> cboTTL = new JComboBox<>();
    private final JPopupMenu contextMenu = new JPopupMenu();
    private final JMenuItem itemDeleteContact = new JMenuItem("Xóa liên hệ");

    private static class TTLItem {

        String label;
        long seconds;

        TTLItem(String l, long s) {
            label = l;
            seconds = s;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private static class ContactItem {

        String username;

        ContactItem(String u) {
            username = u;
        }

        @Override
        public String toString() {
            return username;
        }
    }

    public ChatForm(AuthService.Session session) {
        super("SecureChat - " + session.username());
        this.session = session;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(1000, 700); 
        setLocationRelativeTo(null);

        // --- SETUP LAYOUT ---
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setDividerLocation(280);

        // LEFT PANEL 
        JPanel leftPanel = new JPanel(new BorderLayout());

        // Header Left: Avatar + Tên mình
        JPanel myInfoPanel = new JPanel(new BorderLayout(10, 0));
        myInfoPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        myInfoPanel.setBackground(new Color(230, 240, 255));

        JLabel lblMyAvatar = new JLabel();
        loadAvatarToLabel(session.username(), lblMyAvatar, 50);

        JPanel myTextPanel = new JPanel(new GridLayout(2, 1));
        myTextPanel.setOpaque(false);
        JLabel lblMyName = new JLabel(session.username());
        lblMyName.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblMyName.setForeground(new Color(0, 102, 204));
        myTextPanel.add(lblMyName);
        myTextPanel.add(new JLabel("Online"));

        myInfoPanel.add(lblMyAvatar, BorderLayout.WEST);
        myInfoPanel.add(myTextPanel, BorderLayout.CENTER);

        // Search Panel
        JPanel searchPanel = new JPanel(new BorderLayout());
        searchPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        searchPanel.add(txtNewContact, BorderLayout.CENTER);
        JButton btnAddContact = new JButton("+");
        searchPanel.add(btnAddContact, BorderLayout.EAST);

        JPanel topContainer = new JPanel(new BorderLayout());
        topContainer.add(myInfoPanel, BorderLayout.NORTH);
        topContainer.add(searchPanel, BorderLayout.SOUTH);
        leftPanel.add(topContainer, BorderLayout.NORTH);

        contactList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        contactList.setFixedCellHeight(50); // Tăng chiều cao row
        contactList.setCellRenderer(new ContactRenderer());
        leftPanel.add(new JScrollPane(contactList), BorderLayout.CENTER);

        // RIGHT PANEL (Chat)
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

        // Footer (Input)
        JPanel footerPanel = new JPanel(new BorderLayout(10, 10));
        footerPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        txtMsg.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btnSend.setBackground(new Color(0, 120, 215));
        btnSend.setForeground(Color.BLUE);

        btnAttach.setText("file");
        btnAttach.setFont(new Font("Segoe UI", Font.PLAIN, 13)); //
        btnAttach.setToolTipText("Đính kèm tệp tin (Mã hóa E2E)");
        btnAttach.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                btnAttach.setContentAreaFilled(true);
                btnAttach.setBackground(new Color(230, 230, 230));
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent evt) {
                btnAttach.setContentAreaFilled(false);
            }
        });

        cboTTL.addItem(new TTLItem("Vĩnh viễn", 0));
        cboTTL.addItem(new TTLItem("1 Phút", 60));
        cboTTL.addItem(new TTLItem("1 Ngày", 24 * 60 * 60));
        cboTTL.addItem(new TTLItem("1 Tuần", 7 * 24 * 60 * 60));
        cboTTL.addItem(new TTLItem("1 Tháng", 30 * 24 * 60 * 60));
        cboTTL.addItem(new TTLItem("1 Năm", 365 * 24 * 60 * 60));

        JPanel sendActionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        sendActionPanel.add(cboTTL);
        sendActionPanel.add(btnAttach);
        sendActionPanel.add(btnSend);

        footerPanel.add(txtMsg, BorderLayout.CENTER);
        footerPanel.add(sendActionPanel, BorderLayout.EAST);
        rightPanel.add(footerPanel, BorderLayout.SOUTH);

        splitPane.setLeftComponent(leftPanel);
        splitPane.setRightComponent(rightPanel);
        add(splitPane);

        // --- EVENTS ---
        contextMenu.add(itemDeleteContact);
        itemDeleteContact.addActionListener(e -> deleteSelectedContact());

        JMenuBar menuBar = new JMenuBar();
        JMenu menuAccount = new JMenu("Tài khoản");
        JMenuItem itemProfile = new JMenuItem("Hồ sơ & Đổi mật khẩu");
        JMenuItem itemLogout = new JMenuItem("Đăng xuất");
        itemProfile.addActionListener(e -> new ProfileDialog(this, session.username()).setVisible(true));
        itemLogout.addActionListener(e -> {
            dispose();
            new LoginForm().setVisible(true);
        });
        menuAccount.add(itemProfile);
        menuAccount.addSeparator();
        menuAccount.add(itemLogout);
        menuBar.add(menuAccount);
        setJMenuBar(menuBar);

        contactList.addMouseListener(new MouseAdapter() {
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

            @Override
            public void mousePressed(MouseEvent e) {
                checkPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                checkPopup(e);
            }

            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int index = contactList.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        contactList.setSelectedIndex(index);
                        contextMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        btnAddContact.addActionListener((var e) -> {
            String newMate = txtNewContact.getText().trim();
            if (newMate.isEmpty() || newMate.equals(session.username())) {
                return;
            }
            new SwingWorker<Boolean, Void>() {
                @Override
                protected Boolean doInBackground() {
                    return chatService.checkUserExists(newMate);
                }

                @Override
                protected void done() {
                    try {
                        if (get()) {
                            switchToChat(newMate);
                            txtNewContact.setText("");
                        }
                    } catch (InterruptedException | ExecutionException ex) {
                    }
                }
            }.execute();
        });

        ActionListener sendAction = e -> onSend();
        btnSend.addActionListener(sendAction);
        txtMsg.addActionListener(sendAction);
        btnAttach.addActionListener(e -> onAttachFile());

        checkOfflineMessages();
        startHeartbeat();
        startMessagePolling();
        loadContactList();
    }

    // --- [MỚI] Helper load Avatar ---
    private void loadAvatarToLabel(String username, JLabel label, int size) {
        new SwingWorker<Icon, Void>() {
            @Override
            protected Icon doInBackground() {
                byte[] bytes = authService.getUserAvatar(username);
                return ImageUtils.createCircularAvatar(bytes, size);
            }

            @Override
            protected void done() {
                try {
                    label.setIcon(get());
                } catch (InterruptedException | ExecutionException e) {
                }
            }
        }.execute();
    }

    private void getAvatarForChat(String username, Runnable onLoaded) {
        if (avatarCache.containsKey(username)) {
            if (onLoaded != null) {
                onLoaded.run();
            }
            return;
        }
        new SwingWorker<Icon, Void>() {
            @Override
            protected Icon doInBackground() {
                byte[] bytes = authService.getUserAvatar(username);
                return ImageUtils.createCircularAvatar(bytes, 35); // Size nhỏ cho chat bubble
            }

            @Override
            protected void done() {
                try {
                    avatarCache.put(username, get());
                    if (onLoaded != null) {
                        onLoaded.run();
                    }
                } catch (Exception e) {
                }
            }
        }.execute();
    }

    // --- LOGIC GỬI FILE ---
    private void onAttachFile() {
        if (currentPartner == null) {
            JOptionPane.showMessageDialog(this, "Chọn người nhận trước!");
            return;
        }

        JFileChooser fileChooser = new JFileChooser();
        int res = fileChooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (file.length() > 10 * 1024 * 1024) {
                JOptionPane.showMessageDialog(this, "File quá lớn! (Giới hạn demo 10MB)");
                return;
            }

            // Lấy TTL từ UI để gửi xuống Backend -> Backend sẽ set ngày hết hạn
            TTLItem selectedTTL = (TTLItem) cboTTL.getSelectedItem();
            long ttlSeconds = selectedTTL.seconds;

            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    byte[] fileBytes = Files.readAllBytes(file.toPath());
                    chatService.sendFile(session, currentPartner, fileBytes, file.getName(), ttlSeconds);
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        loadConversation();
                    } catch (InterruptedException | ExecutionException ex) {
                        JOptionPane.showMessageDialog(ChatForm.this, "Gửi file lỗi: " + ex.getMessage());
                    }
                }
            }.execute();
        }
    }

    private void onDownloadFile(String protocolString) {
        try {
            String[] parts = protocolString.split("\\|");
            String fileName = (parts.length >= 4) ? parts[3] : "downloaded_file";

            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(fileName));
            int res = fileChooser.showSaveDialog(this);

            if (res == JFileChooser.APPROVE_OPTION) {
                File saveFile = fileChooser.getSelectedFile();
                new SwingWorker<Void, Void>() {
                    @Override
                    protected Void doInBackground() throws Exception {
                        byte[] data = chatService.downloadAndDecryptFile(protocolString);
                        try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                            fos.write(data);
                        }
                        return null;
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                            JOptionPane.showMessageDialog(ChatForm.this, "Tải và giải mã thành công!\nLưu tại: " + saveFile.getAbsolutePath());
                            if (Desktop.isDesktopSupported()) {
                                Desktop.getDesktop().open(saveFile);
                            }
                        } catch (HeadlessException | IOException | InterruptedException | ExecutionException ex) {
                            JOptionPane.showMessageDialog(ChatForm.this, "Lỗi tải file (Có thể file đã hết hạn/bị xóa): " + ex.getMessage());
                        }
                    }
                }.execute();
            }
        } catch (HeadlessException e) {
            JOptionPane.showMessageDialog(this, "Lỗi phân tích file: " + e.getMessage());
        }
    }

    private void deleteSelectedContact() {
        ContactItem selected = contactList.getSelectedValue();
        if (selected == null) {
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Xóa '" + selected.username + "' và toàn bộ tin nhắn?", "Xác nhận", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            contactListModel.removeElement(selected);
            if (currentPartner != null && currentPartner.equals(selected.username)) {
                chatAreaPanel.removeAll();
                chatAreaPanel.repaint();
                lblCurrentPartner.setText("...");
                currentPartner = null;
            }
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() {
                    chatService.removeSavedContact(session.username(), selected.username);
                    return null;
                }

                @Override
                protected void done() {
                    loadContactList();
                }
            }.execute();
        }
    }

    private void onSend() {
        if (currentPartner == null) {
            return;
        }
        String text = txtMsg.getText().trim();
        if (text.isEmpty()) {
            return;
        }
        TTLItem selectedTTL = (TTLItem) cboTTL.getSelectedItem();
        long ttlSeconds = selectedTTL.seconds;
        txtMsg.setText("");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                chatService.sendMessage(session, currentPartner, text, ttlSeconds);
                return null;
            }

            @Override
            protected void done() {
                loadConversation();
                loadContactList();
            }
        }.execute();
    }

    private void addMessageBubble(DecryptedMessage m) {
        boolean isMe = m.from.equals(session.username());
        JPanel rowPanel = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT));
        rowPanel.setBackground(new Color(245, 245, 245));
        rowPanel.setOpaque(false);
        rowPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 100));

        // --- Avatar ---
        if (!avatarCache.containsKey(m.from)) {
            // Nếu chưa có, load và vẽ lại UI sau
            getAvatarForChat(m.from, () -> {
                // Chỉ repaint nếu cần thiết, ở đây cache đã cập nhật, lần render sau sẽ có ảnh
                chatAreaPanel.repaint();
            });
        }
        Icon avatarIcon = avatarCache.getOrDefault(m.from, ImageUtils.createCircularAvatar(null, 35));
        JLabel lblAvt = new JLabel(avatarIcon);

        // --- Bubble ---
        JPanel bubble = new JPanel();
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12));
        bubble.setBackground(isMe ? new Color(220, 248, 198) : Color.WHITE);
        bubble.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1, true));

        // Nội dung tin nhắn
        if (m.plaintext.startsWith("[FILE]:")) {
            String[] parts = m.plaintext.split("\\|");
            String fileName = (parts.length >= 4) ? parts[3] : "Unknown File";
            JLabel lblInfo = new JLabel("📎 File đính kèm:");
            lblInfo.setFont(new Font("Segoe UI", Font.ITALIC, 11));
            JButton btnDownload = new JButton("📥 " + fileName);
            btnDownload.setBackground(new Color(240, 240, 240));
            btnDownload.addActionListener(e -> onDownloadFile(m.plaintext));
            bubble.add(lblInfo);
            bubble.add(Box.createVerticalStrut(2));
            bubble.add(btnDownload);
        } else {
            JLabel lblContent = new JLabel("<html><body style='width: 250px'>" + m.plaintext + "</body></html>");
            lblContent.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            bubble.add(lblContent);
        }

        // Meta info
        String statusText = m.signatureValid ? "✓" : "⚠";
        Color statusColor = m.signatureValid ? new Color(0, 128, 0) : Color.RED;
        JLabel lblMeta = new JLabel(statusText + " " + new SimpleDateFormat("HH:mm").format(new Date(m.ts)));
        lblMeta.setFont(new Font("SansSerif", Font.PLAIN, 10));
        lblMeta.setForeground(statusColor);
        JPanel metaPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        metaPanel.setOpaque(false);
        metaPanel.add(lblMeta);
        metaPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        bubble.add(Box.createVerticalStrut(4));
        bubble.add(metaPanel);

        // Layout: Avatar + Bubble
        if (!isMe) {
            rowPanel.add(lblAvt);
            rowPanel.add(bubble);
        } else {
            rowPanel.add(bubble);
            // Không hiện avatar của mình bên phải cho gọn, hoặc thích thì bỏ comment dòng dưới
            // rowPanel.add(lblAvt); 
        }

        chatAreaPanel.add(rowPanel);
        chatAreaPanel.add(Box.createVerticalStrut(10));
    }

    @Override
    public void dispose() {
        if (heartbeatTimer != null) {
            heartbeatTimer.stop();
        }
        if (pollingTimer != null) {
            pollingTimer.stop();
        }
        new Thread(() -> authService.logout(session.username())).start();
        super.dispose();
    }

    private void checkOfflineMessages() {
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                long lastLogout = authService.getLastLogoutTime(session.username());
                return chatService.checkNewMessages(session.username(), lastLogout);
            }

            @Override
            protected void done() {
                try {
                    List<String> offlineSenders = get();
                    if (!offlineSenders.isEmpty()) {
                        unreadSenders.addAll(offlineSenders);
                        contactList.repaint();
                    }
                } catch (InterruptedException | ExecutionException e) {
                }
            }
        }.execute();
    }

    private class ContactRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new BorderLayout(5, 5));
            p.setBorder(new EmptyBorder(5, 5, 5, 5));
            if (isSelected) {
                p.setBackground(list.getSelectionBackground());
            } else {
                p.setBackground(list.getBackground());
            }

            ContactItem item = (ContactItem) value;
            JLabel lblName = new JLabel(item.username);
            lblName.setFont(new Font("Segoe UI", Font.BOLD, 14));

            // Render avatar nhỏ trong danh sách
            if (!avatarCache.containsKey(item.username)) {
                getAvatarForChat(item.username, () -> list.repaint());
            }
            Icon icon = avatarCache.getOrDefault(item.username, ImageUtils.createCircularAvatar(null, 40));
            JLabel lblIcon = new JLabel(icon);

            p.add(lblIcon, BorderLayout.WEST);
            p.add(lblName, BorderLayout.CENTER);

            if (unreadSenders.contains(item.username)) {
                JLabel lblBadge = new JLabel("●");
                lblBadge.setForeground(Color.RED);
                p.add(lblBadge, BorderLayout.EAST);
            }
            return p;
        }
    }

    private void loadContactList() {
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return chatService.getRecentContacts(session.username());
            }

            @Override
            protected void done() {
                try {
                    List<String> contacts = get();
                    ContactItem currentSelection = contactList.getSelectedValue();
                    String currentName = (currentSelection != null) ? currentSelection.username : null;
                    contactListModel.clear();
                    for (String c : contacts) {
                        contactListModel.addElement(new ContactItem(c));
                    }
                    if (currentName != null) {
                        for (int i = 0; i < contactListModel.size(); i++) {
                            if (contactListModel.get(i).username.equals(currentName)) {
                                contactList.setSelectedIndex(i);
                                break;
                            }
                        }
                    }
                } catch (InterruptedException | ExecutionException e) {
                }
            }
        }.execute();
    }

    private void switchToChat(String partner) {
        this.currentPartner = partner;
        lblCurrentPartner.setText("Đang chat với: " + partner);
        loadConversation();
    }

    private void loadConversation() {
        if (currentPartner == null) {
            return;
        }
        new SwingWorker<List<DecryptedMessage>, Void>() {
            @Override
            protected List<DecryptedMessage> doInBackground() {
                return chatService.loadConversation(session, currentPartner, 50);
            }

            @Override
            protected void done() {
                try {
                    updateChatUI(get());
                } catch (InterruptedException | ExecutionException e) {
                }
            }
        }.execute();
    }

    private void updateChatUI(List<DecryptedMessage> msgs) {
        int currentCount = chatAreaPanel.getComponentCount();
        if (currentCount > 0 && msgs.size() * 2 == (currentCount - 1)) {
            return;
        }

        chatAreaPanel.removeAll();
        chatAreaPanel.add(Box.createVerticalGlue());
        for (DecryptedMessage m : msgs) {
            addMessageBubble(m);
        }
        chatAreaPanel.revalidate();
        chatAreaPanel.repaint();
        SwingUtilities.invokeLater(() -> chatScrollPane.getVerticalScrollBar().setValue(chatScrollPane.getVerticalScrollBar().getMaximum()));
    }

    private void startMessagePolling() {
        lastCheckTime = System.currentTimeMillis();
        pollingTimer = new Timer(2000, e -> {
            if (currentPartner != null) {
                loadConversation();
            }
            checkNotifications();
        });
        pollingTimer.start();
    }

    private void checkNotifications() {
        long now = System.currentTimeMillis();
        new SwingWorker<List<String>, Void>() {
            @Override
            protected List<String> doInBackground() {
                return chatService.checkNewMessages(session.username(), lastCheckTime);
            }

            @Override
            protected void done() {
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
                    if (!senders.isEmpty()) {
                        lastCheckTime = now;
                        loadContactList();
                    }
                    if (needRefresh) {
                        contactList.repaint();
                    }
                } catch (InterruptedException | ExecutionException e) {
                }
            }
        }.execute();
    }

    private void showToast(String msg) {
        JWindow toast = new JWindow();
        toast.setBackground(new Color(40, 40, 40, 220));
        JLabel lbl = new JLabel(msg);
        lbl.setForeground(Color.BLUE);
        lbl.setBorder(new EmptyBorder(10, 20, 10, 20));
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 12));
        toast.add(lbl);
        toast.pack();
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        toast.setLocation(scr.width - toast.getWidth() - 20, scr.height - toast.getHeight() - 50);
        toast.setAlwaysOnTop(true);
        toast.setVisible(true);
        new Timer(5000, e -> {
            toast.dispose();
            ((Timer) e.getSource()).stop();
        }).start();
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
                    if (!get()) {
                        dispose();
                        JOptionPane.showMessageDialog(null, "Phiên đăng nhập hết hạn!");
                        new LoginForm().setVisible(true);
                    }
                } catch (HeadlessException | InterruptedException | ExecutionException e) {
                }
            }
        }.execute();
    }
}
