package proyecto_chat.server;

import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CallRelay implements Runnable {
    private DatagramSocket socket;
    private int port;
    private ConcurrentHashMap<String, Set<InetSocketAddress>> calls = new ConcurrentHashMap<>();

    public CallRelay(int port) {
        this.port = port;
        try {
            socket = new DatagramSocket(port);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void registerParticipant(String callId, InetSocketAddress addr) {
        calls.computeIfAbsent(callId,
                k -> Collections.newSetFromMap(new ConcurrentHashMap<InetSocketAddress, Boolean>())).add(addr);
        System.out.println("CallRelay: participante registrado para call " + callId + " -> " + addr);
    }

    public void unregisterParticipant(String callId, InetSocketAddress addr) {
        Set<InetSocketAddress> s = calls.get(callId);
        if (s != null)
            s.remove(addr);
    }

    @Override
    public void run() {
        System.out.println("CallRelay UDP escuchando en puerto " + port);
        byte[] buf = new byte[4096];
        DatagramPacket packet = new DatagramPacket(buf, buf.length);
        while (true) {
            try {
                socket.receive(packet);
                // Dentro del while de CallRelay.run() tras recibir 'packet'
                int len = packet.getLength();
                byte[] data = Arrays.copyOf(packet.getData(), len);

                // 1) Parseo encabezado binario: [4 bytes len][callId][audio]
                if (data.length < 4)
                    continue;
                int callIdLen = ((data[0] & 0xFF) << 24) | ((data[1] & 0xFF) << 16) | ((data[2] & 0xFF) << 8)
                        | (data[3] & 0xFF);
                if (4 + callIdLen > data.length)
                    continue;

                String callId = new String(data, 4, callIdLen);
                byte[] payload = Arrays.copyOfRange(data, 4 + callIdLen, data.length);

                // 2) Autorregistro del remitente (para que el relay conozca su puerto de
                // escucha si le llegó un registro)
                Set<InetSocketAddress> participants = calls.computeIfAbsent(
                        callId, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
                participants.add((InetSocketAddress) packet.getSocketAddress());

                // 3) Reenvío a todos menos al remitente
                for (InetSocketAddress p : participants) {
                    if (!p.equals(packet.getSocketAddress())) {
                        DatagramPacket out = new DatagramPacket(payload, payload.length, p.getAddress(), p.getPort());
                        socket.send(out);
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
