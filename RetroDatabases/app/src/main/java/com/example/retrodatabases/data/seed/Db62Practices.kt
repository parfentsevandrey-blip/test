package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1420 to 1430: the Florentine year that began on 25 March, herringbone brickwork in the new dome, a single vanishing point in Masaccio's Trinity, the Italian hours from sunset and Ghiberti's bronze doors for the Baptistery. */
val db62Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 62,
        kind = Kind.PRACTICE,
        section = "Calendars",
        title = "The Florentine year that began on 25 March",
        originalTitle = "Stile fiorentino",
        author = "The Florentine chancery and notaries",
        year = 1427,
        summary = "Florence began its year on 25 March, the feast of the Annunciation, so a date in January to March belonged to the year before the modern one.",
        details = "Florentine documents of the period, including the returns of the catasto of 1427, were dated in this style. A date of 10 March 1427 in modern reckoning was written as 1426 in Florence, because the year number changed only on 25 March, and the practice lasted well beyond the 1420s.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.PRACTICE,
        section = "Brickwork",
        title = "Herringbone brickwork in the dome of Florence",
        originalTitle = "Spina di pesce",
        author = "Filippo Brunelleschi's building works",
        year = 1420,
        summary = "The dome of Florence Cathedral was built from 1420 with bricks laid in herringbone courses, so that the rings could stand without centring.",
        details = "The dome was begun in 1420, and its double shell uses herringbone patterns of brick in the courses of the masonry. Historians describe the pattern as a means of building the dome without a full timber centring, and the exact construction is studied from the surviving fabric.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.PRACTICE,
        section = "Perspective",
        title = "One vanishing point in Masaccio's Trinity",
        originalTitle = "Trinita",
        author = "Masaccio",
        year = 1427,
        summary = "Masaccio's fresco of the Trinity in Santa Maria Novella is built on a single vanishing point, so the receding barrel vault looks deep on a flat wall.",
        details = "The fresco is dated to about 1426 to 1428, and the year 1427 is used here. It is among the earliest surviving paintings that show linear perspective built with a single vanishing point, which Brunelleschi had shown in his demonstrations.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.PRACTICE,
        section = "Communion",
        title = "Communion in both kinds, the Hussite article",
        originalTitle = "Utraquism",
        author = "Hussite masters of Prague",
        year = 1420,
        summary = "The Four Articles of Prague of 1420 demanded that the laity receive communion in both kinds, bread and wine.",
        details = "The articles were drawn up in Prague in 1420 and became the core demand of the Hussite movement. The practice divided the Hussites from the Roman church for generations, and the year 1420 is the date of the articles in the usual accounts.",
    ),

    entry(
        databaseId = 62,
        kind = Kind.PRACTICE,
        section = "Bronze casting",
        title = "Ghiberti's bronze doors for the Baptistery",
        originalTitle = "Porte del Battistero",
        author = "Lorenzo Ghiberti",
        year = 1424,
        summary = "Ghiberti's first bronze doors for the Florence Baptistery were finished in 1424 by the lost-wax method of casting in bronze.",
        details = "The doors are cast in panels by the lost-wax method, in which a wax model is melted out of a clay mould and replaced by molten bronze. The doors were finished in 1424, and the panels show the new taste for perspective and figures in relief.",
    ),

)
