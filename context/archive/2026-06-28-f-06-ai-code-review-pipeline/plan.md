# AI Code Review Pipeline — Plan implementacji

## Przegląd

Nowy GitHub Actions workflow (`ai-review.yml`) + skrypt Node (`.github/scripts/review.js`) implementujący composable agenta (Vercel AI SDK 6 + Zod schema + Claude Sonnet 4.6). Agent ocenia diff PR wg 5-wymiarowej rubryki (poprawność, idiomatyczność Java/Spring, złożoność, testy, bezpieczeństwo — każde 1-10) i postuje strukturalny komentarz markdown na PR przez GitHub REST API (octokit). Gate informacyjny — nie blokuje merge.

## Analiza stanu obecnego

Istniejący CI (`.github/workflows/ci.yml`) to dwa niezależne joby: backend `mvnw verify` + frontend `npm build`. Zero AI w pipeline. Brak root-level `package.json` — skrypt review potrzebuje własnego. Repo ma istniejącą infrastrukturę `.claude/skills/10x-impl-review-ci/` (review implementacji vs plan), ale to jest "gotowy agent" (claude-code-action) — f-06 to "composable agent" (Vercel AI SDK, certyfikacja M5L2/M5L3).

## Pożądany stan końcowy

Po zakończeniu planu: otwarcie PR na `main` automatycznie triggeruje job `ai-review`, który postuje komentarz z tabelą 5 ocen + summary + werdykt (APPROVED / NEEDS_ATTENTION / REJECTED). Komentarz zawiera HTML marker do update-existing-comment, conditional collapsibility (compact dla APPROVED, expanded dla NEEDS_ATTENTION/REJECTED), footer z identyfikacją bota. Istniejący `ci.yml` i runtime aplikacji pozostają bez zmian.

### Kluczowe odkrycia:

- `.github/workflows/ci.yml:1-30` — prosty CI, dwa joby, brak AI. Nowy workflow musi być w osobnym pliku (izolacja per FR-005)
- Brak root `package.json` — `.github/scripts/` będzie nowym katalogiem z własnym `package.json`
- `frontend/package.json` używa Node 22, npm — workflow powinien być spójny
- `.claude/skills/10x-impl-review-ci/references/workflow-template.yml` — istniejący wzorzec (claude-code-action), ale f-06 celowo wybiera composable agent (Vercel AI SDK) dla certyfikacji M5L2

## Czego NIE robimy

