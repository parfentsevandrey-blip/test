package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Notations and codes of 1850 to 1860: logic, quaternions, chemical bonds, polytopes and a telegraph alphabet. */
val db19Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 19,
        kind = Kind.LANGUAGE,
        section = "Logic notation",
        title = "Boole's algebra of logic",
        originalTitle = "An Investigation of the Laws of Thought, on Which are Founded the Mathematical Theories of Logic and Probabilities (1854)",
        author = "George Boole",
        year = 1854,
        summary = "Boole wrote logic as algebra in the symbols 1 and 0, so the product xy names the class of things that are both x and y.",
        details = "Boole first set out the method in a short pamphlet of 1847 and gave the full system in this 1854 book. Its key law, x squared equals x, says that a class combined with itself is unchanged, and the book treats probabilities with the same symbols.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.LANGUAGE,
        section = "Quaternions",
        title = "Hamilton's quaternion notation",
        originalTitle = "Lectures on Quaternions (1853)",
        author = "William Rowan Hamilton",
        year = 1853,
        summary = "Quaternions are sums a + bi + cj + dk with i squared = j squared = k squared = ijk = -1, a notation for rotations in space in which the product does not commute.",
        details = "Hamilton found the rule ijk = -1 in October 1843 near Dublin and spent the next decade developing it. The 1853 Lectures set out the full system, and quaternions were one of the first non-commutative algebras studied in detail.",
    ),

    entry(
        databaseId = 19,
        kind = Kind.LANGUAGE,
        section = "Chemical notation",
        title = "Couper's bond lines in chemical formulae",
        originalTitle = "Sur une nouvelle theorie chimique (1858)",
        author = "Archibald Scott Couper",
        year = 1858,
        summary = "Couper drew each chemical formula as atoms joined by dashes, one dash for each bond, so a formula showed how the atoms were linked.",
        details = "Couper and Kekule both published such structural formulae in 1858, and the priority between them is still disputed. The notation spread through organic chemistry and is the form of formula taught today.",
        rare = true,
    ),

    entry(
        databaseId = 19,
        kind = Kind.LANGUAGE,
        section = "Logic notation",
        title = "De Morgan's syllogistic notation",
        originalTitle = "Syllabus of a Proposed System of Logic (1860)",
        author = "Augustus De Morgan",
        year = 1860,
        summary = "De Morgan's Syllabus gave symbols for quantified predicates, so that syllogisms with 'some' and 'not' could be written and checked.",
        details = "De Morgan had begun quantifying predicates in his Formal Logic of 1847, and the 1860 Syllabus set out the notation in full. William Rowan Hamilton disputed the priority for quantifying the predicate, and the dispute was carried on in print.",
        rare = true,
    ),

    entry(
        databaseId = 19,
        kind = Kind.LANGUAGE,
        section = "Polytopes",
        title = "Schlafli's symbols for regular polytopes",
        originalTitle = "Theorie der vielfachen Kontinuitaet (written 1852, printed 1901)",
        author = "Ludwig Schlafli",
        year = 1852,
        summary = "Schlafli's symbol {p, q, r} names a regular polytope by the shape of its faces and by how they are arranged around each vertex and edge.",
        details = "Schlafli classified the regular polytopes in 1852, but the work was not printed until 1901, so the date of its first public form is disputed. The record uses the date of writing, which places the symbol in this decade.",
        rare = true,
    ),

    entry(
        databaseId = 19,
        kind = Kind.LANGUAGE,
        section = "Telegraph codes",
        title = "Gerke's revised Morse alphabet",
        originalTitle = "German telegraph alphabet of Gerke (1851)",
        author = "Friedrich Clemens Gerke",
        year = 1851,
        summary = "Gerke revised Morse's signs for German telegraphy, and his alphabet became the standard of the German-Austrian telegraph union.",
        details = "Accounts differ on the date of Gerke's revision: some give 1848, others 1851 for the modern alphabet and 1852 for its use across the union. The alphabet later served as a basis for the International Morse code.",
    ),
)
