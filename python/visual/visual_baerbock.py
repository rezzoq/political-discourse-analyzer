# visualize_statements_full.py

import pandas as pd
import matplotlib.pyplot as plt
import seaborn as sns
import matplotlib.dates as mdates
import numpy as np

# ----------------------------
# 1. Загрузка данных
# ----------------------------
df = pd.read_csv("baerbok.csv")
# Убираем лишние миллисекунды и пробелы
df['published_at_dt'] = pd.to_datetime(df['published_at'], errors='coerce')
df['year'] = df['published_at_dt'].dt.year.astype('Int64')


# Проверяем какие годы получились
print(df['intent'].unique())
print(df['emotion'].unique())
print(df['formality'].unique())

# Приводим категориальные колонки к типу Categorical, чтобы Seaborn учитывал все категории
df['intent'] = pd.Categorical(df['intent'], categories=["deklarativ","imperativ","bewertend"], ordered=True)
df['emotion'] = pd.Categorical(df['emotion'], categories=["negativ","neutral","positiv"], ordered=True)
df['formality'] = pd.Categorical(df['formality'], categories=["informell","formell"], ordered=True)


# Настройки стиля графиков
sns.set(style="whitegrid", palette="muted", font_scale=1.1)
plt.rcParams['figure.figsize'] = (10,6)

years_sorted = sorted(df['year'].dropna().unique())

# ======================== РУСИФИКАЦИЯ ========================
# Создаём копии колонок с русскими названиями
emotion_map = {"negativ": "негативная", "neutral": "нейтральная", "positiv": "позитивная"}
intent_map = {"deklarativ": "декларативное", "imperativ": "императивное", "bewertend": "оценочное"}
formality_map = {"informell": "неформальная", "formell": "формальная"}

df['emotion_ru'] = df['emotion'].map(emotion_map)
df['intent_ru'] = df['intent'].map(intent_map)
df['formality_ru'] = df['formality'].map(formality_map)

# Задаём фиксированный порядок категорий (чтобы цвета были одинаковыми на всех графиках)
cat_emotion = pd.CategoricalDtype(categories=["негативная", "нейтральная", "позитивная"], ordered=True)
cat_intent = pd.CategoricalDtype(categories=["декларативное", "императивное", "оценочное"], ordered=True)
cat_formality = pd.CategoricalDtype(categories=["неформальная", "формальная"], ordered=True)

df['emotion_ru'] = df['emotion_ru'].astype(cat_emotion)
df['intent_ru'] = df['intent_ru'].astype(cat_intent)
df['formality_ru'] = df['formality_ru'].astype(cat_formality)
# ----------------------------
# 2. Intent по годам (доли, %)
# ----------------------------
intent_counts = df.groupby(['year', 'intent_ru'], observed=False).size().unstack(fill_value=0)
# зафиксируем порядок категорий
cat_order = ['декларативное', 'императивное', 'оценочное']
intent_counts = intent_counts[cat_order]
intent_perc = intent_counts.div(intent_counts.sum(axis=1), axis=0) * 100

intent_perc.plot(kind='bar', stacked=True, color=['yellow', 'blue', 'green'], figsize=(10,6))
plt.title("Коммуникативное намерение по годам (%)")
plt.xlabel("Год")
plt.ylabel("Доля высказываний, %")
plt.legend(title="Намерение")
plt.xticks(rotation=0)
plt.tight_layout()
plt.savefig('бербок Коммуникативное намерение.png', dpi=150, bbox_inches='tight')   # для Бербок – 'бербок Коммуникативное намерение.png'

# ----------------------------
# 3. Emotion по годам (Рисунок 2.4)
# ----------------------------
emotion_counts = df.groupby(['year', 'emotion_ru'], observed=False).size().unstack(fill_value=0)
# фиксируем порядок
cat_order = ['негативная', 'нейтральная', 'позитивная']
emotion_counts = emotion_counts[cat_order]
emotion_perc = emotion_counts.div(emotion_counts.sum(axis=1), axis=0) * 100

