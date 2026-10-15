package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1690 to 1700: a life table from Breslau, a way of teaching the deaf to speak, a state lottery, a window tax and a physician's study of trades. */
val db35Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 35,
        kind = Kind.PRACTICE,
        section = "Mortality",
        title = "Halley's life table for Breslau",
        originalTitle = "An Estimate of the Degrees of the Mortality of Mankind",
        author = "Edmond Halley",
        year = 1693,
        summary = "Halley turned a register of Breslau births and deaths into a table showing how many people survive to each age.",
        details = "He printed the estimate in the Philosophical Transactions in 1693, using counts of deaths in Breslau from 1687 to 1691 that Caspar Neumann had collected. The table starts from a group of 1,000 births and shows how many are still alive at each later age, and it was used afterwards to reason about the price of annuities.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.PRACTICE,
        section = "Teaching",
        title = "Amman's method for teaching the deaf to speak",
        originalTitle = "Surdus loquens",
        author = "Johann Conrad Amman",
        year = 1692,
        summary = "A physician taught deaf pupils to speak by having them watch his mouth and feel his throat, then copy the movements until they could say words.",
        details = "Amman's book was printed in Amsterdam in 1692, although some histories give 1693. John Wallis reported the method in the Philosophical Transactions of 1698, and Amman extended it in his Dissertatio de loquela of 1700.",
        rare = true,
    ),

    entry(
        databaseId = 35,
        kind = Kind.PRACTICE,
        section = "Lotteries",
        title = "The Million Adventure lottery of 1694",
        originalTitle = "The Million Adventure",
        author = "Parliament of England",
        year = 1694,
        summary = "A state lottery of 1694 that sold tickets to raise a million pounds for the war against France.",
        details = "Tickets were sold in London, and the lottery was one of several means used to fund the war after the Revolution. The prize structure is not given in this record.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.PRACTICE,
        section = "Taxes",
        title = "The window tax of 1696",
        originalTitle = "The window tax of 1696",
        author = "Parliament of England",
        year = 1696,
        summary = "A tax on houses by the number of their windows, introduced in England in 1696 and collected for more than a century.",
        details = "Householders who wanted to lower the bill blocked up some windows, which is why many older houses still have blind openings. The tax was repealed in 1851, so it was in force for about 155 years.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.PRACTICE,
        section = "Medicine at work",
        title = "Ramazzini's study of workers' diseases",
        originalTitle = "De morbis artificum diatriba",
        author = "Bernardino Ramazzini",
        year = 1700,
        summary = "Ramazzini described the illnesses of many trades, from miners to scribes, and asked physicians to ask patients about their work.",
        details = "The book was printed in Modena in 1700 and expanded in later editions. It is often called the first systematic study of occupational disease, and it urged doctors to record a patient's trade in the case history.",
    ),

)
