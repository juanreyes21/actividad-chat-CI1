let username = null;
let activeChat = null;

let mediaRecorder = null;
let audioChunks = [];
let audioSocket = null;

let currentPeerConnection = null;
let localStream = null;
let currentCallPeer = null;

// =========================
// Integración ICE (WebSocket)
// =========================
let iceCommunicator = null;
let iceService = null;
let iceAdapter = null;

async function initIce(username) {
    if (iceService) {
        // Ya está inicializado
        return;
    }

async function pollCallEnd() {
    if (!username || !currentPeerConnection) return;
    try {
        const r = await api('/api/call/end/' + encodeURIComponent(username));
        if (r.status === 'ok' && r.ended) {
            endCurrentCall();
        }
    } catch (e) {
        console.error('Error comprobando fin de llamada:', e);
    }
}

    try {
        // Inicializar Ice en el navegador
        const initData = new Ice.InitializationData();
        iceCommunicator = Ice.initialize([], initData);

        // Proxy al servicio en el servidor ICE
        // Debe coincidir con el endpoint de IceServer.java:
        // "ws -h 127.0.0.1 -p 12000"
        const base = iceCommunicator.stringToProxy("ChatService:ws -h 127.0.0.1 -p 12000");
        iceService = await Chat.ServicePrx.checkedCast(base);

        if (!iceService) {
            console.error("No se pudo hacer checkedCast a Chat.ServicePrx");
            return;
        }

        // Adapter para callbacks (bidireccional)
        iceAdapter = iceCommunicator.createObjectAdapter("");

        // Implementación del callback que el servidor va a invocar
        class ClientCallbackI extends Chat.ClientCallback {
            async onNewMessage(msg, current) {
                console.log("[ICE] onNewMessage", msg);

                if (!activeChat) return;

                const chatLower = activeChat.toLowerCase();
                const sender = (msg.sender || "").toLowerCase();
                const recipient = (msg.recipient || "").toLowerCase();

                // Si el mensaje pertenece al chat activo, recargamos el historial
                if (chatLower === sender || chatLower === recipient) {
                    try {
                        await loadHistoryIncremental(activeChat, true);
                    } catch (e) {
                        console.error("[ICE] Error refrescando historial:", e);
                    }
                }
            }

            async onUserJoined(user, current) {
                console.log("[ICE] onUserJoined:", user);
                // Si quieres, podrías recargar la sidebar:
                // await loadSidebar();
            }

            async onUserLeft(user, current) {
                console.log("[ICE] onUserLeft:", user);
            }
        }

        const cb = new ClientCallbackI();
        // Registramos el callback local y creamos un proxy hacia él
        const cbPrx = await iceAdapter.addWithUUID(cb);

        // Asociar el adapter a la conexión para callbacks bidireccionales
        const conn = await iceService.ice_getConnection();
        conn.setAdapter(iceAdapter);

        // Hacemos login en el servicio ICE, pasando el callback
        await iceService.login(username, cbPrx);

        console.log("[ICE] Cliente conectado a ICE como", username);
    } catch (e) {
        console.error("[ICE] Error inicializando ICE:", e);
    }
}

async function startWebRtcAsCaller() {
    try {
        if (!currentCallPeer) return;

        const pc = new RTCPeerConnection({
            iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
        });
        currentPeerConnection = pc;

        pc.onicecandidate = async (event) => {
            if (event.candidate) {
                try {
                    await api('/api/call/candidate', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ to: currentCallPeer, candidate: event.candidate })
                    });
                } catch (e) {
                    console.error('Error enviando candidate:', e);
                }
            }
        };

function endCurrentCall() {
    currentCallPeer = null;
    try {
        if (currentPeerConnection) {
            currentPeerConnection.close();
        }
    } catch (e) {
        console.error('Error cerrando RTCPeerConnection:', e);
    }
    currentPeerConnection = null;

    if (localStream) {
        try {
            localStream.getTracks().forEach(t => t.stop());
        } catch (e) {
            console.error('Error parando localStream:', e);
        }
    }
    localStream = null;

    const remoteAudio = document.getElementById('remoteAudio');
    if (remoteAudio) {
        remoteAudio.srcObject = null;
    }

    const hangBtn = document.getElementById('hangupBtn');
    if (hangBtn) hangBtn.disabled = true;
}

