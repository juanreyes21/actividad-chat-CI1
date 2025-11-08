package proyecto_chat.server;

import proyecto_chat.common.Message;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class ClientHandler implements Runnable {
    private ConcurrentHashMap<String, Set<String>> groups;
    private Socket socket;
    private ConcurrentHashMap<String, ClientHandler> clients;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username; // nombre original con mayúsculas/minúsculas

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

            // Leer username original (SIN convertir a minúsculas)
            this.username = ((String) in.readObject()).trim();
            
            // Registrar con clave en minúsculas para búsquedas case-insensitive
            clients.put(username.toLowerCase(), this);
            
            System.out.println(username + " se ha unido al chat.");

            while (true) {
                Message message = (Message) in.readObject();

                switch (message.getType()) {
                    case CREATE_GROUP:
                        String groupName = message.getTextContent();
                        groups.putIfAbsent(groupName, new CopyOnWriteArraySet<>());
                        historyManager.insertGroup(groupName);
                        System.out.println("Grupo creado: " + groupName);
                        break;

                    case JOIN_GROUP:
                        String groupToJoin = message.getTextContent();
                        if (groups.containsKey(groupToJoin)) {
                            groups.get(groupToJoin).add(this.username);
                            historyManager.insertGroupMember(groupToJoin, this.username);
                            System.out.println(username + " se unió al grupo: " + groupToJoin);
                        }
                        break;

                    case TEXT:
                        String recipientText = message.getRecipient();
                        boolean isGroup = groups.containsKey(recipientText);
                        
                        // Guardar con nombres originales
                        historyManager.saveTextMessage(
                            message.getId(), 
                            message.getSender(), 
                            recipientText,
                            isGroup,
                            message.getTextContent(), 
                            message.getTimestamp()
                        );
                        
                        if (isGroup) {
                            // Mensaje de grupo
                            for (String member : groups.get(recipientText)) {
                                // Comparar case-insensitive
                                if (!member.equalsIgnoreCase(this.username)) {
                                    ClientHandler handler = clients.get(member.toLowerCase());
                                    if (handler != null) {
                                        handler.sendMessage(message);
                                    }
                                }
                            }
                        } else {
                            // Mensaje directo - buscar con clave lowercase
                            ClientHandler handler = clients.get(recipientText.toLowerCase());
                            if (handler != null) {
                                handler.sendMessage(message);
                            }
                        }
                        break;

                    case VOICE_NOTE:
                        String recipientVoice = message.getRecipient();
                        historyManager.saveVoiceNoteToDiskAndDb(
                            message.getId(), 
                            message.getSender(), 
                            recipientVoice,
                            message.getContent(), 
                            message.getFileName(), 
                            message.getTimestamp()
                        );

                        // Enviar la nota de voz a los miembros apropiados.
                        // Si es un grupo, enviarla a todos los miembros excepto el remitente.
                        // Si es un chat directo, enviarla al destinatario.
                        boolean isGroupVoice = groups.containsKey(recipientVoice);
                        if (isGroupVoice) {
                            for (String member : groups.get(recipientVoice)) {
                                if (!member.equalsIgnoreCase(this.username)) {
                                    ClientHandler handler = clients.get(member.toLowerCase());
                                    if (handler != null) {
                                        handler.sendMessage(message);
                                    }
                                }
                            }
                        } else {
                            ClientHandler voiceHandler = clients.get(recipientVoice.toLowerCase());
                            if (voiceHandler != null) {
                                voiceHandler.sendMessage(message);
                            }
                        }
                        break;

                    case CALL_START:
                        String callee = message.getRecipient();
                        ClientHandler calleeHandler = clients.get(callee.toLowerCase());
                        if (calleeHandler != null) {
                            calleeHandler.sendMessage(message);
                            System.out.println(username + " está llamando a " + callee);
                        }
                        break;

                    case CALL_ACCEPT:
                        String caller = message.getRecipient();
                        ClientHandler callerHandler = clients.get(caller.toLowerCase());
                        if (callerHandler != null) {
                            callerHandler.sendMessage(message);
                            System.out.println(username + " aceptó la llamada de " + caller);
                        }
                        break;

                    case CALL_END:
                        String otherParty = message.getRecipient();
                        ClientHandler otherHandler = clients.get(otherParty.toLowerCase());
                        if (otherHandler != null) {
                            otherHandler.sendMessage(message);
                        }
                        break;
                }
            }
        } catch (Exception e) {
            if (username != null) {
                System.out.println(username + " se ha desconectado.");
                clients.remove(username.toLowerCase());
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