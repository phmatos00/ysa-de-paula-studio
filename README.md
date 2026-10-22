# Ysa de Paula · Lash Designer

Sistema de gestão para Lash Designer, feito em Java 21 com Spring Boot.

- **Agenda**: horários livres calculados a partir do funcionamento e da duração de cada serviço, sem conflitos. Status: a confirmar, confirmado, realizado, cancelado ou faltou.
- **Financeiro**: entradas e saídas por categoria e forma de pagamento, com resumo mensal. Quando um atendimento é concluído, o valor é lançado no caixa sozinho.
- **Estoque**: produtos com estoque mínimo e alerta de reposição. Cada entrada e saída fica no histórico, e uma compra pode virar uma saída no financeiro.
- **Atendente virtual (Bia)**: chat público em que a cliente conversa com uma IA (Claude, da Anthropic). A IA tira dúvidas, consulta horários, agenda, confirma, remarca e cancela, sempre usando os dados reais do sistema.

## Como rodar

1. Crie uma chave de API em https://console.anthropic.com e coloque-a na variável de ambiente `ANTHROPIC_API_KEY`. No PowerShell:

   ```powershell
   $env:ANTHROPIC_API_KEY = "sk-ant-..."
   ```

2. Na pasta do projeto, rode o comando abaixo. Ele precisa do Java 21 ou mais novo, e o Maven é baixado sozinho.

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

3. Acesse:
   - Site do studio (serviços, preços, contato e botão de agendar): http://localhost:8080
   - Painel: http://localhost:8080/admin (usuário `admin`, senha `lash123`; troque em `application.properties` ou pela variável `LASH_ADMIN_SENHA`)
   - Chat das clientes: http://localhost:8080/chat.html

Sem a chave de API, o painel funciona normalmente. Só o chat responde que está com um problema técnico.

## Personalização

Em `src/main/resources/application.properties` você define o nome do studio, o endereço, os dias e horários de funcionamento, o intervalo entre horários e o endereço público do sistema (`studio.url-publica`), que vai no link de confirmação.
Serviços, preços e produtos podem ser editados direto no painel.

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

O código é organizado **por funcionalidade**: cada módulo tem o próprio pacote, e dentro dele ficam as camadas daquele módulo.

```
src/main/java/br/com/ysadepaula
├── YsaDePaulaApplication.java
├── agenda/          agendamentos, horários livres, confirmação e lembretes
│   ├── controller/  AgendaController
│   ├── dto/         NovoAgendamentoRequest, ConclusaoRequest, RemarcacaoRequest, LembreteConfirmacaoResponse
│   ├── model/       Agendamento, StatusAgendamento, OrigemAgendamento
│   ├── repository/  AgendamentoRepository
│   └── service/     AgendaService
├── atendimento/     atendente virtual (Bia) com o Claude
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
src/main/resources/static   site (index.html), login, painel (admin/), chat, CSS e JS
```

Regras seguidas:
- Um módulo pode usar o `service` ou o `model` de outro (a agenda lança no financeiro, por exemplo), mas nunca o `controller` de outro.
- Entidades JPA ficam em `model`; o que entra e sai da API e não é entidade fica em `dto` (records).
- Enums ficam em arquivos próprios no `model` do módulo.

Localmente, os dados ficam em um banco H2 em arquivo, na pasta `dados/`. Faça backup dessa pasta. Na internet, o sistema usa o perfil `producao` com o PostgreSQL do Supabase (veja abaixo).

## Colocar no ar de graça (Render + Supabase)

O sistema roda no **Render** e guarda os dados no **Supabase** (PostgreSQL), os dois no plano gratuito. O `Dockerfile` diz como compilar e iniciar o sistema, e o `render.yaml` monta o serviço no Render.

### 1. Banco no Supabase

1. Crie uma conta em https://supabase.com e um projeto novo (plano Free). Na região, escolha **East US (North Virginia)**, a mesma do Render; assim o sistema fica mais rápido. Anote a senha do banco.
2. No projeto, clique em **Connect** e escolha **Session pooler**. Anote:
   - **host** (algo como `aws-0-us-east-1.pooler.supabase.com`)
   - **user** (algo como `postgres.abcdefghijk`)

Use o *Session pooler*, e não a conexão direta: no plano gratuito, a conexão direta só funciona por IPv6.
As tabelas são criadas sozinhas na primeira vez que o sistema sobe.

### 2. Sistema no Render

1. Crie uma conta em https://render.com usando o login do GitHub (não pede cartão).
2. Clique em **New → Blueprint**, escolha o repositório `ysa-de-paula-studio` e confirme. O Render lê o `render.yaml` e pede os valores:

   | Variável | Valor |
   |---|---|
   | `DB_HOST` | host do Session pooler |
   | `DB_USUARIO` | user do Session pooler |
   | `DB_SENHA` | senha do banco do Supabase |
   | `LASH_ADMIN_SENHA` | senha forte para o painel |
   | `ANTHROPIC_API_KEY` | chave da Anthropic (para a Bia responder) |
   | `URL_PUBLICA` | deixe em branco por enquanto |

3. Espere a primeira publicação terminar (alguns minutos). O endereço aparece no topo do serviço, algo como `https://ysa-de-paula-studio.onrender.com`.
4. Em **Environment**, preencha `URL_PUBLICA` com esse endereço. Ele vai no link de confirmação pelo WhatsApp.
5. A cada `push` na `main`, o Render publica a nova versão sozinho.

### 3. Manter acordado (opcional, também grátis)

No plano gratuito, o Render "dorme" depois de 15 minutos sem acesso, e o próximo acesso demora cerca de 1 minuto. Para evitar isso:

1. Crie uma conta em https://uptimerobot.com.
2. **Add New Monitor → HTTP(s)**, com o endereço do sistema e intervalo de **5 minutos**.

As 750 horas mensais do plano gratuito do Render cobrem um serviço ligado o mês inteiro.

Bom saber:
- O plano gratuito do Supabase tem 500 MB, que sobra para o studio, e pausa projetos que ficam uma semana sem nenhum acesso. Com o sistema no ar, isso não acontece.
- Os dados ficam no Supabase, então nada se perde quando o Render dorme ou republica. Só o login do painel e os bloqueios de senha errada, que ficam na memória, recomeçam.
- Faça backups pelo painel do Supabase (**Database → Backups**) ou com `pg_dump`.
- O mesmo `Dockerfile` serve para o Railway, que é pago, se um dia você quiser trocar.

## Testes

```powershell
.\mvnw.cmd test
```

## Próximos passos sugeridos

- Integrar com a API oficial do WhatsApp Business (Meta Cloud API), para a Bia responder direto no WhatsApp e mandar os lembretes sozinha.
- Baixar o estoque automaticamente pelos materiais usados em cada serviço.
