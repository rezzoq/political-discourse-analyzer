"""
classifier_server.py
FastAPI server exposing the NLP classification pipeline.

Run:
    pip install fastapi uvicorn transformers torch langdetect
    uvicorn classifier_server:app --host 0.0.0.0 --port 8000

Endpoint:
    POST /classify   { "text": "..." }
    GET  /health
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from transformers import pipeline
import re

app = FastAPI(title="Political Discourse Classifier", version="1.0.0")

# ---------------------------------------------------------------------------
# Load model once at startup (takes ~30s, uses ~2GB RAM)
# ---------------------------------------------------------------------------
print("Loading facebook/bart-large-mnli …")
classifier = pipeline("zero-shot-classification", model="facebook/bart-large-mnli")
print("Model ready.")

# ---------------------------------------------------------------------------
# German lexicons (shared with 12_fill_model.py / 14_fill_rhetoric.py)
# ---------------------------------------------------------------------------
EMOTION_POSITIVE = [
    "glücklich", "froh", "zufrieden", "optimistisch", "hoffnungsvoll",
    "erfreut", "begeistert", "dankbar", "stolz", "zuversichtlich",
    "entspannt", "ruhig", "ermutigt", "inspiriert", "positiv"
]
EMOTION_NEGATIVE = [
    "traurig", "wütend", "frustriert", "enttäuscht", "besorgt",
    "ängstlich", "verärgert", "nervös", "deprimiert", "hoffnungslos",
    "erschöpft", "überfordert", "verunsichert", "verletzt", "gereizt",
    "angespannt", "müde", "resigniert", "pessimistisch"
]
AMPLIFIERS   = ["absolut", "extrem", "sehr", "total", "äußerst", "hoch", "perfekt", "stark"]
MODALS       = ["müssen", "sollen", "haben zu", "brauchen", "verpflichtet", "erforderlich"]
CONTRASTS    = ["aber", "jedoch", "obwohl", "trotzdem", "allerdings", "doch"]
COLLECTIVE   = ["wir", "unser", "uns", "zusammen", "uns selbst"]
THEATRICAL   = ["historisch", "beispiellos", "entscheidend", "dramatisch",
                "Wendepunkt", "epochal", "grundlegend"]
PRONOUNS     = ["ich", "du", "er", "sie", "es", "wir", "ihr", "mich", "dich",
                "ihn", "uns", "euch", "mein", "dein", "sein", "ihr", "unser", "euer"]
IRONY_MARKERS = ["offensichtlich", "sicher", "ja genau", "als ob", "definitiv",
                 "klar", "zweifellos", "absolut", "perfekt", "wunderbar"]

VALUE_LEXICON = {
    "security":       ["sicherheit", "schutz", "stabilität", "ordnung", "frieden"],
    "responsibility": ["verantwortung", "pflicht", "verpflichtung", "aufgabe"],
    "solidarity":     ["solidarität", "zusammenhalt", "gemeinschaft", "unterstützung"],
    "democracy":      ["demokratie", "freiheit", "rechte", "wahl", "parlament"],
    "national":       ["deutschland", "nation", "vaterland", "bürger", "heimat"],
    "europe":         ["europa", "eu", "europäisch", "union", "mitglied"],
    "stability":      ["stabilität", "kontinuität", "verlässlichkeit", "beständigkeit"],
    "economy":        ["wirtschaft", "wachstum", "arbeitsplätze", "investition", "markt"],
}

# ---------------------------------------------------------------------------
# Helper functions
# ---------------------------------------------------------------------------

def _count(text: str, words: list[str]) -> int:
    t = text.lower()
    return sum(t.count(w.lower()) for w in words)

def calc_emotion_intensity(text: str) -> int:
    words = text.lower().split()
    count = sum(1 for w in words if w in EMOTION_POSITIVE or w in EMOTION_NEGATIVE)
    count += text.count("!")
    count += sum(text.lower().count(w) for w in AMPLIFIERS)
    return count

def calc_value_density(text: str) -> dict:
    words = text.lower().split()
    total = len(words) if words else 1
    scores = {cat: _count(text, kws) for cat, kws in VALUE_LEXICON.items()}
    dominant = max(scores, key=scores.get)
    density = sum(scores.values()) / total
    return {"value_density": round(density, 4), "dominant_value": dominant, "value_scores": scores}

def calc_rhetoric(text: str) -> dict:
    modal_count      = _count(text, MODALS)
    contrast_count   = _count(text, CONTRASTS)
    collective_count = _count(text, COLLECTIVE)
    amp_count        = _count(text, AMPLIFIERS)
    theatricality    = _count(text, THEATRICAL)
    sarcasm_flag     = 1 if any(w in text.lower() for w in IRONY_MARKERS) else 0

    words = text.split()
    pronoun_ratio = sum(_count(text, [p]) for p in PRONOUNS) / len(words) if words else 0

    sentences = [s.strip() for s in re.split(r'[.!?]', text) if s.strip()]
    sent_len  = (sum(len(s.split()) for s in sentences) / len(sentences)) if sentences else 0

    word_set = set(text.lower().split())
    repetition = len(words) - len(word_set)

    value_density = calc_value_density(text)["value_density"]
    emotion_intensity = calc_emotion_intensity(text)

    r_value = (
        3 * (1 if modal_count > 0 else 0)
        + 1 * (1 if contrast_count > 0 else 0)
        + 2 * (1 if collective_count > 0 else 0)
        + 3 * (1 if value_density > 0 else 0)
        + 2 * min(emotion_intensity, 1)
        + 2 * min(theatricality, 1)
        + 2 * (1 if pronoun_ratio > 0.05 else 0)
    )

    return {
        "modal_strength":      1 if modal_count > 0 else 0,
        "contrast_present":    1 if contrast_count > 0 else 0,
        "collective_frame":    1 if collective_count > 0 else 0,
        "theatricality":       theatricality,
        "sarcasm_flag":        sarcasm_flag,
        "repetition_score":    repetition,
        "pronoun_ratio":       round(pronoun_ratio, 4),
        "sent_len":            round(sent_len, 2),
        "r_value":             r_value,
    }

def zero_shot(text: str, labels: list[str]) -> str:
    result = classifier(text, labels)
    return result["labels"][0]

# ---------------------------------------------------------------------------
# API schema
# ---------------------------------------------------------------------------

class TextRequest(BaseModel):
    text: str

class ClassifyResponse(BaseModel):
    emotion:           str        # negative | neutral | positive
    intent:            str        # declarative | imperative | evaluative
    formality:         str        # formal | informal
    emotion_intensity: int
    value_density:     float
    dominant_value:    str
    value_scores:      dict
    modal_strength:    int
    contrast_present:  int
    collective_frame:  int
    theatricality:     int
    sarcasm_flag:      int
    repetition_score:  int
    pronoun_ratio:     float
    sent_len:          float
    r_value:           int

# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.get("/health")
def health():
    return {"status": "ok"}

@app.post("/classify", response_model=ClassifyResponse)
def classify(req: TextRequest):
    text = req.text.strip()
    if not text:
        raise HTTPException(status_code=400, detail="text must not be empty")

    emotion   = zero_shot(text, ["negative", "neutral", "positive"])
    intent    = zero_shot(text, ["declarative", "imperative", "evaluative"])
    formality = zero_shot(text, ["formal", "informal"])

    value_info = calc_value_density(text)
    rhetoric   = calc_rhetoric(text)

    return ClassifyResponse(
        emotion=emotion,
        intent=intent,
        formality=formality,
        emotion_intensity=calc_emotion_intensity(text),
        value_density=value_info["value_density"],
        dominant_value=value_info["dominant_value"],
        value_scores=value_info["value_scores"],
        **rhetoric,
    )
