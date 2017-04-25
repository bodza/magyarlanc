(ns magyarlanc.dependency
    (:require [magyarlanc.morphology :as mor])
    (:import [is2.data SentenceData09]
             [is2.parser Options Parser])
    (:import [magyarlanc MSDTools])
    (:gen-class))

(def ^:private parser* (delay (Parser. (Options. (into-array ["-model" "./data/szeged.dep.model" "-cores" "1"])))))

(defn- parseSentence
    ([morph]
        (let [word (map #(nth % 0) morph)
             lemma (map #(nth % 1) morph)
               msd (map #(nth % 2) morph)
               pos (map #(str (first (nth % 2))) morph)
             conll (map #(MSDTools/msdToConllFeatures (nth % 1) (nth % 2)) morph)]
            (parseSentence word lemma msd pos conll)))

    ([word lemma msd pos conll]
        (let [data (doto (SentenceData09.)
                (.init      (into-array (cons "<root>" word)))
                (.setLemmas (into-array (cons "<root-LEMMA>" lemma)))
                (.setPPos   (into-array (cons "<root-POS>" pos)))
                (.setFeats  (into-array (cons "<no-type>" conll))))]
            (if (< 1 (.length data))
                (let [data (.apply @parser* data)]
                    (map #(vector (inc %1) %2 %3 %4 %5 %6 (str %7) %8)
                            (range) word lemma msd pos conll (.pheads data) (.plabels data)))))))

(defn depParseSentence [sentence]
    (parseSentence (mor/morphParseSentence sentence)))

(defn depParse [text]
    (map parseSentence (mor/morphParse text)))
