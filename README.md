# Sistema de Agendamento — Módulo 0

> API REST em Java 21, construída sem frameworks, como projeto de fundamentos do curso de Arquitetura de Software.

[![Java](https://img.shields.io/badge/Java-21_LTS-orange)](https://openjdk.org/projects/jdk/21/)
[![Build](https://img.shields.io/badge/Build-Gradle_8.10-blue)](https://gradle.org/)
[![Status](https://img.shields.io/badge/Status-Módulo_0-yellow)](#)

---

## 📋 Sobre o Projeto

Este é um recorte do módulo de fundamentos de um curso pessoal de Arquitetura de Software. Implementa a **entidade `Serviço`** do domínio "Sistema de Agendamento para barbearia/salão", propositalmente **sem framework algum** — apenas o JDK padrão do Java 21.

O objetivo é sedimentar fundamentos de HTTP, REST, JSON e padrões arquiteturais **antes** de introduzir frameworks como Spring Boot. A ideia é que, ao usar Spring depois, cada anotação (`@RestController`, `@RequestBody`, `@PatchMapping`) tenha significado real — não seja "mágica".

### Escopo desta versão

Esta implementação cobre **apenas a entidade `Serviço`**. As demais entidades do sistema completo (Cliente, Barbeiro, Feriado, Agendamento) foram modeladas em detalhe mas **não implementadas neste repositório** — serão desenvolvidas em projeto separado após conclusão dos módulos de Design Patterns, Spring Boot e DDD/Hexagonal.

---

## 🎯 Decisões Arquiteturais

### Sem framework

Nada de Spring, Quarkus, Micronaut. Apenas `com.sun.net.httpserver.HttpServer` (JDK padrão). Motivo pedagógico:

- **Entender o problema antes da solução:** frameworks resolvem problemas específicos (roteamento, injeção de dependência, serialização). Sem contexto do problema, o framework vira caixa-preta.
- **Fundamentos sólidos:** HTTP, REST, JSON, threads, concorrência — tudo à mão.

**Referência:** Robert C. Martin, *Clean Architecture* (2017) — arquitetura decide antes do framework, não o contrário.

### Package by Feature

Pacotes organizados por **recurso de negócio**, não por camada técnica:

br.com.jmcodestudio.agendamento
├── servico/ ← tudo relacionado a Serviço junto
│ ├── Servico.java
│ ├── ServicoRepository.java
│ └── ServicoHandler.java
├── shared/ ← utilitários compartilhados (futuro)
└── Main.java ← Composition Root


**Referência:** Simon Brown, C4 Model — [c4model.com](https://c4model.com/); Robert C. Martin, *Clean Architecture* (2017), cap. 21.

### Record imutável com invariantes protegidas

`Servico` é um Record Java 21 que valida suas próprias invariantes no construtor. Nome vazio, preço negativo, moeda fora do padrão ISO 4217 são impossíveis por design.

```java
public record Servico(UUID id, String nome, int duracaoMinutos, ...) {
    public Servico {
        if (nome == null || nome.isBlank()) {
            throw new IllegalArgumentException("Nome do serviço é obrigatório");
        }
        // ...
    }
}
```

**Referência:** Eric Evans, *Domain-Driven Design* (2003), cap. 5 — Entities protegem invariantes.

### Soft delete via sub-recurso de ação

Serviço nunca é deletado — é **desativado** (`dataInativacao` marca o momento). Isso preserva histórico e permite reversão.

POST /servicos/{id}/desativar
POST /servicos/{id}/reativar


**Referência:** Roy Fielding, tese (2000) — sub-recursos de ação para transições de estado; Martin Fowler, *Bi-Temporal History* — histórico auditável.

### Persistência em memória

`ConcurrentHashMap` em vez de banco de dados. Escolha didática para este módulo. A **interface** do `ServicoRepository` foi desenhada de forma que, em uma futura evolução, a troca por banco relacional (Postgres) exigirá alterar apenas a implementação — nenhum código consumidor precisa mudar.

**Referência:** Martin Fowler, *Patterns of Enterprise Application Architecture* (2002) — Repository Pattern.

---

## 🚀 Como Rodar

### Pré-requisitos

- **Java 21 LTS** ou superior ([download](https://adoptium.net/temurin/releases/?version=21))
- **Gradle 8.10+** (o wrapper incluído dispensa instalação global)

### Passos

Clone o repositório e execute:

```bash
git clone https://github.com/jbbastos1303/jmcode-agendamento.git
cd jmcode-agendamento

# Windows
.\gradlew run

# Linux/Mac
./gradlew run
```

O servidor sobe em `http://localhost:8080`.

### Health check

```bash
curl http://localhost:8080/health
# {"status":"UP"}
```

---

## 📡 Endpoints Disponíveis

### Serviço

| Verbo | URI | Descrição | Códigos |
|---|---|---|---|
| `POST` | `/servicos` | Cria um serviço | 201, 400, 422 |
| `GET` | `/servicos` | Lista serviços (com filtros e paginação) | 200, 400 |
| `GET` | `/servicos/{id}` | Detalhes de um serviço | 200, 400, 404 |
| `PATCH` | `/servicos/{id}` | Atualização parcial | 200, 400, 404, 422 |
| `POST` | `/servicos/{id}/desativar` | Soft delete | 200, 400, 404, 409 |
| `POST` | `/servicos/{id}/reativar` | Reverte soft delete | 200, 400, 404, 409 |

### Filtros e paginação

GET /servicos → serviços ativos (default), primeira página, 20 itens
GET /servicos?ativos=false → só inativos
GET /servicos?ativos=all → todos
GET /servicos?page=0&size=10 → paginação customizada (max size = 100)


### Sistema

| Verbo | URI | Descrição |
|---|---|---|
| `GET` | `/health` | Verifica se o servidor está no ar |

---

## 📝 Exemplos de Uso

### Criar um serviço

**Request:**
```http
POST /servicos
Content-Type: application/json

{
    "nome": "Corte simples",
    "duracaoMinutos": 30,
    "preco": "40.00",
    "moeda": "BRL"
}
```

**Response (201 Created):**
```json
{
    "id": "0ab2a889-31a6-447e-82b4-7892f59c8167",
    "nome": "Corte simples",
    "duracaoMinutos": 30,
    "preco": "40.00",
    "moeda": "BRL",
    "criadoEm": "2026-09-29T16:48:22.928-03:00",
    "dataInativacao": null
}
```

### Listar com paginação

**Request:**
```http
GET /servicos?page=0&size=2
```

**Response (200 OK):**
```json
{
    "data": [
        { "id": "...", "nome": "Corte simples", ... },
        { "id": "...", "nome": "Corte feminino", ... }
    ],
    "pagination": {
        "page": 0,
        "size": 2,
        "totalElements": 3,
        "totalPages": 2
    }
}
```

### Erros

Todos os erros retornam JSON no formato:

```json
{ "erro": "Descrição legível do problema" }
```

**Códigos utilizados:**

| Código | Significado | Exemplo |
|---|---|---|
| `400` | Formato inválido (JSON malformado, UUID inválido, campo obrigatório ausente) | UUID `abc-123` no path |
| `404` | Recurso não existe | UUID válido mas serviço não cadastrado |
| `405` | Método HTTP não suportado neste recurso | `DELETE /servicos` |
| `409` | Conflito de estado (regra de negócio violada) | Desativar serviço já inativo |
| `422` | Semântica inválida (invariante de domínio violada) | Preço negativo, nome vazio |
| `500` | Erro interno | Bug não previsto |

**Referência:** RFC 7231 e RFC 7807 (Problem Details for HTTP APIs).

---

## 🛠️ Stack Técnica

| Categoria | Tecnologia |
|---|---|
| Linguagem | Java 21 LTS |
| Build | Gradle 8.10 com Kotlin DSL |
| HTTP | `com.sun.net.httpserver.HttpServer` (JDK) |
| Persistência | Em memória (`ConcurrentHashMap`) |
| Testes manuais | Bruno (coleção incluída em `/bruno`) |

**Zero dependências externas.** Toda a funcionalidade usa apenas o JDK padrão.

---

## 🔬 Trade-offs Conhecidos (Débito Técnico Consciente)

Este projeto **assume limitações intencionais** para servir ao propósito didático:

| Limitação | Motivo | Endereçado em |
|---|---|---|
| Parser JSON caseiro (frágil com JSON complexo) | Sentir problema antes de usar biblioteca | Jackson (Módulo 3 — Spring) |
| Dispatcher com cascata de `if/else` | Ver por que anotações declarativas existem | `@GetMapping` etc (Módulo 3) |
| Persistência volátil (perde tudo ao reiniciar) | Isolar padrão Repository de infraestrutura | JPA + Postgres (Módulo 4) |
| `Preco` inline em vez de Value Object | Simplicidade inicial | Refactoring (Módulo 1 — Padrões) |
| Sem testes automatizados | Foco em fundamentos HTTP/REST | JUnit + Testcontainers (Módulo 8) |
| Sem autenticação | Escopo mono-tenant | Spring Security (Módulo 3) |
| Timestamp com nanossegundos | Padrão do `OffsetDateTime.toString()` | Formatter customizado (Módulo 3) |

---

## 📚 Referências Bibliográficas

### Livros

- **Roy Fielding** — Architectural Styles and the Design of Network-based Software Architectures (Tese de doutorado, 2000)
- **Eric Evans** — Domain-Driven Design: Tackling Complexity in the Heart of Software (2003)
- **Martin Fowler** — Patterns of Enterprise Application Architecture (2002)
- **Robert C. Martin** — Clean Architecture (2017)
- **Vaughn Vernon** — Implementing Domain-Driven Design (2013)
- **Sam Newman** — Building Microservices (2ª ed., 2021)
- **Joshua Bloch** — Effective Java (3ª ed., 2018)
- **Brian Goetz** — Java Concurrency in Practice (2006)

### RFCs e Especificações

- RFC 7231 — HTTP/1.1 Semantics and Content
- RFC 7807 — Problem Details for HTTP APIs
- RFC 3339 — Date and Time on the Internet (ISO 8601 profile)
- ISO 4217 — Currency Codes

### Documentação Oficial

- [Java 21 HttpServer](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/)
- [Gradle Kotlin DSL](https://docs.gradle.org/current/userguide/kotlin_dsl.html)

### Referências de Comunidade (BR)

- Michelli Brito — cursos e vídeos sobre arquitetura de software
- Loiane Groner — Java e boas práticas

---

## 📄 Licença

MIT License — sinta-se livre para usar como referência de estudo.

---

## 👩‍💻 Autora

**Jéssica** — [JM Code Studio](https://jmcodestudio.com.br)

Projeto criado como parte de um plano estruturado de estudos em Arquitetura de Software.