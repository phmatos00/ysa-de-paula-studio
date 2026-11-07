// Chat público da cliente com a atendente virtual.

const caixa = document.getElementById('mensagens');
const form = document.getElementById('form');
const entrada = document.getElementById('texto');
const sugestoes = document.getElementById('sugestoes');
const parametros = new URLSearchParams(location.search);
const telefone = parametros.get('tel');
// vindo do card de uma técnica no site (/chat.html?servico=Volume%20Russo)
const servicoEscolhido = (parametros.get('servico') || '').trim().slice(0, 60);
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

/** Mostra as escolhas como botões; clicar num botão envia o texto dele como resposta. */
function mostrarOpcoes(opcoes) {
    sugestoes.replaceChildren(...opcoes.map((texto) => {
        const b = document.createElement('button');
        b.type = 'button';
        b.textContent = texto;
        b.onclick = () => enviar(texto);
        return b;
    }));
    caixa.scrollTop = caixa.scrollHeight;
}

async function enviar(texto) {
    texto = texto.trim();
    if (!texto || ocupado) return;
    ocupado = true;
    form.querySelector('button').disabled = true;
    mostrarOpcoes([]);
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
        mostrarOpcoes(dados.opcoes || []);
    } catch (e) {
        digitando.remove();
        adicionar('Ops, perdi a conexão por um instante. Pode enviar de novo?', 'ia');
    } finally {
        ocupado = false;
        form.querySelector('button').disabled = false;
        // no celular, não abre o teclado quando há botões para escolher
        if (!sugestoes.children.length) entrada.focus();
    }
}

form.onsubmit = (e) => {
    e.preventDefault();
    enviar(entrada.value);
};

fetch('/api/chat/inicio' + (telefone ? '?tel=' + encodeURIComponent(telefone) : ''))
    .then((r) => r.json())
    .then((d) => {
        conversaId = d.conversaId;
        document.getElementById('studio').textContent = d.studio;
        document.title = 'Agendamento · ' + d.studio;
        adicionar(d.saudacao, 'ia');
        if (servicoEscolhido && !telefone) {
            // a cliente já escolheu a técnica no site: a conversa começa direto por ela
            enviar(`Quero agendar ${servicoEscolhido}`);
            return;
        }
        mostrarOpcoes(telefone
            ? ['Sim, confirmo minha presença!', 'Preciso remarcar']
            : ['Quero agendar um horário', 'Quais técnicas vocês fazem?', 'Ver meus agendamentos']);
    });
