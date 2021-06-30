(defproject jarohen/chime "0.3.4-SNAPSHOT"
  :description "A really lightweight Clojure scheduler"

  :url "https://github.com/jarohen/chime"

  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.10.1"]
                 [org.clojure/tools.logging "1.0.0"]
                 [clj-time/clj-time "0.15.2" :scope "provided"]
                 [org.clojure/core.async "1.1.587" :scope "provided"]])
