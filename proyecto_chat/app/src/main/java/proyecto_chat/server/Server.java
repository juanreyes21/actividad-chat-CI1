package proyecto_chat.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class Server {
    // Mapa seguro para hilos que almacena el manejador de cada cliente conectado.
    // La clave es el nombre de usuario y el valor es el objeto que maneja su conexión.
    private static ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    // Mapa seguro para hilos que almacena los miembros de cada grupo.
    // La clave es el nombre del grupo y el valor es un Set de nombres de usuario.
    private static ConcurrentHashMap<String, Set<String>> groups = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        int port = 9090; // Puerto en el que escuchará el servidor
        System.out.println("Servidor de chat iniciado en el puerto " + port);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // Bucle infinito para aceptar clientes continuamente.
            while (true) {
                Socket clientSocket = serverSocket.accept(); // Espera a que un cliente se conecte.
                System.out.println("Nuevo cliente conectado: " + clientSocket);

                // Por cada cliente, crea un nuevo hilo para manejar su comunicación.
                // Esto permite al servidor atender a múltiples clientes a la vez.
                ClientHandler clientHandler = new ClientHandler(clientSocket, clients, groups);
                new Thread(clientHandler).start();
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }
}