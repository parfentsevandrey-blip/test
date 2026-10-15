package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Practices of 1650 to 1660: a register act, a London coffee house, a Commonwealth newsbook, an anagram that hid a discovery and Pascal's pamphlet letters. */
val db39Practices: List<RecordEntity> = listOf(

    entry(
        databaseId = 39,
        kind = Kind.PRACTICE,
        section = "Records",
        title = "The 1653 act for registers of marriages, births and burials",
        originalTitle = "An Act for the Better Registering of Marriages, Births and Burials",
        author = "Parliament of the Commonwealth",
        year = 1653,
        summary = "An act of 1653 ordered that marriages, births and burials be registered, a new state demand on parish and civil record-keeping.",
        details = "Marriages were to be made before a justice and entered in a register, and the scheme lapsed when the monarchy returned in 1660. The record gives the act's date and its title, and it shows how a government used registers to count its people.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.PRACTICE,
        section = "Public life",
        title = "Pasqua Rosee's coffee house in London",
        originalTitle = "Pasqua Rosee's coffee house in London",
        author = "Pasqua Rosee",
        year = 1652,
        summary = "A coffee house opened in London in 1652, one of the first places where men met over a shared table to read news and talk about learned matters.",
        details = "Accounts place the first London coffee house in St Michael's Alley in 1652, while an Oxford coffee house of 1650 is sometimes named as the first in England, so the dates differ. The new houses became meeting places for merchants and natural philosophers, and their talk fed the learned societies that came later.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.PRACTICE,
        section = "Public life",
        title = "Mercurius Politicus, the Commonwealth's weekly newsbook",
        originalTitle = "Mercurius Politicus",
        author = "Marchamont Nedham",
        year = 1650,
        summary = "Nedham's weekly newsbook, begun in 1650, carried the government's news and opinion to readers in London and beyond.",
        details = "The paper ran from 1650 to 1660 and was the official voice of the Commonwealth for much of that time, with Nedham as its editor. Its weekly issues set a regular rhythm of printed news, a practice that later newspapers followed.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.PRACTICE,
        section = "Secrecy",
        title = "Huygens's anagram to claim the ring of Saturn",
        originalTitle = "Huygens's anagram to claim the ring of Saturn",
        author = "Christiaan Huygens",
        year = 1656,
        summary = "Huygens published a scrambled Latin sentence to claim his discovery of a ring around Saturn, keeping the answer back until he was ready.",
        details = "Accounts differ on whether the anagram appeared in 1655 or 1656, and its answer was printed only in 1659 in the Systema Saturnium. The answer says that a thin flat ring encircles the planet, touches it nowhere and is tilted to the ecliptic.",
    ),

    entry(
        databaseId = 39,
        kind = Kind.PRACTICE,
        section = "Publishing",
        title = "Pascal's Provincial Letters as a series of pamphlets",
        originalTitle = "Lettres provinciales",
        author = "Blaise Pascal",
        year = 1656,
        summary = "Pascal's eighteen letters, issued one after another as cheap pamphlets from 1656, attacked the Jesuits in plain French for a general readership.",
        details = "The letters appeared anonymously over the following year, and church authorities condemned them, which made them a sensation. Their serial form, sold as pamphlets, let a controversy reach readers who would never have bought a learned treatise.",
    ),

)
