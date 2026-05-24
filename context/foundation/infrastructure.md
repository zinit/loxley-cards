---
project: loxley-cards
researched_at: 2026-05-24
recommended_platform: Hetzner Cloud VPS
runner_up: Railway
context_type: mvp
tech_stack:
  language: java-21
  framework: spring-boot-4.0.6
  runtime: jvm
  database: postgresql-16 (managed by Supabase, EU region)
  frontend_host: cloudflare-pages
  mail_service: resend
  domain: loxley.cards
  vps_type: hetzner-cx22-fsn1
---

## Recommendation

**Backend on Hetzner Cloud VPS (CX22 in `fsn1` Falkenstein), database on Supabase (managed PostgreSQL), email via Resend, frontend on Cloudflare Pages, all under `loxley.cards`.**

The developer has strong ops familiarity with Hetzner VPS (Docker, systemd, nginx, certbot) and consciously accepts the operational overhead that managed PaaS platforms would absorb. EU data center location (Falkenstein) provides ~30ms latency for the target persona (PL-based users) — substantially better than the US-West default of Railway or Render. The cost (~$4/mo CX22) is competitive with Railway's Hobby tier while providing full pipeline control (Dockerfile + GitHub Actions), no vendor lock-in on build tooling, and unrestricted JVM resource allocation on 2 vCPU / 4 GB RAM.

**Supabase as managed PostgreSQL only** — connected via JDBC from Spring Boot (HikariCP pool + Supabase pgbouncer). Supabase Auth, Storage, Realtime and Edge Functions are explicitly NOT used; magic-link authentication is implemented natively in Spring Boot (token generation, validation, expiry) with email delivery via Resend. This removes self-hosted Postgres operational burden (backups, monitoring, exposure, vacuum tuning) without taking on Supabase BaaS dependency.

**Magic-link emails via Resend** — Daniel has prior familiarity. Free tier (3,000 emails/mo) covers MVP traffic with headroom.

**Frontend on Cloudflare Pages** — free tier, native git integration, global CDN. Routes to `loxley.cards` apex; backend exposed at `api.loxley.cards`.

The decision was made after a structured anti-bias cross-check that surfaced operational risks — all accepted with concrete mitigations documented in the risk register below.

**Total MVP cost: ~$4/mo** (Hetzner CX22 only — Supabase Free, Resend Free, Cloudflare Pages Free, domain already owned).

## Platform Comparison

| Criterion (weight) | Hetzner VPS | Railway | Render | Fly.io |
|---|---|---|---|---|
| **CLI-first** (critical) | Partial — `hcloud` for infra, custom scripts for app deploy | Pass — `railway up/logs/redeploy` | Partial — CLI GA, rollback API-only | Pass — `fly deploy/logs/releases` |
| **Managed/serverless** (critical) | Fail — raw VPS, app ops burden (DB now offloaded to Supabase) | Pass — managed containers | Pass — managed containers | Pass — Firecracker microVMs |
| **Agent-readable docs** (medium) | Partial — HTML docs, no `llms.txt` | Pass — `llms.txt`, GitHub markdown | Pass — `llms.txt`, `.md` URLs | Partial — GitHub source, no clean `llms.txt` |
| **Stable deploy API** (critical) | Fail — requires custom GH Actions pipeline | Pass — `railway up`, deterministic | Partial — deploy CLI ok, rollback REST-only | Pass — `fly deploy`, structured output |
| **MCP / integration** (light) | Partial — unofficial community MCP | Pass — GA local + remote MCP | Pass — GA MCP, 20+ tools | Partial — experimental `fly mcp server` |
| **Est. MVP cost/mo** | ~$4 (VPS only, DB external) | ~$5 (Hobby covers app + DB) | ~$13-31 ($25 for reliable JVM RAM) | ~$50 (managed PG dominates) |

### Shortlisted Platforms

#### 1. Hetzner Cloud VPS (Chosen)

Wins on: EU region proximity (Falkenstein), developer familiarity, full control over pipeline and runtime, no vendor lock-in on build tooling, unrestricted resource allocation. Fails on managed/serverless and stable deploy API — both consciously accepted with documented mitigations (custom GitHub Actions pipeline, Certbot for TLS, unattended-upgrades with Docker package pinning). The decision to offload PostgreSQL to **Supabase managed** removes the largest chunk of the original VPS ops burden (backups, monitoring, vacuum tuning, port exposure risk).

