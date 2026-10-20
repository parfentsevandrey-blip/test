package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 244: the decempeda, the ten-foot rod of Roman land surveyors, placed at 244 by the accession of Philip the Arab. */
val db180Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 180,
        kind = Kind.TOOL,
        section = "Surveying",
        title = "The decempeda, the ten-foot rod that Roman land surveyors measured with",
        originalTitle = "decempeda",
        author = "Roman land surveyors",
        year = 244,
        summary = "The decempeda was a ten-foot measuring rod used by Roman land surveyors, and the sources do not date any particular rod, so the year given is an anchor.",
        details = "The rod is described in Roman writing on land measurement, but no source dates a particular example. 244 is the year of the anchor, the accession of Philip the Arab, and it places this record only; it is not the date of the rod.",
        rare = true,
    ),

)
