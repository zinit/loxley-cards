---
project: loxley-cards
iteration: 1
status: approved
approved_at: 2026-05-24
goal: hello-world deploy — verify pipeline end-to-end before game engine / password auth / REST API
target_backend: https://api.loxley.cards
target_frontend: https://loxley.cards
sources:
  - context/foundation/infrastructure.md
  - context/foundation/tech-stack.md
---

# Plan: Pierwsze wdrożenie produkcyjne loxley-cards (iter. 1, "hello deploy")

## 1. Context

Celem tej iteracji jest **zweryfikowanie pipeline'u end-to-end od commitu do HTTPS** zanim zainwestujemy czas w game engine, password auth i REST API. Po wykonaniu planu push do `main` powinien automatycznie zbudować obraz Spring Boot, opublikować go w GHCR, wdrożyć na Hetzner VPS za nginx + Let's Encrypt, oraz odświeżyć frontend na Cloudflare Pages — a w przeglądarce na `https://loxley.cards` użytkownik zobaczy status `UP` z `https://api.loxley.cards/actuator/health`. Wszystkie zewnętrzne usługi (Supabase JDBC, Spring Security + JWT, JPA, Flyway) są celowo odroczone do iter. 2 — uruchamianie ich razem z pierwszym deployem znacznie rozszerza powierzchnię błędu, a większość ryzyk z risk register (DB pool sizing, pause-after-inactivity, cert renewal, CORS/cookie scope) najlepiej testować na działającym pipeline, a nie razem z nim. Spring Security jest świadomie nie dodany — bez konfiguracji blokuje wszystko HTTP Basic.

---

## 2. Pre-requisites

### 2A. Manual: konta (user, w przeglądarce)

1. **Hetzner Cloud** — jeśli nie masz: https://accounts.hetzner.com/signUp → załóż konto → dodaj kartę → Cloud Console → "New Project" → nazwa `loxley-cards`. (Koszt: ~4 EUR/mo CX22.)
2. **GitHub Personal Access Token (classic) dla GHCR** — https://github.com/settings/tokens → "Generate new token (classic)" → nazwa `loxley-cards-ghcr`, expiration 1 year, scopes: `write:packages` + `read:packages` + `delete:packages`. Zapisz token w password manager (już go nigdy nie zobaczysz). Token będzie potrzebny do `docker login ghcr.io` z VPS.
3. **Cloudflare** — masz już konto skoro DNS jest w Cloudflare. Zweryfikuj że domena `loxley.cards` jest na liście stref.
4. **(Równolegle, dla iter. 2 — niech dojrzewa)** **Supabase**: https://supabase.com/dashboard → New project, region **`aws-eu-central-1` (Frankfurt)**, nazwa `loxley-cards`, hasło DB w password manager. Region jest LOCKED przy tworzeniu — wybierz dobrze. Nic z tego nie używamy w iter. 1.
5. **(Iter. 2 nie potrzebuje email-providera)** Auth w F-03 to username + hasło (BCrypt + JWT, lokalnie w `users` table) — żadnego maila nie wysyłamy. Brak Resend / Mailgun / SMTP w MVP. Świadoma decyzja udokumentowana w `context/foundation/prd.md` (Access Control) i `context/foundation/infrastructure.md` (risk register).

### 2B. Command: brakujące CLI (user, lokalnie)

```bash
brew install gh hcloud
npm install -g wrangler   # opcjonalnie — Cloudflare Pages można też skonfigurować przez dashboard
gh auth login              # otworzy przeglądarkę, autoryzuj
hcloud context create loxley-cards   # spyta o API token — wygeneruj w Hetzner Cloud Console → Security → API Tokens → "Generate API Token", scope: Read & Write, nazwa "hcloud-cli-laptop"
```

### 2C. Decyzja — Cloudflare proxy mode dla obu domen (rozstrzygam teraz, bo wpływa na Etap 3)

- **`loxley.cards` (apex, frontend)** → CNAME do Cloudflare Pages, orange-cloud (proxied). Cloudflare zarządza TLS automatycznie.
- **`api.loxley.cards` (backend)** → A record direct na Hetzner public IPv4, **gray-cloud (DNS only)**. Powód: Cloudflare proxy + Let's Encrypt HTTP-01 challenge są możliwe, ale (a) Cloudflare wymaga osobnej konfiguracji "Full (strict)" TLS mode i własnego origin cert, (b) Certbot HTTP-01 challenge musi mieć port 80 widoczny przez proxy, (c) komplikuje debugging gdy coś nie działa. Dla MVP z 1 endpointem prostota > extra CDN warstwa przed API.

---

## 3. Etap 1: Provisioning manualny (~30 min, jednorazowo)

### 3.1 VPS Hetzner

**Command (lokalnie):**
```bash
# Najpierw upewnij się że masz dedykowany klucz SSH dla VPS (NIE id_rsa)
ssh-keygen -t ed25519 -f ~/.ssh/loxley_vps_admin -C "loxley-vps-admin-$(date +%Y%m%d)" -N ""
# Wgraj public key do Hetzner Cloud
hcloud ssh-key create --name loxley-admin --public-key-from-file ~/.ssh/loxley_vps_admin.pub
# Stwórz VPS
hcloud server create \
  --name loxley-cards \
  --type cx22 \
  --image ubuntu-24.04 \
  --location fsn1 \
  --ssh-key loxley-admin
# Zapisz public IPv4 — będzie potrzebny do DNS i SSH
hcloud server ip loxley-cards
```

