package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code piece of 750 to 760: the bits needed to number the Man'yoshu poems up to 4516, computed in Python and in C. */
val db129Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 129,
        kind = Kind.CROSS,
        section = "Japanese",
        title = "The bits needed to number the Man'yoshu poems up to 4516, in Python and C",
        originalTitle = "Poem numbers in binary",
        author = "Compilers of the Man'yoshu",
        year = 759,
        summary = "The bits needed to write the Man'yoshu poem numbers up to 4516 are counted by a Python program and by a C program, and a script checks that both print the same figures.",
        details = "The last dated poem, of 759, carries the number 4516 in the usual numbering, and 4516 needs 13 binary digits, since 12 digits stop at 4095. Both programs print the 13 bits, the 421 poems numbered from 4096 to 4516 that need the thirteenth digit, and the largest numbers that 12 and 13 digits can hold, so the figures show the arithmetic of the numbering only.",
        instructions = """
            1. Save the example as poems.py, poems.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### poems.py
            LAST = 4516
            print("bits needed for poem number 4516:", LAST.bit_length())
            print("largest number in 12 bits:", 2 ** 12 - 1)
            print("poems that need the thirteenth bit:", LAST - 4096 + 1)
            print("largest number in 13 bits:", 2 ** 13 - 1)
            ### poems.c
            #include <stdio.h>

            int main(void) {
                int last = 4516;
                int bits = 0;
                int n = last;
                while (n) {
                    bits++;
                    n >>= 1;
                }
                printf("bits needed for poem number 4516: %d\n", bits);
                printf("largest number in 12 bits: %d\n", (1 << 12) - 1);
                printf("poems that need the thirteenth bit: %d\n", last - 4096 + 1);
                printf("largest number in 13 bits: %d\n", (1 << 13) - 1);
                return 0;
            }
            ### run.sh
            python3 poems.py > python.txt
            cc -o poems_c poems.c
            ./poems_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
