import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

public class ChatServer {
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final List<String> messageLog = new CopyOnWriteArrayList<>();
    private final int port;
    private boolean running = false;
    private final String logFilePath = "server_log.txt";
    private final Set<String> bannedWords = new HashSet<>(Arrays.asList("badword1", "badword2"));
    private final Map<String, String> sharedFilesData = new ConcurrentHashMap<>(); // To temporarily store file data

    public ChatServer(int port) {
        this.port = port;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Chat server started on port " + port);
            logMessage("Server started on port " + port);

            while (running) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
                System.out.println("New client connected: " + clientHandler.getUsername());
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            for (ClientHandler client : clients) {
                client.disconnect();
            }
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isImageFile(String fileName) {
        String lowerName = fileName.toLowerCase();
        return lowerName.endsWith(".png") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".gif");
    }

    public void broadcastMessage(String message, ClientHandler sender) {
        if (message.startsWith("/file ")) {
            String[] parts = message.split(" ", 3);
            if (parts.length >= 3) {
                String fileName = parts[1];
                String base64FileData = parts[2]; // This is the Base64 encoded file data

                // Store the file data on the server temporarily
                sharedFilesData.put(fileName, base64FileData);

                String notificationMessage = sender.getUsername() + " [FILE_SHARED:" + fileName + "]";

                messageLog.add(sender.getUsername() + " shared file: " + fileName);
                logMessage(sender.getUsername() + " shared file: " + fileName);

                for (ClientHandler client : clients) {
                    // Only send the notification to other clients, not the original sender
                    if (client != sender) {
                        client.sendMessage(notificationMessage);
                    }
                }
                return;
            }
        } else if (message.startsWith("/download ")) { // Generic download request
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                String requestedFileName = parts[1];
                String fileData = sharedFilesData.get(requestedFileName);

                if (fileData != null) {
                    // Send the file data directly to the requesting client
                    sender.sendMessage("[FILEDATA:" + requestedFileName + ":" + fileData + "]");
                    logMessage(sender.getUsername() + " downloaded file: " + requestedFileName);
                } else {
                    sender.sendMessage("[System] File not found or no longer available: " + requestedFileName);
                    logMessage("File not found for download request from " + sender.getUsername() + ": " + requestedFileName);
                }
            }
            return;
        }

        // Regular message handling
        String filtered = filterMessage(message);
        String formatted = sender.getUsername() + ": " + filtered;
        messageLog.add(formatted);
        logMessage(formatted);
        for (ClientHandler client : clients) {
            if (client != sender) { // Regular messages are not sent back to the sender
                client.sendMessage(formatted);
            }
        }
    }
    

    private String filterMessage(String message) {
        for (String word : bannedWords) {
            message = message.replaceAll("(?i)" + word, "****");
        }
        return message;
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        logMessage("User disconnected: " + client.getUsername());
        System.out.println("Disconnected: " + client.getUsername());
    }

    private void logMessage(String msg) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFilePath, true))) {
            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            writer.println(time + " - " + msg);
        } catch (IOException e) {
            System.err.println("Log error: " + e.getMessage());
        }
    }

    public int getOnlineUserCount() {
        return clients.size();
    }

    public static void main(String[] args) {
        int port = 9000;
        ChatServer server = new ChatServer(port);
        new Thread(server::start).start();  // Run server on separate thread

        // Start admin GUI
        SwingUtilities.invokeLater(() -> new AdminGUI(server));
    }

    // Inner class for client handling
    static class ClientHandler implements Runnable {
        private final Socket socket;
        private final ChatServer server;
        private PrintWriter out;
        private BufferedReader in;
        private String username = "Anonymous";
        private boolean connected = true;

        public ClientHandler(Socket socket, ChatServer server) {
            this.socket = socket;
            this.server = server;
        }

        public void run() {
            try {
                out = new PrintWriter(socket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                username = in.readLine();
                if (username == null || username.trim().isEmpty()) username = "Anonymous";
                out.println("Welcome, " + username + "!");
                server.logMessage("User connected: " + username);

                String line;
                while (connected && (line = in.readLine()) != null) {
                    server.broadcastMessage(line, this);
                }
            } catch (IOException e) {
                System.err.println("Client error: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        public void sendMessage(String message) {
            if (out != null && connected) {
                out.println(message);
            }
        }

        public void disconnect() {
            connected = false;
            try {
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            server.removeClient(this);
        }

        public String getUsername() {
            return username;
        }
    }
    // Add these methods to your ChatServer class
    public Set<String> getBannedWords() {
        return new HashSet<>(bannedWords);
    }
    
    public void addBannedWord(String word) {
        bannedWords.add(word);
        logMessage("Added banned word: " + word);
    }
    
    public void removeBannedWord(String word) {
        bannedWords.remove(word);
        logMessage("Removed banned word: " + word);
    }
    
    public List<String> getUsernames() {
        List<String> usernames = new ArrayList<>();
        for (ClientHandler client : clients) {
            usernames.add(client.getUsername());
        }
        return usernames;
    }
    
   

    // Helper method to determine file type from extension
    private String getFileType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(dotIndex + 1);
        }
        return "file";
    }

    // Helper method to calculate approximate file size
    private String getFileSize(String base64Data) {
        int bytes = (int) (base64Data.length() * 0.75); // Approximate base64 to bytes conversion
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return (bytes / 1024) + " KB";
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}