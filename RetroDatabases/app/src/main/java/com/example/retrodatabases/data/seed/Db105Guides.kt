package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A guide of 994: how the sun's noon heights at the two solstices give the tilt of the ecliptic. */
val db105Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 105,
        kind = Kind.GUIDE,
        section = "Astronomy",
        title = "How the sun's noon heights at the two solstices give the tilt of the ecliptic",
        originalTitle = "How the sun's noon heights at the two solstices give the tilt of the ecliptic",
        author = "Abu Mahmud al-Khujandi",
        year = 994,
        summary = "Half the difference between the sun's noon heights at the two solstices gives the tilt of the ecliptic, the meridian method of the kind al-Khujandi used at Rayy.",
        details = "At the June solstice the noon height is 90 degrees minus the latitude plus the tilt, and at the December solstice it is 90 minus the latitude minus the tilt, so half the difference is the tilt and the latitude cancels out. The program uses the latitude of Rayy and the tilt of 23 degrees 32 minutes 19 seconds to work out the two heights, then reverses the method to recover the tilt.",
        rare = true,
        instructions = """
            1. Save the example as tilt_from_heights.py.
            2. Run it: python3 tilt_from_heights.py
        """,
        example = """
            # Tilt of the ecliptic from the sun's noon heights at the two solstices.
            # The heights are worked out from the Rayy latitude and tilt, then the method is reversed.
            phi = 35 + 34 / 60 + 38.45 / 3600      # latitude of Rayy
            eps = 23 + 32 / 60 + 19 / 3600         # tilt of the ecliptic

            summer = 90 - phi + eps                # noon height at the June solstice
            winter = 90 - phi - eps                # noon height at the December solstice

            tilt = (summer - winter) / 2           # the method: half the difference
            print(f"summer noon height {summer:.4f} degrees")
            print(f"winter noon height {winter:.4f} degrees")
            print(f"tilt from the two heights {tilt:.4f} degrees")
            print("matches the tilt used:", abs(tilt - eps) < 1e-9)
        """,
    ),

)
