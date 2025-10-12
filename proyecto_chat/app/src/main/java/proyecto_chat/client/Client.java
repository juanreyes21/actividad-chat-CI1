package proyecto_chat.client;

import proyecto_chat.common.Message;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

    public static void main(String[] args) {
        String serverAddress = "127.0.0.1";
        int port = 9090;

        try (Socket socket = new Socket(serverAddress, port);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
                Scanner scanner = new Scanner(System.in)) {

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

                            case CALL_START:
                                System.out.println("\n[" + message.getSender()
                                        + "] está intentando llamarte. Escribe: callaccept@" + message.getSender());
                                break;

                            case CALL_ACCEPT:
                                System.out.println(
                                        "\n[" + message.getSender() + "] aceptó tu llamada. Conectando audio...");
                                startCall(message.getId(), serverAddress);
                                break;

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
                        case "call":
                            msg = new Message(Message.MessageType.CALL_START, username, content, "");
                            break;
                        case "callaccept":
                            msg = new Message(Message.MessageType.CALL_ACCEPT, username, content, "");
                            break;
                        case "hangup":
                            msg = new Message(Message.MessageType.CALL_END, username, content, "");
                            stopCall();
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
}
