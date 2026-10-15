package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1660 to 1670: collisions by Huygens's rule, layers by Steno's principle, a hyperbola's area, a sign tree and a compound microscope. */
val db38Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 38,
        kind = Kind.GUIDE,
        section = "Mechanics",
        title = "How Huygens's rules give the speeds after a collision",
        originalTitle = "Rules of collision in the Philosophical Transactions of 1669",
        author = "Christiaan Huygens",
        year = 1669,
        summary = "Huygens's rule for a perfectly elastic collision says that the centre of gravity keeps its speed and each body's speed relative to that centre is reversed.",
        details = "Wallis, Wren and Huygens sent their answers on colliding bodies to the Royal Society in 1668, and the rules were printed in 1669. The three answers did not agree on every case, and the example applies Huygens's rule to three head-on collisions, keeping each new speed as a fraction over the total mass.",
        instructions = """
            1. Save the example as huygens_collision.py.
            2. Run it: python3 huygens_collision.py
        """,
        example = """
            def huygens_after(m1, v1, m2, v2):
                # Huygens's rule: the centre of gravity keeps its speed, and each body's speed relative to it is reversed.
                # The new speeds are returned as numerators over the total mass, so no rounding is needed.
                total = m1 + m2
                momentum = m1 * v1 + m2 * v2
                return (2 * momentum - total * v1, total), (2 * momentum - total * v2, total)

            for m1, v1, m2, v2 in ((2, 3, 1, 0), (1, 2, 1, 0), (3, 1, 1, -1)):
                (n1, d), (n2, _) = huygens_after(m1, v1, m2, v2)
                print(f"masses {m1} and {m2}, speeds {v1} and {v2}: after {n1}/{d} and {n2}/{d}")
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.GUIDE,
        section = "Geology",
        title = "How Steno's law of superposition orders the layers of a cliff",
        originalTitle = "De solido intra solidum naturaliter contento dissertationis prodromus",
        author = "Nicolas Steno",
        year = 1669,
        summary = "Steno's principle of 1669 says that in undisturbed rock each layer is older than the layer above it, so the layers of a cliff can be put in order of age.",
        details = "Steno set out the principle in his Prodromus of 1669, a preface to a work on a solid body enclosed within another solid. The example lists the layers of an invented cliff from the top down and prints them from oldest to youngest, which is the first step in reading a section.",
        instructions = """
            1. Save the example as steno_layers.py.
            2. Run it: python3 steno_layers.py
        """,
        example = """
            # Layers of an invented cliff, listed from the top down; each layer is older than the one above it.
            CLIFF = ["soil", "sandstone", "chalk", "limestone", "clay"]

            def oldest_first(layers_top_down):
                # Steno's principle of superposition: in undisturbed rock the lowest layer is the oldest.
                return list(reversed(layers_top_down))

            for age, layer in enumerate(oldest_first(CLIFF), start=1):
                print(f"{age}. {layer}")
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.GUIDE,
        section = "Quadrature",
        title = "How Gregory's hyperbola gives a logarithm",
        originalTitle = "Vera circuli et hyperbolae quadratura",
        author = "James Gregory",
        year = 1667,
        summary = "Gregory's quadrature of 1667 dealt with the area under a hyperbola, which we now know is a logarithm, and strips of known heights bracket that area between two sums.",
        details = "The book was printed at Padua in 1667, and it squares the circle and the hyperbola by means of series and sums. The example cuts the area under y = 1/x between 1 and 2 into equal strips and brackets the area between a lower and an upper sum, using exact fractions.",
        instructions = """
            1. Save the example as hyperbola_strips.py.
            2. Run it: python3 hyperbola_strips.py
        """,
        example = """
            from fractions import Fraction as F
            from math import log

            def strip_sums(strips):
                # Area under y = 1/x from x = 1 to x = 2, cut into equal strips.
                # Heights taken at the right-hand ends give a lower sum, and heights at the left-hand ends give an upper sum.
                h = F(1, strips)
                xs = [1 + i * h for i in range(strips + 1)]
                lower = sum(h / x for x in xs[1:])
                upper = sum(h / x for x in xs[:-1])
                return lower, upper

            for strips in (2, 4):
                lower, upper = strip_sums(strips)
                print(f"{strips} strips: lower {lower}, upper {upper}")
            print(f"natural log of 2 is about {log(2):.6f}")
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.GUIDE,
        section = "Universal language",
        title = "How Dalgarno's sign tree builds a sign",
        originalTitle = "Ars Signorum",
        author = "George Dalgarno",
        year = 1661,
        summary = "Dalgarno's Ars Signorum of 1661 proposed signs built from a thing's place in a classification, so each sign records a category and then a kind.",
        details = "The example builds each sign from a category letter and a kind letter, and reads a sign back to its category and kind. The table of signs is invented, and it is not Dalgarno's own scheme.",
        instructions = """
            1. Save the example as dalgarno_sign.py.
            2. Run it: python3 dalgarno_sign.py
        """,
        example = """
            CATEGORY = {"animal": "a", "plant": "p"}
            KIND = {"dog": "d", "cat": "c", "oak": "o"}

            def sign(category, kind):
                # The first letter places the thing in its category, and the second places it among the kinds.
                return CATEGORY[category] + KIND[kind]

            def read(code):
                category = next(name for name, letter in CATEGORY.items() if letter == code[0])
                kind = next(name for name, letter in KIND.items() if letter == code[1])
                return category, kind

            for category, kind in (("animal", "dog"), ("animal", "cat"), ("plant", "oak")):
                print(f"{category} {kind}: sign {sign(category, kind)}")
            print("read back po as", " ".join(read("po")))
        """,
    ),

    entry(
        databaseId = 38,
        kind = Kind.GUIDE,
        section = "Microscopy",
        title = "How a compound microscope multiplies its two magnifications",
        originalTitle = "Micrographia",
        author = "Robert Hooke",
        year = 1665,
        summary = "The total magnification of a compound microscope is the magnification of its objective times that of its eyepiece, so two modest lenses give a large total.",
        details = "Hooke's Micrographia of 1665 described observations made with a compound instrument of this kind. The example multiplies three objectives by one eyepiece, using invented magnifications, and prints the total for each pair.",
        instructions = """
            1. Save the example as microscope.py.
            2. Run it: python3 microscope.py
        """,
        example = """
            # A compound microscope: the objective magnifies the object into an image, and the eyepiece magnifies that image.
            OBJECTIVES = [4, 10, 40]   # magnifications of three objectives; invented figures
            EYEPIECE = 10              # magnification of the eyepiece; an invented figure

            for objective in OBJECTIVES:
                total = objective * EYEPIECE
                print(f"objective {objective}x and eyepiece {EYEPIECE}x: total {total}x")
        """,
    ),

)
