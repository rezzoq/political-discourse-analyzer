# название файла 13_fill_value.py

import pandas as pd

# ----------------------------
# 1. Словари ценностей
# ----------------------------
SECURITY_LEXICON = [
    "security", "safety", "defence", "defense", "protect", "protection",
    "threat", "risk", "stability", "peace", "order", "NATO", "deterrence"
]

RESPONSIBILITY_LEXICON = [
    "responsibility", "accountability", "duty", "obligation", "commitment",
    "liability", "role", "answerable"
]

SOLIDARITY_LEXICON = [
    "solidarity", "together", "unity", "support", "stand with", "allies", "partners"
]

DEMOCRACY_LEXICON = [
    "democracy", "rights", "freedom", "justice", "law", "equality", "rule of law"
]

STABILITY_LEXICON = [
    "stability", "order", "continuity", "predictable", "calm", "secure"
]

NATIONAL_LEXICON = [
    "national", "sovereignty", "territory", "citizens", "homeland", "state", "independence"
]

ECONOMY_LEXICON = [
    "economy", "financial", "growth", "sustainable", "budget", "investment", "market"
]

EUROPE_LEXICON = [
    "Europe", "EU", "European", "integration", "union", "Brussels", "euro"
]

VALUE_LEXICONS = {
    "value_security": SECURITY_LEXICON,
    "value_responsibility": RESPONSIBILITY_LEXICON,
    "value_solidarity": SOLIDARITY_LEXICON,
    "value_democracy": DEMOCRACY_LEXICON,
    "value_stability": STABILITY_LEXICON,
    "value_national": NATIONAL_LEXICON,
    "value_economy": ECONOMY_LEXICON,
    "value_europe": EUROPE_LEXICON
}

# ----------------------------
# 2. Функция подсчёта
# ----------------------------
def detect_values(text, lexicons=VALUE_LEXICONS):
    text_lower = text.lower()
    counts = {}
    raw_counts = {}
    total_count = 0
    for key, lex in lexicons.items():
        c = sum(text_lower.count(word.lower()) for word in lex)
        counts[key] = 1 if c > 0 else 0
        raw_counts[key] = c
        total_count += c

    # value_density — доля слов ценностей относительно всех слов
    word_count = len(text.split())
    value_density = total_count / word_count if word_count > 0 else 0
    # dominant_value — категория с наибольшим числом совпадений
    dominant_value = max(raw_counts.items(), key=lambda x: x[1])[0] if total_count > 0 else None
    return counts, value_density, dominant_value

# ----------------------------
# 3. Подгружаем данные
# ----------------------------
df = pd.read_csv("labeled_statements_filled.csv")

# ----------------------------
# 4. Заполняем колонки ценностей
# ----------------------------
for index, row in df.iterrows():
    counts, density, dominant = detect_values(row['quote'])
    for key, val in counts.items():
        df.loc[index, key] = val
    df.loc[index, "value_density"] = density
    df.loc[index, "dominant_value"] = dominant

# ----------------------------
# 5. Сохраняем результат
# ----------------------------
df.to_csv("labeled_statements_filled_values.csv", index=False, encoding="utf-8")
print("Готово! Ценностные маркеры добавлены и сохранены в labeled_statements_filled_values.csv")
