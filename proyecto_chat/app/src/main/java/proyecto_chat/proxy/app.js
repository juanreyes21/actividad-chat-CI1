const express = require('express');
const bodyParser = require('body-parser');
const net = require('net');
const fs = require('fs');
const http = require('http');
const WebSocket = require('ws');
const dgram = require('dgram');

const PROXY_HOST = '127.0.0.1';
const PROXY_PORT = 10001; // donde escucha ProxyListener en Java
const HTTP_PORT = 3000;

// CallRelay UDP (mismo host y puerto que en Server.java al crear CallRelay)
const CALL_RELAY_HOST = '127.0.0.1';
const CALL_RELAY_PORT = 20000;

const udpSocket = dgram.createSocket('udp4');

const app = express();
app.use(bodyParser.json());

// helper: request sync-like via socket (envía JSON, espera 1 línea JSON respuesta)
function sendToJavaProxy(obj) {
  return new Promise((resolve, reject) => {
    const client = new net.Socket();
    let responseData = '';
    client.connect(PROXY_PORT, PROXY_HOST, () => {
      client.write(JSON.stringify(obj) + '\n');
    });
    client.on('data', (data) => {
      responseData += data.toString('utf8');
      client.destroy();
    });
    client.on('close', () => {
      try {
        const parsed = JSON.parse(responseData);
        resolve(parsed);
      } catch (e) {
        reject(e);
      }
    });
    client.on('error', (err) => {
      reject(err);
    });
  });
}

// Endpoints para cliente web:

