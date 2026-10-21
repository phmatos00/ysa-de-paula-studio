// Painel administrativo do studio (JavaScript puro, sem dependências).

const conteudo = document.getElementById('conteudo');
let csrf = null;
let servicosCache = [];

// ---------- utilitários ----------

// os nomes vêm também do chat público, então todo texto é escapado antes de entrar no HTML
const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const moeda = (v) => Number(v || 0).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'});
const qtd = (v) => Number(v || 0).toLocaleString('pt-BR', {maximumFractionDigits: 2});
const hora = (iso) => iso.substring(11, 16);
const dataBR = (iso) => iso.substring(0, 10).split('-').reverse().join('/');
const hojeISO = () => new Date(Date.now() - new Date().getTimezoneOffset() * 60000).toISOString().substring(0, 10);
const somarDias = (iso, n) => {
    const d = new Date(iso + 'T12:00:00');
    d.setDate(d.getDate() + n);
    return d.toISOString().substring(0, 10);
};
const diaSemana = (iso) => {
    const t = new Date(iso + 'T12:00:00').toLocaleDateString('pt-BR', {weekday: 'long', day: '2-digit', month: 'long'});
    return t.charAt(0).toUpperCase() + t.slice(1);
};
const telefoneBR = (t) => t && t.length >= 10 ? `(${t.substring(0, 2)}) ${t.substring(2, t.length - 4)}-${t.substring(t.length - 4)}` : (t || '');
const STATUS = {PENDENTE: 'A confirmar', CONFIRMADO: 'Confirmado', REALIZADO: 'Realizado', CANCELADO: 'Cancelado', FALTOU: 'Faltou'};
const FORMAS = ['Pix', 'Dinheiro', 'Cartão de crédito', 'Cartão de débito', 'Transferência'];

function aviso(msg, erro = false) {
    const el = document.createElement('div');
    el.className = 'aviso' + (erro ? ' erro' : '');
    el.textContent = msg;
    document.body.appendChild(el);
    setTimeout(() => el.remove(), 3500);
}

async function api(metodo, url, corpo) {
    if (!csrf && metodo !== 'GET') csrf = await api('GET', '/api/csrf');
    const opcoes = {method: metodo, headers: {}};
    if (metodo !== 'GET') opcoes.headers[csrf.header] = csrf.token;
    if (corpo !== undefined) {
        opcoes.headers['Content-Type'] = 'application/json';
        opcoes.body = JSON.stringify(corpo);
    }
    const resp = await fetch(url, opcoes);
    if (resp.redirected && resp.url.includes('/login')) {
        location.href = '/login';
        throw new Error('Sessão expirada');
    }
    const texto = await resp.text();
    const dados = texto ? JSON.parse(texto) : null;
    if (!resp.ok) {
        const msg = dados?.erro || 'Não foi possível concluir a operação.';
        aviso(msg, true);
        throw new Error(msg);
    }
    return dados;
}

/**
 * Abre um formulário em janela modal. campos: [{nome, rotulo, tipo, valor, opcoes, obrigatorio}]
 * Resolve com um objeto {nome: valor} ou null se cancelado.
 */
