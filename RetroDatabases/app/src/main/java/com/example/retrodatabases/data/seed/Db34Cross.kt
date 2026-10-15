package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 1700 to 1710: binary addition and a sign test, each in Python and C. */
val db34Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 34,
        kind = Kind.CROSS,
        section = "Number systems",
        title = "Leibniz's binary addition in Python and C",
        originalTitle = "Explication de l'Arithmetique Binaire",
        author = "Gottfried Wilhelm Leibniz",
        year = 1703,
        summary = "The same binary addition is written once in Python and once in C, and a script checks that the two programs print the same sums.",
        details = "Leibniz's binary arithmetic of 1703 adds digit by digit with a carry, and the two programs follow that method. The sums are 1011 plus 110 and 111 plus 1, and the run script compares the output of the two languages.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### binary_add.py
            def add_binary(a, b):
                # Add two binary strings digit by digit from the right, carrying as Leibniz's rules do.
                a, b = a[::-1], b[::-1]
                out, carry = [], 0
                for i in range(max(len(a), len(b))):
                    da = int(a[i]) if i < len(a) else 0
                    db = int(b[i]) if i < len(b) else 0
                    s = da + db + carry
                    out.append(str(s % 2))
                    carry = s // 2
                if carry:
                    out.append("1")
                return "".join(reversed(out))

            for x, y in (("1011", "110"), ("111", "1")):
                print(f"{x} + {y} = {add_binary(x, y)}")
            ### binary_add.c
            #include <stdio.h>
            #include <string.h>

            /* Add two binary strings digit by digit from the right, carrying as Leibniz's rules do. */
            static void add_binary(const char *a, const char *b, char *out) {
                int la = (int)strlen(a), lb = (int)strlen(b);
                int n = la > lb ? la : lb;
                char tmp[64];
                int carry = 0, k = 0;
                for (int i = 0; i < n; i++) {
                    int da = (i < la) ? a[la - 1 - i] - '0' : 0;
                    int db = (i < lb) ? b[lb - 1 - i] - '0' : 0;
                    int s = da + db + carry;
                    tmp[k++] = (char)('0' + s % 2);
                    carry = s / 2;
                }
                if (carry) {
                    tmp[k++] = '1';
                }
                for (int i = 0; i < k; i++) {
                    out[i] = tmp[k - 1 - i];
                }
                out[k] = '\0';
            }

            int main(void) {
                const char *pairs[2][2] = {{"1011", "110"}, {"111", "1"}};
                for (int p = 0; p < 2; p++) {
                    char out[64];
                    add_binary(pairs[p][0], pairs[p][1], out);
                    printf("%s + %s = %s\n", pairs[p][0], pairs[p][1], out);
                }
                return 0;
            }
            ### run.sh
            python3 binary_add.py > python.txt
            cc -o binary_add_c binary_add.c
            ./binary_add_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

    entry(
        databaseId = 34,
        kind = Kind.CROSS,
        section = "Statistics",
        title = "Arbuthnot's sign test in Python and C",
        originalTitle = "An Argument for Divine Providence, taken from the constant regularity observ'd in the births of both sexes",
        author = "John Arbuthnot",
        year = 1710,
        summary = "The chance of one outcome in 82 years is computed once in Python and once in C, and a script checks that both print the same figures.",
        details = "Arbuthnot's record of 82 years of London christenings gives the same chance when it is computed two ways, once with the power operator in Python and once with the power function in C. The run script compares the printed lines.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### sign_test.py
            YEARS = 1710 - 1629 + 1
            print("years counted:", YEARS)
            print(f"chance: {2.0 ** -YEARS:.3e}")
            ### sign_test.c
            #include <stdio.h>
            #include <math.h>

            int main(void) {
                int years = 1710 - 1629 + 1;
                printf("years counted: %d\n", years);
                printf("chance: %.3e\n", pow(2.0, -(double)years));
                return 0;
            }
            ### run.sh
            python3 sign_test.py > python.txt
            cc -o sign_test_c sign_test.c -lm
            ./sign_test_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
