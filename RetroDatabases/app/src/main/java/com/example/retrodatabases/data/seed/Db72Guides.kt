package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1320 to 1330: ells and quarters of cloth, the quarters of a shield, a disputed question, Roman dating by the Kalends, Nones and Ides, and the days of a stage journey. */
val db72Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 72,
        kind = Kind.GUIDE,
        section = "Cloth",
        title = "How cloth is measured in ells and quarters",
        originalTitle = "Ell and quarter reckoning",
        author = "Cloth merchants of the period",
        year = 1320,
        summary = "A length of cloth was reckoned in ells, each divided into four quarters, and a short program adds two lengths and carries the quarters into whole ells.",
        details = "The quarter was the usual fraction of the ell in the cloth trade, so every sum carries at four rather than at ten. The example adds three pairs of lengths, and each total can be checked by hand against the carry.",
        instructions = """
            1. Save the example as ells.py.
            2. Run it: python3 ells.py
        """,
        example = """
            QUARTERS_PER_ELL = 4

            def add(first, second):
                total = first[0] * QUARTERS_PER_ELL + first[1] + second[0] * QUARTERS_PER_ELL + second[1]
                return total // QUARTERS_PER_ELL, total % QUARTERS_PER_ELL

            pairs = [((3, 2), (1, 3)), ((4, 3), (2, 2)), ((1, 0), (0, 1))]
            for number, (first, second) in enumerate(pairs, start=1):
                ells, quarters = add(first, second)
                print(f"sum {number}: ells {ells} quarters {quarters}")
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.GUIDE,
        section = "Heraldry",
        title = "How the four quarters of a shield are numbered",
        originalTitle = "Quartering",
        author = "Heralds of western Europe",
        year = 1325,
        summary = "A shield divided into four quarters by a cross is numbered from the top field on the viewer's left, and a short program prints the place of each quarter.",
        details = "The numbering runs across the shield from the dexter chief, the field on the viewer's left at the top, and then down, so the quarters read one, two, three and four. Heraldic quartering was in use across western Europe by the 1320s, and 1325 is a round date for that use.",
        instructions = """
            1. Save the example as quarters.py.
            2. Run it: python3 quarters.py
        """,
        example = """
            QUARTERS = {1: "dexter chief", 2: "sinister chief", 3: "dexter base", 4: "sinister base"}
            for number, place in QUARTERS.items():
                print(f"quarter {number} is the {place}")
            print("quarters on the shield", len(QUARTERS))
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.GUIDE,
        section = "Disputations",
        title = "How a disputed question is laid out in the schools",
        originalTitle = "Quaestio disputata",
        author = "Masters of the university schools",
        year = 1325,
        summary = "A disputed question was set out in a fixed order of objections, a counter-authority, the master's answer and replies, and a short program prints that order for two objections.",
        details = "The form opened with the question, gave the objections in turn with the phrase it seems that not, then the authority on the other side and the master's answer, and ended with a reply to each objection. The example prints only the order of the parts, and 1325 is a middle date for the disputations of the decade.",
        instructions = """
            1. Save the example as disputed.py.
            2. Run it: python3 disputed.py
        """,
        example = """
            objections = 2
            print("Utrum", "the question is put")
            for number in range(1, objections + 1):
                print("Videtur quod non", "objection", number)
            print("Sed contra", "the authority on the other side")
            print("Respondeo dicendum", "the master's answer")
            for number, ordinal in enumerate(["primum", "secundum"], start=1):
                print("Ad", ordinal, "reply to objection", number)
            print("parts", 3 + 2 * objections)
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.GUIDE,
        section = "Dating",
        title = "How a date is named by the Kalends, Nones and Ides",
        originalTitle = "Kalends, Nones and Ides",
        author = "Roman and medieval scribes",
        year = 1320,
        summary = "Latin documents named a day of the month by counting back from the Kalends, Nones or Ides, and a short program writes six dates of the 1320s in that form.",
        details = "The Nones fell on the fifth day of the month and the Ides on the thirteenth, except in March, May, July and October, when they fell on the seventh and fifteenth. The example writes the Declaration of Arbroath, dated 6 April 1320, as a.d. VIII Id. Apr., and five more dates of the decade.",
        instructions = """
            1. Save the example as dating.py.
            2. Run it: python3 dating.py
        """,
        example = """
            MONTHS = ["Ian.", "Febr.", "Mart.", "Apr.", "Mai.", "Iun.", "Iul.", "Aug.", "Sept.", "Oct.", "Nov.", "Dec."]
            DAYS = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]

            def roman(number):
                out = ""
                for value, sign in [(10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]:
                    while number >= value:
                        out += sign
                        number -= value
                return out

            def roman_date(year, month, day):
                length = DAYS[month - 1]
                if month == 2 and year % 4 == 0:
                    length = 29
                nones = 7 if month in (3, 5, 7, 10) else 5
                ides = nones + 8
                name = MONTHS[month - 1]
                if day == 1:
                    return "Kal. " + name
                if day <= nones:
                    if day == nones:
                        return "Non. " + name
                    if day == nones - 1:
                        return "pridie Non. " + name
                    return "a.d. " + roman(nones - day + 1) + " Non. " + name
                if day <= ides:
                    if day == ides:
                        return "Id. " + name
                    if day == ides - 1:
                        return "pridie Id. " + name
                    return "a.d. " + roman(ides - day + 1) + " Id. " + name
                count = length + 2 - day
                following = MONTHS[month % 12]
                if count == 2:
                    return "pridie Kal. " + following
                return "a.d. " + roman(count) + " Kal. " + following

            for year, month, day in [(1320, 4, 6), (1321, 9, 14), (1322, 9, 28), (1326, 2, 11), (1328, 8, 23), (1330, 3, 15)]:
                print(f"{year}-{month:02d}-{day:02d} = {roman_date(year, month, day)}")
        """,
    ),

    entry(
        databaseId = 72,
        kind = Kind.GUIDE,
        section = "Travel",
        title = "How a journey's days are totalled from its stages",
        originalTitle = "Itinerary reckoning",
        author = "Travellers and their scribes",
        year = 1324,
        summary = "A journey was reckoned as a list of stages, each with its days of travel, and a short program totals the days and finds the average stage.",
        details = "The example uses four stages of three, five, four and six days, an illustrative route with no claim about its real places. The year 1324 is the year of Mansa Musa's pilgrimage, a long journey of the kind that stage lists describe.",
        instructions = """
            1. Save the example as stages.py.
            2. Run it: python3 stages.py
        """,
        example = """
            stages = [3, 5, 4, 6]   # days of travel on each stage, illustrative
            total = sum(stages)
            print("stages", len(stages))
            print("days", total)
            print(f"average {total / len(stages):.2f} days")
        """,
    ),

)