function formulario(titulo, campos, textoBotao = 'Salvar') {
    return new Promise((resolve) => {
        const fundo = document.createElement('div');
        fundo.className = 'fundo-modal';
        fundo.innerHTML = `<form class="modal"><h2>${esc(titulo)}</h2><div class="campos">${campos.map((c) => {
            if (c.tipo === 'checkbox') {
                return `<label class="check"><input type="checkbox" name="${c.nome}" ${c.valor ? 'checked' : ''}> ${esc(c.rotulo)}</label>`;
            }
            const req = c.obrigatorio ? 'required' : '';
            let campo;
            if (c.tipo === 'select') {
                campo = `<select name="${c.nome}" ${req}>${c.opcoes.map((o) => {
                    const [v, t] = Array.isArray(o) ? o : [o, o];
                    return `<option value="${esc(v)}" ${String(v) === String(c.valor ?? '') ? 'selected' : ''}>${esc(t)}</option>`;
                }).join('')}</select>`;
            } else if (c.tipo === 'textarea') {
                campo = `<textarea name="${c.nome}" rows="3">${esc(c.valor)}</textarea>`;
            } else {
                campo = `<input name="${c.nome}" type="${c.tipo || 'text'}" value="${esc(c.valor)}" ${c.tipo === 'number' ? 'step="0.01" min="0"' : ''} ${req}>`;
            }
            return `<div><label>${esc(c.rotulo)}</label>${campo}</div>`;
        }).join('')}</div><div class="botoes"><button type="button" class="secundario" data-cancelar>Cancelar</button><button type="submit">${esc(textoBotao)}</button></div></form>`;
        const fechar = (v) => { fundo.remove(); resolve(v); };
        fundo.querySelector('[data-cancelar]').onclick = () => fechar(null);
        fundo.onclick = (e) => { if (e.target === fundo) fechar(null); };
        fundo.querySelector('form').onsubmit = (e) => {
            e.preventDefault();
            const r = {};
            campos.forEach((c) => {
                const el = e.target.elements[c.nome];
                r[c.nome] = c.tipo === 'checkbox' ? el.checked : el.value;
            });
            fechar(r);
        };
        document.body.appendChild(fundo);
        fundo.querySelector('input, select, textarea')?.focus();
    });
}

async function carregarServicos() {
    servicosCache = await api('GET', '/api/servicos');
    return servicosCache;
}

// ---------- Início ----------

const ICONES = {
    agenda: '<rect x="3" y="4.5" width="18" height="16.5" rx="2"/><path d="M3 9.5h18M8 2.5v4M16 2.5v4"/>',
    sino: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 8 3 8H3s3-1 3-8"/><path d="M10.3 20a2 2 0 0 0 3.4 0"/>',
    entrada: '<path d="M12 19V5M5 12l7-7 7 7"/>',
    saida: '<path d="M12 5v14M19 12l-7 7-7-7"/>',
    carteira: '<rect x="2.5" y="6" width="19" height="13" rx="2"/><circle cx="12" cy="12.5" r="2.5"/>',
    alerta: '<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4M12 17h.01"/>',
    ok: '<circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/>',
    whatsapp: '<path d="M21 11.5a8.4 8.4 0 0 1-12.4 7.4L3 20.5l1.7-5.3A8.4 8.4 0 1 1 21 11.5z"/>',
};
const icone = (nome) => `<svg class="icone" viewBox="0 0 24 24">${ICONES[nome]}</svg>`;
let studioInfo = {nome: 'Ysa de Paula', profissional: 'Ysa'};

function cabecalho(sobretitulo, titulo, acoes = '') {
    return `<div class="cabecalho"><div><div class="sobretitulo">${esc(sobretitulo)}</div><h1>${esc(titulo)}</h1></div>
        ${acoes ? `<div class="acoes">${acoes}</div>` : ''}</div>`;
}

async function telaInicio() {
    const d = await api('GET', '/api/dashboard');
    const h = new Date().getHours();
    const saudacao = h < 12 ? 'Bom dia' : h < 18 ? 'Boa tarde' : 'Boa noite';
    const atendimentos = d.hoje.filter((a) => a.status !== 'CANCELADO').length;
    conteudo.innerHTML = `
        ${cabecalho(diaSemana(hojeISO()), `${saudacao}, ${studioInfo.profissional} ✨`, '<a class="botao" href="#agenda">Abrir agenda</a>')}
        <div class="indicadores">
            <div class="indicador destaque"><div class="rotulo">${icone('agenda')}Atendimentos hoje</div><div class="valor">${atendimentos}</div></div>
            <div class="indicador"><div class="rotulo">${icone('sino')}A confirmar até amanhã</div><div class="valor">${d.pendentesConfirmacao}</div></div>
            <div class="indicador"><div class="rotulo">${icone('entrada')}Entradas no mês</div><div class="valor">${moeda(d.entradasMes)}</div></div>
            <div class="indicador ${d.saldoMes >= 0 ? 'positivo' : 'negativo'}"><div class="rotulo">${icone('carteira')}Saldo do mês</div><div class="valor">${moeda(d.saldoMes)}</div></div>
        </div>
        <div class="grade2">
            <div class="cartao"><div class="cartao-cabecalho"><h2>Agenda de hoje</h2><a href="#agenda" class="texto-suave">Ver agenda completa →</a></div><div id="lista-hoje"></div></div>
            <div class="cartao"><div class="cartao-cabecalho"><h2>Estoque</h2><a href="#estoque" class="texto-suave">Gerenciar →</a></div>${d.estoqueBaixo.length === 0
                ? `<div class="sucesso-vazio">${icone('ok')}Tudo em dia com o estoque.</div>`
                : `<p class="texto-suave">Estes itens chegaram no mínimo e precisam de reposição:</p>` + d.estoqueBaixo.map((p) =>
                    `<div class="alerta">${icone('alerta')}<span><strong>${esc(p.nome)}</strong> · ${qtd(p.quantidade)} ${esc(p.unidade)} (mínimo ${qtd(p.estoqueMinimo)})</span></div>`).join('')}
            </div>
        </div>`;
    renderizarAgendamentos(document.getElementById('lista-hoje'), d.hoje, telaInicio);
}

