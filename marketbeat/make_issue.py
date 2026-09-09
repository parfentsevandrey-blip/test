#!/usr/bin/env python3
"""Сборка выпуска: пул фактов -> отбор -> проза -> issue.json.

Полоса MarketBeat держится на прозе: две колонки — инвестиционный рынок и
рынок пользователя — плюс прогноз, по 150–350 слов связного текста с цифрами
внутри. Сначала движок (picker.py) отбирает из пула недели набор фактов под
бюджет полосы и ограничения сбалансированности; затем проза пишется ПО
отобранному набору, и сборка проверяет связку в обе стороны: ключевая цифра
каждого отобранного факта обязана быть в тексте сектора, а факт, который движок
не взял, в тексте появляться не должен. Неотобранные сделки не пропадают — они
идут в таблицу «Сделки недели».

Читатель не обязан знать голландские термины и жаргон: каждый термин при первом
употреблении объясняется в тексте, а на полосе есть словарь.

Пул собран по двенадцати источникам (см. README) и проверен по датам на самих
страницах. Поле outlet — ПЕРВОИСТОЧНИК (CBS, Stivad, застройщик), а не
издание-пересказчик: ограничение на разнообразие источников иначе теряет смысл.
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
LO, HI = '2026-08-31', '2026-09-07'
VJ = 'https://vastgoedjournaal.nl/news/'
VGM = 'https://www.vastgoedmarkt.nl/'
COB = 'https://www.cobouw.nl/'
LOG = 'https://www.logistiek.nl/'
RT = 'https://retailtrends.nl/news/'
CBS = 'https://www.cbs.nl/nl-nl/nieuws/2026/36/'

# Бюджет полосы для движка — в знаках прозы, которые факт займёт вместе с
# пояснением и связкой. Стоимость задаётся классом факта, а не длиной моей
# формулировки: показатель рынка или закон надо объяснить (~250 знаков),
# сделке хватает ~175. Две текстовые колонки и прогноз вмещают около 5 600
# знаков, из них примерно две трети несут факты.
PROSE_BUDGET = 3600
COST = {'market': 250, 'policy': 250, 'deal': 175}


def it(id, sector, block, date, outlet, url, tier, scope, pol, nums, lead, deal=None):
    return dict(id=id, sector=sector, block=block, date=date, outlet=outlet,
                url=url, tier=tier, scope=scope, polarity=pol, numbers=nums,
                lead=lead, deal=deal, height=COST[scope], impact=2)


POOL = [
    # ================================================================ ЖИЛЬЁ ==
    it('l-cbre', 'living', 'investment', '2026-09-01', 'CBRE', VJ + '74125', 1,
       'market', 1, ['€7 млрд', '36%', '€15 млрд'],
       'CBRE: за I полугодие в Нидерландах продано недвижимости на €7 млрд, +36% за год — '
       'лучшее полугодие с 2022 года; прогноз на 2026 год повышен с 14,3 до €15 млрд.'),
    it('l-heimstaden', 'living', 'investment', '2026-08-31', 'Heimstaden', VJ + '74104', 1,
       'deal', 1, ['529'],
       'Heimstaden покупает у застройщика Bakkers|Hommen 529 новых арендных квартир и '
       '5 400 кв. м коммерческих помещений в Max Euwe Kwartier (Роттердам); по словам '
       'компании, «дно инвестиционного климата пройдено».',
       deal=('Роттердам, Max Euwe Kwartier: 529 новых арендных квартир', 'Heimstaden ← Bakkers|Hommen', '529 кв.')),
    it('l-urban', 'living', 'investment', '2026-09-04', 'Urban Interest', VJ + '74182', 1,
       'deal', 1, ['390'],
       'Urban Interest купил у NLV портфель из 390 квартир и девяти коммерческих помещений.',
       deal=('Портфель: 390 квартир и 9 коммерческих помещений', 'Urban Interest ← NLV', '390 кв.')),
    it('l-havensteder', 'living', 'investment', '2026-09-01', 'Havensteder', VJ + '74120', 1,
       'deal', 1, ['171'],
       'Жилищная корпорация Havensteder договорилась с Bakkers|Hommen о 171 социальной '
       'арендной квартире в том же Max Euwe Kwartier.',
       deal=('Роттердам, Max Euwe Kwartier: 171 социальная квартира', 'Havensteder ← Bakkers|Hommen', '171 кв.')),
    it('l-ndsm', 'living', 'investment', '2026-09-07', 'Lieven de Key', VJ + '74204', 1,
       'deal', 1, ['79'],
       'COD/Synchroon продали корпорации Lieven de Key 79 социальных квартир и мастерские '
       'на участке NDSM A7-2 (Амстердам-Норд).',
       deal=('Амстердам, NDSM: 79 социальных квартир + мастерские', 'Lieven de Key ← COD/Synchroon', '79 кв.')),
    it('l-vivet', 'living', 'investment', '2026-08-31', 'Vivet', VJ + '74113', 1,
       'deal', 1, ['€9,73 млн'],
       'Vivet купил 33 арендные квартиры на Houtkampstraat в Дутинхеме за €9,73 млн.',
       deal=('Дутинхем: 33 арендные квартиры', 'Vivet', '€9,73 млн')),
    it('l-nectar', 'living', 'investment', '2026-09-03', 'Nectar', VJ + '74159', 1,
       'deal', 1, ['56'],
       'Nectar с партнёрами купил жилой комплекс 2016 года постройки в Энсхеде: 56 квартир, '
       'из них 20 с уходом.',
       deal=('Энсхеде: комплекс 56 квартир (20 с уходом)', 'Nectar с партнёрами', '56 кв.')),
    it('l-cire', 'living', 'investment', '2026-09-03', 'Cire Vastgoed', VJ + '74167', 1,
       'deal', 1, ['€6,05 млн'],
       'Cire Vastgoed купил у Rubens Capital Partners комплекс жилья с уходом в Уретерпе за €6,05 млн.',
       deal=('Уретерп: комплекс жилья с уходом', 'Cire ← Rubens Capital', '€6,05 млн')),
    it('l-brabant', 'living', 'investment', '2026-09-07', 'провинция Северный Брабант',
       VJ + '74154', 1, 'policy', 1, ['€150 млн'],
       'Провинция Северный Брабант вкладывает €150 млн в фонд жилья с уходом Achmea: '
       '«рынок не справляется»; преференций при застройке фонд не получит.'),
    it('l-uitpond', 'living', 'investment', '2026-09-07', 'Vastgoed Belang', VJ + '74202', 1,
       'market', -1, ['76%', '776'],
       'Опрос Vastgoed Belang (776 владельцев): 76% частных арендодателей, распродающих '
       'жильё поштучно, назвали правку правила контрдоказательства минимальным условием, '
       'чтобы снова сдавать.'),
    it('l-box3', 'living', 'investment', '2026-09-02', 'коалиция (утечка бюджета)',
       VJ + '74144', 1, 'policy', -1, ['2028'],
       'Партии коалиции не договорились о законе о налоге по фактической доходности (box 3) '
       'и отложили решение; введение с 2028 года под вопросом.'),
    it('l-box3cost', 'living', 'investment', '2026-09-04', 'Минфин (письмо в парламент)',
       VJ + '74174', 1, 'policy', 0, ['€16,6 млрд'],
       'Компенсации по box 3 обошлись казне пока в €1 млрд при оценке €16,6 млрд; подано '
       '863 000 заявлений, рассмотрено 537 000.'),
    it('l-ja21', 'living', 'investment', '2026-09-04', 'JA21 (резолюция)', VGM + '210784', 2,
       'policy', 0, ['JA21'],
       'Резолюция JA21: ввести налог по фактической доходности для недвижимости уже сейчас, '
       'не дожидаясь решения по остальным активам.'),
    it('l-sector', 'living', 'investment', '2026-09-03', 'отрасль (круглый стол в парламенте)',
       VGM + '210741', 2, 'market', -1, ['политические риски'],
       'На круглом столе в парламенте 2 сентября инвесторы заявили, что не могут закладывать '
       'политические риски в расчёт: отсрочка box 3 — часть непредсказуемой политики.'),
    it('l-atad', 'living', 'investment', '2026-09-04', 'RTL Nieuws (утечка бюджета)',
       VJ + '74191', 2, 'policy', 1, ['ATAD'],
       'По утёкшим бюджетным документам, правило ATAD для жилищных корпораций отменяют с 2028 '
       'года — миллионы евро дополнительного инвестиционного ресурса.'),
    it('l-ec', 'living', 'investment', '2026-09-03', 'Европейская комиссия (утечка)',
       VJ + '74163', 2, 'policy', -1, ['вторых домов'],
       'Утёкший законопроект Еврокомиссии предлагает ограничить не только краткосрочную аренду, '
       'но и покупку земли и вторых домов.'),
    it('l-ecb', 'living', 'investment', '2026-09-02', 'Bundesbank', VGM + '210726', 2,
       'market', -1, ['ЕЦБ'],
       'Глава Бундесбанка ожидает, что ЕЦБ на следующей неделе снова повысит ставку.'),
    it('l-rates', 'living', 'investment', '2026-09-03', 'NOS (Rabobank)', 'https://nos.nl/l/2629485',
       2, 'market', -1, ['госдолг'],
       'Доходности госдолга растут во всех странах; десятилетние облигации США идут к 5%; '
       'триггер — нефть выше $95 после обострения между США и Ираном.'),
    it('l-dev', 'living', 'investment', '2026-09-02', 'Vastgoedmarkt (опрос застройщиков)',
       VGM + '210721', 3, 'market', -1, ['застройщиков'],
       'Рост ставок бьёт прежде всего по застройщикам: дороже и кредит, и стройка; при '
       'сжатии выручки объёмы падают.'),
    it('l-galaxy', 'living', 'investment', '2026-09-01', 'Galaxy Tower', VGM + '210649', 2,
       'deal', -1, ['Galaxy Tower'],
       'Жилая и гостиничная башня Galaxy Tower в Утрехте задерживается ещё на год: сдача '
       'перенесена с конца 2026-го на конец 2027 года.',
       deal=('Утрехт, Galaxy Tower: сдача перенесена на конец 2027', 'проект', '—')),
    it('l-huur', 'living', 'occupier', '2026-09-04', 'CBS', CBS + 'woninghuur-stijgt-gemiddeld-met-4-4-procent',
       1, 'market', 0, ['4,4%', '4,9%', '5,4%'],
       'CBS: аренда в июле выше прошлогодней на 4,4% (2025 — 4,9%, 2024 — 5,4%); социальное '
       'жильё +4,3%, свободный сегмент +4,5%; у корпораций +4,4%, у прочих +4,1%.'),
    it('l-infl', 'living', 'occupier', '2026-09-01', 'CBS', CBS + 'inflatie-in-augustus-3-3-procent-bij-snelle-raming',
       1, 'market', -1, ['3,3%'],
       'Инфляция в августе 3,3% по быстрой оценке против 3,2% в июле.'),
    it('l-klok', 'living', 'occupier', '2026-08-31', 'CBS', CBS + 'economisch-beeld-minder-negatief-in-augustus',
       1, 'market', 0, ['9 из 13'],
       'Конъюнктурные часы CBS: в августе картина менее негативна, чем в июле; 9 из 13 '
       'индикаторов ниже долгосрочного тренда.'),
    it('l-rente', 'living', 'occupier', '2026-09-07', 'Vastgoedjournaal (обзор ставок)', VJ + '74192',
       3, 'market', -1, ['Rabobank и ABN'],
       'Ипотечные ставки растут вторую неделю; Rabobank и ABN Amro повысили тарифы; в 2027 '
       'году ставку ждут в районе 4,5%.'),
    it('l-hdn', 'living', 'occupier', '2026-09-02', 'HDN', 'https://vastgoedactueel.nl/meeste-hypotheekaanvragen-blijven-voor-aankoop-aandeel-starters-daalt/',
       1, 'market', -1, ['HDN'],
       'HDN: число заявок на ипотеку в августе снизилось; большинство — на покупку, но доля '
       'покупателей первого жилья падает.'),
    it('l-vraagprijs', 'living', 'occupier', '2026-09-01', 'Vastgoed Actueel (данные по объявлениям)',
       'https://vastgoedactueel.nl/wie-zakt-met-de-vraagprijs-zakt-fors/', 3, 'market', 0, ['одним шагом'],
       'Продавцы, снижающие запрашиваемую цену, делают это одним шагом: в августе медианное '
       'снижение превысило 5%.'),
    it('l-huurmarkt', 'living', 'occupier', '2026-09-04', 'Vastgoedmarkt (анализ предложения)',
       VGM + '210774', 3, 'market', -1, ['14,4%'],
       'Предложение арендного жилья в августе на 14,4% ниже прошлогоднего — сжатие дошло и до '
       'свободного сегмента, хотя лето обычно даёт пик предложения.'),
    it('l-warmte', 'living', 'occupier', '2026-09-02', 'Nationaal Warmtefonds', 'https://nos.nl/l/2629387',
       1, 'market', 1, ['22 000'],
       'Национальный фонд тепла выдал за январь–август 22 000 займов на утепление против '
       '21 000 за весь 2025 год; у частных владельцев +67%.'),
    it('l-labels', 'living', 'occupier', '2026-09-03', 'Cobouw (данные по энергопаспортам)',
       COB + '339091', 2, 'market', 1, ['33 497'],
       'В июле 33 497 домов получили энергопаспорт класса A и выше — на 8,6% больше, чем в июне.'),
    it('l-borger', 'living', 'occupier', '2026-09-02', 'VNG', VJ + '74147', 1,
       'policy', -1, ['1 300'],
       'Отстранены три контролёра качества строительства, включая одно из крупнейших бюро: '
       'по оценке VNG, до 1 300 жилищных проектов остались без обязательного контроля.'),
    it('l-locaties', 'living', 'occupier', '2026-09-01', 'Министерство жилья (VRO)',
       'https://stadszaken.nl/artikel/9493/kabinet-wijst-vijf-nieuwe-grootschalige-woningbouwgebieden-aan',
       1, 'policy', 1, ['30 000'],
       'Министр Букхолт-О’Салливан назначила пять новых крупных площадок — Девентер, Харлем, '
       'Леуварден, Маастрихт, Верт: около 30 000 домов до 2035 года; всего в этом году будет '
       'десять; дополнительных денег на дороги нет.'),
    it('l-impuls', 'living', 'occupier', '2026-09-03', 'Министерство жилья (VRO)', COB + '339044',
       1, 'policy', 1, ['€75 млн'],
       'Новый раунд программы Woningbouwimpuls: муниципалитетам дадут €75 млн на проекты '
       'доступного жилья.'),
    it('l-funder', 'living', 'occupier', '2026-09-03', 'Правительство (письмо в парламент)',
       'https://aedes.nl/gezond-en-veilig-wonen/kabinet-stelt-eur-56-miljoen-beschikbaar-voor-aanpak-funderingsproblemen',
       1, 'policy', 1, ['€56 млн'],
       'Кабинет выделяет €56 млн на многолетнюю программу ремонта фундаментов; корпорациям '
       'отведена центральная роль в квартальных проектах.'),
    it('l-eib', 'living', 'occupier', '2026-09-03', 'EIB', COB + '339077', 1,
       'policy', -1, ['24 000'],
       'EIB: готовящийся закон о защите трудовых мигрантов на рынке жилья увеличит дефицит '
       'их жилья на 24 000 единиц.'),
    it('l-pbl', 'living', 'occupier', '2026-09-04', 'PBL', COB + '339112', 1,
       'policy', -1, ['PBL'],
       'PBL: азотные планы кабинета помогут выдаче разрешений на стройку лишь незначительно.'),
    it('l-bouwkosten', 'living', 'occupier', '2026-09-02', 'Cobouw Marktsignaal', COB + '338961',
       2, 'market', -1, ['0,5%'],
       'Стоимость строительства дома в июле +0,5% к июню и +5% за год.'),
    it('l-nvm', 'living', 'occupier', '2026-09-03', 'NVM', 'https://www.nvm.nl/nieuws/2026/nvm-wettelijke-kwaliteitsnormen-nodig-voor-alle-woningmakelaars/',
       1, 'policy', 0, ['NVM'],
       'NVM требует законодательно закрепить требования ко всем риелторам — профессия не '
       'регулируется с 2001 года; PVV готовит инициативный закон с реестром и правилами торгов.'),
    it('l-student', 'living', 'occupier', '2026-09-04', 'ING Research', VJ + '74170', 1,
       'market', -1, ['студент'],
       'ING: студенты всё дольше живут с родителями; Kences — дефицит не сокращается, несмотря '
       'на тысячи новых единиц в год; родители покупают «комнаты» до €200 000 (NOS).'),
    it('l-liander', 'living', 'occupier', '2026-08-31', 'Liander', VJ + '74109', 1,
       'market', 1, ['18 000'],
       'Liander: три ветряка у Noorder IJplas разгрузят сеть вокруг Амстердама на объём '
       'потребления 18 000 домов.'),
    it('l-top10', 'living', 'occupier', '2026-09-07', 'Cobouw 50', COB + '339117', 2,
       'market', 0, ['€33 млрд'],
       'Десять крупнейших строителей пятый год подряд поставили рекорд выручки — более '
       '€33 млрд, +7%, — при снижении прибыли.'),
    it('l-roden', 'living', 'occupier', '2026-09-03', 'Raad van State', VJ + '74158', 1,
       'deal', 1, ['120'],
       'Госсовет отклонил возражения против плана Maatlanden-De Zulthe: до 120 домов в Родене.',
       deal=('Роден: план на 120 домов утверждён Госсоветом', 'муниципалитет Нордевелд', '120 дом.')),
    it('l-breda', 'living', 'occupier', '2026-09-02', 'Dennenborgh', VJ + '74152', 1,
       'deal', 1, ['350'],
       'Dennenborgh выкупил последний участок под Bread Breda: 350 квартир и паркинг-хаб на 28 500 кв. м.',
       deal=('Бреда, Bread Breda: участок под 350 квартир', 'Dennenborgh', '350 кв.')),
    it('l-apeldoorn', 'living', 'occupier', '2026-09-02', 'Plegt-Vos', VJ + '74140', 1,
       'deal', 1, ['300'],
       'Plegt-Vos с муниципалитетом Апелдорна и корпорациями Veluwonen и Ons Huis начинает '
       'первые 300 домов новой деревни Beekbergsebroek.',
       deal=('Апелдорн, Beekbergsebroek: первые 300 домов', 'Plegt-Vos + муниципалитет', '300 дом.')),
    it('l-provast', 'living', 'occupier', '2026-09-07', 'Provast', VJ + '74194', 1,
       'deal', 1, ['1 000'],
       'Provast показал Flow Town в Rijnhaven (Роттердам): 120 000 кв. м, более 1 000 квартир, башня 185 м.',
       deal=('Роттердам, Rijnhaven: Flow Town, башня 185 м', 'Provast (тендер Blok 1)', '1 000 кв.')),

    # =============================================================== РИТЕЙЛ ==
    it('r-stivad', 'retail', 'investment', '2026-09-02', 'Stivad', VJ + '74150', 1,
       'market', -1, ['€800 млн', '102', '€951 млн', '7,9%'],
       'Stivad: вложения в магазины за I полугодие €800 млн по 102 сделкам против €951 млн '
       'годом ранее (−16%); валовая начальная доходность 7,9%.'),
    it('r-cbspi', 'retail', 'investment', '2026-09-01', 'CBS/Kadaster/DNB',
       'https://www.cbs.nl/nl-nl/maatwerk/2026/36/prijsindices-commercieel-vastgoed', 1,
       'market', 1, ['3,2%'],
       'Индекс цен магазинов CBS/Kadaster за II квартал: +3,2% за год по тренду — стабильный '
       'рост восемь кварталов подряд.'),
    it('r-nlv', 'retail', 'investment', '2026-08-31', 'NLV', VJ + '74115', 1,
       'deal', 1, ['Eskerplein'],
       'NLV купил торговый центр Eskerplein в Алмело у Nova Capital, действовавшего от имени фонда Eskerplein.',
       deal=('Алмело: районный ТЦ Eskerplein', 'NLV ← Nova Capital', 'н/д')),
    it('r-urban', 'retail', 'investment', '2026-09-04', 'Urban Interest', VJ + '74182', 1,
       'deal', 1, ['девять'],
       'В портфеле Urban Interest, купленном у NLV, девять коммерческих помещений при 390 квартирах.',
       deal=('9 торговых помещений в жилом портфеле', 'Urban Interest ← NLV', '9 объектов')),
    it('r-heim', 'retail', 'investment', '2026-08-31', 'Heimstaden', VJ + '74104', 1,
       'deal', 1, ['5 400 кв. м'],
       'В сделке Heimstaden по Max Euwe Kwartier — 5 400 кв. м коммерческих помещений первых этажей.',
       deal=('Роттердам, Max Euwe Kwartier: первые этажи', 'Heimstaden ← Bakkers|Hommen', '5 400 кв. м')),
    it('r-meterkast', 'retail', 'investment', '2026-09-04', 'Vastgoedjournaal (аналитика)',
       VJ + '74188', 3, 'market', 0, ['щиток'],
       'Стоимость торгового объекта всё чаще определяет «щиток»: энергопотребление, мощность '
       'подключения и регулирование, а не только проходимость.'),
    it('r-stappen', 'retail', 'investment', '2026-09-07', 'AndersFinancieren (колонка)',
       VJ + '74201', 3, 'market', 1, ['AndersFinancieren'],
       'Финансист Van der Stappen (AndersFinancieren): считать ритейл одной рисковой категорией — '
       'ошибка; разрыв между сильными и слабыми локациями растёт, и в нём возможности.'),
    it('r-leegstand', 'retail', 'investment', '2026-09-04', 'муниципалитет Роттердама',
       VGM + '210772', 1, 'policy', -1, ['пустующ'],
       'Роттердам в ноябре представит план постановления о пустующих зданиях, чтобы жёстче '
       'действовать против владельцев, годами держащих недвижимость пустой.'),
    it('r-hitte', 'retail', 'investment', '2026-09-01', 'Stadszaken',
       'https://stadszaken.nl/artikel/9495/waar-is-het-koel-hitte-wordt-concurrentiefactor-winkelcentra',
       3, 'market', 0, ['жар'],
       'Прохладные торговые центры в жару собирают больше посетителей: озеленение, тень и '
       'охлаждение становятся задачей владельца.'),
    it('r-cbs', 'retail', 'occupier', '2026-09-01', 'CBS', CBS + 'detailhandel-zet-bijna-3-procent-meer-om-in-juli',
       1, 'market', 1, ['2,8%'],
       'CBS: оборот розницы в июле +2,8% за год, объём продаж +1,8%; non-food +3,6%, продукты '
       '+1,5%, онлайн +3,8%.'),
    it('r-conf', 'retail', 'occupier', '2026-08-31', 'CBS', CBS + 'economisch-beeld-minder-negatief-in-augustus',
       1, 'market', 0, ['−34'],
       'Потребительское доверие в августе −34 против −46 в мае; доверие производителей выросло до +3,7.'),
    it('r-grens', 'retail', 'occupier', '2026-09-04', 'RetailTrends (исследование)', RT + '79986',
       2, 'market', -1, ['72%'],
       '72% жителей приграничных муниципалитетов ежемесячно покупают в Бельгии или Германии; '
       'эффект распространяется и вглубь страны.'),
    it('r-blokker', 'retail', 'occupier', '2026-08-31', 'Blokker', VJ + '74116', 1,
       'deal', 1, ['66'],
       'Blokker открывает в октябре–ноябре пять магазинов (Гронинген, Розендал, Рейссен, '
       'Хеллевутслёйс, Блерик) и растёт до 66 точек, 12 из них у франчайзи.',
       deal=('Blokker: 5 магазинов в 5 городах, сеть → 66', 'аренда', '5 точек')),
    it('r-coolblue', 'retail', 'occupier', '2026-09-01', 'Coolblue', VJ + '74134', 1,
       'deal', 1, ['1 200 кв. м'],
       'Coolblue открыл 22-й магазин в Нидерландах: 1 200 кв. м на Meubelplein Ekkersrijt в Соне.',
       deal=('Сон, Meubelplein Ekkersrijt: 22-й магазин Coolblue', 'аренда', '1 200 кв. м')),
    it('r-hugo', 'retail', 'occupier', '2026-08-31', 'Omroep West', VJ + '74108', 2,
       'deal', -1, ['Hugo'],
       'Магазин Hugo в Westfield Mall of the Netherlands за 2,5 года ни разу не вышел в прибыль; '
       'оператор MD Fashion Netherlands обанкрочен в конце июля.',
       deal=('Лейдсендам, Mall of the Netherlands: Hugo закрыт', 'MD Fashion Netherlands (банкрот)', '—')),
    it('r-sostrene', 'retail', 'occupier', '2026-09-07', 'Søstrene Grene', RT + '79995', 1,
       'deal', 1, ['Søstrene'],
       'Søstrene Grene в октябре открывает второй магазин в Амстердаме — возвращается на Nieuwendijk.',
       deal=('Амстердам, Nieuwendijk: 2-й магазин Søstrene Grene', 'аренда', '—')),
    it('r-nza', 'retail', 'occupier', '2026-09-07', 'NZA New Zealand Auckland', RT + '79992', 1,
       'deal', 1, ['25-й'],
       'NZA New Zealand Auckland открыл 25-й магазин в стране — франшиза в Энсхеде.',
       deal=('Энсхеде: 25-й магазин NZA (франшиза)', 'аренда', '—')),
    it('r-bedden', 'retail', 'occupier', '2026-09-03', 'Beddenspecialist.nl', RT + '79972', 1,
       'deal', 1, ['20-й'],
       'Beddenspecialist.nl 1 октября открывает 20-й магазин — в Энсхеде, по франшизе.',
       deal=('Энсхеде: 20-й магазин Beddenspecialist.nl', 'аренда', '—')),
    it('r-auping', 'retail', 'occupier', '2026-09-04', 'Auping', RT + '79983', 1,
       'deal', -1, ['Auping'],
       'Koninklijke Auping делает ставку на меньшую сеть точек продаж.'),
    it('r-polspotten', 'retail', 'occupier', '2026-09-07', 'Polspotten', RT + '79989', 1,
       'deal', -1, ['Polspotten'],
       'Polspotten назначил нового гендиректора и закрывает магазин в Амстердаме в октябре: '
       'ставка на онлайн.',
       deal=('Амстердам: магазин Polspotten закрывается в октябре', 'уход из офлайна', '—')),
    it('r-dreamland', 'retail', 'occupier', '2026-09-02', 'DreamLand', VJ + '74151', 1,
       'deal', 1, ['DreamLand'],
       'Сеть игрушек DreamLand открыла магазин на Paul Krugerkade 10 в Харлеме.',
       deal=('Харлем, Paul Krugerkade 10: DreamLand', 'аренда', '—')),
    it('r-rousseau', 'retail', 'occupier', '2026-09-01', 'Rousseau Chocolade', VJ + '74118', 1,
       'deal', 1, ['Rousseau'],
       'Rousseau Chocolade подписал аренду на Voetboog 4 в торговой зоне De Parade (Берген-оп-Зом).',
       deal=('Берген-оп-Зом, De Parade: Rousseau Chocolade', 'аренда', '—')),
    it('r-jumbo', 'retail', 'occupier', '2026-09-07', 'Eigen Haard', VJ + '74203', 1,
       'deal', 1, ['Jumbo'],
       'Eigen Haard и Jumbo подписали соглашение о намерениях: супермаркет остаётся в '
       'Hamerkwartier (Амстердам-Норд) и переезжает в новостройку корпорации.',
       deal=('Амстердам-Норд, Hamerkwartier: новый Jumbo', 'Eigen Haard → Jumbo', 'соглашение')),
    it('r-bloom', 'retail', 'occupier', '2026-09-03', 'Bloom & Wolf', RT + '79971', 1,
       'deal', 1, ['Bloom'],
       'Bloom & Wolf открыл первый физический магазин — shop-in-shop в House of Rituals.',
       deal=('Амстердам, House of Rituals: Bloom & Wolf', 'shop-in-shop', '—')),

    # =========================================================== ИНДАСТРИАЛ ==
    it('i-cbre', 'industrial', 'investment', '2026-09-01', 'CBRE', LOG + '211256', 1,
       'market', 1, ['10 000 кв. м'],
       'CBRE: логистический рынок в I полугодии показал явное восстановление; вернулись сделки '
       'крупнее 10 000 кв. м, но рост в основном за счёт переездов, а не расширения.'),
    it('i-eqt', 'industrial', 'investment', '2026-08-31', 'EQT Real Estate', LOG + '211233', 1,
       'deal', 1, ['55 000 кв. м'],
       'EQT Real Estate купил для клиента распределительный центр около 55 000 кв. м, построенный '
       'Panattoni на XL Business Park Twente в Алмело.',
       deal=('Алмело, XL Business Park Twente: РЦ', 'EQT RE ← Panattoni', '55 000 кв. м')),
    it('i-cbspi', 'industrial', 'investment', '2026-09-01', 'CBS/Kadaster/DNB',
       'https://www.cbs.nl/nl-nl/maatwerk/2026/36/prijsindices-commercieel-vastgoed', 1,
       'market', 0, ['4,7%'],
       'Индекс цен производственно-складских объектов CBS/Kadaster: +4,7% за год по тренду во '
       'II квартале против +8,5% два года назад — рост цен замедляется восьмой квартал.'),
    it('i-wdp', 'industrial', 'investment', '2026-09-02', 'WDP', VJ + '74145', 1,
       'deal', 1, ['€23,5 млн'],
       'WDP заплатил €23,5 млн за участок 100 000 кв. м в порту Антверпен-Брюгге с тяжёлым '
       'гарантированным подключением к сети.',
       deal=('Порт Антверпен-Брюгге: участок 100 000 кв. м с мощностью', 'WDP', '€23,5 млн')),
    it('i-dhs', 'industrial', 'investment', '2026-08-31', 'DHS REIM', VJ + '74110', 1,
       'deal', 1, ['€4,63 млн'],
       'DHS REIM с EuroZaken купили площадку под застройку на Rucphensebaan 77 в Розендале за €4,63 млн.',
       deal=('Розендал, Rucphensebaan 77: площадка под застройку', 'DHS REIM + EuroZaken', '€4,63 млн')),
    it('i-wageningen', 'industrial', 'investment', '2026-09-03', 'Nederlands Bakkerij Centrum',
       VGM + '210750', 1, 'deal', 1, ['€3,69 млн'],
       'Фонд Nederlands Bakkerij Centrum продал частному инвестору комплекс на Agro Business Park '
       'в Вагенингене за €3,69 млн с обратной арендой.',
       deal=('Вагенинген, Agro Business Park: продажа с обратной арендой', 'частный инвестор ← NBC', '€3,69 млн')),
    it('i-swiss', 'industrial', 'investment', '2026-09-02', 'Swiss Life Asset Managers', LOG + '211266', 1,
       'deal', 1, ['Tilburg I'],
       'Выдано разрешение на РЦ «Tilburg I» более 16 000 кв. м в Остерхауте на бывшей площадке '
       'ForFarmers, купленной Swiss Life AM в начале года.',
       deal=('Остерхаут: разрешение на РЦ Tilburg I', 'Swiss Life AM (девелопмент)', '16 000 кв. м')),
    it('i-meterkast', 'industrial', 'investment', '2026-09-04', 'Vastgoedjournaal (аналитика)',
       VJ + '74188', 3, 'market', 0, ['щиток'],
       'Мощность подключения к сети и энергохозяйство («щиток») становятся ценообразующим фактором наравне с локацией.'),
    it('i-levitudo', 'industrial', 'investment', '2026-09-02', 'Levitudo', VJ + '74142', 1,
       'deal', 1, ['Oud Gastel'],
       'Levitudo купил производственный комплекс на Watermolen 8-14 в Oud Gastel у частного инвестора.',
       deal=('Oud Gastel, Watermolen 8-14: комплекс', 'Levitudo ← частный инвестор', 'н/д')),
    it('i-merle', 'industrial', 'investment', '2026-09-01', 'Merle Mooi', VJ + '74137', 1,
       'deal', 1, ['5 772'],
       'Merle Mooi купил для своего фонда два здания на Gronsveld у трассы A2: 2 934 и 5 772 кв. м.',
       deal=('Гронсвелд (A2): два здания', 'Merle Mooi Vastgoed CV', '8 706 кв. м')),
    it('i-voldijn', 'industrial', 'investment', '2026-09-01', 'Voldijn', VJ + '74128', 1,
       'deal', 1, ['De Brand'],
       'Voldijn купил для одного из фондов два полностью сданных объекта на De Brand в Ден-Босе.',
       deal=('Ден-Бос, De Brand: два сданных объекта', 'Voldijn', 'н/д')),
    it('i-oudenbosch', 'industrial', 'investment', '2026-09-03', 'BM Property Invest', VJ + '74157', 2,
       'deal', 1, ['Oudenbosch'],
       'Международная компания купила комплекс на Oudlandsedijk 10 в Oudenbosch у BM Property Invest.',
       deal=('Oudenbosch, Oudlandsedijk 10: комплекс', 'международная компания ← BM Property', 'н/д')),
    it('i-zwijndrecht', 'industrial', 'investment', '2026-09-04', 'частный инвестор', VJ + '74185', 3,
       'deal', 1, ['Zwijndrecht'],
       'Частный инвестор купил полностью сданное здание на Molenvliet 85-87 в Zwijndrecht у другого частного инвестора.',
       deal=('Zwijndrecht, Molenvliet 85-87: сданное здание', 'частный ← частный', 'н/д')),
    it('i-inrev', 'industrial', 'investment', '2026-09-03', 'INREV', VGM + '210742', 1,
       'market', -1, ['INREV'],
       'INREV: инвесторы видят возможности в дата-центрах, но недооценивают риски этого типа недвижимости.'),
    it('i-rabo', 'industrial', 'investment', '2026-09-03', 'Van de Bilt, Wismans (блог)', VJ + '74155', 3,
       'market', -1, ['конца 2026'],
       'Финансисты Van de Bilt и Wismans: расчёт на снижение ставок и ралли оценок не оправдался, '
       'макроподъёма до конца 2026 года не будет.'),
    it('i-newcold', 'industrial', 'investment', '2026-09-07', 'Logistiek.nl (Newcold)', LOG + '211190', 2,
       'market', 0, ['Newcold'],
       'Всё больше логистов держат девелопмент в своих руках: Newcold растёт на деньги private equity, '
       'не отдавая контроль над зданиями.'),
    it('i-melis', 'industrial', 'occupier', '2026-09-04', 'Next Level Development', VJ + '74189', 1,
       'deal', 1, ['22 000 кв. м'],
       'Next Level Development строит почти 22 000 кв. м для Melis Logistics на Centerpoort-Noord 2 '
       'в Duiven; муниципалитет передал землю 1 сентября.',
       deal=('Duiven, Centerpoort-Noord 2: под Melis Logistics', 'Next Level Development', '22 000 кв. м')),
    it('i-vanacht', 'industrial', 'occupier', '2026-09-03', 'Van Acht Logistics', LOG + '211273', 1,
       'deal', 1, ['58 000 кв. м'],
       'Van Acht Logistics ввёл в Вегеле новый РЦ на 58 000 кв. м: под международного производителя '
       'электромобилей и Mars.',
       deal=('Вегел: новый РЦ Van Acht (производитель электромобилей, Mars)', 'ввод в эксплуатацию', '58 000 кв. м')),
    it('i-roemaat', 'industrial', 'occupier', '2026-09-07', 'Aloys Roemaat Transport', VJ + '74193', 1,
       'deal', 1, ['24 902 кв. м'],
       'Aloys Roemaat Transport арендовал распределительный центр на Zuidgang 2 в Groenlo: 24 902 кв. м '
       'склада и 1 495 кв. м офисов на участке более 6 га.',
       deal=('Groenlo, Zuidgang 2: РЦ', 'Aloys Roemaat Transport (аренда)', '26 397 кв. м')),
    it('i-hbrts', 'industrial', 'occupier', '2026-09-02', 'HB RTS', LOG + '211287', 1,
       'deal', 1, ['Bleiswijk'],
       'HB RTS удваивает площади на Urban Logistics Campus A12 в Bleiswijk: арендует половину '
       'нового здания 4 (более 16 000 кв. м всего).',
       deal=('Bleiswijk, ULC-A12: здание 4 сдано', 'HB RTS (аренда половины)', '16 000 кв. м')),
    it('i-zijlstra', 'industrial', 'occupier', '2026-09-01', 'Creative Technology', VJ + '74131', 1,
       'deal', 1, ['12 190 кв. м'],
       'Zijlstra Beroepskleding купил у Creative Technology комплекс 12 190 кв. м в Sint Annaparochie: '
       '10 500 кв. м производства и 1 690 кв. м офисов.',
       deal=('Sint Annaparochie, De Wissel 3: комплекс', 'Zijlstra ← Creative Technology', '12 190 кв. м')),
    it('i-firstbase', 'industrial', 'occupier', '2026-09-01', 'First Base Ground Screws', VGM + '210700', 1,
       'deal', 1, ['First Base'],
       'First Base Ground Screws арендовал логистический объект на Lauwersmeer 13 в Оссе.',
       deal=('Осс, Lauwersmeer 13: логистический объект', 'First Base (аренда)', 'н/д')),
    it('i-knaapen', 'industrial', 'occupier', '2026-09-04', 'Aan de Stegge', VJ + '74178', 1,
       'deal', 1, ['Knaapen'],
       'Aan de Stegge Twello строит новую штаб-квартиру и производственное здание Knaapen на Ekkersrijt в Соне.',
       deal=('Сон, Ekkersrijt: штаб-квартира и цех Knaapen', 'Aan de Stegge (стройка)', 'н/д')),
    it('i-uithoorn', 'industrial', 'occupier', '2026-09-01', 'жители Уитхорна', LOG + '211251', 2,
       'market', -1, ['Uithoorn'],
       'Жители Uithoorn выступают против двух РЦ Delin Property на площадке PPG: «под угрозой '
       'качество жизни района».'),
    it('i-brabant', 'industrial', 'occupier', '2026-09-02', 'Stadszaken (анализ 561 площадки)',
       'https://stadszaken.nl/artikel/9279/data-toont-ruimtekansen-op-bedrijventerreinen-uitvoering-blijft-knelpunt',
       2, 'market', 0, ['561'],
       'Анализ 561 промзоны Северного Брабанта показал резерв уплотнения существующих площадок; '
       'узкое место — исполнение.'),

    # ================================================================ ОФИСЫ ==
    it('o-cbspi', 'offices', 'investment', '2026-09-01', 'CBS/Kadaster/DNB',
       'https://www.cbs.nl/nl-nl/maatwerk/2026/36/prijsindices-commercieel-vastgoed', 1,
       'market', 1, ['5,7%'],
       'Индекс цен офисов CBS/Kadaster: +5,7% за год по тренду во II квартале; пик +7,0% '
       'пройден в III квартале 2025-го.'),
    it('o-derotterdam', 'offices', 'investment', '2026-08-31', 'муниципалитет Роттердама', VGM + '210666', 1,
       'market', -1, ['40 000 кв. м'],
       'Муниципалитет Роттердама подтвердил, что может покинуть 40 000 кв. м в башне De Rotterdam '
       'на Wilhelminapier: договор истекает в конце 2028 года, цель — экономия.'),
    it('o-zuidas', 'offices', 'investment', '2026-08-31', 'Vastgoedmarkt (анализ)', VGM + '210630', 3,
       'market', 0, ['Zuidas'],
       'Даже на Zuidas строить новые офисы всё труднее, а спрос на качество сохраняется — '
       'выход в обновлении старых неустойчивых зданий.'),
    it('o-eindhoven', 'offices', 'investment', '2026-09-01', 'Verschuuren & Schreppers', VJ + '74130', 2,
       'market', 1, ['25 000 кв. м'],
       'Эйндховен: двадцать лет в центре почти не строили офисов; новые EDGE Eindhoven (25 000 кв. м) '
       'и The RED (12 500 кв. м) показали, насколько велик отложенный спрос.'),
    it('o-arcona', 'offices', 'investment', '2026-09-04', 'Arcona Capital', VJ + '74190', 1,
       'deal', 1, ['Neherkade'],
       'Arcona Capital продал офисное здание на Neherkade 3000-3140 в Гааге местным предпринимателям.',
       deal=('Гаага, Neherkade 3000-3140: офисное здание', 'местные предприниматели ← Arcona', 'н/д')),
    it('o-fb', 'offices', 'investment', '2026-09-04', 'FB Investments', VJ + '74183', 1,
       'deal', 1, ['Rabobank'],
       'FB Investments купил у Rabobank её офис на Keizer Karelplein в Неймегене.',
       deal=('Неймеген, Keizer Karelplein: офис Rabobank', 'FB Investments ← Rabobank', 'н/д')),
    it('o-benkey', 'offices', 'investment', '2026-09-01', 'Benkey', VJ + '74138', 1,
       'deal', 1, ['Ravenswade'],
       'Benkey купил у Laagraven Investments для Benkey Vastgoedfonds здание на Ravenswade 2 в Ньивегейне.',
       deal=('Ньивегейн, Ravenswade 2: офисное здание', 'Benkey ← Laagraven Investments', 'н/д')),
    it('o-coolsepoort', 'offices', 'investment', '2026-09-04', 'Aroundtown', VJ + '74184', 1,
       'deal', 1, ['28 000 кв. м'],
       'Aroundtown вновь открыл Coolse Poort в центре Роттердама: односъёмщицкий офис превращён в '
       'многофункциональный комплекс 28 000 кв. м.',
       deal=('Роттердам, Coolse Poort: реконструкция завершена', 'Aroundtown', '28 000 кв. м')),
    it('o-emro', 'offices', 'investment', '2026-09-02', 'Emro Real Estate', VJ + '74146', 1,
       'deal', -1, ['142'],
       'Emro и муниципалитет Амстердама переводят полностью согласованный офисный проект Amstel Next '
       'в Bullewijk в 142 арендные квартиры: офисы не нашли рынка.',
       deal=('Амстердам, Bullewijk: Amstel Next → 142 квартиры', 'Emro + муниципалитет', '142 кв.')),
    it('o-gpr', 'offices', 'investment', '2026-09-01', 'GPR (индекс)', VGM + '210712', 1,
       'market', -1, ['4,3%'],
       'Европейские акции недвижимости в августе потеряли 4,3% (GPR 250 Europe) после трёх '
       'месяцев роста.'),
    it('o-rabo', 'offices', 'investment', '2026-09-03', 'Van de Bilt, Wismans (блог)', VJ + '74155', 3,
       'market', -1, ['конца 2026'],
       'Финансисты Van de Bilt и Wismans: макроподъёма до конца 2026 года не будет, ставки не '
       'снизились, оценки не подтянулись.'),
    it('o-leegstand', 'offices', 'investment', '2026-09-04', 'муниципалитет Роттердама', VGM + '210772', 1,
       'policy', -1, ['пустующ'],
       'Роттердам в ноябре представит план постановления о пустующих зданиях — инструмент '
       'против владельцев, годами держащих объекты пустыми.'),
    it('o-rtl', 'offices', 'occupier', '2026-08-31', 'RTL Z (опрос пяти консультантов)', VJ + '74107', 2,
       'market', 1, ['пяти'],
       'Опрос RTL Z пяти консультантов: работодатели вкладываются в современные офисы у транспорта, '
       'чтобы вернуть людей; на центральных локациях спрос превышает предложение, ставки растут.'),
    it('o-cfpb', 'offices', 'occupier', '2026-09-07', 'CFPB (через NOS)', 'https://nos.nl/l/2630039', 2,
       'market', 0, ['38%'],
       'Пик посещаемости офисов — вторник и четверг; работники умственного труда проводят в офисе '
       'около 38% рабочего времени, половину — дома (Center for People and Buildings).'),
    it('o-aroundtown', 'offices', 'occupier', '2026-09-01', 'Aroundtown', VJ + '74122', 1,
       'deal', 1, ['14 500 кв. м'],
       'Aroundtown продлил и заключил новые аренды более чем на 14 500 кв. м на Admiraliteitskade 62 в Роттердаме.',
       deal=('Роттердам, Admiraliteitskade 62: продления и новые аренды', 'Aroundtown (арендодатель)', '14 500 кв. м')),
    it('o-deloitte', 'offices', 'occupier', '2026-09-07', 'Deloitte', VGM + '210798', 1,
       'deal', 1, ['Deloitte'],
       'Deloitte подписал новую долгосрочную аренду в башне Maastoren (Wilhelminakade 1, Роттердам).',
       deal=('Роттердам, Maastoren: Deloitte продлевает', 'аренда', 'н/д')),
    it('o-opcharge', 'offices', 'occupier', '2026-09-03', 'Opcharge', VJ + '74156', 1,
       'deal', 1, ['WTC Rotterdam'],
       'Opcharge подписал долгосрочную аренду The Loft в WTC Rotterdam.',
       deal=('Роттердам, WTC: The Loft', 'Opcharge (аренда)', 'н/д')),
    it('o-pidz', 'offices', 'occupier', '2026-09-02', 'DHS REIM', VJ + '74143', 1,
       'deal', 1, ['пять лет'],
       'PIDZ арендовал у DHS REIM офис в Diana & Vesta (Амстердам-Зёйдост) на пять лет.',
       deal=('Амстердам-Зёйдост, Diana & Vesta: PIDZ', 'DHS REIM (арендодатель)', '5 лет')),
    it('o-dcmr', 'offices', 'occupier', '2026-09-04', 'DCMR', VJ + '74187', 1,
       'deal', 1, ['2010'],
       'DCMR долгосрочно продлил аренду офиса на Parallelweg 1 в Схидаме, где сидит с 2010 года.',
       deal=('Схидам, Parallelweg 1: DCMR продлевает', 'аренда', 'н/д')),
    it('o-booking', 'offices', 'occupier', '2026-08-31', 'Booking Experts', VJ + '74112', 1,
       'deal', 1, ['City Post'],
       'Booking Experts подписал долгосрочную аренду в The City Post Oost на Westerlaan 51 в Зволле.',
       deal=('Зволле, The City Post Oost: Booking Experts', 'аренда', 'н/д')),
    it('o-werkgeluk', 'offices', 'occupier', '2026-09-02', 'Vingerling (эксперт)', VJ + '74139', 3,
       'market', 0, ['Vingerling'],
       'Эксперт по благополучию Vingerling: урезание метров на сотрудника при гибридной работе '
       'имеет цену — людям нужно собственное место.'),
]

# Масштаб события (см. picker.IMPACT_VALUE): 3 — общенациональный показатель,
# закон или сделка первой величины; 1 — локальная мелочь; остальное — 2.
IMPACT = {
    3: ['l-cbre', 'l-heimstaden', 'l-uitpond', 'l-box3', 'l-atad', 'l-huur', 'l-infl', 'l-borger',
        'l-locaties', 'l-rente', 'l-huurmarkt', 'l-student', 'l-ecb',
        'r-stivad', 'r-cbs', 'r-nlv',
        'i-cbre', 'i-eqt', 'i-wdp', 'i-vanacht', 'i-roemaat',
        'o-derotterdam', 'o-emro', 'o-coolsepoort', 'o-rtl'],
    1: ['l-vivet', 'l-nectar', 'l-cire', 'l-liander', 'l-top10', 'l-labels', 'l-vraagprijs', 'l-galaxy',
        'r-bloom', 'r-bedden', 'r-nza', 'r-rousseau', 'r-dreamland', 'r-hitte', 'r-polspotten',
        'i-levitudo', 'i-oudenbosch', 'i-zwijndrecht', 'i-firstbase', 'i-knaapen', 'i-wageningen',
        'i-brabant', 'i-uithoorn', 'i-newcold',
        'o-opcharge', 'o-pidz', 'o-dcmr', 'o-booking', 'o-benkey', 'o-werkgeluk', 'o-gpr'],
}
for _imp, _ids in IMPACT.items():
    for _i in POOL:
        if _i['id'] in _ids:
            _i['impact'] = _imp

# Проза пишется ПО отобранному набору (см. main). Даты в тексте — даты
# публикации первоисточника; квартальная база помечена явно. Термины
# объясняются при первом употреблении: читатель не обязан знать голландские
# слова и профессиональный жаргон.
PROSE = {
    'living': [
        dict(key='investment', title='Инвестиционный рынок',
             subtitle='крупные покупатели вернулись, частные арендодатели ждут решения по налогу',
             paras=[
                 'Деньги в недвижимость снова идут. По данным CBRE от 1 сентября, за первое '
                 'полугодие в Нидерландах куплено недвижимости на €7 млрд — на 36% больше, чем '
                 'годом ранее, и это лучшее полугодие с 2022 года; прогноз на весь 2026 год повышен '
                 'с 14,3 до €15 млрд. Жильё — крупнейшая часть этого потока: по квартальной базе '
                 'Cushman & Wakefield на него пришлось €3,1 млрд, 53% всех вложений. Неделя '
                 'подтвердила тренд двумя большими сделками. 31 августа Heimstaden договорился с '
                 'застройщиком Bakkers|Hommen о покупке 529 новых арендных квартир и 5 400 кв. м '
                 'коммерческих помещений в Max Euwe Kwartier в Роттердаме — и заявил, что «дно '
                 'инвестиционного климата пройдено», хотя иностранные инвесторы в целом продолжают '
                 'уходить. 4 сентября Urban Interest купил у NLV портфель из 390 квартир и девяти '
                 'коммерческих помещений. Государство тоже входит в роль инвестора: провинция '
                 'Северный Брабант 7 сентября решила вложить €150 млн в фонд жилья с уходом '
                 'страховщика Achmea — «рынок сам не справляется».',
                 'Обратная сторона — частные арендодатели, которые распродают квартиры поштучно. '
                 'Причина налоговая. В Нидерландах доход от сбережений и инвестиций, включая '
                 'сдаваемое жильё, облагается в так называемом «box 3» — по условной доходности, а '
                 'не по фактическому доходу; владелец квартиры с низкой реальной отдачей платит '
                 'налог с денег, которых не получал. Опрос ассоциации Vastgoed Belang среди 776 '
                 'владельцев (7 сентября) показал: 76% тех, кто уже распродаёт или собирается, '
                 'назвали минимальным условием возврата на рынок правку «правила '
                 'контрдоказательства» — права доказать налоговой, что реальный доход ниже '
                 'условного. Между тем из утёкших 2 сентября бюджетных документов следует, что '
                 'партии коалиции не смогли договориться о законе о налоге по фактической '
                 'доходности и отложили решение: его введение с 2028 года под вопросом. Деньги на '
                 'это у казны есть: компенсации по старым спорам о box 3 обошлись пока в €1 млрд из '
                 'заложенных €16,6 млрд (письмо Минфина в парламент, 4 сентября). Не хватает не '
                 'денег, а согласия.',
                 'Стоимость денег не помогает: 2 сентября глава Бундесбанка заявил, что ЕЦБ на '
                 'следующей неделе, скорее всего, снова повысит ставку. Для рынка, который в начале '
                 'года рассчитывал на дешевеющий кредит, это движение в обратную сторону.',
             ],
             sources='CBRE 01.09 · Heimstaden 31.08 · Urban Interest 04.09 · пров. Сев. Брабант 07.09 · '
                     'Vastgoed Belang 07.09 · утечка бюджета 02.09 · Минфин 04.09 · Bundesbank 02.09'),
        dict(key='occupier', title='Рынок пользователя',
             subtitle='аренда растёт медленнее, спрос давит снизу, стройка споткнулась о контроль',
             paras=[
                 'CBS сообщил 4 сентября: аренда жилья в июле была на 4,4% выше, чем год назад. Это '
                 'меньше, чем в 2025-м (4,9%) и в 2024-м (5,4%), — третий год замедления. '
                 'Социальное (регулируемое) жильё подорожало на 4,3%, свободный сегмент — на 4,5%; '
                 'у жилищных корпораций, которым принадлежат две трети всех арендных домов, рост '
                 'составил 4,4%, у прочих владельцев — 4,1%. При инфляции 3,3% в августе (быстрая '
                 'оценка CBS от 1 сентября; в июле было 3,2%) аренда всё ещё растёт быстрее цен, '
                 'но разрыв сократился до одного процентного пункта.',
                 'Спрос при этом никуда не делся, он просто хуже финансируется. По данным '
                 'Hypotheken Data Netwerk (HDN) от 2 сентября, число заявок на ипотеку в августе '
                 'снизилось, а доля покупателей первого жилья продолжает падать. Ниже всех по '
                 'лестнице стоят студенты: ING Research 4 сентября показал, что они всё дольше '
                 'живут с родителями из-за дефицита жилья и высоких аренд; Kences (объединение '
                 'студенческих арендодателей) 2 сентября констатировал, что дефицит не сокращается, '
                 'несмотря на тысячи новых единиц в год, а NOS 5 сентября описал новый рынок — '
                 'родители покупают «комнаты» до 20 кв. м и до €200 000, к неудовольствию '
                 'муниципалитетов. Растёт и спрос на утепление: Национальный фонд тепла выдал за '
                 'январь–август 22 000 займов — больше, чем за весь 2025 год (21 000); частным '
                 'владельцам — на 67% больше. Толчок дали цены на энергию после закрытия '
                 'Ормузского пролива.',
                 'Предложение получило удар с неожиданной стороны. С 2024 года частный дом нельзя '
                 'сдать в эксплуатацию без подписи независимого контролёра качества (закон Wkb). '
                 '2 сентября стало известно, что три таких контролёра, включая одно из крупнейших '
                 'бюро страны, отстранены: по оценке союза муниципалитетов VNG, до 1 300 жилищных '
                 'проектов остались без обязательного надзора, и стройку на них надо '
                 'останавливать. На длинном горизонте новости лучше: 1 сентября министр жилья '
                 'назначила пять новых крупных площадок — в Девентере, Харлеме, Леувардене, '
                 'Маастрихте и Верте, около 30 000 домов до 2035 года; всего в этом году таких '
                 'площадок будет десять. Дополнительных денег на дороги к ним не дали. По '
                 'квартальной базе Cushman & Wakefield во втором квартале на продажу вышло рекордное '
                 'число существующих домов — во многом бывших арендных, — сделок стало заметно '
                 'больше, конкуренция покупателей ослабла: цена прибавила 3,4% за квартал при 2,1% '
                 'за год; спрос смещается к энергоэффективным домам, а дорогие и неутеплённые '
                 'продаются только со скидкой.',
             ],
             sources='CBS 01.09, 04.09 · HDN 02.09 · ING 04.09 · Kences 02.09 · NOS 05.09 · '
                     'Nationaal Warmtefonds 02.09 · VNG 02.09 · Министерство жилья 01.09'),
        dict(key='outlook', title='Прогноз',
             subtitle='всё решит бюджет 15 сентября',
             paras=[
                 'Ключевая дата — Prinsjesdag, день оглашения бюджета. Если отсрочка налога по '
                 'фактической доходности подтвердится, частные арендодатели продолжат распродажу, а '
                 'покупателями останутся корпорации, фонды и государство — как Heimstaden и '
                 'Брабант на этой неделе. Квартальная картина (цена сделки €506 000, +2,1% за год) '
                 'не изменилась, но аренда растёт всё медленнее, а кредит после ожидаемого шага '
                 'ЕЦБ дешеветь не будет. Второй риск — строительный контроль: пока не решён вопрос '
                 'с отстранёнными контролёрами, часть проектов стоит. Базовый сценарий на 12 месяцев: '
                 'цены растут медленнее инфляции, аренда в свободном сегменте — быстрее средней, '
                 'частное арендное предложение сжимается.',
             ],
             sources='оценка редакции по источникам недели и Cushman & Wakefield MarketBeat Q2 2026'),
    ],
    'retail': [
        dict(key='investment', title='Инвестиционный рынок',
             subtitle='сделок меньше, цены не падают, покупают вместе с жильём',
             paras=[
                 'Stivad — фонд, куда инвесторы сдают данные о своих сделках, — подвёл 2 сентября '
                 'итоги полугодия: в магазины вложено €800 млн по 102 сделкам против €951 млн '
                 'годом ранее, падение почти на 16%. Валовая начальная доходность магазинов — '
                 'годовая аренда, делённая на цену покупки, — составила 7,9%. Для сравнения: лучшие '
                 'офисы дают около 5,25%, склады 4,85%; магазины продаются с большой премией по '
                 'доходности, потому что покупатель закладывает риск ухода арендатора. При этом дешевле объекты '
                 'не становятся: по индексу цен магазинов CBS/Kadaster (1 сентября) цены во втором '
                 'квартале были на 3,2% выше прошлогодних, и этот темп держится восемь кварталов '
                 'подряд. Падение объёма — это меньше сделок, а не дешевле активы. Квартальная база '
                 'Cushman & Wakefield добавляет два штриха: кредит для торговой недвижимости стал '
                 'доступнее — банки конкурируют за сделки, — но часть владельцев предпочла '
                 'рефинансировать объекты, а не продавать, и предложение на рынке от этого сжалось; '
                 'крупные портфели всё чаще продаются как акции компании-владельца, чтобы не платить '
                 'налог на передачу 10,4%. Годовой ориентир C&W — €1,4 млрд — потребует сильной осени.',
                 'Сделки недели показывают, кто покупает. 31 августа NLV купил торговый центр '
                 'Eskerplein в Алмело у Nova Capital — районный центр с супермаркетом-якорем и '
                 'повседневными магазинами, самый востребованный сейчас тип объекта. Остальная '
                 'торговля прошла внутри жилых сделок: у Urban Interest в портфеле, купленном у NLV '
                 '4 сентября, девять коммерческих помещений при 390 квартирах; в покупке Heimstaden '
                 'в роттердамском Max Euwe Kwartier (31 августа) — 5 400 кв. м первых этажей. '
                 'Магазины всё чаще меняют владельца как часть жилого проекта, а не сами по себе. '
                 'Это согласуется с квартальным наблюдением C&W: спрос на удобную повседневную '
                 'торговлю силён, но качественного продукта на продажу мало.',
                 'Регулятор наступает на пустоту. 4 сентября Роттердам объявил, что в ноябре '
                 'представит план постановления о пустующих зданиях: такой документ позволяет '
                 'городу вести реестр долго пустующих объектов, штрафовать владельцев и в итоге '
                 'предлагать им арендаторов. Для собственников пустых магазинов на второстепенных '
                 'улицах это прямое давление продавать или сдавать дешевле.',
             ],
             sources='Stivad 02.09 · CBS/Kadaster/DNB 01.09 · NLV 31.08 · Urban Interest 04.09 · '
                     'Heimstaden 31.08 · муниципалитет Роттердама 04.09 · C&W MarketBeat Q2 2026'),
        dict(key='occupier', title='Рынок пользователя',
             subtitle='обороты растут, сети расширяются по франшизе, отдельные форматы уходят',
             paras=[
                 'Торговля растёт. По данным CBS от 1 сентября, оборот розницы в июле был на 2,8% '
                 'выше прошлогоднего, а объём продаж (без эффекта цен) — на 1,8%; непродовольственные '
                 'магазины прибавили 3,6%, продуктовые 1,5%, онлайн 3,8%. Потребительское доверие '
                 'в августе −34 пункта против −46 в мае (CBS, 31 августа): настроение всё ещё '
                 'ниже нуля, но отходит от весеннего дна, а доверие производителей уже '
                 'положительное (+3,7). Слабое место — граница: исследование от 4 сентября показало, '
                 'что 72% жителей приграничных муниципалитетов ежемесячно закупаются в Бельгии или '
                 'Германии, и эффект тянется вглубь страны. Разница между оборотом и объёмом — '
                 'цены: около одного пункта дала инфляция.',
                 'Сети открываются, но выборочно и чаще по франшизе — когда магазин ведёт '
                 'независимый предприниматель под вывеской сети. Blokker 31 августа объявил о пяти '
                 'открытиях в октябре–ноябре (Гронинген, Розендал, Рейссен, Хеллевутслёйс, Блерик): '
                 'сеть, прошедшая банкротство, вырастет до 66 точек, 12 из них у франчайзи. Coolblue '
                 '1 сентября открыл 22-й магазин — 1 200 кв. м на мебельном бульваре Ekkersrijt в '
                 'Соне. Датская Søstrene Grene в октябре возвращается на Nieuwendijk вторым '
                 'амстердамским магазином. Одёжная NZA New Zealand Auckland открыла 25-й магазин '
                 '(франшиза, Энсхеде), Beddenspecialist.nl 1 октября откроет там же 20-й. По '
                 'квартальной базе Cushman & Wakefield интерес арендаторов расширяется: в страну '
                 'выходят новые международные сети, спрос сосредоточен на локациях с проходимостью '
                 'и потенциалом оборота, вакансия крупных помещений снижается, а мелкие переходят '
                 'из рук в руки.',
                 'Уходят форматы, а не улицы. Магазин Hugo в крупнейшем молле страны, Westfield '
                 'Mall of the Netherlands, за два с половиной года ни разу не вышел в прибыль; его '
                 'оператор MD Fashion Netherlands обанкрочен в конце июля (Omroep West, 31 августа). '
                 'Auping 4 сентября объявил о переходе к меньшей сети точек продаж, Polspotten '
                 '7 сентября — о закрытии амстердамского магазина в октябре ради онлайна. '
                 'Продовольственный якорь, напротив, держится: Jumbo 7 сентября подписал с '
                 'корпорацией Eigen Haard соглашение о новом супермаркете в её новостройке в '
                 'Hamerkwartier (Амстердам-Норд) — остаётся в районе, меняя здание. Поляризация '
                 'между лучшими торговыми улицами и второстепенными локациями усиливается, и рост '
                 'аренды всё больше зависит от оборота арендатора, а не от конкуренции за помещения.',
             ],
             sources='CBS 31.08, 01.09 · RetailTrends 04.09, 07.09 · Blokker 31.08 · Coolblue 01.09 · '
                     'Omroep West 31.08 · Auping 04.09 · Eigen Haard 07.09'),
        dict(key='outlook', title='Прогноз',
             subtitle='капитал есть, но он избирателен',
             paras=[
                 'Доходность 7,9% при растущих ценах объектов говорит, что продавцы и покупатели '
                 'договариваются только по лучшим активам — районным центрам с продуктовым якорем и '
                 'торговле в составе жилых проектов. Обороты магазинов растут быстрее инфляции '
                 'товаров, что поддерживает арендаторов среднего сегмента и франшизные сети в '
                 'средних городах; премиальные монобренды в моллах остаются зоной риска. На '
                 '12 месяцев: доходность около 7,9% стабильна, объём сделок подтягивается к '
                 'прошлогоднему, а роттердамское постановление о пустующих зданиях станет образцом '
                 'для других городов. Финансирование доступнее, чем год назад, поэтому сделки, '
                 'отложенные в 2025-м, могут закрыться осенью.',
             ],
             sources='оценка редакции по источникам недели, Stivad, CBS, C&W MarketBeat Q2 2026'),
    ],
    'industrial': [
        dict(key='investment', title='Инвестиционный рынок',
             subtitle='крупные сделки вернулись, рост цен замедляется, мощность сети стала товаром',
             paras=[
                 'CBRE 1 сентября отчиталась о явном восстановлении логистического рынка в первом '
                 'полугодии: вернулись сделки крупнее 10 000 кв. м, хотя рост спроса пока идёт за '
                 'счёт переездов, а не расширения компаний. Цены при этом растут всё медленнее: по '
                 'индексу CBS/Kadaster (1 сентября) производственно-складские объекты во втором '
                 'квартале стоили на 4,7% больше, чем год назад, — против +8,5% двумя годами '
                 'раньше, восьмой квартал замедления подряд. По квартальной базе Cushman & Wakefield '
                 'за полугодие в сектор вложено €772 млн (из них €618 млн в логистику) против '
                 '€980 млн годом ранее.',
                 'Сделка недели — 31 августа EQT Real Estate купил для одного из клиентов '
                 'распределительный центр около 55 000 кв. м, построенный Panattoni на XL Business '
                 'Park Twente в Алмело. Ориентир цены за мощность задал сосед: 2 сентября WDP '
                 'заплатил €23,5 млн за участок 100 000 кв. м в порту Антверпен-Брюгге, главное '
                 'достоинство которого — тяжёлое гарантированное подключение к электросети. Для '
                 'нидерландского рынка, где новых подключений в большинстве регионов нет или '
                 'очередь на годы, это цена, которую капитал готов платить за мегаватты. Остальные '
                 'сделки меньше и типичны: DHS REIM с EuroZaken купили площадку под застройку в '
                 'Розендале за €4,63 млн; Swiss Life AM получил разрешение на центр «Tilburg I» в '
                 'Остерхауте на бывшей промплощадке; Merle Mooi купил два здания у трассы A2 у '
                 'Гронсвелда (2 934 и 5 772 кв. м), Voldijn — два сданных объекта на De Brand в '
                 'Ден-Босе; фонд Nederlands Bakkerij Centrum продал комплекс в Вагенингене частному '
                 'инвестору за €3,69 млн с обратной арендой — продавец остаётся арендатором.',
                 'Два предупреждения. INREV, ассоциация фондов недвижимости, 3 сентября отметила, '
                 'что инвесторы видят возможности в дата-центрах, но недооценивают их риски. А '
                 'логисты всё чаще строят сами: Newcold растёт на деньги private equity, не отдавая '
                 'контроль над зданиями девелоперам (7 сентября) — конкуренция за лучшие площадки '
                 'растёт со стороны самих пользователей. По квартальной базе, самые консервативные '
                 'фонды берут только лучшие объекты с длинными договорами и надёжными арендаторами, '
                 'более широкий круг инвесторов идёт за доходностью выше этого уровня; проверка '
                 'объектов перед покупкой стала длиннее, и часть сделок 2025 года закрывается '
                 'только сейчас.',
             ],
             sources='CBRE 01.09 · CBS/Kadaster/DNB 01.09 · EQT 31.08 · WDP 02.09 · DHS REIM 31.08 · '
                     'Swiss Life AM 02.09 · Merle Mooi 01.09 · Voldijn 01.09 · NBC 03.09 · INREV 03.09 · '
                     'Newcold 07.09 · C&W MarketBeat Q2 2026'),
        dict(key='occupier', title='Рынок пользователя',
             subtitle='строят под заказчика и покупают под себя',
             paras=[
                 'Спрос идёт от конечных пользователей. Van Acht Logistics 3 сентября ввёл в Вегеле '
                 'новый распределительный центр на 58 000 кв. м — под международного производителя '
                 'электромобилей и Mars. Aloys Roemaat Transport 7 сентября арендовал центр на '
                 'Zuidgang 2 в Гроенло: 24 902 кв. м склада и 1 495 кв. м офисов на участке '
                 'более шести гектаров. Для Melis Logistics в Дёйвене строится почти 22 000 кв. м '
                 '«под заказчика» — здание проектируют под конкретного арендатора, а не в надежде '
                 'сдать потом; муниципалитет передал землю 1 сентября. HB RTS удваивает площади на '
                 'кампусе городской логистики у трассы A12 в Bleiswijk, взяв половину нового здания '
                 '(всего более 16 000 кв. м). А Zijlstra Beroepskleding 1 сентября вовсе купил '
                 'комплекс 12 190 кв. м в Sint Annaparochie — 10 500 кв. м производства и '
                 '1 690 кв. м офисов: контроль над площадкой важнее гибкости аренды. Общая черта '
                 'этих сделок — размер: пользователи берут по 20–60 тысяч кв. м под собственную '
                 'операцию, тогда как средняя сделка на рынке, по квартальной базе, мельчает — '
                 'арендаторы смотрят на качество локации, характеристики здания и полную стоимость '
                 'владения, а не только на ставку.',
                 'Сопротивление растёт там, где склады подходят к жилью: жители Uithoorn 1 сентября '
                 'выступили против двух центров Delin Property на площадке PPG — «под угрозой '
                 'качество жизни района». Ответ — уплотнять существующие промзоны: анализ 561 '
                 'площадки Северного Брабанта (2 сентября) показал заметный резерв, но узкое место — '
                 'исполнение, а не данные. Квартальная база это подтверждает: поглощение — площади, '
                 'взятые пользователями за квартал, — 1,82 млн кв. м (+12% за год), из них около '
                 '1,1 млн логистика; но свободные площади выросли с 6,80 до 7,71 млн кв. м, в том '
                 'числе за счёт новых зданий, сданных без арендатора. Существующие здания '
                 'выигрывают: при дорогой стройке, долгих разрешениях и дефиците земли ухоженный '
                 'старый склад с подключением привлекательнее нового проекта, который ещё надо '
                 'согласовать и запитать. Юг страны и Рандстад держат 70% спроса, а разрыв между '
                 'лучшими объектами и вторичными рынками, где вакансия выше и арендаторов приходится '
                 'заманивать скидками, растёт. Для владельцев вторичных объектов это означает торг '
                 'о скидках и вложения в подключение — без него здание не сдать. Прайм-ставка €125 '
                 'за кв. м в год держится на дефиците качественных объектов с мощностью.',
             ],
             sources='Van Acht 03.09 · Aloys Roemaat 07.09 · Next Level Development 04.09 · HB RTS 02.09 · '
                     'Creative Technology 01.09 · Logistiek.nl 01.09 · Stadszaken 02.09 · C&W MarketBeat Q2 2026'),
        dict(key='outlook', title='Прогноз',
             subtitle='двухскоростной рынок сохраняется',
             paras=[
                 'Расхождение между растущими свободными площадями и растущей прайм-ставкой '
                 'сохранится, пока перегрузка сети ограничивает предложение с мощностью: объекты с '
                 'подключением дорожают, объекты без него теряют ликвидность независимо от '
                 'локации. Сделка WDP задала ориентир того, сколько стоят гарантированные '
                 'мегаватты. Рост цен замедлился до 4,7% и, судя по тренду, замедлится ещё; '
                 'инвестиционный объём останется ниже прошлогоднего, спрос пользователей — в '
                 'формате «под заказчика» и покупки под себя; более сильное второе полугодие '
                 'возможно за счёт сделок, перенесённых с 2025 года. Дата-центры добавят спроса на '
                 'мощность и конкуренции за участки с подключением. На 12 месяцев: вакансия выше, '
                 'прайм-ставка выше, средняя ставка стоит.',
             ],
             sources='оценка редакции по источникам недели и Cushman & Wakefield MarketBeat Q2 2026'),
    ],
    'offices': [
        dict(key='investment', title='Инвестиционный рынок',
             subtitle='покупают предприниматели, старые здания перестраивают или переводят в жильё',
             paras=[
                 'Институциональных покупателей на неделе не было; офисы переходят к местному и '
                 'частному капиталу. 4 сентября Arcona Capital продал здание на Neherkade в Гааге '
                 'местным предпринимателям, а FB Investments купил у Rabobank её офис на Keizer '
                 'Karelplein в Неймегене. Это повторяет квартальную картину Cushman & Wakefield: за '
                 'полугодие в офисы вложено всего €587 млн — меньше, чем в любой другой сектор, — '
                 'рынок держат семейные офисы и частные инвесторы, а фонды сдерживает налог на '
                 'передачу 10,4% от цены. Цены при этом растут: по индексу офисов CBS/Kadaster '
                 '(1 сентября) во втором квартале они были на 5,7% выше прошлогодних, хотя пик '
                 '+7,0% пройден в третьем квартале 2025-го. Настроение на бирже хуже: европейские '
                 'акции недвижимости в августе потеряли 4,3% после трёх месяцев роста (индекс '
                 'GPR 250 Europe, 1 сентября).',
                 'Старый фонд получает вторую жизнь или уходит из сектора. Aroundtown 4 сентября '
                 'заново открыл Coolse Poort в центре Роттердама: бывший офис одного арендатора '
                 'превращён в многофункциональный комплекс 28 000 кв. м. Emro в Амстердаме, '
                 'напротив, отказался от офисов: 2 сентября компания и муниципалитет договорились '
                 'перевести уже полностью согласованный офисный проект Amstel Next в Bullewijk в '
                 '142 арендные квартиры — офисы «не нашли рынка». При этом качественных площадей '
                 'не хватает: анализ от 31 августа показал, что даже на Zuidas, главной деловой '
                 'улице страны, строить новые офисы всё труднее, и выход — обновление старых '
                 'неэффективных зданий. Эйндховен даёт ту же картину с другой стороны: за двадцать '
                 'лет в центре почти ничего не строили, и новые EDGE Eindhoven (25 000 кв. м) и '
                 'The RED (12 500 кв. м) быстро заполнились — отложенный спрос был велик '
                 '(Verschuuren & Schreppers, 1 сентября).',
                 'Главный риск недели — в Роттердаме. 31 августа муниципалитет подтвердил, что '
                 'может покинуть 40 000 кв. м в башне De Rotterdam на Wilhelminapier, когда договор '
                 'истечёт в конце 2028 года: цель — экономия на площадях. Тот же город 4 сентября '
                 'объявил, что в ноябре представит план постановления о пустующих зданиях — '
                 'реестр, штрафы и в перспективе принудительное предложение арендаторов '
                 'владельцам, годами держащим объекты пустыми.',
             ],
             sources='Arcona 04.09 · FB Investments 04.09 · CBS/Kadaster/DNB 01.09 · GPR 01.09 · '
                     'Aroundtown 04.09 · Emro 02.09 · Vastgoedmarkt 31.08 · Verschuuren & Schreppers 01.09 · '
                     'муниципалитет Роттердама 31.08, 04.09 · C&W MarketBeat Q2 2026'),
        dict(key='occupier', title='Рынок пользователя',
             subtitle='качество в дефиците, арендаторы продлевают, а не переезжают',
             paras=[
                 'Опрос RTL Z пяти консультантов по недвижимости (31 августа) описывает спрос: '
                 'работодатели вкладываются в современные офисы у транспортных узлов, чтобы вернуть '
                 'людей в офис и привлекать кадры; на центральных локациях спрос на качественные '
                 'площади превышает предложение, и ставки там растут. Здания при этом заняты '
                 'неровно: по данным Center for People and Buildings (NOS, 7 сентября), пик '
                 'посещаемости приходится на вторник и четверг, а работники умственного труда '
                 'проводят в офисе около 38% рабочего времени и половину — дома. Квартальная база '
                 'Cushman & Wakefield согласуется: прайм-ставка €625 за кв. м в год при вакансии '
                 '6,7%, а поглощение с начала года — 420 837 кв. м, на 15% меньше прошлогоднего: '
                 'арендаторы платят за качество, но не берут больше метров. Квартальная база '
                 'объясняет, почему при таком спросе поглощение падает: высокие затраты на отделку '
                 'и переезд плюс растущие ставки удерживают компании в существующих зданиях; те, '
                 'кто всё же переезжает, берут меньше метров, но дороже и лучше, и всё чаще выбирают '
                 'офисы «под ключ», не требующие вложений в отделку. Медленнее всего решения '
                 'принимаются в сегменте 1 000–2 000 кв. м. Новых игроков мало: компании не растут '
                 'настолько, чтобы искать площади, и не сокращаются настолько, чтобы их сдавать; '
                 'заметный новый спрос создают только ИИ-компании в Амстердаме. В Гааге '
                 'государственный Rijksvastgoedbedrijf занимает столько качественных площадей, что '
                 'коммерческим арендаторам их не хватает, — от этого выигрывает Роттердам.',
                 'Сделки недели — почти сплошь удержание. Aroundtown 1 сентября сообщил о '
                 'продлениях и новых договорах более чем на 14 500 кв. м на Admiraliteitskade 62 в '
                 'Роттердаме; Deloitte 7 сентября подписал новую долгосрочную аренду в башне '
                 'Maastoren на Wilhelminakade; экологическая служба DCMR 4 сентября надолго '
                 'продлила аренду в Схидаме, где сидит с 2010 года. Новые договоры точечные: '
                 'Opcharge взял The Loft в WTC Rotterdam (3 сентября), PIDZ — площади в Diana & '
                 'Vesta в Амстердам-Зёйдост на пять лет (2 сентября). Роттердам доминирует в '
                 'спросе недели — и он же, через возможный уход муниципалитета из De Rotterdam, '
                 'несёт главный риск предложения. Все пять договоров — обновлённые здания у вокзалов '
                 'или на набережной: это и есть бегство в качество — меньше метров, лучше место. '
                 'Для владельцев это значит, что доход растёт за счёт индексации и продлений, а не '
                 'новых арендаторов; здания без обновления теряют арендаторов при первой '
                 'возможности переезда — как показывает возможный уход муниципалитета из De Rotterdam.',
             ],
             sources='RTL Z 31.08 · CFPB/NOS 07.09 · Aroundtown 01.09 · Deloitte 07.09 · DCMR 04.09 · '
                     'Opcharge 03.09 · DHS REIM 02.09 · C&W MarketBeat Q2 2026'),
        dict(key='outlook', title='Прогноз',
             subtitle='поляризация продолжится',
             paras=[
                 'Рынок разделён на два. Лучшие здания у транспорта дорожают и заполняются; '
                 'вторичный фонд либо перестраивается (Coolse Poort), либо уходит в жильё '
                 '(Amstel Next), либо переходит к местным владельцам по ценам, далёким от '
                 'институциональных ориентиров. Решение Роттердама по De Rotterdam до 2028 года '
                 'станет тестом для всего Wilhelminapier. Институциональные инвесторы, по '
                 'квартальной базе, могут вернуться в ближайшие месяцы к хорошо связанным '
                 'устойчивым офисам в крупных городах, где прайм-доходность около 5%, — но без '
                 'давления на владельцев продавать рынок будет ждать. На 12 месяцев: прайм-ставка '
                 'выше €625, вакансия ниже 6,7% за счёт вывода зданий из офисного фонда, объём '
                 'сделок держится на частном капитале, рост цен замедляется вслед за индексом.',
             ],
             sources='оценка редакции по источникам недели и Cushman & Wakefield MarketBeat Q2 2026'),
    ],
}

# Три цифры недели: (id факта, крупная цифра, пояснение). Факт обязан быть отобран.
TAKEAWAYS = {
    'living': [
        ('l-cbre', '€7 млрд', 'вложено в недвижимость за I полугодие, +36% — лучшее полугодие с 2022 года'),
        ('l-uitpond', '76%', 'распродающих частных арендодателей вернутся только после правки правила контрдоказательства'),
        ('l-huur', '4,4%', 'рост аренды за год в июле — третий год замедления; инфляция 3,3%'),
    ],
    'retail': [
        ('r-stivad', '€800 млн', 'вложений в магазины за полугодие — на 16% меньше, чем год назад; доходность 7,9%'),
        ('r-cbs', '+2,8%', 'оборот розницы в июле к прошлому году; объём продаж +1,8%, онлайн +3,8%'),
        ('r-blokker', '66', 'магазинов будет у Blokker после пяти открытий осенью, 12 из них у франчайзи'),
    ],
    'industrial': [
        ('i-eqt', '55 000 кв. м', 'распределительный центр Panattoni в Алмело перешёл к EQT — крупнейшая сделка недели'),
        ('i-cbspi', '+4,7%', 'цены складов за год по индексу CBS/Kadaster — рост замедляется восьмой квартал'),
        ('i-vanacht', '58 000 кв. м', 'новый центр Van Acht в Вегеле под производителя электромобилей и Mars'),
    ],
    'offices': [
        ('o-derotterdam', '40 000 кв. м', 'может освободить муниципалитет Роттердама в башне De Rotterdam после 2028 года'),
        ('o-cbspi', '+5,7%', 'цены офисов за год по индексу CBS/Kadaster; пик +7,0% пройден в 2025 году'),
        ('o-cfpb', '38%', 'рабочего времени работники умственного труда проводят в офисе; пики — вторник и четверг'),
    ],
}

GLOSSARY = {
    'living': [
        ('Box 3', 'налог на сбережения и инвестиции, включая сдаваемое жильё; сейчас считается '
                  'от условной доходности, а не от фактического дохода'),
        ('Правило контрдоказательства', 'право доказать налоговой, что реальный доход ниже условного, '
                                       'и заплатить меньше'),
        ('ATAD', 'правило ЕС, ограничивающее вычет процентов по кредитам из налогооблагаемой '
                 'прибыли; для жилищных корпораций — лишний налог'),
        ('Prinsjesdag', 'день оглашения бюджета, третий вторник сентября — 15.09.2026'),
        ('Контролёр качества', 'независимый инспектор стройки, обязательный с 2024 года по закону Wkb'),
    ],
    'retail': [
        ('Валовая начальная доходность', 'годовая арендная плата, делённая на цену покупки'),
        ('Районный центр', 'небольшой торговый центр с супермаркетом-якорем и повседневными магазинами'),
        ('Stivad', 'фонд, куда инвесторы сдают данные о сделках; считает объёмы по дате передачи'),
        ('Франчайзи', 'независимый предприниматель, работающий под вывеской сети'),
        ('Постановление о пустующих зданиях', 'даёт городу реестр долго пустующих объектов, штрафы '
                                              'и право предлагать владельцу арендаторов'),
    ],
    'industrial': [
        ('Перегрузка сети', 'в большинстве регионов новых подключений к электросети нет или '
                            'очередь на годы; мощность стала дефицитом'),
        ('Под заказчика (build-to-suit)', 'здание строится под конкретного арендатора, а не «на склад» в надежде сдать'),
        ('Поглощение', 'площади, арендованные или купленные пользователями за период'),
        ('Прайм-ставка', 'аренда лучших объектов в лучших локациях, € за кв. м в год'),
        ('Обратная аренда', 'продажа здания инвестору, после которой продавец остаётся в нём арендатором'),
    ],
    'offices': [
        ('Прайм-доходность', 'годовая аренда лучших офисов, делённая на цену покупки с расходами'),
        ('Поглощение', 'площади, арендованные или купленные пользователями за период'),
        ('Налог на передачу', '10,4% от цены при покупке коммерческой недвижимости; для жилья с 2026 года — 8%'),
        ('Бегство в качество', 'арендаторы берут меньше метров, но в лучших зданиях у транспорта'),
        ('Индекс цен CBS/Kadaster', 'экспериментальный индекс по реальным сделкам; квартальные '
                                    'значения скачут, поэтому здесь приводится тренд'),
    ],
}


def rows(*r):
    return [dict(value=v, label=l, yoy=y, fc=f) for v, l, y, f in r]


CONF = [('янв', -23), ('фев', -24), ('мар', -30), ('апр', -44), ('май', -46), ('июн', -39), ('июл', -35), ('авг', -34)]
RETAIL_TURNOVER = [('янв 25', 3.3), ('фев', 2.2), ('мар', 3.8), ('апр', 3.6), ('май', 2.6), ('июн', 3.2),
                   ('июл', 4.0), ('авг', 3.6), ('сен', 3.4), ('окт', 2.9), ('ноя', 3.6), ('дек', 2.4),
                   ('янв 26', 1.6), ('фев', 1.6), ('мар', 3.2), ('апр', 3.2), ('май', 3.4), ('июн', 2.9), ('июл', 2.8)]
PRICE_IDX_Q = ['II 24', 'III', 'IV', 'I 25', 'II', 'III', 'IV', 'I 26', 'II 26']
PI_IND = [8.5, 8.6, 8.5, 8.1, 7.5, 6.7, 5.8, 5.1, 4.7]
PI_OFF = [3.0, 4.1, 5.1, 6.1, 6.7, 7.0, 6.7, 6.1, 5.7]


def f1(v):
    return f'{v:.1f}'.replace('.', ',')


SECTORS = {
    'living': dict(
        key='living', name='Жильё',
        tagline='Крупные покупатели вернулись, частные арендодатели уходят, аренда растёт медленнее',
        economic=rows(('1,0%', 'Рост ВВП, 2026 (прогноз CPB)', 0, 0),
                      ('3,3%', 'Инфляция, август (быстрая оценка CBS)', 1, 0),
                      ('3,9%', 'Безработица, II кв. 2026 (CBS)', 1, 0)),
        economic_source='CPB, CBS',
        fundamentals=rows(('€506 000', 'Средняя цена сделки, II кв.', 1, 1),
                          ('4,4%', 'Рост аренды за год, июль', -1, -1),
                          ('395 000', 'Дефицит жилья, единиц', 1, 0)),
        fundamentals_source='C&W MarketBeat Q2 2026, CBS, ABF',
        stats=dict(title='Квартальная база', c1='II кв. 2026', c2='год назад',
                   source='Cushman & Wakefield MarketBeat Q2 2026, CBS/Kadaster/DNB (индекс цен, тренд)',
                   rows=[('Инвестиции в жильё, I полугодие', '€3,1 млрд', '—'),
                         ('Доля жилья в общем объёме', '53%', '—'),
                         ('Рост цены жилья, кв/кв · г/г', '3,4% · 2,1%', '—'),
                         ('Индекс цен арендного жилья, г/г', '+10,4%', '+8,7%'),
                         ('Жильё у инвесторов (Kadaster)', '745 400', '—'),
                         ('Доля инвесторов в фонде', '8,9%', '9,2%')]),
        charts=[
            dict(title='Цены на жильё, % год к году', kind='line', fmt='{:.1f}',
                 subtitle='индекс цен существующего жилья CBS, июль 2025 — июль 2026',
                 source='Источник: CBS',
                 points=[dict(k=k, v=v, t=(f1(v) if i % 3 == 0 else ''))
                         for i, (k, v) in enumerate(zip(
                             ['июл', 'авг', 'сен', 'окт', 'ноя', 'дек', 'янв', 'фев', 'мар',
                              'апр', 'май', 'июн', 'июл'],
                             [8.6, 7.9, 7.0, 6.6, 6.1, 5.8, 5.4, 5.4, 5.0, 4.3, 4.4, 4.1, 3.9]))]),
            dict(title='Рост арендной платы, % к июлю прошлого года', kind='bar', fmt='{:.1f}',
                 subtitle='все арендные дома, 2016 — 2026',
                 source='Источник: CBS, 04.09.2026',
                 points=[dict(k=str(y), v=v, t=f1(v), dim=(y < 2026))
                         for y, v in zip(range(2016, 2027),
                                         [1.9, 1.6, 2.3, 2.5, 2.9, 0.8, 3.0, 2.0, 5.4, 4.9, 4.4])]),
        ],
        deals=dict(title='Сделки и проекты недели',
                   note='Все сделки недели из пула; цена указана, если раскрыта.'),
        sources_line='Источники: CBS, CPB, CBRE, Vastgoed Belang, VNG, NVM, ING, HDN, PBL, EIB, Liander, '
                     'Министерство жилья, пресс-релизы компаний (через Vastgoedjournaal, Vastgoedmarkt, '
                     'Cobouw, Stadszaken, NOS), Cushman & Wakefield MarketBeat Q2 2026'),
    'retail': dict(
        key='retail', name='Ритейл',
        tagline='Объём сделок упал на 16%, обороты магазинов растут, сети открываются выборочно',
        economic=rows(('3,3%', 'Инфляция, август (быстрая оценка CBS)', 1, 0),
                      ('−34', 'Потребительское доверие, август (CBS)', 1, 0),
                      ('3,9%', 'Безработица, II кв. 2026 (CBS)', 1, 0)),
        economic_source='CBS',
        fundamentals=rows(('7,9%', 'Валовая доходность магазинов, I полуг.', 1, 0),
                          ('€800 млн', 'Вложения в магазины, I полуг. (Stivad)', -1, 1),
                          ('2,8%', 'Оборот розницы за год, июль (CBS)', 1, 0)),
        fundamentals_source='Stivad, CBS',
        stats=dict(title='Квартальная база', c1='I полуг. 2026', c2='год назад',
                   source='Cushman & Wakefield MarketBeat Q2 2026, Stivad, CBS/Kadaster/DNB (индекс цен, тренд)',
                   rows=[('Инвестобъём (C&W)', '€809 млн', '—'),
                         ('Инвестобъём (Stivad)', '€800 млн', '€951 млн'),
                         ('Число сделок (Stivad)', '102', '—'),
                         ('Валовая доходность магазинов', '7,9%', '—'),
                         ('Индекс цен магазинов, г/г', '+3,2%', '+3,1%'),
                         ('Ориентир на год (C&W)', '€1,4 млрд', '—')]),
        charts=[
            dict(title='Оборот розницы, % к тому же месяцу прошлого года', kind='line', fmt='{:.1f}',
                 subtitle='с поправкой на состав торговых дней, январь 2025 — июль 2026',
                 source='Источник: CBS, 01.09.2026',
                 points=[dict(k=(k if i % 3 == 0 or i == 18 else ''), v=v,
                              t=(f1(v) if i in (0, 6, 12, 18) else ''))
                         for i, (k, v) in enumerate(RETAIL_TURNOVER)]),
            dict(title='Вложения в магазины, млн € за I полугодие', kind='bar', fmt='{:.0f}',
                 subtitle='по дате передачи; 2026 — 102 сделки',
                 source='Источник: Stivad, 02.09.2026',
                 points=[dict(k='I пг 2025', v=951, t='951', dim=True),
                         dict(k='I пг 2026', v=800, t='800')]),
        ],
        deals=dict(title='Открытия, закрытия и сделки недели',
                   note='Все сюжеты уровня сделки из пула; площадь указана, если раскрыта.'),
        sources_line='Источники: CBS, Stivad, RetailTrends, Omroep West, Stadszaken, муниципалитет Роттердама, '
                     'пресс-релизы компаний (через Vastgoedjournaal, Vastgoedmarkt), '
                     'Cushman & Wakefield MarketBeat Q2 2026'),
    'industrial': dict(
        key='industrial', name='Склады и индастриал',
        tagline='Крупные сделки вернулись, цену объекта задаёт подключение к сети',
        economic=rows(('1,0%', 'Рост ВВП, 2026 (прогноз CPB)', 0, 0),
                      ('9 из 13', 'Индикаторов CBS ниже тренда, август', -1, 0),
                      ('4,85%', 'Прайм-доходность (GIY, без затрат)', 1, 0)),
        economic_source='CPB, CBS, Cushman & Wakefield',
        fundamentals=rows(('7,71 млн', 'Свободно, кв. м, II кв.', 1, 1),
                          ('1,82 млн', 'Поглощение, кв. м, II кв.', 1, 0),
                          ('€125', 'Прайм-ставка, кв. м/год', 1, 1)),
        fundamentals_source='Cushman & Wakefield MarketBeat Q2 2026',
        stats=dict(title='Квартальная база', c1='2026', c2='2025',
                   source='Cushman & Wakefield MarketBeat Q2 2026, CBS/Kadaster/DNB (индекс цен, тренд)',
                   rows=[('Инвестобъём, I полугодие', '€772 млн', '€980 млн'),
                         ('в том числе логистика', '€618 млн', '—'),
                         ('Поглощение, II кв., млн кв. м', '1,82', '1,62'),
                         ('Свободно, млн кв. м', '7,71', '6,80'),
                         ('Индекс цен, II кв., г/г', '+4,7%', '+7,5%'),
                         ('Доля юга и Рандстада в спросе', '70%', '—')]),
        charts=[
            dict(title='Свободные площади и поглощение, млн кв. м', kind='bar', fmt='{:.1f}',
                 subtitle='II квартал, 2025 и 2026',
                 source='Источник: Cushman & Wakefield MarketBeat Q2 2026',
                 points=[dict(k='свободно 25', v=6.80, t='6,80', dim=True),
                         dict(k='свободно 26', v=7.71, t='7,71'),
                         dict(k='поглощ. 25', v=1.62, t='1,62', dim=True),
                         dict(k='поглощ. 26', v=1.82, t='1,82')]),
            dict(title='Цены производственно-складских объектов, % год к году', kind='line', fmt='{:.1f}',
                 subtitle='индекс CBS/Kadaster/DNB, тренд, II кв. 2024 — II кв. 2026',
                 source='Источник: CBS, 01.09.2026 (экспериментальная статистика)',
                 points=[dict(k=k, v=v, t=(f1(v) if i in (0, 4, 8) else ''))
                         for i, (k, v) in enumerate(zip(PRICE_IDX_Q, PI_IND))]),
        ],
        deals=dict(title='Сделки недели',
                   note='Все сделки недели из пула; площадь и цена указаны, если раскрыты.'),
        sources_line='Источники: CBRE, CBS/Kadaster/DNB, INREV, WDP, EQT, пресс-релизы компаний и брокеров '
                     '(через Vastgoedjournaal, Vastgoedmarkt, Logistiek.nl, Stadszaken), CPB, '
                     'Cushman & Wakefield MarketBeat Q2 2026'),
    'offices': dict(
        key='offices', name='Офисы',
        tagline='Покупают предприниматели и семейные фонды, арендаторы продлевают, а не переезжают',
        economic=rows(('1,0%', 'Рост ВВП, 2026 (прогноз CPB)', 0, 0),
                      ('3,9%', 'Безработица, II кв. 2026 (CBS)', 1, 0),
                      ('5,25%', 'Прайм-доходность (GIY, с затратами)', 1, 0)),
        economic_source='CPB, CBS, Cushman & Wakefield',
        fundamentals=rows(('6,7%', 'Вакансия, II кв.', -1, -1),
                          ('420 837', 'Поглощение, кв. м, с начала года', -1, 0),
                          ('€625', 'Прайм-ставка, кв. м/год', 1, 1)),
        fundamentals_source='Cushman & Wakefield MarketBeat Q2 2026',
        stats=dict(title='Квартальная база', c1='II кв. 2026', c2='год назад',
                   source='Cushman & Wakefield MarketBeat Q2 2026, CBS/Kadaster/DNB (индекс цен, тренд)',
                   rows=[('Инвестобъём, I полугодие', '€587 млн', '—'),
                         ('Вакансия', '6,7%', '—'),
                         ('Поглощение с начала года, кв. м', '420 837', '493 268'),
                         ('Индекс цен офисов, II кв., г/г', '+5,7%', '+6,7%'),
                         ('Прайм-ставка, кв. м/год', '€625', '—'),
                         ('Прайм-доходность (с затратами)', '5,25%', '—')]),
        charts=[
            dict(title='Инвестиции по секторам, I полугодие 2026, млн €', kind='bar', fmt='{:,.0f}',
                 subtitle='офисы — наименьший объём из четырёх секторов',
                 source='Источник: Cushman & Wakefield MarketBeat Q2 2026 (четыре выпуска)',
                 points=[dict(k='жильё', v=3100, t='3 100', dim=True),
                         dict(k='ритейл', v=809, t='809', dim=True),
                         dict(k='индастриал', v=772, t='772', dim=True),
                         dict(k='офисы', v=587, t='587')]),
            dict(title='Цены офисов, % год к году', kind='line', fmt='{:.1f}',
                 subtitle='индекс CBS/Kadaster/DNB, тренд, II кв. 2024 — II кв. 2026',
                 source='Источник: CBS, 01.09.2026 (экспериментальная статистика)',
                 points=[dict(k=k, v=v, t=(f1(v) if i in (0, 5, 8) else ''))
                         for i, (k, v) in enumerate(zip(PRICE_IDX_Q, PI_OFF))]),
        ],
        deals=dict(title='Сделки недели',
                   note='Все сделки недели из пула; площадь указана, если раскрыта.'),
        sources_line='Источники: CBS/Kadaster/DNB, RTL Z, NOS, GPR, муниципалитет Роттердама, пресс-релизы '
                     'компаний и брокеров (через Vastgoedjournaal, Vastgoedmarkt), CPB, '
                     'Cushman & Wakefield MarketBeat Q2 2026'),
}

ORDER = ['living', 'retail', 'industrial', 'offices']
MAX_DEAL_ROWS = 7


def norm(s):
    """Сравнение цифр без оглядки на вид пробела и дефиса; разряды склеиваются,
    чтобы «300» не находилось внутри «1 300»."""
    s = re.sub(r'[\s  ]+', ' ', str(s)).replace('−', '-').lower()
    return re.sub(r'(?<=\d) (?=\d{3}(?!\d))', '', s)


def mentions(text, key):
    """Ключ найден как отдельное число или слово, а не как кусок другого числа."""
    k = norm(key)
    return re.search(r'(?<![\d.,])' + re.escape(k) + r'(?![\d])', text) is not None


def check_link(sel, dropped, blocks):
    """Связка в обе стороны: ключевая цифра каждого отобранного факта обязана быть
    в прозе сектора, а факт, который движок не взял, в прозе появляться не должен —
    иначе отбор становится декорацией."""
    text = norm(' '.join(p for b in blocks for p in b['paras']))
    missing = [i for i in sel if not mentions(text, i['numbers'][0])]
    extra = [i for i in dropped if mentions(text, i['numbers'][0])]
    return missing, extra


def deal_rows(items):
    rows = sorted((i for i in items if i.get('deal')), key=lambda i: i['date'])
    return [(i['date'][8:] + '.' + i['date'][5:7],) + tuple(i['deal']) for i in rows][:MAX_DEAL_ROWS]


def main():
    sys.path.insert(0, HERE)
    from picker import select

    os.makedirs(os.path.join(HERE, 'content'), exist_ok=True)
    os.makedirs(os.path.join(HERE, 'build'), exist_ok=True)
    json.dump({'window': [LO, HI], 'items': POOL},
              open(os.path.join(HERE, 'content', 'pool.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)

    issue = {
        'issue_line': 'Выпуск № 08 · 31 августа — 7 сентября 2026',
        'week_label': '31.08 — 07.09.2026',
        'prepared_by': 'NL Real Estate Research Desk',
        'imprint': 'Аналитика рынка Голландии · даты всех сюжетов проверены по странице источника',
        'sectors': [],
    }
    reports, problems = {}, {}
    print('ОТБОР')
    for key in ORDER:
        items = [i for i in POOL if i['sector'] == key]
        sel, rep = select(items, PROSE_BUDGET, LO, HI)
        reports[key] = rep
        if not rep.get('ok'):
            print(f'  {SECTORS[key]["name"]:<22} НЕ СОБРАЛСЯ: {rep["reason"]} {rep.get("closest")}')
            problems[key] = ['отбор не собрался']
            continue
        sel_ids = [i['id'] for i in sel]
        print(f'  {SECTORS[key]["name"]:<22} {rep["selected"]} из {rep["considered"]}, '
              f'бюджет {rep["fill"]*100:.0f}%, блоки {rep["by_block"]}, знаки {rep["by_polarity"]}')
        print('     отобрано: ' + ', '.join(sel_ids))
        drop = rep['dropped']
        if drop:
            print('     не вошли: ' + ', '.join(f'{d["id"]}' for d in drop))

        sec = dict(SECTORS[key])
        sec['facts'] = sel_ids
        sec['deals'] = dict(sec['deals'], rows=deal_rows(items))
        sec['glossary'] = GLOSSARY[key]
        probs = []
        if key not in PROSE:
            probs.append('проза не написана')
        else:
            sec['blocks'] = PROSE[key]
            dropped = [i for i in items if i not in sel]
            missing, extra = check_link(sel, dropped, PROSE[key])
            if missing:
                probs.append('НЕТ В ПРОЗЕ: ' + ', '.join(f'{i["id"]} [{i["numbers"][0]}]' for i in missing))
            if extra:
                probs.append('В ПРОЗЕ, НО НЕ ОТОБРАНО: ' + ', '.join(f'{i["id"]} [{i["numbers"][0]}]' for i in extra))
            tk = TAKEAWAYS.get(key, [])
            bad_tk = [t[0] for t in tk if t[0] not in sel_ids]
            if len(tk) != 3 or bad_tk:
                probs.append(f'главное недели: нужно 3 отобранных факта, лишние {bad_tk}')
            sec['takeaways'] = [dict(num=t[1], text=t[2], src=next(
                (i['outlet'] + ' ' + i['date'][8:] + '.' + i['date'][5:7]) for i in sel if i['id'] == t[0]))
                for t in tk if t[0] in sel_ids]
        problems[key] = probs
        for p in probs:
            print('     ' + p)
        issue['sectors'].append(sec)

    json.dump(issue, open(os.path.join(HERE, 'content', 'issue.json'), 'w', encoding='utf-8'),
              ensure_ascii=False, indent=1)
    json.dump(reports, open(os.path.join(HERE, 'build', 'select_report.json'), 'w',
                            encoding='utf-8'), ensure_ascii=False, indent=1)
    if any(problems.values()):
        print('\nсвязка отбор -> проза нарушена')
        sys.exit(1)
    print('\nсвязка отбор -> проза: все отобранные факты в тексте, лишних нет')


if __name__ == '__main__':
    main()
