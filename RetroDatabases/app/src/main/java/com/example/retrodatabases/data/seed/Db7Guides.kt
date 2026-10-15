package com.example.retrodatabases.data.seed

import com.example.retrodatabases.data.Kind
import com.example.retrodatabases.data.RecordEntity

/** Guides of 1960 to 1970: how paging, time-sharing, estimation and packet networks work, with small programs where the sandbox allows. */
val db7Guides: List<RecordEntity> = listOf(

    entry(
        databaseId = 7,
        kind = Kind.GUIDE,
        section = "Virtual memory",
        title = "How paging works, as on Atlas",
        originalTitle = "Demand paging with least recently used replacement",
        author = "Written as a teaching example",
        year = 1962,
        summary = "The address is split into a page number and an offset. A page missing from core causes a fault, and the least recently used page is moved out to make room.",
        details = "Atlas brought demand paging into practice in hardware and software. The example is a toy with three frames of core and a list of addresses. " +
            "Each address is split into a page and an offset, a hit moves the page to the most recently used end, and a fault evicts the least recently used page.",
        instructions = """
            1. Save the example as paging.py.
            2. Run it: python3 paging.py
        """,
        example = """
            # A toy paged memory: pages of 4 addresses, 3 frames of core, least recently used replacement.
            PAGE_SIZE = 4
            FRAMES = 3
            frames = []          # pages in core, least recently used first

            def access(address):
                page = address // PAGE_SIZE
                offset = address % PAGE_SIZE
                if page in frames:
                    frames.remove(page)
                    frames.append(page)
                    result = "hit"
                else:
                    result = "fault"
                    if len(frames) == FRAMES:
                        evicted = frames.pop(0)
                        result += f", evicts page {evicted}"
                    frames.append(page)
                return f"address {address:2} -> page {page}, offset {offset}: {result}"

            for address in [0, 5, 9, 1, 13, 2, 17, 6, 0]:
                print(access(address))
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.GUIDE,
        section = "Time-sharing",
        title = "How a time-sharing scheduler gives each user a turn",
        originalTitle = "Round-robin scheduling with a time quantum",
        author = "Written as a teaching example",
        year = 1961,
        summary = "The processor gives each user a short turn, called a quantum, and then moves on to the next user, so that everyone's work advances at once.",
        details = "A time-sharing system such as CTSS gave each terminal user a slice of the processor. A job that did not finish in its slice went to the back of the queue. " +
            "The example runs three users with different amounts of work and prints each turn.",
        instructions = """
            1. Save the example as scheduler.py.
            2. Run it: python3 scheduler.py
        """,
        example = """
            # Three users share one processor. Each turn lasts at most QUANTUM units, then the next user gets a turn.
            QUANTUM = 2
            jobs = {"ann": 5, "bob": 3, "cy": 1}    # units of work each user still needs
            clock = 0
            queue = list(jobs)

            while queue:
                user = queue.pop(0)
                run = min(QUANTUM, jobs[user])
                jobs[user] -= run
                clock += run
                if jobs[user] == 0:
                    print(f"time {clock:2}: {user} finishes")
                else:
                    print(f"time {clock:2}: {user} runs {run}, {jobs[user]} left")
                    queue.append(user)
            print("all users done at time", clock)
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.GUIDE,
        section = "Estimation",
        title = "How a Kalman filter updates an estimate",
        originalTitle = "A new approach to linear filtering and prediction problems (1960)",
        author = "Rudolf Kalman",
        year = 1960,
        summary = "The filter keeps an estimate and its uncertainty. Each new measurement moves the estimate by a gain that depends on how uncertain the estimate still is.",
        details = "Kalman published his method in 1960. When the estimate is very uncertain the gain is close to one, so the new measurement dominates; as the estimate settles, the gain falls. " +
            "The example estimates a constant value from five noisy readings.",
        instructions = """
            1. Save the example as kalman.py.
            2. Run it: python3 kalman.py
        """,
        example = """
            # One-dimensional Kalman filter: estimate a constant value from noisy measurements.
            measurements = [1.2, 0.9, 1.1, 1.0, 1.05]
            x = 0.0      # estimate
            p = 1.0      # variance of the estimate
            r = 0.1      # variance of the measurement noise
            for step, z in enumerate(measurements, start=1):
                k = p / (p + r)          # gain: how much to trust the new measurement
                x = x + k * (z - x)
                p = (1 - k) * p
                print(f"step {step}: measurement {z:.2f}, estimate {x:.3f}, gain {k:.3f}")
        """,
    ),

    entry(
        databaseId = 7,
        kind = Kind.GUIDE,
        section = "Networks",
        title = "How a packet network moves a message",
        originalTitle = "Store-and-forward packet switching",
        author = "Written as a teaching explanation",
        year = 1969,
        summary = "A message is cut into packets, and each node stores a packet, checks it and forwards it to the next node, so no line has to be reserved for the whole conversation.",
        details = "The ARPANET used this idea from its first links in 1969. A message could travel by different routes in its packets, and no single line had to be kept for the whole conversation. " +
            "A telephone call, by contrast, held a circuit from start to finish.",
        rare = true,
    ),
)
