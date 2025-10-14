package proyecto_chat.common;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

public class Message implements Serializable {
    public enum MessageType {
        TEXT,
        VOICE_NOTE,
        CREATE_GROUP,
        JOIN_GROUP,
        CALL_START,
        CALL_ACCEPT,
        CALL_REJECT,
        CALL_END
    }

    private final MessageType type;
    private final String id;
    private final String sender;
    private final String recipient;
    private final byte[] content;
    private final String fileName;
    private final long timestamp;

    // Texto (UTF-8)
    public Message(MessageType type, String sender, String recipient, String textContent) {
        this.type = type;
        this.id = UUID.randomUUID().toString();
        this.sender = sender;
        this.recipient = recipient;
        this.content = textContent != null ? textContent.getBytes(StandardCharsets.UTF_8) : new byte[0];
        this.fileName = null;
        this.timestamp = Instant.now().toEpochMilli();
    }

    // Archivo (nota de voz)
    public Message(MessageType type, String sender, String recipient, byte[] fileContent, String fileName) {
        this.type = type;
        this.id = UUID.randomUUID().toString();
        this.sender = sender;
        this.recipient = recipient;
        this.content = fileContent == null ? new byte[0] : fileContent;
        this.fileName = fileName;
        this.timestamp = Instant.now().toEpochMilli();
    }

    // Constructor genérico
    public Message(MessageType type, String id, String sender, String recipient, byte[] content, String fileName, long timestamp) {
        this.type = type;
        this.id = id;
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.fileName = fileName;
        this.timestamp = timestamp;
    }

    public MessageType getType() { return type; }
    public String getId() { return id; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public byte[] getContent() { return content; }
    public String getFileName() { return fileName; }
    public long getTimestamp() { return timestamp; }

    public String getTextContent() {
        return new String(this.content, StandardCharsets.UTF_8);
    }
}
