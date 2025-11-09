# Sistema de Chat CI1 (TCP/UDP + Proxy HTTP + Cliente Web)

## Integrantes
- Sebastian Castillo - A00170732
- Juan José Reyes - A00405296
- Ismael Barrionuevo - A00403480

## Descripción general

Este sistema implementa un chat funcional distribuido en tres componentes:

1. Cliente Web (HTML, CSS, JavaScript)
2. Proxy HTTP desarrollado en Node.js (traduce peticiones HTTP a mensajes TCP)
3. Servidor backend en Java, con persistencia del historial en SQLite

El servidor maneja conexiones de múltiples clientes, almacena el historial de mensajes en SQLite y reenvía mensajes entre los usuarios conectados.

La base de datos se genera automáticamente, no requiere configuración manual.

---

## Arquitectura de comunicación

```
Cliente Web (HTTP/JSON)
        |
        v
Proxy HTTP (Node.js/Express)  ---- TCP ---->  ProxyListener (Java) -> Servidor Java
                                                          |
                                                          v
                                                      SQLite (historial)
```

Flujo del sistema:

1. El Cliente Web envía solicitudes HTTP al Proxy HTTP.
2. El Proxy convierte esas solicitudes en mensajes TCP y las envía al ProxyListener del Servidor Java.
3. El Servidor Java procesa la solicitud, guarda el historial en SQLite y distribuye mensajes a los clientes conectados.
4. El Proxy HTTP envía la respuesta nuevamente al Cliente Web.

El Cliente Java (CLI) se conecta directamente al Servidor Java mediante sockets TCP, sin pasar por el proxy.

---

## Requisitos

| Software | Versión recomendada |
|----------|---------------------|
| JDK      | 17 o superior |
| Node.js  | 18 o superior (incluye npm) |
| Gradle   | No es necesario instalar, el proyecto incluye `gradlew` |

SQLite no requiere instalación previa; la base de datos se crea automáticamente.

---

## Estructura del proyecto

```
proyecto_chat/
 ├─ app/
 │  ├─ src/main/java/proyecto_chat/
 │  │  ├─ server/      -> Backend Java (Server.java, ClientHandler.java, ProxyListener.java, HistoryManager.java, CallRelay.java)
 │  │  ├─ proxy/       -> Proxy HTTP en Node.js (app.js, package.json)
 │  │  └─ client/      -> Cliente Web (index.html, style.css, js/chat.js)
 │  └─ storage/        -> chat_history.db (SQLite)
 ├─ gradlew / gradlew.bat
 └─ settings.gradle
```

---

## Instrucciones para ejecutar

### 1) Iniciar el servidor Java (backend)

Ubicarse en la carpeta principal del proyecto:

```
cd proyecto_chat
```

En Windows (PowerShell):

```
.\gradlew.bat :app:runServer
```

En Linux/Mac:

```
./gradlew :app:runServer
```

---

### 2) Ejecutar el Proxy HTTP (Node.js)

En otra terminal:

```
cd proyecto_chat/app/src/main/java/proyecto_chat/proxy
npm install
npm start
```

Salida esperada:

```
Proxy HTTP escuchando en http://localhost:3000
```

---

### 3) Abrir el Cliente Web

Abrir en el navegador:

```
http://localhost:3000/index.html
```

---

## Cliente Web (UI actual)

En esta versión del proyecto, el usuario interactúa mediante la interfaz web


La comunicación desde el navegador se realiza mediante solicitudes HTTP al Proxy (Node.js), y este las traduce a mensajes TCP hacia el servidor Java.

**Las acciones (enviar mensajes, crear grupos, consultar historial, etc.) se gestionan desde la interfaz web.  
No es necesario usar comandos en consola.**


---

## Persistencia en SQLite

Ubicación:
```
proyecto_chat/app/storage/chat_history.db
```

Audios:
```
proyecto_chat/app/storage/audio/
```


### Tablas del sistema

#### Tabla: `messages`
Guarda el historial de mensajes (texto y notas de voz).

| Campo        | Tipo      | Descripción |
|--------------|-----------|-------------|
| id           | TEXT (PK) | Identificador único del mensaje |
| type         | TEXT      | TEXT o VOICE_NOTE |
| sender       | TEXT      | Usuario que envía |
| recipient    | TEXT      | Usuario o grupo destino |
| text_content | TEXT      | Contenido del mensaje si aplica |
| file_path    | TEXT      | Ruta al archivo .wav (notas de voz) |
| timestamp    | INTEGER   | Epoch ms |

---

#### Tabla: `user`
Representa un usuario registrado en el sistema.

| Campo        | Tipo      | Descripción |
|--------------|-----------|-------------|
| username     | TEXT (PK) | Identificador del usuario |
| created_at   | INTEGER   | Fecha de creación (epoch ms) |

---

#### Tabla: `visibility`
Define la relación entre mensajes y usuarios (permite el historial individual y por grupo).

| Campo        | Tipo      | Descripción |
|--------------|-----------|-------------|
| message_id   | TEXT (FK → messages.id) | Mensaje visible para el usuario |
| username     | TEXT (FK → user.username) | Usuario que puede ver el mensaje |

---

### Relación entre las tablas

La tabla `messages` almacena cada mensaje enviado en el sistema (texto o nota de voz). La tabla `user` registra los usuarios existentes. La tabla `visibility` actúa como tabla de relación entre ambas, indicando qué usuarios pueden ver cada mensaje. Esto permite soportar:

- Mensajes 1:1 (visibility tiene 2 entradas: emisor y receptor)
- Mensajes a grupos (visibility genera una entrada por cada miembro del grupo)
- Historial persistente y filtrado por usuario

De esta manera, el sistema no duplica mensajes en la base de datos, sino que los vincula a múltiples usuarios según corresponda.


---

## Decisiones de diseño

- TCP para mensajes y notas de voz (fiabilidad)
- UDP para llamadas (baja latencia)
- El proxy solo traduce HTTP → TCP. No tiene lógica de negocio.
- El cliente CLI se conecta directamente al servidor sin pasar por el proxy.