// ---------- Agenda ----------

let diaAgenda = hojeISO();

async function telaAgenda() {
    await carregarServicos();
    const [lista, confirmacoes] = await Promise.all([
        api('GET', `/api/agenda?dia=${diaAgenda}`),
        api('GET', '/api/agenda/confirmacoes'),
    ]);
    conteudo.innerHTML = `
        ${cabecalho('Agenda', diaSemana(diaAgenda), `
            <button class="secundario icone-so" id="dia-anterior" title="Dia anterior">‹</button>
            <input type="date" id="dia" value="${diaAgenda}">
            <button class="secundario icone-so" id="dia-seguinte" title="Próximo dia">›</button>
            <button class="secundario" id="dia-hoje">Hoje</button>
            <button id="novo-agendamento">+ Novo agendamento</button>`)}
        ${confirmacoes.length ? `<div class="cartao faixa-dourada">
            <div class="cartao-cabecalho"><h2>Lembretes para enviar</h2><span class="texto-suave">${confirmacoes.length} aguardando confirmação</span></div>
            <p class="texto-suave">O botão abre o WhatsApp com a mensagem pronta e o link do chat, onde a Bia confirma com a cliente.</p>
            ${confirmacoes.map((c) => `<div class="lembrete">
                <div class="quando">${esc(dataBR(c.agendamento.inicio))} · ${hora(c.agendamento.inicio)}</div>
                <div class="quem">${esc(c.agendamento.cliente.nome)}<small>${esc(c.agendamento.servico.nome)}</small></div>
                <a class="botao pequeno verde" href="${esc(c.linkWhatsapp)}" target="_blank">${icone('whatsapp')}Enviar lembrete</a>
            </div>`).join('')}</div>` : ''}
        <div class="cartao"><div id="lista-dia"></div></div>`;

    const irPara = (iso) => { diaAgenda = iso; telaAgenda(); };
    document.getElementById('dia').onchange = (e) => irPara(e.target.value);
    document.getElementById('dia-anterior').onclick = () => irPara(somarDias(diaAgenda, -1));
    document.getElementById('dia-seguinte').onclick = () => irPara(somarDias(diaAgenda, 1));
    document.getElementById('dia-hoje').onclick = () => irPara(hojeISO());
    document.getElementById('novo-agendamento').onclick = novoAgendamento;
    renderizarAgendamentos(document.getElementById('lista-dia'), lista, telaAgenda);
}

