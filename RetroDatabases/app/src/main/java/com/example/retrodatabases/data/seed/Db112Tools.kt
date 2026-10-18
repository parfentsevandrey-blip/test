package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One tool of 924, placed at the year of an anchor: the Burghal Hidage, a list of English fortresses and the hides that supplied their garrisons. */
val db112Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 112,
        kind = Kind.TOOL,
        section = "England",
        title = "The Burghal Hidage, a list of English fortresses and the hides that supplied their garrisons",
        originalTitle = "Burghal Hidage",
        author = "An anonymous compiler for the court of Edward the Elder",
        year = 924,
        summary = "The Burghal Hidage lists English fortified towns and gives each a number of hides, the land unit that fixed how many men each town owed for its defence.",
        details = "The list is usually dated to about 914 to 919, in the reign of Edward the Elder, so 924 is not the date of the list. The year 924 is when Edward died and Athelstan succeeded him, and it is used here as the year of the anchor.",
        rare = true,
    ),

)
