package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** A guide of 1000: how a product of two cosines becomes half a sum of cosines, the rule behind Ibn Yunus's timing procedures. */
val db104Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 104,
        kind = Kind.GUIDE,
        section = "Trigonometry",
        title = "How a product of two cosines becomes half a sum of cosines",
        originalTitle = "Zij al-Kabir al-Hakimi",
        author = "Ibn Yunus, with the identity named after Johannes Werner",
        year = 1000,
        summary = "A product of two cosines equals half the sum of the cosine of the difference of the angles and the cosine of their sum, a rule that turns a multiplication into two table look-ups, an addition and a halving.",
        details = "Johannes Werner wrote the identity down in a sixteenth-century text on conic sections, and in 1819 Delambre found that two of Ibn Yunus's altitude-based timing procedures amount to the same rule. Prosthaphaeresis used this kind of identity to multiply numbers with sine and cosine tables, and the program below checks the rule for exact cosines of 0, 60, 120 and 180 degrees.",
        rare = true,
        instructions = """
            1. Save the example as cosine_product.py.
            2. Run it: python3 cosine_product.py
        """,
        example = """
            from fractions import Fraction

            # exact cosines at 0, 60, 120 and 180 degrees
            table = {0: Fraction(1), 60: Fraction(1, 2), 120: Fraction(-1, 2), 180: Fraction(-1)}


            def cos_deg(angle):
                angle = angle % 360
                if angle > 180:
                    angle = 360 - angle
                return table[angle]


            def by_sum_rule(a, b):
                # cos a * cos b = (cos(a - b) + cos(a + b)) / 2
                return (cos_deg(a - b) + cos_deg(a + b)) / 2


            pairs = [(0, 60), (60, 60), (60, 120), (120, 180), (180, 180)]
            agree = True
            for a, b in pairs:
                direct = cos_deg(a) * cos_deg(b)
                summed = by_sum_rule(a, b)
                agree = agree and direct == summed
                print(f"cos {a} * cos {b} = {direct}, by the sum rule {summed}")
            print("all pairs agree:", agree)
        """,
    ),

)
