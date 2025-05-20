import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;

public class AdminGUI extends JFrame {
    private JLabel userCountLabel;
    private JButton openLogButton;
    private ChatServer server;

    public AdminGUI(ChatServer server) {
        this.server = server;
        setTitle("Admin Control Panel");
        setSize(300, 150);
        setLayout(new FlowLayout());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        userCountLabel = new JLabel("Users Online: " + server.getOnlineUserCount());
        add(userCountLabel);

        openLogButton = new JButton("Open Chat Log");
        openLogButton.addActionListener(e -> openLogFile());
        add(openLogButton);

        // Timer to refresh user count every 5 seconds
        new Timer(5000, e -> userCountLabel.setText("Users Online: " + server.getOnlineUserCount())).start();

        setVisible(true);
    }

    private void openLogFile() {
        try {
            File file = new File("server_log.txt");
            if (file.exists()) {
                Desktop.getDesktop().open(file);
            } else {
                JOptionPane.showMessageDialog(this, "Log file not found.");
            }
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error opening file: " + e.getMessage());
        }
    }
}
