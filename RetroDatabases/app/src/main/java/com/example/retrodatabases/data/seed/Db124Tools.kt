package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 800 to 810: the Propositiones ad acuendos iuvenes, a Latin book of problems dated in some accounts to about 800. */
val db124Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 124,
        kind = Kind.TOOL,
        section = "Latin",
        title = "The Propositiones ad acuendos iuvenes, a Latin book of problems to sharpen the young, credited to Alcuin",
        originalTitle = "Propositiones ad acuendos iuvenes",
        author = "Alcuin of York (attributed)",
        year = 800,
        summary = "The Propositiones ad acuendos iuvenes is a Latin collection of mathematical problems and puzzles, dated in some accounts to about 800 and credited to Alcuin of York.",
        details = "The attribution to Alcuin, who died in 804, is disputed, and the earliest surviving manuscripts date only from the late ninth century. The number of problems depends on the edition, 53 or 56, and problem 18 is the wolf, goat and cabbage crossing, whose first recorded form appears in this text.",
        rare = true,
    ),

)
