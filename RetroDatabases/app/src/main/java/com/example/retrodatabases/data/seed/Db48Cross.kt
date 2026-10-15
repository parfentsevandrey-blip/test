package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1560 to 1570: a Porta-style cipher round trip and the days from Trent to Shakespeare's baptism, each in Python and C. */
val db48Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 48,
        kind = Kind.CROSS,
        section = "Ciphers",
        title = "Porta's cipher round trip in Python and C",
        originalTitle = "De furtivis literarum notis",
        author = "Giambattista della Porta",
        year = 1563,
        summary = "A message is encrypted with a keyword and decrypted again, and a script checks that the Python and C programs print the same cipher and plain text.",
        details = "The rows are chosen by the key letters b, a, m and b, and the table is the simplified model from the guide to Porta's rows, not Porta's own table. The run script compares the two outputs line by line.",
        instructions = """
            1. Save each part of the example as the file named on its ### line, in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### porta.py
            ALPHABET = "abcdefghijklmnopqrstuvwxyz"
            KEY = "bam"

            def encode(letter, row):
                # A key letter picks a row; the first half of the alphabet moves to the second half, and back again.
                x = ALPHABET.index(letter)
                if x < 13:
                    return ALPHABET[13 + (x + row) % 13]
                return ALPHABET[(x - 13 - row) % 13]

            message = "code"
            keys = KEY * 2
            cipher = "".join(encode(letter, ALPHABET.index(key) % 13) for letter, key in zip(message, keys))
            plain = "".join(encode(letter, ALPHABET.index(key) % 13) for letter, key in zip(cipher, keys))
            print("cipher:", cipher)
            print("plain:", plain)
            ### porta.c
            #include <stdio.h>
            #include <string.h>

            static const char *ALPHABET = "abcdefghijklmnopqrstuvwxyz";

            static char encode(char letter, int row) {
                int x = (int)(strchr(ALPHABET, letter) - ALPHABET);
                int y;
                if (x < 13) {
                    y = 13 + (x + row) % 13;
                } else {
                    y = ((x - 13 - row) % 13 + 13) % 13;
                }
                return ALPHABET[y];
            }

            static int row_of(char key_letter) {
                return (int)(strchr(ALPHABET, key_letter) - ALPHABET) % 13;
            }

            int main(void) {
                const char *message = "code";
                const char *key = "bam";
                char cipher[5];
                char plain[5];
                int length = (int)strlen(message);
                for (int i = 0; i < length; i++) {
                    cipher[i] = encode(message[i], row_of(key[i % 3]));
                }
                cipher[length] = '\0';
                for (int i = 0; i < length; i++) {
                    plain[i] = encode(cipher[i], row_of(key[i % 3]));
                }
                plain[length] = '\0';
                printf("cipher: %s\n", cipher);
                printf("plain: %s\n", plain);
                return 0;
            }
            ### run.sh
            python3 porta.py > python.txt
            cc -o porta_c porta.c
            ./porta_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 48,
        kind = Kind.CROSS,
        section = "Calendars",
        title = "Days from the Council of Trent's close to Shakespeare's baptism in Python and C",
        originalTitle = "Council of Trent",
        author = "Pope Pius IV",
        year = 1564,
        summary = "The Council of Trent closed on 4 December 1563, and the days to Shakespeare's baptism on 26 April 1564 come to 144, counted by two programs.",
        details = "Both dates are given as commonly stated, and they fall in the same calendar, so counting the days between them needs no conversion. The Python program uses the date library and the C program uses its own day count, and the run script compares the two.",
        instructions = """
            1. Save each part of the example as the file named on its ### line, in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### days.py
            from datetime import date

            start = date(1563, 12, 4)   # the Council of Trent closed on this day, as commonly given
            end = date(1564, 4, 26)     # Shakespeare's baptism at Stratford, as commonly given
            print("days:", (end - start).days)
            ### days.c
            #include <stdio.h>

            static int leap(int year) {
                return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
            }

            static long day_number(int year, int month, int day) {
                static const int before[] = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
                long y = year - 1;
                long days = 365 * y + y / 4 - y / 100 + y / 400;
                days += before[month - 1] + day;
                if (month > 2 && leap(year)) {
                    days += 1;
                }
                return days;
            }

            int main(void) {
                printf("days: %ld\n", day_number(1564, 4, 26) - day_number(1563, 12, 4));
                return 0;
            }
            ### run.sh
            python3 days.py > python.txt
            cc -o days_c days.c
            ./days_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
