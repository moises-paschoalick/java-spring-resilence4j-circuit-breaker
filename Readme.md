# Publication Service — Circuit Breaker com Resilience4j

Projeto de estudo sobre o padrão **Circuit Breaker** aplicado a microsserviços com Spring Boot e Resilience4j. Desenvolvido seguindo o curso do **Dev de Olho**.

O serviço `publication` consulta seu próprio banco MongoDB e enriquece cada publicação com comentários vindos de um serviço externo (`comment`). Quando esse serviço externo falha ou fica lento, o circuit breaker entra em ação para proteger a aplicação e degradar de forma graciosa.

## O padrão Circuit Breaker

O Circuit Breaker melhora a resiliência e a estabilidade de sistemas distribuídos, evitando o efeito cascata (dominó) quando um serviço fica indisponível. Funciona como um disjuntor elétrico, alternando entre três estados:

| Estado | Comportamento |
|--------|---------------|
| **CLOSED** (fechado) | As requisições passam normalmente para o serviço de comentários. |
| **OPEN** (aberto) | O serviço falhou além do limite. As chamadas são bloqueadas e o fallback responde imediatamente, sem nem tentar a conexão. |
| **HALF_OPEN** (semi-aberto) | Após o tempo de espera, deixa passar algumas requisições de teste. Se voltarem com sucesso, fecha o circuito; se falharem, abre de novo. |

Transições típicas observadas nos logs:

```
CircuitBreaker 'comments' changed state from CLOSED to OPEN
CircuitBreaker 'comments' changed state from OPEN to HALF_OPEN
CircuitBreaker 'comments' changed state from HALF_OPEN to OPEN
CircuitBreaker 'comments' changed state from OPEN to HALF_OPEN
CircuitBreaker 'comments' changed state from HALF_OPEN to CLOSED
```

## Arquitetura

```
┌──────────────┐      ┌──────────────────┐      ┌──────────────┐
│              │ ───> │  Circuit Breaker │ ───> │              │
│  Publication │      │  CLOSED / OPEN / │      │   Comment    │
│   (8080)     │ <─── │    HALF_OPEN     │ <─── │   (8082)     │
└──────┬───────┘      └────────┬─────────┘      └──────┬───────┘
       │                       │                       │
   ┌───▼───┐               ┌───▼───┐               ┌───▼───┐
   │MongoDB│               │ Redis │  (fallback)   │  ...  │
   └───────┘               └───────┘               └───────┘
```

Quando o circuito está **OPEN**, o fallback responde com uma lista de comentários vazia (ou, numa evolução, a partir de um cache em Redis), mantendo a publicação disponível mesmo sem o serviço de comentários.

## Stack

- **Java 17**
- **Spring Boot 4.1.0**
- **Spring Cloud** — Circuit Breaker (Resilience4j) e OpenFeign
- **Resilience4j** — implementação do circuit breaker
- **Spring Data MongoDB** — persistência das publicações
- **Spring Data Redis (Jedis)** — cache para o fallback
- **MapStruct** — mapeamento entre entidades, domínios e DTOs
- **Lombok** — redução de boilerplate
- **Docker Compose** — MongoDB, Mongo Express e Redis
- **WireMock** — mock do serviço de comentários para simular falhas e lentidão

## Pré-requisitos

- JDK 17
- Maven
- Docker e Docker Compose
- WireMock standalone (`wiremock-standalone-3.13.2.jar`)

## Como rodar

### 1. Suba a infraestrutura

```bash
docker compose up -d
```

Isso levanta:
- **MongoDB** na porta `27017` (usuário `root`, senha `example`)
- **Mongo Express** (interface web)
- **Redis** na porta `6379`

### 2. Crie a base de dados no MongoDB

Conecte via `mongosh`:

```bash
docker compose exec mongo mongosh "mongodb://root:example@localhost:27017/circuit-breaker?authSource=admin"
```

