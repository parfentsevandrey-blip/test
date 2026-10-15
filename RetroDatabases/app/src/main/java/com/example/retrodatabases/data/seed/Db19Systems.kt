package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Telegraph and signalling systems of 1850 to 1860: a German union, two cables, a fire-alarm network and an Indian line. */
val db19Systems: List<RecordEntity> = listOf(

    entry(
        databaseId = 19,
        kind = Kind.SYSTEM,
        section = "Telegraph union",
        title = "German-Austrian telegraph union",
        originalTitle = "Deutsch-Oesterreichischer Telegraphenverein (1850)",
        author = "The telegraph administrations of Germany and Austria",
        year = 1850,
        summary = "The telegraph union linked the German states and Austria in one system with common rules and one telegraph alphabet.",
        details = "The union was formed in 1850 at Dresden, so that messages could cross the member states on shared terms. Its common alphabet was Gerke's, and its rules were an early step toward later international telegraph agreements.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.SYSTEM,
        section = "Submarine cables",
        title = "Dover to Calais telegraph cable",
        originalTitle = "Submarine telegraph between England and France (1851)",
        author = "John Watkins Brett and the Submarine Telegraph Company",
        year = 1851,
        summary = "A cable across the Strait of Dover carried telegraph messages between England and France after an earlier cable of 1850 was soon broken.",
        details = "The first cable was laid in 1850, and an armoured cable laid in 1851 was the one that carried messages reliably. The Channel cables showed that a wire insulated in gutta-percha could be laid across the sea floor.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.SYSTEM,
        section = "Fire alarm systems",
        title = "Boston municipal fire-alarm telegraph",
        originalTitle = "Municipal fire-alarm telegraph of Boston (1852)",
        author = "William Francis Channing and Moses G. Farmer",
        year = 1852,
        summary = "Boston's municipal fire-alarm telegraph carried alarms by wire from street boxes to a central office and to bells, and it began operating in April 1852.",
        details = "The city approved funds in 1851 at Channing's urging. Accounts differ on the first day of operation, given as 28 April or 29 April 1852, and on the number of alarm bells, so the record does not give one.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.SYSTEM,
        section = "Transatlantic cable",
        title = "Atlantic telegraph cable of 1858",
        originalTitle = "Atlantic telegraph cable (1858)",
        author = "Cyrus West Field and the Atlantic Telegraph Company",
        year = 1858,
        summary = "The first transatlantic cable linked Ireland and Newfoundland in August 1858, and its signal failed within weeks, so no working cable crossed until 1866.",
        details = "Messages crossed the cable in August 1858, including an exchange between Queen Victoria and President Buchanan, and the signal faded in September. A second cable laid in 1866 gave the first lasting service, so the 1858 line was an early attempt rather than a working service.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.SYSTEM,
        section = "Telegraph networks",
        title = "Indian telegraph, Calcutta to Agra",
        originalTitle = "Telegraph line from Calcutta to Agra (1854)",
        author = "Government of India under Lord Dalhousie",
        year = 1854,
        summary = "The first Indian telegraph line, from Calcutta to Agra, opened in 1854 and carried official messages between the two cities.",
        details = "Dalhousie's government began building the telegraph in the early 1850s, and the first line linked Calcutta with Agra. The network grew in the following years to serve government and commercial traffic.",
    ),
)
