# название файла 14_fill_rhetoric.py

import pandas as pd
import re

# ----------------------------
# 1. Правила риторики
# ----------------------------
AMPLIFIERS = ["absolutely", "extremely", "very", "completely", "totally", "utterly", "highly", "perfectly", "strongly"]
MODALS = ["must", "should", "have to", "need to", "shall", "obliged", "required", "должны", "обязаны"]
CONTRASTS = ["but", "however", "although", "yet", "though", "но", "однако", "всё же"]
COLLECTIVE = ["we", "our", "us", "together", "ourselves"]

PRONOUNS = ["i","you","he","she","it","we","they","me","him","her","us","them","my","your","his","her","its","our","their"]
THEATRICAL_WORDS = [
    "historic", "unprecedented", "crucial", "dramatic",
    "turning point", "epoch", "decisive", "fundamental"
]

# ----------------------------
# 2. Функции подсчёта
# ----------------------------
def count_matches(text, words):
    text_lower = text.lower()
    return sum(text_lower.count(word.lower()) for word in words)

def detect_rhetoric(text):
    # Усилители
    amp_count = count_matches(text, AMPLIFIERS)
    # модальные конструкции
    modal_count = count_matches(text, MODALS)
    modal_strength = 1 if modal_count > 0 else 0
    # противопоставления
    contrast_count = count_matches(text, CONTRASTS)
    contrast_present = 1 if contrast_count > 0 else 0
    # коллективные апелляции
    collective_count = count_matches(text, COLLECTIVE)
    collective_frame = 1 if collective_count > 0 else 0
    # риторическая насыщенность
    rhetoric_score = amp_count + contrast_count + modal_count + collective_count
    if rhetoric_score == 0:
        rhetorical_intensity = 0
    elif rhetoric_score <= 2:
        rhetorical_intensity = 1
    else:
        rhetorical_intensity = 2
    # диагностика
    sentences = re.split(r'[.!?]', text)
    sentences = [s.strip() for s in sentences if s.strip()]
    sent_len = sum(len(s.split()) for s in sentences)/len(sentences) if sentences else 0
    pronoun_ratio = sum(count_matches(text, [p]) for p in PRONOUNS)/len(text.split()) if len(text.split())>0 else 0
    exclamation = 1 if '!' in text else 0

    return {
        "rhetorical_intensity": rhetorical_intensity,
        "modal_strength": modal_strength,
        "contrast_present": contrast_present,
        "collective_frame": collective_frame,
        "sent_len": round(sent_len,2),
        "pronoun_ratio": round(pronoun_ratio,2),
        "modal_count": modal_count,
        "exclamation": exclamation
    }

def detect_theatricality(text):
    text_lower = text.lower()
    return sum(text_lower.count(w) for w in THEATRICAL_WORDS)

def repetition_score(text):
    words = text.lower().split()
    return len(words) - len(set(words))

IRONY_EN = [
    "obviously", "sure", "yeah, right", "as if", "definitely",
    "clearly", "no doubt", "absolutely", "perfect", "wonderful"
]

def detect_sarcasm_en(text):
    text_lower = text.lower()
    return 1 if any(w in text_lower for w in IRONY_EN) else 0
# ----------------------------
# 3. Загружаем данные
# ----------------------------
df = pd.read_csv("labeled_statements_filled_values.csv")

# ----------------------------
# 3.1 Вычисляем новые признаки театральности и повторов
# ----------------------------
df["theatricality"] = df["quote"].apply(detect_theatricality)
df["repetition"] = df["quote"].apply(repetition_score)
df["sarcasm_flag"] = df["quote"].apply(detect_sarcasm_en)
# ----------------------------
# 4. Заполняем колонки риторики
# ----------------------------
for index, row in df.iterrows():
    text = row['quote']
    features = detect_rhetoric(text)
    # ... сохранение features ...

    word_count = len(text.split())
    N = max(word_count, 1)

    # частоты из features и уже имеющихся колонок
    modal_freq = features['modal_count'] / N
    contrast_freq = count_matches(text, CONTRASTS) / N
    collective_freq = count_matches(text, COLLECTIVE) / N
    v_dens = row['value_density']  # уже есть

    # эмоциональная интенсивность (emotion_intensity уже посчитана ранее)
    emotion_intensity = row['emotion_intensity']
    emotion_intensity_freq = emotion_intensity / N

    # театральность (уже посчитана в theatricality)
    theatricality = row['theatricality']
    theatricality_freq = theatricality / N

    # доля местоимений (уже в features)
    pronoun_ratio = features['pronoun_ratio']


    # Новая формула R
    r_value = (3 * modal_freq
               + 1 * contrast_freq
               + 2 * collective_freq
               + 3 * v_dens
               + 2 * emotion_intensity_freq
               + 2 * theatricality_freq
               + 2 * pronoun_ratio)

    df.loc[index, "r_value"] = round(r_value, 5)
# ----------------------------
# 5. Сохраняем результат
# ----------------------------
# Преобразуем пустые строки в NaN
df.replace("", pd.NA, inplace=True)

# Удалим строки, где quote или title пустые
df.dropna(subset=["quote", "title"], inplace=True)

# Сбросим индексы
df.reset_index(drop=True, inplace=True)


df.to_csv("labeled_statements_filled_rhetoric.csv", index=False, encoding="utf-8")
print("Готово! Риторические признаки добавлены и сохранены в labeled_statements_filled_rhetoric.csv")
import shutil, os
# Убедимся, что папка visual существует
os.makedirs("../../visual", exist_ok=True)
shutil.copy("labeled_statements_filled_rhetoric.csv", "../visual/scholz.csv")
print("Финальный CSV скопирован в visual/")