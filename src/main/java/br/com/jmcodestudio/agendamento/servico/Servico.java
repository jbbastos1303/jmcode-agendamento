package br.com.jmcodestudio.agendamento.servico;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Servico(
        UUID id,
        String nome,
        int duracaoMinutos,
        BigDecimal preco,
        String moeda,
        OffsetDateTime criadoEm,
        OffsetDateTime dataInativacao
) {
    public Servico {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do serviço é obrigatório");
        }
        if (duracaoMinutos <= 0) {
            throw new IllegalArgumentException("Duração deve ser positiva");
        }
        if (preco == null || preco.signum() < 0) {
            throw new IllegalArgumentException("Preço não pode ser negativo");
        }
        if (moeda == null || moeda.length() != 3) {
            throw new IllegalArgumentException("Moeda deve seguir ISO 4217 (3 letras)");
        }
    }

    public boolean estaAtivo() {
        return dataInativacao == null;
    }
}