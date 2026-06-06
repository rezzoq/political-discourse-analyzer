# название файла 11_read_save_data.py

import psycopg2
import pandas as pd
from langdetect import detect, LangDetectException
from transformers import MarianMTModel, MarianTokenizer
import torch
from tqdm import tqdm
import os
from dotenv import load_dotenv
load_dotenv()

def load_data():
    conn = psycopg2.connect(
        dbname=os.getenv("DB_NAME", "news_db"),
        user=os.getenv("DB_USER", "postgres"),
        password=os.getenv("DB_PASSWORD"),
        host=os.getenv("DB_HOST", "localhost"),
        port=int(os.getenv("DB_PORT", 5432))
    )

    query = """
        SELECT
            person,
            quote,
            title,
            published_at,
            source,
            url
        FROM baerbok_quotes
        WHERE source = 'Auswärtiges Amt'
        ORDER BY published_at
        """
    df = pd.read_sql(query, conn)
    conn.close()
    return df

df = load_data()

# ----------------------------
# 2. Определение языка
# ----------------------------
def detect_lang(text):
    try:
        return detect(text)
    except LangDetectException:
        return "unknown"

def normalize_quote(text):
    lang = detect_lang(text)
    if lang == "de":
        return text        # оставляем немецкий как есть
    else:
        return None        # отбрасываем всё, что не немецкий

df["quote"] = df["quote"].apply(normalize_quote)
df = df[df["quote"].notna()].reset_index(drop=True)

# применяем к колонке quote
# применяем к колонке quote с прогресс-баром

#quotes = []
#for text in tqdm(df["quote"], desc="Перевод"):
#    quotes.append(normalize_quote(text))
#df["quote"] = quotes
# Убираем None после фильтрации остальных языков
df = df[df["quote"].notna()].reset_index(drop=True)

# Добавим пустые колонки
df["intent"] = ""        # 1 декларативное / 2 императивное / 3 эвальоративное
df["emotion"] = ""       # −1 / 0 / +1 (полярность)
df["emotion_intensity"] = ""  # числовой признак, автоматически
df["formality"] = ""     # 0 неформ / 1 формально

# Колонки ценностей. 1 — явно присутствует. 0 — отсутствует
df["value_security"] = ""        # безопасность
df["value_responsibility"] = "" # ответственность
df["value_solidarity"] = ""     # солидарность
df["value_democracy"] = ""      # демократия / право
df["value_national"] = ""       # национальные интересы
df["value_europe"] = ""         # европейские ценности

# РИТОРИЧЕСКИЕ КОНСТРУКЦИИ. Это не эмоции, не intent — это как сказано
df["rhetorical_intensity"] = ""   # 0 / 1 / 2 насыщенность риторическими приёмами
df["r_value"] = ""   # количественная риторическая насыщенность R (формула 2)
df["modal_strength"] = ""         # 0 / 1 есть ли долженствование
df["contrast_present"] = ""       # 0 / 1 есть ли противопоставление
df["collective_frame"] = ""       # 0 / 1 (we / our / together) апелляция к коллективу

# Диагностика
df["sent_len"] = ""           # средняя длина предложения
df["pronoun_ratio"] = ""      # доля местоимений
df["modal_count"] = ""        # количество must / should
df["exclamation"] = ""        # ! присутствует

# Сохраним для ручной разметки
df.to_csv("labeled_statements.csv", index=False, encoding="utf-8")
print("Данные сохранены в labeled_statements.csv")

