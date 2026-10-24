"""Шаги 2-4 ежедневного конвейера: кластеры спроса, лоты-концентраты интереса, отсев накрутки и двойная проверка.

Вход: history/*/lots.jsonl.gz (слепки) и */views.jsonl.gz (просмотры; у каждого лота -- последняя выгрузка).
Выход за день DAY: report.xlsx, metrics.jsonl.gz (метрики лотов -- для проверки исходами через 7/14 дней),
history/hot.json (горячие лоты -- их просмотры завтра опрашиваются снова: проверка повтором).

Сегменты: застройщик (первичка от застройщика), переуступка (новостройка от агента/собственника),
свежая вторичка (дом с FRESH_YEAR года или в ЖК), вторичка (старый фонд).

Метрики лота
  vpd       -- просмотры в день, среднее по 7 последним полным дням выгрузки
  vpd_n     -- то же с поправкой на фон дня: просмотры дня / средний просмотр лота того же сегмента в этот день
               (убирает общие провалы и всплески площадки: 11.10 -- 86 просмотров на 580 лотов, с 16.10 -- рост в 1,5-2 раза)
  rel_price -- цена м² к ожидаемой гедонической моделью (новостройки: ЖК, комнаты, площадь, этаж, отделка, сдан ли;
               вторичка: район, комнаты, площадь, этаж, год постройки, материал, метро); 0,85 = на 15% дешевле аналогов
  excess    -- «интерес, не объяснённый ценой и продвижением»: остаток модели log(vpd_n+1) ~ сегмент, продавец, комнаты,
               площадь, rel_price, самый дешёвый в группе, платные услуги, поднятие, возраст, фото, округ
  idx_peer  -- vpd_n / медиана группы аналогов (ЖК × тип продавца; во вторичке -- сегмент × район × комнатность)
  persist   -- дней из 7, когда лот собрал не меньше max(2, 2 × медиана аналогов) просмотров
Флаги отсева (шаг 4)
  всплеск      -- один день дал >= 40% просмотров за 10 дней (реклама вне Циана, рассылка, накрутка)
  счётчик      -- «всего с публикации» меньше суммы по дням, или прошлые дни пересчитаны вниз при повторной выгрузке
                  (Циан задним числом снимает недействительные просмотры)
  поднятие     -- платное поднятие в окне: без дня поднятия и следующего дня интерес падает более чем вдвое
  платное      -- сейчас включены ТОП-3 / премиум / выделение / аукцион
  приманка     -- агент, цена на 25%+ ниже аналогов и признаки фиктивного объявления (повторный нарушитель,
                  дублированное описание, меньше 5 фото)
  новизна      -- опубликован менее 3 дней назад: всплеск новой публикации, рано судить
  цена         -- не накрутка, а объяснение: самый дешёвый среди аналогов и на 10%+ ниже модели, или на 20%+ ниже модели
  аномальная цена -- на 35%+ ниже модели: торги, обременение, доля, приманка -- интерес не про спрос (жёсткий флаг)
  люкс         -- от 300 млн: просмотры из любопытства, не ликвидность (информационный)
Двойная проверка
  1) повтором: горячий лот опрашивается и на следующий день; интерес должен сохраниться, а дни не пересчитаны вниз;
  2) исходом: через 7/14 дней -- снят с публикации без перепубликации, забронирован, подорожал. Таблица «Проверка
     исходом» сравнивает долю ушедших лотов у горячих и у остальных: если разницы нет, отбор не работает.
usage: python daily/analyze.py [--day YYYY-MM-DD]
"""
import argparse, datetime as dt, glob, json, os, re, sys

import numpy as np
import pandas as pd

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from common import DATA, log, read_jsonl, today, write_jsonl

FRESH_YEAR = 2012
SEGS = ["застройщик", "переуступка", "свежая вторичка", "вторичка"]
PAID = ["top3", "premium", "colorized", "auction"]
MASS_GONE = 50  # столько лотов одного ЖК за день = отзыв фида, а не продажи


