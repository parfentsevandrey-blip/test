package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1480 to 1490: the binary digits of three years, and Sacrobosco's algorism of 1488, each in Python and C. */
val db56Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 56,
        kind = Kind.CROSS,
        section = "Binary",
        title = "The binary digits of three years in Python and C",
        originalTitle = "Binary digits",
        author = "Anonymous arithmetic teachers",
        year = 1486,
        summary = "A binary numeral writes a year with ones and zeros, and a script checks that the Python and C programs print the same digits for three years of the 1480s.",
        details = "Each year is written with eleven binary places, from 2 to the tenth power down to one, and both programs print the same digits. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the parts of the example as binary_years.py, binary_years.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### binary_years.py
            for year in (1480, 1486, 1488):
                print(year, format(year, "b"))
            ### binary_years.c
            #include <stdio.h>

            static void show(unsigned int n) {
                printf("%u ", n);
                for (int bit = 10; bit >= 0; bit--) {
                    putchar(((n >> bit) & 1) ? '1' : '0');
                }
                putchar('\n');
            }

            int main(void) {
                show(1480);
                show(1486);
                show(1488);
                return 0;
            }
            ### run.sh
            python3 binary_years.py > python.txt
            cc -o binary_years_c binary_years.c
            ./binary_years_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 56,
        kind = Kind.CROSS,
        section = "Arithmetic",
        title = "Sacrobosco's Algorismus of 1488 in Python and C",
        originalTitle = "Algorismus",
        author = "Johannes de Sacrobosco",
        year = 1488,
        summary = "Sacrobosco's Algorismus, printed at Strasbourg in 1488, taught Hindu-Arabic addition digit by digit with carries, and a script checks that the Python and C programs print the same columns for one sum.",
        details = "The treatise was first printed at Strasbourg in 1488 and was printed again in several editions up to 1582, and the record uses the Strasbourg printing. The example adds 1488 and 1497 column by column, and the run script compares the two outputs line by line.",
        instructions = """
            1. Save the parts of the example as addition.py, addition.c and run.sh in one folder.
            2. Run it: sh run.sh
        """,
        example = """
            ### addition.py
            a = [int(d) for d in "1488"][::-1]   # units first, as the algorism writes its columns
            b = [int(d) for d in "1497"][::-1]
            carry = 0
            result = []
            for place in range(4):
                total = a[place] + b[place] + carry
                print(f"column {place + 1}: {a[place]} + {b[place]} + carry {carry} = {total}, write {total % 10}")
                result.append(total % 10)
                carry = total // 10
            if carry:
                result.append(carry)
            print("sum: " + "".join(str(d) for d in reversed(result)))
            ### addition.c
            #include <stdio.h>

            int main(void) {
                int a[4] = {8, 8, 4, 1};
                int b[4] = {7, 9, 4, 1};
                int result[5];
                int n = 0;
                int carry = 0;
                for (int place = 0; place < 4; place++) {
                    int total = a[place] + b[place] + carry;
                    printf("column %d: %d + %d + carry %d = %d, write %d\n", place + 1, a[place], b[place], carry, total, total % 10);
                    result[n++] = total % 10;
                    carry = total / 10;
                }
                if (carry) {
                    result[n++] = carry;
                }
                printf("sum: ");
                for (int i = n - 1; i >= 0; i--) {
                    printf("%d", result[i]);
                }
                printf("\n");
                return 0;
            }
            ### run.sh
            python3 addition.py > python.txt
            cc -o addition_c addition.c
            ./addition_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
