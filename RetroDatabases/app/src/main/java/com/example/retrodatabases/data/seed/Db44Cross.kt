package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1600 to 1610: Bacon's biliteral cipher and the weekly issues of a news sheet, each in Python and C. */
val db44Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 44,
        kind = Kind.CROSS,
        section = "Ciphers",
        title = "Bacon's biliteral cipher in Python and C",
        originalTitle = "The Advancement of Learning",
        author = "Francis Bacon",
        year = 1605,
        summary = "Bacon's cipher writes each letter as five signs of two kinds, and a script checks that the Python and C programs print the same codes for the same word.",
        details = "Bacon's biliteral cipher of 1605 gives each letter a group of five signs, and both programs compute the group from the letter's place in the 24-letter alphabet. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### biliteral.py
            ALPHABET = "abcdefghiklmnopqrstuwxyz"   # 24 letters: I serves for J, and U for V

            def biliteral(letter):
                # Each letter gets its place in the alphabet, written as five binary digits: a for 0 and b for 1.
                index = ALPHABET.index(letter)
                return "".join("b" if (index >> shift) & 1 else "a" for shift in (4, 3, 2, 1, 0))

            for letter in "bacon":
                print(letter, biliteral(letter))
            ### biliteral.c
            #include <stdio.h>
            #include <string.h>

            static const char *ALPHABET = "abcdefghiklmnopqrstuwxyz";

            int main(void) {
                const char *message = "bacon";
                for (size_t i = 0; i < strlen(message); i++) {
                    const char *p = strchr(ALPHABET, message[i]);
                    int index = (int)(p - ALPHABET);
                    char code[6];
                    for (int bit = 0; bit < 5; bit++) {
                        code[bit] = ((index >> (4 - bit)) & 1) ? 'b' : 'a';
                    }
                    code[5] = '\0';
                    printf("%c %s\n", message[i], code);
                }
                return 0;
            }
            ### run.sh
            python3 biliteral.py > python.txt
            cc -o biliteral_c biliteral.c
            ./biliteral_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 44,
        kind = Kind.CROSS,
        section = "Newspapers",
        title = "Weekly issue dates of a news sheet in Python and C",
        originalTitle = "Relation",
        author = "Johann Carolus",
        year = 1605,
        summary = "The Strasbourg Relation of 1605 is among the earliest printed news sheets issued each week, and a program lists the dates of six weekly issues.",
        details = "Carolus printed the Relation at Strasbourg in 1605, and accounts differ on whether it was the first newspaper, since other printed news sheets of about the same date are also claimed. The example lists six weekly issue dates from an illustrative start, in Python with the date library and in C with its own month table, and the run script compares the two.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### weekly.py
            from datetime import date, timedelta

            first = date(1605, 1, 1)   # an illustrative first issue, not the Relation's own date
            for week in range(6):
                print(f"issue {week + 1}: {first + timedelta(days=7 * week)}")
            ### weekly.c
            #include <stdio.h>

            static int days_in_month(int year, int month) {
                static const int days[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
                int leap = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0;
                return (month == 2 && leap) ? 29 : days[month - 1];
            }

            int main(void) {
                int year = 1605, month = 1, day = 1;
                for (int issue = 1; issue <= 6; issue++) {
                    printf("issue %d: %04d-%02d-%02d\n", issue, year, month, day);
                    for (int step = 0; step < 7; step++) {
                        day++;
                        if (day > days_in_month(year, month)) {
                            day = 1;
                            month++;
                            if (month > 12) {
                                month = 1;
                                year++;
                            }
                        }
                    }
                }
                return 0;
            }
            ### run.sh
            python3 weekly.py > python.txt
            cc -o weekly_c weekly.c
            ./weekly_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
