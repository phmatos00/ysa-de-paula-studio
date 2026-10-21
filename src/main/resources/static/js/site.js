// Página inicial pública: carrega dados do studio e serviços.

const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({'&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'}[c]));
const moeda = (v) => Number(v || 0).toLocaleString('pt-BR', {style: 'currency', currency: 'BRL'});
const duracao = (m) => m >= 60 ? `${Math.floor(m / 60)}h${m % 60 ? String(m % 60).padStart(2, '0') : ''}` : `${m} min`;

document.getElementById('ano').textContent = new Date().getFullYear();

// fios do ornamento do topo, distribuídos ao longo da curva
const cilios = document.getElementById('cilios');
for (let i = 1; i < 28; i++) {
    const t = i / 28;
    const x = 20 + 560 * t;
    const y = 120 - 4 * 75 * t * (1 - t);
    const angulo = (t - 0.5) * 1.3;
    const tam = 26 + 26 * Math.sin(Math.PI * t);
    const x2 = x + Math.sin(angulo) * tam;
    const y2 = y - Math.cos(angulo) * tam;
    cilios.insertAdjacentHTML('beforeend', `<path d="M${x} ${y} Q ${x + (x2 - x) * 0.3 + 6} ${y + (y2 - y) * 0.6} ${x2 + 8} ${y2}"/>`);
}

fetch('/api/publico/studio').then((r) => r.json()).then((s) => {
    document.querySelectorAll('[data-studio]').forEach((el) => el.textContent = s.nome);
    document.getElementById('endereco').textContent = s.endereco;
    document.getElementById('funcionamento').textContent = s.funcionamento.charAt(0).toUpperCase() + s.funcionamento.slice(1);
});

fetch('/api/publico/servicos').then((r) => r.json()).then((lista) => {
    document.getElementById('lista-servicos').innerHTML = lista.map((s) => `
        <article class="servico">
            <h3>${esc(s.nome)}</h3>
            <p>${esc(s.descricao)}</p>
            <div class="rodape-servico">
                <span class="preco">${moeda(s.preco)}</span>
                <span class="duracao"><svg class="icone" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>${duracao(s.duracaoMinutos)}</span>
            </div>
        </article>`).join('');
});
