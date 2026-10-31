// Tela de login: mostra mensagens e coloca o token CSRF no formulário.

const params = new URLSearchParams(location.search);
document.getElementById('erro').hidden = !params.has('erro');
document.getElementById('saiu').hidden = !params.has('saiu');
document.getElementById('bloqueado').hidden = !params.has('bloqueado');

fetch('/api/csrf').then((r) => r.json()).then((t) => {
    const campo = document.getElementById('csrf');
    campo.name = t.parametro;
    campo.value = t.token;
});
