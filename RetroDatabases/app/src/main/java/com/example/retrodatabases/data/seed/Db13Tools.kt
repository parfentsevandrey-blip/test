package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1910 to 1920: printed tables, a shorthand machine, a card index, a handbook, a star catalogue and a code book. */
val db13Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 13,
        kind = Kind.TOOL,
        section = "Statistics",
        title = "Pearson's Tables for Statisticians and Biometricians",
        originalTitle = "Tables for Statisticians and Biometricians, Part I (Pearson, 1914)",
        author = "Karl Pearson",
        year = 1914,
        summary = "A book of printed tables of statistical functions, which statisticians looked up instead of working out each value by hand.",
        details = "The first part was published in 1914 by Cambridge University Press for the Biometric Laboratory at University College London. A copy in the catalogues has 83 roman-numbered pages and 143 further pages. Some catalogues date the second part to 1931, so this record gives the year of the first part.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.TOOL,
        section = "Shorthand",
        title = "The stenotype machine",
        originalTitle = "Stenotype, a chorded shorthand keyboard (Ireland, 1910)",
        author = "Ward Stone Ireland",
        year = 1910,
        summary = "A keyboard on which the reporter presses several keys together to write a whole syllable at once, so that a transcript can keep pace with speech.",
        details = "Ireland, a former court reporter, filed patent applications in April 1910, according to one account. Several sources give 1911 as the year the machine was introduced, and Wikipedia dates the forerunner of modern machines to about 1913. Court reporters took to the machine for its accuracy.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.TOOL,
        section = "Card indexes",
        title = "The Mundaneum card index",
        originalTitle = "Repertoire Bibliographique Universel and the Palais Mondial (Otlet and La Fontaine, 1910)",
        author = "Paul Otlet and Henri La Fontaine",
        year = 1910,
        summary = "A card index of world knowledge in Brussels, in which every card was filed by subject under a decimal classification, so that a question could be answered from the cards.",
        details = "The index began in 1895 as a bibliography on cards, and Wikipedia dates the founding of the Mundaneum to 1910. Public access ended in 1934, and in 1940 part of the holdings was damaged. Mons has hosted the legacy since 1998. The Mundaneum's own timeline credits Otlet with devising the Universal Decimal Classification.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.TOOL,
        section = "Handbooks",
        title = "The chemistry and physics handbook of 1913",
        originalTitle = "First edition of the Chemical Rubber Company handbook, 116 pages (1913)",
        author = "Chemical Rubber Company, Cleveland",
        year = 1913,
        summary = "A pocket reference of tables and constants that a rubber company handed out with its aprons, the start of a handbook that is still published.",
        details = "The 1913 debut ran to 116 pages. Sources conflict on whether its title was Rubber Handbook or Handbook of Chemistry and Physics. Most accounts put the start of the Cleveland firm in 1903, and the firm took the CRC Press name in 1973.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.TOOL,
        section = "Astronomy",
        title = "Henry Draper Catalogue",
        originalTitle = "Henry Draper Catalogue of stellar spectra, first volume (Harvard College Observatory, 1918)",
        author = "Harvard College Observatory, directed by Edward Charles Pickering",
        year = 1918,
        summary = "A catalogue of stellar spectra in nine volumes, in which the spectral classes run from O for the hottest stars to M for the coolest, as Annie Jump Cannon arranged them.",
        details = "The first volume appeared in 1918 and the ninth and last in 1924. Pickering's text in the first volume says the classification began on 2 October 1911 and was practically complete by 30 September 1915. Cannon and Antonia Maury did most of the classification work.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.TOOL,
        section = "Telegraphy",
        title = "ABC Telegraphic Code, sixth edition",
        originalTitle = "ABC Universal Commercial Electric Telegraphic Code, sixth edition (1920)",
        author = "W. Clauson-Thue",
        year = 1920,
        summary = "A commercial telegraph code book whose code words stood for whole phrases, in the sixth edition of a series that began in the 1870s.",
        details = "The series ran to seven numbered editions between 1873 and 1936. The fifth edition appeared in 1901, and the sixth in 1920, according to a codebook study that cites both dates. Businesses used such code books to cut the cost of cabled and telegraphed messages.",
    ),
)