def days_upto(day):
    return sorted(os.path.basename(p) for p in glob.glob(os.path.join(DATA, "????-??-??"))
                  if os.path.basename(p) <= day and os.path.exists(os.path.join(p, "lots.jsonl.gz")))


def load_lots(day):
    df = pd.DataFrame(read_jsonl(os.path.join(DATA, day, "lots.jsonl.gz")))
    df = df[df["category"].isin(["flatSale", "newBuildingFlatSale"]) & (df["price"] > 0) & (df["area"] > 8)].copy()
    return df.set_index("id", drop=False)


def fingerprint(df):
    """Одна и та же квартира под разными id (перепубликация, несколько агентов)."""
    place = np.where(df["jk_id"].notna(), df["jk_id"].astype(str) + "/" + df["house_id"].astype(str),
                     df["lat"].round(4).astype(str) + "," + df["lng"].round(4).astype(str))
    return pd.Series(place, index=df.index) + "|" + df["floor"].astype(str) + "|" + df["rooms"].astype(str) + "|" \
        + df["area"].round(0).astype(str)


def fill_place(df):
    """Округ и район, где их нет (новостройки из базового сбора): по своему ЖК, иначе по соседним лотам (сетка ~1 км)."""
    for col in ("okrug", "raion"):
        known = df[col].notna() & (df[col] != "н/д")
        by_jk = df[known & df["jk_id"].notna()].groupby("jk_id")[col].agg(lambda s: s.mode().iat[0])
        cell = (df["lat"] * 100).round().astype("Int64").astype(str) + ":" + (df["lng"] * 60).round().astype("Int64").astype(str)
        by_cell = df[known].groupby(cell[known])[col].agg(lambda s: s.mode().iat[0])
        miss = ~known
        df.loc[miss, col] = df.loc[miss, "jk_id"].map(by_jk)
        miss = df[col].isna()
        df.loc[miss, col] = cell[miss].map(by_cell)
    return df


def bands(df):
    rooms = df["rooms"].fillna(-1).astype(int)
    df["rooms_b"] = np.select([(df["flat_type"] == "studio") | (rooms == 0), df["flat_type"] == "openPlan", rooms >= 4],
                              ["студия", "своб. план.", "4+к"], rooms.astype(str) + "к")
    fresh = (df["build_year"].fillna(0) >= FRESH_YEAR) | df["jk_id"].notna()
    df["seg"] = np.select([(df["kind"] == "new") & (df["seller"] == "developer"), df["kind"] == "new", fresh],
                          SEGS[:3], SEGS[3])
    df["price_b"] = pd.cut(df["price"] / 1e6, [0, 10, 15, 20, 30, 50, 100, np.inf],
                           labels=["до 10 млн", "10-15", "15-20", "20-30", "30-50", "50-100", "100+ млн"]).astype(str)
    df["area_b"] = pd.cut(df["area"], [0, 30, 40, 55, 75, 100, 150, np.inf],
                          labels=["до 30 м²", "30-40", "40-55", "55-75", "75-100", "100-150", "150+ м²"]).astype(str)
    fl, fls = df["floor"].fillna(0), df["floors"].fillna(0)
    df["floor_b"] = np.select([fl <= 1, (fl == fls) & (fls > 1), fl <= 3], ["первый", "последний", "2-3"], "средний")
    df["era"] = pd.cut(df["build_year"].fillna(0), [-1, 1, 1960, 1980, 2000, FRESH_YEAR, 3000],
                       labels=["н/д", "до 1960", "1960-79", "1980-99", f"2000-{FRESH_YEAR - 1}", f"{FRESH_YEAR}+"]).astype(str)
    df["metro_b"] = pd.cut(df["metro_min"].fillna(99), [-1, 5, 10, 15, 25, 1000],
                           labels=["до 5 мин", "5-10", "10-15", "15-25", "25+ / н/д"]).astype(str)
    fill_place(df)
    for c in ("decoration", "material", "okrug", "raion"):
        df[c] = df[c].fillna("н/д").astype(str)
    df["jk_key"] = df["jk_id"].fillna(0).astype(int).astype(str)
    return df


