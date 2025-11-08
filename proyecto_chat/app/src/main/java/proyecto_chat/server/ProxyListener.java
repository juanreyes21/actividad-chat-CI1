package proyecto_chat.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.ResultSet;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * ProxyListener: escucha conexiones TCP (puerto 10001 por ejemplo).
 * Recibe JSON line-delimited (cada línea = JSON request) con la forma:
 *
 * { "action":"create_group" , "username":"alice", "group":"equipo" }
 * { "action":"send_text" , "username":"alice", "recipient":"bob" , "text":"hola" }
 * { "action":"fetch_history", "recipient":"equipo" }
 *
 * Responde con JSON en la misma conexión (una línea).
 */
public class ProxyListener implements Runnable {
    private final int port;
    private final Server server; // referencia al Server para acceder a maps y historyManager

    public ProxyListener(int port, Server server) {
        this.port = port;
        this.server = server;
    }

    @Override
    public void run() {
        try (ServerSocket ss = new ServerSocket(port)) {
            System.out.println("ProxyListener escuchando en puerto " + port);
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> handleConnection(s)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void handleConnection(Socket s) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
             PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true)) {

            String line;
            while ((line = br.readLine()) != null) {
                try {
                    JSONObject req = new JSONObject(line);
                    JSONObject resp = processRequest(req);
                    pw.println(resp.toString());
                } catch (Exception ex) {
                    JSONObject err = new JSONObject();
                    err.put("status", "error");
                    err.put("message", ex.getMessage());
                    pw.println(err.toString());
                }
            }
        } catch (Exception e) {
            // conexión cerrada o error
        }
    }

    private JSONObject processRequest(JSONObject req) {
        String action = req.optString("action", "");
        JSONObject res = new JSONObject();
        switch (action) {
            case "create_group": {
                String group = req.getString("group");
                server.createGroupFromProxy(group);
                res.put("status", "ok");
                break;
            }

            case "join_group": {
                String group = req.optString("group", null);
                String username = req.optString("username", null);
                if (group == null || username == null) {
                    return new JSONObject().put("status","error").put("message","username and group required");
                }
                server.joinGroupFromProxy(username, group);
                return new JSONObject().put("status","ok");
            }

            case "send_text": {
                String sender = req.optString("username", null);
                String recipient = req.optString("recipient", null);
                String text = req.optString("text", null);
                if (sender == null || recipient == null || text == null) {
                    return new JSONObject().put("status","error").put("message","username, recipient and text required");
                }
                server.handleProxySendText(sender, recipient, text);
                return new JSONObject().put("status","ok");
            }

            case "send_voice": {
                String sender = req.optString("username", null);
                String recipient = req.optString("recipient", null);
                String fileName = req.optString("fileName", null);
                String contentB64 = req.optString("content", null);
                if (sender == null || recipient == null || fileName == null || contentB64 == null) {
                    return new JSONObject().put("status","error").put("message","username, recipient, fileName and content required");
                }
                try {
                    byte[] content = java.util.Base64.getDecoder().decode(contentB64);
                    server.handleProxySendVoice(sender, recipient, content, fileName);
                    return new JSONObject().put("status","ok");
                } catch (IllegalArgumentException iae) {
                    return new JSONObject().put("status","error").put("message","invalid base64");
                }
            }

            case "delete_chat": {
                String sender = req.optString("username", null);
                String recipient = req.optString("recipient", null);
                if (sender == null || recipient == null) {
                    return new JSONObject().put("status","error").put("message","username and recipient required");
                }
                server.deleteChatFromProxy(sender, recipient);
                return new JSONObject().put("status","ok").put("message","conversation deleted");
            }


            case "fetch_history": {
                String username = req.optString("username", null);
                String chatTarget = req.optString("recipient", null);
                if (username == null || chatTarget == null) {
                    return new JSONObject().put("status","error").put("message","username and recipient required");
                }
                JSONArray arr = new JSONArray();
                try (ResultSet rs = server.fetchHistoryForRecipient(username, chatTarget)) {
                    while (rs.next()) {
                        JSONObject m = new JSONObject();
                        m.put("id", rs.getString("id"));
                        m.put("type", rs.getString("type"));
                        m.put("sender", rs.getString("sender"));
                        m.put("recipient", rs.getString("recipient"));
                        m.put("text_content", rs.getString("text_content"));
                        m.put("file_path", rs.getString("file_path"));
                        m.put("timestamp", rs.getLong("timestamp"));
                        arr.put(m);
                    }
                } catch (Exception e) { e.printStackTrace(); }
                return new JSONObject().put("status","ok").put("messages", arr);
            }

            case "fetch_audio": {
                String file = req.optString("file", null);
                if (file == null) return new JSONObject().put("status","error").put("message","file required");
                try {
                    String b64 = server.fetchAudioBase64(file);
                    if (b64 == null) return new JSONObject().put("status","error").put("message","file not found");
                    JSONObject out = new JSONObject();
                    out.put("status","ok");
                    out.put("content", b64);
                    out.put("mime", "audio/wav");
                    return out;
                } catch (Exception e) {
                    return new JSONObject().put("status","error").put("message", e.getMessage());
                }
            }

            case "backfill_visibility": {
                try {
                    server.backfillVoiceNoteVisibility();
                    return new JSONObject().put("status","ok").put("message","backfill executed");
                } catch (Exception e) {
                    return new JSONObject().put("status","error").put("message", e.getMessage());
                }
            }

            case "list_groups": {
                String username = req.optString("username", null);
                if (username == null) {
                    return new JSONObject().put("status","error").put("message","username required");
                }
                JSONArray arr = new JSONArray();
                for (String gname : server.listGroupsForUserFromDb(username)) {
                    arr.put(gname);
                }
                return new JSONObject().put("status","ok").put("groups", arr);
            }

            case "login": {
                String username = req.optString("username", null);
                if (username == null) {
                    return new JSONObject().put("status","error").put("message","username required");
                }
                server.registerUserIfNotExists(username);
                return new JSONObject().put("status","ok");
            }


            case "list_users": {
                JSONArray arr = new JSONArray();
                for (String u : server.getUsers()) arr.put(u);
                res.put("status", "ok");
                res.put("users", arr);
                break;
            }



            default:
                res.put("status", "error");
                res.put("message", "unknown action");
        }
        return res;
    }
}