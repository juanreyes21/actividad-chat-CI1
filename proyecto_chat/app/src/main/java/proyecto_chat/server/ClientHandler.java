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

    public ClientHandler(Socket socket, ConcurrentHashMap<String, ClientHandler> clients, ConcurrentHashMap<String, Set<String>> groups) {
    this.socket = socket;
    this.clients = clients;
    this.groups = groups; 
}
    
    @Override
public void run() {
    try {
        out = new ObjectOutputStream(socket.getOutputStream());
        in = new ObjectInputStream(socket.getInputStream());

        // El primer mensaje que se espera de un cliente es su nombre de usuario.
        this.username = (String) in.readObject();
        clients.put(username, this);
        System.out.println(username + " se ha unido al chat.");

        // Bucle para escuchar mensajes del cliente.
        while (true) {
            Message message = (Message) in.readObject();

            // Usamos un switch para manejar los diferentes tipos de mensajes de forma ordenada.
            switch (message.getType()) {
                case CREATE_GROUP:
                    String groupName = message.getTextContent();
                    // `putIfAbsent` es un método seguro para hilos que crea el grupo solo si no existe.
                    groups.putIfAbsent(groupName, new CopyOnWriteArraySet<>());
                    System.out.println("Usuario " + username + " creó el grupo: " + groupName);
                    break;

                case JOIN_GROUP:
                    String groupToJoin = message.getTextContent();
                    if (groups.containsKey(groupToJoin)) {
                        groups.get(groupToJoin).add(this.username);
                        System.out.println("Usuario " + username + " se unió al grupo: " + groupToJoin);
                    }
                    break;
                    
                case TEXT:
                case VOICE_NOTE:
                    String recipient = message.getRecipient();
                    
                    // Comprueba si el destinatario es un grupo existente.
                    if (groups.containsKey(recipient)) {
                        // Si es un grupo, reenvía el mensaje a todos sus miembros.
                        Set<String> members = groups.get(recipient);
                        for (String member : members) {
                            // Envía a todos los miembros que estén conectados, excepto al que lo envió.
                            if (!member.equals(this.username) && clients.containsKey(member)) {
                                clients.get(member).sendMessage(message);
                            }
                        }
                    } else if (clients.containsKey(recipient)) {
                        // Si no es un grupo, es un mensaje directo a un usuario.
                        clients.get(recipient).sendMessage(message);
                    } else {
                        System.out.println("Destinatario '" + recipient + "' no encontrado.");
                    }
                    break;
            }
        }
    } catch (Exception e) {
        // Si hay un error (ej. el cliente se desconecta), se le elimina de la lista.
        if (username != null) {
            System.out.println(username + " se ha desconectado.");
            clients.remove(username);
            
            // Opcional pero recomendado: eliminar al usuario de todos los grupos a los que pertenecía.
            for (Set<String> groupMembers : groups.values()) {
                groupMembers.remove(username);
            }
        }
    }
}
    
    // Método para enviar un mensaje a este cliente
    public void sendMessage(Message message) {
        try {
            out.writeObject(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}