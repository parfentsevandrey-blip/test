package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Systems of 1690 to 1700: the Bank of England, a Scottish colony company, a Scottish bank, a Virginia college and a London price sheet. */
val db35Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 35,
        kind = Kind.SYSTEM,
        section = "Banking",
        title = "The Bank of England's charter of 1694",
        originalTitle = "The Governor and Company of the Bank of England",
        author = "William Paterson and the subscribers",
        year = 1694,
        summary = "The Bank of England was chartered in July 1694 to lend money to the government, on capital subscribed by private investors.",
        details = "The charter is dated 27 July 1694, and the subscription reached 300,000 pounds on the opening day and the full 1.2 million pounds within a fortnight. The Bank lent the sum to the government at eight per cent a year and received a separate fee of 4,000 pounds a year.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.SYSTEM,
        section = "Trade and empire",
        title = "The Company of Scotland's Darien scheme",
        originalTitle = "Company of Scotland Trading to Africa and the Indies",
        author = "Company of Scotland directors",
        year = 1695,
        summary = "A Scottish trading company chartered in 1695 to settle a colony on the isthmus of Darien, which failed within a few years.",
        details = "The Scottish Parliament passed the statute on 26 June 1695, and the planners aimed for 600,000 pounds of capital. Investors were invited in February 1696 to commit 400,000 pounds, a goal reached about six months later, but the colony at Darien was abandoned in 1700.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.SYSTEM,
        section = "Education",
        title = "The College of William and Mary's royal charter",
        originalTitle = "Royal charter of the College of William and Mary",
        author = "William III and Mary II",
        year = 1693,
        summary = "A royal charter of February 1693 founded a college in Virginia, named after the king and queen who signed it.",
        details = "The charter was granted on 8 February 1693, and building work at Williamsburg began in 1695. It was one of the earliest colleges in the English colonies, after Harvard.",
    ),

    entry(
        databaseId = 35,
        kind = Kind.SYSTEM,
        section = "Markets",
        title = "John Castaing's Course of the Exchange",
        originalTitle = "The Course of the Exchange and other things",
        author = "John Castaing",
        year = 1698,
        summary = "A weekly printed sheet of London stock and exchange prices, issued from a coffee house in Exchange Alley.",
        details = "Castaing began publishing the list in 1698 at Jonathan's coffee house, and it gave stock prices and the rates of exchange with foreign cities. The sheet became a regular source of market news for merchants and brokers in the City of London.",
        rare = true,
    ),

    entry(
        databaseId = 35,
        kind = Kind.SYSTEM,
        section = "Banking",
        title = "The Bank of Scotland",
        originalTitle = "The Governor and Company of the Bank of Scotland",
        author = "Scottish Parliament",
        year = 1695,
        summary = "A bank founded in Edinburgh in 1695, in the same year that the Scottish Parliament chartered the Darien company.",
        details = "The Scottish Parliament counted the bank among its 1695 measures for the country's trade and money, which were troubled that year. The bank remained in business long after the Darien company had collapsed.",
        rare = true,
    ),

)
