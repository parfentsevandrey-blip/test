package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Tools of 1860 to 1870: the periodic table, integral tables, abstracts and catalogues that organised the decade's science. */
val db18Tools: List<RecordEntity> = listOf(

    entry(
        databaseId = 18,
        kind = Kind.TOOL,
        section = "Chemistry tables",
        title = "Mendeleev's periodic table of 1869",
        originalTitle = "The relation of the properties to the atomic weights of the elements (1869)",
        author = "Dmitri Mendeleev",
        year = 1869,
        summary = "The elements are arranged by atomic weight in rows, so that properties repeat at intervals, and the gaps mark elements that were still to be found.",
        details = "Mendeleev presented the table to the Russian Chemical Society in 1869, and his paper was printed that year. Lothar Meyer published a related table of the elements in 1864 and a fuller one in 1870, so the priority of the periodic arrangement is disputed. The gaps in the table were later used to predict the properties of unknown elements.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.TOOL,
        section = "Integral tables",
        title = "Bierens de Haan's tables of definite integrals",
        originalTitle = "Nouvelles tables d'integrales definies (1867)",
        author = "David Bierens de Haan",
        year = 1867,
        summary = "A large printed table of definite integrals, so that a reader could look up the value of many integrals instead of working each one out by hand.",
        details = "The Nouvelles tables appeared at Leiden in 1867, after a first volume issued at Amsterdam in 1858 and a supplement around 1864. This record uses the 1867 volume, which carries the Nouvelles title.",
        rare = true,
    ),

    entry(
        databaseId = 18,
        kind = Kind.TOOL,
        section = "Biography",
        title = "Poggendorff's biographical dictionary of the exact sciences",
        originalTitle = "Biographisch-literarisches Handwoerterbuch zur Geschichte der exacten Wissenschaften (1863)",
        author = "Johann Christian Poggendorff",
        year = 1863,
        summary = "A reference that lists scientists in mathematics, physics and chemistry with their publications, so that a reader can trace an author's work in print.",
        details = "The first volume appeared in 1863, and later editors continued the work into the next century. Entries give each author's papers and the journals in which they appeared, which made the dictionary a finding aid for the exact sciences.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.TOOL,
        section = "Abstracts",
        title = "Jahrbuch ueber die Fortschritte der Mathematik",
        originalTitle = "Jahrbuch ueber die Fortschritte der Mathematik, volume 1 (1868)",
        author = "Carl Ohrtmann and Felix Muller",
        year = 1868,
        summary = "A yearly review that reports the mathematical papers of the year in short notes, so that a reader can find work in a field without searching every journal.",
        details = "The first volume covers the literature of 1868 and was printed in Berlin by Georg Reimer, with its appearance dated to 1871, in three fascicles of 426 pages. Accounts differ on the founding year, 1868 or 1869, so this record uses the year of the first volume.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.TOOL,
        section = "Catalogues",
        title = "Royal Society Catalogue of Scientific Papers",
        originalTitle = "Catalogue of Scientific Papers, 1800 to 1863, volume 1 (1867)",
        author = "Royal Society of London",
        year = 1867,
        summary = "A catalogue that lists scientific papers by author and subject, so that researchers could find the published work on a question without searching journal runs.",
        details = "The first volume, covering authors from 1800 to 1863, is dated 1867, and later volumes continued the series in the following decades. It was one of the first attempts to index the scientific literature of a whole period by author.",
    ),

    entry(
        databaseId = 18,
        kind = Kind.TOOL,
        section = "Chemistry reference",
        title = "Watts's Dictionary of Chemistry",
        originalTitle = "A Dictionary of Chemistry and the Allied Branches of Other Sciences, first volume (1863)",
        author = "Henry Watts",
        year = 1863,
        summary = "A multi-volume dictionary of chemistry with an entry for each substance, process and chemist, which served English-speaking chemists as a standard reference.",
        details = "The first volume appeared in 1863, and the set was continued through the later 1860s with further volumes. Watts edited it, and it stood among the main chemical reference works of its time.",
    ),

)