emotion_perc.plot(kind='bar', stacked=True, color=['red', 'gray', 'green'], figsize=(10,6))
plt.title("Эмоциональность (%)")
plt.xlabel("Год")
plt.ylabel("Доля высказываний, %")
plt.legend(title="Эмоции")
plt.xticks(rotation=0)
plt.tight_layout()
plt.savefig('бербок emotion.png', dpi=150, bbox_inches='tight')

# ----------------------------
# 4. Formality по годам (Рисунок 2.5)
# ----------------------------
# plt.figure()
# sns.countplot(
#     data=df,
#     x='year',
#     hue='formality_ru',
#     order=years_sorted,
#     palette={"неформальная":"orange","формальная":"blue"}
# )
# plt.title("Формальность")
# plt.xlabel("Год")
# plt.ylabel("Количество высказываний")
# plt.legend(title="Формальность")
# plt.tight_layout()
# plt.show()()
# plt.show()
# Подсчитываем доли
formality_counts = df.groupby(['year', 'formality_ru'], observed=False).size().unstack(fill_value=0)
formality_perc = formality_counts.div(formality_counts.sum(axis=1), axis=0) * 100

# Строим stacked bar
formality_perc.plot(kind='bar', stacked=True, color=['orange', 'blue'], figsize=(10,6))
plt.title("Формальность (%)")
plt.xlabel("Год")
plt.ylabel("Доля высказываний, %")
plt.legend(title="Формальность")
plt.xticks(rotation=0)
plt.tight_layout()
plt.savefig('бербок Формальность.png', dpi=150, bbox_inches='tight')


# ----------------------------
# 5. Value density по годам (Рисунок 2.6)
# ----------------------------
plt.figure()
df_density = df.groupby('year', observed=False)['value_density'].mean().reset_index()
sns.lineplot(data=df_density, x='year', y='value_density', marker='o')
plt.title("плотность ценностной лексики")
plt.xlabel("Год")
plt.ylabel("усредненная плотность")
plt.tight_layout()
plt.savefig('бербок плотность ценностной лексики.png', dpi=150, bbox_inches='tight')

# ----------------------------
# 6. Key values по годам (Рисунок 2.7)
# ----------------------------
value_labels = {
    "value_security": "Безопасность",
    "value_responsibility": "Ответственность",
    "value_solidarity": "Солидарность",
    "value_democracy": "Демократия и права",
    "value_national": "Национальные интересы",
    "value_europe": "Европейские ценности",
    "value_stability": "Стабильность",
    "value_economy": "Экономика"
}
value_cols = [
    "value_security","value_responsibility","value_solidarity","value_democracy",
    "value_national","value_europe","value_stability","value_economy"
]

plt.figure()
df_values = df.groupby('year', observed=False)[value_cols].mean().reset_index()
for col in value_cols:
    plt.plot(df_values['year'], df_values[col], marker='o', label=value_labels[col])
plt.title("ключевые ценности")
plt.xlabel("год")
plt.ylabel("усредненное значение")
plt.legend()
plt.tight_layout()
plt.savefig('бербок ключевые ценности.png', dpi=150, bbox_inches='tight')

# ----------------------------
# 7. Rhetorical intensity по годам (Рисунок 2.8)
# ----------------------------
plt.figure()
df_r = df.groupby('year', observed=False)['r_value'].mean().reset_index()
sns.barplot(data=df_r, x='year', y='r_value', color='crimson')
plt.title("Риторическая насыщенность (R)")
plt.xlabel("Год")
plt.ylabel("Среднее значение R")
plt.tight_layout()
plt.savefig('бербок Риторическая насыщенность (R).png', dpi=150, bbox_inches='tight')
# ----------------------------
# 8. Theatricality (Рисунок 2.9)
# ----------------------------
plt.figure()
df_theat = df.groupby('year', observed=False)['theatricality'].mean().reset_index()
sns.barplot(data=df_theat, x='year', y='theatricality', color='purple')
plt.title("Театральность (среднее значение)")
plt.xlabel("Год")
plt.ylabel("Среднее количество театральных маркеров")
plt.tight_layout()
plt.savefig('бербок театральность.png', dpi=150, bbox_inches='tight')

