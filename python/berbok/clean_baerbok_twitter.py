import psycopg2
import pandas as pd
import spacy
from tqdm import tqdm
import os
from dotenv import load_dotenv
load_dotenv()
# =========================
# 1. Настройки
# =========================
DB_CONFIG = {
    "dbname": "news_db",
    "user": "postgres",
    "password": os.getenv("DB_PASSWORD"),
    "host": "localhost",
    "port": 5432
}

# Ключевые слова для Баербока
PERSON_KEYWORDS = [
    "Baerbock", "Annalena Baerbock", "Annalena",
    "Foreign Minister", "German Foreign Minister",
    "Außenministerin", "Außenministerin Baerbock",
    "Frau Baerbock",
    "Baerbock said", "Baerbock stated", "Baerbock added"
]

# =========================
# 2. spaCy NER (опционально)
# =========================
print("🔄 Загружаем NER модель spaCy...")
nlp = spacy.load("en_core_web_trf")

def ner_mentions_baerbock(text: str) -> bool:
    doc = nlp(text)
    for ent in doc.ents:
        if ent.label_ == "PERSON" and "Baerbock" in ent.text:
            return True
    return False

def contains_person(text: str) -> bool:
    return any(k.lower() in text.lower() for k in PERSON_KEYWORDS)

# =========================
# 3. Загрузка данных
# =========================
print("📥 Загружаем новые записи из statements...")
conn = psycopg2.connect(**DB_CONFIG)

query = f"""
SELECT id, content, title, url, source, created_at, published_at
FROM statements
WHERE content IS NOT NULL
"""
df = pd.read_sql(query, conn)
conn.close()
print(f"Всего новых строк: {len(df)}")

# =========================
# 4. Фильтрация по Баербоку и NER
# =========================
clean_rows = []

for _, row in tqdm(df.iterrows(), total=len(df)):
    text = row["content"]

    # Фильтр по имени
    if not contains_person(text):
        continue

    # Проверка NER (можно отключить, если медленно)
    if not ner_mentions_baerbock(text):
        continue

    # Дата публикации
    pub_date = row["published_at"]
    if not pub_date or str(pub_date).strip() == "" or str(pub_date).startswith("0000"):
        pub_date = row["created_at"]

    clean_rows.append({
        "person": "Annalena Baerbock",
        "quote": text.strip(),  # сохраняем целиком
        "published_at": pub_date,
        "title": row["title"],
        "url": row["url"],
        "source": row["source"],
        "created_at": row["created_at"]
    })

# =========================
# 5. Сохраняем в DataFrame
# =========================
clean_df = pd.DataFrame(clean_rows)
print(f"✅ Получено записей: {len(clean_df)}")

# В CSV
clean_df.to_csv("baerbok_quotes.csv", index=False, encoding="utf-8")
print("💾 Сохранено в baerbok_quotes.csv")

# =========================
# 6. Сохраняем в PostgreSQL
# =========================
conn = psycopg2.connect(**DB_CONFIG)
cur = conn.cursor()
cur.execute("""
CREATE TABLE IF NOT EXISTS baerbok_quotes (
    person TEXT,
    quote TEXT,
    published_at TIMESTAMP,
    title TEXT,
    url TEXT,
    source TEXT,
    created_at TIMESTAMP
)
""")
conn.commit()

for _, row in clean_df.iterrows():
    cur.execute("""
    INSERT INTO baerbok_quotes (person, quote, published_at, title, url, source, created_at)
    VALUES (%s,%s,%s,%s,%s,%s,%s)
    """, (
        row["person"], row["quote"], row["published_at"],
        row["title"], row["url"], row["source"], row["created_at"]
    ))
conn.commit()
cur.close()
conn.close()
print("💾 Сохранено в таблицу PostgreSQL 'baerbok_quotes'")
