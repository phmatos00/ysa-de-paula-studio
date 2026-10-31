# Ysa de Paula · Lash Designer

Sistema de gestão para Lash Designer, feito em Java 21 com Spring Boot.

- **Agenda**: horários livres calculados a partir do funcionamento e da duração de cada serviço, sem conflitos. Status: a confirmar, confirmado, realizado, cancelado ou faltou.
- **Financeiro**: entradas e saídas por categoria e forma de pagamento, com resumo mensal. Quando um atendimento é concluído, o valor é lançado no caixa sozinho.
- **Estoque**: produtos com estoque mínimo e alerta de reposição. Cada entrada e saída fica no histórico, e uma compra pode virar uma saída no financeiro.
- **Atendente virtual (Bia)**: chat público em que a cliente conversa com uma IA (Google Gemini, no plano gratuito, ou qualquer API compatível com a da OpenAI). A IA tira dúvidas, consulta horários, agenda, confirma, remarca e cancela, sempre usando os dados reais do sistema.

## Como rodar

1. Crie uma chave gratuita do Gemini em https://aistudio.google.com/apikey e coloque-a na variável de ambiente `IA_API_KEY`. No PowerShell:

   ```powershell
   $env:IA_API_KEY = "sua-chave-aqui"
   ```

2. Na pasta do projeto, rode o comando abaixo. Ele precisa do Java 21 ou mais novo, e o Maven é baixado sozinho.

   ```powershell
   .\mvnw.cmd -pl backend -am spring-boot:run
   ```

   No IntelliJ: abra a pasta do projeto (ele reconhece os módulos Maven) e rode a configuração **YsaDePaula**, que já vem pronta em `.run/`.

3. Acesse:
   - Site do studio (serviços, preços, contato e botão de agendar): http://localhost:8080
   - Painel: http://localhost:8080/admin (usuário `admin`, senha `lash123`; troque em `application.yml` ou pela variável `LASH_ADMIN_SENHA`)
   - Chat das clientes: http://localhost:8080/chat.html

Sem a chave de API, o painel funciona normalmente. Só o chat responde que está com um problema técnico.

Para usar outro provedor compatível com a API da OpenAI (Groq, OpenAI etc.), defina também `IA_URL` (endereço da API) e `IA_MODELO` (nome do modelo). Se o modelo principal estiver sobrecarregado, a Bia tenta de novo e depois usa o modelo reserva (`IA_MODELO_RESERVA`).

## Personalização

Nenhum dado do studio fica fixo no código. Cada valor tem um lugar só:

| O que mudar | Onde |
| --- | --- |
| Serviços, preços, duração e descrição | Painel → Serviços |
| Produtos, estoque mínimo e custo | Painel → Estoque |
| Nome do studio, nome da profissional, especialidade ("Lash Designer") | `backend/src/main/resources/application.yml`, bloco `studio` |
| Endereço, WhatsApp e Instagram (vazios não aparecem no site) | `application.yml` |
| Dias e horários de funcionamento, intervalo da agenda | `application.yml` |
| Usuário e senha do painel | `application.yml` ou variável `LASH_ADMIN_SENHA` |
| Endereço público do sistema (vai no link de confirmação) | variável `URL_PUBLICA` |
| Provedor e modelo da IA | variáveis `IA_URL`, `IA_MODELO`, `IA_MODELO_RESERVA` |
| Jeito de falar e orientações da Bia | `promptSistema()` em `AtendenteIA.java` |
| Cores e fontes | `frontend/src/css/estilo.css` (painel) e `site.css` (site) |

Depois de mudar o `application.yml`, reinicie o sistema. O arquivo é lido em UTF-8, então pode usar acentos; os horários vão entre aspas (`"09:00"`). Qualquer valor do bloco `studio` também pode vir de uma variável de ambiente, por exemplo `STUDIO_WHATSAPP=11987654321`. Os serviços e produtos de exemplo da primeira execução estão em `DadosIniciais.java`.

O nome, a especialidade e o monograma ("YP") das telas são preenchidos pelo `frontend/src/js/marca.js` a partir da API. Por isso, trocar o nome no `application.yml` muda todas as telas.

## Como funciona a confirmação

Em **Agenda → Lembretes para enviar** aparecem os atendimentos até amanhã que ainda não foram confirmados. O botão **Enviar no WhatsApp** abre a conversa com a cliente já com a mensagem escrita e o link do chat (`/chat.html?tel=...`). Quando a cliente abre o link, a Bia já sabe quem ela é, encontra o agendamento e confirma, ou remarca se ela precisar.

## Segurança

Feita com o Spring Security (`compartilhado/config/SecurityConfig.java` e o pacote `compartilhado/seguranca`):

