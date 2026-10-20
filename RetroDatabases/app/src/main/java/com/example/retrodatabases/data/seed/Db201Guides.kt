package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 30 to 40: a bridge of boats counted in ships across a gap, written out as code. */
val db201Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 201,
        kind = Kind.GUIDE,
        section = "Engineering",
        title = "How many ships a bridge of boats needs across a gap, counted in code",
        originalTitle = "Bridge of boats",
        author = "No single author",
        year = 39,
        summary = "A bridge of boats needs enough ships to cover the gap when they lie end to end, and a short program divides the gap by the length of one ship and rounds up.",
        details = "The gap is the 3,600 paces that Suetonius gives for the crossing at Baiae, but the ship lengths are illustrative and the example does not claim how many ships Caligula used. The division rounds up, so a gap that does not divide evenly still needs one more ship.",
        instructions = """
            1. Save the example as bridge_ships.py.
            2. Run it: python3 bridge_ships.py
        """,
        example = """
            GAP = 3600   # paces, the crossing given for the bridge at Baiae
            for ship_length in (30, 35):   # paces, illustrative lengths of one ship
                ships = -(-GAP // ship_length)   # division rounded up
                print(f"ships of {ship_length} paces: {ships}")
            print("gap in paces:", GAP)
        """,
    ),

)
