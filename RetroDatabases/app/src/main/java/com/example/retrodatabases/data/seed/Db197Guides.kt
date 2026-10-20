package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One guide of 70 to 80: how the twenty-four priestly courses of the Temple rotated through the weeks of the year, placed at the year the Temple fell. */
val db197Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 197,
        kind = Kind.GUIDE,
        section = "Temple",
        title = "How the twenty-four priestly courses rotate through the weeks of the Temple year",
        originalTitle = "Mishmarot",
        author = "Priests of the Jerusalem Temple",
        year = 70,
        summary = "The twenty-four priestly courses served the Temple in turn, and a short program shows which course serves each week and how often each course returns in a year of fifty-two weeks.",
        details = "First Chronicles lists twenty-four divisions of priests, and the courses served the Temple in turn, each for a week, until the Temple fell in 70. The example takes a year of exactly fifty-two weeks as an illustration, and it shows that the rota returns to the first course after every twenty-four weeks, so four courses serve three times in such a year and twenty serve twice.",
        rare = true,
        instructions = """
            1. Save the example as courses.py.
            2. Run it: python3 courses.py
        """,
        example = """
            COURSES = 24
            WEEKS = 52   # illustrative: a year of exactly 52 weeks

            def course_for_week(week):
                return (week - 1) % COURSES + 1

            for week in [1, 24, 25, 52]:
                print("week", week, "course", course_for_week(week))

            served = {}
            for week in range(1, WEEKS + 1):
                course = course_for_week(week)
                served[course] = served.get(course, 0) + 1
            print("courses served three times:", sum(1 for n in served.values() if n == 3))
            print("courses served twice:", sum(1 for n in served.values() if n == 2))
        """,
    ),

)
