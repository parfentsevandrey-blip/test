package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1600 to 1610: two chartered trading companies, a municipal bank, a reopened library and an academy of the lynx. */
val db44Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 44,
        kind = Kind.SYSTEM,
        section = "Trade",
        title = "The Dutch East India Company, chartered in 1602",
        originalTitle = "Vereenigde Oostindische Compagnie",
        author = "The States General of the Dutch Republic",
        year = 1602,
        summary = "The Dutch East India Company was chartered in March 1602 as one company with a monopoly of Dutch trade east of the Cape of Good Hope.",
        details = "The States General granted the charter on 20 March 1602, merging several earlier trading companies into one. Its capital came from investors who bought shares, and its business was run by chambers in several Dutch towns.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.SYSTEM,
        section = "Banking",
        title = "The Bank of Amsterdam, opened in 1609",
        originalTitle = "Amsterdamsche Wisselbank",
        author = "The city of Amsterdam",
        year = 1609,
        summary = "The Bank of Amsterdam let merchants keep balances in one standard unit of account and paid by transfers from one account to another.",
        details = "The bank's opening day is generally given as 31 January 1609, and its premises came from an older town hall. Its bank money often stood at a premium over the coins in circulation, a premium called agio, which made its accounts a safe place to keep money.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.SYSTEM,
        section = "Libraries",
        title = "The Bodleian Library at Oxford, reopened in 1602",
        originalTitle = "Bibliotheca Bodleiana",
        author = "Sir Thomas Bodley",
        year = 1602,
        summary = "Thomas Bodley refitted the library of the University of Oxford and reopened it in 1602, with books he had gathered and money he had given.",
        details = "The library opened on 8 November 1602, and it became one of the great research libraries of the English-speaking world. Its books were kept on shelves under shelfmarks, the system that the library's printed catalogue of 1605 depended on.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.SYSTEM,
        section = "Learned societies",
        title = "The Accademia dei Lincei, the lynx academy",
        originalTitle = "Accademia dei Lincei",
        author = "Federico Cesi",
        year = 1603,
        summary = "Federico Cesi founded the Accademia dei Lincei in Rome in 1603 with a few friends, a scientific academy named for the sharp eyes of the lynx.",
        details = "Cesi was a young nobleman, and the academy gathered naturalists and mathematicians who met to study nature. Galileo became a member in 1611, and the members studied plants, animals and the new instruments of the time.",
    ),

    entry(
        databaseId = 44,
        kind = Kind.SYSTEM,
        section = "Trade",
        title = "The English East India Company's charter of 1600",
        originalTitle = "The Governor and Company of Merchants of London trading into the East Indies",
        author = "Queen Elizabeth I",
        year = 1600,
        summary = "Queen Elizabeth I granted the English East India Company its charter at the very end of 1600, a monopoly of English trade with the East Indies.",
        details = "The charter was dated 31 December 1600 and ran for fifteen years at first, with the company's first voyages sailing in 1601. It served as a model for the chartered trading companies that followed in Europe.",
    ),

)
