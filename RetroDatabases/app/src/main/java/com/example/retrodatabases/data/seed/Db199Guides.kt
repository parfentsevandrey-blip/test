package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 50 to 59: how Roman inclusive counting names the days of October, with the day Claudius died. */
val db199Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 199,
        kind = Kind.GUIDE,
        section = "Calendar",
        title = "How Roman inclusive counting names the days of October, with 13 October 54",
        originalTitle = "Ante diem counting",
        author = "Roman pontiffs and clerks of the calendar",
        year = 54,
        summary = "Roman dates were counted back from the Kalends, Nones and Ides with the anchor day counted as one, and a short program names five days of October 54 in that way.",
        details = "In October the Nones fall on the 7th and the Ides on the 15th, so 13 October is written as the third day counted back from the Ides, a.d. III Id. Oct. The example names five days of the month, and 13 October 54 is the day that Claudius died, as the sources give it.",
        instructions = """
            1. Save the example as ante_diem.py.
            2. Run it: python3 ante_diem.py
        """,
        example = """
            ROMAN = {1: "I", 2: "II", 3: "III", 4: "IV", 5: "V", 6: "VI", 7: "VII", 8: "VIII"}

            def name_day(day):
                # October: Kalends 1, Nones 7, Ides 15; the anchor day counts as one
                if day == 1:
                    return "Kal. Oct."
                if day == 7:
                    return "Non. Oct."
                if day == 15:
                    return "Id. Oct."
                if day == 14:
                    return "pridie Id. Oct."
                if day < 7:
                    return "a.d. " + ROMAN[8 - day] + " Non. Oct."
                return "a.d. " + ROMAN[16 - day] + " Id. Oct."

            for day in [1, 7, 13, 14, 15]:
                print(f"{day} October 54: {name_day(day)}")
        """,
        rare = true,
    ),

)