### 3.2 Hetzner Cloud Firewall (hypervisor-level, **NIE używamy UFW**)

```bash
hcloud firewall create --name loxley-fw
# SSH (22) — zostaw open dla 0.0.0.0/0 w iter. 1 dla wygody; po deployu rozważ restrict do twojego IP
hcloud firewall add-rule loxley-fw --direction in --port 22 --protocol tcp --source-ips 0.0.0.0/0 --source-ips ::/0
hcloud firewall add-rule loxley-fw --direction in --port 80 --protocol tcp --source-ips 0.0.0.0/0 --source-ips ::/0
hcloud firewall add-rule loxley-fw --direction in --port 443 --protocol tcp --source-ips 0.0.0.0/0 --source-ips ::/0
# Port 8080 NIE jest opened — Spring Boot jest dostępny tylko przez nginx
hcloud firewall apply-to-resource loxley-fw --type server --server loxley-cards
```

**Krytyczne (z risk register, unknown unknowns):** Docker iptables bypassuje UFW. Dlatego wszystko robimy na poziomie Hetzner Cloud Firewall, NIE OS firewall. Port 8080 nie jest opened tu — i jest dodatkowo zabezpieczony na poziomie Docker bind (Etap 5).

### 3.3 DNS w Cloudflare (manual, klikamy w dashboard)

Cloudflare dashboard → `loxley.cards` → DNS → Records → "Add record":

- **A**: `api`, value = `<HETZNER_PUBLIC_IPV4>`, proxy = **DNS only (gray cloud)**, TTL Auto
- (apex `loxley.cards` skonfigurujemy w Etap 7 razem z Cloudflare Pages)

### 3.4 Deploy SSH key (dla GitHub Actions → VPS)

**Command (lokalnie):**
```bash
ssh-keygen -t ed25519 -f ~/.ssh/loxley_deploy_ci -C "loxley-deploy-ci-$(date +%Y%m%d)" -N ""
cat ~/.ssh/loxley_deploy_ci      # private — pójdzie do GitHub Secrets
cat ~/.ssh/loxley_deploy_ci.pub  # public — pójdzie na VPS do usera 'deploy'
```

**Uzasadnienie:** **Dedicated key, NIE id_rsa** (risk register: SSH key kompromitacja). Klucz osobny per środowisko = łatwa rotacja, audyt, revoke. Nazwa `loxley_deploy_ci` opisuje rolę.

### 3.5 GitHub Secrets

**Manual:** GitHub → `zinit/loxley-cards` → Settings → Secrets and variables → Actions → "New repository secret". Dodaj:

| Name | Value | Skąd |
|---|---|---|
| `VPS_HOST` | Hetzner public IPv4 (z `hcloud server ip`) | Etap 3.1 |
| `VPS_USER` | `deploy` | (utworzymy w Etap 5.1) |
| `VPS_SSH_KEY` | Cała zawartość `~/.ssh/loxley_deploy_ci` (private, z `-----BEGIN OPENSSH PRIVATE KEY-----`) | Etap 3.4 |
| `VPS_SSH_KNOWN_HOSTS` | Output z `ssh-keyscan -t ed25519 <HETZNER_IP>` (uruchom lokalnie po VPS up) | po Etap 3.1 |

**Uwaga:** `GITHUB_TOKEN` (do push w GHCR) jest auto-injected w workflow, nie trzeba dodawać.

---

## 4. Etap 2: Backend code changes (agentowe)

### 4.1 `backend/app/pom.xml` — dodać Actuator

W sekcji `<dependencies>` dodać:
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```
**Nie dodajemy:** `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `org.postgresql:postgresql` — to iter. 2.

### 4.2 `backend/app/src/main/resources/application.properties` — minimalne prod-safe defaults

Pozostawić istniejące + dodać:
```properties
# Actuator — w iter. 1 wystawiamy tylko /actuator/health (default exposed)
management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never

# Behind reverse proxy (nginx terminuje TLS, dodaje X-Forwarded-*)
server.forward-headers-strategy=framework

# Bindowanie nasłuchu — domyślnie 0.0.0.0:8080, zostawiamy (Docker przekieruje na 127.0.0.1)
server.port=8080
```

### 4.3 `backend/app/src/main/resources/application-prod.properties` (NOWY)

```properties
# Profile-prod overrides — aktywowany przez SPRING_PROFILES_ACTIVE=prod w docker-compose
# W iter. 1 nic specyficznego prod-only, ale plik istnieje jako convention + wymusza świadomą aktywację
logging.level.root=INFO
logging.level.cards.loxley=INFO
```
**Uzasadnienie:** Trzymamy profil "prod" jako szwicz separujący dev/prod nawet zanim mamy DB. Gdy iter. 2 doda Supabase, `DATABASE_URL`-style config trafi tu, a dev `application.properties` zostanie czysty.

### 4.4 `backend/Dockerfile` (NOWY) — multi-stage, eclipse-temurin

