package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1190: a leonine rhyme, the sine table of Arabic astronomers, zakat on silver and the six feet of a Latin hexameter. */
val db85Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 85,
        kind = Kind.GUIDE,
        section = "Writing",
        title = "How a leonine verse rhymes its middle with its end",
        originalTitle = "Leonine verse",
        author = "Latin poets of the twelfth century",
        year = 1190,
        summary = "A leonine verse is a Latin line whose word before the caesura rhymes with its last word, and a short program checks the rhyme of two example lines.",
        details = "The program compares only the last two letters of the two words, which is a simplified test for these examples and not the full rules of quantity and stress. Leonine verse was popular with Latin poets of the twelfth century, and the two example lines are made up to show the test.",
        instructions = """
            1. Save the example as leonine.py.
            2. Run it: python3 leonine.py
        """,
        example = """
            LINES = [
                "Vivit in amore | quam cruciat dolore",
                "Dulcis est in ore | nec timet sub hora",
            ]
            for number, line in enumerate(LINES, start=1):
                left, right = line.split("|")
                first = left.split()[-1]
                last = right.split()[-1]
                rhyme = "yes" if first[-2:] == last[-2:] else "no"
                print(f"line {number}: {first} / {last} rhymes: {rhyme}")
        """,
    ),

    entry(
        databaseId = 85,
        kind = Kind.GUIDE,
        section = "Arabic astronomy",
        title = "How an Arabic astronomer reads a sine table between its entries",
        originalTitle = "Sine table",
        author = "Arabic astronomers of the twelfth century",
        year = 1190,
        summary = "A sine table gives values only at set angles, and a short program reads an in-between angle by linear interpolation between the two nearest entries.",
        details = "The table uses a radius of sixty units, the scale of Ptolemy's tables, with rounded values. Linear interpolation is an approximation: at 75 degrees the program prints 56, while the true value is about 58, so the answer is close but not exact.",
        instructions = """
            1. Save the example as sine_table.py.
            2. Run it: python3 sine_table.py
        """,
        example = """
            SINE = {0: 0, 30: 30, 45: 42, 60: 52, 90: 60}   # radius 60, rounded values

            def sine_between(angle):
                lower = max(a for a in SINE if a <= angle)
                upper = min(b for b in SINE if b >= angle)
                return SINE[lower] + (angle - lower) * (SINE[upper] - SINE[lower]) // (upper - lower)

            for angle in [40, 75]:
                print(f"{angle} degrees: {sine_between(angle)}")
        """,
    ),

    entry(
        databaseId = 85,
        kind = Kind.GUIDE,
        section = "Law",
        title = "How zakat on silver is reckoned once the threshold is reached",
        originalTitle = "Zakat on silver",
        author = "Islamic jurists",
        year = 1190,
        summary = "Under Islamic law silver held for a year pays zakat of two and a half per cent once it reaches two hundred dirhams, and a short program applies that rule to four sums.",
        details = "No zakat is due below the threshold of two hundred dirhams, and once the threshold is reached the rate applies to the whole sum rather than only to the amount above it. The rule was set by Islamic jurists long before 1190, and the example uses four sums to show both sides of the threshold.",
        instructions = """
            1. Save the example as zakat.py.
            2. Run it: python3 zakat.py
        """,
        example = """
            NISAB = 200   # dirhams of silver at which zakat begins
            for wealth in [150, 200, 480, 1000]:
                zakat = 0 if wealth < NISAB else wealth * 25 // 1000
                print(f"wealth {wealth} dirhams: zakat {zakat} dirhams")
        """,
    ),

    entry(
        databaseId = 85,
        kind = Kind.GUIDE,
        section = "Latin verse",
        title = "How the six feet of a Latin hexameter are told apart",
        originalTitle = "Hexameter",
        author = "Latin grammarians and poets",
        year = 1190,
        summary = "A Latin hexameter has six feet, each a dactyl or a spondee, and a short program names the feet of a line given as long and short syllables.",
        details = "A dactyl is a long syllable followed by two short ones, and a spondee is two long syllables. The program reads a line of seventeen syllables, long and short, and names each foot in turn, so it does not handle the other forms the sixth foot can take.",
        instructions = """
            1. Save the example as hexameter.py.
            2. Run it: python3 hexameter.py
        """,
        example = """
            LINE = "LSSLSSLSSLSSLSSLL"   # L long, S short, one letter per syllable
            feet = []
            pos = 0
            while pos < len(LINE):
                if LINE[pos:pos + 3] == "LSS":
                    feet.append("dactyl")
                    pos += 3
                elif LINE[pos:pos + 2] == "LL":
                    feet.append("spondee")
                    pos += 2
                else:
                    feet.append("not a foot")
                    break
            for number, foot in enumerate(feet, start=1):
                print(f"foot {number}: {foot}")
            print(f"feet: {feet.count('dactyl')} dactyls and {feet.count('spondee')} spondee")
        """,
    ),

)
