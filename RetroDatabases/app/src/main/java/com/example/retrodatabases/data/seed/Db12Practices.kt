package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1920 to 1930: how experiments, data and radio were run, from random plots to short-wave contacts. */
val db12Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 12,
        kind = Kind.PRACTICE,
        section = "Experiments",
        title = "Randomised field experiments",
        originalTitle = "The arrangement of field experiments (Fisher, 1926)",
        author = "R. A. Fisher",
        year = 1926,
        summary = "The practice of assigning field plots to treatments at random and grouping them into blocks, argued for in a 1926 paper.",
        details = "Fisher's paper appeared in the Journal of the Ministry of Agriculture in 1926, on pages 503 to 513 by one catalogue and 503 to 515 by another. Speed's introduction credits Fisher with bringing in blocking and randomisation in the years after 1919. The paper is often cited as an early argument for randomisation.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.PRACTICE,
        section = "Statistics",
        title = "Yule's nonsense correlation",
        originalTitle = "Why do we sometimes get nonsense-correlations between time-series? (Yule, 1926)",
        author = "G. Udny Yule",
        year = 1926,
        summary = "A warning that two series with trends can show a high correlation with no causal link between them.",
        details = "The paper was Yule's presidential address, delivered in November 1925 and printed in the Journal of the Royal Statistical Society, volume 89, in January 1926. His example correlated the share of Church of England marriages with mortality from 1866 to 1911, and got about 0.95. His random-walk experiments gave correlations that scattered widely and often reached large values, and the questions they raised stayed open for decades.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.PRACTICE,
        section = "Workplace studies",
        title = "Hawthorne lighting experiments",
        originalTitle = "Artificial-lighting experiments at the Hawthorne Works (1924 to 1927)",
        author = "National Research Council with Western Electric",
        year = 1924,
        summary = "Lighting experiments at the Hawthorne Works that began in 1924 and led the team to suspect that their own observation changed output.",
        details = "Investigators wanted to know whether better lighting raised output, and they changed the lighting for different study groups. Output rose whether the light went up or down, in test and comparison groups alike. In 1927 the team concluded that other factors mattered and that their own oversight might be part of the effect, and it halted the lighting work. Mayo joined as a consultant in 1927. Sources give 1932 or 1933 as the end of the wider series.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.PRACTICE,
        section = "Radio",
        title = "Amateur short-wave transatlantic contact",
        originalTitle = "Station 1BCG to Ardrossan, 11 December 1921",
        author = "Station 1BCG, Greenwich, Connecticut",
        year = 1921,
        summary = "The first short-wave crossing of the Atlantic by amateurs, heard at Ardrossan in Scotland on 11 December 1921.",
        details = "Station 1BCG in Greenwich, Connecticut, sent signals that were heard at Ardrossan, Scotland, on 11 December 1921. The Scottish reception is dated 12 December locally. Members of the Radio Club of America ran the transmitter, among them Edwin Armstrong. The signals came in around 230 to 235 metres, roughly where the broadcast band sits today. The practice of working on short waves opened long-distance amateur radio.",
    ),

    entry(
        databaseId = 12,
        kind = Kind.PRACTICE,
        section = "Telegraph signalling",
        title = "Nyquist's telegraph transmission criterion",
        originalTitle = "Certain Topics in Telegraph Transmission Theory (Nyquist, 1928)",
        author = "Harry Nyquist",
        year = 1928,
        summary = "The 1928 analysis that found the minimum frequency range needed for a given telegraph signalling speed, the criterion behind the Nyquist rate.",
        details = "The paper appeared in the Transactions of the AIEE, volume 47, pages 617 to 644, in April 1928. Nyquist treated distortion through steady-state characteristics and worked out the minimum frequency range for a given signalling speed. He compared single- and double-sideband carrier transmission and concluded that carrier telegraphy needs twice the range of direct-current telegraphy, all else equal. Its bandwidth and sampling material is now known as the Nyquist sampling theorem.",
    ),
)
