# Sistema de Chat CI1 (TCP/UDP + Proxy HTTP + Cliente Web)

## Integrantes
- Sebastian Castillo
- Juan José Reyes
- Ismael Barrionuevo

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

## Comandos del cliente CLI (mensajería y llamadas)

Texto a usuario:
```
destinatario@mensaje
```

Texto a grupo:
```
grupo@mensaje
```

Crear grupo:
```
creategroup@nombre
```

Unirse a grupo:
```
joingroup@nombre
```

Nota de voz (ruta a archivo .wav):
```
voicenote@destinatario@archivo.wav
```

Llamada 1:1:
```
call@usuario
callaccept@usuario
hangup@usuario
```

Llamada grupal:
```
call@grupo
callaccept@grupo
hangup@grupo
```

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

Tabla principal `messages`:

| Campo | Descripción |
|--------|------------|
| id | identificador del mensaje |
| type | TEXT o VOICE_NOTE |
| sender | emisor |
| recipient | usuario o grupo |
| text_content | mensaje (si aplica) |
| file_path | ruta del audio (si aplica) |
| timestamp | epoch ms |

---

## Decisiones de diseño

- TCP para mensajes y notas de voz (fiabilidad)
- UDP para llamadas (baja latencia)
- El proxy solo traduce HTTP → TCP. No tiene lógica de negocio.
- El cliente CLI se conecta directamente al servidor sin pasar por el proxy.
