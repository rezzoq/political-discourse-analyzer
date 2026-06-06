# название файла 11_read_save_data.py

import psycopg2
import pandas as pd
from langdetect import detect, LangDetectException
from transformers import MarianMTModel, MarianTokenizer

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
        FROM scholz_quotes
        WHERE source = 'Bundesregierung Archive'
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

# ----------------------------
# 3. Перевод DE → EN (in-place)
# ----------------------------
MODEL_DE_EN = "Helsinki-NLP/opus-mt-de-en"
tokenizer_de_en = MarianTokenizer.from_pretrained(MODEL_DE_EN)
model_de_en = MarianMTModel.from_pretrained(MODEL_DE_EN)

MAX_TOKENS = 400  # длина чанка для перевода

def translate_de_chunked(text):
    # Токенизация
    tokens = tokenizer_de_en(text, return_tensors="pt")["input_ids"][0]
    chunks = []
    for i in range(0, len(tokens), MAX_TOKENS):
        chunk_tokens = tokens[i:i+MAX_TOKENS].unsqueeze(0)
        try:
            # Добавил параметры для стабильности и обрезание длины ответа
            with torch.no_grad():
                translated_chunk = model_de_en.generate(
                    chunk_tokens,
                    max_length=min(chunk_tokens.shape[1] + 50, 512),
                    num_beams=2,
                    early_stopping=True
                )
            decoded_chunk = tokenizer_de_en.decode(translated_chunk[0], skip_special_tokens=True)
            chunks.append(decoded_chunk)
        except Exception as e:
            print(f"Ошибка перевода: {e}. Текст: {text[:100]}...")
            chunks.append("[TRANSLATION ERROR]")
    return " ".join(chunks)

def normalize_quote(text):
    lang = detect_lang(text)
    if lang == "de":
        return translate_de_chunked(text)
    elif lang == "en":
        return text
    else:
        return None

# применяем к колонке quote
df["quote"] = df["quote"].apply(normalize_quote)

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

