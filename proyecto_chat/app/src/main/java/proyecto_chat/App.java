package proyecto_chat;

import proyecto_chat.server.Server;
import proyecto_chat.client.Client;

public class App {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("server")) {
            Server.main(new String[]{});
        } else if (args.length > 0 && args[0].equalsIgnoreCase("client")) {
            Client.main(new String[]{});
        } else {
            System.out.println("Uso:");
            System.out.println("  ./gradlew run --args='server'   # Inicia servidor");
            System.out.println("  ./gradlew run --args='client'   # Inicia cliente");
        }
    }
}
