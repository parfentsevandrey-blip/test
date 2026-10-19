package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 850 to 860: how a float valve from the Book of Ingenious Devices keeps a cistern near a set height, written as a program. */
val db119Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 119,
        kind = Kind.GUIDE,
        section = "Control",
        title = "How a float valve from the Book of Ingenious Devices keeps a cistern near a set height",
        originalTitle = "How a float valve keeps a cistern near a set height",
        author = "The Banu Musa brothers",
        year = 850,
        summary = "A float valve closes a cistern's inlet when the water reaches a set height, and a short program runs that on-off control and prints the level step by step.",
        details = "The Book of Ingenious Devices describes float valves among its valve types, and this example models the principle rather than copying the brothers' construction. Because the inlet is simply open or shut, the level swings above and below the set height, and the program prints eight steps and the highest level reached.",
        instructions = """
            1. Save the example as float_valve.py.
            2. Run it: python3 float_valve.py
        """,
        example = """
            SET_HEIGHT = 10     # the float shuts the inlet at this level
            INFLOW = 5          # added per step while the inlet is open
            OUTFLOW = 2         # drawn off per step
            level = 0
            highest = 0
            for step in range(1, 9):
                valve_open = level < SET_HEIGHT
                if valve_open:
                    level += INFLOW
                level = max(0, level - OUTFLOW)
                highest = max(highest, level)
                state = "open" if valve_open else "closed"
                print("step", step, "valve", state, "level", level)
            print("highest level:", highest)
        """,
    ),

)