- Status check blokujący merge (v2 — wymaga decyzji "kiedy blokujemy")
- Labels `ai-cr:passed/failed` (v2 — kosmetyka)
- promptfoo evaluator (v2 — potrzebny gdy częsty tuning rubryki)
- Multi-model haiku triage → sonnet escalation (v2 — optymalizacja kosztów)
- Cost dashboard / onStepFinish monitoring (v2 — basic logs w workflow output wystarczą)
- Tools dla agenta (v2 — agent w MVP jest "scorerem", nie "aktorem")
- Chunking/splitting dużych diffów (v2 — MVP skip'uje review gdy diff za duży)
- Zmiany w istniejącym `ci.yml` — izolacja workflow jest twardą zasadą

## Podejście do implementacji

Trzy fazy: (1) scaffold skryptu review z Vercel AI SDK 6 + Zod schema + octokit, testowanie lokalne; (2) workflow GitHub Actions triggerowany na `pull_request`; (3) weryfikacja end-to-end z trial PR. Skrypt jest samodzielny w `.github/scripts/` z własnym `package.json`. Workflow przekazuje PR metadata (title, body, diff, PR number) jako env vars do skryptu.

## Krytyczne szczegóły implementacji

- **Diff delivery via temp file**: workflow zapisuje `git diff origin/main...HEAD` do pliku tymczasowego (`/tmp/pr_diff.txt`) i przekazuje ścieżkę jako `PR_DIFF_FILE` env var. Skrypt czyta `fs.readFileSync(process.env.PR_DIFF_FILE, 'utf-8')`. Powód: `$GITHUB_ENV` ma limit ~48KB per variable i ryzyko kolizji delimiter — plik tymczasowy eliminuje oba problemy.
- **Diff size guard**: jeśli odczytany diff przekracza ~50k znaków (~12.5k tokens, ~25% kontekstu Sonnet 4.6 200K), skrypt NIE wywołuje API — postuje info komentarz "Diff too large for automated AI review (X chars > 50K limit). Manual review required." i kończy z exit code 0 (graceful). Próg jest stałą w skrypcie, nie konfiguracją. Powód progu 50K (vs większy teoretyczny limit): cost discipline (oszczędność tokenów) + "honest about limits" (PR >50K zwykle to kitchen-sink change który powinien być rozbity manual) + duży bufor pod system prompt / instructions / output. Decyzja z shape-notes Sokrates #2 / FR-002 refinement.
- **Update-existing-comment**: komentarz zaczyna się od `<!-- ai-review-bot -->` HTML marker. Skrypt szuka istniejącego komentarza z tym markerem (octokit `listComments` + find) — jeśli znajdzie, update'uje zamiast tworzyć nowy. Eliminuje spam przy re-pushach do tego samego PR.
- **Conditional collapsibility**: jeśli werdykt = APPROVED, tabela + summary owinięte w `<details><summary>Full review</summary>...</details>` (compact). Jeśli NEEDS_ATTENTION lub REJECTED — expanded (bez `<details>`).

---

## Faza 1: Scaffold skryptu review

### Przegląd

Utworzenie `.github/scripts/` z `package.json`, `review.js` (composable agent) i Zod schema. Skrypt przyjmuje PR metadata z env vars, wywołuje Claude Sonnet 4.6 przez Vercel AI SDK 6, emituje structured output (JSON z ocenami + werdykt), postuje komentarz na PR przez octokit.

### Wymagane zmiany:

#### 1. Package manifest

**Plik**: `.github/scripts/package.json`

**Cel**: Deklaracja zależności dla skryptu review — Vercel AI SDK 6, Anthropic provider, Zod, octokit.

**Kontrakt**: `"type": "module"`, `"private": true`. Dependencies: `ai` (Vercel AI SDK 6), `@ai-sdk/anthropic`, `zod`, `@octokit/rest`. Brak devDependencies — skrypt uruchamiany tylko w CI. Po `npm install` lokalnie, commit zarówno `package.json` jak i `package-lock.json` (workflow używa `npm ci`, który wymaga lockfile).

#### 2. Zod schema dla structured output

**Plik**: `.github/scripts/review.js`

**Cel**: Definicja Zod schema dla structured output agenta — 5 kryteriów (nazwa, ocena 1-10, uzasadnienie) + summary + verdict enum.

**Kontrakt**: Schema definiuje obiekt z polami: `criteria` (array of `{ name: string, score: number (1-10), rationale: string }`), `summary` (string), `verdict` (enum: `APPROVED | NEEDS_ATTENTION | REJECTED`). Zod `.describe()` na każdym polu — opisy są częścią promptu dla modelu.

#### 3. System prompt i wywołanie agenta

**Plik**: `.github/scripts/review.js`

**Cel**: System prompt definiujący rolę reviewera + wywołanie `generateObject` z Vercel AI SDK 6 z diffem i PR metadata.

**Kontrakt**: 
- System prompt: rola code reviewer, 5 wymiarów rubryki (poprawność logiczna, idiomatyczność Java/Spring + React/TS, złożoność cyklomatyczna/czytelność, pokrycie testami, bezpieczeństwo OWASP top 10), skala 1-10, kryteria werdyktu (APPROVED: min 7 w każdym wymiarze, NEEDS_ATTENTION: min 1 wymiar 4-6, REJECTED: min 1 wymiar < 4).
- User message: PR title + PR body + diff (concatenated).
- `generateObject({ model: anthropic('claude-sonnet-4-6-20250514'), schema, system, prompt, temperature: 0, maxOutputTokens: 2000, maxRetries: 1 })`. Parametry: `temperature: 0` dla determinism (ten sam diff → ten sam werdykt, eliminuje noise — shape Sokrates #3 / FR-003 refinement); `maxOutputTokens: 2000` jako cap na structured JSON output (typical 800-1500 wystarczy); `maxRetries: 1` zamiast default 3 (cost discipline na network errors / 5xx — shape Sokrates #1). Uwaga: `stepCountIs` / `maxBudgetUsd` z M5L2 dotyczy `ToolLoopAgent` (multi-step tool calling), nie `generateObject` (single-shot extract); adekwatne caps dla naszego use case to `maxOutputTokens` + `maxRetries`. Model name `claude-sonnet-4-6-20250514` zweryfikuj w Anthropic docs przed kodem — alternatywa: canonical `claude-sonnet-4-6`.
- Env vars consumed: `PR_DIFF_FILE` (ścieżka do pliku z diffem), `PR_TITLE`, `PR_BODY`, `PR_NUMBER`, `GITHUB_REPOSITORY`, `GITHUB_TOKEN`, `ANTHROPIC_API_KEY`.

#### 4. Markdown formatter i poster komentarza

**Plik**: `.github/scripts/review.js`

**Cel**: Formatowanie structured output jako markdown komentarz i postowanie na PR przez octokit. Obsługa update-existing-comment, conditional collapsibility, diff size guard.

**Kontrakt**:
- **Diff read**: `const diff = fs.readFileSync(process.env.PR_DIFF_FILE, 'utf-8')`.
- **Diff size guard**: jeśli `diff.length > 50_000`, pomiń wywołanie API, postuj info komentarz, exit 0. (Próg z shape Sokrates #2; pełne uzasadnienie w sekcji "Krytyczne szczegóły implementacji" wyżej.)
- **Marker**: `<!-- ai-review-bot -->` jako pierwsza linia body komentarza.
- **Pierwsza linia widoczna**: werdykt emoji + tekst + key flags (wymiary z oceną < 7).
  Emoji: APPROVED → `🟢`, NEEDS_ATTENTION → `⚠️`, REJECTED → `🔴`.
  Format: `## ⚠️ NEEDS_ATTENTION — security: 4/10, tests: 6/10`
- **Body**: tabela 5 kryteriów (Criterion | Score | Rationale) + summary.
  Jeśli APPROVED: owinięte w `<details><summary>Full review</summary>...</details>`.
  Jeśli NEEDS_ATTENTION/REJECTED: expanded (bez `<details>`).
- **Footer**: `---\n🤖 AI Code Review (Claude Sonnet 4.6, temperature=0). Ocena wygenerowana przez LLM, skala 1-10 jest wskazówką.`
- **Update logic**: octokit `listComments` → find komentarz z `<!-- ai-review-bot -->` → jeśli znaleziono: `updateComment`; w przeciwnym razie: `createComment`.
- Error handling: jeśli API call lub posting failuje, log error na stderr, exit 0 (graceful — nie blokuj merge).

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- `cd .github/scripts && npm install` kończy się bez błędów
- `node --check .github/scripts/review.js` — brak błędów składni

#### Weryfikacja ręczna:

- Lokalne uruchomienie z mock env vars (PR_DIFF_FILE wskazujący na plik z małym diffem, ANTHROPIC_API_KEY z env, PR_TITLE/PR_BODY/PR_NUMBER mock) zwraca poprawny JSON z 5 ocenami i werdyktem
- Format komentarza markdown renderuje się poprawnie w GitHub markdown preview

---

## Faza 2: Workflow GitHub Actions

### Przegląd

Nowy workflow `.github/workflows/ai-review.yml` triggerowany na `pull_request` do `main`. Job: checkout z pełną historią, obliczenie diffu, instalacja zależności skryptu, uruchomienie `review.js` z env vars.

### Wymagane zmiany:

#### 1. Workflow YAML

**Plik**: `.github/workflows/ai-review.yml`

**Cel**: GitHub Actions workflow triggerujący AI review na każdy PR do main. Pojedynczy job z krokami: checkout, diff, setup Node, npm install, run review.js.

**Kontrakt**:
- Trigger: `on: pull_request: branches: [main]`
- Concurrency: `concurrency: { group: ai-review-${{ github.event.pull_request.number }}, cancel-in-progress: true }` — nowy push do PR kanceluje aktualnie biegnący job (oszczędność tokenów + unikanie race condition na update komentarza). Refinement z shape Sokrates #1 / FR-001.
- Permissions: `contents: read`, `pull-requests: write` (minimum dla checkout + komentarz na PR)
- Job `ai-review`, `runs-on: ubuntu-latest`:
  1. `actions/checkout@v5` z `fetch-depth: 0` (pełna historia do git diff)
  2. Step "Compute diff": `git diff origin/main...HEAD > /tmp/pr_diff.txt` (diff do pliku tymczasowego — omija limit 48KB GITHUB_ENV i ryzyko kolizji delimiter)
  3. `actions/setup-node@v5` z `node-version: '22'`
  4. `npm ci` w `working-directory: .github/scripts`
  5. Run `node .github/scripts/review.js` z env vars: `PR_DIFF_FILE: /tmp/pr_diff.txt`, `PR_TITLE: ${{ github.event.pull_request.title }}`, `PR_BODY: ${{ github.event.pull_request.body }}`, `PR_NUMBER: ${{ github.event.pull_request.number }}`, `GITHUB_REPOSITORY`, `GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}`, `ANTHROPIC_API_KEY: ${{ secrets.ANTHROPIC_API_KEY }}`
- `continue-on-error: true` na step z review.js — graceful failure, nie blokuje statusu PR

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Workflow YAML jest poprawny składniowo (brak błędów parsowania YAML)
- `actionlint .github/workflows/ai-review.yml` (jeśli dostępny) — brak ostrzeżeń

#### Weryfikacja ręczna:

- Workflow pojawia się w GitHub Actions UI (po push do brancha)
- Istniejący `ci.yml` nie ma żadnych zmian (git diff)

---

## Faza 3: Weryfikacja end-to-end

### Przegląd

Trial PR (micro-change) triggerujący workflow. Potwierdzenie: job zielony, komentarz na PR z rubryką + werdyktem, format poprawny, istniejący CI nienaruszony.

### Wymagane zmiany:

#### 1. GitHub Secret

**Plik**: n/a (GitHub Settings → Secrets → Actions)

**Cel**: Dodanie `ANTHROPIC_API_KEY` do GitHub Secrets repozytorium.

**Kontrakt**: Secret name: `ANTHROPIC_API_KEY`. Value: klucz API Anthropic. Scope: repo-level (nie environment-level).

#### 2. Trial PR

**Plik**: dowolna micro-zmiana (np. aktualizacja komentarza w README, trivialny refactor)

**Cel**: Trigger workflow ai-review, potwierdzenie end-to-end flow.

**Kontrakt**: 
- Otwórz PR z brancha na `main`
- Poczekaj na job `ai-review` w GitHub Actions
- Sprawdź: job zielony, komentarz na PR z markerem `<!-- ai-review-bot -->`, tabela 5 ocen, werdykt, footer
- Sprawdź: istniejący `ci.yml` job nadal zielony (niezależny)
- Re-push do tego samego PR: sprawdź, że komentarz jest update'owany (nie nowy)

### Kryteria sukcesu:

#### Weryfikacja automatyczna:

- Job `ai-review` kończy się statusem success w GitHub Actions
- Job `backend` i `frontend` z `ci.yml` nadal zielone (brak regresji)

#### Weryfikacja ręczna:

- Komentarz na PR zawiera: marker HTML, werdykt z emoji, tabelę 5 kryteriów, summary, footer
- Re-push do PR update'uje istniejący komentarz (nie tworzy nowego)
- Screen pipeline (zielony job) + screen komentarza = dowód do formularza certyfikacji
- Workflow wykonuje się w < 2 minuty

---

## Strategia testowania

### Testy jednostkowe:

- Brak formalnych unit testów w MVP — skrypt jest prosty (1 plik, ~150 linii). Weryfikacja przez lokalne uruchomienie z mock env vars.

### Testy integracyjne:

- Trial PR (Faza 3) jest de facto testem integracyjnym end-to-end.

### Kroki testowania ręcznego:

1. Lokalne uruchomienie `review.js` z env vars z prawdziwego diffu — sprawdź structured output
2. Push workflow do brancha — sprawdź że pojawia się w GitHub Actions UI
3. Trial PR — sprawdź komentarz na PR (format, werdykt, marker)
4. Re-push do trial PR — sprawdź update-existing-comment
5. Opcjonalnie: PR z dużym diffem (>50k znaków) — sprawdź info komentarz "Diff too large for automated AI review"

## Referencje

- Shape notes: `context/foundation/shape-notes.md`
- Istniejący CI: `.github/workflows/ci.yml`
- Vercel AI SDK 6 docs: `https://sdk.vercel.ai/docs`
- M5L2 (SDK): `.10xdevs/m5l2_twoj-pierwszy-agent-zespolowy-sdk-koszty-metryki.md`
- M5L3 (Pipeline): `.10xdevs/m5l3_code-review-w-erze-ai-standardy-dod-i-agent-w-pipeline.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles.

### Phase 1: Scaffold skryptu review

#### Automated

- [x] 1.1 npm install w .github/scripts/ kończy się bez błędów
- [x] 1.2 node --check .github/scripts/review.js — brak błędów składni

#### Manual

- [x] 1.3 Lokalne uruchomienie z mock env vars zwraca poprawny JSON z 5 ocenami i werdyktem (verdict NEEDS_ATTENTION, graceful failure na dummy GITHUB_TOKEN potwierdzony — pipeline end-to-end działa)
- [ ] 1.4 Format komentarza markdown renderuje się poprawnie w GitHub markdown preview — deferred do Phase 3 (naturalnie pokryje się trial PR)

### Phase 2: Workflow GitHub Actions

#### Automated

- [x] 2.1 Workflow YAML poprawny składniowo

#### Manual

- [ ] 2.2 Workflow pojawia się w GitHub Actions UI — deferred do Phase 3 (wymaga push, naturalnie pokryje się trial PR)
- [x] 2.3 Istniejący ci.yml nie ma żadnych zmian (git diff main -- .github/workflows/ci.yml = pusty)

### Phase 3: Weryfikacja end-to-end

#### Automated

- [ ] 3.1 Job ai-review kończy się statusem success
- [ ] 3.2 Job backend i frontend z ci.yml nadal zielone

#### Manual

- [ ] 3.3 Komentarz na PR zawiera marker, werdykt, tabelę, summary, footer
- [ ] 3.4 Re-push do PR update'uje istniejący komentarz
- [ ] 3.5 Screen pipeline + screen komentarza jako dowód certyfikacji
- [ ] 3.6 Workflow wykonuje się w < 2 minuty
