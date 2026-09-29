package br.com.jmcodestudio.agendamento.servico;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServicoRepository {

    // Map para armazenar os serviços em memória, usando UUID como chave e Servico como valor
    // ConcurrentHashMap é usado para permitir acesso seguro em ambientes multithread, servidor HTTP é multi-thread
    private final Map<UUID, Servico> banco = new ConcurrentHashMap<>();

    // insere no Map. Se o ID já existe, sobrescreve
    public Servico salvar(Servico servico) {
        banco.put(servico.id(), servico);
        return servico;
    }

    // busca um serviço pelo ID.Retornar null é anti-padrão universal em Java moderno
    public Optional<Servico> buscarPorId(UUID id) {
        return Optional.ofNullable(banco.get(id));
    }

    // Criando uma nova ArrayList a partir dos values, retornamos uma cópia. Modificações nela não afetam o repositório
    public List<Servico> listarTodos() {
        return new ArrayList<>(banco.values());
    }

    // existePorId vai ser usado antes de operações que assumem existência
    public boolean existePorId(UUID id) {
        return banco.containsKey(id);
    }

    // quantidade ajuda em métricas e testes
    public int quantidade() {
        return banco.size();
    }

    public List<Servico> listarAtivos() {
        return banco.values().stream()
                .filter(Servico::estaAtivo)
                .toList();
    }

    public List<Servico> listarInativos() {
        return banco.values().stream()
                .filter(s -> !s.estaAtivo())
                .toList();
    }

    public List<Servico> paginar(List<Servico> origem, int page, int size) {
        int inicio = page * size;
        if (inicio >= origem.size()) {
            return List.of();
        }
        int fim = Math.min(inicio + size, origem.size());
        return origem.subList(inicio, fim);
    }
}