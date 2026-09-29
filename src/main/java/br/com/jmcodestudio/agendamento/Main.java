package br.com.jmcodestudio.agendamento;

import br.com.jmcodestudio.agendamento.servico.ServicoHandler;
import br.com.jmcodestudio.agendamento.servico.ServicoRepository;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException {
        // cria o servidor HTTP na porta especificada
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Ordem importa: repositório primeiro, porque o handler precisa dele.
        // Composition Root — monta o grafo de dependências
        ServicoRepository servicoRepository = new ServicoRepository();
        ServicoHandler servicoHandler = new ServicoHandler(servicoRepository);

        // Health check
        // registra um handler pro path /health que retorna um JSON com status UP
        // exchange representa a requisição e a resposta HTTP
        server.createContext("/health", exchange -> {
            // JSON literal para indicar que o serviço está UP
            String response = "{\"status\":\"UP\"}";
            // converte a string de resposta para bytes
            byte[] responseBytes = response.getBytes();

            // define o tipo de conteúdo da resposta como JSON
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            // envia o código de status HTTP 200 (OK) e o tamanho da resposta
            exchange.sendResponseHeaders(200, responseBytes.length);

            // escreve os bytes da resposta no corpo da resposta HTTP e fecha o OutputStream
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        });

        // Recurso Serviço
        // ele registra o handler pra QUALQUER path que comece com /servicos.
        // Ou seja, tanto /servicos, /servicos/, /servicos/uuid-qualquer vão pro nosso handler.
        // É por isso que dentro do handle() a gente precisa checar o path exato pra decidir o que fazer
        server.createContext("/servicos", servicoHandler);

        // configura o executor do servidor como null, o que significa que ele usará um executor padrão
        server.setExecutor(null);
        // inicia o servidor HTTP
        server.start();

        System.out.println("Servidor rodando em http://localhost:" + PORT);
        System.out.println("Endpoints disponíveis:");
        System.out.println("  GET  /health");
        System.out.println("  POST /servicos");
        System.out.println("  GET  /servicos");
        System.out.println("  GET  /servicos/{id}");
    }
}