// Crear grupo
// 'username' aquí; no se usa para crear el grupo
app.post('/api/groups', async (req, res) => {
  const { group } = req.body;
  try {
    const r = await sendToJavaProxy({ action: 'create_group', group });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});


// Unirse a grupo
app.post('/api/groups/join', async (req, res) => {
  const { username, group } = req.body;
  try {
    const r = await sendToJavaProxy({ action: 'join_group', username, group });
    res.json(r);
  } catch (err) { res.status(500).json({ status: 'error', message: err.message }); }
});

// Enviar mensaje de texto
app.post('/api/messages', async (req, res) => {
  const { username, recipient, text } = req.body;
  try {
    const r = await sendToJavaProxy({ action: 'send_text', username, recipient, text });
    res.json(r);
  } catch (err) { res.status(500).json({ status: 'error', message: err.message }); }
});

app.delete('/api/messages', async (req, res) => {
  const { username, recipient } = req.body;
  const r = await sendToJavaProxy({ action: 'delete_chat', username, recipient });
  res.json(r);
});


// Obtener historial
app.get('/api/history/:recipient', async (req, res) => {
    const recipient = req.params.recipient;
    const username = req.query.username;
    
    if (!username) return res.status(400).json({ status: 'error', message: 'username required' });

    try {
        const r = await sendToJavaProxy({ action: 'fetch_history', username, recipient });
        res.json(r);
    } catch (err) {
        res.status(500).json({ status: 'error', message: err.message });
    }
});


app.get('/api/groups/:username', async (req,res)=>{
  const username = req.params.username;
  try {
    const r = await sendToJavaProxy({ action:'list_groups', username });
    res.json(r);
  } catch(e){res.json({status:'error'});}
});

app.use(express.static('../client'));

const server = http.createServer(app);

const wss = new WebSocket.Server({ server, path: '/voice' });

wss.on('connection', (ws) => {
  let meta = null;

  ws.on('message', async (data, isBinary) => {
    try {
      if (!meta) {
        // Primer mensaje: metadatos en JSON (texto)
        const text = isBinary ? data.toString('utf8') : data.toString();
        meta = JSON.parse(text);
        return;
      }

      // Segundo mensaje: binario de audio
      const buffer = isBinary ? data : Buffer.from(data);
      const audioBase64 = buffer.toString('base64');

      const { username, recipient, filename } = meta;
      if (!username || !recipient || !filename) {
        ws.send(JSON.stringify({ status: 'error', message: 'invalid metadata' }));
        return;
      }

      const resp = await sendToJavaProxy({
        action: 'send_voice',
        username,
        recipient,
        filename,
        audio_base64: audioBase64
      });

      ws.send(JSON.stringify(resp));
      ws.close();
    } catch (err) {
      ws.send(JSON.stringify({ status: 'error', message: err.message }));
      ws.close();
    }
  });
});

// WebSocket para audio de llamadas en tiempo real
// El cliente debe conectarse con query ?callId=... (y opcionalmente ?user=...)
const wssCall = new WebSocket.Server({ server, path: '/call-audio' });

wssCall.on('connection', (ws, req) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host}`);
    const callId = url.searchParams.get('callId');
    // user no es estrictamente necesario para CallRelay, pero puede ser útil para logs
    const user = url.searchParams.get('user') || 'unknown';

    if (!callId) {
      ws.close(1008, 'callId required');
      return;
    }

    console.log(`[WS CALL] Nueva conexión de audio: user=${user}, callId=${callId}`);

    const callIdBuf = Buffer.from(callId, 'utf8');
    const header = Buffer.alloc(4);
    header.writeUInt32BE(callIdBuf.length, 0);

    // Registro inicial en CallRelay (payload vacío) para que conozca este endpoint
    const registerPacket = Buffer.concat([header, callIdBuf]);
    udpSocket.send(registerPacket, CALL_RELAY_PORT, CALL_RELAY_HOST);

    ws.on('message', (data, isBinary) => {
      // data es un frame de audio (por ejemplo PCM o chunks codificados)
      const payload = isBinary ? data : Buffer.from(data);
      const packet = Buffer.concat([header, callIdBuf, payload]);

      udpSocket.send(packet, CALL_RELAY_PORT, CALL_RELAY_HOST, (err) => {
        if (err) {
          console.error('[WS CALL] Error enviando UDP a CallRelay:', err);
        }
      });
    });

    ws.on('close', () => {
      console.log(`[WS CALL] Cierre de conexión de audio: user=${user}, callId=${callId}`);
      // Enviar BYE (payload = 0x01) para desregistrar en CallRelay
      const byePayload = Buffer.from([0x01]);
      const byePacket = Buffer.concat([header, callIdBuf, byePayload]);
      udpSocket.send(byePacket, CALL_RELAY_PORT, CALL_RELAY_HOST);
    });

    ws.on('error', (err) => {
      console.error('[WS CALL] Error en WebSocket de llamada:', err);
    });
  } catch (e) {
    console.error('[WS CALL] Error al manejar conexión:', e);
    ws.close(1011, 'internal error');
  }
});

server.listen(HTTP_PORT, () => {
  console.log(`Proxy HTTP + WS escuchando en http://localhost:${HTTP_PORT}`);
});

// Login (auto-registro)
app.post('/api/login', async (req, res) => {
  const { username } = req.body;
  try {
    const r = await sendToJavaProxy({ action: 'login', username });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status:'error', message: err.message });
  }
});

// Listar usuarios reales
app.get('/api/users', async (req, res) => {
  try {
    const r = await sendToJavaProxy({ action: 'list_users' });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status:'error', message: err.message });
  }
});

app.get('/api/audio/:id', async (req, res) => {
  const id = req.params.id;
  try {
    const r = await sendToJavaProxy({ action: 'get_audio_path', id });
    if (!r || r.status !== 'ok' || !r.file_path) {
      return res.status(404).json({ status: 'error', message: 'audio not found' });
    }
    const filePath = r.file_path;
    const mimeType = r.mime_type || 'audio/webm';

    if (!fs.existsSync(filePath)) {
      return res.status(404).json({ status: 'error', message: 'file not found on disk' });
    }

    res.setHeader('Content-Type', mimeType);
    const stream = fs.createReadStream(filePath);
    stream.on('error', () => {
      res.status(500).end();
    });
    stream.pipe(res);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});