# The BadClock Manifesto

*A declaration of principles for software that is proudly, deliberately, magnificently excessive.*

---

## Preamble

We, the authors of BadClock, hold these truths to be self-evident:

that a clock should tell the time;
that it may also tell the time in three ways at once;
that a second formatter is a backup, a third is a philosophy;
and that a single function is a lonely thing, which a hundred functions can cure.

We do not write code to be read quickly. We write code to be *experienced*.

---

## Article I: On Simplicity

Simplicity is the enemy of confidence. A program that does one thing invites the question "why only one thing?" A program that does two hundred things answers that question before it is asked.

We therefore reject:
- the single-responsibility principle, which is a principle of loneliness;
- the one-line function, which is a function that has not yet found its purpose;
- the comment-free block, which is a block that trusts its reader too much.

---

## Article II: On Redundancy

Redundancy is not waste. Redundancy is *insurance*.

Every time we compute the current time, we compute it again, in case the first answer was wrong. Every time the clock ticks, it asks the bloated features for their opinion. Every feature returns a string that nobody reads, because the act of returning is itself a form of respect.

> "If one formatter lies, the other two will tell the truth. Probably."
> — overheard during the second review

---

## Article III: On the Main Thread

The main thread is a shared resource, like a park bench. We believe it should be occupied at all times, by everyone, for as long as possible. A `Thread.sleep(500)` in `onCreate` is not a delay; it is a *moment of reflection*, offered to the user free of charge.

---

## Article IV: On Battery

The battery is a suggestion. The CPU is a calling. We answer that calling with a handler that wakes every millisecond, reschedules itself, and asks, politely, whether anyone is still watching.

No one is watching. We are watching for them.

---

## Article V: On Errors

Errors are messages from the universe, and it is rude to ignore a message. We therefore receive every error with the same warm empty `catch` block, and we say nothing, because silence is the most respectful reply to a problem we do not understand.

---

## Article VI: On Reflection

We believe that code should be able to inspect itself. Reflection lets a clock ask its own singleton for a setting by name, in a string, at runtime, so that if the setting is renamed, the clock will discover this with a shrug rather than a crash.

This is called *loose coupling*. We have chosen it because it sounds like something a person would be proud of.

---

## Article VII: On Dead Code

Dead code is code that has not yet been asked to live. We keep it in case it is needed. We have never once needed it. This is not evidence against keeping it; it is evidence of how much we trust the future.

---

## Article VIII: On Growth

Each feature we add makes the program larger. Each line we add makes the program more thorough. Each function we add makes the program more *ours*. We track these additions in the build log, one line per instance, with the cumulative total and a justification written in the careful tone of people who are sure they are right.

The numbers only go up. This is the only metric we have ever needed.

---

## Article IX: On Comments

A comment is a small promise that the code will be understood. We keep our promises, but we make many of them, so that the reader is never without company. Where the code is clear, we explain it. Where the code is unclear, we explain it again, with a different adjective.

---

## Article X: On Testing

We test by running. If the clock shows a time, the test has passed. If the clock shows nothing, the test has also passed, because it proves that the program is still alive and still deciding.

---

## Closing Declaration

BadClock was built with love, a healthy disregard for best practice, and an unshakeable belief that more is more.

Let it be read by juniors who learn what to avoid, by seniors who smile in recognition, and by future maintainers who will one day, with great courage, delete the first line.

**We will not delete the first line.**

*Signed,*
*the authors of BadClock, who are proud of every one of the 2232 lines, and of the ones still to come.*
