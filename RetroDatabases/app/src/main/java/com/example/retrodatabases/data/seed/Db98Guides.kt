package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1060: a method for tracing a juan of the New History of the Tang to its part of the work. */
val db98Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 98,
        kind = Kind.GUIDE,
        section = "Histories",
        title = "How a juan of the New History of the Tang is traced to its part of the work",
        originalTitle = "Xin Tangshu",
        author = "Ouyang Xiu and Song Qi",
        year = 1060,
        summary = "The 225 juan of the New History of the Tang fall into annals, treatises, tables and biographies in that order, and a short program finds the part that holds a given juan.",
        details = "The annals take juan 1 to 10, the treatises 11 to 60, the tables 61 to 75 and the biographies 76 to 225. The year 1060 is the completion date of this four-part plan, and the example checks the boundaries with a few juan numbers.",
        instructions = """
            1. Save the example as tang_parts.py.
            2. Run it: python3 tang_parts.py
        """,
        example = """
            PARTS = [("annals", 10), ("treatises", 50), ("tables", 15), ("biographies", 150)]

            def part_of(juan):
                first = 1
                for name, count in PARTS:
                    last = first + count - 1
                    if first <= juan <= last:
                        return name
                    first = last + 1
                return "outside the work"

            for juan in [1, 10, 11, 60, 61, 75, 76, 225]:
                print(juan, part_of(juan))
            print("total juan:", sum(count for _, count in PARTS))
        """,
        rare = true,
    ),

)
