package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** One practice of 880: Pope John VIII's letter Industriae tuae, which allowed the Mass in Slavonic in Moravia. */
val db116Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 116,
        kind = Kind.PRACTICE,
        section = "Moravia",
        title = "Pope John VIII's letter Industriae tuae, which allowed the Mass in Slavonic",
        originalTitle = "Industriae tuae",
        author = "Pope John VIII",
        year = 880,
        summary = "Pope John VIII's letter Industriae tuae of 880 allowed the Mass to be said in Slavonic in Moravia, with the Gospel read first in Latin.",
        details = "The letter was addressed to the Moravian prince Svatopluk and concerned the Slavonic liturgy brought to Moravia by the mission of Methodius. It is dated 880 in the usual accounts, and it set the rule that the Gospel was to be read in Latin before it was read in Slavonic. A successor of John VIII later withdrew the permission.",
        rare = true,
    ),

)
