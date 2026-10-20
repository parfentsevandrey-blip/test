package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Examples of 180: the Spartan scytale modelled as a column transposition, a placed record whose year is the death of Marcus Aurelius. */
val db187Examples: List<RecordEntity> = listOf(

    entry(
        databaseId = 187,
        kind = Kind.EXAMPLE,
        section = "Ciphers",
        title = "The Spartan scytale, a staff that scrambled a message by columns, written as code",
        originalTitle = "skytale",
        author = "The Spartan authorities",
        year = 180,
        summary = "The Spartan scytale wound a strip of writing material round a staff, so that a message read as scrambled letters until the strip was wound on a staff of the same width, and a short program models the device as a column transposition.",
        details = "This is a placed record: Plutarch describes the scytale in his Life of Lysander, written in the early second century, and the device is traditionally associated with Sparta, so 180 is the year of the anchor, the death of Marcus Aurelius, and not the year the device was made. The example models the scytale as a column transposition with four letters to a turn, and it prints the cipher and its decoding.",
        instructions = """
            1. Save the example as scytale.py.
            2. Run it: python3 scytale.py
        """,
        example = """
            message = "RETREAT AT DAWN".replace(" ", "")
            turn = 4                       # letters that fit along the staff in one turn, as modelled here
            pad = (-len(message)) % turn   # filler letters that complete the last row
            text = message + "X" * pad
            rows = [text[i:i + turn] for i in range(0, len(text), turn)]
            cipher = "".join("".join(row[c] for row in rows) for c in range(turn))
            print("cipher:", cipher)
            height = len(text) // turn
            columns = [cipher[c * height:(c + 1) * height] for c in range(turn)]
            plain = "".join("".join(columns[c][r] for c in range(turn)) for r in range(height))
            print("plain:", plain.rstrip("X"))
            print("letters:", len(message), "padding:", pad)
        """,
        rare = true,
    ),

)
