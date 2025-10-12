package proyecto_chat.client;

import javax.sound.sampled.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class AudioSender implements Runnable {
    private final String callId;
    private final InetAddress serverAddr;
    private final int serverPort;
    private volatile boolean running = true;

    public AudioSender(String callId, InetAddress serverAddr, int serverPort) {
        this.callId = callId;
        this.serverAddr = serverAddr;
        this.serverPort = serverPort;
    }

    public void stop() { running = false; }

    @Override
    public void run() {
        try {
            // Formato estándar PCM
            AudioFormat format = new AudioFormat(44100.0f, 16, 1, true, false);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            DatagramSocket socket = new DatagramSocket();
            byte[] buffer = new byte[4096];

            while (running) {
                int read = microphone.read(buffer, 0, buffer.length);
                if (read > 0) {
                    
                    byte[] callIdBytes = callId.getBytes();
                    byte[] packet = new byte[4 + callIdBytes.length + read];

                    
                    packet[0] = (byte)(callIdBytes.length >> 24);
                    packet[1] = (byte)(callIdBytes.length >> 16);
                    packet[2] = (byte)(callIdBytes.length >> 8);
                    packet[3] = (byte)(callIdBytes.length);

                    
                    System.arraycopy(callIdBytes, 0, packet, 4, callIdBytes.length);
                    
                    System.arraycopy(buffer, 0, packet, 4 + callIdBytes.length, read);

                    DatagramPacket dp = new DatagramPacket(packet, packet.length, serverAddr, serverPort);
                    socket.send(dp);
                }
            }

            microphone.stop();
            microphone.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