#### 2. Railway (Runner-up)

Best agent-friendliness score overall: cheapest managed option ($5/mo Hobby), auto-detects Spring Boot via Railpack, strongest MCP integration (GA local + remote server), excellent agent docs (`llms.txt`). Lost to Hetzner on: US-West default region (100-150ms latency from PL), Railpack vendor lock-in concerns, equivalent cost without the EU advantage.

#### 3. Fly.io (Third)

Strong CLI (`flyctl`) and managed Firecracker microVMs. Managed Postgres at $38/mo pushes total MVP cost to ~$50/mo — 10x Hetzner+Supabase for similar capabilities at this traffic level. MCP integration is experimental only. Good platform for persistent-process workloads, but cost is prohibitive for a solo hobby project.

### Dropped Platforms

- **Render** — Needs $25/mo Standard tier for reliable JVM memory (512 MB Starter is borderline for Spring Boot + JPA). Rollback requires REST API scripting, not CLI. Good docs and MCP but cost/benefit doesn't beat Railway.
- **Cloudflare Workers** — JS/TS runtime only. Cannot run JVM. Dropped on hard filter.
- **Vercel** — No JVM serverless support. Dropped on hard filter.
- **Netlify** — No JVM serverless support. Dropped on hard filter.

## Anti-Bias Cross-Check: Hetzner Cloud VPS + Supabase

### Devil's Advocate — Weaknesses

1. **No deploy API = fragile CI/CD** — GitHub Actions SSH deploy requires storing SSH keys in GitHub Secrets and maintaining a custom deploy script. If the VPS IP changes (floating IP not configured), the pipeline breaks silently. No structured deploy feedback — `docker compose up -d` exit codes don't confirm the app actually started or successfully connected to Supabase.
2. **TLS renewal failure is silent** — Certbot auto-renewal via systemd timer can fail (DNS propagation, port 80 blocked, Hetzner firewall change) without alerting. HTTPS breaks silently unless cert expiry is separately monitored.
3. **Unattended-upgrades can break Docker** — Kernel updates can break Docker's iptables/nftables integration, requiring manual reboot or Docker restart. Known issue on Ubuntu/Debian with Docker CE.
4. **No rollback primitive** — Rolling back code means `docker pull <previous-tag>` + restart. Without proper image tagging, no rollback is possible. No platform-level deployment history. DB schema changes are forward-only via Flyway/Liquibase — rollback requires a manual reverse migration.
5. **Supabase dependency** — App availability now depends on a third party. Supabase outage = app down. Supabase free tier has no SLA. Free tier project can be paused after 1 week of inactivity (must hit a Supabase API to keep alive).

### Pre-Mortem — How This Could Fail

The developer deployed Spring Boot on a Hetzner CX22 in Falkenstein, connected via JDBC to a Supabase Free tier Postgres in Frankfurt, with magic-link auth sending through Resend. It worked perfectly for a month — friends played the first campaign stage, scores were saved, the developer celebrated the first deploy. Then Supabase paused the free-tier project after one quiet week (no requests = paused), and Spring Boot's HikariCP pool started rejecting connections with cryptic timeout errors. The developer didn't notice until a friend messaged "magic link not arriving" — but the email was sent fine; the issue was that the auth controller couldn't write the token to the (paused) DB and Resend got called but the JWT was never stored, so the click flow failed at validation. UptimeRobot was hitting `/actuator/health` which returned 200 from the app's HTTP layer without touching the DB. Recovery took an hour to diagnose (Spring Boot stack trace pointed to JDBC, not to "Supabase project paused") and a Supabase dashboard click to unpause. Lesson: free tier project pausing + app-level health check that doesn't exercise the DB = silent multi-hour outage.

### Unknown Unknowns

