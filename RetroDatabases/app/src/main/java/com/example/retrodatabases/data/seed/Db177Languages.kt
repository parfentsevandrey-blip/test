package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 270 to 279: Porphyry's Greek introduction to logic, and the Bamboo Annals, a Chinese chronicle on bamboo slips. */
val db177Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 177,
        kind = Kind.LANGUAGE,
        section = "Logic",
        title = "Porphyry's Isagoge, a Greek introduction to logic with the tree of kinds",
        originalTitle = "Eisagoge",
        author = "Porphyry of Tyre",
        year = 270,
        summary = "Porphyry wrote the Isagoge, a Greek introduction to Aristotle's Categories, in Sicily about 270, and it stayed a standard school text of logic for many centuries.",
        details = "The ancient commentators Ammonius, Elias and David place its composition in Sicily between 268 and 270 and name Chrysaorius as its recipient, so the record uses 270, the end of that window. The book sets out the Tree of Porphyry, a scheme of genera and species running from substance down to individuals, and Boethius's Latin translation carried it into the medieval schools.",
    ),

    entry(
        databaseId = 177,
        kind = Kind.LANGUAGE,
        section = "Chronicles",
        title = "The Bamboo Annals, a chronicle written on bamboo slips and found in a tomb",
        originalTitle = "Zhushu jinian",
        author = "Historians of the state of Wei",
        year = 279,
        summary = "The Bamboo Annals is a Chinese chronicle of rulers from the legendary past down to the kings of Wei, written on bamboo slips that a tomb yielded in the late 270s.",
        details = "Accounts differ on the year of the find, with 279 given in some and 281 in others, and this record uses 279 as the year of the find. Scholars at the Jin court copied and arranged the slips after the find, and the chronology they give differs from that of the Shiji in places.",
        rare = true,
    ),

)
