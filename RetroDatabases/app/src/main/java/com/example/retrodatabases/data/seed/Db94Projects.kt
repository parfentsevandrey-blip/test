package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1101: the six orders of the Mishnah and their tractate counts, looked up and totalled in code. */
val db94Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 94,
        kind = Kind.PROJECT,
        section = "Talmud",
        title = "The six orders of the Mishnah, with their tractate counts totalled",
        originalTitle = "Shishah sedarim",
        author = "Judah ha-Nasi and the Tannaim",
        year = 1101,
        summary = "The Mishnah is arranged in six orders with 63 tractates in all, and a short program lists the orders, looks up three tractates and totals the count.",
        details = "The Arukh of 1101 explains the words of the Talmud, and the Talmud comments on the Mishnah, so the order counts frame that work. The counts used are the usual ones: 11 tractates in Seeds, 12 in Festivals, 7 in Women, 10 in Damages, 11 in Holy Things and 12 in Purities.",
        instructions = """
            1. Save the example as mishnah_orders.py.
            2. Run it: python3 mishnah_orders.py
        """,
        example = """
            ORDERS = [
                ("Seeds", 11),
                ("Festivals", 12),
                ("Women", 7),
                ("Damages", 10),
                ("Holy Things", 11),
                ("Purities", 12),
            ]
            FIND = {"Berakhot": "Seeds", "Sanhedrin": "Damages", "Niddah": "Purities"}
            for name, count in ORDERS:
                print(name, count)
            for tractate, order in FIND.items():
                print(tractate, "is in", order)
            print("orders:", len(ORDERS))
            print("tractates:", sum(count for _, count in ORDERS))
        """,
    ),

)