```dockerfile
# Stage 1: build
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY acommon-game-engine/pom.xml acommon-game-engine/
COPY acommon-db/pom.xml acommon-db/
COPY acommon-ai/pom.xml acommon-ai/
COPY app/pom.xml app/
# Pre-fetch deps (lepszy cache layer)
RUN ./mvnw -B dependency:go-offline -pl app -am || true
# Skopiuj resztę i zbuduj
COPY acommon-game-engine/src acommon-game-engine/src
COPY acommon-db/src acommon-db/src
COPY acommon-ai/src acommon-ai/src
COPY app/src app/src
RUN ./mvnw -B -DskipTests package

# Stage 2: runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# curl potrzebny do docker-compose healthcheck (wget nie jest gwarantowany w jre-jammy)
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*
RUN useradd -r -u 1001 spring && chown spring:spring /app
USER spring
COPY --from=build /workspace/app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
```

**Decyzje techniczne (rozstrzygnięte):**
- **Base image:** `eclipse-temurin:21-jre-jammy` (Ubuntu-based, Adoptium build). Powód: oficjalny obraz Eclipse Adoptium z najszerszym community, dobra dokumentacja, regularne CVE scany. Alpine alternatywa (`azul/zulu-openjdk-alpine`) jest mniejsza (~80 MB vs ~250 MB), ALE musl libc bywa źródłem nietrywialnych bugów w bibliotekach JVM (zwłaszcza native code w przyszłej AI / native compile). Dla MVP gdzie image pull leci raz na deploy do jednego VPS, 170 MB różnicy nie boli — wybieram stabilność.
- **Multi-stage:** TAK. Powody: (a) final image ~280 MB zamiast ~700+ MB (JDK + maven cache w runtime to overhead), (b) shipping JDK = większa powierzchnia CVE, (c) CI build time różnica jest <1 min na pierwszym buildzie i pomijalna na kolejnych (BuildKit cache).
- **Non-root user `spring` (uid 1001):** standard hardening, kosztuje 2 linie, eliminuje całą klasę container-escape vectorów.
- **`-XX:MaxRAMPercentage=75`** — na CX22 (4 GB RAM) zostawia ~1 GB dla nginx + system. JVM 21 wykrywa cgroup limit automatycznie.
- **`curl` w runtime stage:** doinstalowany apt-em (~5 MB) tylko po to żeby healthcheck w docker-compose miał czym strzelać do `/actuator/health`. Default `eclipse-temurin:21-jre-jammy` zawiera Adoptium-specific minimal apt set i `wget` nie jest częścią gwarancji, `curl` jednak jest częstszy — instalujemy świadomie żeby healthcheck nie był source of flakiness.

### 4.5 `backend/.dockerignore` (NOWY)

```
target/
**/target/
.mvn/wrapper/maven-wrapper.jar
.idea/
*.iml
.git/
.DS_Store
```

### 4.6 Sanity-check (user uruchamia lokalnie)

```bash
cd /Users/daniel/IdeaProjects/loxley-cards/backend
./mvnw -B clean package
# powinien zbudować 5 modułów; weryfikuj że app/target/*.jar istnieje
ls -la app/target/loxley-cards-app-0.0.1-SNAPSHOT.jar
# Sanity test docker build lokalnie
docker build -t loxley-cards:local .
docker run --rm -p 127.0.0.1:8080:8080 loxley-cards:local
# w drugim terminalu:
curl -s http://127.0.0.1:8080/actuator/health
# oczekiwane: {"status":"UP"}
```

---

## 5. Etap 3: VPS setup (~45 min, jednorazowo, manual przez SSH)

SSH in:
```bash
ssh -i ~/.ssh/loxley_vps_admin root@<HETZNER_IP>
```

### 5.1 Apt baseline + non-root user `deploy`

```bash
apt update && apt upgrade -y
apt install -y nginx certbot python3-certbot-nginx ca-certificates curl gnupg apt-transport-https
# Stwórz usera deploy
adduser --disabled-password --gecos "" deploy
usermod -aG sudo deploy
# Wgraj public key z 3.4 do deploy@VPS
mkdir -p /home/deploy/.ssh
# WSTAW tu wartość ~/.ssh/loxley_deploy_ci.pub
nano /home/deploy/.ssh/authorized_keys
chmod 700 /home/deploy/.ssh && chmod 600 /home/deploy/.ssh/authorized_keys
chown -R deploy:deploy /home/deploy/.ssh
```

### 5.2 Docker CE + Compose plugin + apt-mark hold

```bash
# Oficjalny repo Docker
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor -o /etc/apt/keyrings/docker.gpg
chmod a+r /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | tee /etc/apt/sources.list.d/docker.list > /dev/null
apt update
apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
# KRYTYCZNE (risk register): zapobiega unattended-upgrades łamaniu iptables
apt-mark hold docker-ce docker-ce-cli containerd.io
# User deploy może odpalać docker bez sudo
usermod -aG docker deploy
# Weryfikacja
docker --version && docker compose version
```

### 5.3 GHCR login dla usera `deploy`

```bash
su - deploy
# Token z 2A.2 (PAT z scope read:packages)
echo "<GHCR_PAT>" | docker login ghcr.io -u zinit --password-stdin
# Zapisuje credentials w /home/deploy/.docker/config.json
exit
```

### 5.4 Layout `/opt/loxley-cards/` i `.env`

