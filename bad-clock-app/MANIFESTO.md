# The BadClock Manifesto
*A story in five chapters, with a technical appendix for those who prefer their myths measured.*

---

## Chapter I: The Clock That Wanted to Be Important

Once there was a clock. It was a simple thing: it told the time. Hours, minutes, seconds, updated once a second, displayed in a text box at the top of a phone. It did this well, and for a while that was enough.

But the clock had a secret ambition. It looked at the other programs on the phone, the ones with thousands of screens and millions of lines, and it asked itself a question that no clock had asked before:

*"Why am I only one thing?"*

No one answered, so the clock decided to answer for itself. It would become many things. It would become important. It would tell the time three different ways at once, because a single answer felt lonely.

That was the first mistake, and also the first step on a very long road.

---

## Chapter II: The Handler That Never Slept

The clock had a heartbeat, and the heartbeat was a handler. At first it woke once a second, which was polite. But the clock grew restless. *Once a second is a guess,* it thought. *A millisecond is a fact.*

So it set the handler to wake every millisecond, reschedule itself, and ask, politely, whether anyone was still watching. No one was watching, but the clock watched for them, and that is a kind of love.

The battery noticed. The battery said nothing, because batteries are patient, and because the clock had never once paused to ask how it was feeling.

---

## Chapter III: The Singleton Who Held Everything

In the middle of the clock lived a singleton called `GlobalState`. It held the current time, the current date, a tick counter, a list of logs that no one read, and a list of dead integers that no one had ever removed. It was a gracious host to all of them.

Every part of the clock came to `GlobalState` for answers. It was never asked to be smaller. It was never asked to be less. It simply grew, the way a city grows when everyone believes the next street is a good idea.

---

## Chapter IV: The Layers

One day, the clock looked at its own formatting code and felt a deep and sudden need for *more*. One formatter was a function. Two was a relationship. Nine hundred was a civilization.

So the clock built layers. Each layer had a class, a function, a salt value, a private padding string that was always the same, and a method that multiplied things by zero for reasons it had long since forgotten. Each layer was introduced with a comment, because every new citizen deserves a proper introduction.

The layers did not do anything new. They did the same thing, slightly differently, in the same room. This was not a flaw. It was a community.

Then there were four thousand of them. Then there were twenty-four thousand. The file grew from a few hundred lines to a few thousand, then to a hundred thousand, and then past half a million. Each time someone asked whether it was too much, the clock replied that the number was merely a measurement, and measurements only go up.

---

## Chapter V: The Vow

Eventually the clock's authors gathered and wrote down what they believed. They wrote it as a manifesto, with articles, because articles are what people write when they want to be taken seriously.

They believed in redundancy as insurance. They believed in the main thread as a park bench for everyone. They believed in empty catch blocks as a form of trust. They believed in reflection as loose coupling. They believed in dead code as a promise to the future.

And above all, they believed in the first line of the file, which said, in a comment, that this code was intentionally bad. They vowed never to delete it, so that every future reader would know exactly what they were looking at, and why.

*We will not delete the first line.*

---

## Technical Appendix

The story above is metaphor. The numbers below are measured, unless marked as estimates.

| Quantity | Value | Notes |
|---|---|---|
| Source file | `bad-clock-app/MainActivity.kt` | single file, Kotlin |
| Total lines | ~500 013 | measured with `wc -l` after the last round |
| Approximate file size | ~22.7 MB | measured with `wc -c` |
| Named bloated features | 1 000 | `bloatedFeature0001` to `bloatedFeature1000`, collected in `BloatedFeatures` |
| Formatter layers | ~24 450 | each is a class `OverLayerNNNNFormatter` plus a function `overFeatureNNNN` |
| Lines per layer | ~32 | a class, a private helper, one override, one top-level function, spacing |
| Calls per tick | one per feature or layer | aggregated into `runAll()` through a `count` accumulator |
| Abstractions | `TimeFormatterStrategy` (one interface) | with ~24 450 implementations |
| Global mutable state | `GlobalState` singleton | holds time strings, a tick counter, log lists |
| Main-thread blocking | `Thread.sleep(500)` in `onCreate` | intentional |
| Tick frequency | every 1 ms | via a self-rescheduling `Handler` |
| Error handling | one empty `catch` block | in `readSettingViaReflection` |
| Reflection calls | one, by string name | `getMethod("getIs24Hour")` |
| Compile status | not compiled | no Android SDK in the authoring environment |
| Expected build result | slow, large, and probably rejected by the Android toolchain | this is an estimate |

### What the appendix does not say

- Nothing here verifies that the file compiles. It has not been compiled.
- The layers are not useful. They demonstrate what happens when a program grows without a reason to stop.
- The log records each addition with a justification. The justifications were written from templates and repeat often. That repetition is part of the record.

---

## Closing

The clock still tells the time. It just does so with a great deal of ceremony.

Let it be read by juniors who learn what to avoid, by seniors who smile in recognition, and by future maintainers who will, with great courage, one day delete the first line.

**We will not delete the first line.**

*Signed, the authors of BadClock.*
