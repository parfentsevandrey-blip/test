package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Two practices of 820 to 830: the Plan of St Gall, a drawn model of a monastery, and the Utrecht Psalter, whose pictures sit beside the verses they illustrate. */
val db122Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 122,
        kind = Kind.PRACTICE,
        section = "Reichenau",
        title = "The plan of St Gall, a drawn plan of an ideal monastery with its rooms named in Latin",
        originalTitle = "Plan of St Gall",
        author = "Probably the scribes of Reichenau, for Abbot Gozbert of St Gall",
        year = 820,
        summary = "The plan of St Gall, a drawing on parchment of an ideal monastery with its rooms labelled in Latin, was made about 820 and sent to the abbot of St Gall.",
        details = "The plan sets the church, cloister, dormitory, refectory, kitchen and workshops on one layout, as a model for a monastery rather than a survey of any real site. The date of about 820 is the usual one, although some accounts put the making between 819 and 826, and the draughtsman is not named.",
        rare = true,
    ),

    entry(
        databaseId = 122,
        kind = Kind.PRACTICE,
        section = "Reims",
        title = "The Utrecht Psalter, whose drawings sit beside the psalm verses they illustrate",
        originalTitle = "Utrecht Psalter",
        author = "Artists of the Reims school",
        year = 820,
        summary = "The Utrecht Psalter, a psalter with drawings placed beside the verses they illustrate, was made in the Reims region about 820 to 835.",
        details = "Many of its drawings follow the words of the psalms almost literally, so each picture works as a visual gloss on the text. The usual date is about 820 to 835, and the place is given as the Reims region, with the abbey of Hautvillers near Reims the usual suggestion.",
    ),

)
