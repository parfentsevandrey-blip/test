# jsonQuery — язык поиска Циан

`cat.php?...` — это только фасад. Внутри Циан переводит query-строку в объект
`jsonQuery` и с ним идёт в поисковый API. Если обращаться к API напрямую,
подбирать имена query-параметров не нужно вообще: `jsonQuery` — и есть исходный
язык фильтров.

## Эндпоинт

```
POST https://api.cian.ru/search-offers/v2/search-offers-desktop/
Content-Type: application/json

{ "jsonQuery": { ... } }
```

Заголовки: `referer` на cian.ru, обычный браузерный `user-agent`,
`accept-language: ru-RU`. Запрос идёт из контекста с куками cian.ru — без
прогрева сессии прилетает капча.

Ответ:

```
data.offerCount         — оценка числа найденного (завышена, см. ниже)
data.offersSerialized[] — офферы текущей страницы (по 28)
data.queryString        — тот же запрос, сериализованный обратно в параметры cat.php
data.fullUrl            — готовая ссылка на выдачу для браузера
```

### queryString — обратный переводчик

`data.queryString` решает две задачи разом. Во-первых, даёт каноническое имя
любого фильтра в адресе: положили в `jsonQuery` ключ `ceiling_height` —
получили `min_ceiling_height=3.0`. Во-вторых, это надёжный признак, что фильтр
вообще принят.

**Изменение счётчика таким признаком не является.** `is_first_floor: false`
в запросе «этаж от пятого» ничего не отсекает — счётчик стоит на месте, хотя
фильтр применён и виден в `queryString` как `is_first_floor=0`. И наоборот:
несуществующий ключ молча выбрасывается, в `queryString` не попадает.

```
node tools/cian/cian.js probe --query q.json --with '{"balcony":{"type":"term","value":true}}'
  balcony = {"type":"term","value":true}
      count 88 -> 88, НЕ ПРИНЯТ (в query-строку не попал)
```

### Глубина выдачи и честный охват

`offerCount` — оценка, а не точное число, и подряд его добрать нельзя. Предел
зависит от запроса: у ЗАО-поиска 80 из 88 за три страницы, у ЖК «Остров»
974 из 1212 за сорок четыре. Общее — недобор порядка 10–20%.

Объединение по разным сортировкам добавляет немного (`search --all`: 84 вместо
80), потому что у каждой сортировки свой хвост. По-настоящему помогает дробление
на непересекающиеся подзапросы — `sweep`: на «Острове» 1156 из 1212 (95%).
Подробнее — в [traps.md](traps.md).

## Форма значений

| Тип | Вид | Пример |
|---|---|---|
| `term` | одно значение | `{"type":"term","value":2}` |
| `terms` | список | `{"type":"terms","value":[3]}` |
| `range` | диапазон | `{"type":"range","value":{"gte":5,"lte":10}}` |
| `geo` | география | `{"type":"geo","value":[{"type":"district","id":11}]}` |

Обязательный минимум для продажи квартир:

```json
{
  "_type": "flatsale",
  "engine_version": { "type": "term", "value": 2 },
  "region": { "type": "terms", "value": [1] }
}
```

## Ключи и значения

