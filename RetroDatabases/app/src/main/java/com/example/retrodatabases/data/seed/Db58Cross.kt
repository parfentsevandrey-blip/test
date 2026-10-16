package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1460 to 1470: the year 1467 in base seven, and the one-bit counts of the years 1460 to 1470, each in Python and C. */
val db58Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 58,
        kind = Kind.CROSS,
        section = "Number bases",
        title = "1467 in base seven, in Python and C",
        originalTitle = "De componendis cifris",
        author = "Leon Battista Alberti",
        year = 1467,
        summary = "A Python script and a C program both write the year 1467 in base seven, and a run script checks that the two outputs agree.",
        details = "Alberti's cipher treatise is dated about 1466 or 1467, and the year is only a convenient number for the base conversion. Both programs divide by seven and read the remainders from the end, and the run script compares the two printed lines.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### base7.py
            def to_base(n, base):
                digits = []
                while n > 0:
                    digits.append(str(n % base))
                    n //= base
                return "".join(reversed(digits))

            print("1467 in base 7 is", to_base(1467, 7))
            ### base7.c
            #include <stdio.h>

            int main(void) {
                char digits[32];
                int count = 0;
                int n = 1467;
                while (n > 0) {
                    digits[count++] = (char)('0' + n % 7);
                    n /= 7;
                }
                printf("1467 in base 7 is ");
                while (count > 0) {
                    putchar(digits[--count]);
                }
                putchar('\n');
                return 0;
            }
            ### run.sh
            python3 base7.py > python.txt
            cc -o base7_c base7.c
            ./base7_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 58,
        kind = Kind.CROSS,
        section = "Bit counts",
        title = "One-bit counts for the decade in Python and C",
        originalTitle = "The decade 1460 to 1470",
        author = "Program example for the decade of this database",
        year = 1470,
        summary = "Python and C each count the one-bits of every year from 1460 to 1470, and a run script checks that the two lists of counts agree.",
        details = "The years are the decade of this database, and the count of one-bits is a short exercise in binary arithmetic. The two programs print the same eleven lines, and the run script compares them.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### onebits.py
            for year in range(1460, 1471):
                print(f"{year} has {bin(year).count('1')} one-bits")
            ### onebits.c
            #include <stdio.h>

            int main(void) {
                for (int year = 1460; year <= 1470; year++) {
                    int ones = 0;
                    for (int n = year; n > 0; n >>= 1) {
                        ones += n & 1;
                    }
                    printf("%d has %d one-bits\n", year, ones);
                }
                return 0;
            }
            ### run.sh
            python3 onebits.py > python.txt
            cc -o onebits_c onebits.c
            ./onebits_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
