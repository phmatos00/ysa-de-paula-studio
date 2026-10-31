// Chat público da cliente com a atendente virtual.

const caixa = document.getElementById('mensagens');
const form = document.getElementById('form');
const entrada = document.getElementById('texto');
const sugestoes = document.getElementById('sugestoes');
const telefone = new URLSearchParams(location.search).get('tel');
let conversaId = null;
let ocupado = false;

function adicionar(texto, quem) {
    const div = document.createElement('div');
    div.className = 'msg ' + quem;
    div.textContent = texto; // textContent evita injeção de HTML
    const horario = document.createElement('span');
    horario.className = 'horario';
    horario.textContent = new Date().toLocaleTimeString('pt-BR', {hour: '2-digit', minute: '2-digit'});
    div.appendChild(horario);
    caixa.appendChild(div);
    caixa.scrollTop = caixa.scrollHeight;
}

async function enviar(texto) {
    texto = texto.trim();
    if (!texto || ocupado) return;
    ocupado = true;
    form.querySelector('button').disabled = true;
    sugestoes.remove();
    adicionar(texto, 'cliente');
    entrada.value = '';

    const digitando = document.createElement('div');
    digitando.className = 'digitando';
    digitando.innerHTML = '<span></span><span></span><span></span>';
    caixa.appendChild(digitando);
    caixa.scrollTop = caixa.scrollHeight;

    try {
        const resp = await fetch('/api/chat', {
            method: 'POST',
            headers: {'Content-Type': 'application/json'},
            body: JSON.stringify({conversaId, telefone, mensagem: texto}),
        });
        const dados = await resp.json();
        conversaId = dados.conversaId;
        digitando.remove();
        adicionar(dados.resposta, 'ia');
    } catch (e) {
        digitando.remove();
        adicionar('Ops, perdi a conexão por um instante. Pode enviar de novo?', 'ia');
    } finally {
        ocupado = false;
        form.querySelector('button').disabled = false;
        entrada.focus();
    }
}

form.onsubmit = (e) => {
    e.preventDefault();
    enviar(entrada.value);
};
sugestoes.querySelectorAll('button').forEach((b) => b.onclick = () => enviar(b.textContent));

fetch('/api/chat/inicio' + (telefone ? '?tel=' + encodeURIComponent(telefone) : ''))
    .then((r) => r.json())
    .then((d) => {
        conversaId = d.conversaId;
        document.getElementById('studio').textContent = d.studio;
        document.title = 'Agendamento · ' + d.studio;
        adicionar(d.saudacao, 'ia');
        if (telefone) {
            sugestoes.innerHTML = '<button>Sim, confirmo minha presença!</button><button>Preciso remarcar</button>';
            sugestoes.querySelectorAll('button').forEach((b) => b.onclick = () => enviar(b.textContent));
        }
    });
entrada.focus();
