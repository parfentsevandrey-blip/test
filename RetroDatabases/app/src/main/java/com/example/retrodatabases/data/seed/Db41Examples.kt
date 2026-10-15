package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1630 to 1640: a search that Fermat's margin set, letter charges by stage, the pairs of Venus transits, a metre check, a sort count and Fermat's plane loci. */
val db41Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 41,
        kind = Kind.EXAMPLE,
        section = "Number theory",
        title = "Fermat's marginal note searched for whole-number solutions",
        originalTitle = "Observatio Domini Petri de Fermat",
        author = "Pierre de Fermat",
        year = 1637,
        summary = "Fermat wrote in the margin of his Diophantus that x^n + y^n = z^n has no whole-number solutions for n above 2, and the example searches small cases of the squares and the cubes.",
        details = "Fermat's note was written about 1637 in his copy of Bachet's edition of Diophantus, and it was printed by his son Samuel in 1670, since Fermat's own copy is lost. The example counts the Pythagorean triples with z up to 20 and checks that the cube case has none up to 50, which is only a search and not a proof.",
        instructions = """
            1. Save the example as fermat_margin.py.
            2. Run it: python3 fermat_margin.py
        """,
        example = """
            def solutions(n, bound):
                # Counts x <= y with x^n + y^n = z^n for a whole number z up to bound; z comes from a float root and is checked exactly.
                count = 0
                for x in range(1, bound + 1):
                    for y in range(x, bound + 1):
                        total = x ** n + y ** n
                        z = round(total ** (1.0 / n))
                        for candidate in (z - 1, z, z + 1):
                            if 1 <= candidate <= bound and candidate ** n == total:
                                count += 1
                return count

            print("exponent 2, z up to 20:", solutions(2, 20), "triples")
            print("exponent 3, z up to 50:", solutions(3, 50), "triples")
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.EXAMPLE,
        section = "Post",
        title = "Charging for a letter by the stages it travels",
        originalTitle = "Letter-office charges of 1635",
        author = "Thomas Witherings",
        year = 1635,
        summary = "A letter carried by the 1635 Letter Office was charged by how far it went, and the example adds up the charges for a bundle of letters under an illustrative rule of stages.",
        details = "The example uses an illustrative rule of two pence for the first stage and one penny for each stage after it, because the rates of 1635 are not reproduced here. It prints the charge for four letters and their total, which is the sum a clerk would make for a day's bundle.",
        instructions = """
            1. Save the example as letter_charges.py.
            2. Run it: python3 letter_charges.py
        """,
        example = """
            def charge(stages):
                # Illustrative rule: two pence for the first stage, and one penny for each stage after it.
                return 2 + (stages - 1)

            letters = [1, 3, 6, 10]
            for stages in letters:
                print(f"stages {stages}: {charge(stages)} pence")
            print(f"total for four letters: {sum(charge(s) for s in letters)} pence")
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.EXAMPLE,
        section = "Astronomy",
        title = "Horrocks's 1639 transit of Venus and the pairs eight years apart",
        originalTitle = "Venus in Sole visa",
        author = "Jeremiah Horrocks",
        year = 1639,
        summary = "Transits of Venus come in pairs eight years apart, and Horrocks observed the second transit of the pair of 1631 and 1639.",
        details = "Horrocks's account of the 1639 transit was printed only in 1662, and he observed it from Lancashire. The example lists the transit years from 1631 to 2012, prints the gaps between them, and shows that each pair is eight years apart while the gaps between pairs are about a century.",
        instructions = """
            1. Save the example as venus_pairs.py.
            2. Run it: python3 venus_pairs.py
        """,
        example = """
            transits = [1631, 1639, 1761, 1769, 1874, 1882, 2004, 2012]
            # Each pair of transits is eight years apart; the longer gaps between the pairs are about a century.
            gaps = [b - a for a, b in zip(transits, transits[1:])]
            pair_gaps = gaps[0::2]
            print("gaps between transits:", gaps)
            print("gaps within each pair:", pair_gaps)
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.EXAMPLE,
        section = "Verse",
        title = "A common-metre check of psalm lines, as in the Bay Psalm Book",
        originalTitle = "The Whole Booke of Psalmes Faithfully Translated into English Metre",
        author = "Richard Mather and others",
        year = 1640,
        summary = "The Bay Psalm Book of 1640 set its psalms in common metre, lines of eight and six syllables in turn, and the example checks a stanza's syllables against that pattern.",
        details = "The book was printed at Cambridge in Massachusetts on the press that Daye had set up, and it was the first book printed in British America. The check counts syllables by runs of vowels, so it is a rough counter for short English words and not a scholarly scansion.",
        instructions = """
            1. Save the example as common_metre.py.
            2. Run it: python3 common_metre.py
        """,
        example = """
            def syllables(line):
                # Counts runs of vowels in each word; this is a rough count for short English words.
                total = 0
                for word in line.lower().split():
                    groups = 0
                    previous_vowel = False
                    for ch in word:
                        vowel = ch in "aeiouy"
                        if vowel and not previous_vowel:
                            groups += 1
                        previous_vowel = vowel
                    total += max(groups, 1)
                return total

            COMMON = [8, 6, 8, 6]
            stanzas = {
                "stanza A": ["the Lord to me a shepherd is", "he leads me to the rest", "my cup runs over I am glad", "and I will dwell in God"],
                "stanza B": ["the Lord to me a shepherd is", "he leads me to the rest", "my cup runs over I am glad", "and I will dwell in God on high"],
            }
            for name, lines in stanzas.items():
                counts = [syllables(line) for line in lines]
                print(f"{name} line counts: {counts} common metre: {counts == COMMON}")
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.EXAMPLE,
        section = "Printing",
        title = "Counting the letters of a 1640 title page",
        originalTitle = "Title page of a Cambridge book",
        author = "Stephen Daye",
        year = 1640,
        summary = "A printer had to keep a stock of each letter in the case, so counting the letters on a title page shows how many sorts of each kind a small press had to supply.",
        details = "The example counts the letters of an invented title line and tallies the e and n sorts, which a press of the period had to hold in quantity. The line is illustrative and does not reproduce a real title page.",
        instructions = """
            1. Save the example as title_sorts.py.
            2. Run it: python3 title_sorts.py
        """,
        example = """
            text = "cambridge in new england 1640"
            letters = [ch for ch in text if ch.isalpha()]
            print("letters:", len(letters))
            print("e sorts:", letters.count("e"))
            print("n sorts:", letters.count("n"))
        """,
    ),

    entry(
        databaseId = 41,
        kind = Kind.EXAMPLE,
        section = "Curves",
        title = "Fermat's plane loci sorted by their equations",
        originalTitle = "Ad locos planos et solidos isagoge",
        author = "Pierre de Fermat",
        year = 1636,
        summary = "Fermat's plane loci of about 1636 are the curves given by equations of the first and second degree, and the example sorts four of them into lines, ellipses, parabolas and hyperbolas.",
        details = "Fermat's Isagoge on plane and solid loci was written around 1636 and printed only in 1679, and it sorts equations by their degree. The example applies the modern discriminant test to the quadratic part of four equations, which is a later shortcut for the same sorting, and prints the kind of each curve.",
        instructions = """
            1. Save the example as plane_loci.py.
            2. Run it: python3 plane_loci.py
        """,
        example = """
            def kind(a, b, c):
                # a, b and c are the coefficients of x squared, x y and y squared; all zero means the equation is of the first degree.
                if a == 0 and b == 0 and c == 0:
                    return "line"
                d = b * b - 4 * a * c
                return "ellipse" if d < 0 else "parabola" if d == 0 else "hyperbola"

            loci = [
                ("x^2 + y^2 = 1", 1, 0, 1),
                ("x y = 1", 0, 1, 0),
                ("y^2 = x", 0, 0, 1),
                ("x + y = 1", 0, 0, 0),
            ]
            for name, a, b, c in loci:
                print(f"{name}: {kind(a, b, c)}")
        """,
    ),

)
