package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Операционные системы, среды и СУБД 1980-х: массовые, исследовательские и почти забытые. */
val db1Systems: List<RecordEntity> = listOf(

    // ---------- Операционные системы ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "UNIX",
        originalTitle = "UNIX",
        author = "Кен Томпсон, Деннис Ритчи (Bell Labs)",
        year = 1971,
        summary = "Многозадачная ОС Bell Labs, написанная в основном на C; идейный предок Linux, BSD и macOS.",
        details = "Проект начали в 1969 году на PDP-7, в 1971-м вышло первое руководство. В 1973 году ядро переписали на C. " +
            "Версия 7 (1979) стала отправной точкой для System V и BSD. Философия: маленькие программы, текст и конвейеры.",
        example = """
            ls -l | grep '^d'   # только каталоги
        """,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "Multics",
        originalTitle = "Multics",
        author = "MIT, Bell Labs, General Electric",
        year = 1969,
        summary = "Предок Unix: многопользовательская ОС с иерархическими каталогами и кольцами защиты.",
        details = "Проект 1965–1969 годов. Идеи иерархических каталогов и колец защиты перешли в Unix, " +
            "но сама система была сложной и медленной.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "IBM OS/360",
        originalTitle = "OS/360",
        author = "IBM",
        year = 1966,
        summary = "Главная ОС семейства System/360: огромный проект, на опыте которого родился закон Брукса.",
        details = "Первые релизы — в 1966 году. Фред Брукс руководил разработкой; опыт описан в «Мифическом человеко-месяце» (1975). " +
            "OS/360 задала стандарты пакетной обработки и язык управления заданиями JCL.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "ОС ЕС",
        originalTitle = "ОС ЕС ЭВМ",
        author = "Разработчики ЕС ЭВМ, СССР и страны СЭВ",
        year = 1973,
        summary = "Операционные системы «Единой системы» ЭВМ: советские машины с архитектурой IBM System/360.",
        details = "Программы ЕС ЭВМ создавались по образцу OS/360. В 1970–1980-х эти системы работали в вычислительных " +
            "центрах НИИ и на предприятиях.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "CP/M",
        originalTitle = "CP/M",
        author = "Гэри Килдалл, Digital Research",
        year = 1974,
        summary = "ОС для 8-битных машин на Intel 8080 и Zilog Z80; до MS-DOS — стандарт де-факто.",
        details = "Килдалл создал CP/M в 1974 году. Она работала на Altair, Osborne 1 и сотнях других машин. " +
            "Под CP/M писали WordStar и первые версии dBase, а её интерфейс повлиял на MS-DOS.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "MS-DOS",
        originalTitle = "MS-DOS, PC DOS",
        author = "Microsoft",
        year = 1981,
        summary = "Однозадачная ОС IBM PC с файловой системой FAT и командной строкой.",
        details = "Microsoft лицензировала 86-DOS и поставила её IBM под именем PC DOS (1981). В 80-х MS-DOS задала стандарт PC: " +
            "640 КБ обычной памяти, драйверы, оверлеи и резидентные программы (TSR).",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "VMS",
        originalTitle = "VMS (позже OpenVMS)",
        author = "Digital Equipment Corporation",
        year = 1977,
        summary = "ОС миникомпьютеров VAX: виртуальная память, кластеры и надёжность.",
        details = "DEC создала VMS для VAX-11/780. Кластеры из нескольких машин с общими дисками считались образцом " +
            "отказоустойчивости. Потомок системы, OpenVMS, работает до сих пор.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "OS-9",
        originalTitle = "OS-9",
        author = "Microware, Motorola",
        year = 1979,
        summary = "Многозадачная UNIX-подобная ОС для процессоров 6809 и 68000 в промышленности и домашней технике.",
        details = "OS-9 работала на промышленных контроллерах и на домашних компьютерах Tandy Color Computer. " +
            "Её ядро было компактным и подходило для систем реального времени.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "OS/2",
        originalTitle = "OS/2",
        author = "IBM, Microsoft",
        year = 1987,
        summary = "Многозадачная ОС для PC с защитой памяти и графической оболочкой Presentation Manager.",
        details = "OS/2 задумывалась как преемник DOS. Она требовала много памяти и медленно обрастала программами; " +
            "позже её вытеснили Windows NT и Windows 95.",
    ),

    // ---------- Варианты Unix ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Варианты Unix",
        title = "BSD 4.2",
        originalTitle = "4.2BSD",
        author = "Университет Беркли",
        year = 1983,
        summary = "Вариант Unix из Беркли: сокеты, стек TCP/IP и быстрая файловая система.",
        details = "В Беркли добавили сетевые сокеты и TCP/IP, а файловую систему FFS сделали быстрой. " +
            "Из BSD выросли FreeBSD и OpenBSD, а код BSD вошёл в macOS.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Варианты Unix",
        title = "SunOS",
        originalTitle = "SunOS",
        author = "Sun Microsystems",
        year = 1982,
        summary = "Unix для рабочих станций Sun на основе BSD; сетевая файловая система NFS появилась в 1984-м.",
        details = "Sun выпустила SunOS в 1982 году. NFS позволяла монтировать диски другой машины прямо в дерево каталогов — " +
            "для 80-х это было новинкой.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Варианты Unix",
        title = "Xenix",
        originalTitle = "Xenix",
        author = "Microsoft",
        year = 1980,
        summary = "Вариант Unix от Microsoft для PC и микроЭВМ, лицензированный у AT&T.",
        details = "Microsoft продавала Xenix для PC/XT и PC/AT. Unix давал многозадачность и сеть, но MS-DOS в 80-х " +
            "оставалась проще и дешевле.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Варианты Unix",
        title = "Minix",
        originalTitle = "MINIX",
        author = "Эндрю Таненбаум",
        year = 1987,
        summary = "Учебный Unix-подобный клон для IBM PC; вдохновил Линуса Торвальдса на создание Linux.",
        details = "Таненбаум написал Minix, чтобы студенты видели устройство ОС в исходниках. " +
            "В 1991 году Торвальдс начал Linux, вдохновившись этой системой.",
    ),

    // ---------- ОС для домашних и графических компьютеров ----------

    entry(
        kind = Kind.SYSTEM,
        section = "ОС для домашних и графических компьютеров",
        title = "Mac OS System 1",
        originalTitle = "System 1",
        author = "Apple",
        year = 1984,
        summary = "Графическая ОС Macintosh: окна, меню, иконки и мышь.",
        details = "System 1 вышла вместе с Macintosh 128K. Интерфейс опирался на опыт Xerox Star и Lisa и стал образцом " +
            "для индустрии 80-х. Программы использовали общий набор процедур Toolbox.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "ОС для домашних и графических компьютеров",
        title = "AmigaOS",
        originalTitle = "AmigaOS (Exec, Intuition)",
        author = "Commodore",
        year = 1985,
        summary = "Многозадачная ОС Amiga: отдельные процессы для звука, графики и интерфейса.",
        details = "Exec — ядро, Intuition — графический интерфейс. Программы работали параллельно, а чипсет брал на себя " +
            "звук и графику, освобождая процессор.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "ОС для домашних и графических компьютеров",
        title = "TOS и GEM",
        originalTitle = "TOS, GEM",
        author = "Atari, Digital Research",
        year = 1985,
        summary = "Система Atari ST: TOS в ПЗУ и графическая оболочка GEM.",
        details = "TOS записана в ПЗУ и запускалась сразу после включения. GEM — оконная оболочка, похожая на Macintosh. " +
            "ST стал популярен у музыкантов благодаря встроенным MIDI-портам.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "ОС для домашних и графических компьютеров",
        title = "Windows 1.0",
        originalTitle = "Windows 1.0",
        author = "Microsoft",
        year = 1985,
        summary = "Графическая оболочка поверх MS-DOS: окна, мышь и простые приложения.",
        details = "Первые версии Windows (1985) были медленными и почти не использовали возможности процессора. " +
            "Массовый успех пришёл с Windows 3.0 (1990).",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "ОС для домашних и графических компьютеров",
        title = "ProDOS",
        originalTitle = "ProDOS",
        author = "Apple",
        year = 1983,
        summary = "ОС Apple II с иерархическими каталогами и поддержкой больших дисков.",
        details = "ProDOS пришла на смену DOS 3.3 и поддерживала подкаталоги и блочные устройства большой ёмкости. " +
            "Для программистов Apple II это был заметный шаг вперёд.",
        rare = true,
    ),

    // ---------- Исследовательские среды ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Исследовательские среды",
        title = "Lisp-машины Symbolics",
        originalTitle = "Symbolics Genera",
        author = "Symbolics, LMI",
        year = 1980,
        summary = "Среда, в которой редактор, отладчик и сама система написаны на Lisp.",
        details = "Lisp-машины Symbolics и LMI были персональными рабочими станциями для ИИ. Исходники системы можно было " +
            "читать и менять прямо на машине. Стоили десятки тысяч долларов.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Исследовательские среды",
        title = "Oberon System",
        originalTitle = "Oberon System",
        author = "Никлаус Вирт, Юрг Гутнехт (ETH Zurich)",
        year = 1987,
        summary = "ОС и редактор Вирта: весь код на Oberon, любой текст может быть командой.",
        details = "Систему создали в ETH Zurich для рабочей станции Ceres. Щелчок мышью по слову в тексте запускает " +
            "процедуру, поэтому документ и интерфейс — одно и то же.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Исследовательские среды",
        title = "Xerox Star",
        originalTitle = "Xerox 8010 Star",
        author = "Xerox",
        year = 1981,
        summary = "Первая коммерческая система с окнами, иконками и мышью.",
        details = "Star продавалась дорого и проиграла конкуренции с IBM PC. Но её рабочий стол, папки и иконки стали " +
            "образцом для Apple и Microsoft. Программное обеспечение писали на Mesa.",
        rare = true,
    ),

    // ---------- Системы управления базами данных ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Системы управления базами данных",
        title = "Oracle",
        originalTitle = "Oracle Version 2",
        author = "Oracle Corporation",
        year = 1979,
        summary = "Одна из первых коммерческих СУБД с языком SQL (вторая версия, 1979).",
        details = "Вторая версия Oracle вышла в 1979 году и стала одной из первых коммерческих реализаций SQL. " +
            "В 80-х компания быстро вышла в лидеры рынка реляционных СУБД.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Системы управления базами данных",
        title = "dBase",
        originalTitle = "dBase II",
        author = "Ashton-Tate",
        year = 1980,
        summary = "Персональная СУБД с командами для таблиц .dbf: поиск, сортировка, отчёты.",
        details = "dBase II сначала работала под CP/M, затем под MS-DOS. Бухгалтерские и складские программы в 80-х " +
            "часто писали на ней. Формат .dbf пережил саму программу.",
    ),
)
