package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Системы 1980-х, которых не было в первой редакции: ОС, микроядра, окна, сети и СУБД. */
val db1ExtraSystems: List<RecordEntity> = listOf(

    // ---------- Операционные системы ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "UCSD p-System",
        originalTitle = "UCSD p-System",
        author = "Университет Калифорнии в Сан-Диего",
        year = 1978,
        summary = "ОС и среда на P-коде (1978): одна программа работала на Apple II, IBM PC и многих других машинах.",
        details = "Система была написана на Pascal и выполнялась виртуальной P-машиной. Так переносимость достигалась переносом одного интерпретатора.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "CP/M-86",
        originalTitle = "CP/M-86",
        author = "Digital Research",
        year = 1981,
        summary = "16-битная версия CP/M (1981) от Digital Research для процессоров Intel 8086.",
        details = "CP/M-86 задумывался как преемник CP/M для 16-битных машин, но уступил MS-DOS на рынке IBM-совместимых ПК.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "TRS-DOS",
        originalTitle = "TRSDOS",
        author = "Tandy / Radio Shack",
        year = 1977,
        summary = "Дисковая ОС для TRS-80 (1977): файловая система и команды для программ на BASIC и ассемблере.",
        details = "TRSDOS работала на TRS-80 и на более поздних моделях Tandy. Для миллионов пользователей это была первая дисковая ОС.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "Apple DOS 3.3",
        originalTitle = "Apple DOS 3.3",
        author = "Apple",
        year = 1980,
        summary = "Дисковая ОС Apple II (1980): стандарт для программ 80-х на этой машине.",
        details = "DOS 3.3 пришла на смену DOS 3.2. Она поддерживала тысячи программ, а ProDOS появилась позже (1983).",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "DR DOS",
        originalTitle = "DR DOS",
        author = "Digital Research",
        year = 1988,
        summary = "Клон MS-DOS от Digital Research (1988) с собственным управлением памятью и утилитами.",
        details = "DR DOS предлагала управление памятью, которого не было в ранних версиях MS-DOS. Продавалась как альтернатива Microsoft.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "MSX-DOS",
        originalTitle = "MSX-DOS",
        author = "Microsoft, ASCII",
        year = 1984,
        summary = "ОС для компьютеров MSX (1984), совместимая с CP/M по программному интерфейсу.",
        details = "MSX-DOS позволяла запускать программы CP/M на машинах MSX. Это был мост между миром CP/M и домашними компьютерами.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "ITS",
        originalTitle = "Incompatible Timesharing System",
        author = "MIT, Лаборатория искусственного интеллекта",
        year = 1967,
        summary = "Система разделения времени MIT (1967–1980-е): хакерская культура и место, где появился Emacs.",
        details = "ITS писали и использовали программисты MIT. Ричард Столлман работал с этой системой до создания GNU.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "TOPS-20",
        originalTitle = "TOPS-20",
        author = "Digital Equipment Corporation",
        year = 1976,
        summary = "ОС DEC для мейнфреймов PDP-10 (1976): основа многих университетских вычислительных центров.",
        details = "TOPS-20 развивала идеи разделения времени. Её использовали в университетах для обучения и исследований вплоть до конца 1980-х.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "GS/OS",
        originalTitle = "GS/OS",
        author = "Apple",
        year = 1986,
        summary = "Операционная система Apple IIGS (1986) с иерархией каталогов и многозадачностью.",
        details = "GS/OS заменила ProDOS на 16-битной машине и поддерживала длинные имена файлов.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Операционные системы",
        title = "Desqview",
        originalTitle = "DESQview",
        author = "Quarterdeck",
        year = 1985,
        summary = "Многозадачная оболочка поверх MS-DOS (1985): несколько программ в окнах на обычном PC.",
        details = "Desqview дала многозадачность DOS-программам, которые её не умели. Вместе с Windows она показала спрос на окна.",
        rare = true,
    ),

    // ---------- Варианты Unix ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Варианты Unix",
        title = "Unix System V",
        originalTitle = "UNIX System V",
        author = "AT&T",
        year = 1983,
        summary = "Коммерческий Unix AT&T (1983): основа большого семейства вариантов Unix.",
        details = "System V стала эталоном для коммерческих Unix-систем. От неё происходят Solaris, HP-UX и многие другие.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Варианты Unix",
        title = "AIX",
        originalTitle = "IBM AIX",
        author = "IBM",
        year = 1986,
        summary = "Unix от IBM (1986): первая версия для рабочих станций RT PC.",
        details = "AIX впервые вышла на рабочих станциях IBM RT PC, а позже работала на больших системах IBM.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Варианты Unix",
        title = "A/UX",
        originalTitle = "Apple A/UX",
        author = "Apple",
        year = 1988,
        summary = "Unix для Macintosh II (1988): можно было запускать Unix и Mac-программы на одной машине.",
        details = "A/UX стала первой попыткой Apple предложить полноценный Unix. Продажи были скромными, но идея жила в Mac OS X.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Варианты Unix",
        title = "Ultrix",
        originalTitle = "DEC Ultrix",
        author = "Digital Equipment Corporation",
        year = 1984,
        summary = "Unix для машин DEC (1984): варианты для VAX и MIPS на рабочих станциях.",
        details = "Ultrix ставили в университетах и научных центрах, где работали машины DEC.",
        rare = true,
    ),

    // ---------- Микроядра и распределённые системы ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Микроядра и распределённые системы",
        title = "Mach",
        originalTitle = "Mach",
        author = "Университет Карнеги — Меллона",
        year = 1985,
        summary = "Микроядро Mach (1985): основа NeXTSTEP и позже macOS.",
        details = "Mach выносил файловые системы и сеть из ядра в пользовательские серверы. Эта идея живёт в современном ядре XNU.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Микроядра и распределённые системы",
        title = "NeXTSTEP",
        originalTitle = "NeXTSTEP",
        author = "NeXT",
        year = 1989,
        summary = "Система NeXT (1989) на Mach и BSD с Objective-C и графическим интерфейсом.",
        details = "NeXTSTEP стала основой для будущей macOS. Её объектно-ориентированный интерфейс опирался на Objective-C.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Микроядра и распределённые системы",
        title = "QNX",
        originalTitle = "QNX",
        author = "Quantum Software Systems",
        year = 1982,
        summary = "Микроядерная ОС реального времени (1982) для промышленности и медицины.",
        details = "QNX передавала сообщения между процессами, что давало предсказуемые задержки. Система используется в автомобилях и медицинском оборудовании.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Микроядра и распределённые системы",
        title = "Amoeba",
        originalTitle = "Amoeba",
        author = "Эндрю Таненбаум и коллеги, Vrije Universiteit",
        year = 1981,
        summary = "Распределённая ОС (1981): много машин выглядят как одна система с общими файлами.",
        details = "Amoeba создавалась как учебный и исследовательский проект по распределённым системам. Её идеи повлияли на последующие работы.",
        rare = true,
    ),

    // ---------- Графика и сети ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Графика и сети",
        title = "X Window System",
        originalTitle = "X Window System (X10, X11)",
        author = "MIT, проект Athena",
        year = 1984,
        summary = "Оконная система для Unix (1984; X11 в 1987): окна не привязаны к железу и работают по сети.",
        details = "X разделяла сервер, который рисует, и клиента, который решает, что рисовать. Эта схема используется в Linux до сих пор.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Графика и сети",
        title = "NeWS",
        originalTitle = "NeWS",
        author = "Sun Microsystems",
        year = 1986,
        summary = "Оконная система Sun (1986) на языке PostScript: окна и графику описывала программа.",
        details = "NeWS была мощной, но тяжёлой для железа 1980-х. Её идеи повлияли на последующие графические системы.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Графика и сети",
        title = "Usenet",
        originalTitle = "Usenet",
        author = "Том Траскотт и Джим Эллис, Duke University",
        year = 1980,
        summary = "Система обмена новостями по сети Unix (1980): предок современных форумов и групп рассылки.",
        details = "Сообщения передавались между машинами через uucp. В 80-х Usenet стал одним из главных мест обсуждения программирования.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Графика и сети",
        title = "FidoNet",
        originalTitle = "FidoNet",
        author = "Том Дженнингс",
        year = 1984,
        summary = "Сеть BBS (1984), которая соединила тысячи домашних ПК ночными звонками по модему.",
        details = "Сообщения передавались от узла к узлу по расписанию. FidoNet работала на обычных телефонных линиях и связывала пользователей по всему миру.",
        rare = true,
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Графика и сети",
        title = "TCP/IP",
        originalTitle = "TCP/IP",
        author = "Винт Серф, Боб Кан и коллеги",
        year = 1983,
        summary = "Стек протоколов TCP/IP (1983): ARPANET перешла на него 1 января 1983 года.",
        details = "Протоколы разделили надёжную передачу (TCP) и адресацию пакетов (IP). Эта схема стала основой Интернета.",
    ),

    entry(
        kind = Kind.SYSTEM,
        section = "Графика и сети",
        title = "Ethernet",
        originalTitle = "Ethernet (DIX)",
        author = "Xerox PARC, DEC, Intel",
        year = 1980,
        summary = "Спецификация Ethernet (1980): локальная сеть, которая связала рабочие станции и ПК.",
        details = "Первая спецификация DIX появилась в 1980 году. Коаксиальный кабель, а позже витая пара, сделали сеть дешёвой.",
    ),

    // ---------- Системы управления базами данных ----------

    entry(
        kind = Kind.SYSTEM,
        section = "Системы управления базами данных",
        title = "IBM DB2",
        originalTitle = "IBM DB2",
        author = "IBM",
        year = 1983,
        summary = "Реляционная СУБД IBM (1983) для мейнфреймов с языком SQL: эталон для коммерческих баз.",
        details = "DB2 вышла для MVS и позже получила версии для других платформ. Многие банки и страховые держали на ней данные десятилетиями.",
    ),
)
