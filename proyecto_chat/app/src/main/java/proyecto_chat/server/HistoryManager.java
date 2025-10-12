package proyecto_chat.server;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.sql.*;
import java.time.Instant;

public class HistoryManager {
    private static final String DB_FILE = "storage/chat_history.db";
    private static final String AUDIO_DIR = "storage/audio";

    private Connection conn;

    public HistoryManager() throws Exception {
        File storage = new File("storage");
        if (!storage.exists()) storage.mkdirs();
        File audio = new File(AUDIO_DIR);
        if (!audio.exists()) audio.mkdirs();

        Class.forName("org.sqlite.JDBC");
        conn = DriverManager.getConnection("jdbc:sqlite:" + DB_FILE);
        initTables();
    }

    private void initTables() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS messages (" +
                    "id TEXT PRIMARY KEY, " +
                    "type TEXT, " +
                    "sender TEXT, " +
                    "recipient TEXT, " +
                    "text_content TEXT, " +
                    "file_path TEXT, " +
                    "timestamp INTEGER)");
        }
    }

    public synchronized void saveTextMessage(String id, String sender, String recipient, String text, long timestamp) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO messages (id,type,sender,recipient,text_content,file_path,timestamp) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, "TEXT");
            ps.setString(3, sender);
            ps.setString(4, recipient);
            ps.setString(5, text);
            ps.setString(6, null);
            ps.setLong(7, timestamp);
            ps.executeUpdate();
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
    }

    public synchronized String saveVoiceNoteToDiskAndDb(String id, String sender, String recipient, byte[] content, String originalFileName, long timestamp) {
        try {
            String safeName = Instant.ofEpochMilli(timestamp).toString().replace(":", "-") + "_" + originalFileName;
            File outFile = new File(AUDIO_DIR, safeName);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(content);
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO messages (id,type,sender,recipient,text_content,file_path,timestamp) VALUES (?,?,?,?,?,?,?)")) {
                ps.setString(1, id);
                ps.setString(2, "VOICE_NOTE");
                ps.setString(3, sender);
                ps.setString(4, recipient);
                ps.setString(5, null);
                ps.setString(6, outFile.getAbsolutePath());
                ps.setLong(7, timestamp);
                ps.executeUpdate();
            }
            return outFile.getAbsolutePath();
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
    }

    // Ejemplo simple: obtener historial de una conversación o grupo (devuelve ResultSet o procesado)
    public synchronized ResultSet fetchHistoryForRecipient(String recipient) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM messages WHERE recipient = ? OR sender = ? ORDER BY timestamp ASC");
        ps.setString(1, recipient);
        ps.setString(2, recipient);
        return ps.executeQuery();
    }
}
