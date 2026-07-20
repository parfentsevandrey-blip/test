import json
pages = open('report.txt', encoding='utf-8').read().split('\f')

def page_of(key, start=2):  # skip page 1 (cover) and page 2 (TOC) to avoid self-match
    for i in range(start, len(pages)):
        if key in pages[i]:
            return i + 1
    return None

# (level, display title, search key)
entries = [
    (1, "Краткое резюме", "Краткое резюме"),
    (1, "Почему маленькая страна стала сверхдержавой", "Почему маленькая страна стала технологической"),
    (1, "Часть I. Чипы: Брейнпорт Эйндховен", "Часть I. Чипы"),
    (2, "ASML — Велдховен, промзона De Run", "ASML — Велдховен, промзона De Run"),
    (2, "High Tech Campus Eindhoven", "High Tech Campus Eindhoven"),
    (2, "Неймеген — завод чипов NXP и Ampleon", "Неймеген — завод чипов NXP"),
    (1, "Часть II. Дата-центры: где живёт «облако»", "Часть II. Дата-центры"),
    (2, "Амстердам — Science Park и узел AMS-IX", "Амстердам — Science Park и узел AMS-IX"),
    (2, "Эмсхавен — гиперскейл-кампус Google", "Эмсхавен — гиперскейл-кампус Google"),
    (2, "Agriport A7 — Мидденмер (Microsoft и Google)", "Agriport A7 — Мидденмер (Microsoft и Google)"),
    (1, "Часть III. Искусственный интеллект и наука будущего", "Часть III. Искусственный интеллект"),
    (2, "Делфт — QuTech и квантовые технологии", "Делфт — QuTech и квантовые технологии"),
    (1, "Часть IV. Экономика, энергия и трудные вопросы", "Часть IV. Экономика"),
    (1, "Сравнение ключевых зон", "Сравнение ключевых зон"),
    (1, "Заключение: что дальше", "Заключение: что дальше"),
    (1, "Источники", "Источники"),
]
toc = []
for lvl, title, key in entries:
    p = page_of(key)
    toc.append({"level": lvl, "title": title, "page": p})
    print(f"  {'  ' if lvl==2 else ''}{title[:48]:48} .... {p}")
json.dump(toc, open('toc.json', 'w'), ensure_ascii=False, indent=1)
missing = [t for t in toc if t['page'] is None]
print("MISSING:", missing if missing else "none")