function renderizarAgendamentos(el, lista, recarregar) {
    if (!lista.length) {
        el.innerHTML = '<div class="vazio">Nenhum atendimento neste dia.</div>';
        return;
    }
    el.innerHTML = lista.map((a) => {
        const ativo = a.status === 'PENDENTE' || a.status === 'CONFIRMADO';
        return `<div class="agendamento">
            <div class="hora">${hora(a.inicio)}<small>até ${hora(a.fim)}</small></div>
            <div class="info">
                <div class="nome">${esc(a.cliente.nome)} <span class="selo ${a.status}">${STATUS[a.status]}</span> ${a.origem === 'IA' ? '<span class="selo IA">via IA</span>' : ''}</div>
                <div class="detalhe">${esc(a.servico.nome)} · ${moeda(a.valor)} · <a href="https://wa.me/55${esc(a.cliente.telefone)}" target="_blank">${esc(telefoneBR(a.cliente.telefone))}</a></div>
                ${a.observacoes ? `<div class="detalhe">${esc(a.observacoes)}</div>` : ''}
            </div>
            <div class="botoes">
                ${a.status === 'PENDENTE' ? `<button class="pequeno secundario" data-acao="confirmar" data-id="${a.id}">Confirmar</button>` : ''}
                ${ativo ? `<button class="pequeno verde" data-acao="concluir" data-id="${a.id}">Concluir</button>
                    <button class="pequeno secundario" data-acao="remarcar" data-id="${a.id}">Remarcar</button>
                    <button class="pequeno perigo" data-acao="falta" data-id="${a.id}">Faltou</button>
                    <button class="pequeno perigo" data-acao="cancelar" data-id="${a.id}">Cancelar</button>` : ''}
            </div>
        </div>`;
    }).join('');

    el.querySelectorAll('[data-acao]').forEach((b) => b.onclick = async () => {
        const a = lista.find((x) => x.id == b.dataset.id);
        const acao = b.dataset.acao;
        if (acao === 'concluir') {
            const r = await formulario(`Concluir atendimento de ${a.cliente.nome}`, [
                {nome: 'valor', rotulo: 'Valor recebido (R$)', tipo: 'number', valor: a.valor, obrigatorio: true},
                {nome: 'formaPagamento', rotulo: 'Forma de pagamento', tipo: 'select', opcoes: FORMAS},
            ], 'Concluir e lançar no caixa');
            if (!r) return;
            await api('POST', `/api/agenda/${a.id}/concluir`, r);
            aviso('Atendimento concluído e lançado no financeiro 💰');
        } else if (acao === 'remarcar') {
            const r = await escolherHorario(`Remarcar ${a.cliente.nome}`, a.servico.id, a.inicio.substring(0, 10));
            if (!r) return;
            await api('POST', `/api/agenda/${a.id}/remarcar`, {inicio: r});
            aviso('Atendimento remarcado.');
        } else {
            const textos = {cancelar: 'Cancelar este agendamento?', falta: 'Marcar que a cliente faltou?'};
            if (textos[acao] && !confirm(textos[acao])) return;
            await api('POST', `/api/agenda/${a.id}/${acao}`);
            aviso('Pronto!');
        }
        recarregar();
    });
}

/** Pede o dia e mostra só os horários livres para o serviço. Devolve "AAAA-MM-DDTHH:mm" ou null. */
async function escolherHorario(titulo, servicoId, diaInicial) {
    const passo1 = await formulario(titulo, [{nome: 'dia', rotulo: 'Dia', tipo: 'date', valor: diaInicial, obrigatorio: true}], 'Ver horários');
    if (!passo1) return null;
    const livres = await api('GET', `/api/agenda/horarios?dia=${passo1.dia}&servicoId=${servicoId}`);
    if (!livres.length) {
        aviso('Não há horários livres neste dia.', true);
        return null;
    }
    const passo2 = await formulario(`${titulo} · ${dataBR(passo1.dia)}`, [
        {nome: 'hora', rotulo: 'Horário', tipo: 'select', opcoes: livres.map((h) => h.substring(0, 5))},
    ], 'Confirmar horário');
    return passo2 ? `${passo1.dia}T${passo2.hora}` : null;
}