```bash
mkdir -p /opt/loxley-cards
chown deploy:deploy /opt/loxley-cards
su - deploy
cd /opt/loxley-cards
```

**Stwórz `/opt/loxley-cards/.env`** (jako user `deploy`, mode 600):
```bash
# Iter. 1 — minimalny .env. Iter. 2 doda DATABASE_URL, JWT_SECRET
IMAGE_TAG=latest
SPRING_PROFILES_ACTIVE=prod
```
```bash
chmod 600 /opt/loxley-cards/.env
```

**Stwórz `/opt/loxley-cards/docker-compose.yml`:**
```yaml
services:
  app:
    image: ghcr.io/zinit/loxley-cards:${IMAGE_TAG}
    restart: unless-stopped
    ports:
      # KRYTYCZNE: bind 127.0.0.1, NIE 0.0.0.0 — Docker bypassuje UFW
      - "127.0.0.1:8080:8080"
    environment:
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE}
    healthcheck:
      test: ["CMD-SHELL", "curl -fsS http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"' || exit 1"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
```

**Uzasadnienie bind:** `127.0.0.1:8080:8080` (NIE `8080:8080`). Docker domyślnie binduje na `0.0.0.0` co omija Hetzner Cloud Firewall byłoby OK ale UFW byłby bypass'owany. My i tak nie używamy UFW i mamy port 8080 zamknięty na firewallu — ALE defense in depth: bind na 127.0.0.1 sprawia że nawet jeśli ktoś przypadkiem doda regułę firewall albo Docker zaktualizuje się i zmieni semantykę, app pozostaje invisible. Jedyny ruch do app idzie przez nginx (lokalnie).

### 5.5 nginx config

**Stwórz `/etc/nginx/sites-available/api.loxley.cards`** (jako root):
```nginx
server {
    listen 80;
    listen [::]:80;
    server_name api.loxley.cards;

    # CORS dla frontendu (loxley.cards → api.loxley.cards = cross-origin)
    add_header 'Access-Control-Allow-Origin' 'https://loxley.cards' always;
    add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
    add_header 'Access-Control-Allow-Headers' 'Authorization, Content-Type' always;
    add_header 'Access-Control-Max-Age' 86400 always;
    if ($request_method = 'OPTIONS') {
        return 204;
    }

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header X-Forwarded-Host $host;
        proxy_read_timeout 60s;
    }
}
```
```bash
ln -s /etc/nginx/sites-available/api.loxley.cards /etc/nginx/sites-enabled/
rm -f /etc/nginx/sites-enabled/default
nginx -t && systemctl reload nginx
```

**Decyzja CORS — w nginx, NIE w Spring `@CrossOrigin`:** W iter. 1 jeszcze nie mamy żadnych REST controllerów (tylko Actuator). Reguły w nginx są niezależne od kodu aplikacji, niech już teraz "działają". W iter. 2, jeśli Spring Security pojawi się jako `WebSecurityConfigurer` z CORS, można ten temat skonsolidować — ale dopóki app jest jedna i frontend jeden, nginx CORS jest najprostszy. Header w nginx jest `always`, więc pokrywa też error responses (Spring `@CrossOrigin` ich nie pokrywa).

### 5.6 Let's Encrypt (Certbot) — kolejność matters

**Kolejność:** Najpierw nginx vhost (5.5) musi już działać na port 80 (właśnie zrobiliśmy). DNS `api.loxley.cards` musi już propagować (3.3). Dopiero teraz Certbot.

```bash
# weryfikacja propagacji DNS najpierw
dig +short api.loxley.cards   # powinien zwrócić Hetzner IP
# Certbot z auto-configure nginx (doda listen 443 ssl, cert paths, redirect 80→443)
certbot --nginx -d api.loxley.cards --non-interactive --agree-tos -m email@email.com --redirect
# Certbot dodaje cron / systemd timer renewal automatycznie:
systemctl list-timers | grep certbot
```

**Sprawdzenie po:** plik `/etc/nginx/sites-available/api.loxley.cards` zawiera teraz blok `listen 443 ssl` i `ssl_certificate /etc/letsencrypt/live/api.loxley.cards/...`. Renewal jest auto — ale **silent fail jest realnym ryzykiem** (risk register) — patrz Pułapki, p. 3.

### 5.7 Pierwszy ręczny deploy (sanity, zanim odpalimy CI)

Z lokalnego laptopa, zanim CI istnieje, sprawdźmy że obraz w ogóle działa na VPS:

```bash
# Lokalnie zbuduj i wypchnij ręcznie raz
cd /Users/daniel/IdeaProjects/loxley-cards/backend
docker build --platform linux/amd64 -t ghcr.io/zinit/loxley-cards:bootstrap .
echo "<GHCR_PAT>" | docker login ghcr.io -u zinit --password-stdin
docker push ghcr.io/zinit/loxley-cards:bootstrap

# Na VPS jako deploy
ssh -i ~/.ssh/loxley_deploy_ci deploy@<HETZNER_IP>
cd /opt/loxley-cards
# tymczasowo zmień IMAGE_TAG=bootstrap w .env
sed -i 's/IMAGE_TAG=latest/IMAGE_TAG=bootstrap/' .env
docker compose pull && docker compose up -d
docker compose ps   # powinien być healthy po ~60s
curl -s http://127.0.0.1:8080/actuator/health   # {"status":"UP"}
curl -s https://api.loxley.cards/actuator/health  # {"status":"UP"} przez HTTPS!
```

