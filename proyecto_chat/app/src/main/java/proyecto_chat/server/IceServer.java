package proyecto_chat.server;

import Chat.*;                 // Servicio generado por slice2java
import com.zeroc.Ice.*;        // API principal de Ice

public class IceServer {

    public static void main(String[] args) {
        int status = 0;

        // Inicializamos el comunicador de Ice
        try (Communicator communicator = Util.initialize(args)) {

            // Creamos el adaptador con endpoint WebSocket (ws)
            // Escucha en ws://127.0.0.1:12000
            ObjectAdapter adapter = communicator.createObjectAdapterWithEndpoints(
                    "ChatAdapter",
                    "ws -h 127.0.0.1 -p 12000"
            );

            // Creamos la implementación del servicio
            IceChatService service = new IceChatService();

            // Registramos la instancia con una identidad
            Identity id = Util.stringToIdentity("ChatService");
            adapter.add(service, id);

            // Activamos el adaptador
            adapter.activate();

            System.out.println("[ICE] Servidor ICE escuchando en ws://127.0.0.1:12000 como 'ChatService'");

            // Esperamos a que el servidor termine (Ctrl+C o shutdown)
            communicator.waitForShutdown();

        } catch (java.lang.Exception e) {
            e.printStackTrace();
            status = 1;
        }

        System.exit(status);
    }
}
