(ns magyarlanc.msdtools
    (:require [clojure.string :as str])
    (:import [magyarlanc KRTools])
  #_(:gen-class))

; reduce noun
(defn- reduceN [msd]
    (let [sb (StringBuilder. "N")]
        ; dative/genitive ; superessive/essive
        (if (< 4 (.length msd)) (let [x (.charAt msd 4)]
            (if (or (= x \d) (= x \g) (= x \p))
                (.append sb x))))
        ; N.-..---s3
        (if (re-find #"N.-..---s3" msd)
            (.append sb \s))
        ; N.-..---..s
        (if (re-find #"N.-..---..s" msd)
            (.append sb \z))
        (.toString sb)))

; reduce verb
(defn- reduceV [msd]
    (let [n (.length msd)] (cond
        (.startsWith msd "Va")               "Va"

        (.startsWith msd "Vsis") (cond               ; múlt idejű műveltető igealakok
            (.equals msd "Vsis3p---y")       "Vs3py" ; festettek
            (.equals msd "Vsis1s---y")       "Vs1y"  ; festettem
            (.endsWith msd "---y")           "Vsy"
            :default                         "Vs")

        (.equals msd "Vsip1s---n")           "Vs"    ; festetek
        (re-find #"V[^a]cp[12]p---y" msd)    "Vcp"   ; olvasnánk
        (re-find #"V[^a]ip1s---y" msd)       "Vip"   ; eszek eszem

        (re-find #"V[^a]cp3p---y" msd) (cond         ; festetnék
            (= (.charAt msd 1) \s)           "Vs3p"
            :default                         "V3p")

        (.equals msd "Vmcp1s---n")           "V1"    ; festenék

        (re-find #"V[^a]is1[sp]---y" msd) (cond      ; festettem
            (= (.charAt msd 1) \s)           "Vs1y"
            :default                         "Vy")

        (and (< 2 n) (= (.charAt msd 2) \m)) "Vm"    ; V-m felszólító mód
        (and (< 3 n) (= (.charAt msd 3) \p)) "Vp"    ; V--p jelen idő egybeeshet múlttal pl.: ért
        :default "V")))

; reduce adjective
(defn- reduceA [msd]
    (let [sb (StringBuilder. "A")]
        ; igenevek
        (if (not= (.charAt msd 1) \f)
            (.append sb \r))
        ; dative/genitive ; superessive/essive
        (if (< 5 (.length msd)) (let [x (.charAt msd 5)]
            (if (or (= x \d) (= x \g) (= x \p))
                (.append sb x))))
        ; A..-..-.--s3
        (if (re-find #"A..-..-.--s3" msd)
            (.append sb \s))
        ; A..-..-.--..s
        (if (re-find #"A..-..-.--..s" msd)
            (.append sb \z))
        (.toString sb)))

; reduce pronoun
(defn- reduceP [msd]
    (let [sb (StringBuilder. "P") n (.length msd)]
        ; Pq Pr Pp
        (if (< 1 n) (let [x (.charAt msd 1)]
            (if (or (= x \q) (= x \r) (= x \p))
                (.append sb (if (= x \p) \e x)))))
        ; dative/genitive ; superessive/essive
        (if (< 5 n) (let [x (.charAt msd 5)]
            (if (or (= x \d) (= x \g) (= x \p))
                (.append sb x))))
        (.toString sb)))

; reduce adverb
(defn- reduceR [msd]
    (let [sb (StringBuilder. "R")]
        ; Rq Rr Rp
        (if (< 1 (.length msd)) (let [x (.charAt msd 1)]
            (if (or (= x \q) (= x \r) (= x \p))
                (.append sb x))))
        (.toString sb)))

; reduce numeral
(defn- reduceM [msd]
    (let [sb (StringBuilder. "M") n (.length msd)]
        ; fractal
        (if (< 1 n) (let [x (.charAt msd 1)]
            (if (= x \f)
                (.append sb x))))
        ; dative/genitive ; superessive/essive
        (if (< 4 n) (let [x (.charAt msd 4)]
            (if (or (= x \d) (= x \g) (= x \p))
                (.append sb x))))
        ; M.-...-.--s3
        (if (re-find #"M.-...-.--s3" msd)
            (.append sb \s))
        ; M.-...-.--..s
        (if (re-find #"M.-...-.--..s" msd)
            (.append sb \z))
        (.toString sb)))

; reduce other
(defn- reduceO [msd]
    (let [sb (StringBuilder. "O")]
        ; dative/genitive ; superessive/essive
        (if (< 5 (.length msd)) (let [x (.charAt msd 5)]
            (if (or (= x \d) (= x \g) (= x \p))
                (.append sb x))))
        ; O..-..---s3
        (if (re-find #"O..-..---s3" msd)
            (.append sb \s))
        ; O..-..---..s
        (if (re-find #"O..-..---..s" msd)
            (.append sb \z))
        (.toString sb)))

(defn reduceMSD [msd]
    (if (< 1 (count msd)) (case (first msd)
            \N (reduceN msd)
            \V (reduceV msd)
            \A (reduceA msd)
            \P (reduceP msd)
            \R (reduceR msd)
            \M (reduceM msd)
            \O (reduceO msd)
            \C msd
            #_(\T \S \I \X \Y \Z) (str (first msd)))
        msd))

(defn- vec- [msd n]
    (vec (take n (concat msd (repeat \-)))))

(defn- app- [sb conll x]
    (.. sb (append conll) (append (if (= x \-) "none" x))))

; extract noun
(defn- parseN [msd]
    (let [sb (StringBuilder.) msd (vec- msd 11)]
        (app- sb "SubPOS=" (msd 1))
        ; 2 (not used)
        (app- sb "|Num=" (msd 3))
        (app- sb "|Cas=" (msd 4))
        ; 5 (not used)
        ; 6 (not used)
        ; 7 (not used)
        (app- sb "|NumP=" (msd 8))
        (app- sb "|PerP=" (msd 9))
        (app- sb "|NumPd=" (msd 10))
        (.toString sb)))

; extract verb
(defn- parseV [msd]
    (let [sb (StringBuilder.) msd (vec- msd 11)]
        (app- sb "SubPOS=" (msd 1))
        (app- sb "|Mood=" (msd 2))
        (when (not= (msd 2) \n)
            (app- sb "|Tense=" (msd 3)))
        (app- sb "|Per=" (msd 4))
        (app- sb "|Num=" (msd 5))
        ; 6 (not used)
        ; 7 (not used)
        ; 8 (not used)
        (when (not= (msd 2) \n)
            (app- sb "|Def=" (msd 9)))
        ; 10 (not used)
        (.toString sb)))

; extract adjective
(defn- parseA [msd]
    (let [sb (StringBuilder.) msd (vec- msd 13)]
        (app- sb "SubPOS=" (msd 1))
        (app- sb "|Deg=" (msd 2))
        ; 3 (not used)
        (app- sb "|Num=" (msd 4))
        (app- sb "|Cas=" (msd 5))
        ; 6 (not used)
        ; 7 (not used)
        ; 8 (not used)
        ; 9 (not used)
        (app- sb "|NumP=" (msd 10))
        (app- sb "|PerP=" (msd 11))
        (app- sb "|NumPd=" (msd 12))
        (.toString sb)))

; extract pronoun
(defn- parseP [msd]
    (let [sb (StringBuilder.) msd (vec- msd 17)]
        (app- sb "SubPOS=" (msd 1))
        (app- sb "|Per=" (msd 2))
        ; 3 (not used)
        (app- sb "|Num=" (msd 4))
        (app- sb "|Cas=" (msd 5))
        (app- sb "|NumP=" (msd 6))
        ; 7 (not used)
        ; 8 (not used)
        ; 9 (not used)
        ; 10 (not used)
        ; 11 (not used)
        ; 12 (not used)
        ; 13 (not used)
        ; 14 (not used)
        (app- sb "|PerP=" (msd 15))
        (app- sb "|NumPd=" (msd 16))
        (.toString sb)))

; extract article
(defn- parseT [msd]
    (let [sb (StringBuilder.) msd (vec- msd 2)]
        (app- sb "SubPOS=" (msd 1))
        (.toString sb)))

; extract adverb
(defn- parseR [msd]
    (let [sb (StringBuilder.) msd (vec- msd 6)]
        (app- sb "SubPOS=" (msd 1))
        (app- sb "|Deg=" (msd 2))
        ; 3 (not used)
        (when (= (msd 1) \l)
            (app- sb "|Num=" (msd 4))
            (app- sb "|Per=" (msd 5)))
        (.toString sb)))

; extract adposition
(defn- parseS [msd]
    (let [sb (StringBuilder.) msd (vec- msd 2)]
        (app- sb "SubPOS=" (msd 1))
        (.toString sb)))

; extract conjucion
(defn- parseC [msd]
    (let [sb (StringBuilder.) msd (vec- msd 4)]
        (app- sb "SubPOS=" (msd 1))
        (app- sb "|Form=" (msd 2))
        (app- sb "|Coord=" (msd 3))
        (.toString sb)))

; extract numeral
(defn- parseM [msd]
    (let [sb (StringBuilder.) msd (vec- msd 13)]
        (app- sb "SubPOS=" (msd 1))
        ; 2 (not used)
        (app- sb "|Num=" (msd 3))
        (app- sb "|Cas=" (msd 4))
        (app- sb "|Form=" (msd 5))
        ; 6 (not used)
        ; 7 (not used)
        ; 8 (not used)
        ; 9 (not used)
        (app- sb "|NumP=" (msd 10))
        (app- sb "|PerP=" (msd 11))
        (app- sb "|NumPd=" (msd 12))
        (.toString sb)))

; extract interjection
(defn- parseI [msd]
    (if (== (.length msd) 1) "_" (str "SubPOS=" (.charAt msd 1))))

; extract other/open
(defn- parseO [msd]
    (let [sb (StringBuilder.) msd (vec- msd 12)]
        (app- sb "SubPOS=" (msd 1))
        (when (or (= (msd 1) \e) (= (msd 1) \d) (= (msd 1) \n))
            (app- sb "|Type=" (msd 2)))
        ; 3 (not used)
        (app- sb "|Num=" (msd 4))
        (app- sb "|Cas=" (msd 5))
        ; 6 (not used)
        ; 7 (not used)
        ; 8 (not used)
        (app- sb "|NumP=" (msd 9))
        (app- sb "|PerP=" (msd 10))
        (app- sb "|NumPd=" (msd 11))
        (.toString sb)))

(def ^:private punctations* (delay (into #{} (map str "!,-.:;?–"))))	; red!

(defn msdToConllFeatures [lemma msd]
    (if (@punctations* lemma)
        "_"                          ; relevant punctation
        (case (first msd)
            \N (parseN msd)          ; noun
            \V (parseV msd)          ; verb
            \A (parseA msd)          ; adjective
            \P (parseP msd)          ; pronoun
            \T (parseT msd)          ; article
            \R (parseR msd)          ; adverb
            \S (parseS msd)          ; adposition
            \C (parseC msd)          ; conjuction
            \M (parseM msd)          ; numeral
            \I (parseI msd)          ; interjection
            \O (parseO msd)          ; open/other

            \K (str "SubPOS=" lemma) ; non relevant punctation

            #_(\X \Y \Z) "_")))      ; residual | abbrevation | ?

; convert the pattern to map, that contains the position of the feature in the MSD code
; eg. the noun map will be {SubPOS=1, Num=3, Cas=4, NumP=8, PerP=9, NumPd=10}

(defn- conllMap [conll]
    (into {} (map-indexed #(if (seq %2) [%2 (inc %1)]) (str/split conll #"\|"))))

; Patterns for the MSD attribute positions eg. the noun pattern contains,
; that the 1st character of a noun MSD code contains the SubPOS featurevalue,
; the 3rd character contains the Num featurevalue etc.
; It is important that the 2nd, 5th etc. characters are empty, that means it
; has no value, the representation in the MSD is a - sign.

(def ^:private         nounMap (conllMap "SubPOS||Num|Cas||||NumP|PerP|NumPd"))
(def ^:private         verbMap (conllMap "SubPOS|Mood|Tense|Per|Num||||Def"))
(def ^:private          adjMap (conllMap "SubPOS|Deg||Num|Cas|||||NumP|PerP|NumPd"))
(def ^:private      pronounMap (conllMap "SubPOS|Per||Num|Cas|NumP|||||||||PerP|NumPd"))
(def ^:private      articleMap (conllMap "SubPOS"))
(def ^:private       adverbMap (conllMap "SubPOS|Deg|Clitic|Num|Per"))
(def ^:private   adpositionMap (conllMap "SubPOS"))
(def ^:private  conjunctionMap (conllMap "SubPOS|Form|Coord"))
(def ^:private      numeralMap (conllMap "SubPOS||Num|Cas|Form|||||NumP|PerP|NumPd"))
(def ^:private interjectionMap (conllMap "SubPOS"))
(def ^:private        otherMap (conllMap "SubPOS|Type||Num|Cas||||NumP|PerP|NumPd"))

; possible conll-2009 feature names
(def ^:private possibleFeatures
    #{"SubPOS" "Num" "Cas" "NumP" "PerP" "NumPd" "Mood" "Tense" "Per" "Def" "Deg" "Clitic" "Form" "Coord" "Type"})

; split the features at |s and put the featurenames with its values to a map
(defn- featuresMap [features]
    (into {} (map #(let [[k v] (str/split % #"=")] (if (and (possibleFeatures k) (seq v)) [k v])) (str/split features #"\|"))))

; convert the features to MSD code using the MSD positions and featurevalues, that belongs to the current POS
(defn- to-msd [pos positionsMap fmap]
    (let [sb (.. (StringBuilder. 17) (append pos) (append "----------------"))]
        (doseq [[k v] fmap]
            (if-not (.equals v "none")
                (.setCharAt sb (positionsMap k) (first v))))

        ; főnévi igenevek: ha csak simán 'nézni' van, akkor nem kell, de ha 'néznie', akkor igen
        (if (and (= pos \V) (= (.charAt sb 3) \-))
            (do (.setCharAt sb 3 \p) (let [msd (KRTools/chopMSD (.toString sb))]
                (if (== (.length msd) 4)
                    (.substring msd 0 3)
                    msd)))

            (KRTools/chopMSD (.toString sb)))))

; Convert the POS character and feature to MSD code eg. the POS character can be 'N' and the feature
; that belongs to the POS character can be "SubPOS=c|Num=s|Cas=n|NumP=none|PerP=none|NumPd=none".

; Relevant punctations has no features: featurestring contains only a _ character.
; The MSD code of relevant punctations is the punctation itself.

; X, Y, Z and K have no features ; I may have no features.

(defn- _conllFeaturesToMsd [pos features]
    (cond
        (empty? features) nil

        (= features "_") (str pos)

        :default (let [fmap (featuresMap features)]
            (case pos
                \N (to-msd pos         nounMap fmap)
                \V (to-msd pos         verbMap fmap)
                \A (to-msd pos          adjMap fmap)
                \P (to-msd pos      pronounMap fmap)
                \T (to-msd pos      articleMap fmap)
                \R (to-msd pos       adverbMap fmap)
                \S (to-msd pos   adpositionMap fmap)
                \C (to-msd pos  conjunctionMap fmap)
                \M (to-msd pos      numeralMap fmap)
                \I (to-msd pos interjectionMap fmap)
                \O (to-msd pos        otherMap fmap)

                (\X \Y \Z \K) (str pos)

                nil))))

(defn conllFeaturesToMsd [pos features]
    (if (< 1 (.length pos)) "_" (_conllFeaturesToMsd (.charAt pos 0) features)))

(defn -main [] (println (conllFeaturesToMsd "O" "SubPOS=e|Type=w|Num=s|Cas=n|NumP=none|PerP=none|NumPd=none")))
