package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Cross-code experiments of 180: the age of Marcus Aurelius at his death, worked out in Python and in C. */
val db187Cross: List<RecordEntity> = listOf(

    entry(
        databaseId = 187,
        kind = Kind.CROSS,
        section = "Lives",
        title = "The age of Marcus Aurelius at his death, worked out in Python and C",
        originalTitle = "Age at death",
        author = "Marcus Aurelius",
        year = 180,
        summary = "Marcus Aurelius was born on 26 April 121 and died on 17 March 180, so he was 58 years old, and a script checks that the Python and C programs work out the same age.",
        details = "The dates are the commonly given ones, and each program subtracts the birth year from the death year, then takes off one year when the birthday has not yet come in the death year. The run script compares the two outputs line by line.",
        instructions = """
            1. Save the files below in one folder.
            2. Run the script: sh run.sh
        """,
        example = """
            ### age.py
            birth = (121, 4, 26)   # Marcus Aurelius, 26 April 121, as commonly given
            death = (180, 3, 17)   # his death, 17 March 180, as commonly given
            age = death[0] - birth[0]
            if (death[1], death[2]) < (birth[1], birth[2]):
                age -= 1
            print("age at death:", age)
            ### age.c
            #include <stdio.h>

            int main(void) {
                int birth_year = 121, birth_month = 4, birth_day = 26;   /* 26 April 121, as commonly given */
                int death_year = 180, death_month = 3, death_day = 17;   /* 17 March 180, as commonly given */
                int age = death_year - birth_year;
                if (death_month < birth_month || (death_month == birth_month && death_day < birth_day))
                    age--;
                printf("age at death: %d\n", age);
                return 0;
            }
            ### run.sh
            python3 age.py > python.txt
            cc -o age_c age.c
            ./age_c > c.txt
            cat python.txt
            diff python.txt c.txt && echo "same result"
        """,
    ),

)
