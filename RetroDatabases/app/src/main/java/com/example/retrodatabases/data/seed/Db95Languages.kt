package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1091 to 1100: a Persian treatise on government, a doctrine of words from Soissons, an Arabic critique of the philosophers and an Old French epic. */
val db95Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 95,
        kind = Kind.LANGUAGE,
        section = "Persian",
        title = "Nizam al-Mulk's Siyasatnama, the Persian book of government",
        originalTitle = "Siyasatnama",
        author = "Nizam al-Mulk",
        year = 1091,
        summary = "The Siyasatnama is a Persian treatise on kingship by the Seljuk vizier Nizam al-Mulk, written for the sultan Malik-Shah with stories and advice on rule and on the officers of the state.",
        details = "The book is usually dated to about 1091, a year before its author was murdered in 1092 after a long career in government. Scholars read it as a mirror for princes and as a record of how the Seljuk administration was meant to work.",
    ),

    entry(
        databaseId = 95,
        kind = Kind.LANGUAGE,
        section = "Logic",
        title = "Roscelin's teaching that universals are only a breath of the voice",
        originalTitle = "Flatus vocis",
        author = "Roscelin of Compiegne",
        year = 1092,
        summary = "Roscelin of Compiegne taught that universal words are only a breath of the voice, and his view of the Trinity was rejected as tritheism at Soissons in 1092 or 1093.",
        details = "The council at Soissons rejected his view that the three Persons of the Trinity are three substances, and Roscelin then disavowed it under threat, though the view came back later. The phrase breath of the voice seems to be his critics' label, and his only surviving writing is a letter to Abelard, so his doctrine is known mostly from hostile accounts.",
        rare = true,
    ),

    entry(
        databaseId = 95,
        kind = Kind.LANGUAGE,
        section = "Philosophy",
        title = "Al-Ghazali's Incoherence of the Philosophers, an Arabic critique of Avicenna",
        originalTitle = "Tahafut al-falasifa",
        author = "Abu Hamid al-Ghazali",
        year = 1095,
        summary = "Al-Ghazali's Tahafut al-falasifa is an Arabic critique of the philosophers, chiefly Avicenna and al-Farabi, which sets out twenty points on which their views fail.",
        details = "Al-Ghazali picks out three doctrines of the philosophers as unbelief: that the world is eternal, that God knows only universals, and that bodies are not resurrected. The book is usually dated to about 1095, and Ibn Rushd answered it in the later twelfth century with his Incoherence of the Incoherence.",
    ),

    entry(
        databaseId = 95,
        kind = Kind.LANGUAGE,
        section = "Epic",
        title = "The Chanson de Roland, an Old French epic of the rear guard at Roncevaux",
        originalTitle = "La Chanson de Roland",
        author = "An anonymous poet of northern France",
        year = 1100,
        summary = "The Chanson de Roland is an Old French epic of the rear guard lost at Roncevaux, composed about 1100 and copied in manuscripts of the later twelfth century, and it is written in laisses.",
        details = "The date of composition is disputed: most scholars place the poem around 1100, though some argue for an earlier or later date, and its earliest surviving copy is from the later twelfth century. The laisses, groups of lines closed by one vowel sound, give the poem its form.",
    ),

)
