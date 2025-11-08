package proyecto_chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.PreparedStatement;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Server implements Runnable {
    private int port;
    private ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Set<String>> groups = new ConcurrentHashMap<>();
    private Set<String> users = ConcurrentHashMap.newKeySet(); 
    private HistoryManager historyManager;
    private CallRelay callRelay;

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        new Thread(this).start();
    }

    public void registerUserIfNotExists(String username) {
        if (username == null || username.isBlank()) return;

        users.add(username);

        try (PreparedStatement ps = historyManager.getConnection()
            .prepareStatement("INSERT OR IGNORE INTO users(username) VALUES(?)")) {
            ps.setString(1, username);
            ps.executeUpdate();
        } catch (Exception ignored) {}
    }



    public Set<String> getUsers() {
        return users;
    }

    public java.util.Set<String> listGroupsForUserFromDb(String username) {
        java.util.Set<String> out = java.util.concurrent.ConcurrentHashMap.newKeySet();
        try (java.sql.PreparedStatement ps = historyManager.getConnection()
                .prepareStatement("SELECT group_name FROM group_members WHERE username = ?")) {
            ps.setString(1, username);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(rs.getString("group_name"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    private void hydrateGroupsFromDb() {
        try (java.sql.PreparedStatement ps = historyManager.getConnection()
                .prepareStatement("SELECT group_name, username FROM group_members");
            java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                String g = rs.getString("group_name");
                String u = rs.getString("username");
                groups.putIfAbsent(g, java.util.concurrent.ConcurrentHashMap.newKeySet());
                groups.get(g).add(u);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    @Override
    public void run() {
        System.out.println("Servidor de chat iniciado en el puerto " + port);
        try {
            historyManager = new HistoryManager();
            hydrateGroupsFromDb();
        } catch (Exception e) {
            System.err.println("No se pudo iniciar HistoryManager: " + e.getMessage());
            return;
        }

        /*
        // Para borrar todas las TABLAS
        try {
            Statement s = historyManager.getConnection().createStatement();
            s.executeUpdate("DROP TABLE IF EXISTS message_visibility");
            s.executeUpdate("DROP TABLE IF EXISTS messages");
            s.executeUpdate("DROP TABLE IF EXISTS group_members");
            s.executeUpdate("DROP TABLE IF EXISTS groups");
            s.executeUpdate("DROP TABLE IF EXISTS users");
            s.close();
            System.out.println("Todas las tablas de la base de datos han sido borradas.");
        } catch (Exception e) {
            e.printStackTrace();
        }
         */
        

        try (var rs = historyManager.fetchAllUsersEver()) {
            while (rs.next()) {
                users.add(rs.getString("sender"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }



        // Relay UDP para llamadas
        callRelay = new CallRelay(10000);
        new Thread(callRelay).start();

        int proxyPort = 10001;
        ProxyListener proxyListener = new ProxyListener(proxyPort, this);
        new Thread(proxyListener).start();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                //System.out.println("Nuevo cliente conectado: " + clientSocket);
                ClientHandler clientHandler =
                        new ClientHandler(clientSocket, clients, groups, historyManager, callRelay);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new Server(9090).start();
    }

    public void createGroupFromProxy(String groupName) {
        groups.putIfAbsent(groupName, ConcurrentHashMap.newKeySet());

        historyManager.insertGroup(groupName);

        //System.out.println("Grupo creado: " + groupName);
    }


    public void joinGroupFromProxy(String username, String groupName) {
        registerUserIfNotExists(username);
        createGroupFromProxy(groupName);

        groups.putIfAbsent(groupName, ConcurrentHashMap.newKeySet());
        groups.get(groupName).add(username);

        historyManager.insertGroupMember(groupName, username);

        //System.out.println(username + " se unió al grupo: " + groupName);
    }



    public void handleProxySendText(String sender, String recipient, String text) {

        registerUserIfNotExists(sender);
        if (!groups.containsKey(recipient)) {
            registerUserIfNotExists(recipient);
        }

        


        // Guardar en historial
        boolean isGroup = groups.containsKey(recipient);
        historyManager.saveTextMessage(
            UUID.randomUUID().toString(), sender, recipient, isGroup, text, System.currentTimeMillis()
        );


        // Si es grupo -> reenviar a miembros (si hay handlers conectados en 'clients')
        if (groups.containsKey(recipient)) {
            for (String member : groups.get(recipient)) {
                if (!member.equals(sender) && clients.containsKey(member)) {
                    // Creamos un Message y lo enviamos por sendMessage
                    proyecto_chat.common.Message msg =
                        new proyecto_chat.common.Message(proyecto_chat.common.Message.MessageType.TEXT, sender, recipient, text);
                    clients.get(member).sendMessage(msg);
                }
            }
        } else if (clients.containsKey(recipient)) {
            proyecto_chat.common.Message msg =
                new proyecto_chat.common.Message(proyecto_chat.common.Message.MessageType.TEXT, sender, recipient, text);
            clients.get(recipient).sendMessage(msg);
        }

        /* DEBUG
        System.out.println(">> Nuevo mensaje recibido:");
        System.out.println("   De: " + sender);
        System.out.println("   Para: " + recipient);
        System.out.println("   Texto: " + text);
        System.out.println(">> Guardada visibilidad para: " + sender + " y " + recipient);
        System.out.println(historyManager.pStringHey());
        */
    }

    public void handleProxySendVoice(String sender, String recipient, byte[] content, String originalFileName) {
        registerUserIfNotExists(sender);
        if (!groups.containsKey(recipient)) {
            registerUserIfNotExists(recipient);
        }

        boolean isGroup = groups.containsKey(recipient);
        String msgId = UUID.randomUUID().toString();
        long ts = System.currentTimeMillis();

        // Guardar en storage + DB
        historyManager.saveVoiceNoteToDiskAndDb(msgId, sender, recipient, content, originalFileName, ts);

        proyecto_chat.common.Message msg = new proyecto_chat.common.Message(
            proyecto_chat.common.Message.MessageType.VOICE_NOTE, sender, recipient, content, originalFileName
        );

        if (isGroup) {
            for (String member : groups.get(recipient)) {
                if (!member.equalsIgnoreCase(sender) && clients.containsKey(member)) {
                    clients.get(member).sendMessage(msg);
                }
            }
        } else if (clients.containsKey(recipient.toLowerCase())) {
            clients.get(recipient.toLowerCase()).sendMessage(msg);
        }
    }

    // Devuelve base64 del archivo de audio guardado (por nombre de archivo en storage/audio)
    public String fetchAudioBase64(String fileName) {
        try {
            return historyManager.getAudioFileBase64(fileName);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void backfillVoiceNoteVisibility() {
        try {
            if (historyManager != null) {
                historyManager.backfillVoiceNoteVisibility();
                // Normalizar file_path de voice notes antiguas (ruta -> basename)
                historyManager.normalizeVoiceNoteFilePaths();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public java.sql.ResultSet fetchHistoryForRecipient(String username, String chatTarget) throws java.sql.SQLException {
        return this.historyManager.fetchHistory(username, chatTarget);
    }


    public void deleteChatFromProxy(String user, String other) {
        try {
            historyManager.deleteConversation(user, other);
            //System.out.println(">> Conversación eliminada entre " + user + " y " + other);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ConcurrentHashMap<String, Set<String>> getGroups() {
        return groups;
    }


}