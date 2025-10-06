package proyecto_chat.client;

import proyecto_chat.common.Message;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class Client {
    public static void main(String[] args) {
        String serverAddress = "127.0.0.1"; // IP del servidor (localhost)
        int port = 9090;

        try (Socket socket = new Socket(serverAddress, port);
             ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
             ObjectInputStream in = new ObjectInputStream(socket.getInputStream());
             Scanner scanner = new Scanner(System.in)) {

            System.out.print("Ingresa tu nombre de usuario: ");
            String username = scanner.nextLine();
            out.writeObject(username); // Envía el nombre de usuario al servidor para registrarse.

            // Inicia un hilo separado para escuchar constantemente los mensajes entrantes del servidor.
            Thread listenerThread = new Thread(() -> {
                try {
                    while (true) {
                        Message message = (Message) in.readObject();
                        
                        switch (message.getType()) {
                            case TEXT:
                                System.out.println("\n[" + message.getSender() + "]: " + message.getTextContent());
                                break;
                            case VOICE_NOTE:
                                // Guarda el archivo de audio recibido en la carpeta del proyecto.
                                String receivedFileName = "recibido_" + message.getFileName();
                                Files.write(Paths.get(receivedFileName), message.getContent());
                                System.out.println("\nNota de voz recibida de [" + message.getSender() + "], guardada como: " + receivedFileName);
                                break;
                        }
                    }
                } catch (Exception e) {
                    System.out.println("\nDesconectado del servidor.");
                }
            });
            listenerThread.start();

            // Bucle principal para leer la entrada del usuario y enviar mensajes/comandos.
            System.out.println("\nConectado. Comandos disponibles:");
            System.out.println("  destinatario@texto_del_mensaje");
            System.out.println("  voicenote@destinatario@ruta_del_archivo");
            System.out.println("  creategroup@nombre_del_grupo");
            System.out.println("  joingroup@nombre_del_grupo\n");

            while (true) {
                String line = scanner.nextLine();
                String[] parts = line.split("@", 3); // Dividimos hasta en 3 partes.
                
                if (parts.length >= 2) {
                    String commandOrRecipient = parts[0].toLowerCase();
                    String content = parts[1];
                    Message msg = null;

                    if (commandOrRecipient.equals("voicenote") && parts.length == 3) {
                        String recipient = parts[1];
                        String filePath = parts[2];
                        try {
                            File file = new File(filePath);
                            byte[] fileContent = Files.readAllBytes(file.toPath());
                            msg = new Message(Message.MessageType.VOICE_NOTE, username, recipient, fileContent, file.getName());
                            System.out.println("Enviando nota de voz a " + recipient + "...");
                        } catch (IOException e) {
                            System.out.println("Error al leer el archivo: " + e.getMessage());
                        }
                    } else if (parts.length == 2) {
                        switch (commandOrRecipient) {
                            case "creategroup":
                                msg = new Message(Message.MessageType.CREATE_GROUP, username, "server", content);
                                System.out.println("Creando grupo: " + content + "...");
                                break;
                            case "joingroup":
                                msg = new Message(Message.MessageType.JOIN_GROUP, username, "server", content);
                                System.out.println("Uniéndote al grupo: " + content + "...");
                                break;
                            default: // Mensaje de texto normal
                                msg = new Message(Message.MessageType.TEXT, username, commandOrRecipient, content);
                                break;
                        }
                    }

                    if (msg != null) {
                        out.writeObject(msg);
                    } else if (!commandOrRecipient.equals("voicenote")) {
                         System.out.println("Formato de comando incorrecto.");
                    }
                } else {
                    System.out.println("Formato incorrecto.");
                }
            }
        } catch (Exception e) {
            System.err.println("No se pudo conectar al servidor: " + e.getMessage());
        }
    }
}