- **Supabase pgbouncer + Spring HikariCP pool size mismatch** — Supabase uses pgbouncer in transaction-pooling mode for free tier. HikariCP default max pool size is 10 connections; if Spring opens more than Supabase's pool allows (15 for free tier), connections silently queue or error. Need to tune `spring.datasource.hikari.maximum-pool-size` to match Supabase tier limits.
- **Docker's default bridge network exposes ports to the internet** — `docker compose` with `ports: "8080:8080"` binds to `0.0.0.0`. If the Hetzner Cloud firewall doesn't block 8080 (only 80/443 open), Spring Boot would still be reachable on the public IP without going through nginx. Mitigation: bind to `127.0.0.1:8080:8080` and let nginx proxy `loxley.cards` → `127.0.0.1:8080`.
- **Hetzner Cloud firewall vs OS firewall interaction** — Cloud firewall operates at the hypervisor level. Docker's iptables rules bypass `ufw` entirely by default. Use Hetzner Cloud firewall (hypervisor level, cannot be bypassed) — don't bother with UFW on the VPS.
- **Supabase EU region selection is permanent** — At project creation, the region is locked. Choose `aws-eu-central-1` (Frankfurt) for minimum latency from Falkenstein (~10ms). A wrong choice means re-provisioning the entire Supabase project and migrating data.

## Operational Story

- **Preview deploys**: No native preview environments on Hetzner. Branch deploys require a second VPS or Docker Compose profile with a different port + nginx virtual host. Practical MVP alternative: test locally with `docker compose up`, deploy to production on merge to `main`.
- **Secrets**: Environment variables stored in GitHub Secrets (for CI/CD: `HETZNER_SSH_KEY`, `HETZNER_HOST`, `DOCKER_REGISTRY_TOKEN`) and `.env` file on the VPS (for Docker Compose: `DATABASE_URL` from Supabase, `RESEND_API_KEY`, `MAGIC_LINK_JWT_SECRET`). The `.env` file is NOT committed to git. Rotation: SSH into VPS, edit `.env`, `docker compose up -d` to restart. Supabase DB URL rotation requires updating both `.env` on VPS and any local dev setup. `hcloud` API token scoped to the project.
- **Rollback**: `docker pull <registry>/<image>:<previous-tag>` + `docker compose up -d`. Requires consistent image tagging in CI (use git SHA as tag). Database rollback is manual — Flyway/Liquibase migrations are forward-only; schema rollback requires writing a reverse migration. Supabase Free has daily backups (7-day retention) — restore via Supabase dashboard, NOT automated.
- **Approval**: Human-only operations: VPS deletion, firewall rule changes, DNS changes (loxley.cards subdomains), Supabase project changes, Resend domain verification, SSH key rotation. Agent may: deploy new image versions, restart containers, tail logs, query read-only DB statements, read Supabase metrics via API.
- **Logs**: `ssh <vps> docker compose logs -f app` for Spring Boot logs. Supabase DB logs accessible via Supabase dashboard (Free tier: 1 day retention). No centralized logging at MVP — for structured access: `docker compose logs --since 1h --no-color app | grep ERROR`. Resend mail delivery logs in Resend dashboard.

## Risk Register

| Risk | Source | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| Supabase Free project paused after inactivity | Pre-mortem | M | H | Keepalive cron pings DB every 3 days (Supabase pauses after 7); UptimeRobot health check hits an endpoint that performs a DB `SELECT 1` (not just app `/actuator/health`) |
| Supabase Free tier limits hit (500 MB DB / 2 GB bandwidth/mo / 50K MAUs) | Research finding | L | M | At MVP scale (5-10 users) limits are 100x over headroom. Monitor in Supabase dashboard; upgrade to Pro ($25/mo) if hit |
| HikariCP pool exhausts Supabase pgbouncer free tier (15 connections) | Unknown unknowns | M | M | Set `spring.datasource.hikari.maximum-pool-size=10` (leave 5 for pgbouncer overhead); use transaction-mode pooling |
| Supabase outage = app down | Devil's advocate | L | H | Accepted at MVP scale. Supabase has 99.9% uptime historically. App returns 503 with friendly error during outage |
| Unattended-upgrades kernel update breaks Docker | Devil's advocate | L | M | Pin Docker-related packages in apt; schedule manual review of pending updates weekly |
| TLS cert renewal fails silently | Devil's advocate | L | M | Monitor cert expiry via UptimeRobot HTTPS check (alerts at 14 days before expiry) on `api.loxley.cards` and `loxley.cards` |
| No automated rollback on failed deploy | Devil's advocate | M | M | Tag every Docker image with git SHA in CI; deploy script checks health endpoint (with DB ping) after restart, rolls back to previous SHA on failure |
| Docker iptables bypasses UFW firewall rules | Unknown unknowns | M | H | Do not use UFW — manage all firewall rules via Hetzner Cloud firewall (hypervisor level, cannot be bypassed by Docker). Spring Boot port 8080 bound to `127.0.0.1` only; nginx on 443 proxies to it |
| Wrong Supabase region (locked at creation) | Unknown unknowns | L | M | Choose `aws-eu-central-1` (Frankfurt) at project creation — ~10ms from Hetzner Falkenstein |
| SSH key in GitHub Secrets compromised | Devil's advocate | L | H | Dedicated deploy SSH key with restricted shell (only `docker` and `docker compose` commands); rotate quarterly |
| Single point of failure — VPS down = app down (DB stays up on Supabase) | Research finding | L | H | Hetzner Cloud 99.9% SLA; daily VPS snapshots (~20% cost, optional); acceptable for hobby project with 5-10 users |
| Resend mail rate limit / delivery failure | Research finding | L | M | Free tier: 100 emails/day, 3000/mo — 30x headroom for MVP. Add domain verification (loxley.cards SPF/DKIM) for inbox deliverability of magic links |

