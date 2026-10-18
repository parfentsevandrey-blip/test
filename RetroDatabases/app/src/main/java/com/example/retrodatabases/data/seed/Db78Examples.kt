package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1260 to 1270: the qibla bearing from Maragha, where Hulagu's observatory was in use by 1262, computed from coordinates in code. */
val db78Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 78,
        kind = Kind.EXAMPLE,
        section = "Orientation",
        title = "The qibla bearing from Maragha to Mecca, computed from coordinates",
        originalTitle = "Qibla",
        author = "Islamic astronomers of the period",
        year = 1262,
        summary = "A short program computes the great-circle bearing from Maragha, where Hulagu's observatory was in use by 1262, to Mecca, the direction of prayer, from the coordinates of the two places.",
        details = "Astronomers of the Islamic world worked out the qibla, the direction of Mecca for prayer, with spherical trigonometry from geographic coordinates. The coordinates in the example are approximate, and the bearing is rounded to whole degrees, clockwise from north.",
        instructions = """
            1. Save the example as qibla.py.
            2. Run it: python3 qibla.py
        """,
        example = """
            import math

            def bearing(lat1, lon1, lat2, lon2):
                # Initial great-circle bearing from point 1 to point 2, in degrees clockwise from north.
                p1, p2 = math.radians(lat1), math.radians(lat2)
                dl = math.radians(lon2 - lon1)
                y = math.sin(dl) * math.cos(p2)
                x = math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl)
                return math.degrees(math.atan2(y, x)) % 360

            MECCA = (21.42, 39.83)     # approximate coordinates of Mecca
            MARAGHA = (37.39, 46.24)   # approximate coordinates of Maragha
            print(f"qibla from Maragha: {bearing(MARAGHA[0], MARAGHA[1], MECCA[0], MECCA[1]):.0f} degrees")
        """,
    ),

)
