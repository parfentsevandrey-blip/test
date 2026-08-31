#!/usr/bin/env python3
"""Rebuild data/objects.json from the fetched lots plus the hand-written prose.

Everything factual comes from assets/<id>/lot.json exactly as investmoscow
served it; the only thing written by hand is the object description, which
lives in data/descriptions.json alongside the running order.

    python3 tools/make_entries.py
"""
import json
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ISSUE = 'Выпуск от 31.08.2026'

# procedureInfo fields worth carrying into the document beyond the seven the
# brief asks for, in the order they should be listed
EXTRA_PROCEDURE = [
    ('step', 'Шаг аукциона'),
    ('form', 'Форма проведения'),
    ('applicationStart', 'Начало приёма заявок'),
    ('participantSelection', 'Отбор участников'),
    ('results', 'Подведение итогов'),
]

# objectInfo rows that add nothing once the description and the rows above are
# there, that duplicate a visualBlockInfo row under another name, or that are
# simply empty on these lots
SKIP_OBJECT_INFO = {'Перечень помещений', 'Площадь объекта', 'Тип объекта',
                    'Площадь жилая', 'Площадь кухни'}


def minutes(n):
    """4 -> '4 минуты', 7 -> '7 минут'."""
    n = int(n)
    tail, hundred = n % 10, n % 100
    if 11 <= hundred <= 14 or tail == 0 or tail >= 5:
        return '%d минут' % n
    return '%d минут%s' % (n, 'а' if tail == 1 else 'ы')


def compact_address(address):
    """Fold a dozen 'помещение NN' repeats into one readable parenthetical."""
    rooms = re.findall(r'помещени[ея]\s*([0-9]+[А-Яа-я]?(?:/[0-9]+)?)', address)
    if len(rooms) < 3:
        return address
    head = address.split(', помещение')[0]
    order = sorted(set(rooms), key=lambda r: (int(re.match(r'\d+', r).group()), r))
    return '%s (помещения %s)' % (head, ', '.join(order))


def stamp(value):
    """'20.10.2026 15:00:00' -> '20.10.2026, 15:00 (МСК)'."""
    if not value:
        return None
    parts = value.split()
    if len(parts) != 2:
        return value
    return '%s, %s (МСК)' % (parts[0], parts[1][:5])


def short_title(lot):
    head = lot['title'].replace(' на продажу', '')
    street = lot['address'].split(',')
    # drop the округ / район prefix, keep the street and house
    while street and ('административный округ' in street[0] or street[0].strip() in
                      ('Мещанский', 'Красносельский', 'Чертаново Южное')):
        street = street[1:]
    return '%s — %s' % (head, ', '.join(s.strip() for s in street[:2]))


def entry(obj_id, lot, description):
    extras = []
    for key, label in EXTRA_PROCEDURE:
        if lot.get(key):
            extras.append([label, stamp(lot[key]) if 'заяв' in label or 'участ' in label
                           or 'итог' in label else lot[key]])
    for label, value in lot.get('visual', []):
        if label not in SKIP_OBJECT_INFO:
            extras.insert(0, [label, value])
    for label, value in lot.get('objectInfo', []):
        if label in SKIP_OBJECT_INFO or any(label == e[0] for e in extras):
            continue
        extras.append([label, value])
    if lot.get('metro'):
        extras.insert(0, ['Метро', '«%s» — %s пешком' % (lot['metro'], minutes(lot['metroWalk']))])
    extras.append(['Номер лота', lot['lot']])

    return {
        'id': obj_id,
        'shortTitle': short_title(lot),
        'description': description,
        'address': 'г. Москва, ' + compact_address(lot['address']),
        'startPrice': lot['startPrice'],
        'pricePerSqm': lot['pricePerSqm'],
        'applicationDeadline': stamp(lot['applicationDeadline']),
        'auctionDate': stamp(lot['auctionDate']),
        'deposit': lot['deposit'],
        'extras': extras,
        'sourceUrl': lot['sourceUrl'],
        'coords': '%s, %s' % (lot['lat'], lot['lon']),
        'metro': lot['metro'],
        'map': 'assets/%s/map.png' % obj_id,
        'photosDir': 'assets/%s/photos' % obj_id,
    }


def main():
    conf = json.load(open(os.path.join(ROOT, 'data', 'descriptions.json'), encoding='utf-8'))
    objects = []
    for obj_id in conf['order']:
        lot = json.load(open(os.path.join(ROOT, 'assets', obj_id, 'lot.json'), encoding='utf-8'))
        objects.append(entry(obj_id, lot, conf['descriptions'][obj_id]))

    out = {
        'title': 'Объекты торгов',
        'subtitle': 'Москва и Подмосковье',
        'issue': ISSUE,
        'objects': objects,
    }
    path = os.path.join(ROOT, 'data', 'objects.json')
    json.dump(out, open(path, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    for obj in objects:
        print(obj['id'], '|', obj['shortTitle'], '|', obj['startPrice'], '|', obj['deposit'])


if __name__ == '__main__':
    main()
