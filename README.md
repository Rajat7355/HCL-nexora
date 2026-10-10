# ShopSmart AI

AI-powered shopping platform: catalogue search and compare, accounts/cart/orders/tracking, policy Q&A (RAG with citations),
a shopping assistant with intent routing, and ops agents (inventory, fulfilment, support triage) with a full AI audit log.

**Stack:** Spring Boot 3.5 · Spring AI 1.0 · Java 21 · PostgreSQL 16 + pgvector · React 19 · Vite · Tailwind 4

## Run with Docker
```bash
cp .env.example .env        # optional: add OPENAI_API_KEY (any OpenAI-compatible endpoint)
docker compose up --build
```
App: http://localhost:3000 · API docs: http://localhost:8080/swagger-ui.html

Without an LLM key everything still works: intent parsing uses rules and policy answers are extractive quotes with sources.
For a fully offline start (no embedding model download) set `SHOPSMART_EMBEDDINGS=hash` and `SPRING_AI_MODEL_EMBEDDING=none`.

## Deploy a free demo to Render

The root `render.yaml` defines a Render Blueprint with a static React frontend, Docker-based Spring Boot API,
and PostgreSQL 16 database. To deploy it:

1. Push this project to a GitHub repository. Keep `.env` out of Git; add secrets only in the Render dashboard.
2. In Render, choose **New → Blueprint**, connect the repository, and apply the `render.yaml` Blueprint.
3. Wait for the database and API health check to pass, then open the `shopsmart-web` static-site URL.
4. To enable model-generated responses, set `OPENAI_API_KEY` (and, if needed, `OPENAI_BASE_URL` / `OPENAI_MODEL`)
   in the `shopsmart-api` service's environment settings. The assistant otherwise uses its deterministic and
   extractive fallbacks.
5. If you add a custom frontend domain, update `CORS_ORIGINS` on `shopsmart-api` to that exact origin and redeploy.

This free configuration is for evaluation, not production: Render's free PostgreSQL database expires after
30 days, free web services can sleep, and uploads stored under `/tmp` are ephemeral. Do not put important user
or business data in this demo. Use a paid persistent database and persistent storage before production use.

## Run locally
```bash
docker compose up db                       # Postgres + pgvector
cd backend && mvn -pl app -am spring-boot:run
cd frontend && npm install && npm run dev  # http://localhost:5173
cd frontend && npm run dev:mock            # UI only, no backend needed
```

## Accounts
Create an account at `/register`, then log in at `/login`. Logging out revokes the server session and clears the local session. Select Basic, Prime or Premium from the profile page; membership changes are immediate and do not collect payment.

All catalog, cart, order and membership prices are shown in INR (₹). Existing USD catalog and order amounts are converted using the migration's documented reference rate.
Catalog seed data: 108 products in 8 categories and 3 warehouses. Sample policy documents are loaded by default.
Demo accounts and their sample orders are not created unless `SHOPSMART_DEMO_USERS=true` is explicitly enabled for an isolated test database.

## Try this
- Chat widget: "gaming laptop under ₹100000", "What is the return window for laptops?", "Where is my order?"
- Admin → Agents → run the inventory agent → approve the restock proposal; see Alerts and AI audit.
- Admin → Knowledge base → upload a PDF/Markdown policy; ask about it in Help centre.

## Layout
```
backend/  kit (shared: security, errors, LLM/embedding gateways, audit)
          catalog · commerce · knowledge · assistant   (one vertical slice each; own tables, own migrations)
          app (wiring, application.yml, demo seed)
frontend/src/features/{catalog,account,knowledge,assistant}   routes/nav/widgets auto-discovered
```
Slices only talk through the database views (`v_product_availability`, `v_sales_velocity`, `v_order_tracking`) and the
`PolicyAnswerService` interface. `ai_decision_logs` is written only via `AuditLogger`. See `.github/CODEOWNERS`.

## Business rules
BASIC pays ₹580.23 shipping under ₹9,686.72; PRIME ships free; PREMIUM ships free plus 5% off. Stock is taken by a DB trigger when
an order item is inserted (oversell raises `INSUFFICIENT_STOCK`) and restored on cancel. Orders can be cancelled until PACKED.
# HCL-nexora
