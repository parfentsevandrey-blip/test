package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1210 to 1220: a land measurers' geometry, a Japanese book of poets, a gloss on canon law, a Latin handbook of composition and a Japanese history. */
val db83Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 83,
        kind = Kind.TOOL,
        section = "Geometry",
        title = "Fibonacci's Practica geometriae, a handbook for land measurers",
        originalTitle = "Practica geometriae",
        author = "Leonardo Fibonacci of Pisa",
        year = 1220,
        summary = "Fibonacci's Practica geometriae, finished about 1220, is a geometry handbook for land measurers, with rules for areas, solids and distances.",
        details = "The book builds on Euclid, gives methods for roots and for the areas and solids of common figures, and describes a quadrant for sighting. Most accounts date it to 1220, and it was written as a companion to the author's Liber abaci.",
        rare = true,
    ),

    entry(
        databaseId = 83,
        kind = Kind.TOOL,
        section = "Poetry",
        title = "Kamo no Chomei's Mumyosho, notes on poets and poetry",
        originalTitle = "Mumyosho",
        author = "Kamo no Chomei",
        year = 1211,
        summary = "Kamo no Chomei's Mumyosho is a short Japanese book of notes on poets and their poems, written about 1211 by the author of the Hojoki.",
        details = "The notes are informal and personal, and they sit beside the Hojoki as a second work by the same author. Accounts give dates from about 1209 to about 1212, so 1211 is a rounded date.",
        rare = true,
    ),

    entry(
        databaseId = 83,
        kind = Kind.TOOL,
        section = "Canon law",
        title = "The Glossa ordinaria on Gratian's Decretum, a standard gloss of canon law",
        originalTitle = "Glossa ordinaria in Decretum",
        author = "Johannes Teutonicus",
        year = 1215,
        summary = "Johannes Teutonicus's Glossa ordinaria explains Gratian's Decretum, the basic collection of church law, and became the standard reference gloss for canon lawyers.",
        details = "Accounts give the composition as running from about 1211 to about 1218, and parts of the work seem to reflect the council of 1215. A later revision by Bartholomew of Brescia was made about 1245.",
        rare = true,
    ),

    entry(
        databaseId = 83,
        kind = Kind.TOOL,
        section = "Rhetoric",
        title = "Geoffrey of Vinsauf's Poetria nova, a handbook of Latin composition",
        originalTitle = "Poetria nova",
        author = "Geoffrey of Vinsauf",
        year = 1210,
        summary = "Geoffrey of Vinsauf's Poetria nova is a Latin handbook of composition written about 1210, and it teaches writers how to arrange, amplify and adorn a text.",
        details = "The handbook is addressed to Pope Innocent III and gives rules for building a theme, for amplifying it and for its ornaments of style. Accounts date it to the years between about 1208 and about 1213, so 1210 is a rounded date.",
        rare = true,
    ),

    entry(
        databaseId = 83,
        kind = Kind.TOOL,
        section = "History",
        title = "Jien's Gukansho, a history of Japan's rulers by a monk",
        originalTitle = "Gukansho",
        author = "Jien",
        year = 1219,
        summary = "Jien's Gukansho, dated about 1219, is a history of Japan's emperors and of the rise of warrior government, written by a Buddhist monk.",
        details = "The author was born into the Fujiwara family and spent his life as a Tendai monk, and the book reads the course of the realm as a matter of cause and effect. The date of about 1219 is the usual one for its completion.",
        rare = true,
    ),

)