async function novoAgendamento() {
    const dados = await formulario('Novo agendamento', [
        {nome: 'nome', rotulo: 'Nome da cliente', obrigatorio: true},
        {nome: 'telefone', rotulo: 'WhatsApp com DDD', tipo: 'tel', obrigatorio: true},
        {nome: 'servicoId', rotulo: 'Serviço', tipo: 'select', opcoes: servicosCache.map((s) => [s.id, `${s.nome} · ${moeda(s.preco)}`])},
        {nome: 'observacoes', rotulo: 'Observações', tipo: 'textarea'},
    ], 'Escolher horário');
    if (!dados) return;
    const inicio = await escolherHorario('Horário', dados.servicoId, diaAgenda);
    if (!inicio) return;
    await api('POST', '/api/agenda', {...dados, servicoId: Number(dados.servicoId), inicio});
    aviso('Agendamento criado ✨');
    diaAgenda = inicio.substring(0, 10);
    telaAgenda();
}

// ---------- Financeiro ----------

let mesFinanceiro = hojeISO().substring(0, 7);

async function telaFinanceiro() {
    const [ano, mes] = mesFinanceiro.split('-').map(Number);
    const ultimo = new Date(ano, mes, 0).getDate();
    const r = await api('GET', `/api/financeiro?de=${mesFinanceiro}-01&ate=${mesFinanceiro}-${String(ultimo).padStart(2, '0')}`);
    const categorias = (mapa) => Object.entries(mapa).map(([c, v]) => `<tr><td>${esc(c)}</td><td class="num">${moeda(v)}</td></tr>`).join('') || '<tr><td class="vazio">—</td></tr>';
    conteudo.innerHTML = `
        ${cabecalho('Fluxo de caixa', 'Financeiro', `
            <input type="month" id="mes" value="${mesFinanceiro}">
            <button class="secundario" data-tipo="SAIDA">− Nova saída</button>
            <button data-tipo="ENTRADA">+ Nova entrada</button>`)}
        <div class="indicadores">
            <div class="indicador positivo"><div class="rotulo">${icone('entrada')}Entradas</div><div class="valor">${moeda(r.entradas)}</div></div>
            <div class="indicador negativo"><div class="rotulo">${icone('saida')}Saídas</div><div class="valor">${moeda(r.saidas)}</div></div>
            <div class="indicador destaque"><div class="rotulo">${icone('carteira')}Saldo do período</div><div class="valor">${moeda(r.saldo)}</div></div>
        </div>
        <div class="grade2 igual">
            <div class="cartao"><h3>Entradas por categoria</h3><table>${categorias(r.entradasPorCategoria)}</table></div>
            <div class="cartao"><h3>Saídas por categoria</h3><table>${categorias(r.saidasPorCategoria)}</table></div>
        </div>
        <div class="cartao tabela-rolavel"><h2>Lançamentos</h2>
            <table><thead><tr><th>Data</th><th>Tipo</th><th>Categoria</th><th>Descrição</th><th>Pagamento</th><th class="num">Valor</th><th></th></tr></thead>
            <tbody>${r.lancamentos.map((l) => `<tr>
                <td>${dataBR(l.data)}</td><td><span class="selo ${l.tipo}">${l.tipo === 'ENTRADA' ? 'Entrada' : 'Saída'}</span></td>
                <td>${esc(l.categoria)}</td><td>${esc(l.descricao)}</td><td>${esc(l.formaPagamento)}</td>
                <td class="num">${moeda(l.valor)}</td>
                <td class="num"><button class="pequeno perigo" data-excluir="${l.id}">Excluir</button></td>
            </tr>`).join('') || '<tr><td colspan="7" class="vazio">Nenhum lançamento neste mês.</td></tr>'}</tbody></table>
        </div>`;

    document.getElementById('mes').onchange = (e) => { mesFinanceiro = e.target.value; telaFinanceiro(); };
    conteudo.querySelectorAll('[data-tipo]').forEach((b) => b.onclick = () => novoLancamento(b.dataset.tipo));
    conteudo.querySelectorAll('[data-excluir]').forEach((b) => b.onclick = async () => {
        if (!confirm('Excluir este lançamento?')) return;
        await api('DELETE', `/api/financeiro/${b.dataset.excluir}`);
        telaFinanceiro();
    });
}