document.getElementById('hangupBtn').onclick = () => {
    const peer = currentCallPeer;
    endCurrentCall();
    if (peer) {
        api('/api/call/end', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ to: peer })
        }).catch(e => console.error('Error notificando fin de llamada:', e));
    }
};

        pc.ontrack = (event) => {
            const remoteAudio = document.getElementById('remoteAudio') || createRemoteAudioElement();
            remoteAudio.srcObject = event.streams[0];
        };

        localStream = await navigator.mediaDevices.getUserMedia({ audio: true });
        localStream.getTracks().forEach(track => pc.addTrack(track, localStream));

        const offer = await pc.createOffer();
        await pc.setLocalDescription(offer);

        const payload = JSON.stringify({
            type: 'offer',
            sdp: offer.sdp,
            from: username,
            to: currentCallPeer
        });

        await api('/api/call/offer', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ to: currentCallPeer, payload })
        });
    } catch (e) {
        console.error('Error iniciando WebRTC (caller):', e);
    }
}

async function startWebRtcAsCallee() {
    try {
        if (!currentCallPeer) return;

        const pc = new RTCPeerConnection({
            iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
        });
        currentPeerConnection = pc;

        pc.onicecandidate = async (event) => {
            if (event.candidate) {
                try {
                    await api('/api/call/candidate', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/json' },
                        body: JSON.stringify({ to: currentCallPeer, candidate: event.candidate })
                    });
                } catch (e) {
                    console.error('Error enviando candidate:', e);
                }
            }
        };

        pc.ontrack = (event) => {
            const remoteAudio = document.getElementById('remoteAudio') || createRemoteAudioElement();
            remoteAudio.srcObject = event.streams[0];
        };

        localStream = await navigator.mediaDevices.getUserMedia({ audio: true });
        localStream.getTracks().forEach(track => pc.addTrack(track, localStream));

        // Obtener offer pendiente para este usuario
        const r = await api('/api/call/offer/' + encodeURIComponent(username));
        if (r.status !== 'ok' || !r.has) {
            console.warn('No hay offer pendiente para este usuario');
            return;
        }
        const payload = JSON.parse(r.payload);
        const remoteDesc = new RTCSessionDescription({ type: 'offer', sdp: payload.sdp });
        await pc.setRemoteDescription(remoteDesc);

        const answer = await pc.createAnswer();
        await pc.setLocalDescription(answer);

        const answerPayload = JSON.stringify({
            type: 'answer',
            sdp: answer.sdp,
            from: username,
            to: currentCallPeer
        });

        await api('/api/call/answer', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ to: currentCallPeer, payload: answerPayload })
        });
    } catch (e) {
        console.error('Error iniciando WebRTC (callee):', e);
    }
}

async function pollWebRtcAnswerAndCandidates() {
    if (!username || !currentCallPeer || !currentPeerConnection) return;
    try {
        // El caller busca answer
        const rAns = await api('/api/call/answer/' + encodeURIComponent(username));
        if (rAns.status === 'ok' && rAns.has) {
            const payload = JSON.parse(rAns.payload);
            const remoteDesc = new RTCSessionDescription({ type: 'answer', sdp: payload.sdp });
            if (!currentPeerConnection.currentRemoteDescription) {
                await currentPeerConnection.setRemoteDescription(remoteDesc);
            }
        }

        // Ambos obtienen candidates
        const rCand = await api('/api/call/candidates/' + encodeURIComponent(username));
        if (rCand.status === 'ok' && Array.isArray(rCand.candidates)) {
            for (const c of rCand.candidates) {
                try {
                    const obj = JSON.parse(c);
                    // Evitar InvalidStateError: solo aplicar candidates si ya hay remoteDescription
                    if (!currentPeerConnection.remoteDescription || !currentPeerConnection.remoteDescription.type) {
                        continue;
                    }
                    await currentPeerConnection.addIceCandidate(new RTCIceCandidate(obj));
                } catch (e) {
                    console.error('Error aplicando candidate:', e);
                }
            }
        }
    } catch (e) {
        console.error('Error en pollWebRtcAnswerAndCandidates:', e);
    }
}


