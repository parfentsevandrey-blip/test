package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1580 to 1590: a bookwheel simulation, a striped stocking pattern, a parish tally of baptisms and a sorted word list. */
val db46Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 46,
        kind = Kind.PROJECT,
        section = "Books",
        title = "A bookwheel that brings shelves to the reader",
        originalTitle = "Le diverse et artificiose machine",
        author = "Agostino Ramelli",
        year = 1588,
        summary = "A bookwheel turns a ring of reading desks past the reader, and a short program simulates the wheel and prints which desk stands at the reader's place at each step.",
        details = "Ramelli's book of 1588 shows a revolving wheel of desks, and the project turns a ring of eight desks in fixed steps. The desk count and the step size are illustrative and are not taken from Ramelli's plates.",
        instructions = """
            1. Save the example as bookwheel.py.
            2. Run it: python3 bookwheel.py
        """,
        example = """
            DESKS = 8
            STEP = 3

            position = 0
            visited = set()
            for step in range(4):
                print(f"step {step}: desk {position + 1} at the reader")
                visited.add(position)
                position = (position + STEP) % DESKS
            print("distinct desks in four steps:", len(visited))
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.PROJECT,
        section = "Knitting",
        title = "A striped stocking pattern as rows of loops",
        originalTitle = "The stocking frame",
        author = "William Lee",
        year = 1589,
        summary = "A stocking pattern can be written as rows of loops, and a short program prints four courses in two colours and counts the loops of each colour.",
        details = "Lee's frame knitted courses of loops, and the pattern here is an illustrative program, not a design from the frame. Each course has eight loops, and the program prints the courses and then the loops of each colour.",
        instructions = """
            1. Save the example as stripes.py.
            2. Run it: python3 stripes.py
        """,
        example = """
            NEEDLES = 8
            COURSES = 4

            total = {"A": 0, "B": 0}
            for course in range(1, COURSES + 1):
                colour = "A" if course % 2 == 1 else "B"
                row = colour * NEEDLES
                total[colour] += NEEDLES
                print(f"course {course}: {row}")
            print("loops A:", total["A"], "loops B:", total["B"])
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.PROJECT,
        section = "Registers",
        title = "A parish register of baptisms by month, totalled",
        originalTitle = "A parish register of baptisms by month, totalled",
        author = "Parish clerks of the period",
        year = 1586,
        summary = "A parish clerk totals the baptisms entered in the register month by month, and a short program prints the running total and the busiest month.",
        details = "Parish registers recorded the baptisms of each year, and a clerk could total the entries by month to see the pattern. The example uses illustrative counts for four months, not the entries of any real parish.",
        instructions = """
            1. Save the example as baptisms.py.
            2. Run it: python3 baptisms.py
        """,
        example = """
            BAPTISMS = [("January", 4), ("February", 2), ("March", 5), ("April", 3)]

            total = 0
            for month, count in BAPTISMS:
                total += count
                print(f"{month}: {count} baptisms, {total} so far")
            print("busiest month:", max(BAPTISMS, key=lambda item: item[1])[0])
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.PROJECT,
        section = "Dictionaries",
        title = "Sorting Thomas Thomas's Latin words",
        originalTitle = "Dictionarium Linguae Latinae et Anglicanae",
        author = "Thomas Thomas",
        year = 1587,
        summary = "A short program sorts a list of Latin headwords, counts them and finds the longest one, which is the everyday work of a word book.",
        details = "Thomas's Dictionarium of 1587 arranged its headwords in alphabetical order, and the project sorts seven illustrative Latin words in the same way. The word list is illustrative and is not taken from the book.",
        instructions = """
            1. Save the example as headwords.py.
            2. Run it: python3 headwords.py
        """,
        example = """
            WORDS = ["vir", "amor", "rex", "bonus", "cadere", "ars", "lex"]

            ordered = sorted(WORDS)
            print("sorted:", " ".join(ordered))
            print("headwords:", len(ordered))
            longest = max(ordered, key=len)
            print(f"longest: {longest} ({len(longest)} letters)")
        """,
    ),

)
