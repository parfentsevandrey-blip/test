package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Devices of 1930 to 1940: the calculators, cipher machines and early computers of the decade. */
val db11Devices: List<RecordEntity> = listOf(

    entry(
        databaseId = 11,
        kind = Kind.DEVICE,
        section = "Calculators",
        title = "IBM 601 multiplying punch",
        originalTitle = "IBM Type 601 multiplying punch (1931)",
        author = "International Business Machines",
        year = 1931,
        summary = "A card machine that multiplied two numbers read from one card and punched the product into an unused field of the same card.",
        details = "Each factor could have up to eight decimal digits. The machine also added and subtracted, and because it had no printer it usually worked alongside a tabulator or an accounting machine. Output is dated to 1931 at Endicott, New York, by one source, while a museum record gives production in Milan from 1936.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.DEVICE,
        section = "Cipher machines",
        title = "The Enigma I, the German army cipher machine",
        originalTitle = "Enigma I rotor cipher machine, from the commercial design of Arthur Scherbius",
        author = "Arthur Scherbius and the German armed forces",
        year = 1930,
        summary = "A portable electromechanical machine with three rotors, a reflector and a plugboard, which enciphered each letter along a path that changed with every key press.",
        details = "Army purchases began in the late 1920s. A 117-machine order in June 1930 and the start of encrypted army traffic in 1930 are the reference points, so the record uses 1930. The Wehrmacht adopted the model in 1935. The rotors step like an odometer, and the reflector means that encryption and decryption use the same settings and the same motion.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.DEVICE,
        section = "Computers",
        title = "Zuse Z1, the mechanical binary computer in Berlin",
        originalTitle = "V1, later named Z1 (Zuse, 1936 to 1938)",
        author = "Konrad Zuse",
        year = 1938,
        summary = "A mechanical binary computer with a program read from punched film, built in the Berlin home of its inventor.",
        details = "Zuse built the machine between 1936 and 1938 in his parents' home, with mechanical memory and floating-point numbers. It was unreliable and was destroyed in the war. A replica was assembled by Zuse and three colleagues between 1986 and 1989 and is shown in Berlin.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.DEVICE,
        section = "Computers",
        title = "Zuse Z2, the relay arithmetic unit",
        originalTitle = "Z2, the Z1 memory with relay arithmetic (Zuse and Schreyer, 1939)",
        author = "Konrad Zuse and Helmut Schreyer",
        year = 1939,
        summary = "A machine that kept the Z1 mechanical memory and used telephone relays for its arithmetic and control.",
        details = "Helmut Schreyer joined Zuse on the Z2. Sources differ on whether it was finished in 1939 or 1940, and on the number of relays. It was demonstrated to the Deutsche Versuchsanstalt fuer Luftfahrt in 1940, and that showing is said to have helped win funds for the Z3.",
        rare = true,
    ),

    entry(
        databaseId = 11,
        kind = Kind.DEVICE,
        section = "Computers",
        title = "The Atanasoff-Berry Computer, electronic and built for linear equations",
        originalTitle = "Atanasoff-Berry Computer, ABC (prototype 1939, working machine 1942)",
        author = "John Vincent Atanasoff and Clifford Berry",
        year = 1939,
        summary = "A vacuum-tube machine built at Iowa State to solve systems of linear equations, with a rotating drum of capacitors as its memory.",
        details = "Work began in 1939, and the first demonstration of a prototype is dated to late 1939, with sources giving October and December. The full machine was working in 1942. Its capacity is given as 29 equations, and some sources say 30. Whether it was the first electronic digital computer is disputed, and the question was part of the ENIAC patent case.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.DEVICE,
        section = "Cryptanalysis",
        title = "The Bombe, a machine for testing Enigma settings",
        originalTitle = "British Bombe, designed by Alan Turing with Gordon Welchman (1939)",
        author = "Alan Turing and Gordon Welchman",
        year = 1939,
        summary = "An electromechanical machine that ran through the rotor positions of an Enigma message and stopped at each position where a guessed piece of text was consistent.",
        details = "The Bombe was designed in 1939 and built by the British Tabulating Machine Company. The first machine, named Victory, ran its first job on 14 March 1940 at Bletchley Park. Welchman's diagonal board was added to the improved machines that followed the first.",
    ),

    entry(
        databaseId = 11,
        kind = Kind.DEVICE,
        section = "Relay computers",
        title = "Model K, a one-bit relay adder built on a kitchen table",
        originalTitle = "Model K relay adder (Stibitz, November 1937)",
        author = "George Stibitz",
        year = 1937,
        summary = "A binary adder made from telephone relays, put together over one weekend at home and named K after the kitchen table it was built on.",
        details = "Stibitz built the adder in November 1937 while working at Bell Telephone Laboratories. Sources differ on whether it added one binary digit or two, and this record uses one. Claude Shannon's master's thesis on relay circuits, written in 1937, described comparable designs.",
        rare = true,
    ),

    entry(
        databaseId = 11,
        kind = Kind.DEVICE,
        section = "Cipher machines",
        title = "Typex, the British cipher machine derived from Enigma",
        originalTitle = "Typex cipher machine, developed by Lywood and colleagues (1934 to 1937)",
        author = "Lywood and colleagues",
        year = 1937,
        summary = "A cipher machine with five rotors, built on the commercial Enigma, that entered service with the British armed forces in 1937.",
        details = "Development began in 1934, and the first Mark I units entered service in early 1937. A demonstration of the Mark II in June 1938 led to an order for 350 units. Machines stayed in use into the 1950s, and some Commonwealth users kept them into the 1970s.",
    ),
)
