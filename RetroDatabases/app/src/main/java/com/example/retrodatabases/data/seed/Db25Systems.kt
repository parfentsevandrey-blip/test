package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1790 to 1800: an optical telegraph, a decimal system of measures, a national Mint, a patent law and a survey. */
val db25Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 25,
        kind = Kind.SYSTEM,
        section = "Optical telegraphs",
        title = "Chappe's telegraph from Paris to Lille",
        originalTitle = "Telegraphe, the optical line of Claude Chappe (1794)",
        author = "Claude Chappe",
        year = 1794,
        summary = "A chain of hilltop towers carried coded signs from Paris to Lille, the first long optical telegraph line in service, with a two-arm semaphore on each tower.",
        details = "Chappe received a commission to build the line in 1793, after test results were shown to the Convention, and the line was in service by 1794. Accounts differ on its length, with figures from about 200 to 230 kilometres, and the towers stood roughly ten to twenty kilometres apart. The retaking of Conde-sur-l'Escaut was sent along the line in August 1794, and accounts differ on the day of the first message.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.SYSTEM,
        section = "Measures",
        title = "The metric system of measures",
        originalTitle = "Systeme metrique decimal (1795)",
        author = "French National Convention",
        year = 1795,
        summary = "A decimal system of units in which the metre and the gram were fixed from the size of the Earth and from water, with multiples and parts in tens.",
        details = "The metre was defined in 1791 as one ten-millionth of the distance from the pole to the equator along the Paris meridian. The law of 7 April 1795 established the decimal system in France, and platinum standards of the metre and the kilogram were deposited in 1799.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.SYSTEM,
        section = "Coinage",
        title = "The first US Mint and Coinage Act",
        originalTitle = "An Act establishing a Mint and regulating the coins of the United States (1792)",
        author = "Congress of the United States",
        year = 1792,
        summary = "The act made the dollar the unit of money, fixed silver to gold at 15 to 1 by weight, and set up a national Mint at Philadelphia.",
        details = "Congress passed the Coinage Act on 2 April 1792, and the Mint opened in Philadelphia that year. The silver dollar was set at 371 and a quarter grains of pure silver, and the gold eagle at 247 and a half grains of pure gold.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.SYSTEM,
        section = "Patents",
        title = "The first US patent act",
        originalTitle = "An Act to promote the progress of useful arts (1790)",
        author = "Congress of the United States",
        year = 1790,
        summary = "The first federal patent law granted inventors exclusive rights for a term of years, after each application was reviewed by three officers of government.",
        details = "The act was signed on 10 April 1790 and named the Secretary of State, the Secretary of War and the Attorney General as the review board. The first patent under it was issued on 31 July 1790 to Samuel Hopkins for a method of making potash.",
    ),

    entry(
        databaseId = 25,
        kind = Kind.SYSTEM,
        section = "Surveys",
        title = "The Ordnance Survey of Britain",
        originalTitle = "Principal triangulation of Great Britain (1791)",
        author = "Board of Ordnance",
        year = 1791,
        summary = "A national survey that linked a chain of measured triangles to a baseline on Hounslow Heath, so that maps of the country could share one framework.",
        details = "The baseline on Hounslow Heath was measured in 1784 under William Roy. The Board of Ordnance carried the triangulation on from 1791, and the chain fixed the positions of points across southern England for later maps.",
    ),
)
