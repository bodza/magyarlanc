(defproject hu.szte/magyarlanc "0.0.0-SNAPSHOT"
    :dependencies [[alt.cache/anna "3.3.0"]
                   [alt.cache/icu4j "3.6.1"]
                   [alt.cache/morphadorner "2.0.1"]
                   [alt.cache/stanford-corenlp "3.3.1"]
                   [alt.cache/whatswrong "0.2.3"]
                   [org.clojure/clojure "1.7.0"]
                 #_[org.clojure/core.async "0.2.374"]
                 #_[org.clojure/core.match "0.3.0-alpha4"]
                   [org.clojure/data.int-map "0.2.2"]]
;   :global-vars {*warn-on-reflection* true}
    :jvm-opts ["-Xmx2g"]
;   :javac-options ["-g"]
    :source-paths ["src"] :java-source-paths ["src"] :resource-paths ["src"] :test-paths ["src"]
    :repositories [["alt.cache" "file:///alt/apa/lingua/repository"]]
    :main magyarlanc
    :aliases {"magyarlanc" ["run" "-m" "magyarlanc"]})
