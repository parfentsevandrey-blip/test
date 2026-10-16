package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1410 to 1420: the code points of four Czech words with marks, in Python and C. */
val db63Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 63,
        kind = Kind.CROSS,
        section = "Diacritics",
        title = "Code points of Czech letters with marks, in Python and C",
        originalTitle = "De orthographia Bohemica",
        author = "Jan Hus",
        year = 1410,
        summary = "The marked letters of Czech have fixed code points, and a Python program and a C program print the same code points for four Czech words, so their outputs can be compared.",
        details = "The diacritic marks linked to Hus's spelling are now encoded as Unicode code points, such as U+010D for c with a caron. The Python program reads the code points directly, the C program decodes the UTF-8 bytes by hand, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### codes.py
            WORDS = ["\u010de\u0161tina", "\u0159eka", "\u017eivot", "d\u016fm"]

            for number, word in enumerate(WORDS, start=1):
                codes = " ".join(f"U+{ord(ch):04X}" for ch in word)
                print(f"word {number}: {codes}")
            ### codes.c
            #include <stdio.h>

            int main(void) {
                const char *words[] = {"\304\215e\305\241tina", "\305\231eka", "\305\276ivot", "d\305\257m"};
                for (int w = 0; w < 4; w++) {
                    printf("word %d:", w + 1);
                    const unsigned char *p = (const unsigned char *)words[w];
                    while (*p) {
                        unsigned int cp;
                        if (p[0] < 0x80) {
                            cp = p[0];
                            p += 1;
                        } else {
                            cp = ((p[0] & 0x1Fu) << 6) | (p[1] & 0x3Fu);
                            p += 2;
                        }
                        printf(" U+%04X", cp);
                    }
                    printf("\n");
                }
                return 0;
            }
            ### run.sh
            python3 codes.py > python.txt
            cc -o codes_c codes.c
            ./codes_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
