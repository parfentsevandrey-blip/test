package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1900 to 1910: classification schemes, catalogues and reference tables that organised the decade's knowledge. */
val db14Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 14,
        kind = Kind.TOOL,
        section = "Classification",
        title = "Universal Decimal Classification",
        originalTitle = "Manuel du repertoire bibliographique universel (1905)",
        author = "Paul Otlet and Henri La Fontaine",
        year = 1905,
        summary = "A subject classification with numeric codes and connecting symbols, so one book can carry codes for several subjects at once.",
        details = "The scheme was built on Dewey's decimal classification and added auxiliary tables and symbols for combining subjects. The first French edition is dated 1905, although one source says the first installments came out from 1899 to 1905.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.TOOL,
        section = "Classification",
        title = "Library of Congress Classification",
        originalTitle = "Library of Congress classification schedules, first printed 1901",
        author = "J. C. M. Hanson and Charles Martel",
        year = 1901,
        summary = "Letter and number call numbers, built class by class, that give each library book its place on the shelf.",
        details = "Hanson and Martel built the scheme, and the first printed schedules, for American history, appeared in 1901. Some sources date the start of the work to 1897 and others to 1901, and the first published outline appeared in 1904.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.TOOL,
        section = "Subject headings",
        title = "Library of Congress Subject Headings",
        originalTitle = "Subject headings for use in dictionary catalogs (1909)",
        author = "Library of Congress Catalog Division",
        year = 1909,
        summary = "A list of approved subject terms for cataloguing, so that a book on one topic is filed under the same heading by every librarian.",
        details = "The first printed list is dated 1909, and a HathiTrust record gives the first-edition volumes as 1909 and 1914, with a second edition in 1919. The list was built for dictionary catalogues, which mix subject and author entries in one alphabet.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.TOOL,
        section = "Abstracts",
        title = "Chemical Abstracts",
        originalTitle = "Chemical Abstracts, volume 1 (1907)",
        author = "American Chemical Society",
        year = 1907,
        summary = "A printed service of short abstracts of chemistry papers, started by the American Chemical Society so that chemists could find work quickly.",
        details = "The first issue appeared in January 1907, with William A. Noyes Sr. as editor. Ohio State hosted the service from 1909, and it continues today as CAS, which belongs to the society.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.TOOL,
        section = "Reference tables",
        title = "Landolt-Bornstein physical and chemical tables",
        originalTitle = "Physikalisch-chemische Tabellen, third edition (1905)",
        author = "Hans Landolt and Richard Bornstein",
        year = 1905,
        summary = "A reference set of measured constants for physics and chemistry, revised edition by edition, which scientists consulted for decades.",
        details = "The first edition appeared in 1883 with Springer as publisher, and the third edition came out in 1905. Later editions and supplements grew the set into a series of many volumes.",
    ),

    entry(
        databaseId = 14,
        kind = Kind.TOOL,
        section = "Abstracts",
        title = "International Catalogue of Scientific Literature",
        originalTitle = "International Catalogue of Scientific Literature, first annual volumes (1902)",
        author = "Royal Society, for an international council",
        year = 1902,
        summary = "A catalogue of scientific papers from many countries, indexed by subject and produced by an international council under the Royal Society.",
        details = "The catalogue was arranged in seventeen sciences, each with its own subject index, and the first volumes were reviewed in 1902. Its records began with the literature of 1901.",
        rare = true,
    ),
)
