# название файла 12_fill_model.py

import pandas as pd
from transformers import pipeline


# ----------------------------
# эмоциональная интенсивность по словарю
# ----------------------------
emotion_words = {
    "positive": [
        "glücklich", "froh", "zufrieden", "optimistisch", "hoffnungsvoll",
        "erfreut", "begeistert", "dankbar", "stolz", "zuversichtlich",
        "entspannt", "ruhig", "ermutigt", "inspiriert", "positiv"
    ],
    "negative": [
        "traurig", "wütend", "frustriert", "enttäuscht", "besorgt",
        "ängstlich", "verärgert", "nervös", "deprimiert", "hoffnungslos",
        "erschöpft", "überfordert", "verunsichert", "verletzt", "gereizt",
        "angespannt", "müde", "resigniert", "pessimistisch"
    ]
}
# Усилители – немецкие
def calc_emotion_intensity(text):
    if not isinstance(text, str):
        return 0
    words = text.lower().split()
    count = 0
    for w in words:
        if w in emotion_words["positive"] or w in emotion_words["negative"]:
            count += 1
    # немецкие усилители и восклицательный знак
    count += text.count("!")
    for w in ["sehr", "äußerst", "besonders", "extrem", "wirklich", "absolut", "völlig", "stark", "hoch", "perfekt"]:
        count += text.lower().count(w)
    return count



# ----------------------------
# 1. Загрузка данных
# ----------------------------
df = pd.read_csv("labeled_statements.csv")

# Убираем пустые строки (на всякий случай)
df = df[df["quote"].notna() & df["quote"].str.strip().astype(bool)].reset_index(drop=True)


# ----------------------------
# Zero-shot классификатор (BART-MNLI)
# ----------------------------
classifier = pipeline("zero-shot-classification", model="facebook/bart-large-mnli")
def zero_shot_de(text, candidate_labels):
    if not isinstance(text, str) or not text.strip():
        return None
    result = classifier(text, candidate_labels)
    return result["labels"][0]

df["emotion_intensity"] = df["quote"].apply(calc_emotion_intensity)

# ----------------------------
# 5. Zero-shot разметка
# ----------------------------
df["formality"] = df["quote"].apply(lambda x: zero_shot_de(x, ["informell","formell"]))
df["emotion"]   = df["quote"].apply(lambda x: zero_shot_de(x, ["negativ","neutral","positiv"]))
df["intent"]    = df["quote"].apply(lambda x: zero_shot_de(x, ["deklarativ","imperativ","bewertend"]))

# ----------------------------
# 6. Сохраняем
# ----------------------------
df.to_csv("labeled_statements_filled.csv", index=False, encoding="utf-8")
print("✅ Zero-shot разметка завершена и сохранена в labeled_statements_filled.csv")
