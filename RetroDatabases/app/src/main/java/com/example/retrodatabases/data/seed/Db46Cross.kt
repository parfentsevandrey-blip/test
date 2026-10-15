package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1580 to 1590: Vigenere's autokey and a beacon chain's warning times, each in Python and C. */
val db46Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 46,
        kind = Kind.CROSS,
        section = "Ciphers",
        title = "Vigenere's autokey cipher in Python and C",
        originalTitle = "Traicte des chiffres, ou secretes manieres d'escrire",
        author = "Blaise de Vigenere",
        year = 1586,
        summary = "Vigenere's autokey takes the message itself as part of the key, and a script checks that the Python and C programs print the same ciphertext for one message.",
        details = "The autokey form is one of the variants described in Vigenere's treatise of 1586, and the two programs use the keyword LEMON followed by the plaintext as the key stream. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the example as autokey.py, autokey.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### autokey.py
            def autokey(message, keyword):
                # The key stream is the keyword followed by the plaintext, so after the keyword each shift is a plaintext letter.
                stream = keyword + message
                out = []
                for i, letter in enumerate(message):
                    shift = ord(stream[i]) - ord("A")
                    out.append(chr((ord(letter) - ord("A") + shift) % 26 + ord("A")))
                return "".join(out)

            print(autokey("ATTACKATDAWN", "LEMON"))
            ### autokey.c
            #include <stdio.h>
            #include <string.h>

            int main(void) {
                const char *message = "ATTACKATDAWN";
                const char *keyword = "LEMON";
                char stream[64];
                size_t mlen = strlen(message);
                strcpy(stream, keyword);
                strcat(stream, message);
                for (size_t i = 0; i < mlen; i++) {
                    int shift = stream[i] - 'A';
                    putchar((message[i] - 'A' + shift) % 26 + 'A');
                }
                putchar('\n');
                return 0;
            }
            ### run.sh
            python3 autokey.py > python.txt
            cc -o autokey_c autokey.c
            ./autokey_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 46,
        kind = Kind.CROSS,
        section = "Signals",
        title = "A beacon chain's warning time in Python and C",
        originalTitle = "The Armada beacon chain of 1588",
        author = "The English Privy Council",
        year = 1588,
        summary = "A chain of beacons passes a warning from the coast inland, and a script checks that the Python and C programs print the same warning times for an illustrative chain.",
        details = "The beacon chain of 1588 was meant to pass news quickly from the coast inland, and the example uses illustrative spacing and a fixed lighting time for each beacon, not figures from the English chain. The run script compares the two programs' outputs line by line.",
        instructions = """
            1. Save the example as beacons.py, beacons.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### beacons.py
            for k in range(1, 6):
                print(f"beacon {k}: {5 * k} miles, lit at minute {2 * k}")
            ### beacons.c
            #include <stdio.h>

            int main(void) {
                for (int k = 1; k <= 5; k++) {
                    printf("beacon %d: %d miles, lit at minute %d\n", k, 5 * k, 2 * k);
                }
                return 0;
            }
            ### run.sh
            python3 beacons.py > python.txt
            cc -o beacons_c beacons.c
            ./beacons_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
