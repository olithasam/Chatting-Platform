import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.border.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
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
    private static final Pattern IMAGE_DATA_PATTERN = Pattern.compile("(\\S+) \\[IMAGE_DATA:(.*?):(.*?)\\]");
    private static final Pattern FILE_SHARED_PATTERN = Pattern.compile("(\\S+) \\[FILE_SHARED:(.*?)\\]");
    // Add a map to store shared file data temporarily
    private final Map<String, String> sharedFilesData = new ConcurrentHashMap<>();
    
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

    private boolean isImageFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif");
    }

    private void receiveMessages() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                final String finalMessage = message;
                SwingUtilities.invokeLater(() -> {
                    Matcher imageDataMatcher = IMAGE_DATA_PATTERN.matcher(finalMessage);
                    Matcher fileSharedMatcher = FILE_SHARED_PATTERN.matcher(finalMessage);

                    if (imageDataMatcher.matches()) { // This pattern might now be part of FILE_SHARED if server logic changed
                        String sender = imageDataMatcher.group(1);
                        String fileName = imageDataMatcher.group(2);
                        String base64ImageData = imageDataMatcher.group(3);
                        // If server sends IMAGE_DATA for images, handle it
                        displayImageWithDownloadOption(sender, fileName, base64ImageData);
                    } else if (fileSharedMatcher.matches()) {
                        String sender = fileSharedMatcher.group(1);
                        String fileName = fileSharedMatcher.group(2);
                        
                        // Check if it's an image file and handle accordingly
                        if (isImageFile(fileName) && sharedFilesData.containsKey(fileName)) {

                            // For image files, display the image directly
                            String base64ImageData = sharedFilesData.get(fileName);
                            displayImageWithDownloadOption(sender, fileName, base64ImageData);
                        } else {
                            // Display a generic file shared message with a download link
                            displaySharedFile(sender, fileName);
                        }
                    } else if (finalMessage.startsWith("[FILEDATA:")) {
                        try {
                            String[] parts = finalMessage.substring(10, finalMessage.length() -1).split(":", 2); // Adjusted to remove trailing ']' if present
                            if (parts.length == 2) {
                                String fileName = parts[0];
                                String base64Data = parts[1];
                                
                                // If it's an image file, display it
                                if (isImageFile(fileName)) {
                                    displayImageWithDownloadOption("[System]", fileName, base64Data);
                                } else {
                                    saveFile(fileName, base64Data);
                                }
                            } else {
                                appendToChat("[System] Received corrupted file data format.\n", null);
                            }
                        } catch (Exception e) {
                            appendToChat("[System] Error processing file data: " + e.getMessage() + "\n", null);
                            e.printStackTrace(); 
                        }
                    } else if (!finalMessage.startsWith("[You]") && !finalMessage.startsWith(username + " ")) {
                        appendToChat(finalMessage + "\n", null);
                    }
                    chatArea.setCaretPosition(chatArea.getDocument().getLength());
                });
            }
        } catch (IOException e) {
            if (running) {
                SwingUtilities.invokeLater(() -> {
                    appendToChat("[System] Connection to server lost\n", null);
                    frame.setTitle("Chat Client - Disconnected");
                    
                    // Show a message dialog informing the user that the connection was lost
                    JOptionPane.showMessageDialog(frame, 
                        "Connection to the server has been lost. The application will now close.", 
                        "Connection Lost", 
                        JOptionPane.ERROR_MESSAGE);
                    
                    // Close the application after a short delay
                    Timer timer = new Timer(2000, event -> {
                        disconnect();
                        frame.dispose();
                        System.exit(0);
                    });
                    timer.setRepeats(false);
                    timer.start();
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
                if (e.getButton() == MouseEvent.BUTTON1) {
                    try {
                        int offset = chatArea.viewToModel2D(e.getPoint());
                        StyledDocument doc = chatArea.getStyledDocument();
                        Element element = doc.getCharacterElement(offset);
                        
                        // Get the text of the clicked segment
                        int start = element.getStartOffset();
                        int end = element.getEndOffset();
                        String clickedText = doc.getText(start, end - start).trim();

                        // Check if it's a download link for an image (previously handled)
                        if (clickedText.startsWith("Download ")) { // For images with embedded data
                            String fileNameWithExt = clickedText.substring("Download ".length()).trim();
                            Icon icon = findIconForDownload(fileNameWithExt); 
                            if (icon instanceof ImageIcon && ((ImageIcon)icon).getDescription() != null) {
                                String[] parts = ((ImageIcon)icon).getDescription().split(":", 2);
                                if (parts.length == 2 && parts[0].equals(fileNameWithExt)) {
                                    String base64Data = parts[1];
                                    saveFile(fileNameWithExt, base64Data);
                                    return;
                                }
                            }
                        }
                        
                        // Attempt to find the filename from the line text if it's a file share notification
                        int lineNum = doc.getDefaultRootElement().getElementIndex(offset);
                        Element lineElement = doc.getDefaultRootElement().getElement(lineNum);
                        int lineStartOffset = lineElement.getStartOffset();
                        int lineEndOffset = lineElement.getEndOffset();
                        String lineText = doc.getText(lineStartOffset, lineEndOffset - lineStartOffset).trim();
                        if (StyleConstants.isUnderline(element.getAttributes()) && StyleConstants.getForeground(element.getAttributes()).equals(Color.BLUE)) {
                            
                            
                            Object fileNameAttr = element.getAttributes().getAttribute("fileName");
                            if (fileNameAttr instanceof String) {
                                String fileNameToDownload = (String) fileNameAttr;
                                int choice = JOptionPane.showConfirmDialog(frame,
                                        "Download file: " + fileNameToDownload + "?",
                                        "Confirm Download",
                                        JOptionPane.YES_NO_OPTION);
                                if (choice == JOptionPane.YES_OPTION) {
                                    requestFile(fileNameToDownload); 
                                }
                                return; 
                            }
                        }
                        
                        Matcher fileMatcher = FILE_LINK_PATTERN.matcher(lineText); 
                        Matcher myFileMatcher = MY_FILE_LINK_PATTERN.matcher(lineText);
                        String fileNameToDownloadOld = null;
                        if (fileMatcher.find()) {
                            fileNameToDownloadOld = fileMatcher.group(2);
                        } else if (myFileMatcher.find()) {
                            fileNameToDownloadOld = myFileMatcher.group(1);
                        }

                        if (fileNameToDownloadOld != null) {
                             int choice = JOptionPane.showConfirmDialog(frame,
                                        "Request to download file: " + fileNameToDownloadOld + "?",
                                        "Confirm Download Request",
                                        JOptionPane.YES_NO_OPTION);
                            if (choice == JOptionPane.YES_OPTION) {
                                requestFile(fileNameToDownloadOld);
                            }
                        }

                    } catch (Exception ex) {
                        ex.printStackTrace();
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
        // Add a more comprehensive file filter
        fileChooser.setFileFilter(new javax.swing.filechooser.FileFilter() {
            public boolean accept(File f) {
                if (f.isDirectory()) return true;
                String name = f.getName().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || 
                       name.endsWith(".png") || name.endsWith(".gif") || 
                       name.endsWith(".pdf") || name.endsWith(".txt") || 
                       name.endsWith(".doc") || name.endsWith(".docx") || 
                       name.endsWith(".xls") || name.endsWith(".xlsx") || 
                       name.endsWith(".ppt") || name.endsWith(".pptx") || 
                       name.endsWith(".java") ||
                       name.endsWith(".py") ||  
                       name.endsWith(".zip") ||   
                       name.endsWith(".rar") ||   
                       name.endsWith(".7z");    
            }
            public String getDescription() {
                return "Supported Files (Images, Docs, Code, Archives)";
            }
        });
        fileChooser.setAcceptAllFileFilterUsed(true); // Optionally allow users to select any file type
        
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String encodedFile = Base64.getEncoder().encodeToString(fileBytes);
                String fileName = file.getName();
                
                // Store image data locally if it's an image
                if (isImageFile(fileName)) {
                    sharedFilesData.put(fileName, encodedFile);
                }
                
                out.println("/file " + fileName + " " + encodedFile);
                
                // If it's an image, display it immediately for the sender
                if (isImageFile(fileName)) {
                    displayImageWithDownloadOption("[You]", fileName, encodedFile);
                } else {
                    // Change this line to use displaySharedFile instead of appendToChat
                    displaySharedFile("[You]", fileName);
                }
                chatArea.setCaretPosition(chatArea.getDocument().getLength());

            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame, "Error sharing file: " + e.getMessage(),
                        "File Error", JOptionPane.ERROR_MESSAGE);
            } catch (OutOfMemoryError ome) {
                JOptionPane.showMessageDialog(frame, "Error sharing file: File is too large.",
                        "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void displaySharedFile(String sender, String fileName) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            // Display sender and filename
            SimpleAttributeSet senderAttrs = new SimpleAttributeSet();
            StyleConstants.setBold(senderAttrs, true);
            doc.insertString(doc.getLength(), sender + ": ", senderAttrs);
            doc.insertString(doc.getLength(), "Shared a file - " + fileName + " ", null);

            // Add a clickable "Download" link
            SimpleAttributeSet linkAttrs = new SimpleAttributeSet();
            StyleConstants.setForeground(linkAttrs, Color.BLUE);
            StyleConstants.setUnderline(linkAttrs, true);
            linkAttrs.addAttribute("fileName", fileName); // Store fileName as an attribute

            doc.insertString(doc.getLength(), "(Download)", linkAttrs);
            doc.insertString(doc.getLength(), "\n", null);

        } catch (BadLocationException e) {
            System.err.println("Error displaying shared file: " + e.getMessage());
        }
    }

    // Method to request a file from the server
    private void requestFile(String fileName) {
        if (out != null && running) {
            out.println("/download " + fileName); // Send download command to server
            
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
            fileChooser.setSelectedFile(new File(fileName));
            if (fileChooser.showSaveDialog(frame) == JFileChooser.APPROVE_OPTION) {
                File fileToSave = fileChooser.getSelectedFile();
                Files.write(fileToSave.toPath(), decodedBytes);
                JOptionPane.showMessageDialog(frame, "File '" + fileName + "' downloaded successfully to:\n" + fileToSave.getAbsolutePath(),
                        "Download Complete ", JOptionPane.INFORMATION_MESSAGE);
                appendToChat("[System] File '" + fileName + "' download successfully \n", null);
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

    

    private void displayImageWithDownloadOption(String sender, String fileName, String base64ImageData) {
        try {
            byte[] imageBytes = Base64.getDecoder().decode(base64ImageData);
            ImageIcon originalIcon = new ImageIcon(imageBytes);

           
            int maxWidth = 300;
            Image originalImage = originalIcon.getImage();
            int imgWidth = originalIcon.getIconWidth();
            int imgHeight = originalIcon.getIconHeight();
            if (imgWidth > maxWidth) {
                float ratio = (float) maxWidth / imgWidth;
                imgWidth = maxWidth;
                imgHeight = (int) (imgHeight * ratio);
                Image scaledImage = originalImage.getScaledInstance(imgWidth, imgHeight, Image.SCALE_SMOOTH);
                originalIcon = new ImageIcon(scaledImage);
            }
            

            appendToChat(sender + ":\n", null);
            appendToChat("", originalIcon); // Display the image
            appendToChat("\n", null);

            // Add a clickable "Download" link for the image
            SimpleAttributeSet attrs = new SimpleAttributeSet();
            StyleConstants.setForeground(attrs, Color.BLUE);
            StyleConstants.setUnderline(attrs, true);
            
            StyledDocument doc = chatArea.getStyledDocument();
            try {
                doc.insertString(doc.getLength(), "Download " + fileName, attrs);
                doc.insertString(doc.getLength(), "\n", null);
            } catch (BadLocationException e) {  }

            // Store the image data with the icon or in a map if needed for the click listener
            originalIcon.setDescription(fileName + ":" + base64ImageData); // Storing data in description

        } catch (Exception e) {
            appendToChat("[System] Error displaying image " + fileName + ": " + e.getMessage() + "\n", null);
        }
    }

    // Helper method to find an icon (simplified)
    private Icon findIconForDownload(String fileName) {
        StyledDocument doc = chatArea.getStyledDocument();
        for (int i = 0; i < doc.getLength(); i++) {
            Element charElement = doc.getCharacterElement(i);
            AttributeSet attrs = charElement.getAttributes();
            Icon icon = StyleConstants.getIcon(attrs);
            if (icon instanceof ImageIcon) {
                ImageIcon imageIcon = (ImageIcon) icon;
                if (imageIcon.getDescription() != null && imageIcon.getDescription().startsWith(fileName + ":")) {
                    return imageIcon;
                }
            }
        }
        return null;
    }

    private void appendToChat(String text, Icon icon) {
        StyledDocument doc = chatArea.getStyledDocument();
        try {
            if (icon != null) {
                // Insert the image
                Style style = chatArea.addStyle("ImageStyle", null);
                StyleConstants.setIcon(style, icon);
                doc.insertString(doc.getLength(), "I", style); 
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

        // Ask user for port number
        String portInput = JOptionPane.showInputDialog(
            null,
            "Enter server port number:",
            "Server Port",
            JOptionPane.QUESTION_MESSAGE
        );
        
       
        if (portInput != null && !portInput.trim().isEmpty()) {
            try {
                int userPort = Integer.parseInt(portInput.trim());
                if (userPort > 0 && userPort <= 65535) {
                    port = userPort;
                } else {
                    JOptionPane.showMessageDialog(
                        null,
                        "Invalid port number. Using default port 9000.",
                        "Port Error",
                        JOptionPane.ERROR_MESSAGE
                    );
                }
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(
                    null,
                    "Invalid port number format. Using default port 9000.",
                    "Port Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }

        if (args.length >= 2) {
            serverAddress = args[0];
            port = Integer.parseInt(args[1]);
        }

        final String finalServerAddress = serverAddress;
        final int finalPort = port;
        SwingUtilities.invokeLater(() -> new ChatClient(finalServerAddress, finalPort));
    }
}