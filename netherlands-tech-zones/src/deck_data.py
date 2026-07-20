# -*- coding: utf-8 -*-
"""Slide deck content (Russian). Addresses/coords/residents verified via workflow."""

CAT = {"chips": "#EA580C", "data": "#2563EB", "ai": "#059669"}

META = {
    "kicker": "ПРЕЗЕНТАЦИЯ · 2026",
    "title": "Технологические промзоны Нидерландов",
    "subtitle": "Точные адреса, резиденты и карты — чипы, дата-центры и ИИ",
    "date": "Июль 2026",
}

SECTIONS = {
    "chips": {"num": "01", "title": "Производство чипов", "sub": "Брейнпорт Эйндховен и Неймеген", "img": "assets/wafer.jpg"},
    "data": {"num": "02", "title": "Дата-центры", "sub": "Амстердам, Эмсхавен, Мидденмер", "img": "assets/datacenter.jpg"},
    "ai": {"num": "03", "title": "Искусственный интеллект и наука", "sub": "Делфт и квантовые технологии", "img": "assets/quantum.jpg"},
}

ZONES = [
    {
        "cat": "chips", "key": "asml",
        "name": "ASML — Велдховен", "city": "Велдховен", "region": "Северный Брабант",
        "address": "De Run 6501, 5504 DR Велдховен",
        "maps_query": "De Run 6501, 5504 DR Veldhoven",
        "lat": "51.40468", "lon": "5.41736",
        "img": "assets/sat_asml_veldhoven.jpg",
        "residents": [
            {"name": "ASML", "tag": "Литография EUV/DUV", "addr": "De Run 6501, 5504 DR Велдховен",
             "text": "Делает самые сложные в мире машины фотолитографии — они, как гигантские проекторы, наносят рисунок микросхемы на кремний. Без оборудования ASML нельзя выпускать передовые чипы, поэтому от этого завода зависит вся мировая электроника."},
            {"name": "VDL ETG", "tag": "Точная механика", "addr": "De Schakel 22, 5651 GH Эйндховен",
             "text": "Нидерландский контрактный производитель высокоточных механических узлов и один из главных поставщиков ASML."},
            {"name": "Sioux Technologies", "tag": "Инжиниринг", "addr": "Esp 130, 5633 AA Эйндховен",
             "text": "Инженерная компания: пишет программы, проектирует электронику и механику для сложной техники, включая узлы для ASML."},
        ],
        "facts": ["Выручка 2025: €32,7 млрд", "~83% рынка литографии, ~100% EUV", "~44 000 сотрудников"],
    },
    {
        "cat": "chips", "key": "htc",
        "name": "High Tech Campus Eindhoven", "city": "Эйндховен", "region": "Северный Брабант",
        "address": "High Tech Campus 60, 5656 AG Эйндховен",
        "maps_query": "High Tech Campus 60, 5656 AG Eindhoven",
        "lat": "51.4089", "lon": "5.4604",
        "img": "assets/sat_htc_eindhoven.jpg",
        "residents": [
            {"name": "NXP Semiconductors", "tag": "Чипы · штаб-квартира", "addr": "High Tech Campus 60, 5656 AG Эйндховен",
             "text": "Один из мировых лидеров по производству чипов: для автомобилей, банковских карт и телефонов. Здесь его мировая штаб-квартира."},
            {"name": "Signify", "tag": "Умное освещение", "addr": "High Tech Campus 48, 5656 AE Эйндховен",
             "text": "Бывшая Philips Lighting — мировой лидер освещения, включая умные лампы Philips Hue."},
            {"name": "imec / Holst Centre", "tag": "Открытые НИОКР", "addr": "High Tech Campus 31, 5656 AE Эйндховен",
             "text": "Центр открытых исследований (imec + TNO): наноэлектроника и гибкая электроника — технологии будущего."},
        ],
        "facts": ["~300 компаний", "~12 000 исследователей из 85 стран", "~40% патентов страны"],
    },
    {
        "cat": "chips", "key": "nijmegen",
        "name": "Неймеген — NXP, Nexperia, Ampleon", "city": "Неймеген", "region": "Гелдерланд",
        "address": "Gerstweg 2, 6534 AE Неймеген (завод NXP ICN8)",
        "maps_query": "Gerstweg 2, 6534 AE Nijmegen",
        "lat": "51.8248", "lon": "5.8173",
        "img": "assets/sat_nxp_nijmegen.jpg",
        "residents": [
            {"name": "NXP (фабрика ICN8)", "tag": "Производство чипов", "addr": "Gerstweg 2, 6534 AE Неймеген",
             "text": "Крупный производитель микросхем, ведущий историю от завода Philips 1953 года. Здесь чипы реально производят — на фабрике ICN8."},
            {"name": "Nexperia", "tag": "Дискретные компоненты", "addr": "Jonkerbosplein 52, 6534 AB Неймеген",
             "text": "Отделилась от NXP в 2017 году; выпускает массовые базовые компоненты — транзисторы и диоды. Штаб-квартира в Неймегене."},
            {"name": "Ampleon", "tag": "РЧ-усилители 5G", "addr": "Halfgeleiderweg 8, 6534 AV Неймеген",
             "text": "Бывшее подразделение RF Power компании NXP: силовые радиочастотные транзисторы для базовых станций мобильной связи 5G."},
        ],
        "facts": ["NXP: ~1 700 сотрудников", "Novio Tech Campus: 85+ компаний", "Чипы производят с 1953 года"],
    },
    {
        "cat": "data", "key": "amsterdam",
        "name": "Amsterdam Science Park · AMS-IX", "city": "Амстердам", "region": "Северная Голландия",
        "address": "Science Park 105, 1098 XG Амстердам",
        "maps_query": "Science Park 105, 1098 XG Amsterdam",
        "lat": "52.3564", "lon": "4.9508",
        "img": "assets/sat_amsterdam_sciencepark.jpg",
        "residents": [
            {"name": "AMS-IX", "tag": "Обмен трафиком", "addr": "Science Park 105 (Nikhef), Амстердам",
             "text": "Огромный «перекрёсток» интернета, где сотни операторов связи напрямую обмениваются трафиком. Одна из крупнейших точек обмена в мире."},
            {"name": "Digital Realty (Interxion AMS9)", "tag": "Дата-центр", "addr": "Science Park 121, 1098 XG Амстердам",
             "text": "Крупный дата-центр (бывший Interxion), где физически стоят серверы и сетевое оборудование множества компаний."},
            {"name": "CWI", "tag": "Родина Python", "addr": "Science Park 123, 1098 XG Амстердам",
             "text": "Национальный институт математики и информатики (с 1946 г.). Именно здесь Гвидо ван Россум создал язык программирования Python."},
        ],
        "facts": ["AMS-IX: 882 сети из 70 стран", "~852 МВт ЦОД в Амстердаме", "Nikhef — физика частиц (ЦЕРН)"],
    },
    {
        "cat": "data", "key": "eemshaven",
        "name": "Эмсхавен — дата-центр Google", "city": "Эмсхавен", "region": "Гронинген",
        "address": "Oostpolder 4, 9979 XT Эмсхавен",
        "maps_query": "Oostpolder 4, 9979 XT Eemshaven",
        "lat": "53.4244", "lon": "6.8592",
        "img": "assets/sat_google_eemshaven.jpg",
        "residents": [
            {"name": "Google", "tag": "Гиперскейл-ЦОД", "addr": "Oostpolder 4, 9979 XT Эмсхавен",
             "text": "Огромный дата-центр Google: тысячи серверов, на которых работают Поиск, YouTube, Gmail и Google Cloud. Первый в мире ЦОД Google на 100% зелёной энергии."},
            {"name": "QTS Data Centers", "tag": "Колокейшн", "addr": "Huibertgatweg 2, 9979 XZ Эмсхавен",
             "text": "Коммерческий дата-центр (~36 МВт), где разные компании арендуют место для серверов; подключён к подводному кабелю."},
            {"name": "TenneT", "tag": "Энергосеть", "addr": "Эмсхавен (зона порта)",
             "text": "Оператор высоковольтной сети: узел 380 кВ и преобразовательные станции подводных энергокабелей NorNed и COBRA."},
        ],
        "facts": ["Открыт в 2016 году", "Инвестиции €600 млн + €500 млн", "500+ МВт · ~700 сотрудников"],
    },
    {
        "cat": "data", "key": "agriport",
        "name": "Agriport A7 — Мидденмер", "city": "Мидденмер", "region": "Северная Голландия",
        "address": "Agriport 570 и 601, 1775 TB Мидденмер",
        "maps_query": "Agriport 570, 1775 TB Middenmeer",
        "lat": "52.7656", "lon": "5.0380",
        "img": "assets/sat_agriport_middenmeer.jpg",
        "residents": [
            {"name": "Microsoft", "tag": "Облако Azure", "addr": "Agriport 570 и 601, 1775 TB Мидденмер",
             "text": "Один из крупнейших дата-центровых кампусов Microsoft в Европе: здесь работают облако Azure и Microsoft 365 для региона."},
            {"name": "Google", "tag": "Дата-центр", "addr": "Tussenweg 8, 1775 RK Мидденмер",
             "text": "Второй дата-центр Google в Нидерландах, открыт в декабре 2020 года (инвестиции ~€500 млн)."},
            {"name": "Vattenfall · Windpark Wieringermeer", "tag": "Ветропарк", "addr": "полдер Wieringermeer, рядом с Agriport",
             "text": "Крупнейший наземный ветропарк страны (99 турбин, >300 МВт); питает зелёным током дата-центры Microsoft."},
        ],
        "facts": ["~1,17 ТВт·ч в год (~1% энергии страны)", "ECW — геотермальное тепло для теплиц", "Расширение 2025: +50 га"],
    },
    {
        "cat": "ai", "key": "delft",
        "name": "Делфт — QuTech и кванты", "city": "Делфт", "region": "Южная Голландия",
        "address": "Lorentzweg 1, 2628 CJ Делфт (TU Delft)",
        "maps_query": "Lorentzweg 1, 2628 CJ Delft",
        "lat": "52.0008", "lon": "4.3754",
        "img": "assets/sat_qutech_delft.jpg",
        "residents": [
            {"name": "QuTech (TU Delft + TNO)", "tag": "Квантовые вычисления", "addr": "Lorentzweg 1, 2628 CJ Делфт",
             "text": "Совместный институт университета TU Delft и организации TNO (с 2014 г.) — один из мировых лидеров в квантовых компьютерах и квантовом интернете."},
            {"name": "Microsoft Quantum Lab", "tag": "Топологические кубиты", "addr": "TU Delft, Делфт",
             "text": "Лаборатория Microsoft вместе с QuTech разрабатывает топологические кубиты — особо устойчивый к ошибкам тип квантовых битов."},
            {"name": "QuantWare", "tag": "Квантовые процессоры", "addr": "Molengraaffsingel 8, 2629 JD Делфт",
             "text": "Спин-офф QuTech (2021): проектирует и производит сверхпроводящие квантовые процессоры для лабораторий всего мира."},
        ],
        "facts": ["QuTech: ~350 человек", "Quantum Delta NL: €615 млн", "Qblox — управляющая электроника"],
    },
]

COMPARE = [
    ("chips", "Велдховен", "ASML", "Монополия на EUV — узкое место мировой электроники"),
    ("chips", "High Tech Campus", "NXP, Signify, imec", "~40% патентов страны; 300+ компаний"),
    ("chips", "Неймеген", "NXP, Nexperia, Ampleon", "Крупнейший завод NXP; чипы для авто и 5G"),
    ("data", "Amsterdam Science Park", "AMS-IX, Digital Realty", "Главный узел интернета Европы"),
    ("data", "Эмсхавен", "Google", "Крупнейший ЦОД на 100% зелёной энергии"),
    ("data", "Agriport / Мидденмер", "Microsoft, Google", "Облако рядом с теплицами; ~1% энергии"),
    ("ai", "Делфт", "QuTech, Microsoft", "Мировой центр квантовых вычислений"),
]