```javascript
use circuit-breaker
show collections
```

> Atenção ao `authSource=admin` na URI — sem ele, a autenticação falha com `error 13 (Unauthorized)`.

### 3. Suba o mock do serviço de comentários (WireMock)

```bash
java -jar wiremock-standalone-3.13.2.jar --port 8082
```

Na pasta `mappings/`, coloque o JSON do stub e reinicie o WireMock:

```json
{
  "request": {
    "method": "GET",
    "url": "/comments/6a2ebb1ae004b8814cee5a05"
  },
  "response": {
    "status": 200,
    "headers": { "Content-Type": "application/json" },
    "jsonBody": [
      { "author": "João", "text": "Muito bom o curso do Dev de Olho" },
      { "author": "Danilo", "text": "Excelente!" }
    ]
  }
}
```

### 4. Rode a aplicação

```bash
mvn clean spring-boot:run
```

A API sobe em `http://localhost:8080`.

## Endpoints

| Método | Rota | Descrição |
|--------|------|-----------|
| `POST` | `/api/v1/publications` | Cria uma publicação |
| `GET` | `/api/v1/publications` | Lista todas as publicações |
| `GET` | `/api/v1/publications/{id}` | Busca uma publicação e a enriquece com comentários |

Exemplo de requisição:

```bash
curl --location 'http://localhost:8080/api/v1/publications/6a2ebb1ae004b8814cee5a05'
```

Resposta:

```json
{
  "id": "6a2ebb1ae004b8814cee5a05",
  "title": "Dev de olho",
  "imageUrl": "http://localhost:8080/image.png",
  "text": "Onde você encontra tudo sobre programação.",
  "comments": [
    { "author": "João", "text": "Muito bom o curso do Dev de Olho" },
    { "author": "Danilo", "text": "Top mesmo esse curso!" }
  ]
}
```

## Configuração do Circuit Breaker

A instância `comments` é configurada no `application.yml`:

```yaml
resilience4j.circuitbreaker:
  instances:
    comments:
      slidingWindowSize: 8        # monitora as últimas 8 requisições
      minimumNumberOfCalls: 4     # só avalia após 4 chamadas
      failureRateThreshold: 50    # abre se 50% ou mais falharem
      slowCallDurationThreshold: 2s   # chamada acima de 2s é considerada lenta
      slowCallRateThreshold: 50       # abre se 50% forem lentas
```

O circuit breaker é aplicado no `CommentService`, isolando apenas a chamada externa — assim, uma falha no serviço de comentários não derruba a leitura da publicação:

```java
@CircuitBreaker(name = "comments", fallbackMethod = "getCommentsFallback")
public List<Comment> getComments(String id) {
    return commentClient.getComments(id);
}

public List<Comment> getCommentsFallback(String id, Throwable cause) {
    log.warn("[WARN] fallback with id {}", id);
    return List.of();
}
```

> O método de fallback precisa ter a **mesma assinatura** do método anotado, acrescido de um `Throwable` no final.

## Testando o circuito

Para ver o circuito abrir por **falha**, derrube o WireMock e faça chamadas repetidas:

```bash
for i in {1..6}; do curl -s http://localhost:8080/api/v1/publications/6a2ebb1ae004b8814cee5a05; echo; done
```

Para ver o circuito abrir por **lentidão**, adicione um delay no stub do WireMock:

```json
"response": {
  "fixedDelayMilliseconds": "3000",
  ...
}
```

## Observação importante sobre o setup

Para que a anotação `@CircuitBreaker` (implementada como um `@Aspect` do AspectJ) seja efetivamente aplicada, o `aspectjweaver` precisa estar no classpath. Sem ele, o Spring Boot não habilita o AspectJ auto-proxy, e a anotação é **ignorada silenciosamente** — o circuito nunca conta as falhas nem abre.

Garanta esta dependência no `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
```

## Licença

Projeto de estudo, sem fins comerciais.