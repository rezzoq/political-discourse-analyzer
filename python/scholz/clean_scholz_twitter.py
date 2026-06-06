import psycopg2
import pandas as pd
import re
import spacy
from tqdm import tqdm
import os
from dotenv import load_dotenv
load_dotenv()
# =========================
# 1. НАСТРОЙКИ
# =========================
DB_CONFIG = {
    "dbname": "news_db",
    "user": "postgres",
    "password": os.getenv("DB_PASSWORD"),
    "host": "localhost",
    "port": 5432
}

PERSON_KEYWORDS = [
    # === Базовое имя ===
    "Scholz",
    "Olaf Scholz",
    "Olaf",

    # === Должности (англ.) ===
    "Bundeskanzler",
    "Chancellor",
    "German Chancellor",
    "Germany's chancellor",
    "German leader",

    # === Должности (нем.) ===
    "Kanzler",
    "Bundeskanzler Scholz",
    "Kanzler Scholz",

    # === Должности (рус.) ===
    "канцлер",
    "канцлер Германии",
    "федеральный канцлер",
    "Олаф Шольц",
    "Шольц",

    # === Формы с титулами ===
    "Mr. Scholz",
    "Herr Scholz",

    # === Частые журналистские конструкции ===
    "the chancellor said",
    "the German chancellor said",
    "Scholz said",
    "Scholz said that",
    "Scholz said on",
    "Scholz told",
    "Scholz added",
    "Scholz stressed",
]

MIN_QUOTE_LEN = 10  # теперь ловим короткие цитаты
MAX_QUOTE_LEN = 1000
QUOTE_PATTERN = rf'“([^”]{{{MIN_QUOTE_LEN},{MAX_QUOTE_LEN}}})”|\"([^"]{{{MIN_QUOTE_LEN},{MAX_QUOTE_LEN}}})\"'

TARGET_DATE = "2026-01-24"

# =========================
# 2. spaCy NER (опционально)
# =========================
print("🔄 Загружаем NER модель spaCy...")
nlp = spacy.load("en_core_web_trf")

def ner_mentions_scholz(text: str) -> bool:
    doc = nlp(text)
    for ent in doc.ents:
        if ent.label_ == "PERSON" and "Scholz" in ent.text:
            return True
    return False

def extract_quotes(text: str):
    matches = re.findall(QUOTE_PATTERN, text)
    quotes = [q[0] or q[1] for q in matches]
    return quotes

def contains_person(text: str) -> bool:
    return any(k.lower() in text.lower() for k in PERSON_KEYWORDS)

# =========================
# 3. Загрузка данных
# =========================
print("📥 Загружаем данные из БД...")
conn = psycopg2.connect(**DB_CONFIG)

query = f"""
SELECT id, title, content, url, source, published_at, created_at
FROM statements
WHERE content IS NOT NULL
AND created_at <= '{TARGET_DATE}'
"""
df = pd.read_sql(query, conn)
conn.close()
print(f"Всего строк до {TARGET_DATE}: {len(df)}")

# =========================
# 4. Очистка и извлечение цитат
# =========================
clean_rows = []

for _, row in tqdm(df.iterrows(), total=len(df)):
    text = row["content"]

    # Фильтр по имени
    if not contains_person(text):
        continue

    # Проверка NER (можно отключить, если медленно)
    if not ner_mentions_scholz(text):
        continue

    # Извлечение цитат
    quotes = extract_quotes(text)
    if not quotes:
        continue

    # Дата публикации
    pub_date = row["published_at"]
    if not pub_date or str(pub_date).strip() == "" or str(pub_date).startswith("0000"):
        pub_date = row["created_at"]  # fallback на created_at

    for q in quotes:
        if MIN_QUOTE_LEN <= len(q) <= MAX_QUOTE_LEN:
            clean_rows.append({
                "person": "Olaf Scholz",
                "quote": q.strip(),
                "published_at": pub_date,
                "title": row["title"],
                "url": row["url"],
                "source": row["source"],
                "created_at": row["created_at"]
            })

# =========================
# 5. Сохранение
# =========================
clean_df = pd.DataFrame(clean_rows)
print(f"✅ Получено чистых высказываний: {len(clean_df)}")

# В CSV
clean_df.to_csv("scholz_quotes.csv", index=False, encoding="utf-8")
print("💾 Сохранено в scholz_quotes.csv")

# В PostgreSQL новую таблицу (если нужна)
conn = psycopg2.connect(**DB_CONFIG)
cur = conn.cursor()
cur.execute("""
CREATE TABLE IF NOT EXISTS scholz_quotes (
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

# Сохраняем строки
for _, row in clean_df.iterrows():
    cur.execute("""
    INSERT INTO scholz_quotes (person, quote, published_at, title, url, source, created_at)
    VALUES (%s,%s,%s,%s,%s,%s,%s)
    """, (row["person"], row["quote"], row["published_at"], row["title"], row["url"], row["source"], row["created_at"]))
conn.commit()
cur.close()
conn.close()
print("💾 Сохранено в таблицу PostgreSQL 'scholz_quotes'")
