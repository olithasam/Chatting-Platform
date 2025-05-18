import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.border.*;
import java.io.*;
import java.net.*;

public class ChatClient {
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JPanel mainPanel;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private boolean running = false;

    public ChatClient(String serverAddress, int port) {
        initializeGUI();
        connectToServer(serverAddress, port);
    }

    private void connectToServer(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            running = true;

            // Get username from user
            username = JOptionPane.showInputDialog(frame, "Enter your username:", "Username", JOptionPane.QUESTION_MESSAGE);
            if (username == null || username.trim().isEmpty()) {
                username = "User" + System.currentTimeMillis() % 1000;
            }
            out.println(username);

            // Start message receiving thread
            new Thread(this::receiveMessages).start();

        } catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Could not connect to server: " + e.getMessage(),
                    "Connection Error", JOptionPane.ERROR_MESSAGE);
            System.exit(1);
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> {
                    if (!finalMessage.startsWith("[You]")) {
                        chatArea.append(finalMessage + "\n");
                    }
                });
            }
        } catch (IOException e) {
            if (running) {
                SwingUtilities.invokeLater(() -> {
                    chatArea.append("[System] Connection to server lost\n");
                    frame.setTitle("Chat Client - Disconnected");
                });
                running = false;
            }
        }
    }

    private void initializeGUI() {
        createAndShowGUI();
        setupActionListeners();
    }

    private void createAndShowGUI() {
        // Create main frame
        frame = new JFrame("Chat Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Create main panel with gradient background
        mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2d = (Graphics2D) g;
                int w = getWidth();
                int h = getHeight();
                GradientPaint gp = new GradientPaint(0, 0, new Color(65, 88, 208),
                        w, h, new Color(200, 80, 192));
                g2d.setPaint(gp);
                g2d.fillRect(0, 0, w, h);
            }
        };
        mainPanel.setLayout(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Chat area with custom styling
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setBackground(new Color(248, 250, 252));
        chatArea.setForeground(new Color(30, 41, 59));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        
        // Scrollpane for chat area
        JScrollPane scrollPane = new JScrollPane(chatArea);
        scrollPane.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(255, 255, 255, 100), 1),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        
        // Bottom panel for input
        JPanel bottomPanel = new JPanel(new BorderLayout(5, 0));
        bottomPanel.setOpaque(false);
        
        // Message input field
        messageField = new JTextField();
        messageField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        messageField.setBorder(BorderFactory.createCompoundBorder(
            new LineBorder(new Color(255, 255, 255, 100), 1),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);
        
        // Send button
        sendButton = new JButton("Send");
        styleButton(sendButton);
        
        // Add components
        buttonPanel.add(sendButton);
        bottomPanel.add(messageField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        
        mainPanel.add(scrollPane, BorderLayout.CENTER);
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
        
        // Frame settings
        frame.add(mainPanel);
        frame.setSize(600, 500);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        
        // Add action listeners
    }
    
    private void styleButton(JButton button) {
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(new Color(79, 70, 229));
        button.setBorder(new EmptyBorder(8, 15, 8, 15));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        button.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                button.setBackground(new Color(67, 56, 202));
            }
            
            @Override
            public void mouseExited(MouseEvent e) {
                button.setBackground(new Color(79, 70, 229));
            }
        });
    }

    private void setupActionListeners() {
        // Send message on button click
        sendButton.addActionListener(e -> sendMessage());

        // Send message on Enter key
        messageField.addActionListener(e -> sendMessage());

        // Window closing handler
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
    }

    // Remove file-related methods: shareFile, makeFileClickable, downloadFile

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && out != null) {
            out.println(message);
            // Display own message with [You] prefix
            chatArea.append("[You]: " + message + "\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            messageField.setText("");
        }
    }

    private void makeFileClickable(String fileName) {
        chatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                try {
                    String text = chatArea.getText();
                    int clickPosition = chatArea.viewToModel2D(e.getPoint());
                    
                    if (text.contains(fileName) && text.contains("(Click to download)")) {
                        downloadFile(fileName);
                    }
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(frame, "Error accessing file: " + ex.getMessage(),
                            "File Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    private void downloadFile(String fileName) {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setSelectedFile(new File(fileName));
        if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
            try {
                out.println("/download " + fileName);
                File selectedFile = fileChooser.getSelectedFile();
                chatArea.append("Downloading file: " + fileName + " to " + selectedFile.getPath() + "\n");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(frame, "Error downloading file: " + e.getMessage(),
                        "Download Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void shareFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileBytes = java.nio.file.Files.readAllBytes(file.toPath());
                String encodedFile = java.util.Base64.getEncoder().encodeToString(fileBytes);
                out.println("/file " + file.getName() + " " + encodedFile);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Error sharing file: " + e.getMessage(),
                        "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void disconnect() {
        running = false;
        try {
            if (out != null) {
                out.println("/quit");
                out.close();
            }
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.err.println("Error during disconnect: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String serverAddress = "localhost";
        int port = 9000;

        if (args.length >= 2) {
            serverAddress = args[0];
            port = Integer.parseInt(args[1]);
        }

        final String finalServerAddress = serverAddress;
        final int finalPort = port;
        SwingUtilities.invokeLater(() -> new ChatClient(finalServerAddress, finalPort));
    }
}