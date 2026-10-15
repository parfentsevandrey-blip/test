package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1620 to 1630: Snell's refraction, Bacon's tables, Harvey's half-hour count, proportional parts in a log table and the characteristic of a logarithm. */
val db42Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 42,
        kind = Kind.GUIDE,
        section = "Optics",
        title = "How Snell's law bends a ray at a surface",
        originalTitle = "Snell's manuscript notes on refraction",
        author = "Willebrord Snellius",
        year = 1621,
        summary = "Snell's law says that the sines of the two angles to the normal are in the ratio of the refractive indices, and the guide works out the bend of a ray in three cases.",
        details = "Snellius found the law around 1621, but it was not printed until Huygens published it in 1703, and Descartes printed a form of it in 1637. The example computes three bends of a ray, from air into glass, from glass into air and from air into a denser medium at grazing incidence.",
        instructions = """
            1. Save the example as snell_law.py.
            2. Run it: python3 snell_law.py
        """,
        example = """
            import math

            def refract(n1, n2, angle_deg):
                # Snell's law: n1 sin(angle in) = n2 sin(angle out); the angle out is found with arcsin.
                s = n1 * math.sin(math.radians(angle_deg)) / n2
                return math.degrees(math.asin(s))

            for n1, n2, angle in ((1.0, 1.5, 30), (1.5, 1.0, 30), (1.0, 2.0, 90)):
                print(f"{n1} into {n2} at {angle} degrees: bends to {refract(n1, n2, angle):.2f}")
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.GUIDE,
        section = "Method",
        title = "How Bacon's tables sort the instances of a phenomenon",
        originalTitle = "Novum Organum scientiarum",
        author = "Francis Bacon",
        year = 1620,
        summary = "Bacon's tables of presence and absence list the cases where a feature occurs and where it does not, and a short program sorts instances in the same way.",
        details = "Bacon's Novum Organum of 1620 sets out tables of presence, of absence and of degrees for testing a suspected cause. The example sorts a small list of instances into a table of presence and a table of absence, then keeps only the features that appear where the phenomenon is present.",
        instructions = """
            1. Save the example as bacon_tables.py.
            2. Run it: python3 bacon_tables.py
        """,
        example = """
            INSTANCES = [
                ("fire", True, {"flame", "light"}),
                ("sun", True, {"light", "motion"}),
                ("rubbing", True, {"motion"}),
                ("ice", False, {"light"}),
                ("moon", False, {"light", "motion"}),
            ]

            presence = [name for name, hot, _ in INSTANCES if hot]
            absence = [name for name, hot, _ in INSTANCES if not hot]
            present_features = set().union(*(f for _, hot, f in INSTANCES if hot))
            absent_features = set().union(*(f for _, hot, f in INSTANCES if not hot))
            print("table of presence:", ", ".join(presence))
            print("table of absence:", ", ".join(absence))
            print("features only where heat is present:", ", ".join(sorted(present_features - absent_features)))
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.GUIDE,
        section = "Physiology",
        title = "How Harvey's half-hour count compares the heart's output with the blood",
        originalTitle = "Exercitatio anatomica de motu cordis et sanguinis in animalibus",
        author = "William Harvey",
        year = 1628,
        summary = "Harvey multiplies the beats in half an hour by the volume of each beat, and compares the total with the blood the body holds, which shows that the blood must circulate.",
        details = "Harvey's De motu cordis of 1628 makes the half-hour case. The example uses illustrative figures for the beat rate, the volume per beat and the body's blood, not Harvey's own numbers, and shows the arithmetic that makes the comparison.",
        instructions = """
            1. Save the example as harvey_half_hour.py.
            2. Run it: python3 harvey_half_hour.py
        """,
        example = """
            BEATS_PER_MINUTE = 60     # illustrative beat rate
            OUNCES_PER_BEAT = 2       # illustrative volume per beat
            BODY_BLOOD_POUNDS = 10    # illustrative blood volume of the body
            minutes = 30

            beats = BEATS_PER_MINUTE * minutes
            ounces = beats * OUNCES_PER_BEAT
            pounds = ounces / 16
            print("beats in half an hour:", beats)
            print("ounces pumped:", ounces)
            print(f"pounds pumped: {pounds:.1f}")
            print(f"times the body's blood: {pounds / BODY_BLOOD_POUNDS:.1f}")
            print("pumps more than the body holds:", pounds > BODY_BLOOD_POUNDS)
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.GUIDE,
        section = "Logarithms",
        title = "How proportional parts read a logarithm table between entries",
        originalTitle = "Arithmetica logarithmica",
        author = "Adriaan Vlacq",
        year = 1628,
        summary = "A log table gives entries at fixed steps, and proportional parts estimate a value between two entries by a straight-line share of the difference.",
        details = "Vlacq's tables of 1628, like Briggs's before them, were read this way between their printed entries. The example builds two table entries rounded to four places, interpolates a value between them and compares the result with the exact logarithm.",
        instructions = """
            1. Save the example as proportional_parts.py.
            2. Run it: python3 proportional_parts.py
        """,
        example = """
            import math

            def table(x):
                # A printed table gives each entry rounded to four places.
                return round(math.log10(x), 4)

            lo, hi = 2.40, 2.41
            t_lo, t_hi = table(lo), table(hi)
            print(f"table {lo:.2f}: {t_lo:.4f}")
            print(f"table {hi:.2f}: {t_hi:.4f}")
            x = 2.407
            share = (x - lo) / (hi - lo)
            part = t_lo + share * (t_hi - t_lo)
            print(f"proportional part for {x}: {part:.4f}")
            print(f"exact log10 of {x} to four places: {math.log10(x):.4f}")
        """,
    ),

    entry(
        databaseId = 42,
        kind = Kind.GUIDE,
        section = "Logarithms",
        title = "How the characteristic places the decimal point of a logarithm",
        originalTitle = "Arithmetica logarithmica",
        author = "Henry Briggs",
        year = 1624,
        summary = "The whole-number part of a common logarithm, the characteristic, follows from how many digits a number has before its decimal point, and a short program computes it.",
        details = "Briggs's 1624 book gives the characteristic its own chapter. The example counts the digits of whole numbers and the leading zeros of decimals to find each characteristic, and it checks the result against the floor of the exact logarithm.",
        instructions = """
            1. Save the example as characteristic.py.
            2. Run it: python3 characteristic.py
        """,
        example = """
            import math

            def characteristic(text):
                # The characteristic is the whole-number part of the common logarithm.
                whole, _, frac = text.partition(".")
                if whole.strip("0"):
                    return len(whole.lstrip("0")) - 1
                zeros = len(frac) - len(frac.lstrip("0"))
                return -(zeros + 1)

            for text in ("2", "20", "2500", "10.5", "0.2", "0.02", "0.0005"):
                c = characteristic(text)
                ok = c == math.floor(math.log10(float(text)))
                print(f"{text}: characteristic {c}, agrees with floor of log10: {ok}")
        """,
    ),

)
