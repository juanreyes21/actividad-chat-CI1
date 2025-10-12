package proyecto_chat.client;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.*;

public class AudioReceiver implements Runnable, AutoCloseable {
    private final int listenPort;
    private final String relayHost;
    private final int relayPort;
    private final String callId;

    private volatile boolean running = true;
    private DatagramSocket socket;      // ¡hazlo de campo para poder cerrarlo!
    private SourceDataLine speakers;    // idem

    public AudioReceiver(int listenPort, String relayHost, int relayPort, String callId) {
        this.listenPort = listenPort;
        this.relayHost = relayHost;
        this.relayPort = relayPort;
        this.callId = callId;
    }

    // método "stop" seguro (nombre propio para no confundir con Thread.stop)
    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) socket.close();
        if (speakers != null) {
            try { speakers.drain(); } catch (Exception ignored) {}
            try { speakers.close(); } catch (Exception ignored) {}
        }
    }

    // por si usas try-with-resources en otro lado
    @Override public void close() { stop(); }

    @Override
    public void run() {
        try {
            AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, false);
            speakers = (SourceDataLine) AudioSystem.getLine(new DataLine.Info(SourceDataLine.class, format));
            speakers.open(format);
            speakers.start();

            socket = new DatagramSocket(listenPort);

            // 2) ENVÍO DE REGISTRO (ver abajo la corrección del DatagramPacket)
            byte[] idBytes = callId.getBytes();
            byte[] reg = new byte[4 + idBytes.length];
            reg[0] = (byte)(idBytes.length >> 24);
            reg[1] = (byte)(idBytes.length >> 16);
            reg[2] = (byte)(idBytes.length >> 8);
            reg[3] = (byte)(idBytes.length);
            System.arraycopy(idBytes, 0, reg, 4, idBytes.length);

            DatagramPacket regPacket =
                new DatagramPacket(reg, reg.length, new InetSocketAddress(relayHost, relayPort));
            socket.send(regPacket);

            byte[] buf = new byte[8192];
            DatagramPacket packet = new DatagramPacket(buf, buf.length);

            while (running) {
                socket.receive(packet); // se desbloquea con close() al hacer stop()
                speakers.write(packet.getData(), 0, packet.getLength());
            }
        } catch (Exception e) {
            // Si el socket se cerró por stop(), habrá un SocketException: está bien.
            if (running) e.printStackTrace();
        } finally {
            stop(); // asegura cierre si salimos por excepción
        }
    }
}