def ols(df, y, num, cat):
    X = pd.concat([df[num].astype(float).fillna(df[num].astype(float).median())] +
                  [pd.get_dummies(df[c].astype(str), prefix=c, drop_first=True, dtype=float) for c in cat], axis=1)
    X.insert(0, "const", 1.0)
    beta, *_ = np.linalg.lstsq(X.values, y.values, rcond=None)
    pred = X.values @ beta
    r2 = 1 - ((y.values - pred) ** 2).sum() / max(((y.values - y.values.mean()) ** 2).sum(), 1e-9)
    return pd.Series(pred, index=df.index), pd.Series(beta, index=X.columns), r2


def hedonic(df):
    """rel_price: цена м² к ожидаемой у аналогов (раздельные модели для новостроек и вторички)."""
    df["rel_price"], r2 = np.nan, {}
    df["log_area"] = np.log(df["area"])
    for kind, cat in (("new", ["jk_key", "rooms_b", "floor_b", "decoration", "deadline_done"]),
                      ("sec", ["raion", "rooms_b", "floor_b", "era", "material", "metro_b", "apartments"])):
        m = (df["kind"] == kind) & df["pm2"].notna()
        if m.sum() < 30:
            continue
        sub = df[m].copy()
        sub["deadline_done"] = sub["deadline_done"].astype(str)
        sub["apartments"] = sub["apartments"].astype(str)
        pred, _, r2[kind] = ols(sub, np.log(sub["pm2"]), ["log_area"], cat)
        df.loc[m, "rel_price"] = np.exp(np.log(sub["pm2"]) - pred)
    return r2


def load_views(day, days):
    """Последняя выгрузка просмотров каждого лота + предыдущая (для проверки повтором)."""
    last, prev = {}, {}
    for d in days:
        p = os.path.join(DATA, d, "views.jsonl.gz")
        if not os.path.exists(p):
            continue
        for v in read_jsonl(p):
            if v["id"] in last:
                prev[v["id"]] = last[v["id"]]
            last[v["id"]] = v
    return last, prev


def view_metrics(df, last, prev, day):
    rows, long = [], []
    for i in df.index.intersection(list(last)):
        v = last[i]
        full = [(d, n) for d, n in v["daily"] if d < v["day"]]  # день выгрузки неполный
        if len(full) < 7:
            continue
        w = full[-7:]
        long += [(i, d, n) for d, n in w]
        tot10 = sum(n for _, n in full) + 1e-9
        peak = max(n for _, n in full)
        revised = False
        if i in prev:  # проверка повтором: прошлые полные дни не должны уменьшаться
            old = {d: n for d, n in prev[i]["daily"] if d < prev[i]["day"]}
            revised = any(old.get(d, n) - n >= 3 and old.get(d, 0) > 1.3 * n for d, n in full)
        rows.append({"id": i, "views_day": v["day"], "vpd": sum(n for _, n in w) / 7, "total": v["total"],
                     "v10": tot10, "peak_share": peak / tot10, "spike": tot10 >= 20 and peak / tot10 >= .4,
                     "counter": v["total"] + 2 < sum(n for _, n in v["daily"]) or revised, "revised": revised,
                     "daily7": [n for _, n in w], "dates7": [d for d, _ in w], "refetched": i in prev})
    m = pd.DataFrame(rows).set_index("id") if rows else pd.DataFrame()
    if m.empty:
        return df.assign(vpd=np.nan)
    df = df.join(m, how="left")
    # фон дня по сегменту
    lv = pd.DataFrame(long, columns=["id", "date", "views"])
    lv["seg"] = df.loc[lv["id"], "seg"].values
    bg = lv.groupby(["seg", "date"])["views"].mean()
    bg_mean = bg.groupby(level=0).mean()
    lv["norm"] = lv["views"] / lv.set_index(["seg", "date"]).index.map(bg).values.clip(1e-3) \
        * lv["seg"].map(bg_mean).values
    # поднятие: без дня поднятия и следующего дня
    raise_day = pd.to_datetime(df["added_ts"], unit="s", utc=True).dt.tz_convert("Europe/Moscow").dt.strftime("%Y-%m-%d")
    created = df["created"].str[:10]
    lv["raise"] = lv["id"].map(raise_day.where(raise_day != created))
    nxt = pd.to_datetime(lv["raise"]).dt.strftime("%Y-%m-%d").where(lv["raise"].notna())
    nxt = (pd.to_datetime(nxt) + pd.Timedelta(days=1)).dt.strftime("%Y-%m-%d")
    lv["boosted"] = (lv["date"] == lv["raise"]) | (lv["date"] == nxt)
    g = lv.groupby("id")
    df["vpd_n"] = g["norm"].mean()
    df["vpd_clean"] = lv[~lv["boosted"]].groupby("id")["norm"].mean()
    df["raised"] = g["boosted"].any()
    df.attrs["background"] = bg.unstack(0).round(2)
    return df


