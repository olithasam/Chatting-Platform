import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.border.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Base64; // Ensure this is imported
import java.util.regex.Matcher; // For regex
import java.util.regex.Pattern; // For regex

public class ChatClient {
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField messageField;
    private JButton sendButton;
    private JButton fileButton;
    private JPanel mainPanel;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private boolean running = false;
    // Add a pattern to identify file links in the chat area
    private static final Pattern FILE_LINK_PATTERN = Pattern.compile("(\\S+) shared a file: (.*?) \\(Click to download\\)");
    private static final Pattern MY_FILE_LINK_PATTERN = Pattern.compile("\\[You\\] shared a file: (.*?) \\(Click to download\\)");


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
                    if (finalMessage.startsWith("[FILEDATA:")) {
                        // Handle incoming file data
                        // Format: [FILEDATA:fileName:base64EncodedData]
                        try {
                            String[] parts = finalMessage.substring(10).split(":", 2);
                            if (parts.length == 2) {
                                String fileName = parts[0];
                                String base64Data = parts[1];
                                saveFile(fileName, base64Data);
                            } else {
                                chatArea.append("[System] Received corrupted file data.\n");
                            }
                        } catch (Exception e) {
                            chatArea.append("[System] Error processing file data: " + e.getMessage() + "\n");
                        }
                    } else if (finalMessage.contains("[FILE:")) {
                        // Extract sender and filename
                        int senderEnd = finalMessage.indexOf("[FILE:");
                        String sender = senderEnd > 0 ? finalMessage.substring(0, senderEnd).trim() : "Unknown";
                        int startIndex = finalMessage.indexOf("[FILE:") + 6;
                        int endIndex = finalMessage.indexOf("]", startIndex);
                        if (startIndex >= 6 && endIndex > startIndex) {
                            String fileName = finalMessage.substring(startIndex, endIndex);
                            // Display format: User shared a file: filename.txt (Click to download)
                            chatArea.append(sender + " shared a file: " + fileName + " (Click to download)\n");
                            // The makeFileClickable method is called, but the actual click handling
                            // will be done by the MouseListener on chatArea.
                            makeFileClickable(fileName);
                        }
                    } else if (!finalMessage.startsWith("[You]")) {
                        // Only append messages that aren't our own
                        chatArea.append(finalMessage + "\n");
                    }
                    // Ensure chat area scrolls to the bottom
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
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

