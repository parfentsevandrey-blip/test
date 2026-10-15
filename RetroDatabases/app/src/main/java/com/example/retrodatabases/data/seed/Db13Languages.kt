package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Languages of 1910 to 1920: logic notations and telegraph code books, each with its date and purpose. */
val db13Languages: List<RecordEntity> = listOf(

    entry(
        databaseId = 13,
        kind = Kind.LANGUAGE,
        section = "Logic",
        title = "Principia Mathematica notation",
        originalTitle = "Principia Mathematica, volumes 1 to 3 (Whitehead and Russell, 1910-1913)",
        author = "Alfred North Whitehead and Bertrand Russell",
        year = 1910,
        summary = "A notation for logic in which negation, disjunction and assertion are the starting ideas, and implication is defined from them.",
        details = "The three volumes appeared between 1910 and 1913. The first volume sets up propositional logic and its symbols, and the book aims to keep its basic concepts, axioms and rules as few as possible. The 1925 edition kept the 1910 text largely as it was and added an introduction and appendices that reduce the number of primitive ideas.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.LANGUAGE,
        section = "Radiotelegraphy",
        title = "The Q code of radiotelegraphy",
        originalTitle = "Service regulations of the London radiotelegraph convention, 1912",
        author = "International radiotelegraph conference, London",
        year = 1912,
        summary = "Three-letter code groups that begin with Q let ships and coast stations send common messages, such as a report of interference or a request to reply, in any language.",
        details = "Britain compiled the first set of codes around 1909 for licensed ships and coastal stations. The London service regulations of 1912 listed forty-five codes, which spread internationally because they bridged language barriers between operators. Amateur radio still uses several of them, including QRM for interference and QSO for a two-way contact.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.LANGUAGE,
        section = "Diplomatic codes",
        title = "German diplomatic code 0075",
        originalTitle = "Code 0075, the code book behind the Zimmermann telegram of January 1917",
        author = "German Foreign Office",
        year = 1917,
        summary = "A numbered code book of 10,000 entries, from 0000 to 9999, used for the telegram of 16 January 1917 that proposed a military alliance with Mexico.",
        details = "British codebreakers in Room 40 of the Admiralty, a unit formed in October 1914, worked on the telegram. The entries were numbered 0000 to 9999 but were out of sequence. The analysts recovered only part of the text from the traffic they had, although the sender's signature was legible. Sources differ on whether the code was a simple substitution or a two-part code, so this record gives only the numbering.",
        rare = true,
    ),

    entry(
        databaseId = 13,
        kind = Kind.LANGUAGE,
        section = "Logic",
        title = "The Sheffer stroke",
        originalTitle = "A set of five independent postulates for Boolean algebras, with application to logical constants (Sheffer, 1913)",
        author = "Henry Maurice Sheffer",
        year = 1913,
        summary = "One binary connective, the stroke that is false only when both of its inputs are true, is enough to define every other connective of logic.",
        details = "Sheffer published the result in volume 14 of the Transactions of the American Mathematical Society, pages 481-488. The stroke notation gained wide recognition only after the 1925 edition of Principia Mathematica adopted it. The NAND gate of later digital circuits computes the same operation.",
        rare = true,
    ),

    entry(
        databaseId = 13,
        kind = Kind.LANGUAGE,
        section = "Modal logic",
        title = "Strict implication and the 1918 Survey",
        originalTitle = "A Survey of Symbolic Logic (Lewis, 1918)",
        author = "Clarence Irving Lewis",
        year = 1918,
        summary = "Strict implication says that one proposition cannot be true while another is false, and its calculus started the branch of logic called modal logic.",
        details = "Lewis objected to the material implication used in Principia Mathematica, and he developed the calculus of strict implication in papers from 1912 to 1914. The 1918 Survey set out the calculus at book length, with axiom systems for strict implication. The systems named S1 to S5 were worked out from these axioms and presented in the 1932 Symbolic Logic.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.LANGUAGE,
        section = "Telegraphy",
        title = "The Simplex Standard Telegraphic Code",
        originalTitle = "The Simplex Standard Telegraphic Code, a commercial code book",
        author = "Edward W. Reiss",
        year = 1911,
        summary = "A commercial telegraph code book of 591 pages, published in New York in 1911, of the kind that businesses used to cut the cost of telegrams.",
        details = "The Simplex Code Company of New York published the book in 1911, and the copy in the catalogue record has 591 pages. Telegraph messages were priced by length, so a code word that stood for a whole phrase saved money on every message. The catalogue record gives no information on the vocabulary, so this record stays with the publication facts.",
        rare = true,
    ),
)
