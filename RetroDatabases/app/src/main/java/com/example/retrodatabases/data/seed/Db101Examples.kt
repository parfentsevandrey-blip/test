package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1036: a count of the Tangut script's characters, converted into the bits needed to index them. */
val db101Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 101,
        kind = Kind.EXAMPLE,
        section = "Writing",
        title = "The Tangut script's roughly 6,000 characters, counted in the bits needed to index them",
        originalTitle = "Tangut script",
        author = "Scribes of the Western Xia state",
        year = 1036,
        summary = "A short program finds that a script of 6,000 characters needs 13 bits to give each character its own index, since 12 bits reach only 4,096.",
        details = "The figure of about 6,000 characters is the usual one for the Tangut script, and it is an approximation of the script's size. The bit count is a computed conversion of that figure, not a claim about any Tangut code or device.",
        instructions = """
            1. Save the example as tangut_bits.py.
            2. Run it: python3 tangut_bits.py
        """,
        example = """
            characters = 6000        # about the size of the Tangut script, as commonly given
            bits = 0
            while 2 ** bits < characters:
                bits += 1
            print("characters:", characters, "bits needed:", bits)
        """,
    ),

)
