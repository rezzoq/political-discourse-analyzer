# название файла 13_fill_value.py

import pandas as pd

# ----------------------------
# 1. Словари ценностей
# ----------------------------
VALUE_LEXICONS = {
    "value_security": [
        "sicherheit", "sicherheitspolitik", "verteidigung", "schützen",
        "schutz", "bedrohung", "risiko", "stabilität", "frieden",
        "ordnung", "nato", "abschreckung"
    ],
    "value_responsibility": [
        "verantwortung", "verantwortlichkeit", "pflicht", "verpflichtung",
        "engagement", "haftung", "rolle", "rechenschaft"
    ],
    "value_solidarity": [
        "solidarität", "zusammen", "einheit", "unterstützung",
        "stehen an der seite", "verbündete", "partner"
    ],
    "value_democracy": [
        "demokratie", "rechte", "freiheit", "gerechtigkeit", "gesetz",
        "gleichheit", "rechtsstaatlichkeit"
    ],
    "value_national": [
        "national", "souveränität", "territorium", "bürger", "heimat",
        "staat", "unabhängigkeit"
    ],
    "value_europe": [
        "europa", "eu", "europäisch", "integration", "union",
        "brüssel", "euro"
    ],
    "value_stability": [
        "stabilität", "ordnung", "kontinuität", "vorhersehbar",
        "ruhig", "sicher"
    ],
    "value_economy": [
        "wirtschaft", "finanziell", "wachstum", "nachhaltig",
        "haushalt", "investition", "markt"
    ]
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