| Ключ | Тип | Значения |
|---|---|---|
| `geo` | geo | `{"type":"district","id":N}` — id из `moscow-geo.json` |
| `building_status` | term | 1 вторичка, 2 новостройка |
| `room` | terms | 1–5 комнат, 6 многокомнатная, 7 свободная планировка, 8 доля, 9 студия, 10 койко-место |
| `price` | range | рубли; валюта проставляется сама (`currency: 2`) |
| `total_area`, `living_area`, `kitchen` | range | м² |
| `floor` | range | этаж квартиры |
| `floorn` | range | этажность дома |
| `is_first_floor` | term | `false` — «не первый этаж» |
| `only_foot` | term | `"2"` — считать только пешую доступность |
| `foot_min` | range | минут до метро |
| `house_year` | range | год постройки |
| `house_material` | terms | 1 кирпич, 2 монолит, 3 панель, 4 блок, 5 дерево, 6 сталинский, 7 щитовой, 8 кирпично-монолитный, 10 каркасный, 11 газобетон, 12 газосиликат, 13 пенобетон |
| `repair` | terms | 1 без ремонта, 2 косметический, 3 евроремонт, 4 дизайнерский |
| `apartment` | term | `false` без апартаментов, `true` только апартаменты |
| `windows_type` | term | 0 во двор, 1 на улицу |
| `parking_type` | terms | 2 подземная, 3 наземная, 4 многоуровневая |
| `sost_type` | terms | 1 свободная продажа, 2 альтернативная, 3 214-ФЗ, 4 переуступка, 5 договор ЖСК, 6 предварительный договор, 7 договор инвестирования |
| `loggia` | term | `true` — есть лоджия |
| `balconies` | range | `{"gte":1}` — есть балкон; в адресе `min_balconies` |
| `wc_type` | term | 1 совмещённый (`minsu_s`), 2 раздельный (`minsu_r`) |
| `lifts` | term | минимум лифтов; в адресе `minlift` |
| `not_last_floor` | term | `true` — не последний этаж; в адресе `floornl` |
| `ceiling_height` | range | `{"gte":3}` — высота потолков; в адресе `min_ceiling_height` |
| `is_by_homeowner` | term | `true` — только от собственника |
| `has_video`, `tour_3d`, `penthouse` | term | видео / 3D-тур / пентхаус |
| `publish_period` | term | секунды: 3600 час, 86400 сутки, 604800 неделя |
| `flat_share`, `electronic_trading`, `demolished_in_moscow_programm` | term | доли / торги / дома под снос |
| `with_layout` | term | `true` — есть схема планировки |
| `yeargte` | term | год сдачи новостройки позднее указанного — **не** год постройки |
| `sort` | term | `price_object_order`, `creation_date_desc`, `area_order` |

Числовые словари сняты из фронтенд-бандла Циан (минифицированные enum'ы),
имена в адресе — из `data.queryString`, остальное проверено запросами.

### Имена, на которых легко ошибиться

Пять фильтров панели долго не поддавались, потому что назывались не так, как
кажется. Правильные имена — слева:

| Ключ | Неверные догадки |
|---|---|
| `balconies` | `balcony`, `balcon`, `balkon`, `has_balcony`, `with_balcony` |
| `wc_type` | `wc`, `bath`, `wc_site` |
| `lifts` | `lift`, `lift_service`, `building_lift_types_type` |
| `not_last_floor` | `floor_types`, `is_last_floor`, `only_last_floor` |
| `ceiling_height` | `minceilingheight`, `ceilingheight` |

Полный список из 173 ключей, которые знает фронтенд Циан, — в
`filters.json` → `allJsonQueryKeys`. Перебирать вслепую больше не нужно:
берите имя оттуда и проверяйте через `probe`.

## Поля оффера

Из ответа поиска сразу доступно то, ради чего раньше приходилось открывать
каждую карточку:

| Поле | Смысл |
|---|---|
| `cianId`, `fullUrl` | идентификатор и ссылка |
| `roomsCount`, `totalArea`, `livingArea`, `kitchenArea` | комнаты и площади |
| `floorNumber`, `building.floorsCount` | этаж и этажность |
| `building.buildYear`, `building.materialType` | год постройки и материал |
| `bargainTerms.priceRur` | цена |
| `isApartments` | апартаменты или квартира |
| `geo.address[]` | адрес; округ и район различаются по `type` (`okrug` / `raion`) |
| `geo.undergrounds[]` | метро: `name`, `time`, `transportType` (`walk`) |
| `creationDate` | дата публикации — отсюда срок экспозиции |

**Чего в ответе нет — счётчика просмотров.** Он живёт только в отрисованной
карточке: кнопка `[data-name="OfferStats"]`, по клику раскрывается
«N просмотров с даты создания объявления DD.MM.YYYY».

## Почему доотбор всё равно нужен

`apartment: false` и `house_year` пропускают часть несоответствующих лотов.
На проверке по ЗАО из 80 собранных объявлений 12 не прошли собственную же
заявленную планку — апартаменты под видом квартир и дома старше границы.
Поэтому клиент фильтрует результат ещё раз на своей стороне, по полям
`isApartments` и `building.buildYear`.