- **Login no painel**: o site, o chat e a API pública são abertos; o painel e a API administrativa exigem login com o papel `ADMIN`. A senha fica guardada com BCrypt. Sem login, as páginas levam para a tela de entrada e a API responde `401`.
- **Senha errada**: depois de 5 erros seguidos do mesmo IP, o login fica bloqueado para ele por 15 minutos (até com a senha certa). Os outros IPs continuam entrando.
- **Chat**: cada mensagem custa uma chamada à IA, então o mesmo IP pode mandar até 20 mensagens a cada 5 minutos. Depois disso, a Bia pede para aguardar.
- **CSRF**: tudo o que altera dados no painel exige o token CSRF da sessão.
- **Cabeçalhos de proteção**: política de conteúdo (CSP) que só aceita scripts do próprio sistema, proibição de abrir o sistema dentro de outro site (`X-Frame-Options: DENY`) e outros.
- **Sessão**: expira após 4 horas sem uso; o cookie não pode ser lido por JavaScript. No perfil `producao`, o cookie só trafega por HTTPS e a senha do painel é obrigatória (`LASH_ADMIN_SENHA`).

Os bloqueios ficam em memória e zeram quando o sistema reinicia.

## Estrutura

O projeto é um **Maven multi-módulo** com duas partes:

```
ysa-de-paula-studio/      pom.xml pai (packaging pom): agrupa os módulos
├── frontend/             telas: HTML, CSS e JavaScript puros, sem build próprio
│   └── src/              index.html, login.html, chat.html, admin/, css/, js/
└── backend/              API REST em Spring Boot; depende do frontend
    └── src/main/java     código Java (abaixo)
```

O módulo `frontend` vira um jar com os arquivos dentro de `static/`. O `backend` depende dele, e o Spring Boot serve tudo que está em `classpath:/static`. Assim, o jar final do backend leva as telas junto e o sistema continua sendo publicado como um único arquivo.

No backend, o código é organizado **por funcionalidade**: cada domínio tem o próprio pacote, e dentro dele ficam as camadas daquele domínio.

```
backend/src/main/java/br/com/ysadepaula
├── YsaDePaulaApplication.java
├── agenda/          agendamentos, horários livres, confirmação e lembretes
│   ├── controller/  AgendaController
│   ├── dto/         NovoAgendamentoRequest, ConclusaoRequest, RemarcacaoRequest, LembreteConfirmacaoResponse
│   ├── model/       Agendamento, StatusAgendamento, OrigemAgendamento
│   ├── repository/  AgendamentoRepository
│   └── service/     AgendaService
├── atendimento/     atendente virtual (Bia) com IA
│   ├── controller/  ChatController
│   ├── dto/         MensagemChatRequest, RespostaChatResponse
│   ├── ferramenta/  FerramentasAtendimento (ações que a IA pode executar)
│   └── service/     AtendenteIA
├── cliente/         controller, model, repository
├── dashboard/       controller, dto (resumo da tela inicial do painel)
├── estoque/         controller, dto, model (Produto, MovimentacaoEstoque, TipoMovimentacao), repository, service
├── financeiro/      controller, dto, model (Lancamento, TipoLancamento), repository, service
├── servico/         controller, model, repository (catálogo de serviços e preços)
└── compartilhado/   o que é usado por todos os módulos
    ├── config/      segurança, propriedades do studio, dados iniciais, rotas web
    ├── controller/  PainelController (CSRF e dados do studio), PublicoController (site público)
    ├── seguranca/   ProtecaoLogin (bloqueio por senha errada) e LimitadorTentativas
    ├── dto/         InfoStudioResponse
    ├── exception/   NegocioException e TratamentoErros (respostas de erro da API)
    └── util/        Datas (formatação em português)
```

Regras seguidas:
- O `frontend` não conhece o `backend`: ele só chama a API por HTTP (`/api/...`).
- Um módulo pode usar o `service` ou o `model` de outro (a agenda lança no financeiro, por exemplo), mas nunca o `controller` de outro.
- Entidades JPA ficam em `model`; o que entra e sai da API e não é entidade fica em `dto` (records).
- Enums ficam em arquivos próprios no `model` do módulo.

Localmente, os dados ficam em um banco H2 em arquivo, na pasta `dados/`. Faça backup dessa pasta. Na hospedagem, use o perfil `producao` com PostgreSQL (veja `application-producao.properties`).

## Testes

```powershell
.\mvnw.cmd test
```

## Próximos passos sugeridos

- Integrar com a API oficial do WhatsApp Business (Meta Cloud API), para a Bia responder direto no WhatsApp e mandar os lembretes sozinha.
- Publicar em um servidor (Railway, Render, VPS) com o perfil `producao` e PostgreSQL, para as clientes acessarem pela internet.
- Baixar o estoque automaticamente pelos materiais usados em cada serviço.
