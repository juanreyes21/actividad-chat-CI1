const express = require('express');
const bodyParser = require('body-parser');
const net = require('net');

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


app.use(express.static('../client'));

app.listen(HTTP_PORT, () => {
  console.log(`Proxy HTTP escuchando en http://localhost:${HTTP_PORT}`);
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