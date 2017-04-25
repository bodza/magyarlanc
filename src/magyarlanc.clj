(ns magyarlanc
    (:require [clojure.java.io :as io] [clojure.string :as str] [magyarlanc.gui :as gui])
    (:import [magyarlanc Dependency Morphology])
    (:gen-class))

(defn- in* [file]
    (io/reader (if (seq file) file *in*)))

(defn- lines-in [file]
    (filter seq (map str/trim (line-seq (in* file)))))

(defn- gather [f s]
    (lazy-seq (when-let [s (seq (drop-while (complement f) s))] (cons (take-while f s) (gather f (drop-while f s))))))

(defn- tokens-in [file]
    (gather seq (map str/trim (line-seq (in* file)))))

(defn- pretty [lines]
    (apply str (mapcat conj (map #(vec (interpose \tab %)) lines) (repeat \newline))))

(defn- pretty* [lines apple]
    (doseq [line lines] (.. apple (append (pretty line)) (append \newline))) apple)

(defn- text-out [text file]
    (if (seq file) (with-open [out (io/writer file)] (.flush (pretty* text out))) (.flush (pretty* text *out*))))

(defn -main [& args]
    (let [argc (count args) usage "usage: -mode gui|morana|morphparse|tokenized|depparse"]
        (if (and (pos? argc) (even? argc))
            (let [argm (apply array-map args) in (argm "-input") out (argm "-output")]
                (case (argm "-mode")
                    "gui"        (gui/-main)
                    "morana"     (doseq [line (lines-in in)] (println (str (Morphology/getMorphologicalAnalyses line))))
                    "morphparse" (text-out (Morphology/morphParse (lines-in in)) out)
                    "tokenized"  (text-out (Morphology/morphParse (tokens-in in)) out)
                    "depparse"   (text-out (Dependency/depParse (lines-in in)) out)

                    (.println *err* usage)))
            (.println *err* usage))))
