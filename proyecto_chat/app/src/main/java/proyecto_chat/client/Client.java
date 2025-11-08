package proyecto_chat.client;
import java.util.concurrent.ConcurrentHashMap;
import proyecto_chat.common.Message;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class Client {
    private static AudioSender audioSender;
    private static AudioReceiver audioReceiver;
    private static Thread senderThread;
    private static Thread receiverThread;
    private static final int CALL_RELAY_PORT = 10000;
    private static final ConcurrentHashMap<String, String> activeCallIds = new ConcurrentHashMap<>();
    private static String currentCallId = null;   // callId de la llamada en curso (1:1 o grupo)
    private static String SERVER_IP = null;       // para enviar BYE al relay en hangup


    public static void main(String[] args) {
        String serverAddress = "127.0.0.1";
        int port = 9090;

        try (Socket socket = new Socket(serverAddress, port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                Scanner scanner = new Scanner(System.in)) {

                     SERVER_IP = serverAddress;  

            System.out.print("Ingresa tu nombre de usuario: ");
            String username = scanner.nextLine().trim().toLowerCase();
            out.writeObject(username);

            Thread listenerThread = new Thread(() -> {
                try {
                    while (true) {
                        Message message = (Message) in.readObject();
                        switch (message.getType()) {
                            case TEXT:
                                System.out.println("\n[" + message.getSender() + "]: " + message.getTextContent());
                                break;

                            case VOICE_NOTE:
                                String receivedFileName = "recibido_" + message.getFileName();
                                Files.write(Paths.get(receivedFileName), message.getContent());
                                break;

                            case CALL_START: {
                                String incomingCallId = message.getTextContent(); 
                                String caller = message.getSender().toLowerCase();
                                activeCallIds.put(caller, incomingCallId);
                                System.out.println("\n[" + message.getSender()
                                + "] está intentando llamarte. Escribe: callaccept@" + message.getSender());
                                break;
                            }

                            case CALL_ACCEPT: {
                                String acceptedCallId = message.getTextContent(); 
                                System.out.println("\n[" + message.getSender() + "] aceptó tu llamada. Conectando audio...");
                                startCall(acceptedCallId, serverAddress);
                                break;
                            }

                            case CALL_END:
                                System.out.println("\nLlamada finalizada.");
                                stopCall();
                                break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("\nDesconectado del servidor.");
                }
            });
            listenerThread.start();

            System.out.println("\nConectado. Comandos disponibles:");
            System.out.println("destinatario@mensaje");
            System.out.println("voicenote@destinatario@archivo.wav");
            System.out.println("creategroup@nombre");
            System.out.println("joingroup@nombre");
            System.out.println("call@usuario");
            System.out.println("callaccept@usuario");
            System.out.println("hangup@usuario");

            while (true) {
                String line = scanner.nextLine();
                String[] parts = line.split("@", 3);

                if (parts.length >= 2) {
                    String cmd = parts[0].toLowerCase();
                    String content = parts[1];
                    Message msg = null;

                    switch (cmd) {
                        case "voicenote":
                            if (parts.length == 3) {
                                try {
                                    File file = new File(parts[2]);
                                    byte[] fileContent = Files.readAllBytes(file.toPath());
                                    msg = new Message(Message.MessageType.VOICE_NOTE, username, content, fileContent,
                                            file.getName());
                                } catch (IOException e) {
                                    System.out.println("Error archivo: " + e.getMessage());
                                }
                            }
                            break;
                        case "creategroup":
                            msg = new Message(Message.MessageType.CREATE_GROUP, username, "server", content);
                            break;
                        case "joingroup":
                            msg = new Message(Message.MessageType.JOIN_GROUP, username, "server", content);
                            break;
                        case "call": {
                            String newCallId = java.util.UUID.randomUUID().toString(); // <- nombre distinto
                            activeCallIds.put(content.toLowerCase(), newCallId);
                            msg = new Message(Message.MessageType.CALL_START, username, content, newCallId);
                            break;
                        }

                        case "callaccept": {
                            String peer = content.toLowerCase();
                            String acceptCallId = activeCallIds.get(peer); // <- nombre distinto
                            if (acceptCallId == null) {
                                
                                System.out.println("No tengo callId para " + content + ". Primero debe llegarte CALL_START.");
                                break;
                            }
                            msg = new Message(Message.MessageType.CALL_ACCEPT, username, content, acceptCallId);
                            startCall(acceptCallId, serverAddress);
                            break;
                        }

                        case "hangup":
                            msg = new Message(Message.MessageType.CALL_END, username, content, "");
                            stopCall();
                            activeCallIds.remove(content.toLowerCase());
                            break;
                        default:
                            msg = new Message(Message.MessageType.TEXT, username, cmd, content);
                    }

                    if (msg != null)
                        out.writeObject(msg);
                }
            }
        } catch (Exception e) {
            System.err.println("No se pudo conectar: " + e.getMessage());
        }
    }

    private static void startCall(String callId, String serverAddress) {
        try {
            int localPort = 12000;
            currentCallId = callId;
            audioSender = new AudioSender(callId, InetAddress.getByName(serverAddress), CALL_RELAY_PORT);
            audioReceiver = new AudioReceiver(localPort, serverAddress, CALL_RELAY_PORT, callId);
            senderThread = new Thread(audioSender);
            receiverThread = new Thread(audioReceiver);
            senderThread.start();
            receiverThread.start();
            System.out.println("Audio conectado en puerto " + localPort + ". Habla ahora!");
        } catch (Exception e) {
            System.err.println("Error al iniciar audio: " + e.getMessage());
        }
    }

    private static void stopCall() {
        try {
              // manda BYE al relay si hay callId activo
            if (currentCallId != null) {
            sendRelayBye(currentCallId);
            }

            if (audioSender != null)
                audioSender.stop();
            if (audioReceiver != null)
                audioReceiver.stop();
            if (senderThread != null)
                senderThread.interrupt();
            if (receiverThread != null)
                receiverThread.interrupt();
        } catch (Exception e) {
            System.err.println("Error stop audio: " + e.getMessage());
        }
    }

    private static void sendRelayBye(String callId) {
    if (callId == null || SERVER_IP == null) return;
    try (DatagramSocket s = new DatagramSocket()) {
        byte[] id = callId.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        // Framing: [4 bytes len][callId][1 byte opcode=0x01(BYE)]
        byte[] pkt = new byte[4 + id.length + 1];
        pkt[0] = (byte)(id.length >> 24);
        pkt[1] = (byte)(id.length >> 16);
        pkt[2] = (byte)(id.length >> 8);
        pkt[3] = (byte)(id.length);
        System.arraycopy(id, 0, pkt, 4, id.length);
        pkt[4 + id.length] = 0x01; // opcode BYE
        DatagramPacket dp = new DatagramPacket(
            pkt, pkt.length,
            InetAddress.getByName(SERVER_IP),
            CALL_RELAY_PORT
        );
        s.send(dp);
    } catch (Exception ignored) {}
}

}