package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1230 to 1235: a builder's sketchbook, a Latin concordance of the Bible from Paris, and a Bolognese manual for writing letters. */
val db81Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 81,
        kind = Kind.TOOL,
        section = "Drawing",
        title = "Villard de Honnecourt's sketchbook of machines, figures and geometry",
        originalTitle = "Carnet de Villard de Honnecourt",
        author = "Villard de Honnecourt",
        year = 1230,
        summary = "Villard de Honnecourt's album of about 250 drawings, made in Picardy around 1230, shows masons' geometry, figures from life and machines in one notebook.",
        details = "The album has 33 parchment leaves and is kept in Paris as manuscript Fr 19093 at the national library. Estimates of its date run from about 1225 to 1235, and scholars still argue whether its maker was an architect or a skilled craftsman.",
    ),

    entry(
        databaseId = 81,
        kind = Kind.TOOL,
        section = "Bible",
        title = "Hugh of Saint-Cher's concordance of the Latin Bible, made in Paris",
        originalTitle = "Concordantiae Sancti Jacobi",
        author = "Hugh of Saint-Cher and the friars of St Jacques",
        year = 1230,
        summary = "Hugh of Saint-Cher's concordance lists the words of the Latin Bible in alphabetical order with their places, a finding aid made by Dominican friars in Paris from about 1230.",
        details = "Verse numbers were not yet in use, so each entry gives the book, the chapter and a letter for the part of the chapter. The work was probably finished by 1239, and modern scholars credit the friars who worked under Hugh, with accounts differing on how much of it was his own work.",
        rare = true,
    ),

    entry(
        databaseId = 81,
        kind = Kind.TOOL,
        section = "Letters",
        title = "Boncompagno da Signa's Rhetorica novissima, a manual for writing letters",
        originalTitle = "Rhetorica novissima",
        author = "Boncompagno da Signa",
        year = 1235,
        summary = "Boncompagno da Signa's Rhetorica novissima of 1235 is a Latin manual of letter-writing for students at Bologna, with set forms for greetings, requests and closings.",
        details = "It belongs to the art of composing letters, the ars dictaminis, which taught letters in fixed parts and model phrases. Boncompagno taught at Bologna, where the art was studied in the schools, and the manual is dated to 1235.",
        rare = true,
    ),

)
