import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.nio.file.*;
import java.util.Base64;

public class ChatServer {
    private ServerSocket serverSocket;
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    private final List<String> messageLog = new CopyOnWriteArrayList<>();
    private final int port;
    private HttpServer webServer;
    private final int webPort = 8080;
    private boolean running = false;
    private final String logFilePath = "server_log.txt";
    private final Set<String> bannedWords = new HashSet<>(Arrays.asList(
            "badword1", "badword2", "badword3" // Add your banned words here
    ));
    private final String fileStoragePath = "server_files/";

    public ChatServer(int port) {
        this.port = port;
        try {
            Files.createDirectories(Paths.get(fileStoragePath));
        } catch (IOException e) {
            System.err.println("Error creating file storage directory: " + e.getMessage());
        }
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            System.out.println("Chat server started on port " + port);
            startWebServer();
            logMessage("Server started on port " + port);

            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();
                    System.out.println("New client connected: " + clientSocket.getInetAddress().getHostAddress());
                } catch (IOException e) {
                    if (running) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Could not start server: " + e.getMessage());
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            for (ClientHandler client : clients) {
                client.disconnect();
            }
            clients.clear();

            if (webServer != null) {
                webServer.stop(0);
            }

            logMessage("Server stopped");
            System.out.println("Server stopped");
        } catch (IOException e) {
            System.err.println("Error stopping server: " + e.getMessage());
        }
    }

    public void broadcastMessage(String message, ClientHandler sender) {
        String filteredMessage = filterMessage(message);
        String formattedMessage = sender.getUsername() + ": " + filteredMessage;
        messageLog.add(formattedMessage);
        logMessage(formattedMessage);

        for (ClientHandler client : clients) {
            if (client != sender) {
                client.sendMessage(formattedMessage);
            }
        }
    }

    public void handleFileUpload(String fileName, String fileContent, ClientHandler sender) {
        try {
            byte[] fileBytes = Base64.getDecoder().decode(fileContent);
            Path filePath = Paths.get(fileStoragePath + fileName);
            Files.write(filePath, fileBytes);

            String message = String.format("[FILE] %s shared a file: %s",
                    sender.getUsername(), fileName);
            broadcastMessage(message, sender);
            logMessage(message);
        } catch (IOException e) {
            System.err.println("Error saving file: " + e.getMessage());
        }
    }

    private String filterMessage(String message) {
        for (String word : bannedWords) {
            if (message.toLowerCase().contains(word.toLowerCase())) {
                message = message.replaceAll("(?i)" + word, "****");
            }
        }
        return message;
    }

    public void removeClient(ClientHandler client) {
        clients.remove(client);
        logMessage("User disconnected: " + client.getUsername());
        System.out.println("Client disconnected: " + client.getUsername());
    }

    private void logMessage(String message) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = now.format(formatter);
        String logEntry = timestamp + " - " + message;

        try (FileWriter fw = new FileWriter(logFilePath, true);
             BufferedWriter bw = new BufferedWriter(fw);
             PrintWriter out = new PrintWriter(bw)) {
            out.println(logEntry);
        } catch (IOException e) {
            System.err.println("Error writing to log file: " + e.getMessage());
        }
    }

    private void startWebServer() {
        try {
            webServer = HttpServer.create(new InetSocketAddress(webPort), 0);
            webServer.createContext("/", new StatsHandler());
            webServer.createContext("/users", new UsersHandler());
            webServer.createContext("/messages", new MessageHistoryHandler());
            webServer.setExecutor(null);
            webServer.start();
            System.out.println("Web server started on port " + webPort);
        } catch (IOException e) {
            System.err.println("Could not start web server: " + e.getMessage());
        }
    }

    class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <title>Chat Server Statistics</title>\n" +
                    "    <style>\n" +
                    "        body { font-family: Arial, sans-serif; margin: 20px; }\n" +
                    "        h1 { color: #333; }\n" +
                    "        .stat { margin-bottom: 10px; }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>Chat Server Status</h1>\n" +
                    "    <div class=\"stat\">Server running: " + running + "</div>\n" +
                    "    <div class=\"stat\">Port: " + port + "</div>\n" +
                    "    <div class=\"stat\">Connected clients: " + clients.size() + "</div>\n" +
                    "    <div class=\"stat\">Messages exchanged: " + messageLog.size() + "</div>\n" +
                    "    <p><a href=\"/users\">View connected users</a></p>\n" +
                    "    <p><a href=\"/messages\">View message history</a></p>\n" +
                    "</body>\n" +
                    "</html>";

            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    class UsersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder usersHtml = new StringBuilder();
            for (ClientHandler client : clients) {
                usersHtml.append("<div class=\"user\">").append(client.getUsername()).append("</div>\n");
            }

            String response = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <title>Connected Users</title>\n" +
                    "    <style>\n" +
                    "        body { font-family: Arial, sans-serif; margin: 20px; }\n" +
                    "        h1 { color: #333; }\n" +
                    "        .user { margin-bottom: 5px; padding: 5px; background-color: #f0f0f0; }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>Connected Users</h1>\n" +
                    "    <div class=\"users\">\n" +
                    usersHtml.toString() +
                    "    </div>\n" +
                    "    <p><a href=\"/\">Back to statistics</a></p>\n" +
                    "</body>\n" +
                    "</html>";

            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    class MessageHistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            StringBuilder messagesHtml = new StringBuilder();
            for (String message : messageLog) {
                messagesHtml.append("<div class=\"message\">")
                        .append(message)
                        .append("</div>\n");
            }

            String response = "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <title>Message History</title>\n" +
                    "    <style>\n" +
                    "        body { font-family: Arial, sans-serif; margin: 20px; }\n" +
                    "        h1 { color: #333; }\n" +
                    "        .message { margin-bottom: 5px; padding: 5px; border-bottom: 1px solid #eee; }\n" +
                    "    </style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "    <h1>Message History</h1>\n" +
                    "    <div class=\"messages\">\n" +
                    messagesHtml.toString() +
                    "    </div>\n" +
                    "    <p><a href=\"/\">Back to statistics</a></p>\n" +
                    "</body>\n" +
                    "</html>";

            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

    public static void main(String[] args) {
        int port = 9000;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default port 9000.");
            }
        }

        ChatServer server = new ChatServer(port);
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }

    static class ClientHandler implements Runnable {
        private final Socket clientSocket;
        private final ChatServer server;
        private PrintWriter out;
        private BufferedReader in;
        private String username = "Anonymous";
        private boolean connected = true;

        public ClientHandler(Socket socket, ChatServer server) {
            this.clientSocket = socket;
            this.server = server;
        }

        @Override
        public void run() {
            try {
                out = new PrintWriter(clientSocket.getOutputStream(), true);
                in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                out.println("Please enter your username:");
                username = in.readLine();
                if (username == null || username.trim().isEmpty()) {
                    username = "Anonymous";
                }

                out.println("Welcome to the chat, " + username + "!");
                server.logMessage("User connected: " + username);

                String inputLine;
                while (connected && (inputLine = in.readLine()) != null) {
                    if (inputLine.startsWith("/file ")) {
                        String[] parts = inputLine.split(" ", 3);
                        if (parts.length == 3) {
                            server.handleFileUpload(parts[1], parts[2], this);
                        }
                    } else {
                        server.broadcastMessage(inputLine, this);
                    }
                }
            } catch (IOException e) {
                System.err.println("Error handling client: " + e.getMessage());
            } finally {
                disconnect();
            }
        }

        public void sendMessage(String message) {
            if (out != null && connected) {
                out.println(message);
            }
        }

        public String getUsername() {
            return username;
        }

        public void disconnect() {
            connected = false;
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
                server.removeClient(this);
            } catch (IOException e) {
                System.err.println("Error disconnecting client: " + e.getMessage());
            }
        }
    }
}