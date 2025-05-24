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

    public void broadcastMessage(String message, ClientHandler sender) {
        // Special handling for file messages
        if (message.startsWith("/file ")) {
            // Extract file name from the message
            String[] parts = message.split(" ", 3);
            if (parts.length >= 3) {
                String fileName = parts[1];
                String fileData = parts[2];
                
                // Check if it's an image file
                boolean isImage = fileName.toLowerCase().endsWith(".jpg") || 
                                 fileName.toLowerCase().endsWith(".jpeg") || 
                                 fileName.toLowerCase().endsWith(".png") || 
                                 fileName.toLowerCase().endsWith(".gif");
                
                // Send file notification to all clients
                String fileNotification;
                if (isImage) {
                    fileNotification = sender.getUsername() + " [IMAGE:" + fileName + ":" + fileData + "]";
                } else {
                    fileNotification = sender.getUsername() + " [FILE:" + fileName + "]";
                }
                
                messageLog.add(fileNotification);
                logMessage(fileNotification);
                
                // Send to all other clients
                for (ClientHandler client : clients) {
                    if (client != sender) {
                        client.sendMessage(fileNotification);
                    }
                }
                
                return; // Exit early since we've handled this message
            }
        }
        
        // Regular message handling (unchanged)
        String filtered = filterMessage(message);
        String formatted = sender.getUsername() + ": " + filtered;
        messageLog.add(formatted);
        logMessage(formatted);
        for (ClientHandler client : clients) {
            if (client != sender) {
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
}
