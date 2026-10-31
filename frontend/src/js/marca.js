// Identidade do studio em todas as telas: nome, especialidade, monograma e título da aba.
// Os valores vêm de /api/publico/studio, que lê o application.properties.

const conectivos = ['de', 'da', 'do', 'das', 'dos', 'e'];
const monograma = (nome) => nome.split(/\s+/)
    .filter((p) => p && !conectivos.includes(p.toLowerCase()))
    .slice(0, 2).map((p) => p[0].toUpperCase()).join('');

fetch('/api/publico/studio').then((r) => r.json()).then((s) => {
    document.querySelectorAll('[data-studio]').forEach((el) => el.textContent = s.nome);
    document.querySelectorAll('[data-especialidade]').forEach((el) => el.textContent = s.especialidade);
    document.querySelectorAll('[data-monograma]').forEach((el) => el.textContent = monograma(s.nome));
    // <meta name="pagina" content="Entrar"> vira "Entrar · Nome do studio"; sem ela, "Nome · Especialidade"
    const pagina = document.querySelector('meta[name="pagina"]')?.content;
    document.title = pagina ? `${pagina} · ${s.nome}` : `${s.nome} · ${s.especialidade}`;
});
