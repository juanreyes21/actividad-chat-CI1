let username = null;
let activeChat = null;
let mediaRecorder = null;
let recordedChunks = [];

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

// Asegurar que el botón de grabar esté deshabilitado al cerrar sesión
// Grabación: botón y lógica
const recordBtn = document.getElementById('recordBtn');
if (recordBtn) {
    // si no hay usuario activo al inicio, mantenerlo deshabilitado
    recordBtn.disabled = true;
    recordBtn.onclick = async () => {
        if (!activeChat) return alert('Selecciona un chat primero.');

        if (!mediaRecorder || mediaRecorder.state === 'inactive') {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                recordedChunks = [];
                mediaRecorder = new MediaRecorder(stream);
                mediaRecorder.ondataavailable = (e) => { if (e.data && e.data.size > 0) recordedChunks.push(e.data); };
                mediaRecorder.onstop = async () => {
                    try {
                        const blob = new Blob(recordedChunks, { type: recordedChunks[0].type || 'audio/webm' });
                        // Convertir Blob a WAV PCM 44100Hz 16-bit mono usando AudioContext
                        const arrayBuffer = await blob.arrayBuffer();
                        const audioCtx = new (window.AudioContext || window.webkitAudioContext)();
                        const decoded = await audioCtx.decodeAudioData(arrayBuffer);

                        // Mezclar a mono y re-muestrear a 44100 si es necesario
                        const targetSampleRate = 44100;
                        const offlineCtx = new OfflineAudioContext(1, Math.ceil(decoded.duration * targetSampleRate), targetSampleRate);
                        const src = offlineCtx.createBufferSource();
                        // crear buffer mono
                        const monoBuffer = offlineCtx.createBuffer(1, decoded.length, decoded.sampleRate);
                        const chData = decoded.numberOfChannels > 1 ? decoded.getChannelData(0) : decoded.getChannelData(0);
                        // si tiene múltiples canales, mezclar
                        if (decoded.numberOfChannels > 1) {
                            const out = monoBuffer.getChannelData(0);
                            for (let i = 0; i < decoded.numberOfChannels; i++) {
                                const c = decoded.getChannelData(i);
                                for (let j = 0; j < c.length; j++) out[j] = (out[j] || 0) + c[j] / decoded.numberOfChannels;
                            }
                        } else {
                            monoBuffer.copyToChannel(decoded.getChannelData(0), 0);
                        }
                        src.buffer = monoBuffer;
                        src.connect(offlineCtx.destination);
                        src.start(0);
                        const rendered = await offlineCtx.startRendering();

                        // Encode WAV (16-bit PCM)
                        function encodeWAV(audioBuffer) {
                            const sampleRate = audioBuffer.sampleRate;
                            const samples = audioBuffer.getChannelData(0);
                            const buffer = new ArrayBuffer(44 + samples.length * 2);
                            const view = new DataView(buffer);

                            function writeString(view, offset, string) {
                                for (let i = 0; i < string.length; i++) {
                                    view.setUint8(offset + i, string.charCodeAt(i));
                                }
                            }

                            /* RIFF identifier */ writeString(view, 0, 'RIFF');
                            /* file length */ view.setUint32(4, 36 + samples.length * 2, true);
                            /* RIFF type */ writeString(view, 8, 'WAVE');
                            /* format chunk identifier */ writeString(view, 12, 'fmt ');
                            /* format chunk length */ view.setUint32(16, 16, true);
                            /* sample format (raw) */ view.setUint16(20, 1, true);
                            /* channel count */ view.setUint16(22, 1, true);
                            /* sample rate */ view.setUint32(24, sampleRate, true);
                            /* byte rate (sampleRate * blockAlign) */ view.setUint32(28, sampleRate * 2, true);
                            /* block align (channel count * bytes per sample) */ view.setUint16(32, 2, true);
                            /* bits per sample */ view.setUint16(34, 16, true);
                            /* data chunk identifier */ writeString(view, 36, 'data');
                            /* data chunk length */ view.setUint32(40, samples.length * 2, true);

                            // Write the PCM samples
                            let offset = 44;
                            for (let i = 0; i < samples.length; i++, offset += 2) {
                                let s = Math.max(-1, Math.min(1, samples[i]));
                                view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
                            }

                            return new Blob([view], { type: 'audio/wav' });
                        }

                        const wavBlob = encodeWAV(rendered);
                        const wavArray = await wavBlob.arrayBuffer();
                        // convertir a base64
                        let binary = '';
                        const bytes = new Uint8Array(wavArray);
                        const chunkSize = 0x8000;
                        for (let i = 0; i < bytes.length; i += chunkSize) {
                            binary += String.fromCharCode.apply(null, bytes.subarray(i, i + chunkSize));
                        }
                        const b64 = btoa(binary);
                        const fileName = 'recording_' + Date.now() + '.wav';

                        // Añadir la nota a la UI inmediatamente (optimista) usando el Blob local
                        try {
                            if (activeChat) {
                                const box = document.getElementById('messages');
                                const d = document.createElement('div');
                                d.className = 'msg me';
                                const time = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                                const audioUrl = URL.createObjectURL(wavBlob);
                                d.innerHTML = `\n+                                    <div class="meta">${capitalize(username)} • ${time}</div>\n+                                    <div class="text">(nota de voz)</div>\n+                                    <div class="voice"> <button class="play-voice-local">▶️ Reproducir</button> </div>`;
                                box.appendChild(d);
                                box.scrollTop = box.scrollHeight;

                                // Delegado para reproducir desde el blob (solo para este elemento)
                                const btn = d.querySelector('.play-voice-local');
                                btn.onclick = () => {
                                    const a = new Audio(audioUrl);
                                    a.play();
                                    a.onended = () => { btn.textContent = '▶️ Reproducir'; };
                                    btn.textContent = '⏵️';
                                };
                            }
                        } catch (uiErr) {
                            // no crítico
                            console.warn('No se pudo insertar nota localmente:', uiErr);
                        }

                        // enviar al proxy
                        try {
                            const res = await api('/api/voicenote', {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ username, recipient: activeChat, fileName, content: b64 })
                            });
                            if (res.status === 'ok') {
                                // recargar historial para asegurar sincronización con DB
                                await loadHistoryIncremental(activeChat, true);
                            } else {
                                alert('Error al enviar nota de voz.');
                            }
                        } catch (e) {
                            alert('Error al enviar nota de voz: ' + e.message);
                        }
                    } catch (err) {
                        alert('Error procesando audio: ' + err.message);
                    }
                };
                mediaRecorder.start();
                recordBtn.textContent = '⏺️';
                recordBtn.title = 'Detener y enviar';
            } catch (err) {
                alert('No se pudo acceder al micrófono: ' + err.message);
            }
        } else if (mediaRecorder.state === 'recording') {
            mediaRecorder.stop();
            recordBtn.textContent = '🎤';
            recordBtn.title = 'Grabar nota de voz';
        }
    };
}

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
    if (recordBtn) recordBtn.disabled = false;

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
    if (recordBtn) recordBtn.disabled = true;
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
        const key = `${m.timestamp}|${(m.sender || '').toLowerCase()}|${m.text_content || ''}`;
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

        let fileHtml = '';
        if (m.file_path) {
            // extraer basename (soporta '/' y '\\')
            const parts = (m.file_path || '').split(/\\\\|\//);
            const base = parts[parts.length - 1];
            fileHtml = `\n                <div class="voice">` +
                ` <button class="play-voice" data-file="${base}">▶️ Reproducir</button>` +
                `</div>`;
        }

        d.innerHTML = `
            <div class="meta">${capitalize(m.sender)} • ${time}</div>
            <div class="text">${escapeHtml(m.text_content || '(sin contenido)')}</div>` + fileHtml;
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

    await api('/api/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username, recipient: activeChat, text })
    });

    document.getElementById("text").value = '';
    await loadHistoryIncremental(activeChat, true); // baja al final
};

