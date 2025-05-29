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
    private JButton statsPageButton;
    private JList<String> userList;
    private DefaultListModel<String> userListModel;
    private ChatServer server;
    private JPanel mainPanel;
    private JPanel statsPanel;
    private JTextField portField;
    private JButton startStopButton;
    private boolean serverRunning;

    public AdminGUI(ChatServer server) {
        this.server = server;
        this.serverRunning = server != null && server.isRunning(); // Check server status

        setTitle("Admin Control Panel");
        setSize(700, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        //Main panel
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

        //Create a header panel
        JPanel headerPanel = createHeaderPanel();

        //Create server control panel
        JPanel serverControlPanel = createServerControlPanel();

        //Create content panel with left and right sections
        JPanel contentPanel = new JPanel(new BorderLayout(10, 10));
        contentPanel.setOpaque(false);

        //Create stats panel
        statsPanel = createStatsPanel();

        //Create left panel with users list
        JPanel leftPanel = createUsersPanel();

        //Create right panel with vertically stacked buttons
        JPanel rightPanel = createButtonsPanel();

        //Add panels to content
        contentPanel.add(statsPanel, BorderLayout.NORTH);
        contentPanel.add(leftPanel, BorderLayout.CENTER);
        contentPanel.add(rightPanel, BorderLayout.EAST);

        //Add panels to main panel
        mainPanel.add(headerPanel, BorderLayout.NORTH);
        mainPanel.add(serverControlPanel, BorderLayout.SOUTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        //Add main panel to frame
        add(mainPanel);

        //Timer to refresh user count and list for every 5 seconds
        new Timer(5000, e -> refreshUserData()).start();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private JPanel createServerControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        panel.setOpaque(false);
        panel.setBorder(createPanelBorder("Server Controls"));

        JLabel portLabel = new JLabel("Server Port:");
        portLabel.setForeground(Color.WHITE);
        portLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        portField = new JTextField(String.valueOf(server != null ? server.getPort() : 9000), 10);
        portField.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        startStopButton = createStyledButton(serverRunning ? "Stop Server" : "Start Server");
        startStopButton.addActionListener(e -> toggleServer());

        panel.add(portLabel);
        panel.add(portField);
        panel.add(startStopButton);

        return panel;
    }

    private void toggleServer() {
        if (serverRunning) {
            server.stop();
            startStopButton.setText("Start Server");
            serverRunning = false;
            JOptionPane.showMessageDialog(this, "Server stopped successfully", "Server Status", JOptionPane.INFORMATION_MESSAGE);
        } else {
            try {
                int port = Integer.parseInt(portField.getText().trim());
                if (port < 1 || port > 65535) {
                    throw new NumberFormatException();
                }

                if (server != null) {
                    server.stop();
                }

                server = new ChatServer(port);
                new Thread(() -> {
                    server.start();
                }).start();

                startStopButton.setText("Stop Server");
                serverRunning = true;
                JOptionPane.showMessageDialog(this, "Server started on port " + port, "Server Status", JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Please enter a valid port number (1-65535)", "Invalid Port", JOptionPane.ERROR_MESSAGE);
            }
        }
        refreshUserData();
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

        userCountLabel = new JLabel("Users Online: " + (server != null ? server.getOnlineUserCount() : 0));
        userCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userCountLabel.setForeground(Color.WHITE);
        userCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        userCountLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JLabel serverStatusLabel = new JLabel("Server Status: " + (serverRunning ? "Running" : "Stopped"));
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
        userList.setBackground(Color.WHITE);
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
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));

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

        statsPageButton = createStyledButton("Open Stats Page");
        statsPageButton.addActionListener(e -> openStatsPage());
        statsPageButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        statsPageButton.setMaximumSize(new Dimension(200, 40));

        panel.add(Box.createVerticalStrut(20));
        panel.add(openLogButton);
        panel.add(Box.createVerticalStrut(15));
        panel.add(refreshButton);
        panel.add(Box.createVerticalStrut(15));
        panel.add(banWordButton);
        panel.add(Box.createVerticalStrut(15));
        panel.add(statsPageButton);
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

        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(200, 80, 192));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(79, 70, 229));
            }

            @Override
            public void mousePressed(MouseEvent e) {
                button.setBackground(new Color(150, 60, 150));
            }

            @Override
            public void mouseReleased(MouseEvent e) {
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
        TitledBorder titledBorder = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(255, 255, 255, 100), 1),
                title
        );
        titledBorder.setTitleFont(new Font("Segoe UI", Font.BOLD, 14));
        titledBorder.setTitleColor(Color.WHITE);

        return BorderFactory.createCompoundBorder(
                titledBorder,
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        );
    }

    private void refreshUserData() {
        if (server != null) {
            userCountLabel.setText("Users Online: " + server.getOnlineUserCount());

            // Update user list
            userListModel.clear();
            for (String username : server.getUsernames()) {
                userListModel.addElement(username);
            }

            // Update server status
            JLabel serverStatusLabel = (JLabel) statsPanel.getComponent(1);
            serverStatusLabel.setText("Server Status: " + (serverRunning ? "Running" : "Stopped"));
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
        if (server == null) {
            showErrorMessage("Server is not running");
            return;
        }

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

    private void openStatsPage() {
        if (server == null) {
            showErrorMessage("Server is not running");
            return;
        }

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