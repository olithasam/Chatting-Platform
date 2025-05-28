import java.io.*;
import java.net.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import javax.swing.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Duration;

public class ChatServer {
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final List<String> messageLog = new CopyOnWriteArrayList<>();
    private final int port;
    private volatile boolean running = false;  // Made volatile for thread safety
    private final String logFilePath = "server_log.txt";
    private final Set<String> bannedWords = new HashSet<>(Arrays.asList("badword1", "badword2"));
    private final Map<String, String> sharedFilesData = new ConcurrentHashMap<>();
    private HttpServer httpServer;
    private final int httpPort = 8080;
    private LocalDateTime serverStartTime;

    public ChatServer(int port) {
        this.port = port;
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }

    public void start() {
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            serverSocket = new ServerSocket(port);
            running = true;
            serverStartTime = LocalDateTime.now();
            System.out.println("Chat server started on port " + port);
            logMessage("Server started on port " + port);

            startHttpServer();

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();
                    System.out.println("New client connected: " + clientHandler.getUsername());
                } catch (SocketException e) {
                    if (running) {
                        System.err.println("Server socket error: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            stop();
        }
    }

    private void startHttpServer() {
        try {
            if (httpServer != null) {
                httpServer.stop(0);
            }

            httpServer = HttpServer.create(new InetSocketAddress(httpPort), 0);
            httpServer.createContext("/stats", new StatsHandler());
            httpServer.setExecutor(null);
            httpServer.start();
            System.out.println("HTTP server started on port " + httpPort);
            logMessage("HTTP server for stats page started on port " + httpPort);
        } catch (IOException e) {
            System.err.println("HTTP server error: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            for (ClientHandler client : clients) {
                client.disconnect();
            }
            clients.clear();

            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            if (httpServer != null) {
                httpServer.stop(0);
            }

            System.out.println("Chat server stopped");
            logMessage("Server stopped");
        } catch (IOException e) {
            System.err.println("Error stopping server: " + e.getMessage());
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
                String base64FileData = parts[2];

                sharedFilesData.put(fileName, base64FileData);

                if (isImageFile(fileName)) {
                    for (ClientHandler client : clients) {
                        if (client != sender) {
                            client.sendMessage(sender.getUsername() + " [IMAGE_DATA:" + fileName + ":" + base64FileData + "]");
                        }
                    }
                } else {
                    String notificationMessage = sender.getUsername() + " [FILE_SHARED:" + fileName + "]";
                    for (ClientHandler client : clients) {
                        if (client != sender) {
                            client.sendMessage(notificationMessage);
                        }
                    }
                }

                messageLog.add(sender.getUsername() + " shared file: " + fileName);
                logMessage(sender.getUsername() + " shared file: " + fileName);
                return;
            }
        } else if (message.startsWith("/download ")) {
            String[] parts = message.split(" ", 2);
            if (parts.length == 2) {
                String requestedFileName = parts[1];
                String fileData = sharedFilesData.get(requestedFileName);

                if (fileData != null) {
                    sender.sendMessage("[FILEDATA:" + requestedFileName + ":" + fileData + "]");
                    logMessage(sender.getUsername() + " downloaded file: " + requestedFileName);
                } else {
                    sender.sendMessage("[System] File not found or no longer available: " + requestedFileName);
                    logMessage("File not found for download request from " + sender.getUsername() + ": " + requestedFileName);
                }
            }
            return;
        }

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
        new Thread(server::start).start();

        SwingUtilities.invokeLater(() -> new AdminGUI(server));
    }

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

