package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 1274: the questions in the four parts of Aquinas's Summa theologiae, counted in code. */
val db77Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 77,
        kind = Kind.EXAMPLE,
        section = "Summa",
        title = "How many questions the four parts of Aquinas's Summa theologiae hold",
        originalTitle = "Summa theologiae",
        author = "Thomas Aquinas, with the Supplement compiled by Reginald of Piperno",
        year = 1274,
        summary = "Aquinas's Summa theologiae has 512 questions in its four parts as the work stands, and 611 with the Supplement added after his death, and a short program adds them up.",
        details = "The third part breaks off at question 90, while the Supplement of 99 questions was drawn from Aquinas's commentary on the Sentences and added after his death. The counts are the ones commonly given for the parts, and the example prints each part so the total can be checked by hand.",
        instructions = """
            1. Save the example as summa_questions.py.
            2. Run it: python3 summa_questions.py
        """,
        example = """
            # Questions in each part of the Summa theologiae, as the work stands; the third part breaks off at question 90.
            PARTS = [("Prima pars", 119), ("Prima secundae", 114), ("Secunda secundae", 189), ("Tertia pars", 90)]
            SUPPLEMENT = 99   # questions added after Aquinas's death, drawn from his commentary on the Sentences
            total = 0
            for name, questions in PARTS:
                print(f"{name}: {questions}")
                total += questions
            print("questions in the four parts:", total)
            print("with the Supplement:", total + SUPPLEMENT)
        """,
    ),

)
