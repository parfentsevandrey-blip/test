package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 840 to 850: the years from the Treaty of Verdun to the Treaty of Meerssen, and from the death of Theophilos to the death of Wuzong. */
val db120Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 120,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the Treaty of Verdun to the Treaty of Meerssen",
        originalTitle = "Years between two treaties",
        author = "Lothair I, Louis the German and Charles the Bald",
        year = 843,
        summary = "The Treaty of Verdun was made in 843 and the Treaty of Meerssen in 870, and a short program counts the 27 years between the two settlements.",
        details = "Both years are the usual dates, and the example prints the difference between them. The first treaty divided the Carolingian empire, and the second, of 870, shared out Lotharingia, the northern part of the middle kingdom, between Louis the German and Charles the Bald.",
        instructions = """
            1. Save the example as verdun_meerssen.py.
            2. Run it: python3 verdun_meerssen.py
        """,
        example = """
            VERDUN = 843     # Treaty of Verdun
            MEERSSEN = 870   # Treaty of Meerssen, as commonly dated
            print("years from Verdun to Meerssen:", MEERSSEN - VERDUN)
        """,
    ),

    entry(
        databaseId = 120,
        kind = Kind.FLEX,
        section = "Years",
        title = "Years from the death of Theophilos to the death of Emperor Wuzong of Tang",
        originalTitle = "Years between two deaths",
        author = "Theophilos and Wuzong",
        year = 842,
        summary = "The Byzantine emperor Theophilos died in 842 and the Tang emperor Wuzong in 846, and a short program counts the four years between the two deaths.",
        details = "Both dates are the usual ones, and the two rulers held power on opposite sides of Eurasia in the same years. The example prints the difference, which belongs to neither ruler alone.",
        instructions = """
            1. Save the example as two_deaths.py.
            2. Run it: python3 two_deaths.py
        """,
        example = """
            THEOPHILOS_DIED = 842   # death of the Byzantine emperor Theophilos
            WUZONG_DIED = 846       # death of the Tang emperor Wuzong
            print("years between the two deaths:", WUZONG_DIED - THEOPHILOS_DIED)
        """,
    ),

)
