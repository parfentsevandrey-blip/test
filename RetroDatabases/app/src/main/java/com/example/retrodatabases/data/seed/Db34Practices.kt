package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1700 to 1710: a copyright act, a sign test on births, a tithe proposal, a puzzle almanac and a New Year decree. */
val db34Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 34,
        kind = Kind.PRACTICE,
        section = "Charity",
        title = "Queen Anne's Bounty",
        originalTitle = "The Bounty of Queen Anne",
        author = "Queen Anne",
        year = 1704,
        summary = "A fund set up in 1704 that used the first-fruits and tenths paid by church livings to augment the incomes of poor clergy.",
        details = "The fund took the payments that the crown had received from church livings and returned them to poorer livings. It is an early example of a church body that supports its own clergy through a standing levy on livings.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.PRACTICE,
        section = "Statistics",
        title = "Arbuthnot's argument from christening records",
        originalTitle = "An Argument for Divine Providence, taken from the constant regularity observ'd in the births of both sexes",
        author = "John Arbuthnot",
        year = 1710,
        summary = "A paper arguing that male christenings exceeded female ones in London in every one of 82 years of records, which he took as a sign of design.",
        details = "Arbuthnot published the paper in the Philosophical Transactions in 1710, using London christening counts from 1629 to 1710. It is often cited as an early use of a sign test, since the chance that a fair coin favours one sex in every one of those years is very small.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.PRACTICE,
        section = "Taxation",
        title = "Vauban's tithe proposal",
        originalTitle = "Projet d'une dixme royale",
        author = "Sebastien Le Prestre de Vauban",
        year = 1707,
        summary = "A proposal to replace the old taxes with one tenth of every person's income, based on local surveys of parishes.",
        details = "Vauban published the proposal in 1707, and the authorities suppressed it soon after because it threatened the privileges of the church and the nobility. The plan rested on counts that he had gathered in parishes, so it was an early attempt to base a national tax on surveyed data.",
        rare = true,
    ),

    entry(
        databaseId = 34,
        kind = Kind.PRACTICE,
        section = "Puzzles",
        title = "The Ladies' Diary, a puzzle almanac",
        originalTitle = "The Ladies' Diary: or, the Woman's Almanack",
        author = "Stationers' Company, London",
        year = 1704,
        summary = "A yearly almanac whose puzzle columns posed mathematical questions, answered by readers in later issues.",
        details = "The almanac began in London in 1704, and its puzzle columns invited readers to send in solutions that were printed later. It ran for more than a century and printed many problems in arithmetic and geometry.",
    ),

    entry(
        databaseId = 34,
        kind = Kind.PRACTICE,
        section = "Calendar",
        title = "Peter's New Year on the first of January",
        originalTitle = "Decree on the celebration of the New Year",
        author = "Peter the Great",
        year = 1700,
        summary = "A decree that moved the start of the year to 1 January and counted years from the birth of Christ in official documents.",
        details = "The decree is dated 20 December 1699 in the Julian calendar, and it set the new year at 1 January 1700 in place of the old start on 1 September. It also replaced the older count of years from the creation of the world.",
    ),

)