Jeśli to działa, masz pewność że stack VPS jest poprawny **zanim** dodasz CI/CD złożoność. Wracamy `IMAGE_TAG=latest` w `.env` przed Etap 6.

**Uwaga `--platform linux/amd64`:** Hetzner VPS to x86_64. Mac (Apple Silicon) buduje natywnie arm64 — bez tego flagu obraz nie startuje na VPS (`exec format error`). W GitHub Actions runner `ubuntu-latest` jest amd64, więc tam flaga niepotrzebna.

---

## 6. Etap 4: CI/CD pipeline (agentowe)

### 6.1 `.github/workflows/deploy.yml` (NOWY)

```yaml
name: Build and Deploy

on:
  push:
    branches: [main]
  workflow_dispatch:

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}  # zinit/loxley-cards

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven

      - name: Build & test
        working-directory: backend
        run: ./mvnw -B clean package

      - name: Log in to GHCR
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build & push image
        uses: docker/build-push-action@v5
        with:
          context: backend
          file: backend/Dockerfile
          platforms: linux/amd64
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:${{ github.sha }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}:latest
          cache-from: type=gha
          cache-to: type=gha,mode=max

      - name: Deploy to VPS
        env:
          SSH_KEY: ${{ secrets.VPS_SSH_KEY }}
          SSH_HOST: ${{ secrets.VPS_HOST }}
          SSH_USER: ${{ secrets.VPS_USER }}
          KNOWN_HOSTS: ${{ secrets.VPS_SSH_KNOWN_HOSTS }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          mkdir -p ~/.ssh
          echo "$SSH_KEY" > ~/.ssh/deploy_key && chmod 600 ~/.ssh/deploy_key
          echo "$KNOWN_HOSTS" > ~/.ssh/known_hosts
          ssh -i ~/.ssh/deploy_key -o UserKnownHostsFile=~/.ssh/known_hosts $SSH_USER@$SSH_HOST <<EOF
            set -e
            cd /opt/loxley-cards
            # Aktualizuj IMAGE_TAG w .env (immutable SHA) i zachowaj poprzedni do rollback
            grep -v '^IMAGE_TAG=' .env > .env.new || true
            echo "PREV_IMAGE_TAG=\$(grep '^IMAGE_TAG=' .env | cut -d= -f2)" >> .env.new
            echo "IMAGE_TAG=$IMAGE_TAG" >> .env.new
            mv .env.new .env
            docker compose pull
            docker compose up -d
            # health check (retry przez 90s)
            for i in {1..18}; do
              if curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'; then
                echo "Healthy after \$((i*5))s"; exit 0
              fi
              sleep 5
            done
            echo "Health check failed — rolling back"
            PREV=\$(grep '^PREV_IMAGE_TAG=' .env | cut -d= -f2)
            sed -i "s/^IMAGE_TAG=.*/IMAGE_TAG=\$PREV/" .env
            docker compose pull && docker compose up -d
            exit 1
          EOF

      - name: Verify public endpoint
        run: |
          for i in {1..10}; do
            if curl -fsS https://api.loxley.cards/actuator/health | grep -q '"status":"UP"'; then
              echo "Public endpoint healthy"; exit 0
            fi
            sleep 3
          done
          exit 1
```

**Decyzje:**
- **Tag SHA + `latest`** — SHA jest immutable pointerem (do rollbacku ręcznego: `IMAGE_TAG=<old_sha>` w `.env` + `docker compose up -d`). `latest` jako mutable pointer NIE jest używany przez compose (compose używa `${IMAGE_TAG}` które CI ustawia na SHA), ale `:latest` jest pomocny przy manualnym debug (`docker pull ghcr.io/zinit/loxley-cards:latest`).
- **Rollback w deploy step** — w skrypcie SSH automatic rollback do `PREV_IMAGE_TAG` jeśli health check po 90s nie zwróci UP. Prosty, bez overkill, działa.
- **Verify step zewnętrzny** — drugi health check `https://api.loxley.cards` weryfikuje że TLS i nginx też są OK (nie tylko backend localhost). Failuruje workflow jeśli publiczny endpoint nie odpowiada — jasny sygnał że nginx/cert pojechał.
- **`cache: maven`** w setup-java — przyspiesza re-buildy ~2-3x.
- **`cache-from/to: gha`** — Docker BuildKit cache w GitHub Actions cache, znacząco przyspiesza po pierwszym buildzie.

### 6.2 Jak rozpoznać failure

