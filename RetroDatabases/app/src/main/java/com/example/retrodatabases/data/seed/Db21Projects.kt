package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1830 to 1840: a time signal countdown, frames of a spinning disc, an index table for a prime and a body-build ratio. */
val db21Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 21,
        kind = Kind.PROJECT,
        section = "Time signals",
        title = "Greenwich time ball: seconds to the one o'clock drop",
        originalTitle = "Time ball at the Royal Observatory, Greenwich (1833)",
        author = "Royal Observatory, Greenwich",
        year = 1833,
        summary = "A time ball on a mast at the observatory dropped at one o'clock each day, so that ships could check their chronometers, and the seconds left to the drop follow from the clock.",
        details = "The Greenwich time ball was in use by 1833, and it dropped at one in the afternoon so that ships on the Thames could check their chronometers. The project counts the seconds from a clock reading to the next drop, and it moves to the next day when the drop has already passed. The record uses the 1833 date of the ball.",
        instructions = """
            1. Save the project as greenwich_countdown.py.
            2. Run it: python3 greenwich_countdown.py
        """,
        example = """
            def seconds_to_drop(h, m, s):
                now = h * 3600 + m * 60 + s
                drop = 13 * 3600    # one o'clock in the afternoon, in seconds after midnight
                if now <= drop:
                    return drop - now
                return 24 * 3600 - now + drop

            for clock in ((10, 30, 0), (13, 0, 0), (13, 0, 5), (23, 59, 59)):
                left = seconds_to_drop(*clock)
                h, rem = divmod(left, 3600)
                m, s = divmod(rem, 60)
                print(f"{clock[0]:02d}:{clock[1]:02d}:{clock[2]:02d} -> {left} s left, {h} h {m} m {s} s")
        """,
    ),

    entry(
        databaseId = 21,
        kind = Kind.PROJECT,
        section = "Optical toys",
        title = "Plateau's phenakistiscope frames",
        originalTitle = "Plateau's description of the phenakistiscope (1832)",
        author = "Joseph Plateau",
        year = 1832,
        summary = "A spinning disc with slits, viewed in a mirror, that shows a row of drawings in turn so that they look like one moving picture, here shown as text frames of a dot in motion.",
        details = "Plateau described the phenakistiscope in 1832, and Stampfer announced a similar disc in 1833, so the priority is disputed. The project prints the frames of a dot that moves two places per frame, as the drawings on the disc would show a motion. The record uses the 1832 date of Plateau's description.",
        instructions = """
            1. Save the project as phenakistiscope_frames.py.
            2. Run it: python3 phenakistiscope_frames.py
        """,
        example = """
            frames = 4
            width = 8
            for f in range(frames):
                row = ["."] * width
                row[f * 2] = "o"
                print(f"frame {f}: " + "".join(row))
        """,
    ),

    entry(
        databaseId = 21,
        kind = Kind.PROJECT,
        section = "Index tables",
        title = "Jacobi's index table for the prime 11",
        originalTitle = "Index tables in the Canon arithmeticus (1839)",
        author = "Carl Gustav Jacob Jacobi",
        year = 1839,
        summary = "A table that gives, for each residue modulo a prime, the power of a primitive root that produces it, so that multiplying residues becomes adding their indices.",
        details = "Jacobi's Canon arithmeticus of 1839 printed such index tables for primes and prime powers below 1000. The project builds the table for the prime 11 with the primitive root 2 and checks one product through the indices. The record uses the 1839 date of the tables.",
        instructions = """
            1. Save the project as jacobi_index_mod11.py.
            2. Run it: python3 jacobi_index_mod11.py
        """,
        example = """
            p, g = 11, 2
            index = {}
            x = 1
            for k in range(p - 1):
                index[x] = k
                x = x * g % p
            print("index table mod 11 with primitive root 2:", " ".join(f"{a}:{index[a]}" for a in range(1, p)))
            a, b = 3, 7
            product = a * b % p
            via_index = (index[a] + index[b]) % (p - 1)
            back = [k for k, v in index.items() if v == via_index][0]
            print(f"{a} times {b} is {product} mod {p}, and the indices add to {via_index}, which gives {back}")
        """,
    ),

    entry(
        databaseId = 21,
        kind = Kind.PROJECT,
        section = "Human measures",
        title = "Quetelet's index of weight to height",
        originalTitle = "Recherches sur le poids de l'homme aux differents ages (1832)",
        author = "Adolphe Quetelet",
        year = 1832,
        summary = "A ratio that divides weight by the square of height, a single figure for the build of a grown person that was used to compare groups of people.",
        details = "Quetelet studied the weight of men at different ages in 1832, and the ratio of weight to the square of height became known as the Quetelet index. The project computes it for two people, with weights and heights chosen for the example. The record uses the 1832 date of the study.",
        instructions = """
            1. Save the project as quetelet_index.py.
            2. Run it: python3 quetelet_index.py
        """,
        example = """
            people = [("first", 70.0, 1.75), ("second", 60.0, 1.60)]
            for name, kg, m in people:
                index = kg / (m * m)
                print(f"{name} person: weight {kg} kg, height {m} m, index {index:.2f}")
        """,
    ),
)
