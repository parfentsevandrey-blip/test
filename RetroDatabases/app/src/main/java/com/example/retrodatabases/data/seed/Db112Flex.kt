package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One line of arithmetic each: years between the Balhae takeover and the Althing, the Khitan large script and the Engishiki, and Athelstan and the Cordoba caliphate. */
val db112Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 112,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the fall of Balhae in 926 to the Althing in 930",
        originalTitle = "Years from the fall of Balhae in 926 to the Althing in 930",
        author = "The Khitan Liao state and the Icelandic lawspeakers",
        year = 930,
        summary = "A one-line calculation finds four years from the Khitan takeover of Balhae in 926 to the traditional founding of the Althing in 930.",
        details = "Most accounts date the Khitan takeover of Balhae to 926, and the Althing is traditionally dated to 930, so the calculation gives four years. The calculation is a count of years only and tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as balhae_althing.py.
            2. Run it: python3 balhae_althing.py
        """,
        example = """
            balhae_fall = 926
            althing = 930
            print("years from the fall of Balhae to the Althing:", althing - balhae_fall)
        """,
    ),

    entry(
        databaseId = 112,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Khitan large script of 920 to the Engishiki of 927",
        originalTitle = "Years from the Khitan large script of 920 to the Engishiki of 927",
        author = "Abaoji of the Khitan and the compilers of the Engishiki",
        year = 927,
        summary = "A one-line calculation finds seven years from the devising of the Khitan large script in 920 to the completion of the Engishiki in 927.",
        details = "Most accounts date the Khitan large script to 920 under Abaoji, and the Engishiki was completed in the twelfth month of 927, so the calculation gives seven years. It counts years only, so the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as khitan_engishiki.py.
            2. Run it: python3 khitan_engishiki.py
        """,
        example = """
            khitan_script = 920
            engishiki = 927
            print("years from the Khitan large script to the Engishiki:", engishiki - khitan_script)
        """,
    ),

    entry(
        databaseId = 112,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the start of Athelstan's reign in 924 to the Cordoba caliphate of 929",
        originalTitle = "Years from the start of Athelstan's reign in 924 to the Cordoba caliphate of 929",
        author = "Athelstan of England and Abd al-Rahman III of Cordoba",
        year = 929,
        summary = "A one-line calculation finds five years from the start of Athelstan's reign in 924 to the proclamation of the caliphate at Cordoba in 929.",
        details = "Athelstan succeeded Edward the Elder in 924, and Abd al-Rahman III took the title of caliph at Cordoba in January 929, so the calculation gives five years. It is a count of years only, and the run tests the arithmetic rather than the events.",
        instructions = """
            1. Save the example as athelstan_cordoba.py.
            2. Run it: python3 athelstan_cordoba.py
        """,
        example = """
            athelstan = 924
            cordoba_caliphate = 929
            print("years from Athelstan to the Cordoba caliphate:", cordoba_caliphate - athelstan)
        """,
    ),

)
