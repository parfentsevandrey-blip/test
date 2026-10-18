package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex of 960 to 970: three one-line sums about pairs of dated events of the decade. */
val db108Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 108,
        kind = Kind.FLEX,
        section = "Astronomy",
        title = "Years from al-Sufi's Book of the Fixed Stars to its Doha copy dated 1125",
        originalTitle = "Years from al-Sufi's Book of the Fixed Stars to its Doha copy dated 1125",
        author = "Abd al-Rahman al-Sufi",
        year = 964,
        summary = "The Book of the Fixed Stars of about 964 and its Doha copy of 1125 are 161 years apart, by a simple subtraction of the two dates.",
        details = "Most sources give 964 for the book, and the Doha Museum copy is dated 1125 and is likely the earliest surviving copy. The result is a difference between two dates, not the time it took to copy the book.",
        rare = true,
        instructions = """
            1. Save the example as doha.py.
            2. Run it: python3 doha.py
        """,
        example = """
            book_year = 964       # al-Sufi's book, as most sources give it
            doha_copy = 1125      # the Doha copy, dated 1125
            print("years from the book to the Doha copy:", doha_copy - book_year)
        """,
    ),

    entry(
        databaseId = 108,
        kind = Kind.FLEX,
        section = "China and Egypt",
        title = "Years from the founding of the Song dynasty in 960 to the founding of Cairo in 969",
        originalTitle = "Years from the founding of the Song dynasty in 960 to the founding of Cairo in 969",
        author = "Zhao Kuangyin and Jawhar al-Siqilli",
        year = 960,
        summary = "The Song founding of 960 and the founding of Cairo in 969 are nine years apart, and a short program states the difference.",
        details = "Both dates are the usual years for the events, with the Song founded in early 960 and Cairo founded in 969 as the palace city beside Fustat. The result is a difference of years, which stands for the time between the two foundings.",
        instructions = """
            1. Save the example as song_cairo.py.
            2. Run it: python3 song_cairo.py
        """,
        example = """
            song_founding = 960     # Song dynasty founded, as commonly stated
            cairo_founding = 969    # al-Qahira founded beside Fustat, as commonly stated
            print("years from the Song founding to Cairo:", cairo_founding - song_founding)
        """,
    ),

    entry(
        databaseId = 108,
        kind = Kind.FLEX,
        section = "Germany and Constantinople",
        title = "Years from Otto I's coronation at Rome to Liutprand's embassy to Constantinople",
        originalTitle = "Years from Otto I's coronation at Rome to Liutprand's embassy to Constantinople",
        author = "Otto I and Liutprand of Cremona",
        year = 968,
        summary = "Otto I's coronation of 962 and Liutprand's embassy of 968 are six years apart, which a short program states as a difference of years.",
        details = "The coronation is dated to 962 and the embassy to 968, both as commonly stated, and the program gives only the year difference. The two events are linked, because Liutprand was sent by Otto I to the court at Constantinople.",
        instructions = """
            1. Save the example as otto_liutprand.py.
            2. Run it: python3 otto_liutprand.py
        """,
        example = """
            otto_emperor = 962      # coronation of Otto I at Rome, as commonly stated
            embassy = 968           # Liutprand's embassy to Constantinople, as commonly stated
            print("years from Otto I's coronation to Liutprand's embassy:", embassy - otto_emperor)
        """,
    ),

)
