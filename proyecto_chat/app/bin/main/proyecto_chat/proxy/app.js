const express = require('express');
const bodyParser = require('body-parser');
const net = require('net');
const path = require('path');
const { WebSocketServer } = require('ws');

const PROXY_HOST = '127.0.0.1';
const PROXY_PORT = 10001; // donde escucha ProxyListener en Java
const HTTP_PORT = 3000;

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

// --- Señalización básica de llamadas para cliente web ---
app.post('/api/call/start', async (req, res) => {
  const { caller, callee } = req.body;
  if (!caller || !callee) {
    return res.status(400).json({ status: 'error', message: 'caller and callee required' });
  }
  try {
    const r = await sendToJavaProxy({ action: 'call_start', caller, callee });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});

app.get('/api/call/status/:username', async (req, res) => {
  const username = req.params.username;
  if (!username) {
    return res.status(400).json({ status: 'error', message: 'username required' });
  }
  try {
    const r = await sendToJavaProxy({ action: 'call_status', username });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});

// --- Señalización WebRTC ---
app.post('/api/call/offer', async (req, res) => {
  const { to, payload } = req.body;
  if (!to || !payload) {
    return res.status(400).json({ status: 'error', message: 'to and payload required' });
  }
  try {
    const r = await sendToJavaProxy({ action: 'call_offer', to, payload });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});

app.post('/api/call/answer', async (req, res) => {
  const { to, payload } = req.body;
  if (!to || !payload) {
    return res.status(400).json({ status: 'error', message: 'to and payload required' });
  }
  try {
    const r = await sendToJavaProxy({ action: 'call_answer', to, payload });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});

app.post('/api/call/candidate', async (req, res) => {
  const { to, candidate } = req.body;
  if (!to || !candidate) {
    return res.status(400).json({ status: 'error', message: 'to and candidate required' });
  }
  try {
    const r = await sendToJavaProxy({ action: 'call_candidate', to, candidate: JSON.stringify(candidate) });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});

app.get('/api/call/offer/:username', async (req, res) => {
  const username = req.params.username;
  try {
    const r = await sendToJavaProxy({ action: 'call_poll_offer', username });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});

app.get('/api/call/answer/:username', async (req, res) => {
  const username = req.params.username;
  try {
    const r = await sendToJavaProxy({ action: 'call_poll_answer', username });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});

app.get('/api/call/candidates/:username', async (req, res) => {
  const username = req.params.username;
  try {
    const r = await sendToJavaProxy({ action: 'call_poll_candidates', username });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});

app.post('/api/call/end', async (req, res) => {
  const { to } = req.body;
  if (!to) {
    return res.status(400).json({ status: 'error', message: 'to required' });
  }
  try {
    const r = await sendToJavaProxy({ action: 'call_end_notify', to });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});

app.get('/api/call/end/:username', async (req, res) => {
  const username = req.params.username;
  try {
    const r = await sendToJavaProxy({ action: 'call_poll_end', username });
    res.json(r);
  } catch (err) {
    res.status(500).json({ status: 'error', message: err.message });
  }
});


// Endpoint para servir archivos de audio por id de mensaje
app.get('/api/audio/:id', async (req, res) => {
  const id = req.params.id;
  try {
    const r = await sendToJavaProxy({ action: 'get_audio_path', id });
    if (r.status !== 'ok' || !r.file_path) {
      return res.status(404).json({ status: 'error', message: 'audio not found' });
    }
    const filePath = r.file_path;
    return res.sendFile(path.resolve(filePath));
  } catch (err) {
    console.error('Error en /api/audio:', err);
    return res.status(500).json({ status: 'error', message: err.message });
  }
});

app.use(express.static('../client'));

const server = app.listen(HTTP_PORT, () => {
  console.log(`Proxy HTTP escuchando en http://localhost:${HTTP_PORT}`);
});

// WebSocket para notas de voz
const wss = new WebSocketServer({ server, path: '/audio' });

wss.on('connection', (ws) => {
  let meta = null;
  let chunks = [];

  ws.on('message', async (data, isBinary) => {
    try {
      if (!meta) {
        // Primer mensaje: metadatos en JSON
        const txt = isBinary ? data.toString('utf8') : data.toString();
        meta = JSON.parse(txt);
        return;
      }

      // Si llega un JSON con done:true, procesamos lo acumulado
      if (!isBinary) {
        const txt = data.toString();
        try {
          const obj = JSON.parse(txt);
          if (obj && obj.done) {
            const buffer = Buffer.concat(chunks);
            const b64 = buffer.toString('base64');
            const { sender, recipient, fileName = 'note.webm' } = meta;
            await sendToJavaProxy({
              action: 'send_voice',
              username: sender,
              recipient,
              fileName,
              dataBase64: b64
            });
            ws.close();
            return;
          }
        } catch (_) {
          // no es JSON de control, lo ignoramos
        }
      }

      // Chunks binarios de audio
      if (isBinary || data instanceof Buffer) {
        chunks.push(Buffer.from(data));
      }
    } catch (err) {
      console.error('Error en WebSocket /audio:', err);
      ws.close();
    }
  });

  ws.on('close', () => {
    meta = null;
    chunks = [];
  });
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