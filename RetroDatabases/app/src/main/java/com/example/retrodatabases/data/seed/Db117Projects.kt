package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 870 to 880: the spans of the Zanj revolt and Huang Chao's rebellion, with the gaps between their start and end years. */
val db117Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 117,
        kind = Kind.PROJECT,
        section = "Rebellions",
        title = "The spans of the Zanj revolt and Huang Chao's rebellion, and the gaps between them",
        originalTitle = "Zanj revolt and Huang Chao zhi luan",
        author = "Ali ibn Muhammad and Huang Chao",
        year = 874,
        summary = "The Zanj revolt in Iraq ran from 869 to 883 and Huang Chao's rebellion in China from 874 to 884, and a short program adds their spans and compares their start and end years.",
        details = "Both dates are the usual ones, and the program prints each span, their total, and the gaps between the two start years and the two end years. The Zanj revolt was led by Ali ibn Muhammad among enslaved workers on the salt flats of southern Iraq, and the two revolts ran side by side for most of the 870s.",
        instructions = """
            1. Save the example as revolts.py.
            2. Run it: python3 revolts.py
        """,
        example = """
            REVOLTS = [
                ("Zanj revolt in Iraq", 869, 883),
                ("Huang Chao's rebellion in China", 874, 884),
            ]
            for name, start, end in REVOLTS:
                print(f"{name}: {end - start} years")
            print("together:", sum(end - start for _, start, end in REVOLTS), "years")
            first, second = REVOLTS
            print("gap between the start years:", second[1] - first[1])
            print("gap between the end years:", second[2] - first[2])
        """,
    ),

)