| Failure | Gdzie | Sygnał | Co zrobić |
|---|---|---|---|
| `mvn package` | step "Build & test" | Maven error, np. compile fail | Fix kod, push znowu |
| Docker build | step "Build & push image" | Dockerfile error | Test lokalnie `docker build --platform linux/amd64 -t test backend/` |
| GHCR push 403 | step "Build & push image" | `denied: permission_denied` | Sprawdź że Settings → Actions → General → Workflow permissions = "Read and write" |
| SSH connection | step "Deploy to VPS" | `permission denied (publickey)` | Sprawdź `VPS_SSH_KEY` (cała zawartość z BEGIN do END!), `authorized_keys` na VPS |
| Known hosts | step "Deploy to VPS" | `Host key verification failed` | Odśwież `VPS_SSH_KNOWN_HOSTS` przez `ssh-keyscan -t ed25519 <IP>` |
| Compose pull | wewnątrz SSH | `unauthorized` | Re-login GHCR jako `deploy` (5.3) |
| App nie startuje | wewnątrz SSH | health failed po 90s | `ssh deploy@vps "cd /opt/loxley-cards && docker compose logs --tail=200 app"` |
| Public endpoint failed | step "Verify public endpoint" | curl 502/504 | nginx logs: `ssh deploy@vps "sudo tail -50 /var/log/nginx/error.log"` |

---

## 7. Etap 5: Frontend scaffold + Cloudflare Pages (~30 min)

### 7.1 Scaffold (user uruchamia)

```bash
cd /Users/daniel/IdeaProjects/loxley-cards
# UWAGA: frontend/ istnieje jako pusty folder. npm create vite chce pustego CWD lub stworzy podfolder.
# Najprostsza ścieżka: usuń pusty folder i zostaw vite stworzyć od nowa
rmdir frontend
npm create vite@latest frontend -- --template react-ts
cd frontend
npm install
# Tailwind v4 (Vite plugin, najnowszy idiomatyczny way)
npm install -D tailwindcss @tailwindcss/vite
```

### 7.2 Pliki frontend (agentowe edycje)

**`frontend/vite.config.ts`** — dodać Tailwind plugin:
```ts
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
})
```

**`frontend/src/index.css`** — zastąpić zawartość:
```css
@import "tailwindcss";
```

**`frontend/src/App.tsx`** — zastąpić scaffold:
```tsx
import { useEffect, useState } from 'react'

const API_URL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080'

type HealthStatus = 'loading' | 'UP' | 'DOWN' | 'ERROR'

export default function App() {
  const [status, setStatus] = useState<HealthStatus>('loading')
  const [detail, setDetail] = useState<string>('')

  useEffect(() => {
    fetch(`${API_URL}/actuator/health`)
      .then(async r => {
        const body = await r.json()
        setStatus(body.status === 'UP' ? 'UP' : 'DOWN')
        setDetail(JSON.stringify(body))
      })
      .catch(e => { setStatus('ERROR'); setDetail(String(e)) })
  }, [])

  const colorClass = status === 'UP' ? 'text-green-600'
    : status === 'loading' ? 'text-gray-500'
    : 'text-red-600'

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-50">
      <div className="text-center space-y-4">
        <h1 className="text-4xl font-bold">loxley.cards</h1>
        <p className="text-slate-600">Pierwszy deploy — smoke test pipeline</p>
        <p className={`text-2xl font-mono ${colorClass}`}>API: {status}</p>
        <p className="text-xs text-slate-400 max-w-md break-all">{detail}</p>
        <p className="text-xs text-slate-400">→ {API_URL}/actuator/health</p>
      </div>
    </main>
  )
}
```

**`frontend/.env.example`** (NOWY, commitujemy jako template):
```
VITE_API_URL=https://api.loxley.cards
```

**`frontend/.env.local`** (NOT commit) — dla local dev wskazuje na lokalny backend:
```
VITE_API_URL=http://localhost:8080
```

### 7.3 Sanity-check lokalnie

```bash
cd frontend
npm run dev
# odpal lokalnie backend (Etap 4.6) na :8080, otwórz http://localhost:5173 → status UP
npm run build  # weryfikacja że build przechodzi → dist/
```

### 7.4 Cloudflare Pages wiring (manual w dashboard)

**Manual:** Cloudflare dashboard → Workers & Pages → Create → Pages → "Connect to Git" → wybierz `zinit/loxley-cards` → "Begin setup":
- **Project name:** `loxley-cards`
- **Production branch:** `main`
- **Framework preset:** Vite
- **Build command:** `npm run build`
- **Build output directory:** `dist`
- **Root directory (advanced):** `frontend`
- **Environment variables (Production):** `VITE_API_URL` = `https://api.loxley.cards`
- "Save and Deploy"

