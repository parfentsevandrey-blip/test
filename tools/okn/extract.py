"""Достаёт JSON объекта ОКН из RSC-разметки страницы наследие.дом.рф.

Данные лежат внутри flight-потока Next.js как экранированная строка, поэтому
сначала снимается экранирование, а потом объект вырезается по балансу скобок
от ключа-якоря, который есть у каждой карточки.
"""
import json, re

ANCHOR = '"egroknNum"'


PUSH = 'self.__next_f.push([1,'


def unescape(h):
    """Собирает flight-поток Next.js из пушей в одну строку.

    Каждый push несёт кусок потока как строковый литерал JS, и экранирование
    в нём многоуровневое: кавычка внутри названия объекта приезжает как \\\\".
    Поэтому литерал именно декодируется, а не чистится заменами — иначе
    названия вроде Александровской "полицейской" больницы рвут разбор.
    """
    out = []
    i = 0
    while True:
        i = h.find(PUSH, i)
        if i < 0:
            break
        j = i + len(PUSH)
        while j < len(h) and h[j] in ' \t\r\n':
            j += 1
        if j >= len(h) or h[j] != '"':
            i = j
            continue
        k = j + 1
        esc = False
        while k < len(h):
            ch = h[k]
            if esc:
                esc = False
            elif ch == '\\':
                esc = True
            elif ch == '"':
                break
            k += 1
        try:
            out.append(json.loads(h[j:k + 1]))
        except json.JSONDecodeError:
            pass
        i = k + 1
    return "".join(out)


def carve(text, anchor=ANCHOR):
    """Вырезает JSON-объект, внутри которого встретился anchor."""
    i = text.find(anchor)
    if i < 0:
        return None
    # идём влево до открывающей скобки объекта нужного уровня
    depth = 0
    start = None
    for j in range(i, -1, -1):
        ch = text[j]
        if ch == '}':
            depth += 1
        elif ch == '{':
            if depth == 0:
                start = j
                break
            depth -= 1
    if start is None:
        return None
    # идём вправо, считая скобки и пропуская строки
    depth = 0
    in_str = False
    esc = False
    for k in range(start, len(text)):
        ch = text[k]
        if in_str:
            if esc:
                esc = False
            elif ch == '\\':
                esc = True
            elif ch == '"':
                in_str = False
            continue
        if ch == '"':
            in_str = True
        elif ch == '{':
            depth += 1
        elif ch == '}':
            depth -= 1
            if depth == 0:
                return text[start:k + 1]
    return None


def parse_page(html):
    raw = carve(unescape(html))
    if raw is None:
        return None
    raw = raw.replace('"$undefined"', 'null')
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        # в потоке встречаются ссылки вида "$123" на другие чанки — глушим их
        fixed = re.sub(r'"\$[A-Za-z0-9_]+"', 'null', raw)
        return json.loads(fixed)


if __name__ == "__main__":
    import sys
    d = parse_page(open(sys.argv[1]).read())
    print(json.dumps(d, ensure_ascii=False, indent=1)[:4000])
    print("\nKEYS:", sorted(d.keys()))
