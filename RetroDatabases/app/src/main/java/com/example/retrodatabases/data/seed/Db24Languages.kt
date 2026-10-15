package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1800 to 1810: number-theory and chemical notations, a naval signal vocabulary and a sign system. */
val db24Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 24,
        kind = Kind.LANGUAGE,
        section = "Number theory notation",
        title = "Gauss's congruence sign in the Disquisitiones Arithmeticae",
        originalTitle = "Disquisitiones Arithmeticae (1801)",
        author = "Carl Friedrich Gauss",
        year = 1801,
        summary = "Gauss wrote a congruence between two integers with a three-bar sign, and the phrase modulo m names the modulus, a notation that later texts kept.",
        details = "The Disquisitiones Arithmeticae was printed at Leipzig in 1801, and in it Gauss wrote a congruence with a three-bar sign and used the phrase modulo m for the modulus. The notation spread through number theory over the next century and is still the form used in textbooks.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.LANGUAGE,
        section = "Chemical notation",
        title = "Dalton's circle symbols for the elements",
        originalTitle = "A New System of Chemical Philosophy, Part I (1808)",
        author = "John Dalton",
        year = 1808,
        summary = "Dalton drew each element as a small circle with marks inside it and built compounds by placing the circles together, so a formula was a picture.",
        details = "Part I of Dalton's New System, printed in 1808, gives the symbols for the elements as circles with dots, strokes or letters inside, and draws compounds as groups of those circles. Chemists later replaced the circles with letter symbols, which became the standard form.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.LANGUAGE,
        section = "Naval signals",
        title = "Popham's numbered signal vocabulary for the navy",
        originalTitle = "Telegraphic Signal Book, 1800 edition",
        author = "Sir Home Riggs Popham",
        year = 1800,
        summary = "Popham gave ships a numbered book in which each code number stood for a whole word or phrase, so that a few flags read as numbers could carry a long message.",
        details = "Royal Museums Greenwich dates the system to 1800, and the Science Museum Group gives an introduction dated November 1800. The earliest surviving printing cited is from 1803, and official Royal Navy adoption came only in 1816, so accounts differ on when the book first came into use.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.LANGUAGE,
        section = "Writing systems",
        title = "Grotefend's first reading of Old Persian cuneiform",
        originalTitle = "Report on the Persepolis inscriptions (1802)",
        author = "Georg Friedrich Grotefend",
        year = 1802,
        summary = "Grotefend read the first signs of Old Persian cuneiform in 1802 by guessing the sense of the royal titles in the Persepolis inscriptions.",
        details = "He worked from copies of the royal inscriptions at Persepolis, and his guesses at the names and titles of the kings gave the first values of some signs. Most of the script was read only later, through Rawlinson's work in the 1840s, so the record covers only the start of the decipherment.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.LANGUAGE,
        section = "Grammar",
        title = "Colebrooke's grammar of the Sanskrit language",
        originalTitle = "A Grammar of the Sanskrit Language (1805)",
        author = "Henry Thomas Colebrooke",
        year = 1805,
        summary = "Colebrooke's grammar of 1805 set out the forms of Sanskrit in ordered rules and tables for English readers.",
        details = "Colebrooke was an official of the East India Company in India, and his grammar of 1805 gave the sounds, nouns and verbs of Sanskrit in systematic order. Later writers compared the rule-based method of the ancient grammarian Panini with formal rule systems of the twentieth century.",
    ),

    entry(
        databaseId = 24,
        kind = Kind.LANGUAGE,
        section = "Calculus notation",
        title = "Woodhouse's case for Leibniz's notation in the calculus",
        originalTitle = "Principles of Analytical Calculation (1803)",
        author = "Robert Woodhouse",
        year = 1803,
        summary = "Woodhouse argued in 1803 that Leibniz's differential notation was clearer than Newton's dots, and the argument started a reform of notation at Cambridge.",
        details = "Woodhouse's Principles of Analytical Calculation, printed in 1803, defends the d and dx symbols against the dot notation of Newton, which was still used at Cambridge. The reform is usually dated to the Analytical Society, founded in 1812 by Babbage, Herschel and Peacock, and accounts differ on how far Woodhouse's book led to it.",
        rare = true,
    ),
)
