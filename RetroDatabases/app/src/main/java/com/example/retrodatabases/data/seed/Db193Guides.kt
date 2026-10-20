package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 110 to 120: how the gates and streets of a square Roman camp are named, as the treatise on camps lays them out, placed at 117. */
val db193Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 193,
        kind = Kind.GUIDE,
        section = "Camps",
        title = "How the streets of a Roman marching camp are named from its gates",
        originalTitle = "De munitionibus castrorum",
        author = "Hyginus, attributed",
        year = 117,
        summary = "Hyginus's short treatise on fortifying camps names the gates and streets of a square Roman camp, and a short program lists the gates and the two named streets.",
        details = "The date of the treatise is uncertain, and the sources do not date this layout; 117 is the year Hadrian came to power, the anchor for the placed year, not the year the treatise was written. The example prints each gate with its place and each street with its route, and it counts the gates and the streets it names.",
        instructions = """
            1. Save the example as camp_streets.py.
            2. Run it: python3 camp_streets.py
        """,
        example = """
            GATES = {
                "porta praetoria": "front gate, facing the enemy",
                "porta decumana": "rear gate, behind the praetorium",
                "porta principalis dextra": "right side gate",
                "porta principalis sinistra": "left side gate",
            }
            STREETS = {
                "via praetoria": "runs from the praetorium to the front gate",
                "via principalis": "runs across the camp between the two side gates",
            }
            for gate, place in GATES.items():
                print(gate, "is the", place)
            for street, route in STREETS.items():
                print(street, route)
            print("gates:", len(GATES), "streets named:", len(STREETS))
        """,
        rare = true,
    ),

)
