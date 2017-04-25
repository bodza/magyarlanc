(ns magyarlanc.rfsa
    (:require [clojure.java.io :as io])
  #_(:gen-class))

(def ^:private rfsa* (delay (into [] (map read-string (line-seq (io/reader (io/resource "data/rfsa.edn")))))))

(defn analyse
    ([word] (let [ans (transient [])] (analyse word 0 [] ans) (persistent! ans)))
    ([word n syms ans] (let [state (@rfsa* n) accept (true? (first state))]
        (if (seq word)
            (let [c (Character/toLowerCase ^Character (first word)) word (rest word)]
                (doseq [[[^Character a & z] n] (partition 2 (if accept (rest state) state))]
                    (if (= a c) (analyse word n (apply conj syms z) ans))))
            (if accept (conj! ans (apply str syms)))))))

(defn -main [] (println (analyse "utánam")))
