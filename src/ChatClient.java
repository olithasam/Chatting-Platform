import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.Base64;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;

public class ChatClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private String username;
    private boolean running = false;

    // GUI components
    private JFrame frame;
    private JTextArea chatArea;
    private JTextField inputField;
    private JButton sendButton;
    private JButton fileButton;

    public ChatClient(String serverAddress, int port) {
        initializeGUI();
        connectToServer(serverAddress, port);
    }

    private void initializeGUI() {
        // Create main window
        frame = new JFrame("Chat Client");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        frame.setLayout(new BorderLayout());

        // Chat display area
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(chatArea);
        frame.add(scrollPane, BorderLayout.CENTER);

        // Input components
        inputField = new JTextField();
        sendButton = new JButton("Send");
        fileButton = new JButton("Send File");

        // Bottom panel setup
        JPanel bottomPanel = new JPanel(new BorderLayout());
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2));
        buttonPanel.add(sendButton);
        buttonPanel.add(fileButton);

        bottomPanel.add(inputField, BorderLayout.CENTER);
        bottomPanel.add(buttonPanel, BorderLayout.EAST);
        frame.add(bottomPanel, BorderLayout.SOUTH);

        // Focus management
        inputField.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                System.out.println("Input field focused"); // Debug
            }
        });

        // Event handlers
        ActionListener sendAction = e -> {
            sendMessage();
            inputField.requestFocusInWindow();
        };

        sendButton.addActionListener(sendAction);
        inputField.addActionListener(sendAction);

        fileButton.addActionListener(e -> {
            sendFile();
            inputField.requestFocusInWindow();
        });

        // Window focus handling
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
                inputField.requestFocusInWindow();
            }

            @Override
            public void windowOpened(WindowEvent e) {
                inputField.requestFocusInWindow();
            }
        });

        // Final setup
        frame.setVisible(true);

        // Initial focus (fallback)
        SwingUtilities.invokeLater(() -> {
            inputField.requestFocusInWindow();
            System.out.println("Initial focus attempt complete");
        });
    }

    private void connectToServer(String serverAddress, int port) {
        try {
            socket = new Socket(serverAddress, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            running = true;

            // Get username with focus handling
            SwingUtilities.invokeLater(() -> {
                username = JOptionPane.showInputDialog(frame, "Enter your username:");
                if (username == null || username.trim().isEmpty()) {
                    username = "Anonymous";
                }
                out.println(username);
                chatArea.append("Connected to server at " + serverAddress + ":" + port + "\n");
                inputField.requestFocusInWindow(); // Focus after connection
            });

            new Thread(this::receiveMessages).start();

        } catch (IOException e) {
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(frame, "Error connecting to server: " + e.getMessage(),
                        "Connection Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            });
        }
    }

    private void sendMessage() {
        String message = inputField.getText();
        if (!message.trim().isEmpty()) {
            out.println(message);
            SwingUtilities.invokeLater(() -> {
                chatArea.append("You: " + message + "\n");
                inputField.setText("");
                inputField.requestFocusInWindow();
            });
        }
    }

    private void sendFile() {  // Remove the ActionEvent parameter
        JFileChooser fileChooser = new JFileChooser();
        if (fileChooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                byte[] fileContent = Files.readAllBytes(file.toPath());
                String encodedContent = Base64.getEncoder().encodeToString(fileContent);
                out.println("/file " + file.getName() + " " + encodedContent);
                chatArea.append("[You sent a file: " + file.getName() + "]\n");
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Error reading file: " + ex.getMessage(),
                        "File Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void receiveMessages() {
        try {
            String message;
            while (running && (message = in.readLine()) != null) {
                final String finalMessage = message; // Create final copy for lambda
                SwingUtilities.invokeLater(() -> {
                    if (finalMessage.startsWith("[FILE]")) {
                        chatArea.append(finalMessage + "\n");
                    } else {
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

    public static void main(String[] args) {
        // Initialize with default values
        String serverAddress = "localhost";
        int port = 9000;

        // Process command line arguments
        if (args.length >= 1) {
            serverAddress = args[0];
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(null, "Invalid port number. Using default 9000.");
            }
        }

        // Create final copies for the lambda
        final String finalServerAddress = serverAddress;
        final int finalPort = port;
        SwingUtilities.invokeLater(() -> new ChatClient(finalServerAddress, finalPort));
    }
}