## Getting Started

1. **Provision the VPS**: `hcloud server create --name loxley-cards --type cx22 --image ubuntu-24.04 --location fsn1 --ssh-key <your-key-name>` — creates a 2 vCPU / 4 GB RAM VPS in Falkenstein (EU). Note the public IPv4.
2. **Configure Hetzner Cloud firewall**: `hcloud firewall create --name loxley-fw` then add inbound rules for ports 22 (SSH, optionally restricted to your IP), 80 (HTTP, for Certbot challenge), 443 (HTTPS). Apply: `hcloud firewall apply-to-resource loxley-fw --type server --server loxley-cards`. Do NOT open 8080 (Spring Boot stays behind nginx on 127.0.0.1).
3. **DNS for `loxley.cards`**: Point `loxley.cards` (frontend) to Cloudflare (orange-cloud proxied) and `api.loxley.cards` (backend) A record to the VPS IPv4.
4. **Provision Supabase project**: Create a new Supabase project in region `aws-eu-central-1` (Frankfurt). Note the connection string (Settings → Database → Connection string → URI). Use the transaction-mode pooler URL for Spring (port 6543, not 5432).
5. **Set up Resend**: Create a Resend account, add `loxley.cards` as verified domain (DNS records: SPF, DKIM, DMARC). Generate API key. Store as `RESEND_API_KEY`.
6. **Install Docker + nginx on VPS**: SSH in, install Docker CE + Docker Compose plugin via the official Docker apt repository. Install nginx (`apt install nginx`). Pin Docker packages in apt (`apt-mark hold docker-ce docker-ce-cli`).
7. **Set up TLS**: `certbot --nginx -d api.loxley.cards` for backend cert. Frontend cert handled automatically by Cloudflare Pages for `loxley.cards`.
8. **Create `docker-compose.yml`** with single `app` service (Spring Boot), bind to `127.0.0.1:8080:8080`. Pass `DATABASE_URL`, `RESEND_API_KEY`, `MAGIC_LINK_JWT_SECRET` from `.env`.
9. **nginx config**: `api.loxley.cards` → `proxy_pass http://127.0.0.1:8080`. Reload nginx.
10. **Set up CI/CD**: GitHub Actions workflow: `mvn package` → `docker build -t ghcr.io/<user>/loxley-cards:${{ github.sha }}` → `docker push` → SSH to VPS → `docker pull` + `docker compose up -d` → health check via `curl https://api.loxley.cards/actuator/health/db`. Store SSH key, VPS IP, GHCR token as GitHub Secrets.
11. **Frontend on Cloudflare Pages**: Connect GitHub repo (`frontend/` directory), build command `npm run build`, output directory `dist`. Set `VITE_API_URL=https://api.loxley.cards` as build env var. Auto-deploy on push to `main`.

## Out of Scope

The following were not evaluated in this research:
- Docker image configuration (Dockerfile contents, multi-stage build setup)
- CI/CD pipeline configuration (GitHub Actions workflow file contents)
- Production-scale architecture (multi-region, HA, DR)
- Kubernetes or container orchestration
- Backend CDN (frontend CDN is handled by Cloudflare Pages)
- Supabase Auth, Storage, Realtime, Edge Functions (only Postgres is used)
