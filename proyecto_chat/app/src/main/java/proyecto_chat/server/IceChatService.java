package proyecto_chat.server;

import Chat.*; // Clases generadas por slice2java
import com.zeroc.Ice.Current;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class IceChatService implements Service {

    // Lista de clientes conectados (callback)
    private final Map<String, ClientCallbackPrx> clients = new ConcurrentHashMap<>();

    public IceChatService() {}

    @Override
    public void login(String username, ClientCallbackPrx cb, Current current) {
        System.out.println("[ICE] login: " + username);
        clients.put(username, cb);

        // Notificar a los demás
        broadcastUserJoined(username);
    }

    @Override
    public void sendText(String username, String recipient, String text, Current current) {
        System.out.println("[ICE] sendText: " + username + " -> " + recipient);

        Msg msg = new Msg();
        msg.id = UUID.randomUUID().toString();
        msg.sender = username;
        msg.recipient = recipient;
        msg.text = text;
        msg.timestamp = System.currentTimeMillis();

        broadcastNewMessage(msg);
    }

    @Override
    public void createGroup(String username, String group, Current current) {
        System.out.println("[ICE] createGroup: " + username + " crea " + group);
    }

    @Override
    public void joinGroup(String username, String group, Current current) {
        System.out.println("[ICE] joinGroup: " + username + " se une a " + group);
    }

    @Override
    public Msg[] getHistory(String recipient, int limit, Current current) {
        System.out.println("[ICE] getHistory: " + recipient + " (limit=" + limit + ")");

        // TODO: conectar con HistoryManager
        return new Msg[0];
    }

    // ---------------------------
    // Métodos auxiliares internos
    // ---------------------------

    private void broadcastNewMessage(Msg msg) {
        for (var entry : clients.entrySet()) {
            try {
                entry.getValue().onNewMessage(msg);
            } catch (Exception e) {
                System.err.println("[ICE] Error notify newMessage: " + e.getMessage());
            }
        }
    }

    private void broadcastUserJoined(String username) {
        for (var entry : clients.entrySet()) {
            try {
                entry.getValue().onUserJoined(username);
            } catch (Exception e) {
                System.err.println("[ICE] Error notify userJoined: " + e.getMessage());
            }
        }
    }
}