async function novoLancamento(tipo) {
    const categorias = tipo === 'ENTRADA'
        ? ['Atendimento', 'Venda de produto', 'Curso', 'Outros']
        : ['Material / Estoque', 'Aluguel', 'Energia / Água / Internet', 'Marketing', 'Impostos / MEI', 'Cursos e capacitação', 'Equipamentos', 'Pró-labore', 'Outros'];
    const r = await formulario(tipo === 'ENTRADA' ? 'Nova entrada' : 'Nova saída', [
        {nome: 'valor', rotulo: 'Valor (R$)', tipo: 'number', obrigatorio: true},
        {nome: 'categoria', rotulo: 'Categoria', tipo: 'select', opcoes: categorias},
        {nome: 'descricao', rotulo: 'Descrição'},
        {nome: 'data', rotulo: 'Data', tipo: 'date', valor: hojeISO(), obrigatorio: true},
        {nome: 'formaPagamento', rotulo: 'Forma de pagamento', tipo: 'select', opcoes: FORMAS},
    ]);
    if (!r) return;
    await api('POST', '/api/financeiro', {...r, tipo});
    aviso('Lançamento salvo.');
    telaFinanceiro();
}

// ---------- Estoque ----------

async function telaEstoque() {
    const [produtos, movs] = await Promise.all([api('GET', '/api/estoque'), api('GET', '/api/estoque/movimentacoes')]);
    conteudo.innerHTML = `
        ${cabecalho('Materiais e produtos', 'Estoque', '<button id="novo-produto">+ Novo produto</button>')}
        <div class="cartao tabela-rolavel">
            <table><thead><tr><th>Produto</th><th>Categoria</th><th class="num">Quantidade</th><th class="num">Mínimo</th><th class="num">Custo unit.</th><th></th></tr></thead>
            <tbody>${produtos.map((p) => `<tr>
                <td>${esc(p.nome)}</td><td>${esc(p.categoria)}</td>
                <td class="num ${p.estoqueBaixo ? 'baixo' : ''}">${qtd(p.quantidade)} ${esc(p.unidade)}${p.estoqueBaixo ? ' ⚠' : ''}</td>
                <td class="num">${qtd(p.estoqueMinimo)}</td><td class="num">${moeda(p.custoUnitario)}</td>
                <td class="num">
                    <button class="pequeno verde" data-entrada="${p.id}">Entrada</button>
                    <button class="pequeno secundario" data-saida="${p.id}">Saída</button>
                    <button class="pequeno secundario" data-editar="${p.id}">Editar</button>
                </td></tr>`).join('') || '<tr><td colspan="6" class="vazio">Nenhum produto cadastrado.</td></tr>'}</tbody></table>
        </div>
        <div class="cartao tabela-rolavel"><h2>Últimas movimentações</h2>
            <table><thead><tr><th>Data</th><th>Produto</th><th>Tipo</th><th class="num">Qtd.</th><th>Observação</th></tr></thead>
            <tbody>${movs.map((m) => `<tr><td>${dataBR(m.data)} ${hora(m.data)}</td><td>${esc(m.produto.nome)}</td>
                <td><span class="selo ${m.tipo}">${m.tipo === 'ENTRADA' ? 'Entrada' : 'Saída'}</span></td>
                <td class="num">${qtd(m.quantidade)}</td><td>${esc(m.observacao)}</td></tr>`).join('') || '<tr><td colspan="5" class="vazio">Sem movimentações.</td></tr>'}</tbody></table>
        </div>`;

    const porId = (id) => produtos.find((p) => p.id == id);
    document.getElementById('novo-produto').onclick = () => editarProduto({});
    conteudo.querySelectorAll('[data-editar]').forEach((b) => b.onclick = () => editarProduto(porId(b.dataset.editar)));
    conteudo.querySelectorAll('[data-entrada]').forEach((b) => b.onclick = async () => {
        const p = porId(b.dataset.entrada);
        const r = await formulario(`Entrada: ${p.nome}`, [
            {nome: 'quantidade', rotulo: `Quantidade (${p.unidade})`, tipo: 'number', obrigatorio: true},
            {nome: 'custoUnitario', rotulo: 'Custo unitário (R$)', tipo: 'number', valor: p.custoUnitario},
            {nome: 'lancarDespesa', rotulo: 'Lançar a compra como saída no financeiro', tipo: 'checkbox', valor: true},
            {nome: 'formaPagamento', rotulo: 'Forma de pagamento', tipo: 'select', opcoes: FORMAS},
            {nome: 'observacao', rotulo: 'Observação (fornecedor, nota...)'},
        ], 'Registrar entrada');
        if (!r) return;
        await api('POST', `/api/estoque/${p.id}/entrada`, r);
        aviso('Entrada registrada.');
        telaEstoque();
    });
    conteudo.querySelectorAll('[data-saida]').forEach((b) => b.onclick = async () => {
        const p = porId(b.dataset.saida);
        const r = await formulario(`Saída: ${p.nome}`, [
            {nome: 'quantidade', rotulo: `Quantidade (${p.unidade})`, tipo: 'number', obrigatorio: true},
            {nome: 'observacao', rotulo: 'Motivo', tipo: 'select', opcoes: ['Uso nos atendimentos', 'Venda', 'Perda / vencido', 'Outro']},
        ], 'Registrar saída');
        if (!r) return;
        await api('POST', `/api/estoque/${p.id}/saida`, r);
        aviso('Saída registrada.');
        telaEstoque();
    });
}