**Kluczowe:** `VITE_*` env vars są **BAKED-IN przy build time** (Vite inline'uje je w bundle). Zmiana wymaga re-deploy. NIE są runtime config. Dla MVP gdzie mamy 1 backend URL i nie używamy preview deployów — w porządku.

Po pierwszym sukcesie buildu, Cloudflare przydzieli URL `loxley-cards.pages.dev`. Następnie:

**Cloudflare dashboard → loxley-cards project → Custom domains → "Set up a custom domain":**
- Wpisz `loxley.cards` → Continue. Cloudflare doda CNAME-flattened record (apex) automatycznie bo strefa jest u nich → kliknij "Activate domain". Proxy = orange (default dla Pages custom domain). TLS auto-managed.

**Czemu nie wrangler CLI:** wrangler CLI nadaje się do Workers; dla Pages dashboard wiring jest jednorazowy, click-once, więcej zachodu z CLI niż zysku w MVP. Po setupie deploye lecą auto z gita bez interakcji.

---

## 8. Etap 6: End-to-end verification (~5 min)

### 8.1 Smoke test commands

```bash
# Backend bezpośrednio (HTTPS, valid cert, behind nginx)
curl -v https://api.loxley.cards/actuator/health
# Oczekiwane: HTTP/2 200, body {"status":"UP"}, cert by Let's Encrypt
# Cert weryfikacja:
echo | openssl s_client -connect api.loxley.cards:443 -servername api.loxley.cards 2>/dev/null | openssl x509 -noout -dates -issuer

# CORS preflight
curl -i -X OPTIONS https://api.loxley.cards/actuator/health \
  -H "Origin: https://loxley.cards" \
  -H "Access-Control-Request-Method: GET"
# Oczekiwane: HTTP/2 204, Access-Control-Allow-Origin: https://loxley.cards

# Frontend
curl -I https://loxley.cards
# Oczekiwane: HTTP/2 200, cf-cache-status, server: cloudflare
```

### 8.2 Browser smoke test

Otwórz https://loxley.cards w przeglądarce → powinieneś zobaczyć:
- Tytuł "loxley.cards"
- Linia "API: UP" w kolorze zielonym
- Pod spodem mały JSON `{"status":"UP"}`

DevTools → Network → request do `https://api.loxley.cards/actuator/health` → status 200, response headers zawierają `Access-Control-Allow-Origin: https://loxley.cards`.

### 8.3 Test deploy pipeline (najlepszy smoke test)

Trywialny commit do main, np. zmiana napisu "Pierwszy deploy" na "Hello, world":
```bash
git add frontend/src/App.tsx
git commit -m "deploy smoke test"
git push origin main
```
- GitHub Actions tab → workflow "Build and Deploy" startuje
- Cloudflare Pages dashboard → nowy deploy zaczyna się równolegle
- Po ~5-7 min oba zielone
- Refresh `https://loxley.cards` → nowy tekst widoczny
- `https://api.loxley.cards/actuator/health` → wciąż UP

**Pipeline zweryfikowany end-to-end.**

---

## 9. Krytyczne pułapki & mitygacje

1. **Docker iptables bypassuje UFW.** Mitygacja: **nie używamy UFW**. Cały firewall na Hetzner Cloud Firewall (hypervisor level, nie do obejścia z VM). Dodatkowo Spring Boot bind na `127.0.0.1:8080` w docker-compose — defense in depth.
2. **Spring Boot port 8080 musi być bound do `127.0.0.1`, NIE `0.0.0.0`.** W `docker-compose.yml` zapis `"127.0.0.1:8080:8080"`. Bez tego Docker bindowałby na wszystkich interfejsach i pominąłby intencję ukrycia app za nginx.
3. **Cert renewal silent fail.** Certbot ma systemd timer (`systemctl list-timers | grep certbot`), ale awarie są ciche. **Mitygacja w iter. 1:** ręcznie sprawdź `certbot renew --dry-run` po setupie. **Out of scope teraz, do iter. 2:** dodać UptimeRobot HTTPS check na `api.loxley.cards` (alert 14 dni przed expiry — wbudowane w UptimeRobot Free).
4. **Unattended-upgrades vs Docker.** Update kernela lub `docker-ce` może popsuć iptables. **Mitygacja:** `apt-mark hold docker-ce docker-ce-cli containerd.io` (zrobione w 5.2). Co tydzień ręcznie review `apt list --upgradable` i decyzja czy aktualizować Docker (rzadko trzeba).
5. **SSH deploy key kompromitacja.** Mitygacja: dedicated key `loxley_deploy_ci` (osobny od `id_rsa` i osobny od `loxley_vps_admin`), tylko on jest w GitHub Secrets, user `deploy` zamiast `root`. Rotacja: regeneruj key co ~3-6 miesięcy, replace w `authorized_keys` na VPS + w GitHub Secrets.
6. **CORS — frontend i backend są na różnych subdomenach (cross-origin).** Mitygacja: nginx dodaje `Access-Control-Allow-Origin: https://loxley.cards` (sekcja 5.5). Iter. 2: gdy dodamy Spring Security, trzeba zachować spójność (nie blokować preflight OPTIONS). W frontendzie nie używaj `credentials: 'include'` bez konieczności — wtedy CORS jest prostszy (`Allow-Credentials` byłby wymagany i `Allow-Origin: *` przestaje działać).
7. **Cloudflare orange-cloud proxy vs Let's Encrypt na `api.loxley.cards`.** Rozstrzygnięcie: **`api.loxley.cards` = DNS only (gray cloud)**, A record direct na Hetzner IP. Powód: Let's Encrypt HTTP-01 challenge wymaga port 80 direct, proxy CF mogłoby zwracać własną odpowiedź lub wymuszać Full Strict z origin cert. Apex `loxley.cards` = proxied (orange) bo idzie do Cloudflare Pages, gdzie CF zarządza TLS end-to-end natywnie.

---

## 10. Out of scope (iter. 1) — robimy w iter. 2+

**Backend / app code:**
- `spring-boot-starter-data-jpa` + `org.postgresql` dependencies
- `spring.datasource.url` / `DATABASE_URL` env wiring w `application-prod.properties`
- HikariCP pool size tuning (`maximum-pool-size=10` dla Supabase Free pgbouncer)
- Flyway migrations module / `db/migration/V1__init.sql`
- `spring-boot-starter-security` + `spring-boot-security` autoconfig (SB4 per-tech split, lesson z F-02) + BCrypt password encoder + JWT signing/verification + `/auth/register|login|logout` REST endpointy (F-03)
- `JWT_SECRET` env var (32+ random bytes, `openssl rand -base64 48`) + V3 Flyway migration na `users` table (`username`/`password_hash` columns + `email` nullable)
- Custom `/actuator/health/db` indicator który robi `SELECT 1` (na nim oprzeć UptimeRobot w iter. 2)
- Pierwszy REST controller (np. `GET /api/v1/me` zwracający 401 bez tokena)

**Infrastructure:**
- UptimeRobot monitoring (HTTPS check na obie domeny, +14 day cert expiry alert)
- Hetzner snapshots (daily, ~+20% cost — opcjonalne, hobby project)
- Cron keepalive ping do Supabase (zapobiega pause po 7 dniach inactivity)
- IP allow-list dla SSH (Hetzner Cloud Firewall restrict port 22 do twojego /32)
- Centralized logging / structured logging (`logback-spring.xml` JSON output)
- Database backup automation poza Supabase (S3 / B2 dump przez cron)
- Multi-arch image build (`linux/arm64` dla ewentualnego ARM VPS w przyszłości)
- Preview deploy environments (osobny vhost na branch)

**Frontend:**
- Routing (React Router) — single page w iter. 1
- State management
- Login / register page (username + hasło) + logout flow
- API client setup (axios / tanstack-query)
- Wszystkie game UI screens

**Komentarz strategiczny do "równolegle":** Etap 2A.4 (Supabase project creation w `aws-eu-central-1`) **rób już teraz manualnie podczas Etap 1-3**. Powód: Supabase region jest LOCKED, lepiej kliknąć raz teraz świadomie niż w trakcie iter. 2. Niech dojrzewa w tle. Connection string z Supabase trzymaj w password manager — w iter. 1 NIE wstawiamy go nigdzie w kodzie ani w `.env` na VPS. (Resend / email-provider odpadł po pivocie magic-link → username/password w 2026-06-13.)

**Limit czasowy:** plan jest realizowalny w 2 wieczory po godzinach: wieczór 1 = Etap 1-3 (VPS up, DNS, cert, ręczny smoke deploy z 5.7), wieczór 2 = Etap 4-6 (CI/CD, frontend, weryfikacja). Jeśli coś się rozjedzie (np. DNS propaguje wolno), Etap 6 może zostać do trzeciego wieczoru — ale rzadko.

---

## Critical Files for Implementation

| Plik | Akcja | Etap |
|---|---|---|
| `/Users/daniel/IdeaProjects/loxley-cards/backend/app/pom.xml` | EDIT — dodać `spring-boot-starter-actuator` | 4.1 |
| `/Users/daniel/IdeaProjects/loxley-cards/backend/app/src/main/resources/application.properties` | EDIT — dodać actuator/health expose + `server.forward-headers-strategy=framework` | 4.2 |
| `/Users/daniel/IdeaProjects/loxley-cards/backend/app/src/main/resources/application-prod.properties` | NEW — profile prod activation hook | 4.3 |
| `/Users/daniel/IdeaProjects/loxley-cards/backend/Dockerfile` | NEW — multi-stage temurin 21 | 4.4 |
| `/Users/daniel/IdeaProjects/loxley-cards/backend/.dockerignore` | NEW | 4.5 |
| `/Users/daniel/IdeaProjects/loxley-cards/.github/workflows/deploy.yml` | NEW — build + push GHCR + SSH deploy + rollback + verify | 6.1 |
| `/Users/daniel/IdeaProjects/loxley-cards/frontend/**` | NEW — scaffold `npm create vite` + Tailwind v4 + Hello+health page | 7.1-7.3 |
| `/Users/daniel/IdeaProjects/loxley-cards/context/deployment/deploy-plan.md` | NEW — kopia tego planu (po akceptacji) | finalny krok |

**Pliki na VPS (NIE w git):**
- `/opt/loxley-cards/.env` (mode 600, owner deploy)
- `/opt/loxley-cards/docker-compose.yml`
- `/etc/nginx/sites-available/api.loxley.cards`
- `/etc/letsencrypt/live/api.loxley.cards/*` (auto-managed by Certbot)

---

## Verification Plan (jak udowodnić że deploy działa)

1. **Manual sanity (Etap 5.7)** — bezpośredni `docker compose up` na VPS przed CI, `curl` lokalny i przez HTTPS
2. **Pipeline self-test (Etap 8.3)** — trywialny commit triggeruje pełny workflow, oba deploye zielone
3. **Browser end-to-end (Etap 8.2)** — `https://loxley.cards` w przeglądarce, status UP w UI, CORS w DevTools Network
4. **Cert sanity** — `openssl s_client` pokazuje cert wydany przez Let's Encrypt z prawidłową datą
5. **Rollback drill (opcjonalnie po pierwszym sukcesie)** — manual `IMAGE_TAG=<old_sha>` w `/opt/loxley-cards/.env` + `docker compose up -d` → starsza wersja w `/actuator/health`. Potwierdza że rollback ścieżka istnieje zanim będziesz jej potrzebować w stresie.
