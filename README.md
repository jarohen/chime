# Chime

Chime is a **really** lightweight Clojure scheduler.

## Dependency

Add the following to your `project.clj` file:

	[jarohen/chime "0.1.1-SNAPSHOT"]


## The **'Big Idea'**&trade; behind Chime

The main goal of Chime was to create the simplest possible
scheduler. Many scheduling libraries have gone before, most attempting
to either mimic cron-style syntax, or creating whole DSLs of their
own. This is all well and good, until your scheduling needs cannot be
(easily) expressed using these syntaxes.

When returning to the grass roots of a what a scheduler actually is,
we realised that a scheduler is really just a promise to execute a
function at a (possibly infinite) sequence of times. So, that is
exactly what Chime is - and no more!

Chime doesn't really mind how you generate this sequence of times - in
the spirit of composability **you are free to choose whatever method
you like!** (yes, even including other cron-style/scheduling DSLs!)

When using Chime in other projects, I have settled on a couple of
patterns (mainly involving the rather excellent time functions
provided by [`clj-time`][1] - more on this below.)

[1]: https://github.com/clj-time/clj-time

## Usage

Chime consists of one main function, `chime-at`, which is called with
a callback function and an ordered sequence of [Joda times][2].

[2]: http://joda-time.sourceforge.net/

```clojure
(:require [chime :refer [chime-at]]
          [clj-time.core :as t])

(chime-at [(-> 2 t/secs t/from-now)
           (-> 4 t/secs t/from-now)]
          (fn [time]
            (println "Chiming at" time)))
```

Here we are making use of `clj-time`'s time functions to generate the
sequence of Joda times. 

`chime-at` returns a zero-arg function that can be called to cancel
the schedule.

### Recurring schedules

To achieve recurring schedules, we can lazily generate an infinite
sequence of times using the new (as of 0.5.0) clj-time `periodic-seq`
function. This example runs every 5 minutes from now:

```clojure
(:require [chime :refer [chime-at]]
          [clj-time.core :as t]
          [clj-time.periodic :refer [periodic-seq]])

(chime-at (rest (periodic-seq (t/now) 
                              (-> 5 t/minutes)))
          (fn [time]
            (println "Chiming at" time)))
```

To start a recurring schedule at a particular time, you can combine
this example with some standard Clojure functions. Let's say you want
to run a function at 8pm New York time every day. To generate the
sequence of times, you'll need to seed the call to `periodic-seq` with
the next time you want the function to run:

```clojure
(:require [chime :refer [chime-at]]
          [clj-time.core :as t])
(:import [org.joda.time DateTimeZone])

(chime-at 
 (->> (periodic-seq (.. (t/now)
                        (withZone (DateTimeZone/forID "America/New_York"))
                        (withTime 20 0 0 0))
                    (-> 1 t/days)))
 (fn [time]
   (println "Chiming at" time)))
```

Chime does drop any times that have already passed from the front of
your sequence of times (on the condition that the sequence is ordered)
so it doesn't matter whether 8pm today has already passed - Chime will
handle this gracefully.

### Complex schedules

Because there is no scheduling DSL included with Chime, the sorts of
schedules that you can achieve are not limited to the scope of the
DSL.

Instead, complex schedules can be expressed with liberal use of
standard Clojure sequence-manipulation functions:

```clojure
(:require [clj-time.core :as t])
(:import [org.joda.time DateTimeConstants DateTimeZone])

;; Every Tuesday and Friday:
(->> (periodic-seq (.. (t/now)
                       (withZone (DateTimeZone/forID "America/New_York"))
                       (withTime 0 0 0 0))
                   (-> 1 t/days))
     (filter (comp #{DateTimeConstants/TUESDAY
	                 DateTimeConstants/FRIDAY}
				   #(.getDayOfWeek %))))

;; Week-days
(->> (periodic-seq ...)
     (remove (comp #{DateTimeConstants/SATURDAY
                     DateTimeConstants/SUNDAY}
                   #(.getDayOfWeek %))))

;; Last Monday of the month:
(->> (periodic-seq (.. (t/now)
                       (withZone (DateTimeZone/forID "America/New_York"))
                       (withTime 0 0 0 0))
                   (-> 1 t/days))

     ;; Get all the Mondays					   
     (filter (comp #{DateTimeConstants/MONDAY}
                   #(.getDayOfWeek %)))

     ;; Split into months
     ;; (Make sure you use partition-by, not group-by - 
     ;;  it's an infinite series!)
     (partition-by #(.getMonthOfYear %))

     ;; Only keep the last one in each month
     (map last))

;; 'Triple witching days': 
;; (The third Fridays in March, June, September and December)
;; (see http://en.wikipedia.org/wiki/Triple_witching_day)

;; Here we have to revert the start day to the first day of the month
;; so that when we split by month, we know which Friday is the third
;; Friday. (Any times that have already passed will be dropped, as
;; before)

(->> (periodic-seq (.. (t/now)
                       (withZone (DateTimeZone/forID "America/New_York"))
                       (withTime 0 0 0 0)
					   (withDayOfMonth 1)
                   (-> 1 t/days))

     (filter (comp #{DateTimeConstants/FRIDAY}
				   #(.getDayOfWeek %)))

     (filter (comp #{3 6 9 12}
				   #(.getMonthOfYear %)))

     ;; Split into months
     (partition-by #(.getMonthOfYear %))

     ;; Only keep the third one in each month
	 (map #(nth % 2))))
```

This is quite a different approach to other scheduling libraries, and
therefore I would be very interested to hear your thoughts!

## Error handling

As of 0.1.1, you can pass an error-handler to `chime-at` - a function
that takes the exception as an argument. You can either re-throw it,
to prevent future occurrences of the scheduled task; or squash it to
try again at the next scheduled time.

By default, Chime will print the stack trace on \*err\*, and continue
the schedule.

```clojure
    (chime-at [times...]
              do-task-fn
			  {:error-handler (fn [e] 
			                     ;; log, alert, notify etc?
						       )})
```
							  

## Testing your integration with Chime

Testing time-dependent applications is always more challenging than
other non-time-dependent systems. Chime makes this easier by allowing
you to test the sequence of times independently from the execution of
the scheduled job.

(Although, don't forget to wrap your infinite sequences with `(take x
...)` when debugging!)

## Bugs/thoughts/ideas/suggestions/patches etc

Please feel free to submit these through Github in the usual way!

Thanks!

## Changes

### 0.1.1

No breaking changes. Added error-handling to `chime-at`.

### 0.1.0

Initial release

## License

Copyright © 2013 James Henderson

Distributed under the Eclipse Public License, the same as Clojure.

Big thanks to Malcolm Sparks for providing the initial idea, as well
as his other contributions and discussions.
