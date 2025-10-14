package proyecto_chat.server;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CallRelay implements Runnable {
    private final DatagramSocket socket;
    private final int port;

    // callId -> conjunto de sockets participantes
    private final ConcurrentHashMap<String, Set<InetSocketAddress>> calls = new ConcurrentHashMap<>();

    public CallRelay(int port) {
        this.port = port;
        try {
            this.socket = new DatagramSocket(port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void registerParticipant(String callId, InetSocketAddress addr) {
        calls.computeIfAbsent(
                callId,
                k -> Collections.newSetFromMap(new ConcurrentHashMap<InetSocketAddress, Boolean>())
        ).add(addr);
        System.out.println("CallRelay: participante registrado para call " + callId + " -> " + addr);
    }

    public void unregisterParticipant(String callId, InetSocketAddress addr) {
        Set<InetSocketAddress> set = calls.get(callId);
        if (set != null) {
            set.remove(addr);
            if (set.isEmpty()) {
                calls.remove(callId);
                System.out.println("CallRelay: call " + callId + " sin participantes, limpiado.");
            }
        }
    }

    @Override
    public void run() {
        System.out.println("CallRelay UDP escuchando en puerto " + port);

        // Buffer más holgado (audio + encabezado)
        byte[] buf = new byte[8192];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);

        while (true) {
            try {
                socket.receive(packet);

                int len = packet.getLength();
                if (len < 4) continue; // paquete inválido

                byte[] data = Arrays.copyOf(packet.getData(), len);

                // [4 bytes big-endian = callIdLen][callId UTF-8][payload...]
                int callIdLen =
                        ((data[0] & 0xFF) << 24) |
                        ((data[1] & 0xFF) << 16) |
                        ((data[2] & 0xFF) << 8)  |
                        (data[3] & 0xFF);

                if (callIdLen < 0 || 4 + callIdLen > data.length) continue; // corrupto

                String callId = new String(data, 4, callIdLen, StandardCharsets.UTF_8);
                byte[] payload = Arrays.copyOfRange(data, 4 + callIdLen, data.length);

                InetSocketAddress from = (InetSocketAddress) packet.getSocketAddress();

                // Registro/autorregistro:
               
                if (payload.length == 1 && payload[0] == 0x01) {
                    // BYE: desregistrar y no reenviar nada
                    unregisterParticipant(callId, from);
                    continue;
                }

                // Registro automático si llega paquete de audio o de registro
                registerParticipant(callId, from);

                // Si es solo registro (sin audio), no hay nada que reenviar
                if (payload.length == 0) {
                    continue;
                }

                // Reenvío del audio a todos menos al remitente
                Set<InetSocketAddress> participants = calls.get(callId);
                if (participants == null || participants.isEmpty()) continue;

                for (InetSocketAddress p : participants) {
                    if (!p.equals(from)) {
                        DatagramPacket out = new DatagramPacket(payload, payload.length, p.getAddress(), p.getPort());
                        socket.send(out);
                    }
                }

            } catch (Exception e) {
                // En un cierre intencional del socket, caería aquí con SocketException.
                e.printStackTrace();
            }
        }
    }
}