async function editarProduto(p) {
    const r = await formulario(p.id ? 'Editar produto' : 'Novo produto', [
        {nome: 'nome', rotulo: 'Nome', valor: p.nome, obrigatorio: true},
        {nome: 'categoria', rotulo: 'Categoria', tipo: 'select', valor: p.categoria, opcoes: ['Fios', 'Cola', 'Preparação', 'Remoção', 'Descartáveis', 'Higienização', 'Pinças e ferramentas', 'Outros']},
        {nome: 'unidade', rotulo: 'Unidade', tipo: 'select', valor: p.unidade, opcoes: ['un', 'cx', 'pct', 'ml', 'g']},
        ...(p.id ? [] : [{nome: 'quantidade', rotulo: 'Quantidade inicial', tipo: 'number', valor: 0}]),
        {nome: 'estoqueMinimo', rotulo: 'Avisar quando chegar a', tipo: 'number', valor: p.estoqueMinimo ?? 1},
        {nome: 'custoUnitario', rotulo: 'Custo unitário (R$)', tipo: 'number', valor: p.custoUnitario ?? 0},
    ]);
    if (!r) return;
    await api('POST', '/api/estoque', {...p, ...r});
    aviso('Produto salvo.');
    telaEstoque();
}

// ---------- Serviços ----------

async function telaServicos() {
    const servicos = await carregarServicos();
    conteudo.innerHTML = `
        ${cabecalho('Catálogo', 'Serviços', '<a class="botao secundario" href="/" target="_blank">Ver no site</a><button id="novo-servico">+ Novo serviço</button>')}
        <div class="cartao tabela-rolavel"><p class="texto-suave">Estes são os serviços, preços e durações que a atendente virtual oferece às clientes.</p>
            <table><thead><tr><th>Serviço</th><th>Descrição</th><th class="num">Duração</th><th class="num">Preço</th><th></th></tr></thead>
            <tbody>${servicos.map((s) => `<tr><td><strong>${esc(s.nome)}</strong></td><td class="texto-suave">${esc(s.descricao)}</td>
                <td class="num">${s.duracaoMinutos} min</td><td class="num">${moeda(s.preco)}</td>
                <td class="num"><button class="pequeno secundario" data-editar="${s.id}">Editar</button>
                <button class="pequeno perigo" data-remover="${s.id}">Remover</button></td></tr>`).join('')}</tbody></table>
        </div>`;
    document.getElementById('novo-servico').onclick = () => editarServico({});
    conteudo.querySelectorAll('[data-editar]').forEach((b) => b.onclick = () => editarServico(servicos.find((s) => s.id == b.dataset.editar)));
    conteudo.querySelectorAll('[data-remover]').forEach((b) => b.onclick = async () => {
        if (!confirm('Remover este serviço? Os agendamentos antigos continuam no histórico.')) return;
        await api('DELETE', `/api/servicos/${b.dataset.remover}`);
        telaServicos();
    });
}

