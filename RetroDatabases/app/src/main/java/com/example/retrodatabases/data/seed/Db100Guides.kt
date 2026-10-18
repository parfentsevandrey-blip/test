package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1041: how a page of baked clay type is set in an iron frame, printed and released for the next page. */
val db100Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 100,
        kind = Kind.GUIDE,
        section = "Printing",
        title = "How a page of clay type is set in a frame, printed and released for the next page",
        originalTitle = "Huozi yinshua fa",
        author = "Bi Sheng, as described by Shen Kuo",
        year = 1041,
        summary = "The method sets baked clay types in an iron frame on a resin and wax plate, prints the page, then warms the frame so the types can be lifted out and used again.",
        details = "The method runs in these steps: carve each character on clay, harden the pieces by firing, set them in the frame over the plate, smooth the surface and print. The program keeps a stock of types, sets two pages from it and returns the types after each page, so the same stock serves both pages.",
        rare = true,
        instructions = """
            1. Save the example as clay_type.py.
            2. Run it: python3 clay_type.py
        """,
        example = """
            # a stock of baked clay types, by character
            stock = {"A": 3, "B": 1, "C": 1}

            for page in ["ABACA", "CAB"]:
                frame = []
                for ch in page:
                    if stock[ch] == 0:
                        raise SystemExit("no type left for " + ch)
                    stock[ch] -= 1
                    frame.append(ch)
                print("page", "".join(frame), "printed, types still on hand:", sum(stock.values()))
                # the frame is warmed and the types are lifted out for the next page
                for ch in frame:
                    stock[ch] += 1
            print("types on hand after release:", sum(stock.values()))
        """,
    ),

)