def peers(df):
    sec_key = df["seg"] + "|" + df["raion"] + "|" + df["rooms_b"]
    df["peer"] = np.where(df["kind"] == "new",
                          "ЖК " + df["jk_key"] + "|" + np.where(df["seller"] == "developer", "застр.", "агент"), sec_key)
    has = df["vpd_n"].notna()
    size = df[has].groupby("peer")["id"].transform("count").reindex(df.index)
    small = (df["kind"] == "sec") & (size.fillna(0) < 8)
    df.loc[small, "peer"] = df["seg"] + "|" + df["okrug"] + "|" + df["rooms_b"]
    med = df[has].groupby("peer")["vpd_n"].median()
    df["peer_med"] = df["peer"].map(med)
    df["peer_n"] = df["peer"].map(df[has].groupby("peer")["id"].count())
    df["idx_peer"] = df["vpd_n"] / df["peer_med"].clip(lower=1 / 7)
    # самый дешёвый м² среди аналогов той же комнатности
    grp = df["peer"] + "|" + df["rooms_b"]
    df["cheapest"] = df["pm2"] == df.groupby(grp)["pm2"].transform("min")
    raw_med = df[has].groupby("peer")["vpd"].median()
    thr = np.maximum(2, 2 * df["peer"].map(raw_med).fillna(0))
    df["persist"] = [sum(n >= t for n in d) if isinstance(d, list) else np.nan for d, t in zip(df["daily7"], thr)]


def interest_model(df):
    m = df["vpd_n"].notna()
    sub = df[m].copy()
    if len(sub) < 50:
        df["excess"] = np.nan
        return None, None
    sub["relp"] = ((sub["rel_price"].fillna(1) - 1).clip(-.4, .4)) * 10
    sub["paid"] = sub[PAID].fillna(False).any(axis=1).astype(float)
    sub["brand"] = sub["branding"].notna().astype(float)
    sub["cheap1"] = sub["cheapest"].astype(float)
    sub["raised_f"] = sub["raised"].fillna(False).astype(float)
    sub["log_photos"] = np.log1p(sub["photos"])
    age = (pd.Timestamp(df.attrs["day"]) - pd.to_datetime(sub["created"])).dt.days
    sub["age_b"] = pd.cut(age, [-1, 3, 14, 60, 10**5], labels=["до 3 дн", "4-14", "15-60", "60+"]).astype(str)
    pred, beta, r2 = ols(sub, np.log1p(sub["vpd_n"]), ["log_area", "relp", "paid", "brand", "cheap1", "raised_f", "log_photos"],
                         ["seg", "seller", "rooms_b", "okrug", "age_b"])
    df["excess"] = np.exp(np.log1p(sub["vpd_n"]) - pred) - 1
    df["age_days"] = age
    return beta, r2


