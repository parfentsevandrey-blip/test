package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Projects of 1960 to 1970: small programs in the spirit of the decade, with instructions and code to run. */
val db7Projects: List<RecordEntity> = listOf(

    entry(
        databaseId = 7,
        kind = Kind.PROJECT,
        section = "Conversation",
        title = "ELIZA-style conversation",
        originalTitle = "ELIZA (Weizenbaum, 1966)",
        author = "Joseph Weizenbaum",
        year = 1966,
        summary = "A keyword program in the spirit of ELIZA: each sentence is matched against a few patterns, and the reply turns the captured words around.",
        details = "Weizenbaum wrote ELIZA at MIT and published it in 1966. Its best-known script, DOCTOR, played a psychotherapist by reflecting the user's own words back. This version has four rules and a reflection table, which is enough to show the trick.",
        instructions = """
            1. Save the example as eliza.py.
            2. Run it: python3 eliza.py
            3. Add your own rules to RULES. The first rule that matches is used.
        """,
        example = """
            import re

            # Each rule is a pattern and a reply template. Captured words are reflected (I becomes you) before they are used.
            RULES = [
                (r"i need (.*)", "Why do you need {0}?"),
                (r"i am (.*)", "How long have you been {0}?"),
                (r"my (.*)", "Tell me more about your {0}."),
                (r"(.*)\?", "Why do you ask that?"),
            ]
            REFLECT = {"i": "you", "my": "your", "me": "you", "am": "are"}

            def reflect(text):
                return " ".join(REFLECT.get(word, word) for word in text.split())

            def reply(sentence):
                text = sentence.lower().strip(" .!")
                for pattern, template in RULES:
                    match = re.fullmatch(pattern, text)
                    if match:
                        return template.format(*[reflect(group) for group in match.groups()])
                return "Please go on."

            for line in ["I need a break.", "I am tired.", "My mother worries about me.", "Will it rain?", "Nothing much."]:
                print(">", line)
                print(reply(line))
        """,
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.PROJECT,
        section = "Matching",
        title = "Stable matching by Gale and Shapley",
        originalTitle = "College admissions and the stability of marriage (1962)",
        author = "David Gale and Lloyd Shapley",
        year = 1962,
        summary = "The proposal algorithm that pairs men and women so that no man and woman would both prefer each other to their partners.",
        details = "Gale and Shapley published the method in 1962. Men propose in order of preference, and each woman keeps the best proposal so far. The result is always stable, and it favours the proposing side.",
        instructions = """
            1. Save the example as matching.py.
            2. Run it: python3 matching.py
        """,
        example = """
            # Men propose in order of preference; each woman keeps the best proposal so far.
            men = {
                "Adam": ["Eve", "Ruth", "Ada"],
                "Ben": ["Ruth", "Eve", "Ada"],
                "Carl": ["Eve", "Ada", "Ruth"],
            }
            women = {
                "Ada": ["Carl", "Adam", "Ben"],
                "Eve": ["Ben", "Carl", "Adam"],
                "Ruth": ["Adam", "Ben", "Carl"],
            }

            def stable_matching(men, women):
                rank = {w: {m: i for i, m in enumerate(prefs)} for w, prefs in women.items()}
                free = list(men)
                next_choice = {m: 0 for m in men}
                engaged = {}                       # woman -> man
                while free:
                    man = free.pop(0)
                    woman = men[man][next_choice[man]]
                    next_choice[man] += 1
                    if woman not in engaged:
                        engaged[woman] = man
                    elif rank[woman][man] < rank[woman][engaged[woman]]:
                        free.append(engaged[woman])
                        engaged[woman] = man
                    else:
                        free.append(man)
                return {man: woman for woman, man in engaged.items()}

            matching = stable_matching(men, women)
            for man in sorted(matching):
                print(man, "marries", matching[man])
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.PROJECT,
        section = "Editors",
        title = "A TECO-style line editor",
        originalTitle = "TECO, the Text Editor and Corrector (1963)",
        author = "Written as a teaching example, after Dan Murphy's TECO",
        year = 1963,
        summary = "A small editor with one-letter commands and a current line, which appends, jumps, deletes, substitutes and prints the buffer, in the spirit of TECO.",
        details = "TECO worked on a buffer with a pointer to the current line, and each command moved the pointer or changed the text. This version has five commands, enough to show the idea.",
        instructions = """
            1. Save the example as teco.py.
            2. Run it: python3 teco.py
            3. Change the list of commands to edit a different buffer.
        """,
        example = """
            buffer = []
            current = 0

            def run(commands):
                global current
                for command in commands:
                    op, _, rest = command.partition(" ")
                    if op == "a":                       # append a line and make it current
                        buffer.append(rest)
                        current = len(buffer) - 1
                    elif op == "j":                     # jump to line number n, counting from 1
                        current = int(rest) - 1
                    elif op == "d":                     # delete the current line
                        del buffer[current]
                        current = min(current, len(buffer) - 1)
                    elif op == "s":                     # replace the first match in the current line
                        old, new = rest.split(" ", 1)
                        buffer[current] = buffer[current].replace(old, new, 1)
                    elif op == "p":                     # print the buffer and mark the current line
                        for number, line in enumerate(buffer, start=1):
                            mark = "*" if number - 1 == current else " "
                            print(f"{mark} {number} {line}")

            run([
                "a the quick fox",
                "a jumps over",
                "a the lazy dog",
                "j 2",
                "s jumps leaps",
                "p",
                "j 3",
                "d",
                "p",
            ])
        """,
        rare = true,
    ),

    entry(
        databaseId = 7,
        kind = Kind.PROJECT,
        section = "Concurrency",
        title = "Dining philosophers with ordered forks",
        originalTitle = "The dining philosophers problem (Dijkstra, 1965)",
        author = "Edsger Dijkstra; this version uses threads",
        year = 1965,
        summary = "Five philosophers share five forks around a table. Taking the forks in a fixed order avoids the deadlock in which each holds one fork and waits for the next.",
        details = "Dijkstra posed the problem in 1965. The example runs five threads, and each thread eats three meals. The lower-numbered fork is always taken first, so no circle of waiting can form, and the final count shows that every philosopher ate.",
        instructions = """
            1. Save the example as philosophers.py.
            2. Run it: python3 philosophers.py
            3. Remove the sorting line to see the deadlock risk; the program may then hang.
        """,
        example = """
            import threading

            PHILOSOPHERS = 5
            MEALS = 3
            forks = [threading.Lock() for _ in range(PHILOSOPHERS)]
            meals = [0] * PHILOSOPHERS

            def philosopher(i):
                # Take the lower-numbered fork first: the fixed order prevents the circular wait that causes deadlock.
                first, second = sorted([i, (i + 1) % PHILOSOPHERS])
                for _ in range(MEALS):
                    with forks[first]:
                        with forks[second]:
                            meals[i] += 1

            threads = [threading.Thread(target=philosopher, args=(i,)) for i in range(PHILOSOPHERS)]
            for t in threads:
                t.start()
            for t in threads:
                t.join()
            print("meals:", meals)
        """,
        rare = true,
    ),
)
