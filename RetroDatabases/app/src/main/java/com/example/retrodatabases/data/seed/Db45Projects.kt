package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1590 to 1600: a lecture rota, a school week, shorthand strokes and mansab totals. */
val db45Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 45,
        kind = Kind.PROJECT,
        section = "Lectures",
        title = "Gresham's lecture rota for seven professors",
        originalTitle = "Gresham College",
        author = "Sir Thomas Gresham",
        year = 1597,
        summary = "A rota that gives seven subjects one weekday each over five days, and a short program prints the rota for the seven chairs of the college.",
        details = "The subjects are the seven chairs named in Gresham's plan, and the rota is an illustration rather than the college's own timetable. The rota begins again after Friday, so the program puts the sixth and seventh subjects on Monday and Tuesday.",
        instructions = """
            1. Save the example as lecture_rota.py.
            2. Run it: python3 lecture_rota.py
        """,
        example = """
            SUBJECTS = ["divinity", "astronomy", "geometry", "music", "law", "physic", "rhetoric"]
            DAYS = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"]

            for index, subject in enumerate(SUBJECTS):
                print(f"{subject}: {DAYS[index % len(DAYS)]}")
            print("lectures in a week:", len(SUBJECTS))
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.PROJECT,
        section = "Schools",
        title = "A Jesuit school week: hours by subject",
        originalTitle = "Ratio atque institutio studiorum Societatis Iesu",
        author = "Jesuit Order",
        year = 1599,
        summary = "A week of five school days and five subjects is summed by hours, and a short program prints each subject's weekly total and the subject with the most hours.",
        details = "The hours are illustrative and do not come from the Ratio's own timetable, which set out subjects and methods rather than a fixed table. The program adds each subject's hours across the week, totals the whole week, and names the subject with the most hours.",
        instructions = """
            1. Save the example as school_week.py.
            2. Run it: python3 school_week.py
        """,
        example = """
            timetable = {
                "Latin": [2, 2, 2, 2, 2],
                "Greek": [1, 1, 1, 1, 1],
                "mathematics": [1, 1, 0, 1, 1],
                "rhetoric": [1, 1, 1, 1, 0],
                "religion": [1, 0, 1, 0, 1],
            }
            total = 0
            for subject, hours in timetable.items():
                print(f"{subject}: {sum(hours)} hours a week")
                total += sum(hours)
            print("total hours a week:", total)
            print("most hours:", max(timetable, key=lambda s: sum(timetable[s])))
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.PROJECT,
        section = "Shorthand",
        title = "Strokes for a shorthand sentence",
        originalTitle = "The Writing Schoolemaster",
        author = "Peter Bales",
        year = 1590,
        summary = "Counting the strokes each letter takes gives the cost of writing a word or a sentence, and a short program does this for two words and a sentence.",
        details = "The stroke counts are a toy table made for the example and are not Bales's signs. The program sums the strokes for each word and then for the sentence, so the sentence total is the sum of the two word totals.",
        instructions = """
            1. Save the example as shorthand_strokes.py.
            2. Run it: python3 shorthand_strokes.py
        """,
        example = """
            STROKES = {"s": 1, "h": 2, "o": 1, "r": 2, "t": 1, "a": 1, "n": 2, "d": 2}

            def word_strokes(word):
                return sum(STROKES[letter] for letter in word)

            words = ["short", "hand"]
            for word in words:
                print(f"{word}: {word_strokes(word)} strokes")
            print("sentence:", sum(word_strokes(word) for word in words), "strokes")
        """,
    ),

    entry(
        databaseId = 45,
        kind = Kind.PROJECT,
        section = "Administration",
        title = "Mansab ranks summed for three commanders",
        originalTitle = "Ain-i-Akbari",
        author = "Abul Fazl ibn Mubarak",
        year = 1598,
        summary = "Each commander's men are the horse and the foot added together, and a short program prints the totals for three commanders and the largest.",
        details = "The horse and foot counts are illustrative and do not come from the Ain's ranks, although the Ain did record horse and foot for its mansabs. The program adds the counts for each command, sums the three commands, and names the largest.",
        instructions = """
            1. Save the example as mansab_totals.py.
            2. Run it: python3 mansab_totals.py
        """,
        example = """
            COMMANDERS = {"first": (500, 300), "second": (200, 150), "third": (900, 450)}
            totals = {name: horse + foot for name, (horse, foot) in COMMANDERS.items()}
            for name, total in totals.items():
                print(f"{name} command: {total} men")
            print("all three:", sum(totals.values()), "men")
            print("largest:", max(totals, key=totals.get))
        """,
    ),

)
