import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.swing.*;
import javax.swing.border.*;

public class AdminGUI extends JFrame {
    private JLabel userCountLabel;
    private JButton openLogButton;
    private JButton refreshButton;
    private JButton banWordButton;
    private JButton statsPageButton; // New button for stats page
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private ChatServer server;
    private JPanel mainPanel;
    private JPanel statsPanel;

    public AdminGUI(ChatServer server) {
        this.server = server;
        setTitle("Admin Control Panel");
        setSize(500, 400);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Create main panel with gradient background
        mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                int w = getWidth();
                int h = getHeight();
                GradientPaint gp = new GradientPaint(0, 0, new Color(40, 48, 72),
                        w, h, new Color(133, 147, 152));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);
            }
        };
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // Create header panel
        JPanel headerPanel = createHeaderPanel();
        
        // Create content panel with left and right sections
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setOpaque(false);
        
        // Create stats panel
        statsPanel = createStatsPanel();
        
        // Create left panel with users list (white background)
        JPanel leftPanel = createUsersPanel();
        
        // Create right panel with vertically stacked buttons
        JPanel rightPanel = createButtonsPanel();
        
        // Add panels to content
        contentPanel.add(statsPanel, BorderLayout.NORTH);
        contentPanel.add(leftPanel, BorderLayout.CENTER);
        contentPanel.add(rightPanel, BorderLayout.EAST);
        
        // Add panels to main panel
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);
        
        // Add main panel to frame
        add(mainPanel);
        
        // Timer to refresh user count and list every 5 seconds
        new Timer(5000, e -> refreshUserData()).start();
        
        setLocationRelativeTo(null);
        setVisible(true);
    }
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        JLabel titleLabel = new JLabel("Admin Control Panel");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        
        panel.add(titleLabel, BorderLayout.WEST);
        
        return panel;
    }
    
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(createPanelBorder("Server Statistics"));
        
        userCountLabel = new JLabel("Users Online: " + server.getOnlineUserCount());
        userCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userCountLabel.setForeground(Color.WHITE);
        userCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        userCountLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        JLabel serverStatusLabel = new JLabel("Server Status: Running");
        serverStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        serverStatusLabel.setForeground(Color.WHITE);
        serverStatusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverStatusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        
        panel.add(userCountLabel);
        panel.add(serverStatusLabel);
        
        return panel;
    }
    
    private JPanel createUsersPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(createPanelBorder("Connected Users"));
        
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userList.setBackground(Color.WHITE); // White background as requested
        userList.setForeground(new Color(40, 48, 72));
        userList.setSelectionBackground(new Color(79, 70, 229));
        userList.setSelectionForeground(Color.WHITE);
        
        JScrollPane scrollPane = new JScrollPane(userList);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createButtonsPanel() {
        // Vertical panel for buttons
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
        
        // Create buttons stacked vertically
        openLogButton = createStyledButton("Open Chat Log");
        openLogButton.addActionListener(e -> openLogFile());
        openLogButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        openLogButton.setMaximumSize(new Dimension(200, 40));
        
        refreshButton = createStyledButton("Refresh");
        refreshButton.addActionListener(e -> refreshUserData());
        refreshButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        refreshButton.setMaximumSize(new Dimension(200, 40));
        
        banWordButton = createStyledButton("Manage Banned Words");
        banWordButton.addActionListener(e -> manageBannedWords());
        banWordButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        banWordButton.setMaximumSize(new Dimension(200, 40));
        
        // New button for stats page
        statsPageButton = createStyledButton("Open Stats Page");
        statsPageButton.addActionListener(e -> openStatsPage());
        statsPageButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        statsPageButton.setMaximumSize(new Dimension(200, 40));
        
        // Add spacing between buttons
        panel.add(Box.createVerticalStrut(20));
        panel.add(openLogButton);
        panel.add(Box.createVerticalStrut(15));
        panel.add(refreshButton);
        panel.add(Box.createVerticalStrut(15));
        panel.add(banWordButton);
        panel.add(Box.createVerticalStrut(15));
        panel.add(statsPageButton); // Add the new button
        panel.add(Box.createVerticalGlue());
        
        return panel;
    }
    
    private JButton createStyledButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(79, 70, 229));
        button.setBorder(new EmptyBorder(10, 15, 10, 15));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        // Purple-pink gradient hover effect
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(200, 80, 192)); // Purple-pink color
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(79, 70, 229)); // Return to original color
            }
            
            @Override
            public void mousePressed(MouseEvent e) {
                // Create a gradient button on press
                button.setBackground(new Color(150, 60, 150));
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {
                // Return to hover color if still hovering
                if (button.contains(e.getPoint())) {
                    button.setBackground(new Color(200, 80, 192));
                } else {
                    button.setBackground(new Color(79, 70, 229));
                }
            }
        });
        
        return button;
    }
    
    private Border createPanelBorder(String title) {
        TitledBorder titledBorder = BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 100), 1), title);
        titledBorder.setTitleFont(new Font("Segoe UI", Font.BOLD, 14));
        titledBorder.setTitleColor(Color.WHITE);
        
        return BorderFactory.createCompoundBorder(
            titledBorder,
            BorderFactory.createEmptyBorder(10, 10, 10, 10)
        );
    }
    
    private void refreshUserData() {
        userCountLabel.setText("Users Online: " + server.getOnlineUserCount());
        
        // Update user list
        userListModel.clear();
        for (String username : server.getUsernames()) {
            userListModel.addElement(username);
        }
    }
    
    private void openLogFile() {
        try {
            File file = new File("server_log.txt");
            if (file.exists()) {
                Desktop.getDesktop().open(file);
            } else {
                showErrorMessage("Log file not found.");
            }
        } catch (IOException e) {
            showErrorMessage("Error opening file: " + e.getMessage());
        }
    }
    
    private void manageBannedWords() {
        JDialog dialog = new JDialog(this, "Manage Banned Words", true);
        dialog.setSize(400, 300);
        dialog.setLocationRelativeTo(this);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        DefaultListModel<String> bannedWordsModel = new DefaultListModel<>();
        for (String word : server.getBannedWords()) {
            bannedWordsModel.addElement(word);
        }
        
        JList<String> bannedWordsList = new JList<>(bannedWordsModel);
        JScrollPane scrollPane = new JScrollPane(bannedWordsList);
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addButton = new JButton("Add Word");
        JButton removeButton = new JButton("Remove Word");
        
        addButton.addActionListener(e -> {
            String word = JOptionPane.showInputDialog(dialog, "Enter word to ban:");
            if (word != null && !word.trim().isEmpty()) {
                server.addBannedWord(word.trim().toLowerCase());
                bannedWordsModel.addElement(word.trim().toLowerCase());
            }
        });
        
        removeButton.addActionListener(e -> {
            int selectedIndex = bannedWordsList.getSelectedIndex();
            if (selectedIndex != -1) {
                String word = bannedWordsModel.getElementAt(selectedIndex);
                server.removeBannedWord(word);
                bannedWordsModel.remove(selectedIndex);
            }
        });
        
        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        
        panel.add(new JLabel("Banned Words:"), BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        dialog.add(panel);
        dialog.setVisible(true);
    }
    
    // Method to open stats page in browser
    private void openStatsPage() {
        try {
            Desktop.getDesktop().browse(new URI(server.getStatsUrl()));
        } catch (IOException | URISyntaxException e) {
            showErrorMessage("Error opening stats page: " + e.getMessage());
        }
    }
    
    private void showErrorMessage(String message) {
        JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
    }
}
