(ns magyarlanc.krtools
    (:require [clojure.string :as str])
    (:import [magyarlanc Morphology$MorAna])
  #_(:gen-class))

(defn- morAna [lemma msd] (Morphology$MorAna. lemma msd))

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

(defn getMSD [kr]
    (let [ans (transient #{})
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
                            (conj! ans (morAna lemma (.replace msd \d \g)))))

                    (conj! ans (morAna lemma msd))
                    ; dative
                    (if (= (.charAt msd 4) \d)
                        (conj! ans (morAna lemma (.replace msd \d \g)))))

            (.startsWith code "ADJ")
                (let [msd (convertAdjective (if (participle? kr) kr code))]
                    (conj! ans (morAna lemma msd))
                    ; dative
                    (if (= (.charAt msd 5) \d)
                        (conj! ans (morAna lemma (.replace msd \d \g)))))

            (.startsWith code "VERB")
                (cond
                    (or (.contains code "VERB[PERF_PART]") (.contains code "VERB[PART]")) ; határozói igenév
                        (conj! ans (morAna lemma "Rv"))
                    (or (.contains kr "[FREQ]") (.contains kr "[CAUS]") (.contains kr "<MODAL>"))
                        (conj! ans (morAna stem (convertVerb kr)))
                    :default
                        (conj! ans (morAna lemma (convertVerb code))))

            (.startsWith code "NUM")
                (let [msd (convertNumber code kr)]
                    (conj! ans (morAna lemma msd))
                    ; dative
                    (if (= (.charAt msd 4) \d)
                        (conj! ans (morAna lemma (.replace msd \d \g)))))

            (.startsWith code "ART")     (conj! ans (morAna lemma "T")) ; definite/indefinte
            (.startsWith code "ADV")     (conj! ans (morAna lemma (convertAdverb code)))
            (.startsWith code "POSTP")   (conj! ans (morAna lemma "St"))
            (.startsWith code "CONJ")    (conj! ans (morAna lemma "Ccsp"))
            (.startsWith code "UTT-INT") (conj! ans (morAna lemma "I"))
            (.startsWith code "PREV")    (conj! ans (morAna lemma "Rp"))
            (.startsWith code "DET")     (conj! ans (morAna lemma "Pd3-sn"))
            (.startsWith code "ONO")     (conj! ans (morAna lemma "X"))
            (.startsWith code "E")       (conj! ans (morAna lemma "Rq-y"))
            (.startsWith code "ABBR")    (conj! ans (morAna lemma "Y"))
            (.startsWith code "TYPO")    (conj! ans (morAna lemma "Z")))

        (if (zero? (count ans))
            (conj! ans (morAna lemma "X")))

        (persistent! ans)))

(defn ^String chopMSD [^CharSequence msd]
    (loop [n (.length msd)] (if (zero? n) "" (if (= (.charAt msd (dec n)) \-) (recur (dec n)) (.. (subSequence 0 n) toString)))))

;       public static enum KRPOS
;       {
;           VERB, NOUN, ADJ, NUM, ADV, PREV, ART, POSTP, UTT_INT, DET, CONJ, ONO, PREP, X
;       }

(defn- preProcess [stems]
    (let [stems (transient (vec stems)) n (count stems)]
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
                (assoc! stems (dec n) stem)))
        persistent! stems)
    )

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
                  v! (transient {:végsőtő (re-find #"^[^\(/]*" (first stems)) :ikes false})]

                (when (< 1 n) (doseq [stem (take n stems) todo (map #(% 1) (re-seq #"\((.*?)\)" stem))]
                    (condp re-matches todo
                        ; -1 -2ik
                        #"-(\d+).*"
                        :>> #(let [i (Integer/parseInt (% 1))]
                                (assoc! v! :végsőtő (let [v (v! :végsőtő)] (.substring v 0 (- (.length v) i))))
                                (assoc! v! :ikes (.endsWith todo "ik")))
                        #"-\."
                        (let [v (v! :végsőtő) [_ _1 _2] (re-matches #"(.*?).([bcdfghjklmnpqrstvwxyz!]*)" v)]
                            (assoc! v! :végsőtő (str _1 "!" _2)))
                        ; .a .e .i .o .ö .u .ü
                        #"\.(.*)"
                        :>> #(let [v (v! :végsőtő)]
                                (if (.contains v "!")
                                    (assoc! v! :végsőtő (.replace v "!" (% 1)))
                                    (comment "TODO ez mikor van?"))
                                (assoc! v! :ikes false))
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
                        :>> #(let [v (v! :végsőtő)]
                                (assoc! v! :végsőtő (str v %))
                                (assoc! v! :ikes false))
                        nil)))

                (let [root (str igekötő (v! :végsőtő) (if (v! :ikes) "ik" "") "/" (stems n))]
                    (str "$" (str/replace root #"\([^\(\)]*\)|[!@\$]" ""))))))

; "$fut/VERB[GERUND](�s)/NOUN<PLUR><POSS<1>><CAS<INS>>"
(defn getPOS [code]
;       {
;           int end1 = Integer.MAX_VALUE
;           int end2 = Integer.MAX_VALUE

;           int end = 0

;           if (.contains code "@")
;           {
;               end = (.lastIndexOf code "@")
;           }

;           int start = (.lastIndexOf code "/")

;           if (0 < (.indexOf code "<" start))
;           {
;               end1 = (.indexOf code "<" start)
;           }

;           if (0 < (.indexOf code "[" start))
;           {
;               end2 = (.indexOf code "[" start)
;           }

;           end = (end1 < end2) ? end1 : end2

;           if ((.length code) < end)
;           {
;               end = (.length code)
;           }

;           switch (.substring code start end)
;           {
;               case "VERB":    return KRPOS.VERB
;               case "NOUN":    return KRPOS.NOUN
;           }

;           switch (.substring code (inc start) end)
;           {
;               case "ADJ":     return KRPOS.ADJ
;               case "NUM":     return KRPOS.NUM
;               case "ADV":     return KRPOS.ADV
;               case "PREV":    return KRPOS.PREV
;               case "ART":     return KRPOS.ART
;               case "POSTP":   return KRPOS.POSTP
;               case "UTT-INT": return KRPOS.UTT_INT
;               case "DET":     return KRPOS.DET
;               case "CONJ":    return KRPOS.CONJ
;               case "ONO":     return KRPOS.ONO
;               case "PREP":    return KRPOS.PREP
;           }

;           return KRPOS.X
;       }
    )

(defn- getAbsoluteLemma [form]
;       {
;           List<String> lemma = new ArrayList<String>()

;           for (String s : RFSA.analyse(form))
;           {
                ; igekötők leválasztása
;               s = (.substring s (inc (.indexOf s "$")))

;               if (and (.contains s "(") (< (.indexOf s "(") (.indexOf s "/")))
;                   lemma.add(.substring s 0 (.indexOf s "("))
;               else
;                   lemma.add(.substring s 0 (.indexOf s "/"))
;           }

;           return lemma.toArray(new String[lemma.size()])
;       }
    )

(comment
    (getMSD "$én/NOUN<POSTP<UTÁN>><PERS<1>>")
    (getRoot "$fut/VERB[GERUND](�s)/NOUN<PLUR><POSS<1>><CAS<INS>>")
    (getPOS "$árapály/NOUN"))
