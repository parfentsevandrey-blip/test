package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1410 to 1420: a table of picture signs that decodes a message and marks the pictures it does not know. */
val db63Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 63,
        kind = Kind.PROJECT,
        section = "Picture signs",
        title = "Decoding a message of picture signs by a table",
        originalTitle = "Hieroglyphica",
        author = "Horapollo",
        year = 1419,
        summary = "Horapollo's book read a picture as a sign for an idea rather than a sound, and a short program decodes a message of pictures with a table of such ideas.",
        details = "The table and the message are invented for the example, and they are not Horapollo's own readings. The program prints each picture with its idea and marks any picture that is missing from the table with a question mark.",
        instructions = """
            1. Save the example as sign_table.py.
            2. Run it: python3 sign_table.py
        """,
        example = """
            TABLE = {"hawk": "god", "reed": "year", "eye": "to see", "owl": "night"}   # invented pairs
            MESSAGE = ["eye", "hawk", "reed", "fish", "owl"]

            decoded = []
            for picture in MESSAGE:
                # A picture missing from the table is marked, so that a gap in the table shows.
                decoded.append(TABLE.get(picture, "?"))

            for picture, idea in zip(MESSAGE, decoded):
                print(f"{picture} -> {idea}")
            print("pictures:", len(MESSAGE), "unknown:", decoded.count("?"))
        """,
    ),

)
