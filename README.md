# Political Discourse Analyzer

> NLP pipeline for multi-dimensional analysis of German political speech — sentiment, rhetorical intensity, value framing, and communicative intent.

Analyzed **2 public figures** · **5 data sources** · **12 linguistic dimensions**

---

## What This Does

This project collects and analyzes public statements by German politicians (Annalena Baerbock, Olaf Scholz, Ursula von der Leyen) from 2021–2026 and classifies them across multiple linguistic dimensions using a hybrid rule-based + transformer-based pipeline.

**Research question:** How does the rhetorical and emotional profile of a politician's public communication evolve over time, and what value systems underpin their discourse?

---

## Quick Start

### With Docker (recommended)

**Linux / macOS:**
```bash
cp .env.example .env
# fill in DB_PASSWORD in .env

# Start PostgreSQL + FastAPI classifier
docker compose up -d postgres classifier

# Run the analysis pipeline (Baerbock + Scholz)
docker compose --profile run up batch-runner
```

**Windows (CMD):**
```cmd
copy .env.example .env
# open .env in any editor and set DB_PASSWORD

docker compose up -d postgres classifier
docker compose --profile run up batch-runner
```

> `.env` is listed in `.gitignore` — never commit it. Only `.env.example` goes to the repository.

The classifier downloads `facebook/bart-large-mnli` (~1.6 GB) on first start and caches it in a Docker volume — subsequent starts are fast.

Charts are saved to `./output/` on your host machine.

### Without Docker