function createRemoteAudioElement() {
    const audio = document.createElement('audio');
    audio.id = 'remoteAudio';
    audio.autoplay = true;
    audio.style.display = 'none';
    document.body.appendChild(audio);
    return audio;
}
// Estado para refresco/scroll incremental
const RENDERED_KEYS = new Set();   // evita duplicados
let autoRefresh = true;            // solo refresca si el usuario está al fondo
const BOTTOM_EPS = 20;             // umbral px para considerar "el fondo del chat”
let pendingWhilePaused = 0;        // cuenta nuevos mientras el usuario scrollea hacia arriba

// Utilidades
function capitalize(str) {
    if (!str) return '';
    return str.charAt(0).toUpperCase() + str.slice(1).toLowerCase();
}
function escapeHtml(s = '') {
    return s
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
const api = (path, opts) => fetch(path, opts).then(r => r.json());

// Login/App
function showLogin() {
    document.getElementById("loginScreen").style.display = "flex";
    document.getElementById("chatApp").style.display = "none";
}
function showApp() {
    document.getElementById("loginScreen").style.display = "none";
    document.getElementById("chatApp").style.display = "flex";
    loadSidebar();
}

document.getElementById("loginBtn").onclick = async () => {
    const u = document.getElementById("loginUser").value.trim().toLowerCase();
    if (!u) return alert("Ingresa un nombre.");

    const r = await api('/api/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: u })
    });

    if (r.status === "ok") {
        username = u;
        localStorage.username = u;

        // === Nuevo: conectar también a ICE ===
        await initIce(u);

        showApp();
        document.getElementById("welcomeUser").textContent = capitalize(username);
    } else {
        alert("Error al iniciar sesión.");
    }
};

document.getElementById("logoutBtn").onclick = () => {
    localStorage.removeItem("username");
    username = null;
    activeChat = null;
    showLogin();
};

if (localStorage.username) {
    username = localStorage.username;
    showApp();
    document.getElementById("welcomeUser").textContent = capitalize(username);
} else {
    showLogin();
}

// Sidebar
async function loadSidebar() {
    const userList = document.getElementById("userList");
    const groupList = document.getElementById("groupList");
    userList.innerHTML = "";
    groupList.innerHTML = "";

    const u = await api('/api/users');
    if (u.status === "ok") {
        u.users.filter(x => x !== username).forEach(x => {
            const li = document.createElement("li");
            li.textContent = capitalize(x);
            li.onclick = () => openChat(x);
            userList.appendChild(li);
        });
    }

    const g = await api('/api/groups/' + username);
    if (g.status === "ok") {
        g.groups.forEach(group => {
            const li = document.createElement("li");
            li.textContent = capitalize(group);
            li.onclick = () => openChat(group);
            groupList.appendChild(li);
        });
    }
}

// Abrir chat
async function openChat(chat) {
    activeChat = chat;
    document.getElementById("activeChatName").textContent = capitalize(chat);
    document.getElementById("text").disabled = false;
    document.getElementById("send").disabled = false;
    document.getElementById("recordVoice").disabled = false;
    document.getElementById("callBtn").disabled = false;
    document.getElementById("hangupBtn").disabled = true;

    const box = document.getElementById('messages');
    box.innerHTML = '';
    RENDERED_KEYS.clear();
    pendingWhilePaused = 0;
    autoRefresh = true; // al abrir, habilita autorefresco

    await loadHistoryIncremental(chat, true); // fuerza scroll al fondo
}

// Borrar chat
document.getElementById("deleteChatBtn").onclick = async () => {
    if (!activeChat) return alert("Selecciona un chat primero.");
    const confirmDelete = confirm(`¿Eliminar el chat con ${capitalize(activeChat)}? Esto borra el historial.`);
    if (!confirmDelete) return;

    await api('/api/messages', {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, recipient: activeChat })
    });

    activeChat = null;
    document.getElementById("activeChatName").textContent = "Selecciona un chat";
    document.getElementById("messages").innerHTML = "";
    document.getElementById("text").disabled = true;
    document.getElementById("send").disabled = true;
    document.getElementById("recordVoice").disabled = true;
    document.getElementById("callBtn").disabled = true;
    document.getElementById("hangupBtn").disabled = true;
    RENDERED_KEYS.clear();
};

