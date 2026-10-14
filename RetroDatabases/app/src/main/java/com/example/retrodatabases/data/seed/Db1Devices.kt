package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Машины 1980-х: мейнфреймы, советские ЭВМ, персональные и домашние компьютеры, процессоры. */
val db1Devices: List<RecordEntity> = listOf(

    // ---------- Мейнфреймы и мини-ЭВМ ----------

    entry(
        kind = Kind.DEVICE,
        section = "Мейнфреймы и мини-ЭВМ",
        title = "IBM System/360",
        originalTitle = "IBM System/360",
        author = "IBM",
        year = 1964,
        summary = "Семейство совместимых машин: одна архитектура от малых моделей до больших.",
        details = "IBM выпустила System/360 в 1964 году. Программы переносились между моделями, и идея совместимости " +
            "стала стандартом индустрии. На этой архитектуре выросли OS/360 и советская ЕС ЭВМ.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Мейнфреймы и мини-ЭВМ",
        title = "PDP-11",
        originalTitle = "DEC PDP-11",
        author = "Digital Equipment Corporation",
        year = 1970,
        summary = "16-битный миникомпьютер DEC: восемь регистров и понятная система команд.",
        details = "Архитектура PDP-11 была удобной для программистов. На ранних PDP-11 работал Unix, " +
            "а язык C развивался именно на этих машинах.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Мейнфреймы и мини-ЭВМ",
        title = "VAX-11/780",
        originalTitle = "DEC VAX-11/780",
        author = "Digital Equipment Corporation",
        year = 1977,
        summary = "32-битный миникомпьютер DEC с виртуальной памятью; для него создали VMS.",
        details = "VAX-11/780 стал стандартом университетских вычислительных центров 80-х. Виртуальная память позволяла " +
            "запускать большие программы без оверлеев.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Мейнфреймы и мини-ЭВМ",
        title = "Cray-1",
        originalTitle = "Cray-1",
        author = "Cray Research, Сеймур Крей",
        year = 1976,
        summary = "Суперкомпьютер с векторными регистрами: программы писали так, чтобы обрабатывать массивы целиком.",
        details = "Cray-1 выполнял векторные операции над регистрами. Этот стиль — векторизация — определял, " +
            "как писали научные программы 80-х.",
    ),

    // ---------- Отечественные машины ----------

    entry(
        kind = Kind.DEVICE,
        section = "Отечественные машины",
        title = "БЭСМ-6",
        originalTitle = "БЭСМ-6",
        author = "Коллектив под руководством Сергея Лебедева, ИТМ и ВТ",
        year = 1968,
        summary = "Флагман советской вычислительной техники 1960–70-х: около миллиона операций в секунду.",
        details = "На БЭСМ-6 работали монитор «Дубна», Алгол-60 и Фортран. Машины стояли в институтах и вычислительных " +
            "центрах страны и были основой расчётов для науки и оборонки.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Отечественные машины",
        title = "ЕС ЭВМ",
        originalTitle = "Единая система ЭВМ",
        author = "Страны СЭВ",
        year = 1973,
        summary = "Единая система ЭВМ: советская линейка, совместимая с IBM System/360.",
        details = "Серия создавалась совместно со странами СЭВ, а модели выпускались серийно. Совместимость с IBM позволяла " +
            "использовать западное программное обеспечение и идеи.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Отечественные машины",
        title = "Эльбрус-1",
        originalTitle = "Эльбрус-1",
        author = "Коллектив ИТМ и ВТ",
        year = 1979,
        summary = "Советская ЭВМ с нестандартной архитектурой: тегированная память и аппаратная поддержка языков высокого уровня.",
        details = "Проект считался одним из самых смелых в советской вычислительной технике. Программы писали на собственном " +
            "языке высокого уровня, а архитектура стала предметом изучения в вузах.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Отечественные машины",
        title = "Агат",
        originalTitle = "Агат",
        author = "Советская промышленность",
        year = 1984,
        summary = "Советский клон Apple II: программы для Apple часто запускались и на Агате.",
        details = "Машина выпускалась в СССР на базе идей Apple II и стала одной из самых известных домашних машин отечественного производства. " +
            "Её любили в школах и любительских кружках.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Отечественные машины",
        title = "БК-0010",
        originalTitle = "Электроника БК-0010",
        author = "Советская промышленность",
        year = 1985,
        summary = "Советский бытовой компьютер: BASIC в ПЗУ, подключался к обычному телевизору.",
        details = "БК-0010 работал с телевизором и хранил BASIC в постоянной памяти, поэтому пользователь сразу мог писать программы. " +
            "Такие машины стояли в школьных классах и кружках.",
        rare = true,
    ),

    // ---------- Персональные компьютеры ----------

    entry(
        kind = Kind.DEVICE,
        section = "Персональные компьютеры",
        title = "Altair 8800",
        originalTitle = "MITS Altair 8800",
        author = "MITS",
        year = 1975,
        summary = "Первый массовый набор для сборки ПК на Intel 8080.",
        details = "Продавался через журнал Popular Electronics. Для него Microsoft написала Altair BASIC — первый продукт компании. " +
            "Программы вводили переключателями на панели.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Персональные компьютеры",
        title = "Apple II",
        originalTitle = "Apple II",
        author = "Apple",
        year = 1977,
        summary = "Персональный компьютер Apple с цветной графикой и BASIC в ПЗУ.",
        details = "Открытая архитектура со слотами расширения породила огромный рынок плат и программ. В 80-х " +
            "Apple II был основной машиной школ США.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Персональные компьютеры",
        title = "IBM PC",
        originalTitle = "IBM Personal Computer",
        author = "IBM",
        year = 1981,
        summary = "Открытая архитектура IBM с Intel 8088 и MS-DOS: стандарт персональных компьютеров.",
        details = "Клоны IBM PC быстро захватили рынок, и архитектура стала стандартом. Для программистов это означало: " +
            "один код — много машин, но и много тонкостей совместимости.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Персональные компьютеры",
        title = "Apple Macintosh 128K",
        originalTitle = "Macintosh 128K",
        author = "Apple",
        year = 1984,
        summary = "Персональный компьютер с мышью, окнами и процессором Motorola 68000.",
        details = "128 КБ памяти и маленький чёрно-белый экран, но интерфейс с мышью стал эталоном. " +
            "Программисты писали под Toolbox — набор процедур интерфейса.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Персональные компьютеры",
        title = "Osborne 1",
        originalTitle = "Osborne 1",
        author = "Osborne Computer Corporation",
        year = 1981,
        summary = "Первый массовый портативный компьютер: около 11 кг, CP/M и комплект программ.",
        details = "Машину продавали с WordStar, SuperCalc и BASIC. Экран был маленьким, зато её можно было унести. " +
            "Компания обанкротилась после анонса новой модели — так появился термин «эффект Осборна».",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Персональные компьютеры",
        title = "Atari ST",
        originalTitle = "Atari ST",
        author = "Atari",
        year = 1985,
        summary = "Компьютер на Motorola 68000 со встроенными MIDI-портами; любим музыкантами.",
        details = "TOS и GEM загружались из ПЗУ. MIDI-интерфейс был встроен в корпус, поэтому ST стал стандартом " +
            "в студиях и у клавишников.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Персональные компьютеры",
        title = "Amiga 1000",
        originalTitle = "Commodore Amiga 1000",
        author = "Commodore",
        year = 1985,
        summary = "Мультимедийный компьютер с сопроцессорами для звука и графики и многозадачностью.",
        details = "Чипы Agnus, Denise и Paula брали на себя графику, звук и работу с дисками. Процессор 68000 был свободен " +
            "для программы, что давало плавные игры и анимацию.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Персональные компьютеры",
        title = "NeXT Computer",
        originalTitle = "NeXT Computer",
        author = "NeXT, Стив Джобс",
        year = 1988,
        summary = "Рабочая станция с Mach/BSD, Objective-C и конструктором интерфейсов Interface Builder.",
        details = "Джобс создал NeXT после ухода из Apple. Система NeXTSTEP на Objective-C предвосхитила многие идеи " +
            "будущих платформ.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Персональные компьютеры",
        title = "Xerox Alto",
        originalTitle = "Xerox Alto",
        author = "Xerox PARC",
        year = 1973,
        summary = "Первая персональная рабочая станция: окна, мышь и сеть Ethernet (прототип 1973 года).",
        details = "Alto создавали в Xerox PARC для исследований. Программы писали на Smalltalk и Mesa. " +
            "Машина стала моделью для Star, Lisa и Macintosh.",
        rare = true,
    ),

    // ---------- Домашние компьютеры ----------

    entry(
        kind = Kind.DEVICE,
        section = "Домашние компьютеры",
        title = "ZX Spectrum",
        originalTitle = "Sinclair ZX Spectrum",
        author = "Sinclair Research, Клайв Синклер",
        year = 1982,
        summary = "Британский домашний компьютер: Z80, BASIC в ПЗУ и цветная графика.",
        details = "Машина продавалась дёшево, с 16–48 КБ памяти. Игры и демо на ZX Spectrum писали в машинных кодах: " +
            "BASIC был медленным.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Домашние компьютеры",
        title = "Commodore 64",
        originalTitle = "Commodore 64",
        author = "Commodore",
        year = 1982,
        summary = "Домашний компьютер с 64 КБ памяти, звуковым чипом SID и графикой VIC-II.",
        details = "Одна из самых продаваемых моделей в истории. Звуковой чип SID давал многоголосый синтез, " +
            "и его до сих пор изучают в демосцене.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Домашние компьютеры",
        title = "BBC Micro",
        originalTitle = "Acorn BBC Micro",
        author = "Acorn Computers",
        year = 1981,
        summary = "Учебный компьютер Acorn для британских школ на процессоре 6502 с BASIC и Logo.",
        details = "Программа BBC Computer Literacy Project поставляла машины в школы. BASIC в ПЗУ имел расширенные " +
            "команды для графики и звука.",
        rare = true,
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Домашние компьютеры",
        title = "MSX",
        originalTitle = "MSX",
        author = "Microsoft, ASCII и японские производители",
        year = 1983,
        summary = "Единый стандарт домашних компьютеров: одинаковый BASIC и картриджи на машинах разных фирм.",
        details = "Microsoft и японские производители договорились о стандарте. Программы и картриджи работали на машинах " +
            "разных компаний — редкий пример совместимости в мире домашних компьютеров.",
        rare = true,
    ),

    // ---------- Процессоры ----------

    entry(
        kind = Kind.DEVICE,
        section = "Процессоры",
        title = "Intel 8086",
        originalTitle = "Intel 8086",
        author = "Intel",
        year = 1978,
        summary = "16-битный процессор Intel: начало семейства x86 и основа IBM PC.",
        details = "Архитектура x86 выросла из 8086 и до сих пор исполняет код 80-х в реальном режиме. Программисты того " +
            "времени осваивали сегментную адресацию: 20-битный адрес получался из двух 16-битных чисел.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Процессоры",
        title = "Zilog Z80",
        originalTitle = "Zilog Z80",
        author = "Zilog, Федерико Фаджин",
        year = 1976,
        summary = "8-битный процессор с расширенной системой команд 8080; ZX Spectrum и CP/M-машины.",
        details = "Z80 совместим с 8080 на уровне программ и добавил индексные регистры и новые команды. " +
            "Работал в ZX Spectrum, Amstrad CPC и сотнях CP/M-машин.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Процессоры",
        title = "MOS 6502",
        originalTitle = "MOS Technology 6502",
        author = "MOS Technology, Чак Педдл",
        year = 1975,
        summary = "Дешёвый 8-битный процессор: Apple II, BBC Micro, Commodore PET.",
        details = "Простой и быстрый процессор с малым числом регистров. Программисты на 6502 считали такты и использовали " +
            "нулевую страницу памяти как быстрые регистры.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Процессоры",
        title = "Motorola 68000",
        originalTitle = "Motorola 68000",
        author = "Motorola",
        year = 1979,
        summary = "Процессор с 32-битными регистрами и 16-битной шиной: Macintosh, Amiga, Atari ST.",
        details = "Ортогональная система команд и удобные режимы адресации нравились программистам на C и ассемблере. " +
            "На 68000 построили целое поколение персональных компьютеров.",
    ),

    entry(
        kind = Kind.DEVICE,
        section = "Процессоры",
        title = "INMOS Transputer",
        originalTitle = "INMOS T414 Transputer",
        author = "INMOS, Дэвид Мэй",
        year = 1984,
        summary = "Процессор с аппаратными каналами связи для параллельных систем; программы писали на Occam.",
        details = "Транспьютеры соединялись в сети без общей памяти. Программы для них описывали процессы и каналы " +
            "напрямую на языке Occam.",
        rare = true,
    ),
)
