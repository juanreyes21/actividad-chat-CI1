let username = null;
let activeChat = null;

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

        d.innerHTML = `
            <div class="meta">${capitalize(m.sender)} • ${time}</div>
            <div class="text">${escapeHtml(m.text_content || '(sin contenido)')}</div>
        `;
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