// Cargar historial incremental (sin vaciar)
async function loadHistoryIncremental(recipient, forceScrollBottom = false) {
    const box = document.getElementById('messages');
    const atBottomBefore = box.scrollTop + box.clientHeight >= box.scrollHeight - BOTTOM_EPS;

    const r = await api(
        '/api/history/' + encodeURIComponent(recipient) + '?username=' + encodeURIComponent(username)
    );
    if (r.status !== 'ok' || !Array.isArray(r.messages)) return;

    let appended = 0;

    r.messages.forEach(m => {
        const key = `${m.id || ''}|${m.timestamp}|${(m.sender || '').toLowerCase()}|${m.text_content || ''}|${m.type || ''}`;
        if (RENDERED_KEYS.has(key)) return;

        RENDERED_KEYS.add(key);
        appended++;

        const isMe = (m.sender || '').toLowerCase() === (username || '').toLowerCase();
        const d = document.createElement('div');
        d.className = isMe ? 'msg me' : 'msg other';

        const time = new Date(m.timestamp).toLocaleTimeString([], {
            day: '2-digit',
            month: '2-digit',
            hour: '2-digit',
            minute: '2-digit'
        });

        if ((m.type || '').toUpperCase() === 'VOICE_NOTE') {
            d.innerHTML = `
                <div class="meta">${capitalize(m.sender)} • ${time}</div>
                <audio controls src="/api/audio/${encodeURIComponent(m.id)}"></audio>
            `;
        } else {
            d.innerHTML = `
                <div class="meta">${capitalize(m.sender)} • ${time}</div>
                <div class="text">${escapeHtml(m.text_content || '(sin contenido)')}</div>
            `;
        }
        box.appendChild(d);
    });

    if (forceScrollBottom || atBottomBefore) {
        box.scrollTop = box.scrollHeight;
    } else if (appended > 0) {
        pendingWhilePaused += appended;
        showNewMessagesHint(pendingWhilePaused);
    }
}

// Indicador “Nuevos mensajes”
function showNewMessagesHint(count) {
    let hint = document.getElementById('newMsgHint');
    if (!hint) {
        hint = document.createElement('button');
        hint.id = 'newMsgHint';
        hint.className = 'new-msg-hint';
        hint.style.position = 'absolute';
        hint.style.bottom = '72px';
        hint.style.right = '16px';
        hint.style.padding = '8px 12px';
        hint.style.borderRadius = '16px';
        hint.style.border = 'none';
        hint.style.cursor = 'pointer';
        hint.style.boxShadow = '0 2px 8px rgba(0,0,0,0.2)';
        hint.onclick = () => {
            const box = document.getElementById('messages');
            box.scrollTop = box.scrollHeight;
            pendingWhilePaused = 0;
            hint.remove();
        };
        document.querySelector('.chat-area').appendChild(hint);
    }
    hint.textContent = `Ver ${count} nuevo(s)`;
}

// scroll del contenedor
(function bindScrollWatcher() {
    const box = document.getElementById('messages');
    box.addEventListener('scroll', () => {
        const atBottom = box.scrollTop + box.clientHeight >= box.scrollHeight - BOTTOM_EPS;
        autoRefresh = atBottom; // si el usuario sube, se pausa refresh
        if (atBottom) {
            const hint = document.getElementById('newMsgHint');
            if (hint) hint.remove();
            pendingWhilePaused = 0;
        }
    }, { passive: true });
})();

// Enviar mensaje
document.getElementById('send').onclick = async () => {
    if (!activeChat) return;
    const text = document.getElementById("text").value;
    if (!text.trim()) return;

    // 1) Persistencia vía HTTP (backend anterior)
    await api('/api/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, recipient: activeChat, text })
    });

    // 2) Notificación en tiempo real vía ICE (broadcast)
    if (iceService) {
        try {
            await iceService.sendText(username, activeChat, text);
        } catch (e) {
            console.error("[ICE] Error enviando sendText:", e);
        }
    }

    document.getElementById("text").value = '';
    await loadHistoryIncremental(activeChat, true); // tu comportamiento actual
};


document.getElementById('text').onkeypress = (e) => {
    if (e.key === 'Enter') document.getElementById('send').click();
};

