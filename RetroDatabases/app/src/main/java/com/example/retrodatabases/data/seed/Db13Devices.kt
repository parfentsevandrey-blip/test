package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1910 to 1920: tabulators, calculators, radio tubes and receivers, a gyrocompass, a chess automaton and a page printer. */
val db13Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 13,
        kind = Kind.DEVICE,
        section = "Tabulating",
        title = "Powers punched-card tabulator",
        originalTitle = "Powers Tabulating Machine Company, later Powers Accounting Machine Company",
        author = "James Legrand Powers",
        year = 1914,
        summary = "A punched-card machine that senses the holes in each column, adds them into counters and prints the totals, the way businesses and government offices counted from the 1910s.",
        details = "James Powers, a Census Bureau technician, designed machines that kept the card format of Hollerith's system while avoiding his patents. The Powers Tabulating Machine Company was founded in Newark in 1911, and a unit-record timeline dates its first printing tabulator to 1914.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.DEVICE,
        section = "Automata",
        title = "El Ajedrecista, the chess endgame machine",
        originalTitle = "El Ajedrecista, king and rook against a lone king (Torres Quevedo, 1912)",
        author = "Leonardo Torres Quevedo",
        year = 1912,
        summary = "An automaton that played the king and rook ending against a lone king, moving its own pieces on the board while a person moved the opposing king.",
        details = "Torres Quevedo built the machine in 1912, and it was shown in Paris in 1914. It played the side with a king and a rook, and the lone black king was moved by a person. Accounts differ on the mechanism and on the venue of the 1914 showing.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.DEVICE,
        section = "Radio receivers",
        title = "Armstrong's regenerative receiver",
        originalTitle = "US patent 1,113,149 on the regenerative receiver (Armstrong, granted 1914)",
        author = "Edwin Howard Armstrong",
        year = 1912,
        summary = "A receiver that feeds part of its output back into its input, so a weak radio signal is amplified many times over before it reaches the headphones.",
        details = "Armstrong discovered the feedback effect in 1912. His patent was granted in October 1914, after applications made in 1913, and rival claims came from Lee de Forest and from Meissner, whose application followed in 1914. The circuit, pushed close to oscillation, was the basis of the regenerative receivers that followed.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.DEVICE,
        section = "Radio receivers",
        title = "Armstrong's superheterodyne",
        originalTitle = "US patent 1,342,885 on the superheterodyne circuit (Armstrong, issued 1920)",
        author = "Edwin Howard Armstrong",
        year = 1918,
        summary = "A receiver that mixes each incoming signal with a local oscillator, so that every station is changed to one fixed intermediate frequency that is easy to amplify and tune.",
        details = "Armstrong disclosed the circuit to Major O. E. Buckley in May and June 1918, and the US patent was issued in 1920. Sources give filing dates from December 1918 to February 1919, so this record uses the disclosure year. Walter Schottky in Germany and Lucien Levy in France filed closely related claims.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.DEVICE,
        section = "Electron tubes",
        title = "Langmuir's pliotron",
        originalTitle = "Pliotron, a high-vacuum hot-cathode triode (Langmuir, 1913)",
        author = "Irving Langmuir",
        year = 1913,
        summary = "A high-vacuum triode with a hot filament, made at General Electric, that gave steady and predictable amplification once the gas inside it had been removed.",
        details = "Langmuir moved from lamp research to thermionic tubes in 1913, and he traced irregular tube behavior to trace gas left in the envelope. General Electric made pliotrons in volume for military use in World War I. At AT&T, Harold Arnold was working on high-vacuum triodes at the same time.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.DEVICE,
        section = "Navigation",
        title = "Sperry gyrocompass",
        originalTitle = "Gyrocompass for ships, Sperry Gyroscope Company",
        author = "Elmer Ambrose Sperry",
        year = 1911,
        summary = "A north-seeking gyroscope that finds true north without the magnetic error of a compass needle, and that the United States Navy adopted in 1911.",
        details = "Sperry's Brooklyn firm dates from 1910, and a unit reached readiness for sea trials by January 1911. The German Anschutz gyrocompass came first. After Sperry sold a compass to the German navy in 1914, a Berlin court ruled for Anschutz-Kaempfe in 1915. The United States Navy preferred to buy American instruments rather than German ones.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.DEVICE,
        section = "Calculating machines",
        title = "Monroe Adding-Calculator",
        originalTitle = "Monroe Adding-Calculator, built on the design of Frank Stephen Baldwin",
        author = "Jay Randolph Monroe",
        year = 1912,
        summary = "A desk calculator for office arithmetic, sold by the company that Jay Randolph Monroe launched in April 1912 in New Jersey.",
        details = "Monroe drew on the design of the inventor Frank Stephen Baldwin, and the first machines were made in leased space just outside Newark, New Jersey. A museum record dates the first model to 1911 or 1912, so the year used here is that of the company's launch.",
    ),

    entry(
        databaseId = 13,
        kind = Kind.DEVICE,
        section = "Telegraphy",
        title = "Kleinschmidt's typebar page printer",
        originalTitle = "Typebar page printer for telegraph lines, patent application (Kleinschmidt, 1916)",
        author = "Edward Kleinschmidt",
        year = 1916,
        summary = "A telegraph printer that set each received character on a typebar and printed a page of text, a step on the road to the teleprinter.",
        details = "Kleinschmidt's 1916 application described a page printer that used the Baudot code. It did not yet have the start-stop operation that later teleprinters used, and the patent dispute over that method led to his merger with Morkrum.",
        rare = true,
    ),
)
