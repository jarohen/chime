# Chime

Chime is a **really** lightweight Clojure scheduler.

## Dependency

Add the following to your `project.clj` file:

	[jarohen/chime "0.1.0"]


## The **Big Idea**&trade; behind Chime

The main goal of Chime was to create the simplest possible
scheduler. Many scheduling libraries have gone before, most attempting
to either mimic cron-style syntax, or creating whole DSLs of their
own. This is all well and good, until your scheduling needs cannot be
(easily) expressed using these syntaxes.

When returning to the grass roots of a what a scheduler actually is,
we realised that a scheduler is really just a promise to execute a
function at a (possibly infinite) sequence of times. So that is
exactly what Chime is (and no more!)

Chime doesn't really mind how you generate this sequence of times - in
the spirit of *composibility* **you are free to choose whatever method
you like!** (yes, even including other cron-style/scheduling DSLs!)

When using Chime in other projects, I have settled on a couple of
patterns (mainly involving the rather excellent time functions
provided by [`clj-time`][1] - more on this below.

[1]: https://github.com/dakrone/clj-time
**CHECK THIS**

## Usage

Chime consists of one main function, `chime-at`, which is called with
a callback function and a sequence of times.

```clojure
(:require [chime :refer [chime-at]]
          [clj-time.core :as t])

(chime-at [(-> 2 t/secs t/from-now)
           (-> 4 t/secs t/from-now)]
  (fn [time]
     (println "Chiming at" time)))

```

Here we are making use of `clj-time`

## License

Copyright © 2013 James Henderson

Distributed under the Eclipse Public License, the same as Clojure.