document.getElementById('text').onkeypress = (e) => {
    if (e.key === 'Enter') document.getElementById('send').click();
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
    } catch (e) {

    } finally {
        setTimeout(refreshLoop, 1200);
    }
}
refreshLoop();

// Delegación para reproducir audios cuando el usuario clickea el botón
document.addEventListener('click', async (e) => {
    if (e.target && e.target.classList && e.target.classList.contains('play-voice')) {
        const file = e.target.getAttribute('data-file');
        if (!file) return;
        e.target.disabled = true;
        e.target.textContent = 'Cargando...';
        try {
            const r = await api('/api/audio?file=' + encodeURIComponent(file));
            if (r.status === 'ok' && r.content) {
                const mime = r.mime || 'audio/wav';
                const src = 'data:' + mime + ';base64,' + r.content;
                const audio = new Audio(src);
                audio.play();
                // opcional: cambiar botón mientras suena
                audio.onended = () => { e.target.textContent = '▶️ Reproducir'; e.target.disabled = false; };
                e.target.textContent = '⏵️';
            } else {
                alert('No se pudo obtener el audio: ' + (r.message || 'error'));
                e.target.textContent = '▶️ Reproducir';
                e.target.disabled = false;
            }
        } catch (err) {
            alert('Error al obtener audio: ' + err.message);
            e.target.textContent = '▶️ Reproducir';
            e.target.disabled = false;
        }
    }
});