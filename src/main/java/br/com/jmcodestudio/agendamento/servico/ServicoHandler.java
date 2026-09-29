package br.com.jmcodestudio.agendamento.servico;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// implements HttpHandler interface do JDK que define o contrato de qualquer handler HTTP - só tem o método handle
public class ServicoHandler implements HttpHandler {

    // o handler NÃO CRIA o repositório dentro dele. RECEBE de fora
    // Injetando por construtor, o Main decide qual instância passar.
    // Se for teste, passa mock. Se for produção, passa a real. Se for banco, passa a com JDBC. O handler não sabe e não se importa.
    // Isso é DI manual — o que Spring vai automatizar depois via @Autowired.
    private final ServicoRepository repositorio;

    public ServicoHandler(ServicoRepository repositorio) {
        this.repositorio = repositorio;
    }

    // Este é o "roteador manual". Baseado no verbo HTTP e no path, decide qual método privado chamar.
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String metodo = exchange.getRequestMethod();
        URI uri = exchange.getRequestURI();
        String path = uri.getPath();

        // Repara na cascata de if/else — isso é feio e vai piorar com muitos endpoints.
        // Spring resolve isso com anotações (@PostMapping, @GetMapping). Aqui fazemos à mão pra sedimentar.
        try {
            if (metodo.equals("POST") && path.equals("/servicos")) {
                criar(exchange);
            } else if (metodo.equals("GET") && path.equals("/servicos")) {
                listar(exchange);
            } else if (metodo.equals("PATCH") && path.startsWith("/servicos/") && !path.endsWith("/desativar") && !path.endsWith("/reativar")) {
                atualizar(exchange, path);
            } else if (metodo.equals("POST") && path.endsWith("/desativar")) {
                desativar(exchange, path);
            } else if (metodo.equals("POST") && path.endsWith("/reativar")) {
                reativar(exchange, path);
            } else if (metodo.equals("GET") && path.startsWith("/servicos/")) {
                buscarPorId(exchange, path);
            } else {
                responderErro(exchange, 405, "Método não permitido para este recurso");
            }
        } catch (IllegalArgumentException e) {
            responderErro(exchange, 422, e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            responderErro(exchange, 500, "Erro interno do servidor");
        }
    }

    // Lê o body como UTF-8, extrai campo por campo com nosso parser manual,
    // cria o Servico (que valida invariantes internamente),
    // salva no repositório e devolve JSON com 201 Created — código correto pra criação
    private void criar(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String nome = extrairCampoTexto(body, "nome");
        String duracaoTexto = extrairCampoTexto(body, "duracaoMinutos");
        String precoTexto = extrairCampoTexto(body, "preco");
        String moeda = extrairCampoTexto(body, "moeda");

        if (nome == null || duracaoTexto == null || precoTexto == null || moeda == null) {
            responderErro(exchange, 400, "Campos obrigatórios: nome, duracaoMinutos, preco, moeda");
            return;
        }

        int duracao = Integer.parseInt(duracaoTexto);
        BigDecimal preco = new BigDecimal(precoTexto);

        Servico novo = new Servico(
                UUID.randomUUID(),
                nome,
                duracao,
                preco,
                moeda,
                OffsetDateTime.now(),
                null
        );

        repositorio.salvar(novo);

        String json = servicoParaJson(novo);
        responder(exchange, 201, json);
    }

    // Listar e buscar por ID
    private void listar(HttpExchange exchange) throws IOException {
        String query = exchange.getRequestURI().getQuery();
        String filtro = extrairQueryParam(query, "ativos");
        String pageParam = extrairQueryParam(query, "page");
        String sizeParam = extrairQueryParam(query, "size");

        int page = 0;
        int size = 20;
        try {
            if (pageParam != null) page = Integer.parseInt(pageParam);
            if (sizeParam != null) size = Integer.parseInt(sizeParam);
        } catch (NumberFormatException e) {
            responderErro(exchange, 400, "'page' e 'size' devem ser números inteiros");
            return;
        }

        if (page < 0 || size <= 0 || size > 100) {
            responderErro(exchange, 400, "'page' >= 0; 'size' entre 1 e 100");
            return;
        }

        List<Servico> todos;
        if (filtro == null || filtro.equals("true")) {
            todos = repositorio.listarAtivos();
        } else if (filtro.equals("false")) {
            todos = repositorio.listarInativos();
        } else if (filtro.equals("all")) {
            todos = repositorio.listarTodos();
        } else {
            responderErro(exchange, 400, "Valor inválido para 'ativos'. Use: true, false, all");
            return;
        }

        List<Servico> pagina = repositorio.paginar(todos, page, size);

        int totalElements = todos.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        StringBuilder json = new StringBuilder("{\"data\":[");
        for (int i = 0; i < pagina.size(); i++) {
            json.append(servicoParaJson(pagina.get(i)));
            if (i < pagina.size() - 1) {
                json.append(",");
            }
        }
        json.append("],\"pagination\":{")
                .append("\"page\":").append(page).append(",")
                .append("\"size\":").append(size).append(",")
                .append("\"totalElements\":").append(totalElements).append(",")
                .append("\"totalPages\":").append(totalPages)
                .append("}}");

        responder(exchange, 200, json.toString());
    }

    private void buscarPorId(HttpExchange exchange, String path) throws IOException {
        UUID id = extrairIdDoPath(path, "/servicos/");
        if (id == null) {
            responderErro(exchange, 400, "ID inválido — deve ser UUID");
            return;
        }

        Optional<Servico> encontrado = repositorio.buscarPorId(id);

        if (encontrado.isEmpty()) {
            responderErro(exchange, 404, "Serviço não encontrado");
            return;
        }

        responder(exchange, 200, servicoParaJson(encontrado.get()));
    }