// --- Señalización básica de llamadas ---
document.getElementById('callBtn').onclick = async () => {
    if (!activeChat) {
        alert('Selecciona un chat primero.');
        return;
    }
    if (!username) {
        alert('Inicia sesión primero.');
        return;
    }

    try {
        const r = await api('/api/call/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ caller: username, callee: activeChat })
        });
        if (r.status === 'ok') {
            alert(`Llamando a ${capitalize(activeChat)}...`);
            // Iniciar flujo WebRTC como caller
            currentCallPeer = activeChat;
            await startWebRtcAsCaller();
            document.getElementById('hangupBtn').disabled = false;
        } else {
            alert('No se pudo iniciar la llamada.');
        }
    } catch (e) {
        console.error('Error iniciando llamada:', e);
        alert('Error iniciando llamada.');
    }
};

async function checkIncomingCall() {
    if (!username) return;
    try {
        const r = await api('/api/call/status/' + encodeURIComponent(username));
        if (r.status === 'ok' && r.incoming && r.from) {
            const fromName = capitalize(r.from);
            const accept = window.confirm(`${fromName} te está llamando. ¿Aceptar?`);
            if (accept) {
                currentCallPeer = r.from;
                await startWebRtcAsCallee();
                document.getElementById('hangupBtn').disabled = false;
            } else {
                // Por ahora no notificamos rechazo explícito
            }
        }
    } catch (e) {
        console.error('Error comprobando llamadas entrantes:', e);
    }
}

function getAudioWebSocket() {
    if (audioSocket && audioSocket.readyState === WebSocket.OPEN) {
        return audioSocket;
    }
    audioSocket = new WebSocket('ws://localhost:3000/audio');
    return audioSocket;
}

document.getElementById('recordVoice').onclick = async () => {
    if (!activeChat) {
        alert('Selecciona un chat primero.');
        return;
    }

    const btn = document.getElementById('recordVoice');

    if (!mediaRecorder || mediaRecorder.state === 'inactive') {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
            mediaRecorder = new MediaRecorder(stream);
        } catch (err) {
            console.error('Error accediendo al micrófono:', err);
            alert('No se pudo acceder al micrófono.');
            return;
        }

        audioChunks = [];
        const ws = getAudioWebSocket();

        ws.onopen = () => {
            ws.send(JSON.stringify({
                sender: username,
                recipient: activeChat,
                fileName: 'note.webm'
            }));
        };

        mediaRecorder.ondataavailable = (e) => {
            if (e.data && e.data.size > 0) {
                audioChunks.push(e.data);
            }
        };

        mediaRecorder.onstop = async () => {
            try {
                const blob = new Blob(audioChunks, { type: 'audio/webm' });
                const buffer = await blob.arrayBuffer();
                const ws2 = getAudioWebSocket();

                ws2.send(new Uint8Array(buffer));
                ws2.send(JSON.stringify({ done: true }));
            } catch (err) {
                console.error('Error enviando nota de voz:', err);
            }
        };

        mediaRecorder.start();
        btn.textContent = '■';
    } else if (mediaRecorder.state === 'recording') {
        mediaRecorder.stop();
        btn.textContent = '🎙';
    }
};

// Crear/Unirse a grupo
document.getElementById("createGroupBtn").onclick = async () => {
    const name = prompt("Nombre del grupo:");
    if (!name) return;
    const normalizedName = name.toLowerCase();
    await api('/api/groups', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ group: normalizedName })
    });
    await api('/api/groups/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, group: normalizedName })
    });
    loadSidebar();
};

document.getElementById("joinGroupBtn").onclick = async () => {
    const name = prompt("Nombre del grupo al que deseas unirte:");
    if (!name) return;
    const normalizedName = name.toLowerCase();
    await api('/api/groups/join', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, group: normalizedName })
    });
    loadSidebar();
};

// Bucle de refresco: solo si el usuario está al fondo del chat
async function refreshLoop() {
    try {
        if (activeChat && autoRefresh) {
            await loadHistoryIncremental(activeChat);
        }
        await checkIncomingCall();
        await pollWebRtcAnswerAndCandidates();
        await pollCallEnd();
    } catch (e) {

    } finally {
        setTimeout(refreshLoop, 1200);
    }
}
refreshLoop();