    class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = generateStatsHtml();
            exchange.getResponseHeaders().set("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }

        private String generateStatsHtml() {
            StringBuilder html = new StringBuilder();
            html.append("<!DOCTYPE html>\n");
            html.append("<html>\n");
            html.append("<head>\n");
            html.append("<title>Chat Server Statistics</title>\n");
            html.append("<meta http-equiv=\"refresh\" content=\"5\">\n");
            html.append("<style>\n");
            html.append("body { font-family: 'Segoe UI', Arial, sans-serif; background: linear-gradient(to bottom right, #40486c, #859398); color: white; margin: 0; padding: 20px; }\n");
            html.append("h1 { color: white; }\n");
            html.append(".container { max-width: 800px; margin: 0 auto; background-color: rgba(255, 255, 255, 0.1); border-radius: 10px; padding: 20px; box-shadow: 0 4px 6px rgba(0, 0, 0, 0.1); }\n");
            html.append(".stat-box { background-color: rgba(255, 255, 255, 0.2); border-radius: 5px; padding: 15px; margin-bottom: 15px; }\n");
            html.append(".user-list { background-color: white; color: #40486c; border-radius: 5px; padding: 10px; max-height: 300px; overflow-y: auto; }\n");
            html.append(".user-item { padding: 5px 10px; border-bottom: 1px solid #eee; }\n");
            html.append(".user-item:last-child { border-bottom: none; }\n");
            html.append(".status-indicator { display: inline-block; width: 12px; height: 12px; border-radius: 50%; margin-right: 8px; }\n");
            html.append(".status-running { background-color: #4CAF50; }\n");
            html.append(".status-stopped { background-color: #F44336; }\n");
            html.append("</style>\n");
            html.append("</head>\n");
            html.append("<body>\n");
            html.append("<div class=\"container\">\n");
            html.append("<h1>Chat Server Statistics</h1>\n");
            html.append("<div class=\"stat-box\">\n");
            html.append("<h2>Server Status</h2>\n");

            html.append("<p>");
            if (running) {
                html.append("<span class=\"status-indicator status-running\"></span>Status: Running");
            } else {
                html.append("<span class=\"status-indicator status-stopped\"></span>Status: Stopped");
            }
            html.append("</p>\n");

            html.append("<p>Server started on port: ").append(port).append("</p>\n");
            html.append("<p>Current time: ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</p>\n");

            if (serverStartTime != null) {
                Duration uptime = Duration.between(serverStartTime, LocalDateTime.now());
                long days = uptime.toDays();
                long hours = uptime.toHoursPart();
                long minutes = uptime.toMinutesPart();
                long seconds = uptime.toSecondsPart();
                html.append("<p>Uptime: ");
                if (days > 0) html.append(days).append(" days, ");
                html.append(String.format("%02d:%02d:%02d", hours, minutes, seconds)).append("</p>\n");
            }

            html.append("</div>\n");
            html.append("<div class=\"stat-box\">\n");
            html.append("<h2>User Statistics</h2>\n");
            html.append("<p>Users online: ").append(getOnlineUserCount()).append("</p>\n");
            html.append("<p>Total messages sent: ").append(messageLog.size()).append("</p>\n");
            html.append("<h3>Connected Users:</h3>\n");
            html.append("<div class=\"user-list\">\n");
            List<String> usernames = getUsernames();
            if (usernames.isEmpty()) {
                html.append("<div class=\"user-item\">No users connected</div>\n");
            } else {
                for (String username : usernames) {
                    html.append("<div class=\"user-item\">").append(username).append("</div>\n");
                }
            }
            html.append("</div>\n");
            html.append("</div>\n");
            html.append("<div class=\"stat-box\">\n");
            html.append("<h2>Banned Words</h2>\n");
            html.append("<div class=\"user-list\">\n");
            Set<String> banned = getBannedWords();
            if (banned.isEmpty()) {
                html.append("<div class=\"user-item\">No banned words</div>\n");
            } else {
                for (String word : banned) {
                    html.append("<div class=\"user-item\">").append(word).append("</div>\n");
                }
            }
            html.append("</div>\n");
            html.append("</div>\n");
            html.append("</div>\n");
            html.append("</body>\n");
            html.append("</html>\n");

            return html.toString();
        }
    }

    public String getStatsUrl() {
        return "http://localhost:" + httpPort + "/stats";
    }
}