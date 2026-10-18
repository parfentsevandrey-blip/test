package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1260 to 1270: Archimedes' rule for a floating block and Peregrinus's meridians on a spherical lodestone, each written out as a short program. */
val db78Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 78,
        kind = Kind.GUIDE,
        section = "Floating",
        title = "How Archimedes' rule gives the part of a floating block below water",
        originalTitle = "Floating bodies",
        author = "Archimedes, in William of Moerbeke's Latin",
        year = 1269,
        summary = "A floating block sinks until it displaces its own weight of water, so the share of its height below the surface equals its density, and a short program prints that share for four blocks.",
        details = "The rule is Archimedes' own, and William of Moerbeke's Latin version of 1269 made it available to medieval readers. The densities in the example are illustrative values with water taken as 1.0, and a block denser than water sinks entirely.",
        instructions = """
            1. Save the example as floating_block.py.
            2. Run it: python3 floating_block.py
        """,
        example = """
            densities = [0.25, 0.6, 0.8, 1.5]   # illustrative densities, with water taken as 1.0
            for density in densities:
                if density < 1.0:
                    print(f"density {density}: {round(density * 100)} percent of the block below water")
                else:
                    print(f"density {density}: the block sinks")
        """,
    ),

    entry(
        databaseId = 78,
        kind = Kind.GUIDE,
        section = "Magnets",
        title = "How Peregrinus marks meridians on a spherical lodestone",
        originalTitle = "Epistola de magnete",
        author = "Peter Peregrinus of Maricourt",
        year = 1269,
        summary = "Peregrinus described a spherical lodestone marked with lines from pole to pole, and a short program prints points on two of those lines at three latitudes.",
        details = "The lines run from one pole to the other like the meridians of a globe, and the example computes their points on a unit sphere so that the output can be checked by hand. Positions are printed to three decimal places, and latitude 0 is the equator.",
        instructions = """
            1. Save the example as meridians.py.
            2. Run it: python3 meridians.py
        """,
        example = """
            import math

            for longitude in (0, 90):
                for latitude in (0, 30, 60):
                    lat = math.radians(latitude)
                    lon = math.radians(longitude)
                    x = math.cos(lat) * math.cos(lon)
                    y = math.cos(lat) * math.sin(lon)
                    z = math.sin(lat)
                    print(f"longitude {longitude} latitude {latitude}: {x:.3f} {y:.3f} {z:.3f}")
        """,
        rare = true,
    ),

)
