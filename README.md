# Chat Cliente-Servidor con Proxy HTTP

Proyecto que implementa un sistema de chat con cliente web y cliente Java, comunicándose a través de un proxy HTTP hacia un backend Java. Soporta mensajes de texto, grupos y notas de voz, con historial persistente.

## Integrantes

* Juan José Reyes Ramos - A00405296
* Ismael Barrionuevo
* Sebastián Castillo

## Arquitectura del Sistema

```
[Cliente Web] ←→ [Proxy HTTP] ←→ [Servidor Java]
    (HTTP)         (TCP)         (Persistencia)
```

- **Cliente Web**: Interfaz HTML5/JS con soporte para texto y notas de voz
- **Proxy HTTP**: Node.js/Express, traduce HTTP a mensajes TCP
- **Servidor Java**: Gestión de mensajes y persistencia SQLite

## Configuración y Ejecución

El sistema requiere tres componentes:

### 1. Servidor Java
```powershell
# Desde la carpeta proyecto_chat
.\gradlew.bat run
```
Inicia en:
- Puerto TCP 9090 (mensajería)
- Puerto TCP 10001 (proxy)

### 2. Proxy HTTP Node.js
```powershell
# Desde proyecto_chat/app/src/main/java/proyecto_chat/proxy
npm install
node app.js
```
El proxy escucha en http://localhost:3000

### 3. Acceso al Cliente Web
1. Abre http://localhost:3000 en el navegador
2. Ingresa tu nombre de usuario
3. ¡Listo para chatear!

### 4. Cliente Java (opcional)
```powershell
# Nueva ventana, desde proyecto_chat
.\gradlew.bat runClient
```

Nota: En Linux/macOS usar `./gradlew` en lugar de `.\gradlew.bat`

## Funcionalidades Implementadas

### Cliente Web (Nuevo)
-  Interfaz web responsive
-  Envío y recepción de mensajes de texto
-  Creación y gestión de grupos
-  Grabación y envío de notas de voz
-  Reproducción de notas de voz
-  Historial de mensajes

### Cliente Java (Original)
-  Mensajes de texto 1:1 y grupos
-  Envío de archivos de audio
-  Gestión de grupos
-  Comandos disponibles:
  ```
  destinatario@mensaje         # Mensaje directo
  grupo@mensaje               # Mensaje a grupo
  creategroup@nombre          # Crear grupo
  joingroup@nombre           # Unirse a grupo
  voicenote@dest@archivo.wav # Enviar nota de voz
  ```

## Estructura del Proyecto

```
proyecto_chat/
├── app/
│   ├── src/main/java/proyecto_chat/
│   │   ├── client/
│   │   │   ├── js/           # Cliente web
│   │   │   │   ├── chat.js
│   │   │   │   └── ...
│   │   │   └── *.java       # Cliente consola
│   │   ├── proxy/           # Servidor HTTP
│   │   │   ├── app.js
│   │   │   └── package.json
│   │   └── server/          # Backend
│   └── storage/
│       ├── chat_history.db  # Base de datos
│       └── audio/          # Notas de voz
```

## API HTTP (Proxy)

### Endpoints Principales
- `POST /api/messages` - Enviar mensaje de texto
- `POST /api/groups` - Crear grupo
- `POST /api/groups/join` - Unirse a grupo
- `POST /api/voicenote` - Enviar nota de voz
- `GET /api/audio` - Obtener archivo de audio

## Persistencia

### Base de Datos (SQLite)
```sql
CREATE TABLE messages (
    id TEXT PRIMARY KEY,
    type TEXT,           -- TEXT | VOICE_NOTE
    sender TEXT,
    recipient TEXT,
    is_group INTEGER,    -- 0=directo, 1=grupo
    text_content TEXT,   -- contenido UTF-8
    file_path TEXT,      -- ruta del archivo .wav
    timestamp INTEGER    -- epoch ms
);

-- Visibilidad de mensajes
CREATE TABLE message_visibility (
    message_id TEXT,
    username TEXT,
    visible INTEGER DEFAULT 1,
    PRIMARY KEY(message_id, username)
);
```

## Decisiones de Diseño

1. **Arquitectura**
   - Proxy HTTP como puente entre web y Java
   - Traducción de peticiones HTTP a mensajes TCP
   - Mantenimiento de la lógica del servidor original

2. **Almacenamiento**
   - SQLite para persistencia de mensajes
   - Sistema de archivos para notas de voz
   - Control de visibilidad por usuario

3. **Cliente Web**
   - API MediaRecorder para grabación
   - Conversión a WAV en el navegador
   - UI responsive y amigable