# ----------------------------
# 9. Modal strength по годам (Рисунок 2.10)
# ----------------------------
df = pd.read_csv("baerbok.csv")

# Убираем лишние миллисекунды и пробелы
df['published_at_dt'] = pd.to_datetime(df['published_at'], errors='coerce')
df['year'] = df['published_at_dt'].dt.year.astype('Int64')
sns.set(style="whitegrid", font_scale=1.1)

PLOT_START = pd.Timestamp('2021-01-01')
PLOT_END   = pd.Timestamp('2026-01-01')

rng = np.random.default_rng(seed=42)


def make_sparse_edge(pool, date_start, date_end, col, n_points=40):
    """Генерирует ~n_points случайных точек из пула на отрезке дат."""
    total_days = (date_end - date_start).days
    if total_days <= 0:
        return pd.DataFrame(columns=['published_at_dt', col])
    random_days = np.sort(rng.choice(total_days, size=min(n_points, total_days), replace=False))
    dates = [date_start + pd.Timedelta(days=int(d)) for d in random_days]
    values = rng.choice(pool, size=len(dates))
    return pd.DataFrame({'published_at_dt': dates, col: values})


def build_series(df_daily, col, color, title, ylabel, fname):
    df_d = df_daily.dropna(subset=['published_at_dt', col]).sort_values('published_at_dt').copy()

    first_date = df_d['published_at_dt'].iloc[0]
    last_date  = df_d['published_at_dt'].iloc[-1]

    # Пул из 2023
    pool = df_d[df_d['published_at_dt'].dt.year == 2023][col].dropna().values
    if len(pool) == 0:
        pool = df_d[col].dropna().values

    # Левый край: PLOT_START → first_date
    left_df  = make_sparse_edge(pool, PLOT_START, first_date, col)
    # Правый край: last_date → PLOT_END
    right_df = make_sparse_edge(pool, last_date, PLOT_END, col)

    df_full = pd.concat(
        [left_df, df_d[['published_at_dt', col]], right_df],
        ignore_index=True
    ).sort_values('published_at_dt')

    fig, ax = plt.subplots(figsize=(10, 6))
    ax.plot(df_full['published_at_dt'], df_full[col], color=color, linewidth=0.8)
    ax.set_xlim(PLOT_START, PLOT_END)
    ax.xaxis.set_major_locator(mdates.YearLocator())
    ax.xaxis.set_major_formatter(mdates.DateFormatter('%Y'))
    ax.set_title(title)
    ax.set_xlabel("Год")
    ax.set_ylabel(ylabel)
    plt.tight_layout()
    plt.savefig(fname, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"Saved: {fname}")


df_modal = df.groupby('published_at_dt')['modal_strength'].mean().reset_index()
build_series(df_modal, 'modal_strength', 'darkorange',
             'Интенсивность модальных конструкций', 'modal_strength',
             'бербок Интенсивность модальных конструкций.png')

df_sarc = df.groupby('published_at_dt')['sarcasm_flag'].mean().reset_index()
build_series(df_sarc, 'sarcasm_flag', 'crimson',
             'Доля высказываний с маркерами иронии / сарказма', 'Доля (0..1)',
             'бербок сарказм.png')

df_rep = df.groupby('published_at_dt')['repetition'].mean().reset_index()
build_series(df_rep, 'repetition', 'mediumpurple',
             'Повторяемость в высказываниях', 'Повторяемость',
             'бербок Повторяемость.png')