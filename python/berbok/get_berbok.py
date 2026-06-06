import psycopg2
import pandas as pd
import re
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

MIN_QUOTE_LEN = 20   # минимальная длина «цитаты» после разбиения

# =========================
# 2. Загрузка данных
# =========================
print("📥 Загружаем новые записи из statements...")
conn = psycopg2.connect(**DB_CONFIG)

query = f"""
SELECT id, content, title, url, source, created_at, published_at
FROM statements
WHERE source = 'Auswärtiges Amt'
AND content IS NOT NULL
"""
df = pd.read_sql(query, conn)
conn.close()
print(f"Всего новых строк: {len(df)}")

# =========================
# 3. Разделение длинного текста на предложения
# =========================
def split_into_sentences(text, min_len=MIN_QUOTE_LEN):
    # Простое разделение по точкам, вопросительным и восклицательным знакам
    sentences = re.split(r'(?<=[.!?])\s+', text)
    return [s.strip() for s in sentences if len(s.strip()) >= min_len]

rows = []
for _, row in df.iterrows():
    sentences = split_into_sentences(row["content"])
    for s in sentences:
        rows.append({
            "person": "Annalena Baerbock",
            "quote": s,
            "published_at": row["published_at"],
            "title": row["title"],
            "url": row["url"],
            "source": row["source"],
            "created_at": row["created_at"]
        })

clean_df = pd.DataFrame(rows)
print(f"✅ Получено записей после разбиения: {len(clean_df)}")

# =========================
# 4. Сохраняем в CSV
# =========================
clean_df.to_csv("berbok_quotes_new.csv", index=False, encoding="utf-8")
print("💾 Сохранено в berbok_quotes_new.csv")

# =========================
# 5. Сохраняем в PostgreSQL
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
