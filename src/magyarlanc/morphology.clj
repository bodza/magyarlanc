(ns magyarlanc.morphology
    (:require [clojure.java.io :as io] [clojure.string :as str]
              [magyarlanc.hunsplitter :as hspl] [magyarlanc.krtools :as kr] [magyarlanc.msdtools :as msd] [magyarlanc.rfsa :as rfsa])
    (:import [java.util HashSet List Set])
    (:import [edu.stanford.nlp.ling TaggedWord]
             [edu.stanford.nlp.tagger.maxent SzteMaxentTagger])
    (:import [magyarlanc KRTools$KRPOS Morphology$MorAna])
  #_(:gen-class))

(defn- morAna
    ([tuple] #_(assert-args (== (count tuple) 2) "[lemma msd]") (apply morAna tuple))
    ([lemma msd] (Morphology$MorAna. lemma msd)))

(defn- readCorpus [file]
    (with-open [reader (io/reader (io/resource file))]
        (into {} (map #(let [[word & ans] (str/split % #"\t")] [word (into #{} (map morAna (partition 2 ans)))]) (line-seq reader)))))

(defn- readFrequencies [file]
    (with-open [reader (io/reader (io/resource file))]
        (into {} (map #(let [[msd freq] (str/split % #"\t")] [msd (Integer/parseInt freq)]) (line-seq reader)))))

(def ^:private corpus* (delay (readCorpus "data/25.lex")))

(def ^:private frequencies* (delay (readFrequencies "data/25.freq")))

(def ^:private tagger* (delay (doto (SzteMaxentTagger. "./data/25.model") (.setVerbose false))))

(defn morphParseTokens [tokens]
    (.morphSentence @tagger* (into-array tokens)))

(defn morphParseSentence [sentence]
    (morphParseTokens (hspl/tokenize sentence)))

(defn morphParse [text]
    (map morphParseTokens (hspl/split text)))

;       public static class MorAna implements Comparable<MorAna>
;       {
;           private String lemma;
;           private String msd;

;           public MorAna(String lemma, String msd)
;           {
;               this.lemma = lemma;
;               this.msd = msd;
;           }

;           public String toString()
;           {
;               return lemma + "@" + msd;
;           }

;           public final String getLemma()
;           {
;               return lemma;
;           }

;           public final String getMsd()
;           {
;               return msd;
;           }

;           public int compareTo(MorAna morAna)
;           {
;               int cmp = lemma.compareTo(morAna.lemma);

;               if (cmp == 0)
;                   cmp = msd.compareTo(morAna.msd);

;               return cmp;
;           }

;           public boolean equals(MorAna morAna)
;           {
;               return (compareTo(morAna) == 0);
;           }

; user=> (deftype Pair [a b])
; user.Pair
; user=> (= (Pair. 0 0) (Pair. 0 0))
; false
; user=> (into #{} [(Pair. 0 0) (Pair. 1 1) (Pair. 0 0)])
; #{#<Pair user.Pair@5de3182> #<Pair user.Pair@6497d63> #<Pair user.Pair@38eed810>}

; user=> (deftype Tuple [a b]
;          Object
;          (equals [this other]
;            (and (= (.a this) (.a other))
;                 (= (.b this) (.b other))))
;          (toString [this]
;            (str "<" (.a this) "," (.b this) ">"))
;          (hashCode [this]
;            (hash {:a (.a this) :b (.b this)}))
;          Comparable
;          (compareTo [this that]
;            (compare [(.a this) (.b this)]
;                     [(.a that) (.b that)])))
; user.Tuple
; user=> (= (Tuple. 0 0) (Tuple. 0 0))
; true
; user=> (into #{} [(Tuple. 0 0) (Tuple. 1 1) (Tuple. 0 0)])
; #{#<Tuple <0,0>> #<Tuple <1,1>>}
;       }

; adott szó csak írásjeleket tartalmaz-e
(defn- punctation? [spelling]
    (not-any? #(Character/isLetterOrDigit %) spelling))

(def ^:private punctations* (delay (into #{} (map str "!,-.:;?–"))))	; red!

(defn- puncs [word]
    (if (punctation? word) #{(morAna word (cond (@punctations* word) word (= word "§") "Nn-sn" :default "K"))}))

(defn- readErrata [file]
    (with-open [reader (io/reader (io/resource file))]
        (into {} (map #(let [[k v] (str/split % #"\t")] (if-not (= k v) [k v])) (line-seq reader)))))

(def ^:private errata* (delay (readErrata "data/errata.txt")))

(declare numberGuess guessRomanNumber analyseHyphenicCompoundWord analyseCompoundWord hyphenicGuess)

; adott szó lehetséges morfológiai elemzéseinek meghatározása
(defn getMorphologicalAnalyses [word]
    ; írásjel
    (or (puncs word)
        ; szerepel a corpus.lex-ben (kisbetűvel)
        (@corpus* word) (@corpus* (.toLowerCase word))
        ; szám
        (let [ans (numberGuess word)] (if (seq ans) ans
            ; római szám
            (let [ans (transient (guessRomanNumber word))]

                ; rfsa
                (reduce conj! ans (mapcat #(kr/getMSD %) (rfsa/analyse word)))

                (if (zero? (count ans))
                    ; (kötőjeles) összetett szó
                    (let [i (.indexOf word "-")
                          cs (if (< 1 i) (analyseHyphenicCompoundWord word) (analyseCompoundWord (.toLowerCase word)))]
                        (reduce conj! ans (mapcat #(kr/getMSD %) cs))))

                (if (zero? (count ans))
                    ; guess (Bush-nak, Bush-kormányhoz)
                    (let [i (.lastIndexOf word "-")]
                        (if (< 1 i)
                            (reduce conj! ans (hyphenicGuess (.substring word 0 i) (.substring word (inc i)))))))

                (if (zero? (count ans))
                    ; téves szavak
                    (when-let [corr (or (@errata* word) (@errata* (.toLowerCase word)) nil)]
                        (if-not (= corr word)
                            (reduce conj! ans (getMorphologicalAnalyses corr)))))

                (persistent! ans))))))

;       public static String[] getPossibleTags(String word, Set<String> possibleTags)
;       {
;           Set<MorAna> morAnas = getMorphologicalAnalyses(word);
;           Set<String> tags = new HashSet<String>();

;           for (MorAna morAna : morAnas)
;           {
;               String reduced = msd/reduceMSD(morAna.getMsd());
;               if (possibleTags.contains(reduced))
;               {
;                   tags.add(reduced);
;               }
;           }

;           if (tags.size() == 0)
;           {
;               tags.add("X");
;           }

;           return tags.toArray(new String[tags.size()]);
;       }

;       public static List<TaggedWord> recoverTags(List<TaggedWord> sentence)
;       {
;           for (TaggedWord tw : sentence)
;           {
;               int max = -1;
;               MorAna argmax = null;

;               for (MorAna morAna : getMorphologicalAnalyses(tw.word()))
;               {
;                   int freq = @frequencies*.containsKey(morAna.getMsd()) ? @frequencies*.get(morAna.getMsd()) : 0;

;                   if (!morAna.getMsd().equals(null))
;                   {
;                       if (msd/reduceMSD(morAna.getMsd()).equals(tw.tag()) && (max < freq))
;                       {
;                           argmax = morAna;
;                           max = freq;
;                       }
;                   }
;               }

;               if (argmax != null)
;               {
;                   tw.setValue(argmax.getLemma());
;                   tw.setTag(argmax.getMsd());
;               }
;               else
;               {
;                   tw.setValue(tw.word());
;               }
;           }

;           return sentence;
;       }

(dorun (map #(intern *ns* % (KRTools$KRPOS/valueOf (name %))) '(UTT_INT ART NUM PREV NOUN VERB ADJ ADV)))	; ^:private

(defn- isCompatibleAnalyises [kr1 kr2]
    (let [pos1 (kr/getPOS kr1) pos2 (kr/getPOS kr2)]
        (not (or
            (= pos2 UTT_INT)                    ; UTT-INT nem lehet a második rész
            (= pos2 ART)                        ; ART nem lehet a második rész
            (and (= pos2 NUM) (not= pos1 NUM))  ; NUM előtt csak NUM állhat
            (= pos2 PREV)                       ; PREV nem lehet a második rész
            (and (= pos1 NOUN) (= pos2 ADV))    ; NOUN + ADV letiltva
            (and (= pos1 VERB) (= pos2 ADV))    ; VERB + ADV letiltva
            (and (= pos1 PREV) (= pos2 NOUN))   ; PREV + NOUN letiltva
            (and (= pos1 ADJ) (= pos2 VERB))    ; ADJ + VERB letiltva
            (and (= pos1 VERB) (= pos2 NOUN))   ; VERB + NOUN letiltva

            ; NOUN + VERB csak akkor lehet, ha van a NOUN-nak <CAS>
            (and (= pos1 NOUN) (= pos2 VERB) (not (.contains kr1 "CAS")))

            ; NOUN + VERB<PAST><DEF> és nincs a NOUN-nak <CAS> akkor /ADJ
          #_(and (= pos1 NOUN) (= pos2 VERB) (not (.contains kr1 "CAS")) (.contains kr2 "<PAST><DEF>") #_(.contains kr2 "<DEF>"))
        ))))

(defn- bisectIndex [word]
    (let [n (dec (count word))]
        (loop [i 2]
            (if-not (< i n)
                0
                (if (and (seq (rfsa/analyse (.substring word 0 i))) (seq (rfsa/analyse (.substring word i))))
                    i
                    (recur (inc i)))))))

(defn- getCompatibleAnalises
    ([part1 part2]
        (getCompatibleAnalises part1 part2 false))
    ([part1 part2 hyphenic]
        (into #{} (remove nil?
            (for [a1 (rfsa/analyse part1) a2 (rfsa/analyse part2)]
                (let [kr1 (kr/getRoot a1) kr2 (kr/getRoot a2)]
                    (if (isCompatibleAnalyises kr1 kr2)
                        (.replace kr2 "$" (str "$" part1 (if hyphenic "-"))))))))))

(defn analyseCompoundWord [word]
    (let [bi (bisectIndex word)]
        (if (< 0 bi)
            (getCompatibleAnalises (.substring word 0 bi) (.substring word bi))
            (let [ans (transient #{}) n (dec (count word))]
                (loop [i 2]
                    (if-not (< i n)
                        (persistent! ans)
                        (let [part1 (.substring word 0 i) ans1 (rfsa/analyse part1)]
                            (if (seq ans1)
                                (let [part2 (.substring word i) bi (bisectIndex part2)]
                                    (if (< 0 bi)
                                        (doseq [a1 ans1 a2 (getCompatibleAnalises (.substring part2 0 bi) (.substring part2 bi))]
                                            (let [kr1 (kr/getRoot a1) kr2 (kr/getRoot a2)]
                                                (if (isCompatibleAnalyises kr1 kr2)
                                                    (conj! ans (.replace kr2 "$" (str "$" part1)))))))))
                            (recur (inc i)))))))))

(defn analyseHyphenicCompoundWord [word]
    (let [ans (transient #{}) hp (.indexOf word "-")]
        (if (< 0 hp)
            (let [part1 (.substring word 0 hp) part2 (.substring word (inc hp))]
                (if (< 0 (bisectIndex (str part1 part2)))
                    ; a kötőjel előtti és a kötőjel utáni résznek is van elemzése (pl.: adat-kezelőt)
                    (reduce conj! ans (getCompatibleAnalises part1 part2 true))
                    (let [ans1 (rfsa/analyse part1) bi (bisectIndex part2)]
                        (if (and (seq ans1) (< 0 bi))
                            ; a kötőjel előtti résznek van elemzése, a kötőjel utáni rész két részre bontható
                            (doseq [a1 ans1 a2 (getCompatibleAnalises (.substring part2 0 bi) (.substring part2 bi))]
                                (let [kr1 (kr/getRoot a1) kr2 (kr/getRoot a2)]
                                    (if (isCompatibleAnalyises kr1 kr2)
                                        (conj! ans (.replace kr2 "$" (str "$" part1 "-"))))))
                            (let [bi (bisectIndex part1) ans2 (rfsa/analyse part2)]
                                (if (and (< 0 bi) (seq ans2))
                                    ; a kötőjel előtti rész két részre bontható, a kötőjel utáni résznek van elemzése
                                    (doseq [a1 (getCompatibleAnalises (.substring part1 0 bi) (.substring part1 bi)) a2 ans2]
                                        (let [kr1 (kr/getRoot a1) kr2 (kr/getRoot a2)]
                                            (if (isCompatibleAnalyises kr1 kr2)
                                                (conj! ans (.replace kr2 "$" (str "$" part1 "-")))))))))))))
        (persistent! ans)))

; A morPhonGuess függvény egy ismeretlen (nem elemezhető) főnévi szótő és tetszőleges suffix guess-elésére szolgál.
; A guess-elés során az adott suffix-et a rendszer morPhonDir szótárának elemeire illesztve probáljuk elemezni.
; A szótár reprezentálja a magyar nyelv minden (nem hasonuló) illeszkedési szabályát, így biztosak lehetünk benne,
; hogy egy valós toldalék mindenképp illeszkedni fog legalább egy szótárelemre. Például egy -hoz rag esetén először
; a "köd" elemre próbálunk illeszteni, majd elemezni. A kapott szóalak így a "ködhoz" lesz, melyre a KR elemzőnk
; nem ad elemzést. A következő szótárelem a "talány", a szóalak a "talányhoz" lesz, melyre megkapjuk az Nc-st
; (külső közelítő/allative) főnévi elemzést.

(def ^:private morPhonDir #{"talány" "némber" "sün" "fal" "holló" "felhő" "kalap" "hely" "köd"})

(defn morPhonGuess [root suffix]
    (let [ans (transient #{})]
        (doseq [guess morPhonDir kr (rfsa/analyse (str guess suffix)) stem (kr/getMSD kr)]
            (let [msd (.getMsd stem)]
                (if (.startsWith msd "N") ; csak főnevi elemzesek
                    (conj! ans (morAna root msd)))))
        (persistent! ans)))

(defn hyphenicGuess [root suffix]
    (let [ans (transient (morPhonGuess root suffix))] ; kötőjeles suffix (pl.: Bush-hoz)
        (doseq [kr (rfsa/analyse suffix) stem (kr/getMSD kr)] ; suffix főnév (pl.: Bush-kormánnyal)
            (let [msd (.getMsd stem)]
                (if (.startsWith msd "N") ; csak főnevi elemzesek
                    (conj! ans (morAna (str root "-" (.getLemma stem)) msd)))))
        (persistent! ans)))

; Minden számmal kezdődő token elemzését reguláris kifejezések végzik.
; Egy szóalakhoz több elemzés is tartozhat.
; Egy számmal kezdődő token lehet főnév (N) (pl.: 386-os@Nn-sn),
; melléknév (pl.: 16-ai@Afp-sn), számnév (pl.: 5.@Mo-snd)
; vagy nyílt tokenosztályba tartozó (pl.: 20%@Onp-sn).

(def ^:private abc #"([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)")

(def ^:private rxN (mapv re-pattern
[
  #__0       #"\d+.*" 
  #__1  (str #"(\d+[0-9\.,%-/]*-(as|ás|es|os|ös)+)" abc) ; 1-es 1.3-as 1,5-ös 1/6-os 16-17-es [Afp-sn, Nn-sn]
  #__2       #"\d+[0-9\.,-/]*-i"                         ; 16-i
  #__3  (str #"(\d+-(ai|ei|jei)+)"                  abc) ; 16-(ai/ei/jei)
  #__4  (str #"([\+|\-]{1}\d+[0-9\.,-/]*)-??"       abc) ; +12345
  #__5  (str #"(\d+-\d+)-??"                        abc) ; 12345-12345
  #__6  (str #"((\d{1,2})[\.:](\d{2}))-??"          abc) ; 12:30 12.30 Ont-sn
  #__7  (str #"(\d+,\d+-\d+)-??"                    abc) ; 123,45-12345
  #__8  (str #"(\d+-\d+,\d+)-??"                    abc) ; 12345-12345,12345
  #__9  (str #"(\d+,\d+-\d+,\d+)-??"                abc) ; 12345,12345-12345,12345
  #_10  (str #"(\d+\.\d+,\d+)-??"                   abc) ; 12345.12345,12345
  #_11  (str #"(\d+:\d+)-??"                        abc) ; 10:30
  #_12  (str #"(\d+\.\d+[0-9\.]*)-??"               abc) ; 12345.12345.1234-.
  #_13  (str #"(\d+,\d+)-??"                        abc) ; 12,3-nak
  #_14  (str #"(\d+)-??"                            abc) ; 20-nak
  #_15  (str #"((\d+-??\d*)\.)-??"                  abc) ; 20.
  #_16  (str #"((\d{1,2})-(á|é|jé))"                abc) ; 16-áig
  #_17  (str #"((\d{1,2})-(a|e|je))"              #"()") ; 16-a
  #_18  (str #"(\d+,??\d*%)-??"                     abc) ; 50%
]))

(defn- nounToNumeral [noun numeral]
    (let [sb (StringBuilder. numeral) n (.length noun)]
        (doseq [[a b]
        [
            [ 3  3] ; szám
            [ 4  4] ; eset
            [ 8 10] ; birtokos száma
            [ 9 11] ; birtokos személye
            [10 12] ; birtok(olt) száma
        ]]
            (if (a < n) (.setCharAt sb b (.charAt noun a))))
        (kr/chopMSD (.toString sb))))

(defn- nounToOther [noun other]
    (let [sb (StringBuilder. other) n (.length noun)]
        (doseq [[a b]
        [
            [ 3  4] ; szám
            [ 4  5] ; eset
            [ 8  9] ; birtokos száma
            [ 9 10] ; birtokos személye
            [10 11] ; birtok(olt) száma
        ]]
            (if (a < n) (.setCharAt sb b (.charAt noun a))))
        (kr/chopMSD (.toString sb))))

(defn- nounToNoun [noun other]
    (let [sb (StringBuilder. other) n (.length noun)]
        (doseq [[a b]
        [
            [3 3] ; szám
            [4 4] ; eset
        ]]
            (if (a < n) (.setCharAt sb b (.charAt noun a))))
        (kr/chopMSD (.toString sb))))

(def ^:private romans* (delay (into {} (map vector "IVXLCDM" [1 5 10 50 100 500 1000]))))

#_(defn- romanToArabic [roman]
    (reduce + (map #(let [[n m] %] (if (< n m) (- n) n)) (partition 2 1 (conj (mapv @romans* roman) 0)))))

(defn- romanToArabic [roman]
    (let [rs (mapv @romans* roman) n (count rs)]
        (reduce + (map #(let [r (rs %) m (inc %)] (if (and (< m n) (< r (rs m))) (- r) r)) (range n)))))

(defn- isDate [spelling]
    (not-any? #(< 31 (Integer/parseInt %)) (str/split spelling #"-")))

; számmal kezdődő token elemzése
(defn numberGuess [number]
    (let [stems (transient #{})]
        (if (re-matches (rxN 0) number)
            (or
                ; 386-osok (386-(os))(ok)
                (when-let [[_ root _ suffix] (re-matches (rxN 1) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (let [msd (.getMsd stem)]
                                (conj! stems (morAna root msd))
                                (conj! stems (morAna root (.replace msd (.substring "Nn-sn" 0 2) "Afp"))))))
                    (when (zero? (count stems))
                        (conj! stems (morAna root "Afp-sn"))
                        (conj! stems (morAna root "Nn-sn")))
                    stems)

                ; 16-i
                (when-let [_ (re-matches (rxN 2) number)]
                    (conj! stems (morAna number "Afp-sn"))
                    (conj! stems (morAna number "Onf-sn"))
                    stems)

                ; 16-(ai/ei) 1-jei
                (when-let [[_ root _ suffix] (re-matches (rxN 3) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (conj! stems (morAna root (str "Afp-" (.. stem getMsd (substring 3)))))))
                    (when (zero? (count stems))
                        (conj! stems (morAna root "Afp-sn")))
                    stems)

                ; +/-12345
                (when-let [[_ root suffix] (re-matches (rxN 4) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (conj! stems (morAna root (nounToOther (.getMsd stem) "Ons----------")))))
                    (when (zero? (count stems))
                        (conj! stems (morAna number "Ons-sn")))
                    stems)

                ; 12:30 12.30 Ont-sn
                (when-let [[_ root hour minute suffix] (re-matches (rxN 6) number)]
                    (when (and (< (Integer/parseInt hour) 24) (< (Integer/parseInt minute) 60))
                        (when (seq suffix)
                            (doseq [stem (morPhonGuess root suffix)]
                                (conj! stems (morAna root (nounToOther (.getMsd stem) "Ont---------")))))
                        (when (zero? (count stems))
                            (conj! stems (morAna number "Ont-sn"))))
                    stems)

                ; 12345-12345-*
                (when-let [[_ root suffix] (re-matches (rxN 5) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (let [msd (.getMsd stem)]
                                (conj! stems (morAna root (nounToOther msd "Onr---------")))
                                (conj! stems (morAna root (nounToOther msd "Onf----------")))
                                (conj! stems (morAna root (nounToNumeral msd "Mc---d-------"))))))
                    (when (zero? (count stems))
                        (conj! stems (morAna number "Onr-sn"))
                        (conj! stems (morAna number "Onf-sn"))
                        (conj! stems (morAna number "Mc-snd")))
                    stems)

                ; 12345,12345-12345,12345-* ; 12345-12345,12345-* ; 12345,12345-12345-*
                (when-let [[_ root suffix] (or (re-matches (rxN 7) number) (re-matches (rxN 8) number) (re-matches (rxN 9) number))]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (conj! stems (morAna root (nounToNumeral (.getMsd stem) "Mf---d-------")))))
                    (when (zero? (count stems))
                        (conj! stems (morAna number "Mf-snd")))
                    stems)

                ; 12345.12345,12345
                (when-let [[_ root suffix] (re-matches (rxN 10) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (conj! stems (morAna root (nounToOther (.getMsd stem) "Ond---------")))))
                    (when (zero? (count stems))
                        (conj! stems (morAna number "Ond-sn")))
                    stems)

                ; 10:30-*
                (when-let [[_ root suffix] (re-matches (rxN 11) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (let [msd (.getMsd stem)]
                                (conj! stems (morAna root (nounToOther msd "Onf---------")))
                                (conj! stems (morAna root (nounToOther msd "Onq---------")))
                                (conj! stems (morAna root (nounToOther msd "Onr---------"))))))
                    (when (zero? (count stems))
                        (conj! stems (morAna number "Onf-sn"))
                        (conj! stems (morAna number "Onq-sn"))
                        (conj! stems (morAna number "Onr-sn")))
                    stems)

                ; 12345.12345.1234-.
                (when-let [[_ root suffix] (re-matches (rxN 12) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (let [msd (.getMsd stem)]
                                (conj! stems (morAna root (nounToOther msd "Oi----------")))
                                (conj! stems (morAna root (nounToOther msd "Ond---------"))))))
                    (when (zero? (count stems))
                        (conj! stems (morAna number "Oi--sn"))
                        (conj! stems (morAna number "Ond-sn")))
                    stems)

                ; 16-a 17-e 16-áig 17-éig 1-je 1-jéig
                (when-let [[_ _ root miez suffix] (or (re-matches (rxN 16) number) (re-matches (rxN 17) number))]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (let [msd (.getMsd stem)]
                                (conj! stems (morAna root (nounToNumeral msd "Mc---d----s3-")))
                                (if (isDate root)
                                    (conj! stems (morAna (str root ".") (nounToNoun msd (str (.substring "Nn-sn" 0 2) "------s3-")))))
                                (if (= miez "�")
                                    (conj! stems (morAna root (nounToNumeral msd "Mc---d------s")))))))
                    (when (zero? (count stems))
                        (conj! stems (morAna root "Mc-snd----s3"))
                        (when (isDate root)
                            (conj! stems (morAna (str root ".") (str "Nn-sn" "---s3")))))
                    stems)

                ; 50%
                (when-let [[_ root suffix] (re-matches (rxN 18) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (conj! stems (morAna root (nounToOther (.getMsd stem) "Onp---------")))))
                    (when (zero? (count stems))
                        (conj! stems (morAna root "Onp-sn")))
                    stems)

                ; 12,3-nak
                (when-let [[_ root suffix] (re-matches (rxN 13) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (conj! stems (morAna root (nounToNumeral (.getMsd stem) "Mf---d-------")))))
                    (when (zero? (count stems))
                        (conj! stems (morAna number "Mf-snd")))
                    stems)

                ; 20-nak
                (when-let [[_ root suffix] (re-matches (rxN 14) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (conj! stems (morAna root (nounToNumeral (.getMsd stem) "Mc---d-------")))))
                    (when (zero? (count stems))
                        (conj! stems (morAna number "Mc-snd")))
                    stems)

                ; 15.
                (when-let [[_ root day suffix] (re-matches (rxN 15) number)]
                    (when (seq suffix)
                        (doseq [stem (morPhonGuess root suffix)]
                            (let [msd (.getMsd stem)]
                                (conj! stems (morAna root (nounToNumeral msd "Mo---d-------")))
                                (if (isDate day)
                                    (conj! stems (morAna root msd))))))
                    (when (zero? (count stems))
                        (conj! stems (morAna number "Mo-snd"))
                        (when (isDate day)
                            (conj! stems (morAna number "Nn-sn"))
                            (conj! stems (morAna number (str "Nn-sn" "---s3")))))
                    stems)

                (do
                    (when (zero? (count stems))
                        (conj! stems (morAna number "Oi--sn")))
                    stems)))

        (persistent! stems)))

(def ^:private rMDC #"(CM|CD|D?C{0,3})")
(def ^:private rCLX #"(XC|XL|L?X{0,3})")
(def ^:private rXVI #"(IX|IV|V?I{0,3})")

(def ^:private rxR (mapv re-pattern
[
   #_0  (str #"M{0,4}" rMDC rCLX rXVI)
   #_1  (str #"M{0,4}" rMDC rCLX rXVI #"\.")
   #_2  (str #"M{0,4}" rMDC rCLX rXVI #"-M{0,4}" rMDC rCLX rXVI)
   #_3  (str #"M{0,4}" rMDC rCLX rXVI #"-M{0,4}" rMDC rCLX rXVI #"\.")
]))

(defn guessRomanNumber [word]
    (let [stems (transient #{})]
        (cond
            (re-matches (rxR 0) word) ; MCMLXXXIV
            (let [n (romanToArabic word)]
                (conj! stems (morAna (str n) "Mc-snr")))

            (re-matches (rxR 1) word) ; MCMLXXXIV.
            (let [e (dec (.length word))
                  n (romanToArabic (.substring word 0 e))]
                (conj! stems (morAna (str n ".") "Mo-snr")))

            (re-matches (rxR 2) word) ; MCMLXXXIV-MMIX
            (let [i (.indexOf word "-")
                  n (romanToArabic (.substring word 0 i))
                  m (romanToArabic (.substring word (inc i)))]
                (conj! stems (morAna (str n "-" m) "Mc-snr")))

            (re-matches (rxR 3) word) ; MCMLXXXIV-MMIX.
            (let [i (.indexOf word "-") e (dec (.length word))
                  n (romanToArabic (.substring word 0 i))
                  m (romanToArabic (.substring word (inc i) e))]
                (conj! stems (morAna (str n "-" m ".") "Mo-snr"))))

        (persistent! stems)))

(comment
    (getMorphologicalAnalyses "lehet")

    (morPhonGuess "London" "ban")

    (hyphenicGuess "Bush" "hoz")
    (hyphenicGuess "Bush" "kormánynak")

    (numberGuess "386-osok")
    (numberGuess "16-ai")
    (numberGuess "5.")
    (numberGuess "20%"))
