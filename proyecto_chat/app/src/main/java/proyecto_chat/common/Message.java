package proyecto_chat.common;

import java.io.Serializable;

// Serializable permite convertir este objeto en bytes para enviarlo por la red.
public class Message implements Serializable {
    // Define los posibles tipos de mensajes que podemos enviar.
    public enum MessageType {
        TEXT,
        VOICE_NOTE,
        CREATE_GROUP, // Nuevo
        JOIN_GROUP    // Nuevo
        // Añadiremos más tipos después, como LOGIN, CREATE_GROUP, etc.
    }

    private final MessageType type;
    private final String sender;
    private final String recipient; // Puede ser un usuario o el nombre de un grupo
    private final byte[] content;   // Contenido del mensaje (texto o audio en bytes)
    private final String fileName;  // Para guardar notas de voz con su nombre original

    // Constructor para mensajes de texto
    public Message(MessageType type, String sender, String recipient, String textContent) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.content = textContent.getBytes(); // Convertimos el texto a bytes
        this.fileName = null;
    }

    // Constructor para notas de voz u otros archivos
    public Message(MessageType type, String sender, String recipient, byte[] fileContent, String fileName) {
        this.type = type;
        this.sender = sender;
        this.recipient = recipient;
        this.content = fileContent;
        this.fileName = fileName;
    }

    // Getters para acceder a los datos del mensaje
    public MessageType getType() { return type; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public byte[] getContent() { return content; }
    public String getFileName() { return fileName; }
    
    // Método para obtener el contenido de texto fácilmente
    public String getTextContent() {
        return new String(this.content);
    }
}