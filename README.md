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
the spirit of composability **you are free to choose whatever method
you like!** (yes, even including other cron-style/scheduling DSLs!)

When using Chime in other projects, I have settled on a couple of
patterns (mainly involving the rather excellent time functions
provided by [`clj-time`][1] - more on this below.)

[1]: https://github.com/clj-time/clj-time

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

Here we are making use of `clj-time`'s time functions to generate the
sequence of times. 

### Recurring schedules

To achieve recurring schedules, we can lazily generate an infinite
sequence of times using the new (as of 0.5.0) clj-time `periodic-seq`
function. This example runs every 5 minutes from now:

```clojure
(:require [chime :refer [chime-at]]
          [clj-time.core :as t]
		  [clj-time.periodic :refer [periodic-seq]])

(chime-at (periodic-seq (t/now) (-> 5 t/mins))))
  (fn [time]
     (println "Chiming at" time)))
```

To start a recurring schedule at a particular time, you can combine
this example with the Chime `next-occurrence-of` utility
function. Let's say you want to run a function at 8pm New York time
every day. To generate the sequence of times, you'll need to seed the
call to `periodic-seq` with the next time you want the function to run
(i.e. 8pm today if it hasn't already passed, or 8pm tomorrow if it
has):

```clojure
(:require [chime :refer [chime-at]]
          [chime.util :refer [next-occurrence-of]]
          [clj-time.core :as t])
(:import [org.joda.time DateTimeZone])

(chime-at 
	(periodic-seq (next-occurrence-of {:time-of-day [20 0 0 0]
	                                   :tz (DateTimeZone/forID "America/New_York")})
                  (-> 1 t/days)))
    (fn [time]
		(println "Chiming at" time)))
```

## License

Copyright © 2013 James Henderson

Distributed under the Eclipse Public License, the same as Clojure.