def flags(df):
    f = pd.DataFrame(index=df.index)
    f["всплеск"] = df["spike"].fillna(False).astype(bool)
    f["счётчик"] = df["counter"].fillna(False).astype(bool)
    f["поднятие"] = df["raised"].fillna(False).astype(bool) & (df["vpd_clean"].fillna(0) < .5 * df["vpd_n"]) & (df["v10"] >= 10)
    f["платное"] = df[PAID].fillna(False).any(axis=1)
    # без «не проверен Росреестром»: таких 93% вторички (20.10), признак не отличает фиктивные объявления
    weak = df["recidivist"].fillna(False) | df["dup_descr"].fillna(False) | (df["photos"] < 5)
    f["приманка"] = (df["seller"] != "developer") & (df["rel_price"] <= .75) & weak & (df["kind"] == "sec")
    f["новизна"] = df["age_days"].fillna(99) <= 3
    f["цена"] = (df["cheapest"] & (df["rel_price"] <= .9)) | (df["rel_price"] <= .8)
    # на 35%+ дешевле аналогов: торги, обременение, доля под видом квартиры или приманка -- интерес не про спрос
    f["аномальная цена"] = df["rel_price"] <= .65
    f["люкс"] = df["price"] >= 300e6  # просмотры из любопытства, не ликвидность
    df["flags"] = f.apply(lambda r: ", ".join(k for k, v in r.items() if v), axis=1)
    hard = f[["всплеск", "счётчик", "поднятие", "платное", "приманка", "новизна", "аномальная цена"]].any(axis=1)
    df["candidate"] = (df["excess"] >= 1) & (df["idx_peer"] >= 2) & (df["persist"] >= 4) & (df["vpd"] >= 1)
    df["verdict"] = np.select([df["candidate"] & ~hard & f["цена"], df["candidate"] & ~hard, df["candidate"]],
                              ["настоящий (ценовой)", "настоящий", "отсеян"], "")
    # двойная проверка повтором: лот был горячим вчера, опрошен снова, интерес держится, дни не пересчитаны вниз
    df["recheck"] = np.where(df["refetched"].fillna(False).astype(bool) & (df["verdict"] != ""),
                             np.where(df["candidate"] & ~f["счётчик"], "подтверждён повтором", "не подтвердился"), "")
    return f


def outcomes(df, day, days):
    """Что случилось с лотами со вчерашнего слепка: снят, перепубликован, забронирован, цена изменилась."""
    prev_days = [d for d in days if d < day]
    if not prev_days:
        return pd.DataFrame(), {}
    prev = bands(load_lots(prev_days[-1]))
    new = df.index.difference(prev.index)
    new_fp = set(fingerprint(df.loc[new]))  # перепубликация = новый id с тем же отпечатком (одинаковые планировки
    gone = prev.loc[prev.index.difference(df.index)].copy()  # на одном этаже не должны склеиваться с живыми лотами)
    gone["event"] = np.where(fingerprint(gone).isin(new_fp), "перепубликован", "снят")
    # отзыв выгрузки застройщика: 50+ лотов одного ЖК за день (24.10: «Веер» -- 214) -- не продажи
    batch = gone[gone["event"] == "снят"].groupby("jk_id")["id"].transform("count")
    gone.loc[batch.index[batch >= MASS_GONE], "event"] = "снят пакетом"
    both = df.index.intersection(prev.index)
    ch = pd.DataFrame({"price_was": prev.loc[both, "price"], "price_now": df.loc[both, "price"],
                       "booked_was": prev.loc[both, "booked"], "booked_now": df.loc[both, "booked"]})
    # изменения меньше 0,5% -- техническая подстройка цен в фидах застройщиков (23.10: ЗИЛАРТ 629 лотов на +0,02%)
    up = ch[ch["price_now"] > ch["price_was"] * 1.005].index
    down = ch[ch["price_now"] < ch["price_was"] * 0.995].index
    booked = ch[(ch["booked_now"] == True) & (ch["booked_was"] != True)].index  # noqa: E712
    ev = pd.concat([gone[["seg", "url", "price", "event"]],
                    df.loc[up, ["seg", "url", "price"]].assign(event="подорожал"),
                    df.loc[down, ["seg", "url", "price"]].assign(event="подешевел"),
                    df.loc[booked, ["seg", "url", "price"]].assign(event="забронирован")])
    return ev, {"с прошлого слепка": prev_days[-1], "новых": len(new), **ev["event"].value_counts().to_dict()}


