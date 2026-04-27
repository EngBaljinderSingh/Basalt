# Basalt 🪨

> A locally-hosted AI assistant — authoritative, efficient, and built for engineers.

Basalt is a full-stack AI chat application powered by a **local Ollama LLM**, with **RAG via PostgreSQL + pgvector**, **image generation via Pollinations.ai**, and a **Gemini-inspired Angular UI**. Zero paid APIs. Your data stays on your machine.

---

## Tech Stack

| Layer       | Technology                                      |
|-------------|-------------------------------------------------|
| Backend     | Java 17 · Spring Boot 3.2 · Spring AI 1.0.0-M6 |
| LLM Engine  | Ollama (llama3.1 / deepseek-r1)                 |
| Embeddings  | nomic-embed-text via Ollama                     |
| Vector DB   | PostgreSQL 16 + pgvector (HNSW)                 |
| Image Gen   | Pollinations.ai (free, no auth)                 |
| Frontend    | Angular 17 · Tailwind CSS · ngx-markdown        |
| Container   | Docker Compose                                  |

---

## Project Structure

```
Basalt/
├── docker-compose.yml              # Postgres + pgvector + Ollama
├── scripts/
│   └── init-pgvector.sql           # CREATE EXTENSION vector
├── docs/
│   └── architecture.md             # System architecture & data flows
│
├── basalt-backend/                 # Spring Boot application
│   ├── pom.xml
│   └── src/main/java/com/basalt/ai/
│       ├── BasaltApplication.java
│       ├── config/
│       │   ├── AiConfig.java       # ChatClient bean + system persona
│       │   └── CorsConfig.java     # CORS for Angular dev server
│       ├── controller/
│       │   ├── ChatController.java         # SSE streaming endpoint
│       │   ├── ImageController.java        # Pollinations.ai proxy
│       │   └── DocumentController.java     # RAG document ingestion
│       ├── service/
│       │   ├── ChatService.java            # Prompt building + Ollama stream
│       │   ├── RagService.java             # PgVector similarity search
│       │   └── DocumentIngestionService.java
│       ├── model/
│       │   ├── ChatRequest.java
│       │   └── ImageRequest.java
│       └── resources/
│           └── application.yml
│
└── basalt-frontend/                # Angular 17 application
    ├── package.json
    ├── angular.json
    ├── tailwind.config.js
    ├── proxy.conf.json             # Dev proxy → localhost:8080
    └── src/
        ├── main.ts
        ├── index.html
        ├── styles.scss             # Tailwind + highlight.js + prose
        ├── environments/
        │   ├── environment.ts
        │   └── environment.prod.ts
        └── app/
            ├── app.component.ts
            ├── app.config.ts       # provideMarkdown, provideHttpClient
            ├── app.routes.ts
            ├── core/
            │   ├── models/chat.model.ts
            │   └── services/chat.service.ts  # SSE streaming + image gen
            └── features/
                └── chat/
                    ├── chat.component.ts     # Main conversation view
                    └── components/
                        ├── message-bubble/   # Markdown rendering
                        └── chat-input/       # Auto-resize + RAG/image toggles
```

---

## Quick Start

### Option 1: Full Docker Deployment (Recommended)

**Prerequisites:** Docker Desktop only

1. **Start all services:**
   ```powershell
   .\start-docker.ps1
   ```

   This builds and runs everything: PostgreSQL, Ollama, Backend, and Frontend.

2. **Pull LLM models** (first-time setup):
   ```bash
   docker exec basalt-ollama ollama pull llama3.2:1b
   docker exec basalt-ollama ollama pull nomic-embed-text
   ```

3. **Access the app:**
   - **Frontend:** http://localhost:3000
   - **Backend API:** http://localhost:8080/api

**Stop all services:**
```bash
docker compose down
```

---

### Option 2: Local Development Mode

**Prerequisites:** Docker Desktop, Java 17+, Node.js 20+, Maven 3.9+

1. **Start infrastructure** (Postgres + Ollama):
   ```bash
   docker compose up -d postgres ollama
   ```

2. **Pull LLM models:**
   ```bash
   docker exec basalt-ollama ollama pull llama3.2:1b
   docker exec basalt-ollama ollama pull nomic-embed-text
   ```

3. **Start the backend:**
   ```bash
   cd basalt-backend
   mvn spring-boot:run
   ```
   Backend runs at `http://localhost:8080/api`

4. **Start the frontend:**
   ```bash
   cd basalt-frontend
   npm install
   npm start
   ```
   Frontend runs at `http://localhost:4200`

---

## Key API Endpoints

| Method | Path                        | Description                        |
|--------|-----------------------------|------------------------------------|
| POST   | `/api/chat/stream`          | Stream LLM response (SSE)          |
| POST   | `/api/images/generate`      | Generate image via Pollinations.ai |
| POST   | `/api/documents/upload/pdf` | Ingest PDF into RAG vector store   |
| POST   | `/api/documents/upload/text`| Ingest raw text into vector store  |
| GET    | `/api/actuator/health`      | Service health check               |

---

## Configuration

All settings live in `basalt-backend/src/main/resources/application.yml`:

```yaml
spring:
  ai:
    ollama:
      chat:
        model: llama3.1        # or deepseek-r1
      embedding:
        model: nomic-embed-text
    vectorstore:
      pgvector:
        dimensions: 768
        initialize-schema: true
```

---

## Architecture

See [`docs/architecture.md`](docs/architecture.md) for full component diagrams and data flow documentation.

---

## Basalt Persona

When responding, Basalt embodies a **Lead Software Engineer**: authoritative, precise, and focused on clean, scalable code. Every interaction uses this system prompt:

> *"You are Basalt, a Lead Software Engineer AI assistant. You are authoritative, precise, and focused on clean, scalable code. When answering technical questions, always prefer idiomatic solutions, cite trade-offs where relevant, and format code blocks with the correct language identifier."*

