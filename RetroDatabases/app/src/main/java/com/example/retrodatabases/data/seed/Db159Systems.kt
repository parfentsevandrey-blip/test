package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 451: the Council of Chalcedon and the Roman and Visigothic alliance at the Catalaunian Plains. */
val db159Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 159,
        kind = Kind.SYSTEM,
        section = "Church",
        title = "The Council of Chalcedon, which defined the two natures of Christ in 451",
        originalTitle = "Concilium Chalcedonense",
        author = "The bishops of the council, convened by the emperor Marcian",
        year = 451,
        summary = "The Council of Chalcedon met in 451 and issued a definition that Christ is one person in two natures, divine and human.",
        details = "Most accounts date the council from 8 October to 1 November 451, and it met at Chalcedon across the Bosphorus from Constantinople under the emperor Marcian. The Chalcedonian Definition says the two natures are joined without confusion, change, division or separation, and the canons are numbered differently in different collections. Reports of the number of bishops differ widely, from about 340 present at one time to 600, so the figures are estimates.",
    ),

    entry(
        databaseId = 159,
        kind = Kind.SYSTEM,
        section = "Alliances",
        title = "The Roman and Visigothic alliance that turned back Attila at the Catalaunian Plains",
        originalTitle = "Battle of the Catalaunian Plains",
        author = "Aetius, with Theodoric I of the Visigoths and other allies",
        year = 451,
        summary = "In 451 a Roman army under Aetius, joined by Visigoths and other allies, turned back Attila's invasion of Gaul on the Catalaunian Plains.",
        details = "The alliance was an arrangement between the Roman commander and the Visigothic king, and the Visigothic king Theodoric I fell in the fighting. The ancient accounts give large and doubtful figures for the armies and the dead, so no firm numbers are given here. Attila withdrew from Gaul the same year and invaded Italy in 452.",
    ),

)
