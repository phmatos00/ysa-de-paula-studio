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

// nome, especialidade e monograma ficam por conta do marca.js
fetch('/api/publico/studio').then((r) => r.json()).then((s) => {
    document.getElementById('endereco').textContent = s.endereco;
    document.getElementById('funcionamento').textContent = s.funcionamento.charAt(0).toUpperCase() + s.funcionamento.slice(1);
    document.getElementById('deslocamento').textContent = `Taxa de deslocamento: ${s.deslocamento}.`
        + (s.sinalPercentual ? ` Na colocação, um sinal de ${s.sinalPercentual}% garante o seu horário.` : '');
    if (s.whatsapp) {
        const link = document.getElementById('link-whatsapp');
        link.href = `https://wa.me/55${s.whatsapp}`;
        link.textContent = s.whatsapp.replace(/^(\d{2})(\d{4,5})(\d{4})$/, '($1) $2-$3');
        document.getElementById('contato-whatsapp').hidden = false;
    }
    if (s.instagram) {
        const link = document.getElementById('link-instagram');
        link.href = `https://instagram.com/${s.instagram}`;
        link.textContent = '@' + s.instagram;
        document.getElementById('contato-instagram').hidden = false;
    }
});

// "Volume Inglês" vira "volume-ingles": é o nome da foto em /img/tecnicas
const slug = (nome) => nome.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().trim().replace(/[^a-z0-9]+/g, '-');

fetch('/api/publico/servicos').then((r) => r.json()).then((lista) => {
    const grade = document.getElementById('lista-servicos');
    grade.innerHTML = lista.map((s) => `
        <article class="servico">
            <img class="foto-tecnica" src="/img/tecnicas/${slug(s.nome)}.jpg" alt="Exemplo de ${esc(s.nome)}" loading="lazy">
            <h3>${esc(s.nome)}</h3>
            <p>${esc(s.descricao)}</p>
            <div class="rodape-servico">
                <span class="preco"><small>Colocação</small>${moeda(s.preco)}</span>
                ${s.precoManutencao != null ? `<span class="preco manutencao"><small>Manutenção</small>${moeda(s.precoManutencao)}</span>` : ''}
                <span class="duracao"><svg class="icone" viewBox="0 0 24 24"><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></svg>${duracao(s.duracaoMinutos)}</span>
            </div>
        </article>`).join('');
    // técnica sem foto (ex.: cadastrada depois no painel): troca o ícone quebrado por um quadro "foto em breve"
    grade.querySelectorAll('.foto-tecnica').forEach((img) => img.addEventListener('error', () => {
        const vazio = document.createElement('div');
        vazio.className = 'foto-tecnica sem-foto';
        vazio.textContent = 'Foto em breve';
        img.replaceWith(vazio);
    }));
});
