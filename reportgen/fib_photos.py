#!/usr/bin/env python3
"""Галерея объекта funda in business — только его собственные кадры.

    python3 -m reportgen.fib_photos --objects fib/objects.json 89726651 44554603
    python3 -m reportgen.fib_photos --url https://www.fundainbusiness.nl/.../object-.../

На чём тут легко обжечься (обожглись оба, независимо)
    Первая версия брала со страницы ВСЕ ссылки на cloud.funda.nl регуляркой.
    Получилось 288 «кадров» у объекта, где их 21: в разметке лежат блок
    «Vergelijkbaar in de buurt», портрет маклера и баннеры. Один такой чужой
    кадр — угловой дом с номером 365 на фасаде — я успел принять за
    оцениваемый объект. Оценить не тот дом дороже, чем не оценить вовсе.

    Брать «самую полную папку valentina_media/<a>/<b>/» тоже неверно: у жилых
    объявлений часть своих кадров лежит в соседней папке, а чужие — в той же.

Как правильно
    Свои кадры размечены атрибутом data-media-id, и путь строится из самого id:
        233676268 → /valentina_media/233/676/268_2160x1440.jpg
    Кадры одного объекта делят шестизначный префикс, поэтому берутся только те,
    что совпадают по префиксу с первым кадром («Bekijk foto 1 van …»). Это
    проверяемая принадлежность, а не догадка по месту в разметке.

Почему НЕ ld+json (замерено 21.09.2026 на трёх объявлениях)
    ld+json отдаёт ровно 6 кадров независимо от объёма галереи:

        Amsterdamsestraatweg 585   ld+json 6 | data-media-id 108 | funda пишет 108
        Annastraat 3               ld+json 6 | data-media-id  49 | funda пишет  49
        Zwart Janstraat 65         ld+json 6 | data-media-id  12 | funda пишет  12

    То есть ld+json здесь — превью с потолком в шесть штук: взяв список
    буквально оттуда, теряешь 102 кадра из 108. data-media-id совпадает со
    счётчиком самой funda до штуки. Если ld+json и использовать, то как
    白-список ПАПОК, а не как список кадров.
"""
import argparse, json, os, re, sys, time

from .fib_fetch import fetch

SIZE = "2160x1440"


def gallery(html):
    """(ссылки на кадры объекта, префикс). Пустой список — разметка не та."""
    hero = re.search(r'alt="Bekijk foto 1 van [^"]*"\s+src="https://cloud\.funda\.nl/'
                     r'valentina_media/(\d+)/(\d+)/', html)
    ids = list(dict.fromkeys(re.findall(r'data-media-id="(\d+)"', html)))
    if not ids:
        return [], None
    pre = (hero.group(1) + hero.group(2)) if hero else ids[0][:6]
    own = [i for i in ids if i.startswith(pre)]
    urls = [f"https://cloud.funda.nl/valentina_media/{i[:3]}/{i[3:6]}/{i[6:]}_{SIZE}.jpg"
            for i in own]
    return urls, pre


def gallery_of(url):
    return gallery(fetch(url))


def download(urls, folder, delay=0.2):
    os.makedirs(folder, exist_ok=True)
    got = 0
    try:
        import curl_cffi.requests as cr
        get = lambda u: cr.get(u, impersonate="safari18_0", timeout=25).content
    except ImportError:
        import urllib.request
        get = lambda u: urllib.request.urlopen(u, timeout=25).read()
    for n, u in enumerate(urls):
        p = os.path.join(folder, f"{n:02d}.jpg")
        if os.path.exists(p):
            got += 1
            continue
        try:
            blob = get(u)
            if len(blob) > 3000:
                open(p, "wb").write(blob)
                got += 1
        except Exception as e:
            print(f"    {u[-32:]}: {type(e).__name__}")
        time.sleep(delay)
    return got


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("ids", nargs="*", help="id объектов из objects.json")
    ap.add_argument("--objects", default="fib/objects.json")
    ap.add_argument("--url", help="разобрать один адрес, без objects.json")
    ap.add_argument("--out", default="fib/photos")
    ap.add_argument("--list-only", action="store_true", help="только посчитать кадры")
    a = ap.parse_args()

    targets = []
    if a.url:
        targets = [((re.search(r"object-(\d+)", a.url) or ["", "object"])[1], a.url, "")]
    else:
        R = {r["id"]: r for r in json.load(open(a.objects, encoding="utf-8"))}
        for lid in a.ids:
            r = R.get(lid)
            if not r:
                print(f"{lid}: нет в {a.objects}")
                continue
            targets.append((lid, r["url"], r.get("address") or ""))

    for lid, url, addr in targets:
        urls, pre = gallery_of(url)
        if a.list_only:
            print(f"{lid} {addr[:34]:34s} кадров объекта {len(urls):3d}, префикс {pre}")
            continue
        got = download(urls, os.path.join(a.out, lid))
        print(f"{lid} {addr[:34]:34s} кадров объекта {len(urls):3d}, "
              f"скачано {got:3d}, префикс {pre}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
