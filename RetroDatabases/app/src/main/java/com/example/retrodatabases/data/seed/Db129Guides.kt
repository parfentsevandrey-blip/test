package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 750 to 760: a woodblock page carved in mirror image so that its print reads correctly, checked by a short program. */
val db129Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 129,
        kind = Kind.GUIDE,
        section = "Printing",
        title = "How a woodblock page is carved in mirror image so the print reads correctly, checked by a program",
        originalTitle = "Mirror-image carving",
        author = "Unknown woodblock printers",
        year = 751,
        summary = "A woodblock page is carved in mirror image so that the print reads correctly, and a short program carves three lines and checks that each one reads back.",
        details = "The method is the one used for the Pure Light Dharani, which was printed before 751, and the year here is that print's limit, since the method itself has no fixed date. The example reverses each of three ASCII lines to carve them, then reverses them again to check that the impression reads as the original text.",
        instructions = """
            1. Save the example as mirror_block.py.
            2. Run it: python3 mirror_block.py
        """,
        example = """
            lines = ["PURE LIGHT", "SUTRA", "BLOCK"]
            carved = [line[::-1] for line in lines]
            for number, line in enumerate(carved, start=1):
                print("carved line", number, ":", line)
            reads_back = all(line[::-1] == text for line, text in zip(carved, lines))
            print("every line reads back:", reads_back)
        """,
        rare = true,
    ),

)
