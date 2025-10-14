# Chat de Consola con Llamadas (TCP/UDP)

Proyecto Java que cumple el entregable: grupos, texto 1:1 y a grupos, notas de voz, llamadas 1:1 y a grupos, e historial (texto y audios) persistente.

## Integrantes

* Juan José Reyes Ramos - A00405296
* Ismael Barrionuevo
* Sebastián Castillo



## Cómo ejecutar

Windows PowerShell (usar .\gradlew.bat). Linux/macOS (usar ./gradlew).

1. servidor

```
.\gradlew.bat -x test runServer
```

aparece: “Servidor de chat iniciado en el puerto 9090” y “CallRelay UDP escuchando en puerto 10000”.

2. clientes (cada uno en una ventana nueva)

```
.\gradlew.bat -x test runClient
```

cuando pida el usuario, escribir por ejemplo:

```
a
```

repetir en otra ventana para b (y opcionalmente c).

Notas

* si prefieres tarea genérica run: el build ya define main en proyecto_chat.App. puedes añadir standardInput = System.in a la tarea run si necesitas leer teclado desde run.

## Comandos en el cliente

texto 1:1

```
destinatario@mensaje
```

texto a grupo

```
grupo@mensaje
```

crear/unirse a grupo

```
creategroup@nombre
joingroup@nombre
```

nota de voz a usuario o grupo (ruta a .wav local)

```
voicenote@destinatario@archivo.wav
voicenote@grupo@archivo.wav
```

llamada 1:1

```
call@usuario
callaccept@usuario
hangup@usuario
```

llamada a grupo

```
call@grupo
callaccept@grupo
hangup@grupo
```

## Flujo de prueba recomendado

texto 1:1

1. en a: `b@hola b`  → b ve el mensaje

grupo

1. en a: `creategroup@equipo`
2. en b: `joingroup@equipo` (y c igual)
3. en a: `equipo@hola equipo`  → b y c reciben

llamada 1:1

1. en a: `call@b`  → b verá el aviso para aceptar
2. en b: `callaccept@a`  → ambos conectan audio
3. colgar: `hangup@a` o `hangup@b`

llamada a grupo

1. en a: `call@equipo`
2. en b: `callaccept@equipo` y en c: `callaccept@equipo`
3. cada miembro que cuelgue: `hangup@equipo`

## Decisiones de diseño

* TCP para control, texto y notas de voz (fiabilidad/orden)
* UDP para audio en tiempo real (baja latencia)
* callId único generado por el caller en CALL_START y reutilizado por todos en CALL_ACCEPT
* historial en SQLite (storage/chat_history.db) y audios en storage/audio

## Persistencia

tabla messages (SQLite)

* id TEXT (PK)
* type TEXT [TEXT|VOICE_NOTE]
* sender TEXT
* recipient TEXT
* text_content TEXT (UTF-8)
* file_path TEXT (ruta absoluta del .wav, si aplica)
* timestamp INTEGER (epoch ms)

rutas

* base de datos: storage/chat_history.db
* audios: storage/audio/

## Estructura principal

* proyecto_chat.App (entrypoint)
* client

  * Client (CLI)
  * AudioSender (captura micrófono → UDP)
  * AudioReceiver (UDP → altavoces)
* common

  * Message (UTF-8)
* server

  * Server (TCP)
  * ClientHandler (ruteo a usuarios/grupos y señalización)
  * CallRelay (relay UDP con registro, BYE y broadcast)
  * HistoryManager (SQLite + archivos)




