# название файла 12_fill_model.py

import pandas as pd
from transformers import pipeline


# ----------------------------
# эмоциональная интенсивность по словарю
# ----------------------------
emotion_words = {
    "positive": [
        "happy", "joyful", "cheerful", "excited", "delighted",
        "content", "pleased", "glad", "thrilled", "elated",
        "optimistic", "hopeful", "amused", "inspired", "energetic",
        "proud", "grateful", "relieved", "confident", "loving",
        "enthusiastic", "satisfied", "blessed", "peaceful", "calm"
    ],
    "negative": [
        "sad", "angry", "frustrated", "disappointed", "upset",
        "anxious", "worried", "gloomy", "miserable", "depressed",
        "fearful", "jealous", "resentful", "hurt", "regretful",
        "lonely", "overwhelmed", "stressed", "embarrassed", "ashamed",
        "tired", "bored", "hopeless", "confused", "annoyed"
    ]
}

def calc_emotion_intensity(text):
    if not isinstance(text, str):
        return 0
    words = text.lower().split()
    count = 0
    for w in words:
        if w in emotion_words["positive"] or w in emotion_words["negative"]:
            count += 1
    # Усилители
    count += text.count("!") + sum(text.lower().count(w) for w in ["very", "absolutely", "extremely", "completely",
                                                                   "totally", "utterly", "highly",
                                                                   "perfectly", "strongly"])
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
classifier = pipeline(
    "zero-shot-classification",
    model="facebook/bart-large-mnli"
)

def zero_shot(text, candidate_labels):
    if not isinstance(text, str) or not text.strip():
        return None
    result = classifier(text, candidate_labels)
    return result["labels"][0]  # метка с наибольшей вероятностью

df["emotion_intensity"] = df["quote"].apply(calc_emotion_intensity)

# ----------------------------
# 5. Zero-shot разметка
# ----------------------------
df["formality"] = df["quote"].apply(lambda x: zero_shot(x, ["informal","formal"]))
df["emotion"] = df["quote"].apply(lambda x: zero_shot(x, ["negative","neutral","positive"]))
df["intent"] = df["quote"].apply(lambda x: zero_shot(x, ["declarative","imperative","evaluative"]))

# ----------------------------
# 6. Сохраняем
# ----------------------------
df.to_csv("labeled_statements_filled.csv", index=False, encoding="utf-8")
print("✅ Zero-shot разметка завершена и сохранена в labeled_statements_filled.csv")
