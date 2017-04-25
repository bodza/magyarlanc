(ns magyarlanc.hunsplitter
    (:require [clojure.java.io :as io])
    (:import [edu.northwestern.at.morphadorner.corpuslinguistics.sentencesplitter DefaultSentenceSplitter]
             [edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer DefaultWordTokenizer])
  #_(:gen-class))

(defn- readSet [file] (with-open [reader (io/reader (io/resource file))] (into #{} (line-seq reader))))

(def ^:private stopwords* (delay (readSet "data/stopwords.txt")))
(def ^:private hunAbbrev* (delay (readSet "data/hun_abbrev.txt")))

(def ^:private splitter* (delay (DefaultSentenceSplitter.)))
(def ^:private tokenizer* (delay (DefaultWordTokenizer.)))

(defn- normalize [text]
    (let [sb (StringBuilder. text)]
        (doseq [i (range (.length sb))]
            (when-let [ch (case (int (.charAt sb i))
                (11 12
                 28 29 30 31
                 5760 6158
                 8192 8193 8194 8195 8196 8197 8198
                 8200 8201 8202 8203 8232 8233 8287
                 12288
                 65547 65564 65565 65566 65567)     \space	; sic!
                (733 771 1524 1764 8220
                 61485 61486 61487 61488)           \"
                (768 769 900 1523 1614 1648
                 8216 8217 8218 8219
                 61448 61449)                       \'
                (1643)                              \,
                (8211 8212 8722
                 65533)                             \-
                (803
                 61472 61474 61475 61476 61477
                 61480 61481 61482 61483 61484)     \.
                (1475)                              \:
                nil)]
                (.setCharAt sb i ch)))
        (.toString sb)))

; mondatvégi írásjelek külön tokenek legyenek (.?!:;)
(defn- eosApart [sentence]
    (let [words (vec sentence)] (if-let [eos (peek words)]
        (let [chars (vec eos)] (if-let [eow (peek chars)]
            (if (or (Character/isLetterOrDigit eow) (< (count chars) 2))
                words
                (conj (pop words) (apply str (pop chars)) (str eow)))
            words))
        words)))

; separate ' 'm 's 'd 're 've 'll n't endings into apart tokens
(defn- aposApart [token]
    (let [n (.length token) tlc (.toLowerCase token)]
        (cond
            (and (< 1 n) (.endsWith tlc "'"))
                (let [i (- n 1)] [(.substring token 0 i) (.substring token i)])
            (and (< 2 n) (or (.endsWith tlc "'m") (.endsWith tlc "'s") (.endsWith tlc "'d")))
                (let [i (- n 2)] [(.substring token 0 i) (.substring token i)])
            (and (< 3 n) (or (.endsWith tlc "'re") (.endsWith tlc "'ve") (.endsWith tlc "'ll") (.endsWith tlc "n't")))
                (let [i (- n 3)] [(.substring token 0 i) (.substring token i)])
            :default
                [token])))

(defn- joinT
    ([sentences] (joinT sentences []))
    ([is os]
        (if (seq is)
            (let [s1 (first is) s2 (second is)]
                (if (and (seq s1) (seq s2) (or
                        (let [eos (last s1)]
                            (and (<= 2 (count eos) 3) (Character/isUpperCase (first eos)) (= (last eos) \.)))
                        (let [fos (first s2)]
                            (or (@hunAbbrev* fos) (@hunAbbrev* (.toLowerCase fos))))))
                    (recur (cons (concat s1 s2) (rest (rest is))) os)
                    (recur (rest is) (conj os s1))))
            os)))

(defn tokenize [sentence]
    (let [sentence (normalize sentence)]
        (->> (.extractWords @tokenizer* sentence) eosApart #_(mapcat aposApart))))

(defn split [text]
    (let [text (normalize text)]
        (->> (.extractSentences @splitter* text @tokenizer*) joinT (map eosApart))))

(defn -main []
    (let [t "A 2014-es választások előtt túl jó lehetőséget adna az ellenzék kezébe a dohányboltok profitját nyirbáló kezdeményezés."]
        (doseq [sentence (split t)] (doseq [token sentence] (println token)) (println))))
