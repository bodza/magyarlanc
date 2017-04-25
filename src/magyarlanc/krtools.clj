(ns magyarlanc.krtools
    (:require [clojure.string :as str]
              [magyarlanc.new :as new])
  #_(:gen-class))

; melléknévi igenevek
(defn- participle? [kr] (let [verb (.indexOf kr "/VERB") adj (.indexOf kr "/ADJ")] (< -1 verb adj)))

(defn- getPostPLemma [kr]
    (if-let [[_ lemma] (re-find #"^\$(én|te|ő|mi|ti|ők)/NOUN<POSTP<" kr)]
        (.toLowerCase (let [n (.length kr)] (case lemma
            ("én" "te") (.substring kr 15 (- n 11))	; ouch!
             "ő"        (.substring kr 14 (- n 8))	; ouch!
            ("mi" "ti") (.substring kr 15 (- n 17))	; ouch!
             "ők"       (.substring kr 15 (- n 14)))))	; ouch!
        (if-let [[_ lemma affix] (re-find #"^\$(ez|az)/NOUN<POSTP<([^>]*)>" kr)]
            (let [affix (.toLowerCase affix)]
                ; alá, alatt, alól, által, elő, előbb, ellen, elől, előtt, iránt, után (pl.: ezután)
                (if (.contains kr "(i)")
                    (if (re-find #"^[aáeiu]" affix)
                        (str lemma affix "i")
                        (str (first lemma) affix "i"))
                    (str lemma affix)))
            (.substring kr 1 (.indexOf kr "/")))))

(defn ^String chopMSD [^CharSequence msd]
    (loop [n (.length msd)] (if (zero? n) "" (if (= (.charAt msd (dec n)) \-) (recur (dec n)) (.. msd (subSequence 0 n) toString)))))

(defn- msd-
    ([kr s msd i ch] (if (.contains kr s) (.setCharAt msd i ch)))
    ([kr s msd i1 c1 i2 c2] (when (.contains kr s) (.setCharAt msd i1 c1) (.setCharAt msd i2 c2))))

(defn- convertNoun [lemma kr]
    (cond
        (.contains kr "PERS") ; névmás minden PERS-t tartalmazó NOUN
            (let [msd (StringBuilder. "Pp--sn-----------")]
                ; személy
                (msd- kr "<PERS<1>>"  msd 2 \1)
                (msd- kr "<PERS<2>>"  msd 2 \2)
                (msd- kr "<PERS>"     msd 2 \3)
                ; szám
                (msd- kr "<PLUR>"     msd 4 \p)
                ; eset                    ; \n jelöletlen alapeset
                (msd- kr "<CAS<ACC>>" msd 5 \a)
                (msd- kr "<CAS<GEN>>" msd 5 \g)
                (msd- kr "<CAS<DAT>>" msd 5 \d)
                (msd- kr "<CAS<INS>>" msd 5 \i)
                (msd- kr "<CAS<ILL>>" msd 5 \x)
                (msd- kr "<CAS<INE>>" msd 5 \2)
                (msd- kr "<CAS<ELA>>" msd 5 \e)
                (msd- kr "<CAS<ALL>>" msd 5 \t)
                (msd- kr "<CAS<ADE>>" msd 5 \3)
                (msd- kr "<CAS<ABL>>" msd 5 \b)
                (msd- kr "<CAS<SBL>>" msd 5 \s)
                (msd- kr "<CAS<SUE>>" msd 5 \p)
                (msd- kr "<CAS<DEL>>" msd 5 \h)
                (msd- kr "<CAS<TER>>" msd 5 \9)
                (msd- kr "[MANNER]"   msd 5 \w)
                (msd- kr "<CAS<FOR>>" msd 5 \f)
                (msd- kr "<CAS<TEM>>" msd 5 \m)
                (msd- kr "<CAS<CAU>>" msd 5 \c)
                (msd- kr "[COM]"      msd 5 \q)
                (msd- kr "<CAS<TRA>>" msd 5 \y)
                (msd- kr "[PERIOD1]"  msd 5 \u)
                (chopMSD (.toString msd)))

        (.contains kr "POSTP") ; névmás minden POSTP-t tartalmazó NOUN
            (let [msd (StringBuilder. "Pp3-sn")]
                (case lemma
                    "én" (.setCharAt msd 2 \1)
                    "te" (.setCharAt msd 2 \2)
                    "ő"  (.setCharAt msd 2 \3)
                    "mi" (do (.setCharAt msd 2 \1) (.setCharAt msd 4 \p))
                    "ti" (do (.setCharAt msd 2 \2) (.setCharAt msd 4 \p))
                    "ők" (do (.setCharAt msd 2 \3) (.setCharAt msd 4 \p))
                    nil)
                (chopMSD (.toString msd)))

        :default
            (let [msd (StringBuilder. "Nn-sn------")]
                ; egyes/többes szám NOUN<PLUR> NUON<PLUR<FAM>>
                (msd- kr "NOUN<PLUR"       msd 3 \p)
                ; eset                         ; \n jelöletlen alapeset
                (msd- kr "<CAS<ACC>>"      msd 4 \a)
                (msd- kr "<CAS<GEN>>"      msd 4 \g)
                (msd- kr "<CAS<DAT>>"      msd 4 \d)
                (msd- kr "<CAS<INS>>"      msd 4 \i)
                (msd- kr "<CAS<ILL>>"      msd 4 \x)
                (msd- kr "<CAS<INE>>"      msd 4 \2)
                (msd- kr "<CAS<ELA>>"      msd 4 \e)
                (msd- kr "<CAS<ALL>>"      msd 4 \t)
                (msd- kr "<CAS<ADE>>"      msd 4 \3)
                (msd- kr "<CAS<ABL>>"      msd 4 \b)
                (msd- kr "<CAS<SBL>>"      msd 4 \s)
                (msd- kr "<CAS<SUE>>"      msd 4 \p)
                (msd- kr "<CAS<DEL>>"      msd 4 \h)
                (msd- kr "<CAS<TER>>"      msd 4 \9)
                (msd- kr "<CAS<ESS>>"      msd 4 \w)
                (msd- kr "<CAS<FOR>>"      msd 4 \f)
                (msd- kr "<CAS<TEM>>"      msd 4 \m)
                (msd- kr "<CAS<CAU>>"      msd 4 \c)
                (msd- kr "[COM]"           msd 4 \q)
                (msd- kr "<CAS<TRA>>"      msd 4 \y)
                (msd- kr "[PERIOD1]"       msd 4 \u)
                ; birtokos száma/személye
                (msd- kr "<POSS>"          msd 8 \s 9 \3)
                (msd- kr "<POSS<1>>"       msd 8 \s 9 \1)
                (msd- kr "<POSS<2>>"       msd 8 \s 9 \2)
                (msd- kr "<POSS<1><PLUR>>" msd 8 \p 9 \1)
                (msd- kr "<POSS<2><PLUR>>" msd 8 \p 9 \2)
                (msd- kr "<POSS<PLUR>>"    msd 8 \p 9 \3)
                ; birtok(olt) száma
                (msd- kr "<ANP>"           msd 10 \s)
                (msd- kr "<ANP<PLUR>>"     msd 10 \p)
                (chopMSD (.toString msd)))))

(defn- convertAdjective [kr]
    (let [msd (StringBuilder. "Afp-sn-------")]
        ; típus                        ; \f (melléknév) jelöletlen alapeset
        (msd- kr "[IMPERF_PART"    msd 1 \p) ; p (folyamatos melléknévi igenév)
        (msd- kr "[PERF_PART"      msd 1 \s) ; s (befejezett melléknévi igenév)
        (msd- kr "[FUT_PART"       msd 1 \u) ; u (beálló melléknévi igenév)
        ; fok                          ; \p jelöletlen alapeset
        (msd- kr "[COMPAR"         msd 2 \c)
        (msd- kr "[SUPERLAT"       msd 2 \s)
        (msd- kr "[SUPERSUPERLAT"  msd 2 \e)
        ; szám                         ; \s jelöletlen alapeset
        (msd- kr "ADJ<PLUR>"       msd 4 \p)
        ; eset                         ; \n jelöletlen alapeset
        (msd- kr "<CAS<ACC>>"      msd 5 \a)
        (msd- kr "<CAS<GEN>>"      msd 5 \g)
        (msd- kr "<CAS<DAT>>"      msd 5 \d)
        (msd- kr "<CAS<INS>>"      msd 5 \i)
        (msd- kr "<CAS<ILL>>"      msd 5 \x)
        (msd- kr "<CAS<INE>>"      msd 5 \2)
        (msd- kr "<CAS<ELA>>"      msd 5 \e)
        (msd- kr "<CAS<ALL>>"      msd 5 \t)
        (msd- kr "<CAS<ADE>>"      msd 5 \3)
        (msd- kr "<CAS<ABL>>"      msd 5 \b)
        (msd- kr "<CAS<SBL>>"      msd 5 \s)
        (msd- kr "<CAS<SUE>>"      msd 5 \p)
        (msd- kr "<CAS<DEL>>"      msd 5 \h)
        (msd- kr "<CAS<TER>>"      msd 5 \9)
        (msd- kr "[MANNER]"        msd 5 \w)
        (msd- kr "<CAS<FOR>>"      msd 5 \f)
        (msd- kr "<CAS<TEM>>"      msd 5 \m)
        (msd- kr "<CAS<CAU>>"      msd 5 \c)
        (msd- kr "[COM]"           msd 5 \q)
        (msd- kr "<CAS<TRA>>"      msd 5 \y)
        (msd- kr "[PERIOD1]"       msd 5 \u)
        ; birtokos száma/személye
        (msd- kr "<POSS>"          msd 10 \s 11 \3)
        (msd- kr "<POSS<1>>"       msd 10 \s 11 \1)
        (msd- kr "<POSS<2>>"       msd 10 \s 11 \2)
        (msd- kr "<POSS<1><PLUR>>" msd 10 \p 11 \1)
        (msd- kr "<POSS<2><PLUR>>" msd 10 \p 11 \2)
        (msd- kr "<POSS<PLUR>>"    msd 10 \p 11 \3)
        ; birtok(olt) száma
        (msd- kr "<ANP>"           msd 12 \s)
        (msd- kr "<ANP<PLUR>>"     msd 12 \p)
        (chopMSD (.toString msd))))

(defn- convertVerb [kr]
    (let [msd (StringBuilder. "Vmip3s---n-")]
        (let [modal (.contains kr "<MODAL>") freq (.contains kr "[FREQ]") caus (.contains kr "[CAUS]")]
            (cond
                (and      modal  (not freq) (not caus)) (.setCharAt msd 1 \o)   ; ható
                (and (not modal)      freq  (not caus)) (.setCharAt msd 1 \f)   ; gyakorító
                (and (not modal) (not freq)      caus ) (.setCharAt msd 1 \s)   ; műveltető
                (and      modal       freq  (not caus)) (.setCharAt msd 1 \1)   ; gyakorító + ható
                (and      modal  (not freq)      caus ) (.setCharAt msd 1 \2)   ; műveltető + ható
                (and (not modal)      freq       caus ) (.setCharAt msd 1 \3)   ; műveltető + ható
                (and      modal       freq       caus ) (.setCharAt msd 1 \4))) ; műveltető + gyakorító + ható

        (msd- kr "<COND>" msd 2 \c)

        (when (.contains kr "<INF>")
            (.setCharAt msd 2 \n)
            (.setCharAt msd 9 \-)
            (when (not (.contains kr "<PERS"))
                (.setCharAt msd 3 \-)
                (.setCharAt msd 4 \-)
                (.setCharAt msd 5 \-)))

        (msd- kr "<SUBJUNC-IMP>" msd 2 \m)
        (msd- kr "<PAST>"        msd 3 \s)
        (msd- kr "<PERS<1>>"     msd 4 \1)
        (msd- kr "<PERS<2>>"     msd 4 \2)
        (msd- kr "<PLUR>"        msd 5 \p)
        (msd- kr "<DEF>"         msd 9 \y)

        (when (.contains kr "<PERS<1<OBJ<2>>>>")
            (.setCharAt msd 4 \1)
            (.setCharAt msd 9 \2))

        (chopMSD (.toString msd))))

(defn- convertNumber [kr ans]
    (let [msd (StringBuilder. "Mc-snl-------")]
                                        ; \c jelöletlen alapeset
        (msd- kr "[ORD"             msd 1 \o)
        (msd- kr "[FRACT"           msd 1 \f)
                                        ; \l nincs a magyarban
                                        ; \d nincs a KRben
                                        ; \s jelöletlen alapeset
        (msd- kr "NUM<PLUR>"        msd 3 \p)
        ; eset                          ; \n jelöletlen alapeset
        (msd- kr "<CAS<ACC>>"       msd 4 \a)
        (msd- kr "<CAS<GEN>>"       msd 4 \g)
        (msd- kr "<CAS<DAT>>"       msd 4 \d)
        (msd- kr "<CAS<INS>>"       msd 4 \i)
        (msd- kr "<CAS<ILL>>"       msd 4 \x)
        (msd- kr "<CAS<INE>>"       msd 4 \2)
        (msd- kr "<CAS<ELA>>"       msd 4 \e)
        (msd- kr "<CAS<ALL>>"       msd 4 \t)
        (msd- kr "<CAS<ADE>>"       msd 4 \3)
        (msd- kr "<CAS<ABL>>"       msd 4 \b)
        (msd- kr "<CAS<SBL>>"       msd 4 \s)
        (msd- kr "<CAS<SUE>>"       msd 4 \p)
        (msd- kr "<CAS<DEL>>"       msd 4 \h)
        (msd- kr "<CAS<TER>>"       msd 4 \9)
        (msd- kr "[MANNER]"         msd 4 \w)
        (msd- kr "<CAS<FOR>>"       msd 4 \f)
        (msd- kr "<CAS<TEM>>"       msd 4 \m)
        (msd- kr "<CAS<CAU>>"       msd 4 \c)
        (msd- kr "[COM]"            msd 4 \q)
        (msd- kr "<CAS<TRA>>"       msd 4 \y)
        (msd- kr "[PERIOD1]"        msd 4 \u)
        (msd- kr "[MULTIPL-ITER]"   msd 4 \6)
        ; birtokos száma/személye
        (msd- ans "<POSS>"          msd 10 \s 11 \3)
        (msd- ans "<POSS<1>>"       msd 10 \s 11 \1)
        (msd- ans "<POSS<2>>"       msd 10 \s 11 \2)
        (msd- ans "<POSS<1><PLUR>>" msd 10 \p 11 \1)
        (msd- ans "<POSS<2><PLUR>>" msd 10 \p 11 \2)
        (msd- ans "<POSS<PLUR>>"    msd 10 \p 11 \3)
        ; birtok(olt) száma
        (msd- ans "<ANP>"           msd 12 \s)
        (msd- ans "<ANP<PLUR>>"     msd 12 \p)
        (chopMSD (.toString msd))))

(defn- convertAdverb [kr]
    (let [msd (StringBuilder. "Rx----")]
        (msd- kr "[COMPAR]"        msd 2 \c)
        (msd- kr "[SUPERLAT]"      msd 2 \s)
        (msd- kr "[SUPERSUPERLAT]" msd 2 \e)
        (chopMSD (.toString msd))))

(declare getRoot)

(defn getMSD [kr]
    (let [ans (transient #{}) ; (sorted-set)
          root (getRoot kr) i (.indexOf root "/") lemma (.substring root 1 i) code (.substring root (inc i))
          ; $forog(-.)/VERB[CAUS](at)/VERB[FREQ](gat)/VERB<PAST><PERS<1>>
          stem (let [a (.indexOf kr "(") b (.indexOf kr "/")]
            (cond
                (< -1 a b)         (.substring kr 1 a)
                (.contains kr "+") lemma
                :default           (.substring kr 1 b)))]

        (cond
            (.startsWith code "NOUN")
                (let [msd (convertNoun lemma code) pro (.startsWith msd "P")
                    lemma (if pro (getPostPLemma kr) lemma)]	; ouch!
                    ; pronoun
                    (if pro
                        ; dative
                        (if (= (.charAt msd 5) \d)
                            (conj! ans (new/morAna lemma (.replace msd \d \g)))))

                    (conj! ans (new/morAna lemma msd))
                    ; dative
                    (if (= (.charAt msd 4) \d)
                        (conj! ans (new/morAna lemma (.replace msd \d \g)))))

            (.startsWith code "ADJ")
                (let [msd (convertAdjective (if (participle? kr) kr code))]
                    (conj! ans (new/morAna lemma msd))
                    ; dative
                    (if (= (.charAt msd 5) \d)
                        (conj! ans (new/morAna lemma (.replace msd \d \g)))))

            (.startsWith code "VERB")
                (cond
                    (or (.contains code "VERB[PERF_PART]") (.contains code "VERB[PART]")) ; határozói igenév
                        (conj! ans (new/morAna lemma "Rv"))
                    (or (.contains kr "[FREQ]") (.contains kr "[CAUS]") (.contains kr "<MODAL>"))
                        (conj! ans (new/morAna stem (convertVerb kr)))
                    :default
                        (conj! ans (new/morAna lemma (convertVerb code))))

            (.startsWith code "NUM")
                (let [msd (convertNumber code kr)]
                    (conj! ans (new/morAna lemma msd))
                    ; dative
                    (if (= (.charAt msd 4) \d)
                        (conj! ans (new/morAna lemma (.replace msd \d \g)))))

            (.startsWith code "ART")     (conj! ans (new/morAna lemma "T")) ; definite/indefinte
            (.startsWith code "ADV")     (conj! ans (new/morAna lemma (convertAdverb code)))
            (.startsWith code "POSTP")   (conj! ans (new/morAna lemma "St"))
            (.startsWith code "CONJ")    (conj! ans (new/morAna lemma "Ccsp"))
            (.startsWith code "UTT-INT") (conj! ans (new/morAna lemma "I"))
            (.startsWith code "PREV")    (conj! ans (new/morAna lemma "Rp"))
            (.startsWith code "DET")     (conj! ans (new/morAna lemma "Pd3-sn"))
            (.startsWith code "ONO")     (conj! ans (new/morAna lemma "X"))
            (.startsWith code "E")       (conj! ans (new/morAna lemma "Rq-y"))
            (.startsWith code "ABBR")    (conj! ans (new/morAna lemma "Y"))
            (.startsWith code "TYPO")    (conj! ans (new/morAna lemma "Z")))

        (if (zero? (count ans))
            (conj! ans (new/morAna lemma "X")))

        (persistent! ans)))

(defn- preProcess [stems]
    (let [stems (into-array stems) n (dec (count stems))]
        (doseq [stem stems]
            (if (or ; gyorsan -> gyors ; hallgatólag -> hallgató
                    (.contains stem "ADJ[MANNER]")
                    ; mindenképp, mindenképpen -> minden
                    (.contains stem "NOUN[ESS_FOR]")
                    ; apástul -> apa
                    (and (.contains stem "NOUN") (.contains stem "[COM]"))
                    ; fejenként -> fej
                    (.contains stem "NOUN[PERIOD1]")
                    ; számnevek, melyek KRben /ADV
                    (and (.contains stem "NUM") (.contains stem "["))
                    ; rosszabb, legrosszabb, legeslegrosszabb, rosszabbik, legrosszabbik, legeslegrosszabbik -> rossz
                    (and (.contains stem "ADJ") (or (.contains stem "[COMPAR")
                                                    (.contains stem "[SUPERLAT")
                                                    (.contains stem "[SUPERSUPERLAT")))
                    ; futva, futván -> fut
                    (or (.contains stem "VERB[PART](va)")
                        (.contains stem "VERB[PART](ve)")
                        (.contains stem "VERB[PERF_PART](ván)")
                        (.contains stem "VERB[PERF_PART](vén)")))
                (aset stems n stem)))
        stems))

(defn getRoot [morph]
    (cond
        (.startsWith morph "$sok/NUM[COMPAR]/NUM<CAS<")         "$több/NUM<CAS<ACC>>"
        (.startsWith morph "$sok/NUM[SUPERLAT]/NUM<CAS<")       "$legtöbb/NUM<CAS<ACC>>"
        (.startsWith morph "$sok/NUM[SUPER-SUPERLAT]/NUM<CAS<") "$legeslegtöbb/NUM<CAS<ACC>>"

        (not (.contains morph "/"))
            morph

        :default
            (let [[igekötő morph] (if-let [m (re-matches #"(.*?)/PREV\+(.*)" morph)] [(m 1) (m 2)] ["" morph])
                  stems (preProcess (str/split morph #"/")) n (dec (count stems))
                  végsőtő (atom (re-find #"^[^\(/]*" (first stems))) ikes (atom false)]

                (when (< 1 n) (doseq [stem (take n stems) todo (map #(% 1) (re-seq #"\((.*?)\)" stem))]
                    (condp re-matches todo
                        ; -1 -2ik
                        #"-(\d+).*"
                        :>> (fn [m] (swap! végsőtő #(.substring % 0 (- (.length %) (Integer/parseInt (m 1)))))
                                    (reset! ikes (.endsWith todo "ik")))
                        #"-\."
                        (swap! végsőtő #(let [[_ a b] (re-matches #"(.*?).([bcdfghjklmnpqrstvwxyz!]*)" %)] (str a "!" b)))
                        ; .a .e .i .o .ö .u .ü
                        #"\.(.*)"
                        :>> (fn [m] (swap! végsőtő #(.replace % "!" (m 1))) (reset! ikes false))
                        ; %leg %legesleg
                        #"%.*"
                        (comment "TODO nem találtam ilyet, de.")
                        ; a, abb, ad, al, an, anként, anta, askodik, astul, astól, at,
                        ; az, azik, bb, beli, béli, e, ebb, ed, eget, el, en, enként,
                        ; ente, eskedik, estől, estül, et, ett, ez, ezik, gat, get,
                        ; hetnék, i, kedik, képp, képpen, lag, leg, n, nként, nta, nte,
                        ; nyi, od, odik, ogat, ol, on, onként, onta, oskodik, ostul,
                        ; ostól, ott, ov, oz, ozik, sodik, stól, stől, stül, sul, szer,
                        ; szerez, szeri, szerte, szor, szori, szoroz, szorta, ször,
                        ; szöri, szörte, szöröz, sít, södik, tat, tet, tt, ul, v, va,
                        ; ve, ván, vén, z, zik, á, é, ít, ó, ödik, ődik, öget, öl,
                        ; önként, ösködik, östől, östül, ött, öv, öz, özik, ül, ódik
                        #"[^\-\.%].*"
                        :>> (fn [m] (swap! végsőtő #(str % m)) (reset! ikes false))
                        nil)))

                (let [root (str igekötő @végsőtő (if @ikes "ik" "") "/" (aget stems n))]
                    (str "$" (str/replace root #"\([^\(\)]*\)|[!@\$]" ""))))))

(def KRPOS #{:VERB :NOUN :ADJ :NUM :ADV :PREV :ART :POSTP :UTT-INT :DET :CONJ :ONO :PREP :X})

; "$fut/VERB[GERUND](�s)/NOUN<PLUR><POSS<1>><CAS<INS>>"
(defn getPOS [code]
    (case (if-let [m (re-find #"/([^/<\[]+)(?:[<\[][^/]*)?$" code)] (m 1))
        "VERB"    :VERB
        "NOUN"    :NOUN
        "ADJ"     :ADJ
        "NUM"     :NUM
        "ADV"     :ADV
        "PREV"    :PREV
        "ART"     :ART
        "POSTP"   :POSTP
        "UTT-INT" :UTT-INT
        "DET"     :DET
        "CONJ"    :CONJ
        "ONO"     :ONO
        "PREP"    :PREP
        :X))

(comment
    (getMSD "$én/NOUN<POSTP<UTÁN>><PERS<1>>")
    (getRoot "$fut/VERB[GERUND](�s)/NOUN<PLUR><POSS<1>><CAS<INS>>")
    (getPOS "$árapály/NOUN"))
