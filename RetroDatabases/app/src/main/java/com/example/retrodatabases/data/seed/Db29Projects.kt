package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1750 to 1760: a calendar month with its days dropped, an index, a name checker and a lottery count. */
val db29Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 29,
        kind = Kind.PROJECT,
        section = "Calendars",
        title = "Calendar of September 1752 with eleven days missing",
        originalTitle = "Calendar (New Style) Act 1750",
        author = "Parliament of Great Britain",
        year = 1752,
        summary = "The September calendar of 1752 printed in order, with 3 to 13 September left out and the weekdays kept in sequence.",
        details = "Britain changed from the Julian to the Gregorian calendar in 1752, so Wednesday 2 September was followed by Thursday 14 September. The program prints the month in order with the weekday of each date, and counts the eleven days that were dropped.",
        instructions = """
            1. Save the example as calendar_1752.py.
            2. Run it: python3 calendar_1752.py
        """,
        example = """
            names = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"]
            # Britain went from 2 September 1752 (a Wednesday) straight to 14 September (a Thursday).
            dates = [(1, 2), (2, 3)]
            for day in range(14, 31):
                dates.append((day, (4 + day - 14) % 7))
            for day, index in dates:
                print(day, "September", names[index])
            missing = [day for day in range(3, 14)]
            print("days missing:", len(missing))
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.PROJECT,
        section = "Dictionaries",
        title = "Johnson's index of entries by first letter",
        originalTitle = "A Dictionary of the English Language",
        author = "Samuel Johnson",
        year = 1755,
        summary = "A short word list is sorted into alphabetical groups by first letter, with a count for each group, as a dictionary index does.",
        details = "Johnson's dictionary of 1755 is arranged alphabetically, and grouping words by first letter is the first step in building such an index. The example counts the words under each letter in a fixed list of ten words.",
        instructions = """
            1. Save the example as johnson_index.py.
            2. Run it: python3 johnson_index.py
        """,
        example = """
            words = ["abacus", "ballast", "cipher", "anvil", "bellows", "dictionary", "engine", "gauge", "dial", "adder"]
            counts = {}
            for w in words:
                letter = w[0]
                counts[letter] = counts.get(letter, 0) + 1
            for letter in sorted(counts):
                print(letter, counts[letter])
            print("words indexed:", sum(counts.values()))
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.PROJECT,
        section = "Binomial names",
        title = "Linnaeus's binomial name checker",
        originalTitle = "Species Plantarum",
        author = "Carl Linnaeus",
        year = 1753,
        summary = "A name is checked for the Linnaean form: two words, a capital for the genus and small letters for the species epithet.",
        details = "Linnaeus gave each plant a genus name and a species epithet. The rule that the genus takes a capital and the epithet does not is still part of the botanical codes. The example checks six names and counts the valid ones.",
        instructions = """
            1. Save the example as binomial_check.py.
            2. Run it: python3 binomial_check.py
        """,
        example = """
            names = ["Canis lupus", "Homo sapiens", "canis lupus", "Rosa Canina", "Quercus", "Bellis perennis"]


            def valid(name):
                parts = name.split(" ")
                return (len(parts) == 2
                        and parts[0][:1].isupper() and parts[0][1:].islower()
                        and parts[1].islower())


            for name in names:
                print(name, "valid" if valid(name) else "invalid")
            print("valid names:", sum(valid(name) for name in names))
        """,
    ),

    entry(
        databaseId = 29,
        kind = Kind.PROJECT,
        section = "Lotteries",
        title = "The 1757 lottery: five numbers from ninety",
        originalTitle = "Loterie de l'Ecole militaire",
        author = "Ecole royale militaire, Paris",
        year = 1757,
        summary = "A lottery that draws five numbers from ninety has 43,949,268 possible draws, which the example counts with binomial coefficients.",
        details = "The Paris military school was founded by a royal edict of January 1751, and a lottery to fund it was ordered in August 1757. Accounts give the pool as ninety numbers with five drawn, and the example counts the possible draws and the draws that contain one or two given numbers.",
        instructions = """
            1. Save the example as lottery.py.
            2. Run it: python3 lottery.py
        """,
        example = """
            import math

            draws = math.comb(90, 5)
            with_two = math.comb(88, 3)   # draws that contain two given numbers
            with_one = math.comb(89, 4)   # draws that contain one given number
            print("draws of five from ninety:", draws)
            print("draws containing two given numbers:", with_two)
            print("draws containing one given number:", with_one)
        """,
    ),

)