        // Add MouseListener to chatArea for handling file link clicks
        chatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) { // Left click
                    try {
                        int offset = chatArea.viewToModel2D(e.getPoint());
                        int lineNum = chatArea.getLineOfOffset(offset);
                        int lineStartOffset = chatArea.getLineStartOffset(lineNum);
                        int lineEndOffset = chatArea.getLineEndOffset(lineNum);
                        String lineText = chatArea.getText(lineStartOffset, lineEndOffset - lineStartOffset).trim();

                        Matcher matcher = FILE_LINK_PATTERN.matcher(lineText);
                        Matcher myMatcher = MY_FILE_LINK_PATTERN.matcher(lineText);

                        String fileNameToDownload = null;
                        if (matcher.find()) {
                            fileNameToDownload = matcher.group(2);
                        } else if (myMatcher.find()) {
                            fileNameToDownload = myMatcher.group(1);
                        }

                        if (fileNameToDownload != null) {
                            final String finalFileName = fileNameToDownload;
                            // Confirm before downloading
                            int choice = JOptionPane.showConfirmDialog(frame,
                                    "Download file: " + finalFileName + "?",
                                    "Confirm Download",
                                    JOptionPane.YES_NO_OPTION);
                            if (choice == JOptionPane.YES_OPTION) {
                                requestFile(finalFileName);
                            }
                        }
                    } catch (Exception ex) {
                        // Ignore if click is not on a valid line or other parsing error
                        // System.err.println("Error processing click: " + ex.getMessage());
                    }
                }
            }
        });
        
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
        
        // Keep focus in message field
        SwingUtilities.invokeLater(() -> messageField.requestFocusInWindow());
        
        // Add focus listener to maintain focus
        frame.addWindowFocusListener(new WindowAdapter() {
            @Override
            public void windowGainedFocus(WindowEvent e) {
                messageField.requestFocusInWindow();
            }
        });
        
        // Add placeholder text
        messageField.setText("Type your message...");
        messageField.setForeground(Color.GRAY);
        
        // Add focus listener for placeholder behavior
        messageField.addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                if (messageField.getText().equals("Type your message...")) {
                    messageField.setText("");
                    messageField.setForeground(new Color(30, 41, 59));
                }
                messageField.requestFocusInWindow();
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (messageField.getText().isEmpty()) {
                    messageField.setText("Type your message...");
                    messageField.setForeground(Color.GRAY);
                }
            }
        });
        
        // Buttons panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttonPanel.setOpaque(false);
        
        // Send button
        sendButton = new JButton("Send");
        styleButton(sendButton);
        
        // File button
        fileButton = new JButton("Share File");
        styleButton(fileButton);
        
        // Add components
        buttonPanel.add(fileButton);
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

        // File sharing button
        fileButton.addActionListener(e -> shareFile());

        // Window closing handler
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                disconnect();
            }
        });
    }

    private void sendMessage() {
        String message = messageField.getText().trim();
        if (!message.isEmpty() && !message.equals("Type your message...")) {
            out.println(message);
            chatArea.append("[You]: " + message + "\n"); // Display user's own message
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            messageField.setText(""); // Clear the input field after sending
            // Optionally, reset placeholder if you want it back immediately
            // messageField.setText("Type your message...");
            // messageField.setForeground(Color.GRAY);
            // messageField.requestFocusInWindow(); // Or let focusLost handle it
        }
    }

    private void shareFile() {
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String encodedFile = Base64.getEncoder().encodeToString(fileBytes);
                // Command to server: /file fileName base64Data
                out.println("/file " + file.getName() + " " + encodedFile);
                
                // Display message that you shared a file
                chatArea.append("[You] shared a file: " + file.getName() + " (Click to download)\n");
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
                makeFileClickable(file.getName()); // Call this, though MouseListener handles clicks
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Error sharing file: " + e.getMessage(),
                        "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // Method to request a file from the server
    private void requestFile(String fileName) {
        if (out != null && running) {
            out.println("/download " + fileName); // Send download command to server
            chatArea.append("[System] Requesting to download " + fileName + "...\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        } else {
            chatArea.append("[System] Not connected to server. Cannot download file.\n");
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        }
    }

    // Method to save the received file
    private void saveFile(String fileName, String base64Data) {
        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Data);
            
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setSelectedFile(new File(fileName)); // Suggest original filename
            if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                Files.write(fileToSave.toPath(), decodedBytes);
                JOptionPane.showMessageDialog(frame, "File '" + fileName + "' downloaded successfully to:\n" + fileToSave.getAbsolutePath(),
                        "Download Complete", JOptionPane.INFORMATION_MESSAGE);
                chatArea.append("[System] File '" + fileName + "' downloaded to " + fileToSave.getAbsolutePath() + "\n");
            } else {
                chatArea.append("[System] Download cancelled for file: " + fileName + "\n");
            }
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, "Error decoding file data for '" + fileName + "'. It might be corrupted.",
                    "Download Error", JOptionPane.ERROR_MESSAGE);
            chatArea.append("[System] Error decoding file data for: " + fileName + "\n");
        } 
        catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error saving file '" + fileName + "': " + e.getMessage(),
                    "Download Error", JOptionPane.ERROR_MESSAGE);
            chatArea.append("[System] Error saving file: " + fileName + "\n");
        }
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // Placeholder for makeFileClickable, as primary logic is in MouseListener
    private void makeFileClickable(String fileName) {
        // This method is called when a file message is appended.
        // The actual click handling is done by the MouseListener on chatArea.
        // You could add specific styling here if using JTextPane, but for JTextArea,
        // the MouseListener approach is more straightforward for click actions.
        // System.out.println("File link available for: " + fileName);
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