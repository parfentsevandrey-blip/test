package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practice of 833 to 837: the Kaicheng Stone Classics, twelve Confucian texts cut on 114 stone tablets at Chang'an. */
val db121Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 121,
        kind = Kind.PRACTICE,
        section = "China",
        title = "The Kaicheng Stone Classics, twelve Confucian texts carved on stone at Chang'an",
        originalTitle = "Kaicheng shijing",
        author = "Tang court scholars at Chang'an",
        year = 833,
        summary = "The Kaicheng Stone Classics, twelve Confucian texts carved on 114 stone tablets at Chang'an, were cut between 833 and 837.",
        details = "The tablets carry the text on both faces, and the set holds more than 650,000 characters, from the Changes to the Erya dictionary. Accounts differ on the start, with some giving 830 and others 833, while 837 is the common end date, and the tablets now stand in the Stele Forest Museum at Xi'an.",
        rare = true,
    ),

)
