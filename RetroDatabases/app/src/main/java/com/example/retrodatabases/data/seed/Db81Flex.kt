package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Flex pieces of 1230 to 1236: years between a law and a statute, a Mongol council and a carving, and the span of a concordance. */
val db81Flex: List<RecordEntity> = listOf(

    entry(
        databaseId = 81,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the Liber Augustalis to the Statute of Merton",
        originalTitle = "Years between two fixed dates",
        author = "Frederick II and Henry III",
        year = 1231,
        summary = "Frederick II's Liber Augustalis of 1231 and the Statute of Merton of 1236 lie five years apart, or four if the statute is dated 1235, and a short program prints both counts.",
        details = "The two dates are the usual ones, and the Merton date depends on the reckoning, since the statute belongs to the twentieth regnal year of Henry III. The example prints both counts so the dispute shows in the arithmetic.",
        instructions = """
            1. Save the example as laws_years.py.
            2. Run it: python3 laws_years.py
        """,
        example = """
            augustalis = 1231
            merton_dated_1236 = 1236
            merton_dated_1235 = 1235
            print("years to Merton, dated 1236:", merton_dated_1236 - augustalis)
            print("years to Merton, dated 1235:", merton_dated_1235 - augustalis)
        """,
    ),

    entry(
        databaseId = 81,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from the 1235 kurultai to the finish of the Tripitaka carving",
        originalTitle = "Years between two fixed dates",
        author = "Ogedei Khan and the Goryeo carvers",
        year = 1235,
        summary = "The Mongol kurultai of 1235 and the finish of the Tripitaka Koreana carving in 1251 lie 16 years apart, and a short program computes the gap.",
        details = "Both dates are the usual ones, and the example sets a Mongol council beside a Korean printing project. The arithmetic is a difference of two years, which is all the program needs.",
        instructions = """
            1. Save the example as kurultai_tripitaka.py.
            2. Run it: python3 kurultai_tripitaka.py
        """,
        example = """
            kurultai = 1235
            carving_finished = 1251
            print("years from the kurultai to the finish of the carving:", carving_finished - kurultai)
        """,
    ),

    entry(
        databaseId = 81,
        kind = Kind.FLEX,
        section = "Dates",
        title = "Years from Hugh of Saint-Cher's concordance to its probable finish",
        originalTitle = "Years between two fixed dates",
        author = "Hugh of Saint-Cher and the friars of St Jacques",
        year = 1230,
        summary = "Hugh of Saint-Cher's concordance was begun about 1230 and probably finished by 1239, a span of nine years by the usual dates.",
        details = "The start and finish are both approximate, since the work was made over several years and accounts differ on its stages. The example subtracts the two dates to give the span, which is the only arithmetic involved.",
        instructions = """
            1. Save the example as concordance_span.py.
            2. Run it: python3 concordance_span.py
        """,
        example = """
            begun = 1230
            finished = 1239
            print("years from the start to the probable finish:", finished - begun)
        """,
    ),

)
