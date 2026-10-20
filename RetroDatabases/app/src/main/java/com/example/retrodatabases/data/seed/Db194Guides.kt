package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guide of 100 to 110: Menelaus's rule for a line across a triangle, checked with fractions in code. */
val db194Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 194,
        kind = Kind.GUIDE,
        section = "Geometry",
        title = "Menelaus's rule for a line across a triangle, checked with three ratios",
        originalTitle = "Sphaerica",
        author = "Menelaus of Alexandria",
        year = 100,
        summary = "Menelaus's rule says that a straight line cutting the three sides of a triangle makes three ratios of pieces whose product is one, and a short program checks it with fractions.",
        details = "The Sphaerica is usually dated to about 100, though accounts differ and one gives about 110, and in Book III it states the rule for spherical triangles. The example uses the plane form of the rule for straight lines, which predates Menelaus, so the code checks the arithmetic rather than his proof.",
        instructions = """
            1. Save the example as menelaus.py.
            2. Run it: python3 menelaus.py
        """,
        example = """
            from fractions import Fraction

            # D lies on BC, E on CA and F on AB, and the line through D, E and F cuts the three sides.
            # Menelaus's rule in the plane form: (AF/FB) * (BD/DC) * (CE/EA) = 1.
            af_fb = Fraction(3, 2)
            bd_dc = Fraction(4, 3)
            ce_ea = 1 / (af_fb * bd_dc)
            print("CE/EA =", ce_ea)
            print("product =", af_fb * bd_dc * ce_ea)
        """,
        rare = true,
    ),

)
