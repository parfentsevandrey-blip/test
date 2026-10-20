package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 610 to 620: how a Tang weight of jin, liang and qian is totalled in qian, worked out by a program. */
val db143Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 143,
        kind = Kind.GUIDE,
        section = "Chinese",
        title = "How a Tang weight of jin, liang and qian is turned into qian and back, worked out by a program",
        originalTitle = "jin liang qian",
        author = "Tang officials",
        year = 618,
        summary = "A Tang weight written in jin, liang and qian is turned into qian by a short program, using the ratios of 16 liang to a jin and 10 qian to a liang.",
        details = "The usual accounts do not date this unit rule; 618, the founding year of the Tang, is only the anchor for the record. The program multiplies the weights out and splits the total again with divmod, so a reader can check a sum by hand.",
        rare = true,
        instructions = """
            1. Save the example as jin_liang.py.
            2. Run it: python3 jin_liang.py
        """,
        example = """
            JIN_LIANG = 16      # liang in one jin
            LIANG_QIAN = 10     # qian in one liang
            QIAN_PER_JIN = JIN_LIANG * LIANG_QIAN

            def to_qian(jin, liang, qian):
                return jin * QIAN_PER_JIN + liang * LIANG_QIAN + qian

            def split(total):
                jin, rest = divmod(total, QIAN_PER_JIN)
                liang, qian = divmod(rest, LIANG_QIAN)
                return jin, liang, qian

            weights = [(1, 0, 0), (2, 5, 7), (0, 15, 9)]
            for jin, liang, qian in weights:
                print(f"{jin} jin {liang} liang {qian} qian = {to_qian(jin, liang, qian)} qian")
            total = sum(to_qian(*w) for w in weights)
            print("total:", total, "qian")
            jin, liang, qian = split(total)
            print(f"total in jin, liang and qian: {jin} jin {liang} liang {qian} qian")
        """,
    ),

)
