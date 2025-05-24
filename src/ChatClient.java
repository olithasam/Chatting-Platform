import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.border.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.*;
import javax.swing.Icon;

public class ChatClient {
    private JFrame frame;
    private JTextPane chatArea;
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
                                appendToChat("[System] Received corrupted file data.\n", null);
                            }
                        } catch (Exception e) {
                            appendToChat("[System] Error processing file data: " + e.getMessage() + "\n", null);
                        }
                    } else if (finalMessage.contains("[IMAGE:")) {
                        // Extract sender, filename and image data
                        int senderEnd = finalMessage.indexOf("[IMAGE:");
                        String sender = senderEnd > 0 ? finalMessage.substring(0, senderEnd).trim() : "Unknown";
                        int startIndex = finalMessage.indexOf("[IMAGE:") + 7;
                        int midIndex = finalMessage.indexOf(":", startIndex);
                        int endIndex = finalMessage.indexOf("]", midIndex);
                        
                        if (startIndex >= 7 && midIndex > startIndex && endIndex > midIndex) {
                            String fileName = finalMessage.substring(startIndex, midIndex);
                            String imageData = finalMessage.substring(midIndex + 1, endIndex);
                            displayImage(sender, fileName, imageData);
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
                            appendToChat(sender + " shared a file: " + fileName + " (Click to download)\n", null);
                            // The makeFileClickable method is called, but the actual click handling
                            // will be done by the MouseListener on chatArea.
                            makeFileClickable(fileName);
                        }
                    } else if (!finalMessage.startsWith("[You]")) {
                        // Only append messages that aren't our own
                        appendToChat(finalMessage + "\n", null);
                    }
                    // Ensure chat area scrolls to the bottom
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                });
            }
        } catch (IOException e) {
            if (running) {
                SwingUtilities.invokeLater(() -> {
                    appendToChat("[System] Connection to server lost\n", null);
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
        chatArea = new JTextPane();
        chatArea.setEditable(false);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setBackground(new Color(248, 250, 252));
        chatArea.setForeground(new Color(30, 41, 59));

        // Add MouseListener to chatArea for handling file link clicks
        chatArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1) { // Left click
                    try {
                        int offset = chatArea.viewToModel2D(e.getPoint());
                        int lineNum = chatArea.getDocument().getDefaultRootElement().getElementIndex(offset);
                        Element lineElement = chatArea.getDocument().getDefaultRootElement().getElement(lineNum);
                        int lineStartOffset = lineElement.getStartOffset();
                        int lineEndOffset = lineElement.getEndOffset();
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
            if (out != null) {
                out.println(message);
            } else {
                appendToChat("[System] Not connected to server\n", null);
            }
            appendToChat("[You]: " + message + "\n", null); // Display user's own message
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
            messageField.setText(""); // Clear the input field after sending
        }
    }

    private void shareFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                       name.endsWith(".png") || name.endsWith(".gif") || 
                       name.endsWith(".pdf") || name.endsWith(".txt") || 
                       name.endsWith(".doc") || name.endsWith(".docx");
            }
            public String getDescription() {
                return "Supported Files";
            }
        });
        
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String encodedFile = Base64.getEncoder().encodeToString(fileBytes);
                String fileName = file.getName();
                
                // Check if it's an image file
                boolean isImage = fileName.toLowerCase().endsWith(".jpg") || 
                                 fileName.toLowerCase().endsWith(".jpeg") || 
                                 fileName.toLowerCase().endsWith(".png") || 
                                 fileName.toLowerCase().endsWith(".gif");
                
                // Command to server: /file fileName base64Data
                out.println("/file " + fileName + " " + encodedFile);
                
                // Display message that you shared a file
                if (isImage) {
                    // Display the image in your own chat
                    appendToChat("[You]:\n", null);
                    
                    // Create and display the image
                    ImageIcon originalIcon = new ImageIcon(fileBytes);
                    int maxWidth = 300;
                    int width = originalIcon.getIconWidth();
                    int height = originalIcon.getIconHeight();
                    
                    if (width > maxWidth) {
                        float ratio = (float) maxWidth / width;
                        width = maxWidth;
                        height = (int) (height * ratio);
                        Image scaledImage = originalIcon.getImage().getScaledInstance(width, height, Image.SCALE_SMOOTH);
                        originalIcon = new ImageIcon(scaledImage);
                    }
                    
                    appendToChat("", originalIcon);
                    appendToChat("\n", null);
                } else {
                    appendToChat("[You] shared a file: " + fileName + " (Click to download)\n", null);
                    makeFileClickable(fileName);
                }
                
                chatArea.setCaretPosition(chatArea.getDocument().getLength());
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
            appendToChat("[System] Requesting to download " + fileName + "...\n", null);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        } else {
            appendToChat("[System] Not connected to server. Cannot download file.\n", null);
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
                appendToChat("[System] File '" + fileName + "' downloaded to " + fileToSave.getAbsolutePath() + "\n", null);
            } else {
                appendToChat("[System] Download cancelled for file: " + fileName + "\n", null);
            }
        } catch (IllegalArgumentException e) {
            JOptionPane.showMessageDialog(frame, "Error decoding file data for '" + fileName + "'. It might be corrupted.",
                    "Download Error", JOptionPane.ERROR_MESSAGE);
            appendToChat("[System] Error decoding file data for: " + fileName + "\n", null);
        } 
        catch (IOException e) {
            JOptionPane.showMessageDialog(frame, "Error saving file '" + fileName + "': " + e.getMessage(),
                    "Download Error", JOptionPane.ERROR_MESSAGE);
            appendToChat("[System] Error saving file: " + fileName + "\n", null);
        }
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }

    // Placeholder for makeFileClickable, as primary logic is in MouseListener
    private void makeFileClickable(String fileName) {
        // This method is called when a file message is appended.
        // The actual click handling is done by the MouseListener on chatArea.
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

    private void displayImage(String sender, String fileName, String base64Data) {
        try {
            // Decode the Base64 image data
            byte[] imageBytes = Base64.getDecoder().decode(base64Data);
            ImageIcon originalIcon = new ImageIcon(imageBytes);
            
            // Resize image if it's too large (max width 300px)
            int maxWidth = 300;
            Image originalImage = originalIcon.getImage();
            int width = originalIcon.getIconWidth();
            int height = originalIcon.getIconHeight();
            
            if (width > maxWidth) {
                float ratio = (float) maxWidth / width;
                width = maxWidth;
                height = (int) (height * ratio);
                Image scaledImage = originalImage.getScaledInstance(width, height, Image.SCALE_SMOOTH);
                originalIcon = new ImageIcon(scaledImage);
            }
            
            // Create a WhatsApp-like bubble for the image
            appendToChat(sender + ":\n", null);
            appendToChat("", originalIcon);
            appendToChat("\n", null);
            
            // Store the image data for potential saving
            originalIcon.setDescription(fileName + ":" + base64Data);
            
        } catch (Exception e) {
            appendToChat("[System] Error displaying image: " + e.getMessage() + "\n", null);
        }
    }

    private void appendToChat(String text, Icon icon) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            if (icon != null) {
                // Insert the image
                Style style = chatArea.addStyle("ImageStyle", null);
                StyleConstants.setIcon(style, icon);
                doc.insertString(doc.getLength(), "I", style); // "I" is a placeholder for the image
            } else {
                // Insert text
                doc.insertString(doc.getLength(), text, null);
            }
        } catch (Exception e) {
            System.err.println("Error appending to chat: " + e.getMessage());
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