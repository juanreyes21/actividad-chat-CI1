module Chat {
    // Mensaje básico
    struct Msg {
        string id;
        string sender;
        string recipient;
        string text;
        long   timestamp;
    };

    // Secuencia de mensajes para retornar historiales
    sequence<Msg> MsgSeq;

    // Callback que el servidor usará para notificar al cliente (push tiempo real)
    interface ClientCallback {
        void onNewMessage(Msg m);
        void onUserJoined(string username);
        void onUserLeft(string username);
    };

    // Servicio principal invocado por el cliente
    interface Service {
        // Registro de usuario + callback
        void login(string username, ClientCallback* cb);

        // Envío de mensaje de texto
        void sendText(string username, string recipient, string text);

        // Gestión de grupos
        void createGroup(string username, string group);
        void joinGroup(string username, string group);

        // Historial de mensajes hacia un usuario o grupo
        MsgSeq getHistory(string recipient, int limit);
    };
};