    // Quando adicionarmos Jackson, esse método some
    private String extrairCampoTexto(String json, String campo) {
        String marcador = "\"" + campo + "\"";
        int inicio = json.indexOf(marcador);
        if (inicio == -1) {
            return null;
        }
        int posDoisPontos = json.indexOf(":", inicio);
        int posValor = posDoisPontos + 1;

        while (posValor < json.length() && (json.charAt(posValor) == ' ' || json.charAt(posValor) == '"')) {
            posValor++;
        }

        int fim = posValor;
        while (fim < json.length()
                && json.charAt(fim) != '"'
                && json.charAt(fim) != ','
                && json.charAt(fim) != '}') {
            fim++;
        }

        return json.substring(posValor, fim).trim();
    }

    // monta o JSON literal. Feio mas funcional.
    private String servicoParaJson(Servico s) {
        return "{"
                + "\"id\":\"" + s.id() + "\","
                + "\"nome\":\"" + s.nome() + "\","
                + "\"duracaoMinutos\":" + s.duracaoMinutos() + ","
                + "\"preco\":\"" + s.preco() + "\","
                + "\"moeda\":\"" + s.moeda() + "\","
                + "\"criadoEm\":\"" + s.criadoEm() + "\","
                + "\"dataInativacao\":" + (s.dataInativacao() == null ? "null" : "\"" + s.dataInativacao() + "\"")
                + "}";
    }

    private void atualizar(HttpExchange exchange, String path) throws IOException {
        UUID id = extrairIdDoPath(path, "/servicos/");
        if (id == null) {
            responderErro(exchange, 400, "ID inválido — deve ser UUID");
            return;
        }

        Optional<Servico> encontrado = repositorio.buscarPorId(id);
        if (encontrado.isEmpty()) {
            responderErro(exchange, 404, "Serviço não encontrado");
            return;
        }

        Servico atual = encontrado.get();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String novoNome = extrairCampoTexto(body, "nome");
        String novaDuracaoTexto = extrairCampoTexto(body, "duracaoMinutos");
        String novoPrecoTexto = extrairCampoTexto(body, "preco");
        String novaMoeda = extrairCampoTexto(body, "moeda");

        Servico atualizado = new Servico(
                atual.id(),
                novoNome != null ? novoNome : atual.nome(),
                novaDuracaoTexto != null ? Integer.parseInt(novaDuracaoTexto) : atual.duracaoMinutos(),
                novoPrecoTexto != null ? new BigDecimal(novoPrecoTexto) : atual.preco(),
                novaMoeda != null ? novaMoeda : atual.moeda(),
                atual.criadoEm(),
                atual.dataInativacao()
        );

        repositorio.salvar(atualizado);
        responder(exchange, 200, servicoParaJson(atualizado));
    }

    private void desativar(HttpExchange exchange, String path) throws IOException {
        UUID id = extrairIdDoPath(path, "/servicos/", "/desativar");
        if (id == null) {
            responderErro(exchange, 400, "ID inválido — deve ser UUID");
            return;
        }

        Optional<Servico> encontrado = repositorio.buscarPorId(id);
        if (encontrado.isEmpty()) {
            responderErro(exchange, 404, "Serviço não encontrado");
            return;
        }

        Servico atual = encontrado.get();

        if (!atual.estaAtivo()) {
            responderErro(exchange, 409, "Serviço já está inativo");
            return;
        }

        Servico desativado = new Servico(
                atual.id(),
                atual.nome(),
                atual.duracaoMinutos(),
                atual.preco(),
                atual.moeda(),
                atual.criadoEm(),
                OffsetDateTime.now()
        );

        repositorio.salvar(desativado);
        responder(exchange, 200, servicoParaJson(desativado));
    }

    private void reativar(HttpExchange exchange, String path) throws IOException {
        UUID id = extrairIdDoPath(path, "/servicos/", "/reativar");
        if (id == null) {
            responderErro(exchange, 400, "ID inválido — deve ser UUID");
            return;
        }

        Optional<Servico> encontrado = repositorio.buscarPorId(id);
        if (encontrado.isEmpty()) {
            responderErro(exchange, 404, "Serviço não encontrado");
            return;
        }

        Servico atual = encontrado.get();

        if (atual.estaAtivo()) {
            responderErro(exchange, 409, "Serviço já está ativo");
            return;
        }

        Servico reativado = new Servico(
                atual.id(),
                atual.nome(),
                atual.duracaoMinutos(),
                atual.preco(),
                atual.moeda(),
                atual.criadoEm(),
                null
        );

        repositorio.salvar(reativado);
        responder(exchange, 200, servicoParaJson(reativado));
    }

    // utilitários pra escrever a resposta HTTP com headers e status corretos

    private void responder(HttpExchange exchange, int status, String corpo) throws IOException {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void responderErro(HttpExchange exchange, int status, String mensagem) throws IOException {
        String json = "{\"erro\":\"" + mensagem + "\"}";
        responder(exchange, status, json);
    }

    private UUID extrairIdDoPath(String path, String prefixo) {
        String idTexto = path.substring(prefixo.length());
        try {
            return UUID.fromString(idTexto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private UUID extrairIdDoPath(String path, String prefixo, String sufixo) {
        if (!path.endsWith(sufixo)) {
            return null;
        }
        String idTexto = path.substring(prefixo.length(), path.length() - sufixo.length());
        try {
            return UUID.fromString(idTexto);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String extrairQueryParam(String query, String param) {
        if (query == null) {
            return null;
        }
        String[] pares = query.split("&");
        for (String par : pares) {
            String[] chaveValor = par.split("=", 2);
            if (chaveValor.length == 2 && chaveValor[0].equals(param)) {
                return chaveValor[1];
            }
        }
        return null;
    }
}