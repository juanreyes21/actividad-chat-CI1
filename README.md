# Sistema de Chat CI1 – Migración Completa (TCP/UDP → HTTP → ICE/WebSockets)

## Integrantes
- **Sebastián Castillo** – A00170732  
- **Juan José Reyes** – A00405296  
- **Ismael Barrionuevo** – A00403480  

---

# Descripción General

Este proyecto implementa un sistema de chat distribuido desarrollado progresivamente a través de las tareas del curso:

## **Tarea 1 – Cliente CLI + Servidor Java (TCP/UDP)**
- Mensajes 1:1 y grupos  
- Notas de voz  
- Llamadas 1:1 y grupales  
- Historial persistente con SQLite  
- Cliente en consola usando sockets TCP/UDP  

## **Tarea 2 – Cliente Web + Proxy HTTP**
- Migración del cliente a navegador (HTML/CSS/JS)  
- Proxy en Node.js que traduce HTTP → TCP  
- Servidor Java reutilizado sin cambios en lógica  
- Historial y mensajes ahora accesibles por web  

## **Proyecto Final – Migración a ZeroC ICE (RPC + WebSockets)**
- El cliente web ahora se conecta al servidor mediante **RPC con ZeroC Ice**  
- ICE se expone al navegador usando **WebSockets**  
- Se reemplaza la comunicación HTTP→TCP del Proxy para mensajes en tiempo real  
- Persistencia continúa en SQLite  
- El login y otras operaciones HTTP siguen funcionando vía proxy  

**Falta por implementar (otros miembros del equipo lo harán):**
- **Notas de voz desde el navegador (MediaRecorder + WebSocket)**  
- **Llamadas WebRTC/WebSocket desde navegador**  
- Basado en el ejemplo:  
  https://github.com/AlejandroMu/compu-internet-1/tree/master/audio_rep  

---

# Arquitectura General del Sistema

```
                       ┌──────────────────────┐
                       │      Navegador       │
                       │  (HTML / JS + ICE)   │
                       └──────────┬───────────┘
                                  │
                       WebSockets │ ICE RPC
                                  ▼
                ┌────────────────────────────────┐
                │      ICE Server (Java)         │
                │  - RPC Methods (login, send)   │
                │  - Callbacks a clientes        │
                └──────────┬─────────────────────┘
                           │
                           │ TCP/HTTP
                           ▼
                ┌────────────────────────────────┐
                │     Backend Java (Servidor)     │
                │  - Historial en SQLite          │
                │  - Grupos / Visibilidad         │
                │  - Persistencia completa        │
                └──────────┬─────────────────────┘
                           │
                     HTTP  │
                           ▼
           ┌────────────────────────────────────┐
           │    Proxy Node.js (solo para HTTP)   │
           │  login / historial / endpoints REST │
           └────────────────────────────────────┘
```

---

# Requisitos

| Software | Versión recomendada |
|----------|---------------------|
| **Java** | 17 o superior (se probó con Java 21) |
| **Node.js** | 18 o superior |
| **npm** | Incluido con Node |
| **ZeroC Ice** | 3.7.10 |
| **SQLite** | No requiere instalación |

---

# Estructura del Proyecto

```
proyecto_chat/
 ├─ app/
 │  ├─ src/main/java/proyecto_chat/
 │  │  ├─ server/      -> Servidor Java + ICE
 │  │  ├─ client/      -> Cliente Web
 │  │  │   ├─ index.html
 │  │  │   ├─ js/chat.js
 │  │  │   └─ ice/ (Ice.js + stubs generados)
 │  │  └─ proxy/       -> Proxy HTTP en Node.js
 │  ├─ src/main/slice/ -> Archivo chat.ice
 │  └─ storage/        -> chat_history.db
 ├─ gradlew / gradlew.bat
 └─ settings.gradle
```

---

# Modelo de Datos (SQLite)

### **messages**
| Campo | Tipo | Descripción |
|-------|------|-------------|
| id | TEXT (PK) | Identificador único |
| type | TEXT | TEXT / VOICE_NOTE |
| sender | TEXT | Usuario emisor |
| recipient | TEXT | Usuario o grupo |
| text_content | TEXT | Contenido |
| file_path | TEXT | Ruta al .wav |
| timestamp | INTEGER | Epoch ms |

### **user**
| Campo | Tipo |
|--------|------|
| username | TEXT (PK) |
| created_at | INTEGER |

### **visibility**
| Campo | Tipo |
|--------|------|
| message_id | TEXT (FK → messages.id) |
| username | TEXT (FK → user.username) |

### Relación entre tablas
- `messages` almacena cada mensaje.
- `user` registra usuarios.
- `visibility` indica qué usuario puede ver cada mensaje.
- Soporta grupos sin duplicar mensajes.

---

# Definición ICE (chat.ice)

```slice
module Chat {
    struct Msg {
        string id;
        string sender;
        string recipient;
        string text;
        long   timestamp;
    };

    sequence<Msg> MsgSeq;

    interface ClientCallback {
        void onNewMessage(Msg m);
        void onUserJoined(string username);
        void onUserLeft(string username);
    };

    interface Service {
        void login(string username, ClientCallback* cb);
        void sendText(string username, string recipient, string text);
        void createGroup(string username, string group);
        void joinGroup(string username, string group);
        MsgSeq getHistory(string recipient, int limit);
    };
};
```

---

# Flujo ICE + WebSockets (tiempo real)

1. El usuario inicia sesión por HTTP.
2. El navegador ejecuta:  
   `initIce(username)`
3. Se establece conexión ICE/WS con el servidor:
   ```
   ChatService:ws -h 127.0.0.1 -p 12000
   ```
4. El cliente registra su callback.
5. Cuando hay un mensaje nuevo:
   - servidor llama `onNewMessage(msg)`
   - navegador actualiza el chat automáticamente.

---

# Instrucciones de Ejecución

### 1. Backend Java (Servidor HTTP + Persistencia)
```bash
cd proyecto_chat
.\gradlew.bat :app:runServer
```

### 2. Servidor ICE (RPC + WebSockets)
```bash
cd proyecto_chat
.\gradlew.bat :app:runIceServer
```

### 3. Proxy HTTP (Node.js)
```bash
cd proyecto_chat/app/src/main/java/proyecto_chat/proxy
npm install
npm start
```

### 4. Cliente Web
Abrir en navegador:

```
http://localhost:3000/index.html
```

---

# Pendiente por desarrollar (otros miembros del equipo)

### Requerimiento obligatorio
✔ **Notas de voz desde navegador usando WebSockets**

### Referencia oficial:
https://github.com/AlejandroMu/compu-internet-1/tree/master/audio_rep

Se debe implementar:
- Grabación con `MediaRecorder`
- Envío binario por WebSocket
- Recepción en Java
- Reproducción en navegador
- Integración con historial SQLite

---

# Notas finales

- El proyecto combina 3 tecnologías de red: TCP/UDP, HTTP y ZeroC Ice.  
- Se migró exitosamente la comunicación web a un mecanismo RPC moderno (ICE).  
- La arquitectura permite ampliación a llamadas por WebRTC y notas de voz.  

---

**Este README documenta completamente el estado actual del sistema y su migración a ICE.**
