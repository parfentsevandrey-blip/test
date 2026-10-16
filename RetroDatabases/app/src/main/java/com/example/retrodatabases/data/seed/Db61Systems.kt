package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1430 to 1440: an English voting rule, a Florentine council that recalled Cosimo, a Norman university, a general council and the Korean royal annals. */
val db61Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 61,
        kind = Kind.SYSTEM,
        section = "Councils",
        title = "The Council of Basel, convened in 1431",
        originalTitle = "Concilium Basiliense",
        author = "The council fathers and the pope",
        year = 1431,
        summary = "The Council of Basel met in 1431 to reform the church, and it went on until 1449, long after the pope had tried to close it.",
        details = "Sources differ on the opening day in July 1431, and the pope tried to adjourn the council in December 1431, citing thin attendance and war. The assembly refused to disperse, moved to Lausanne in 1448, and ended on 25 April 1449.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.SYSTEM,
        section = "Voting",
        title = "The forty-shilling freehold rule for county voters, 1430",
        originalTitle = "Statute of 1430 on county elections",
        author = "The English Parliament",
        year = 1430,
        summary = "A statute of 1430 limited the vote in English county elections to freeholders whose land was worth at least forty shillings a year.",
        details = "The rule set a property test for county electors, and it remained the basis of the county vote for a long time afterwards. Historians dispute how strictly sheriffs checked the land values, so accounts differ on how many people the rule shut out in practice.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.SYSTEM,
        section = "Government",
        title = "The Florentine balia of 1434, which recalled Cosimo de' Medici",
        originalTitle = "Balia del 1434",
        author = "The Florentine signoria and its supporters",
        year = 1434,
        summary = "A balia, a special body given full powers, was set up in Florence in 1434, and it recalled Cosimo de' Medici from exile and reshaped the city's councils.",
        details = "The balia was granted in the autumn of 1434, and Cosimo returned from exile soon afterwards. Accounts differ on how many offices the new government replaced, and most histories name its changes to the lists of office holders as its lasting effect.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.SYSTEM,
        section = "Universities",
        title = "The University of Caen, founded in 1432",
        originalTitle = "Universite de Caen",
        author = "The English regime in Normandy, under Henry VI",
        year = 1432,
        summary = "The University of Caen is usually dated to 1432, when the English government in Normandy gave the town a studium with the rights of a university.",
        details = "The founding date of 1432 is the one most histories give, and the university's early teaching was in law and the arts. Accounts differ on how much the English regime planned the foundation and how much it owed to the town's own schools.",
    ),

    entry(
        databaseId = 61,
        kind = Kind.SYSTEM,
        section = "Royal records",
        title = "The Joseon royal annals, kept for the reign of Sejong",
        originalTitle = "Sejong Sillok",
        author = "The royal historians of Joseon Korea",
        year = 1437,
        summary = "The royal annals of King Sejong's reign recorded the making of new instruments, and the record for 1437 covers an instrument that told time by sun and stars.",
        details = "The annals were kept by the court's historians and written up after the reign, so their dates are the dates the court chose to record. The 1437 entry credits Yi Cheon and Jang Yeong-sil with leading the work, and the annals are one of the main sources for Sejong's instruments.",
    ),

)
