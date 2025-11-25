package proyecto_chat.server;

import java.io.File;
import java.io.FileOutputStream;
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

    //private String hey = "";

    public Connection getConnection(){
        return conn;
    }

    private void initTables() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (" +
                    "username TEXT PRIMARY KEY)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS groups (" +
                    "group_name TEXT PRIMARY KEY)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS group_members (" +
                    "group_name TEXT, " +
                    "username TEXT, " +
                    "PRIMARY KEY(group_name, username))");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS messages (" +
                    "id TEXT PRIMARY KEY, " +
                    "type TEXT, " +
                    "sender TEXT, " +
                    "recipient TEXT, " +
                    "is_group INTEGER, " +
                    "text_content TEXT, " +
                    "file_path TEXT, " +
                    "timestamp INTEGER)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS message_visibility (" +
                    "message_id TEXT, " +
                    "username TEXT, " +
                    "visible INTEGER DEFAULT 1, " +
                    "PRIMARY KEY(message_id, username))");
        }
    }

    public synchronized void saveTextMessage(String id, String sender, String recipient, boolean isGroup, String text, long timestamp) {
        //hey = ""; // Limpiar debug
        
        // 1. Guardar el mensaje
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO messages (id,type,sender,recipient,is_group,text_content,timestamp) VALUES (?,?,?,?,?,?,?)")) {
            ps.setString(1, id);
            ps.setString(2, "TEXT");
            ps.setString(3, sender);
            ps.setString(4, recipient);
            ps.setInt(5, isGroup ? 1 : 0);
            ps.setString(6, text);
            ps.setLong(7, timestamp);
            ps.executeUpdate();
            //hey += ">> Mensaje guardado: " + text + " de " + sender + " a " + recipient + "\n";
        } catch (SQLException ex) { 
            ex.printStackTrace(); 
            return;
        }

        // 2. Guardar visibilidad 
        try {
            // Visibilidad para el remitente
            try (PreparedStatement pv = conn.prepareStatement(
                    "INSERT INTO message_visibility (message_id, username, visible) VALUES (?, ?, 1)")) {
                pv.setString(1, id);
                pv.setString(2, sender);
                pv.executeUpdate();
                //hey += ">> Visibilidad añadida para remitente: " + sender + "\n";
            }

            if (isGroup) {
                // Para grupos: todos los miembros excepto el remitente
                try (PreparedStatement gm = conn.prepareStatement(
                        "SELECT username FROM group_members WHERE group_name = ?")) {
                    gm.setString(1, recipient);
                    try (ResultSet rs = gm.executeQuery()) {
                        // Usar un nuevo PreparedStatement para cada inserción
                        try (PreparedStatement pvGroup = conn.prepareStatement(
                                "INSERT INTO message_visibility (message_id, username, visible) VALUES (?, ?, 1)")) {
                            while (rs.next()) {
                                String member = rs.getString("username");
                                if (!member.equals(sender)) {
                                    pvGroup.setString(1, id);
                                    pvGroup.setString(2, member);
                                    pvGroup.executeUpdate();
                                    //hey += ">> Visibilidad añadida para miembro: " + member + "\n";
                                }
                            }
                        }
                    }
                }
            } else {
                // Para chat directo: el destinatario
                try (PreparedStatement pvDirect = conn.prepareStatement(
                        "INSERT INTO message_visibility (message_id, username, visible) VALUES (?, ?, 1)")) {
                    pvDirect.setString(1, id);
                    pvDirect.setString(2, recipient);
                    pvDirect.executeUpdate();
                    //hey += ">> Visibilidad añadida para destinatario: " + recipient + "\n";
                }
            }
        } catch (SQLException ex) { 
            ex.printStackTrace(); 
        }
    }

    /*
    public String pStringHey(){
        return hey;
    }
     */

    public void insertGroup(String groupName) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO groups(group_name) VALUES(?)")) {
            ps.setString(1, groupName);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public void insertGroupMember(String group, String user) {
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO group_members(group_name, username) VALUES(?,?)")) {
            ps.setString(1, group);
            ps.setString(2, user);
            ps.executeUpdate();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public synchronized void deleteConversation(String user, String target) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            "UPDATE message_visibility SET visible = 0 " +
            "WHERE username = ? AND message_id IN (" +
            "  SELECT id FROM messages " +
            "  WHERE (recipient = ? OR sender = ? OR " +
            "         (recipient = ? AND sender = ?) OR " +
            "         (recipient = ? AND sender = ?))" +
            ")"
        )) {
            ps.setString(1, user);
            ps.setString(2, target);
            ps.setString(3, target);
            ps.setString(4, user);
            ps.setString(5, target);
            ps.setString(6, target);
            ps.setString(7, user);
            ps.executeUpdate();
        }
    }

    public ResultSet fetchAllUsersEver() throws SQLException {
        return conn.createStatement().executeQuery(
            "SELECT DISTINCT sender FROM messages"
        );
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

    public boolean isGroup(String name) {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM groups WHERE group_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public synchronized ResultSet fetchHistory(String username, String chatTarget) throws SQLException {
        boolean isGroup = isGroup(chatTarget);

        if (isGroup) {
            PreparedStatement ps = conn.prepareStatement(
                "SELECT m.* FROM messages m " +
                "JOIN message_visibility v ON m.id = v.message_id " +
                "WHERE m.recipient = ? AND m.is_group = 1 AND v.username = ? AND v.visible = 1 " +
                "ORDER BY m.timestamp ASC"
            );
            ps.setString(1, chatTarget);
            ps.setString(2, username);
            return ps.executeQuery();
        } else {
            // Consulta para chats directos
            PreparedStatement ps = conn.prepareStatement(
                "SELECT DISTINCT m.* FROM messages m " +
                "JOIN message_visibility v ON m.id = v.message_id " +
                "WHERE m.is_group = 0 AND v.username = ? AND v.visible = 1 " +
                "AND ((m.sender = ? AND m.recipient = ?) OR (m.sender = ? AND m.recipient = ?)) " +
                "ORDER BY m.timestamp ASC"
            );
            ps.setString(1, username);
            ps.setString(2, username);
            ps.setString(3, chatTarget);
            ps.setString(4, chatTarget);
            ps.setString(5, username);
            return ps.executeQuery();
        }
    }
}