def validation(day, days, df):
    """Проверка исходом: через 1, 7 и 14 дней -- доля ушедших (снят без перепубликации) и забронированных
    застройщиком у горячих лотов против остальных, по квинтилям excess и вердиктам внутри сегмента."""
    out = []
    cur = set(df.index)
    booked_now = df["booked"].eq(True)
    for lag in (1, 7, 14):
        d0 = (dt.date.fromisoformat(day) - dt.timedelta(days=lag)).isoformat()
        p = os.path.join(DATA, d0, "metrics.jsonl.gz")
        if not os.path.exists(p):
            continue
        m = pd.DataFrame(read_jsonl(p)).set_index("id")
        new_fp = set(df.loc[~df.index.isin(m.index), "fp"])
        m["gone"] = ~m.index.isin(cur) & ~m["fp"].isin(new_fp)
        if "jk_id" not in m and os.path.exists(os.path.join(DATA, d0, "lots.jsonl.gz")):  # метрики до 24.10 -- без ЖК
            m["jk_id"] = m.index.map({r["id"]: r.get("jk_id") for r in read_jsonl(os.path.join(DATA, d0, "lots.jsonl.gz"))})
        if "jk_id" in m:  # пакетные снятия (отзыв фида) не считаются уходом
            per_jk = m[m["gone"] & m["jk_id"].notna()].groupby("jk_id").size()
            m.loc[m["jk_id"].isin(per_jk[per_jk >= MASS_GONE].index), "gone"] = False
        was = m["booked"].eq(True) if "booked" in m else pd.Series(False, index=m.index)
        m["booked_new"] = m.index.map(booked_now).fillna(False).astype(bool) & ~was
        m["verdict"] = m["verdict"].fillna("").replace("", "не кандидат")
        q = np.ceil(m.groupby("seg")["excess"].rank(pct=True) * 5).clip(1, 5).astype(int)  # квинтили внутри сегмента
        m["q"] = q.map({1: "Q1 низкий", 2: "Q2", 3: "Q3", 4: "Q4", 5: "Q5 высокий"})
        for (seg, key), g in [((s, "квинтиль excess " + str(q)), g) for (s, q), g in m.groupby(["seg", "q"], observed=True)] + \
                [((s, "вердикт " + v), g) for (s, v), g in m.groupby(["seg", "verdict"])]:
            out.append({"лаг, дн": lag, "сегмент": seg, "группа": key, "лотов": len(g), "ушло": int(g["gone"].sum()),
                        "ушло, %": round(100 * g["gone"].mean(), 2), "забронировано": int(g["booked_new"].sum()),
                        "забронировано, %": round(100 * g["booked_new"].mean(), 2)})
    return pd.DataFrame(out)