async function editarServico(s) {
    const r = await formulario(s.id ? 'Editar serviço' : 'Novo serviço', [
        {nome: 'nome', rotulo: 'Nome', valor: s.nome, obrigatorio: true},
        {nome: 'descricao', rotulo: 'Descrição (a IA usa para explicar às clientes)', tipo: 'textarea', valor: s.descricao},
        {nome: 'duracaoMinutos', rotulo: 'Duração (minutos)', tipo: 'number', valor: s.duracaoMinutos ?? 60, obrigatorio: true},
        {nome: 'preco', rotulo: 'Preço (R$)', tipo: 'number', valor: s.preco, obrigatorio: true},
    ]);
    if (!r) return;
    await api('POST', '/api/servicos', {...s, ...r, duracaoMinutos: Math.round(Number(r.duracaoMinutos))});
    aviso('Serviço salvo.');
    telaServicos();
}

// ---------- Clientes ----------

async function telaClientes() {
    const clientes = await api('GET', '/api/clientes');
    conteudo.innerHTML = `
        ${cabecalho(`${clientes.length} cadastradas`, 'Clientes', '<input id="busca" placeholder="Buscar por nome ou telefone" style="width:280px">')}
        <div class="cartao tabela-rolavel"><table><thead><tr><th>Nome</th><th>WhatsApp</th><th>Observações</th><th></th></tr></thead><tbody id="linhas"></tbody></table></div>`;
    const desenhar = (filtro = '') => {
        const f = filtro.toLowerCase();
        const lista = clientes.filter((c) => c.nome.toLowerCase().includes(f) || c.telefone.includes(f.replace(/\D/g, '') || '§'));
        document.getElementById('linhas').innerHTML = lista.map((c) => `<tr><td>${esc(c.nome)}</td>
            <td><a href="https://wa.me/55${esc(c.telefone)}" target="_blank">${esc(telefoneBR(c.telefone))}</a></td>
            <td class="texto-suave">${esc(c.observacoes)}</td>
            <td class="num"><button class="pequeno secundario" data-editar="${c.id}">Editar</button></td></tr>`).join('')
            || '<tr><td colspan="4" class="vazio">Nenhuma cliente encontrada.</td></tr>';
        document.querySelectorAll('[data-editar]').forEach((b) => b.onclick = async () => {
            const c = clientes.find((x) => x.id == b.dataset.editar);
            const r = await formulario('Editar cliente', [
                {nome: 'nome', rotulo: 'Nome', valor: c.nome, obrigatorio: true},
                {nome: 'observacoes', rotulo: 'Observações (curvatura, espessura, alergias...)', tipo: 'textarea', valor: c.observacoes},
            ]);
            if (!r) return;
            await api('POST', '/api/clientes', {...c, ...r});
            telaClientes();
        });
    };
    document.getElementById('busca').oninput = (e) => desenhar(e.target.value);
    desenhar();
}

// ---------- navegação ----------

const TELAS = {inicio: telaInicio, agenda: telaAgenda, financeiro: telaFinanceiro, estoque: telaEstoque, servicos: telaServicos, clientes: telaClientes};

function navegar() {
    const secao = location.hash.substring(1) || 'inicio';
    document.querySelectorAll('.menu a[data-secao]').forEach((a) => a.classList.toggle('ativo', a.dataset.secao === secao));
    (TELAS[secao] || telaInicio)().catch((e) => console.error(e));
}

document.getElementById('sair').onclick = async (e) => {
    e.preventDefault();
    csrf = csrf || await api('GET', '/api/csrf');
    const form = document.createElement('form');
    form.method = 'POST';
    form.action = '/logout';
    form.innerHTML = `<input type="hidden" name="${esc(csrf.parametro)}" value="${esc(csrf.token)}">`;
    document.body.appendChild(form);
    form.submit();
};

window.addEventListener('hashchange', navegar);
api('GET', '/api/studio').then((s) => {
    studioInfo = s;
    document.getElementById('nome-studio').textContent = s.nome;
    document.title = `Painel · ${s.nome}`;
}).finally(navegar);
