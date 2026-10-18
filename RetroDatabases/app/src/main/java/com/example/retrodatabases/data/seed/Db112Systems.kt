package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 926 to 930: a Japanese code of procedures, the takeover of Balhae, the caliphate of Cordoba and the Icelandic Althing. */
val db112Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 112,
        kind = Kind.SYSTEM,
        section = "Japan",
        title = "The Engishiki, the Japanese code of procedures, completed in the twelfth month of 927",
        originalTitle = "Engishiki",
        author = "The compilers of the Engi-era court",
        year = 927,
        summary = "The Engishiki, a Japanese code of procedures in fifty volumes, was completed in 927 and took effect in 967.",
        details = "Its compilation was ordered in 905, in the Engi era that gives the book its name, and its eighth book holds the core set of twenty-seven norito prayers. Accounts give the twelfth month of 927 for its completion and 967 for the year it took effect, forty years later.",
    ),

    entry(
        databaseId = 112,
        kind = Kind.SYSTEM,
        section = "Manchuria and Korea",
        title = "The takeover of Balhae by the Khitan Liao state in 926",
        originalTitle = "Balhae, also written Bohai",
        author = "The Khitan Liao state",
        year = 926,
        summary = "The Khitan Liao state took over the Balhae kingdom in 926, ending a state founded in 698 whose ruler received a Tang princely title in 713.",
        details = "Most accounts date the Khitan takeover to 926, while one reference gives 936 for the same event. The founding is traditionally dated to 698, and the Tang court named the ruler a prince of Bohai in 713, so the length of Balhae's life depends on which start year is used.",
        rare = true,
    ),

    entry(
        databaseId = 112,
        kind = Kind.SYSTEM,
        section = "Spain",
        title = "The Umayyad caliphate of Cordoba, proclaimed by Abd al-Rahman III in January 929",
        originalTitle = "Al-Khilafa al-Umawiyya bi-Qurtuba",
        author = "Abd al-Rahman III of Cordoba",
        year = 929,
        summary = "Abd al-Rahman III took the title of caliph at Cordoba in January 929, so the Umayyad emirate of al-Andalus became a caliphate that lasted until 1031.",
        details = "The proclamation came on 16 January 929, while the Abbasid caliphs in Baghdad kept the older claim to the title. The caliphate ended in 1031, when its notables deposed Hisham III.",
    ),

    entry(
        databaseId = 112,
        kind = Kind.SYSTEM,
        section = "Iceland",
        title = "The Althing, the assembly of Iceland traditionally founded at Thingvellir in 930",
        originalTitle = "Althing",
        author = "Ulfljotr and the Icelandic settlers",
        year = 930,
        summary = "The Althing, Iceland's general assembly, is traditionally dated to 930, when it met at Thingvellir under a law credited to Ulfljotr.",
        details = "Ari Thorgilsson's Islendingabok, written about 1122, is the main early account of the assembly's origins. The lawspeaker recited about a third of the law aloud each summer, so a three-year term covered the whole code.",
    ),

)
