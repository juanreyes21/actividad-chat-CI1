package proyecto_chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server implements Runnable {
    private int port;
    private ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Set<String>> groups = new ConcurrentHashMap<>();
    private HistoryManager historyManager;
    private CallRelay callRelay;

    public Server(int port) {
        this.port = port;
    }

    public void start() {
        new Thread(this).start();
    }

    @Override
    public void run() {
        System.out.println("Servidor de chat iniciado en el puerto " + port);
        try {
            historyManager = new HistoryManager();
        } catch (Exception e) {
            System.err.println("No se pudo iniciar HistoryManager: " + e.getMessage());
            return;
        }

        // Relay UDP para llamadas
        callRelay = new CallRelay(10000);
        new Thread(callRelay).start();

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuevo cliente conectado: " + clientSocket);
                ClientHandler clientHandler =
                        new ClientHandler(clientSocket, clients, groups, historyManager, callRelay);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new Server(9090).start();
    }
}