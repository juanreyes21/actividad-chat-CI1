package proyecto_chat.server;

import proyecto_chat.common.Message;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class ClientHandler implements Runnable {
    private ConcurrentHashMap<String, Set<String>> groups;
    private Socket socket;
    private ConcurrentHashMap<String, ClientHandler> clients;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    private HistoryManager historyManager;
    private CallRelay callRelay;

    public ClientHandler(Socket socket,
                         ConcurrentHashMap<String, ClientHandler> clients,
                         ConcurrentHashMap<String, Set<String>> groups,
                         HistoryManager historyManager,
                         CallRelay callRelay) {
        this.socket = socket;
        this.clients = clients;
        this.groups = groups;
        this.historyManager = historyManager;
        this.callRelay = callRelay;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            this.username = ((String) in.readObject()).trim().toLowerCase();
            clients.put(username, this);
            System.out.println(username + " se ha unido al chat.");

            while (true) {
                Message message = (Message) in.readObject();

                switch (message.getType()) {
                    case CREATE_GROUP:
                        String groupName = message.getTextContent();
                        groups.putIfAbsent(groupName, new CopyOnWriteArraySet<>());
                        break;

                    case JOIN_GROUP:
                        String groupToJoin = message.getTextContent();
                        if (groups.containsKey(groupToJoin)) {
                            groups.get(groupToJoin).add(this.username);
                        }
                        break;

                    case TEXT:
                        String recipientText = message.getRecipient().trim().toLowerCase();
                        historyManager.saveTextMessage(
                                message.getId(), message.getSender(), recipientText,
                                message.getTextContent(), message.getTimestamp()
                        );
                        if (groups.containsKey(recipientText)) {
                            for (String member : groups.get(recipientText)) {
                                if (!member.equals(this.username) && clients.containsKey(member)) {
                                    clients.get(member).sendMessage(message);
                                }
                            }
                        } else if (clients.containsKey(recipientText)) {
                            clients.get(recipientText).sendMessage(message);
                        }
                        break;

                    case VOICE_NOTE:
                        String recipientVoice = message.getRecipient();
                        historyManager.saveVoiceNoteToDiskAndDb(
                                message.getId(), message.getSender(), recipientVoice,
                                message.getContent(), message.getFileName(), message.getTimestamp()
                        );
                        if (clients.containsKey(recipientVoice)) {
                            clients.get(recipientVoice).sendMessage(message);
                        }
                        break;

                    case CALL_START:
                        String callee = message.getRecipient();
                        if (clients.containsKey(callee)) {
                            clients.get(callee).sendMessage(message);
                            System.out.println(username + " está llamando a " + callee);
                        }
                        break;

                    case CALL_ACCEPT:
                        String caller = message.getRecipient();
                        if (clients.containsKey(caller)) {
                            clients.get(caller).sendMessage(message);
                            System.out.println(username + " aceptó la llamada de " + caller);
                        }
                        break;

                    case CALL_END:
                        String otherParty = message.getRecipient();
                        if (clients.containsKey(otherParty)) {
                            clients.get(otherParty).sendMessage(message);
                        }
                        break;
                }
            }
        } catch (Exception e) {
            if (username != null) {
                System.out.println(username + " se ha desconectado.");
                clients.remove(username);
                for (Set<String> groupMembers : groups.values()) {
                    groupMembers.remove(username);
                }
            }
        }
    }

    public void sendMessage(Message message) {
        try {
            out.writeObject(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
