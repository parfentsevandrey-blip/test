package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages, notations and codes of 1870 to 1880: a constructed world language, a telegraph codebook and symbolic notations. */
val db17Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 17,
        kind = Kind.LANGUAGE,
        section = "Auxiliary languages",
        title = "Volapuk, the 1879 world language",
        originalTitle = "Volapuk: Die Weltsprache (1880)",
        author = "Johann Martin Schleyer",
        year = 1879,
        summary = "A constructed world language whose words are built from roots taken from European languages, and whose author linked its origin to a dream.",
        details = "An outline appeared in May 1879 in Sionsharfe, a Catholic poetry journal edited by the priest Schleyer, and the first grammar came out in 1879 as a supplement. The full German book Volapuk: Die Weltsprache followed in 1880. Accounts differ on how long the construction took, giving 1879 to 1880 or 1879 to 1884.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.LANGUAGE,
        section = "Telegraph codes",
        title = "Phillips Code for press telegraphy",
        originalTitle = "Phillips Code",
        author = "Walter P. Phillips",
        year = 1879,
        summary = "A codebook of short words and abbreviations that let newspaper telegraphers send press reports faster.",
        details = "Walter P. Phillips, who held a senior post with the Associated Press in New York, is credited with the code, which reworked abbreviations that telegraphers already used. The code is dated to about 1879, and accounts differ on the exact year of the first edition. Later editions were issued until a final update in 1925.",
        rare = true,
    ),

    entry(
        databaseId = 17,
        kind = Kind.LANGUAGE,
        section = "Chemical notation",
        title = "Van 't Hoff's three-dimensional formulas",
        originalTitle = "Voorstel tot uitbreiding der tegenwoordig in de scheikunde gebruikte structuurformules in de ruimte (1874)",
        author = "Jacobus Henricus van 't Hoff",
        year = 1874,
        summary = "A proposal to draw the four bonds of a carbon atom in space, so that a carbon with four different groups has two mirror-image forms.",
        details = "Van 't Hoff printed the proposal as a pamphlet in 1874, and Joseph Achille Le Bel published a closely related paper in the same year. The tetrahedral drawing gave a reason for pairs of compounds that differ only as mirror images and turn polarised light in opposite directions.",
    ),

    entry(
        databaseId = 17,
        kind = Kind.LANGUAGE,
        section = "Algebra notation",
        title = "Tait's quaternion treatise, second edition",
        originalTitle = "An Elementary Treatise on Quaternions, second edition (1873)",
        author = "Peter Guthrie Tait",
        year = 1873,
        summary = "A textbook that set out Hamilton's quaternions with symbols for products of vectors, which physicists read for decades.",
        details = "The first edition appeared in 1867 and the second in 1873. The vector notation that grew out of quaternions was later displaced by the vector analysis of Gibbs and Heaviside.",
        rare = true,
    ),

    entry(
        databaseId = 17,
        kind = Kind.LANGUAGE,
        section = "Kinematic notation",
        title = "Reuleaux's kinematic pairs and chains",
        originalTitle = "Theoretische Kinematik (1875)",
        author = "Franz Reuleaux",
        year = 1875,
        summary = "A set of names and symbols for the joints and linked bars of a machine, so that a mechanism could be described before it was built.",
        details = "Reuleaux's Theoretische Kinematik appeared in 1875. It classified the pairs of elements that make up a joint and the chains they form, and it became a standard reference for the design of linkages.",
        rare = true,
    ),

    entry(
        databaseId = 17,
        kind = Kind.LANGUAGE,
        section = "Logic notation",
        title = "Schroder's calculus of logic, the Operationskreis",
        originalTitle = "Der Operationskreis des Logikkalkuls (1877)",
        author = "Ernst Schroder",
        year = 1877,
        summary = "A calculus in which classes and their combinations are written as symbols that obey rules like those of algebra.",
        details = "Schroder built the calculus on the algebra of logic that Boole had begun in 1854. He wrote the rules for classes and for their operations in this symbolic form, which turned the algebra of logic into a working calculus.",
        rare = true,
    ),
)
