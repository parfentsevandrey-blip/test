package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1116: the flavour and nature pairs that a classified materia medica sets out, counted in code. */
val db93Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 93,
        kind = Kind.EXAMPLE,
        section = "Medicine",
        title = "The flavour and nature pairs of a classified materia medica, counted",
        originalTitle = "Wu wei and si xing",
        author = "Chinese physicians and compilers of materia medica",
        year = 1116,
        summary = "Chinese materia medica sorted each drug by five flavours and four natures, and a short program counts the pairs that the two lists allow.",
        details = "The five flavours are sour, bitter, sweet, pungent and salty, and the four natures are cold, cool, warm and hot. The program prints the first three pairs and the total, which is the product of the two list lengths, so the answer can be checked by hand.",
        instructions = """
            1. Save the example as pairs.py.
            2. Run it: python3 pairs.py
        """,
        example = """
            FLAVOURS = ["sour", "bitter", "sweet", "pungent", "salty"]
            NATURES = ["cold", "cool", "warm", "hot"]
            pairs = [(flavour, nature) for flavour in FLAVOURS for nature in NATURES]
            for flavour, nature in pairs[:3]:
                print(flavour, nature)
            print("pairs:", len(pairs))
        """,
    ),

)