def clusters(df):
    """Кластеры спроса: внимание сегмента (доля просмотров / доля лотов внутри своего класса), медиана excess,
    нижняя граница 95% интервала внимания -- кластер «устойчивый», если она > 1."""
    v = df[df["vpd_n"].notna()]
    seg_mean = v.groupby("seg")["vpd_n"].mean()
    cuts = [("округ × комнатность", ["okrug", "rooms_b"]), ("район", ["raion"]), ("бюджет", ["price_b"]),
            ("цена к аналогам", ["relp_b"]), ("площадь", ["area_b"]), ("метро", ["metro_b"]), ("этаж", ["floor_b"]),
            ("отделка", ["decoration"]), ("ЖК", ["jk"]), ("год постройки", ["era"])]
    out = []
    for name, keys in cuts:
        for k, g in v.groupby(["seg"] + keys, dropna=True):
            if len(g) < 15:
                continue
            sm = seg_mean[k[0]]
            se = g["vpd_n"].std(ddof=1) / np.sqrt(len(g))
            out.append({"сегмент": k[0], "разрез": name, "кластер": " / ".join(map(str, k[1:])), "лотов": len(g),
                        "просм./день, медиана": round(g["vpd"].median(), 1),
                        "внимание": round(g["vpd_n"].mean() / sm, 2), "внимание, нижн. граница": round((g["vpd_n"].mean() - 1.96 * se) / sm, 2),
                        "excess, медиана %": round(100 * g["excess"].median()),
                        "горячих лотов": int(g["candidate"].sum()), "настоящих": int(g["verdict"].str.startswith("настоящий").sum()),
                        "цена к аналогам, медиана": round(g["rel_price"].median(), 3)})
    c = pd.DataFrame(out)
    if not c.empty:
        c["устойчивый"] = np.where(c["внимание, нижн. граница"] > 1, "да", "")
        c = c.sort_values(["сегмент", "внимание, нижн. граница"], ascending=[True, False])
    return c


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--day", default=today())
    ap.add_argument("--hot", type=int, default=1500, help="сколько горячих лотов опрашивать завтра повторно")
    a = ap.parse_args()
    days = days_upto(a.day)
    df = bands(load_lots(a.day))
    df.attrs["day"] = a.day
    df["fp"] = fingerprint(df)
    r2p = hedonic(df)
    df["relp_b"] = pd.cut(df["rel_price"], [0, .85, .95, 1.05, 1.15, np.inf],
                          labels=["дешевле 15%+", "дешевле 5-15%", "±5%", "дороже 5-15%", "дороже 15%+"]).astype(str)
    last, prev = load_views(a.day, days)
    df = view_metrics(df, last, prev, a.day)
    bg = df.attrs.get("background")
    df.attrs["day"] = a.day
    peers(df)
    beta, r2i = interest_model(df)
    f = flags(df)
    ev, ev_sum = outcomes(df, a.day, days)
    val = validation(a.day, days, df)
    cl = clusters(df)
    v = df[df["vpd_n"].notna()]
    log(f"лотов {len(df)}, с просмотрами {len(v)}; R² цены {r2p}; R² интереса {r2i and round(r2i, 2)}")

    # горячие на завтра: кандидаты + верх по excess в сегментах интереса
    hot = list(dict.fromkeys(list(v[v["candidate"]].sort_values("excess", ascending=False).index) +
                             list(v.sort_values("excess", ascending=False).index)))[: a.hot]
    json.dump([int(i) for i in hot], open(os.path.join(DATA, "hot.json"), "w"))
    cols = ["id", "seg", "fp", "excess", "idx_peer", "vpd", "vpd_n", "rel_price", "verdict", "flags", "price", "booked", "jk_id"]
    write_jsonl(os.path.join(DATA, a.day, "metrics.jsonl.gz"),
                (dict(zip(cols, (None if pd.isna(x) else x for x in r))) for r in v[cols].itertuples(index=False)))

    # ---------- отчёт ----------
    lotcols = {"seg": "Сегмент", "flags": "Флаги", "verdict": "Вердикт", "recheck": "Проверка повтором", "okrug": "Округ",
               "raion": "Район", "jk": "ЖК", "rooms_b": "Комн.", "area": "Площадь", "floor": "Этаж", "price": "Цена, ₽",
               "pm2": "₽/м²", "rel_price": "Цена к аналогам", "vpd": "Просм./день", "vpd_n": "Просм./день с поправкой на фон",
               "idx_peer": "Интерес к аналогам, раз", "persist": "Дней из 7 выше аналогов", "excess": "Избыточный интерес",
               "peer_n": "Аналогов", "seller": "Продавец", "decoration": "Отделка", "build_year": "Год", "created": "Опубликован",
               "url": "Ссылка"}
    lots = df[df["vpd_n"].notna()].sort_values("excess", ascending=False)[list(lotcols)].rename(columns=lotcols)
    lots["Избыточный интерес"] = (lots["Избыточный интерес"] * 100).round()
    for c in ("Цена к аналогам", "Просм./день", "Просм./день с поправкой на фон", "Интерес к аналогам, раз"):
        lots[c] = lots[c].astype(float).round(2)
    hotl = lots[lots["Вердикт"] != ""]
    flip = lots[(lots["Цена к аналогам"] <= .88) & ~lots["Флаги"].str.contains("приманка|аномальная цена") &
                lots["Сегмент"].isin(["свежая вторичка", "вторичка", "переуступка"])].sort_values("Избыточный интерес", ascending=False)
    devhot = lots[(lots["Сегмент"] == "застройщик") & (lots["Интерес к аналогам, раз"] >= 3)]
    path = os.path.join(DATA, a.day, "report.xlsx")
    with pd.ExcelWriter(path, engine="openpyxl") as xw:
        summary = pd.DataFrame([("день", a.day), ("лотов в слепке", len(df)), ("лотов с просмотрами", len(v)),
                                ("горячих (кандидатов)", int(df["candidate"].sum())),
                                ("настоящих", int(df["verdict"].str.startswith("настоящий").sum())),
                                ("отсеяно", int((df["verdict"] == "отсеян").sum())),
                                ("R² модели цены", json.dumps({k: round(x, 2) for k, x in r2p.items()})),
                                ("R² модели интереса", r2i and round(r2i, 2))] + list(ev_sum.items()), columns=["", "значение"])
        summary.to_excel(xw, sheet_name="Сводка", index=False)
        cl.to_excel(xw, sheet_name="Кластеры", index=False)
        hotl.to_excel(xw, sheet_name="Горячие лоты", index=False)
        flip.head(500).to_excel(xw, sheet_name="Под флип", index=False)
        lots[lots["Флаги"].str.contains("аномальная цена")].head(500).to_excel(xw, sheet_name="Аномальная цена", index=False)
        devhot.head(500).to_excel(xw, sheet_name="Горячие у застройщиков", index=False)
        lots[lots["Флаги"] != ""].head(3000).to_excel(xw, sheet_name="Отсев и флаги", index=False)
        if not ev.empty:
            meta = df[["excess", "verdict"]]
            prev_days = [x for x in days if x < a.day]
            pm = os.path.join(DATA, prev_days[-1], "metrics.jsonl.gz") if prev_days else ""
            if os.path.exists(pm):  # снятые лоты: интерес на момент вчерашнего слепка
                meta = meta.combine_first(pd.DataFrame(read_jsonl(pm)).set_index("id")[["excess", "verdict"]])
            ev.join(meta, how="left").to_excel(xw, sheet_name="События дня")
        if not val.empty:
            val.to_excel(xw, sheet_name="Проверка исходом", index=False)
        if bg is not None:
            bg.to_excel(xw, sheet_name="Фон дня")
        if beta is not None:
            pd.DataFrame({"признак": beta.index, "эффект на просмотры, %": ((np.exp(beta.values) - 1) * 100).round(1)}) \
                .query("признак != 'const'").to_excel(xw, sheet_name="Модель интереса", index=False)
        pd.DataFrame({"методика": (__doc__ or "").strip().splitlines()}).to_excel(xw, sheet_name="Методика", index=False)
        for ws in xw.book.worksheets:
            ws.freeze_panes = "A2"
            for col in ws.columns:
                ws.column_dimensions[col[0].column_letter].width = min(45, max(9, max(len(str(c.value or "")) for c in col[:200]) + 2))
    log(f"горячих {int(df['candidate'].sum())}, настоящих {int(df['verdict'].str.startswith('настоящий').sum())}, "
        f"отсеяно {int((df['verdict'] == 'отсеян').sum())}; кластеров {len(cl)}, устойчивых {int((cl.get('устойчивый', pd.Series()) == 'да').sum())}")
    log("отчёт:", path)


if __name__ == "__main__":
    main()