See the [Setup](#setup) section below for manual installation.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    DATA COLLECTION (Java)                    │
│                                                              │
│  BaerbockSpeechScraper   ScholzSpeechScraper   NYTFetcher    │
│  Selenium + Jsoup        Selenium + Jsoup      REST API      │
│                                                              │
│  NewsAPIFetcher          VonDerLeyenScraper    GNewsFetcher  │
│  REST API                Selenium + Jsoup      REST API      │
│                                                              │
│                    PostgreSQL (news_db)                      │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│              CLASSIFICATION SERVICE (Python / FastAPI)       │
│                                                              │
│  POST /classify  ←  ClassifierClient.java (HTTP POST)        │
│                                                              │
│  facebook/bart-large-mnli  (zero-shot)                       │
│    → emotion · intent · formality                            │
│  German lexicons  (rule-based)                               │
│    → emotion_intensity · value_density · dominant_value      │
│    → r_value · theatricality · sarcasm_flag · modal_strength │
│    → contrast_present · collective_frame · pronoun_ratio     │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│                  BATCH ANALYSIS PIPELINE (Python)            │
│                                                              │
│  11_read_save_data.py  →  12_fill_model.py                   │
│  13_fill_value.py      →  14_fill_rhetoric.py                │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│               VISUALIZATION (Python / matplotlib)            │
│                                                              │
│  Emotional polarity · Intent · Formality (stacked bar)       │
│  Value density · Key values by year (line chart)             │
│  R-value · Theatricality · Modal strength (time series)      │
│  Sarcasm markers · Repetition score (time series)            │
└─────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Technologies |
|---|---|
| Data collection | Java 17, Selenium 4, Jsoup, HttpClient |
| APIs | NYT Article Search API, NewsAPI, GNews API |
| Storage | PostgreSQL 15 |
| Classification service | Python 3.11, FastAPI, Uvicorn |
| NLP | Hugging Face Transformers, `facebook/bart-large-mnli` |
| Language detection | `langdetect` |
| Analysis | pandas, numpy |
| Visualization | matplotlib, seaborn |
| Build | Maven 3 |

---

## Linguistic Dimensions Analyzed

### Transformer-based (zero-shot, `bart-large-mnli`)
- **Emotion** — negative / neutral / positive
- **Intent** — declarative / imperative / evaluative
- **Formality** — formal / informal

### Lexicon-based
- **Emotion intensity** — amplifier count + exclamation markers
- **8 value categories** — security, responsibility, solidarity, democracy, national interests, European values, stability, economy
- **Value density** — ratio of value-bearing words to total word count

### Rule-based rhetorical features
- **R-value** — composite score: `3·modal + 1·contrast + 2·collective + 3·value_density + 2·emotion_intensity + 2·theatricality + 2·pronoun_ratio`
- **Modal strength** — obligation constructions (müssen, sollen, verpflichtet...)
- **Contrast presence** — adversative connectors (aber, jedoch, trotzdem...)
- **Collective framing** — we/our/together appeals
- **Theatricality** — high-impact lexicon (historisch, epochal, Wendepunkt...)
- **Sarcasm flag** — irony marker detection
- **Repetition score** — lexical diversity inverse
- **Pronoun ratio**, **average sentence length**

---

## Data Sources

| Source | Method | Notes |
|---|---|---|
| Auswärtiges Amt (German MFA) | Selenium + Jsoup, paginated | Official speeches, press releases, interviews — public domain |
| EU Commission | Selenium + Jsoup | Von der Leyen public statements |
| New York Times | NYT Article Search API v2, paginated | Official public API, key required |
| NewsAPI | REST, keyword search | Official public API, key required |
| GNews | REST | Official public API, key required |

> **Note on Twitter / X:** A Selenium-based scraper (`TwitterScrapper.java`) was used during the research phase in 2024. It is included for completeness but is marked **deprecated** — X's ToS prohibits automated scraping without API access, and the free API tier was discontinued in 2023. Do not use it.

---

## Running the Classifier Service

The FastAPI server wraps the NLP pipeline and lets Java (or any HTTP client) call it:

```bash
pip install fastapi uvicorn transformers torch psycopg2-binary \
            pandas langdetect python-dotenv

uvicorn classifier_server:app --host 0.0.0.0 --port 8000
```

**Example request:**
```bash
curl -X POST http://localhost:8000/classify \
     -H "Content-Type: application/json" \
     -d '{"text": "Wir müssen gemeinsam Verantwortung übernehmen für die Zukunft Europas."}'
```

**Example response:**
```json
{
  "emotion": "positive",
  "intent": "imperative",
  "formality": "formal",
  "emotion_intensity": 1,
  "value_density": 0.0667,
  "dominant_value": "responsibility",
  "modal_strength": 1,
  "collective_frame": 1,
  "contrast_present": 0,
  "theatricality": 0,
  "sarcasm_flag": 0,
  "r_value": 10
}
```

Interactive docs available at `http://localhost:8000/docs` (Swagger UI).

---

## Setup

### Prerequisites
- Java 17+, Maven 3
- Python 3.11+
- PostgreSQL 15
- Chrome + ChromeDriver (matching version)

### Database
```sql
CREATE DATABASE news_db;
```

### Configuration
```bash
cp .env.example .env
# fill in .env with your values
```

### Python dependencies
```bash
pip install fastapi uvicorn transformers torch pandas numpy \
            langdetect psycopg2-binary matplotlib seaborn \
            tqdm python-dotenv
```

### Run — full pipeline

```bash
# 1. Start the classifier service
uvicorn classifier_server:app --port 8000 &

# 2. Collect data (Java)
mvn compile exec:java -Dexec.mainClass="nir.parsing.BaerbockSpeechScraper"

# 3. Batch analysis pipeline (in order)
cd python/berbok
python 11_read_save_data.py
python 12_fill_model.py      # ~1–2h on CPU, faster with GPU
python 13_fill_value.py
python 14_fill_rhetoric.py

# 4. Visualize
cd ../visual
python visual_baerbock.py
python visual_scholz.py
```

---

## Project Structure

```
nir/
├── classifier_server.py           # FastAPI NLP service
├── .env.example                   # Configuration template
├── .gitignore
├── pom.xml
├── python/
│   ├── berbok/                    # Baerbock analysis pipeline
│   │   ├── 11_read_save_data.py
│   │   ├── 12_fill_model.py
│   │   ├── 13_fill_value.py
│   │   └── 14_fill_rhetoric.py
│   ├── scholz/                    # Scholz pipeline (same structure)
│   └── visual/
│       ├── visual_baerbock.py
│       └── visual_scholz.py
└── src/main/java/nir/
    ├── parsing/                   # Data collectors
    │   ├── BaerbockSpeechScraper.java
    │   ├── ScholzSpeechScraper.java
    │   ├── VonDerLeyenScraper.java
    │   ├── NYTArticleSearchFetcher.java
    │   ├── NewsAPIFetcher.java
    │   ├── GNewsFetcher.java
    │   └── StatementSaver.java
    └── analysis/
        ├── App.java
        ├── ClassifierClient.java  # HTTP POST → /classify
        └── DBService.java
```

---

## Key Design Decisions

**Why Java for collection + Python for analysis?**
Java's Selenium and `HttpClient` give fine-grained control over request timing, cookie handling, and pagination. Python owns the NLP ecosystem. PostgreSQL acts as the handoff layer between the two runtimes.

**Why zero-shot classification instead of a fine-tuned model?**
No labeled training data for German political speech was available. `bart-large-mnli` classifies directly against human-readable labels without annotation overhead, and the labels map exactly to the research categories.

**Why rule-based rhetoric features?**
Rhetorical constructs (collective framing, modal obligation, theatrical vocabulary) are linguistically well-defined. Rule-based extraction keeps feature semantics transparent and traceable to specific lexical choices — important for a research context.

**Why FastAPI instead of calling Python directly from Java?**
Decouples the runtimes cleanly. The classifier model loads once and stays in memory; Java calls it over HTTP as many times as needed without reloading 2GB of weights each time.

---

## Legal & Ethical Notes

All data was collected for **academic research purposes** from publicly available sources.

- **Official government websites** (Auswärtiges Amt, EU Commission) — public domain, standard practice in computational linguistics and political science research.
- **NYT / NewsAPI / GNews** — collected via official public APIs with valid API keys under their respective terms of service.
- **Twitter / X** — the scraper used session-cookie authentication during the 2024 research phase, before X fully deprecated free API access. This component is **deprecated** and excluded from the active pipeline. No tweet content is included in the repository.

No personal data beyond public political statements was collected or stored.

---

## Potential Extensions

- Replace lexicon-based value detection with a fine-tuned German value classifier
- Extend pipeline to additional politicians or other languages (modular per-person structure makes this straightforward)
- Add Streamlit or Dash dashboard for interactive visualization
