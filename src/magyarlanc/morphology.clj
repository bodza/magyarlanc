(ns magyarlanc.morphology
    (:require [clojure.java.io :as io] #_[clojure.set :as set] [clojure.string :as str]
              [magyarlanc.rfsa :as rfsa])
    (:import [java.util HashSet LinkedHashSet List Map Set TreeSet]
             [java.util.regex Matcher Pattern])
    (:import [edu.stanford.nlp.ling TaggedWord]
             [edu.stanford.nlp.tagger.maxent SzteMaxentTagger])
    (:import [magyarlanc HunSplitter KRTools KRTools$KRPOS Morphology$MorAna])
    (:gen-class))

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
    (morphParseTokens (HunSplitter/tokenize sentence)))

(defn morphParse [text]
    (map morphParseTokens (HunSplitter/split text)))

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
;       }

; adott szó csak írásjeleket tartalmaz-e
(defn- punctation? [spelling]
    (not-any? #(Character/isLetterOrDigit %) spelling))

(def punctations* (delay (into #{} (map str "!,-.:;?–"))))

(defn- puncs [word]
    (if (punctation? word) #{(morAna word (cond (@punctations* word) word (= word "§") "Nn-sn" :default "K"))}))

(defn- readErrata [file]
    (with-open [reader (io/reader (io/resource file))]
        (into {} (map #(let [[k v] (str/split % #"\t")] (if-not (= k v) [k v])) (line-seq reader)))))

(def ^:private errata* (delay (readErrata "data/errata.txt")))

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
                (reduce conj! ans (mapcat #(KRTools/getMSD %) (rfsa/analyse word)))

                (if (empty? ans)
                    ; (kötőjeles) összetett szó
                    (let [i (.indexOf word "-")
                          cs (if (< 1 i) (analyseHyphenicCompoundWord word) (analyseCompoundWord (.toLowerCase word)))]
                        (reduce conj! ans (mapcat #(KRTools/getMSD %) cs))))

                (if (empty? ans)
                    ; guess (Bush-nak, Bush-kormányhoz)
                    (let [i (.lastIndexOf word "-")]
                        (if (< 1 i)
                            (reduce conj! ans (hyphenicGuess (.substring word 0 i) (.substring word (inc i)))))))

                (if (empty? ans)
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
;               String reduced = MSDTools.reduceMSD(morAna.getMsd());
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
;                       if (MSDTools.reduceMSD(morAna.getMsd()).equals(tw.tag()) && (max < freq))
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

(map #(intern *ns* % (KRTools$KRPOS/valueOf (name %))) '(UTT_INT ART NUM PREV NOUN VERB ADJ ADV))	; ^:private

(defn- isCompatibleAnalyises [kr1 kr2]
    (let [pos1 (KRTools/getPOS kr1) pos2 (KRTools/getPOS kr2)]
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
                (let [kr1 (KRTools/getRoot a1) kr2 (KRTools/getRoot a2)]
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
                                            (let [kr1 (KRTools/getRoot a1) kr2 (KRTools/getRoot a2)]
                                                (if (isCompatibleAnalyises kr1 kr2)
                                                    (conj! ans (.replace kr2 "$" (str "$" part1)))))))))
                            (recur (inc i)))))))))

;       public static Set<String> analyseHyphenicCompoundWord(String word)
;       {
;           Set<String> analises = new LinkedHashSet<String>();

;           if (!word.contains("-"))
;           {
;               return analises;
;           }

;           int hp = word.indexOf('-');
;           String part1 = word.substring(0, hp), part2 = word.substring(hp + 1);

;           // a kötőjel előtti és a kötőjel utáni résznek is van elemzése (pl.: adat-kezelőt)
;           if (isBisectable(part1 + part2))
;           {
;               analises = getCompatibleAnalises(part1, part2, true);
;           }

;           // a kötőjel előtti résznek is van elemzése, a kötőjel utáni rész két részre bontható
;           else if (RFSA.analyse(part1).size() > 0 && isBisectable(part2))
;           {
;               List<String> ans1 = RFSA.analyse(part1);

;               int bi = bisectIndex(part2);
;               String part21 = part2.substring(0, bi), part22 = part2.substring(bi);

;               Set<String> ans2 = getCompatibleAnalises(part21, part22);

;               for (String a1 : ans1)
;               {
;                   for (String a2 : ans2)
;                   {
;                       if (isCompatibleAnalyises(KRTools.getRoot(a1), KRTools.getRoot(a2)))
;                       {
;                           if (analises == null)
;                           {
;                               analises = new LinkedHashSet<String>();
;                           }
;                           analises.add(KRTools.getRoot(a2).replace("$", "$" + part1 + "-"));
;                       }
;                   }
;               }
;           }

;           else if (isBisectable(part1) && RFSA.analyse(part2).size() > 0)
;           {
;               List<String> ans2 = RFSA.analyse(part2);

;               int bi = bisectIndex(part1);
;               String part11 = part1.substring(0, bi), part12 = part1.substring(bi);

;               Set<String> ans1 = getCompatibleAnalises(part11, part12);

;               for (String a1 : ans1)
;               {
;                   for (String a2 : ans2)
;                   {
;                       if (isCompatibleAnalyises(KRTools.getRoot(a1), KRTools.getRoot(a2)))
;                       {
;                           if (analises == null)
;                           {
;                               analises = new LinkedHashSet<String>();
;                           }
;                           analises.add(KRTools.getRoot(a2).replace("$", "$" + part1 + "-"));
;                       }
;                   }
;               }
;           }

;           return analises;
;       }

;       private static Set<String> morPhonDir = new HashSet<String>()
;       {{
;           String[] morPhons = { "talány", "némber", "sün", "fal", "holló", "felhő", "kalap", "hely", "köd" };

;           for (String morPhon : morPhons)
;           {
;               add(morPhon);
;           }
;       }};

;       private static Set<String> getMorPhonDir()
;       {
;           return morPhonDir;
;       }

;       /**
;        * A morPhonGuess függvény egy ismeretlen (nem elemezhető) főnévi szótő és
;        * tetszőleges suffix guesselésére szolgál. A guesselés során az adott suffixet
;        * a rendszer morPhonDir szótárának elemeire illesztve probáljuk elemezni. A
;        * szótár reprezentálja a magyar nyelv minden (nem hasonuló) illeszkedési
;        * szabályát, így biztosak lehetünk benne, hogy egy valós toldalék mindenképp
;        * illeszkedni fog legalább egy szótárelemre. Például egy 'hoz'rag esetén,
;        * először a köd elemre próbálunk illeszteni, majd elemezni. A kapott szóalak
;        * így a ködhoz lesz, melyre a KR elemzőnk nem ad elemzést. A következő
;        * szótárelem a talány, a szóalak a talányhoz lesz, melyre megkapjuk az Nc-st
;        * (külső közelítő/allative) főnévi elemzést.
;        */
;       public static Set<MorAna> morPhonGuess(String root, String suffix)
;       {
;           Set<MorAna> stems = new TreeSet<MorAna>();

;           for (String guess : getMorPhonDir())
;           {
;               for (String kr : RFSA.analyse(guess + suffix))
;               {
;                   for (MorAna stem : KRTools.getMSD(kr))
;                   {
;                       if (stem.getMsd().startsWith("N"))
;                       {
;                           stems.add(new MorAna(root, stem.getMsd()));
;                       }
;                   }
;               }
;           }

;           return stems;
;       }

;       public static Set<MorAna> hyphenicGuess(String root, String suffix)
;       {
;           Set<MorAna> morAnas = new TreeSet<MorAna>();

;           // kötőjeles suffix (pl.: Bush-hoz)
;           morAnas.addAll(morPhonGuess(root, suffix));

;           // suffix főnév (pl.: Bush-kormannyal)
;           for (String kr : RFSA.analyse(suffix))
;           {
;               for (MorAna morAna : KRTools.getMSD(kr))
;               {
;                   // csak fonevi elemzesek
;                   if (morAna.getMsd().startsWith("N"))
;                   {
;                       morAnas.add(new MorAna(root + "-" + morAna.getLemma(), morAna.getMsd()));
;                   }
;               }
;           }

;           return morAnas;
;       }

;       /**
;        * Minden számmal kezdődő token elemzését reguláris kifejezések végzik.
;        * Egy szóalakhoz több elemzés is tartozhat.
;        * Egy számmal kezdődő token lehet főnév (N) (pl.: 386-os@Nn-sn),
;        * melléknév (pl.: 16-ai@Afp-sn), számnév (pl.: 5.@Mo-snd)
;        * vagy nyílt tokenosztályba tartozó (pl.: 20%@Onp-sn).
;        */

;           private static final String abc = "([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)";

;       private static final Pattern
;           rxN_0 = Pattern.compile("\\d+.*"),

;           // 1-es 1.3-as 1,5-ös 1/6-os 16-17-es [Afp-sn, Nn-sn]
;           rxN_1 = Pattern.compile("(\\d+[0-9\\.,%-/]*-(as|ás|es|os|ös)+)" + abc),

;           // 16-i
;           rxN_2 = Pattern.compile("\\d+[0-9\\.,-/]*-i"),

;           // 16-(ai/ei/jei)
;           rxN_3 = Pattern.compile("(\\d+-(ai|ei|jei)+)" + abc),

;           // +12345
;           rxN_4 = Pattern.compile("([\\+|\\-]{1}\\d+[0-9\\.,-/]*)-??" + abc),

;           // 12345-12345
;           rxN_5 = Pattern.compile("(\\d+-\\d+)-??" + abc),

;           // 12:30 12.30 Ont-sn
;           rxN_6 = Pattern.compile("((\\d{1,2})[\\.:](\\d{2}))-??" + abc),

;           // 123,45-12345
;           rxN_7 = Pattern.compile("(\\d+,\\d+-\\d+)-??" + abc),

;           // 12345-12345,12345
;           rxN_8 = Pattern.compile("(\\d+-\\d+,\\d+)-??" + abc),

;           // 12345,12345-12345,12345
;           rxN_9 = Pattern.compile("(\\d+,\\d+-\\d+,\\d+)-??" + abc),

;           // 12345.12345,12345
;           rxN_10 = Pattern.compile("(\\d+\\.\\d+,\\d+)-??" + abc),

;           // 10:30
;           rxN_11 = Pattern.compile("(\\d+:\\d+)-??" + abc),

;           // 12345.12345.1234-.
;           rxN_12 = Pattern.compile("(\\d+\\.\\d+[0-9\\.]*)-??" + abc),

;           // 12,3-nak
;           rxN_13 = Pattern.compile("(\\d+,\\d+)-??" + abc),

;           // 20-nak
;           rxN_14 = Pattern.compile("(\\d+)-??" + abc),

;           // 20.
;           rxN_15 = Pattern.compile("((\\d+-??\\d*)\\.)-??" + abc),

;           // 16-áig
;           rxN_16 = Pattern.compile("((\\d{1,2})-(á|é|jé))" + abc),

;           // 16-a
;           rxN_17 = Pattern.compile("((\\d{1,2})-(a|e|je))()"),

;           // 50%
;           rxN_18 = Pattern.compile("(\\d+,??\\d*%)-??" + abc);

;       private static String nounToNumeral(String nounMsd, String numeralMsd)
;       {
;           StringBuilder msd = new StringBuilder(numeralMsd);

;           // szám
;           if (nounMsd.length() > 3)
;               msd.setCharAt(3, nounMsd.charAt(3));

;           // eset
;           if (nounMsd.length() > 4)
;               msd.setCharAt(4, nounMsd.charAt(4));

;           // birtokos száma
;           if (nounMsd.length() > 8)
;               msd.setCharAt(10, nounMsd.charAt(8));

;           // birtokos személye
;           if (nounMsd.length() > 9)
;               msd.setCharAt(11, nounMsd.charAt(9));

;           // birtok(olt) száma
;           if (nounMsd.length() > 10)
;               msd.setCharAt(12, nounMsd.charAt(10));

;           return KRTools.cleanMsd(msd.toString());
;       }

;       private static String nounToOther(String nounMsd, String otherMsd)
;       {
;           StringBuilder msd = new StringBuilder(otherMsd);

;           // szám
;           if (nounMsd.length() > 3)
;               msd.setCharAt(4, nounMsd.charAt(3));

;           // eset
;           if (nounMsd.length() > 4)
;               msd.setCharAt(5, nounMsd.charAt(4));

;           // birtokos száma
;           if (nounMsd.length() > 8)
;               msd.setCharAt(9, nounMsd.charAt(8));

;           // birtokos személye
;           if (nounMsd.length() > 9)
;               msd.setCharAt(10, nounMsd.charAt(9));

;           // birtok(olt) száma
;           if (nounMsd.length() > 10)
;               msd.setCharAt(11, nounMsd.charAt(10));

;           return KRTools.cleanMsd(msd.toString());
;       }

;       private static String nounToNoun(String nounMsd, String otherMsd)
;       {
;           StringBuilder msd = new StringBuilder(otherMsd);

;           // szám
;           if (nounMsd.length() > 3)
;               msd.setCharAt(3, nounMsd.charAt(3));

;           // eset
;           if (nounMsd.length() > 4)
;               msd.setCharAt(4, nounMsd.charAt(4));

;           return KRTools.cleanMsd(msd.toString());
;       }

;       private static int romanToArabic(String romanNumber)
;       {
;           char romanChars[] = { 'I', 'V', 'X', 'L', 'C', 'D', 'M' };
;           int arabicNumbers[] = { 1, 5, 10, 50, 100, 500, 1000 };
;           int temp[] = new int[20];
;           int sum = 0;

;           for (int i = 0; i < romanNumber.toCharArray().length; i++)
;           {
;               for (int j = 0; j < romanChars.length; j++)
;               {
;                   if (romanNumber.charAt(i) == romanChars[j])
;                   {
;                       temp[i] = arabicNumbers[j];
;                   }
;               }
;           }

;           for (int i = 0; i < temp.length; i++)
;           {
;               if (i == temp.length - 1)
;               {
;                   sum += temp[i];
;               }

;               else
;               {
;                   if (temp[i] < temp[i + 1])
;                   {
;                       sum += (temp[i + 1] - temp[i]);
;                       i++;
;                   }

;                   else
;                   {
;                       sum += temp[i];
;                   }
;               }
;           }

;           return sum;
;       }

;       /**
;        * 16 15-18 minden szám < 32
;        */
;       private static boolean isDate(String spelling)
;       {
;           for (String s : spelling.split("-"))
;           {
;               if (Integer.parseInt(s) > 31)
;               {
;                   return false;
;               }
;           }

;           return true;
;       }

;       /**
;        * számmal kezdődő token elemzése
;        *
;        * @param number
;        *          egy (számmal kezdődő) String
;        * @return lehetséges elemzéseket (lemma-msd párok)
;        */
;       public static Set<MorAna> numberGuess(String number)
;       {
;           Set<MorAna> stems = new TreeSet<MorAna>();

;           Matcher m = rxN_0.matcher(number);
;           if (!m.matches())
;           {
;               return stems;
;           }

;           m = rxN_1.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               // group 3!!!
;               // 386-osok (386-(os))(ok)
;               String suffix = m.group(3);

;               if (suffix.length() > 0)
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, stem.getMsd()));
;                       stems.add(new MorAna(root, stem.getMsd().replace("Nn-sn".substring(0, 2), "Afp")));
;                   }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(root, "Afp-sn"));
;                   stems.add(new MorAna(root, "Nn-sn"));
;               }

;               return stems;
;           }

;           // 16-i
;           m = rxN_2.matcher(number);
;           if (m.matches())
;           {
;               stems.add(new MorAna(number, "Afp-sn"));
;               stems.add(new MorAna(number, "Onf-sn"));
;               return stems;
;           }

;           // 16-(ai/ei/1-jei)
;           m = rxN_3.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(3);

;               if (suffix.length() > 0)
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, "Afp-" + stem.getMsd().substring(3)));
;                   }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(root, "Afp-sn"));
;               }

;               return stems;
;           }

;           // +/-12345
;           m = rxN_4.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(2);

;               if (suffix.length() > 0)
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Ons----------")));
;                   }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(number, "Ons-sn"));
;               }

;               return stems;
;           }

;           // 12:30 12.30 Ont-sn
;           m = rxN_6.matcher(number);
;           if (m.matches())
;           {
;               if (Integer.parseInt(m.group(2)) < 24 && Integer.parseInt(m.group(3)) < 60)
;               {
;                   String root = m.group(1);
;                   String suffix = m.group(4);

;                   if (suffix.length() > 0)
;                       for (MorAna stem : morPhonGuess(root, suffix))
;                       {
;                           stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Ont---------")));
;                       }

;                   if (stems.size() == 0)
;                   {
;                       stems.add(new MorAna(number, "Ont-sn"));
;                   }
;               }
;           }

;           // 12345-12345-*
;           m = rxN_5.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(2);

;               if (suffix.length() > 0)
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onr---------")));
;                       stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onf----------")));
;                       stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d-------")));
;                   }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(number, "Onr-sn"));
;                   stems.add(new MorAna(number, "Onf-sn"));
;                   stems.add(new MorAna(number, "Mc-snd"));
;               }

;               return stems;
;           }

;           // 12345,12345-12345,12345-*
;           // 12345-12345,12345-*
;           // 12345,12345-12345-*

;           m = rxN_7.matcher(number);

;           if (!m.matches())
;               m = rxN_8.matcher(number);
;           if (!m.matches())
;               m = rxN_9.matcher(number);

;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(2);

;               if (suffix.length() > 0)
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mf---d-------")));
;                   }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(number, "Mf-snd"));
;               }

;               return stems;
;           }

;           // 12345.12345,12345
;           m = rxN_10.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(2);

;               if (suffix.length() > 0)
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Ond---------")));
;                   }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(number, "Ond-sn"));
;               }

;               return stems;
;           }

;           // 10:30-*
;           m = rxN_11.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(2);

;               if (suffix.length() > 0)
;               {
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onf---------")));
;                       stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onq---------")));
;                       stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onr---------")));
;                   }
;               }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(number, "Onf-sn"));
;                   stems.add(new MorAna(number, "Onq-sn"));
;                   stems.add(new MorAna(number, "Onr-sn"));
;               }

;               return stems;
;           }

;           // 12345.12345.1234-.
;           m = rxN_12.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(2);

;               if (suffix.length() > 0)
;               {
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Oi----------")));
;                       stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Ond---------")));
;                   }
;               }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(number, "Oi--sn"));
;                   stems.add(new MorAna(number, "Ond-sn"));
;               }

;               return stems;
;           }

;           // 16-a 17-e 16-áig 17-éig 1-je 1-jéig

;           m = rxN_16.matcher(number);

;           if (!m.matches())
;               m = rxN_17.matcher(number);

;           if (m.matches())
;           {
;               String root = m.group(2);
;               String suffix = m.group(4);

;               if (suffix.length() > 0)
;               {
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d----s3-")));
;                       if (isDate(root))
;                       {
;                           stems.add(new MorAna(root + ".", nounToNoun(stem.getMsd(), "Nn-sn".substring(0, 2) + "------s3-")));
;                       }

;                       if (m.group(3).equals("�"))
;                       {
;                           stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d------s")));
;                       }
;                   }
;               }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(root, "Mc-snd----s3"));
;                   if (isDate(root))
;                   {
;                       stems.add(new MorAna(root + ".", "Nn-sn" + "---s3"));
;                   }
;               }

;               return stems;
;           }

;           // 50%
;           m = rxN_18.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(2);

;               if (suffix.length() > 0)
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onp---------")));
;                   }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(root, "Onp-sn"));
;               }

;               return stems;
;           }

;           // 12,3-nak
;           m = rxN_13.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(2);

;               if (suffix.length() > 0)
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mf---d-------")));
;                   }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(number, "Mf-snd"));
;               }

;               return stems;
;           }

;           // 20-nak
;           m = rxN_14.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(2);

;               if (suffix.length() > 0)
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d-------")));
;                   }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(number, "Mc-snd"));
;               }

;               return stems;
;           }

;           // 15.
;           m = rxN_15.matcher(number);
;           if (m.matches())
;           {
;               String root = m.group(1);
;               String suffix = m.group(3);

;               if (suffix.length() > 0)
;                   for (MorAna stem : morPhonGuess(root, suffix))
;                   {
;                       stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mo---d-------")));

;                       if (isDate(m.group(2)))
;                       {
;                           stems.add(new MorAna(root, stem.getMsd()));
;                       }
;                   }

;               if (stems.size() == 0)
;               {
;                   stems.add(new MorAna(number, "Mo-snd"));
;                   if (isDate(m.group(2)))
;                   {
;                       stems.add(new MorAna(number, "Nn-sn"));
;                       stems.add(new MorAna(number, "Nn-sn" + "---s3"));
;                   }
;               }

;               return stems;
;           }

;           if (stems.size() == 0)
;           {
;               stems.add(new MorAna(number, "Oi--sn"));
;           }

;           return stems;
;       }

;           private static final String
;               rMDC = "(CM|CD|D?C{0,3})",
;               rCLX = "(XC|XL|L?X{0,3})",
;               rXVI = "(IX|IV|V?I{0,3})";

;       public static Set<MorAna> guessRomanNumber(String word)
;       {
;           Set<MorAna> stems = new HashSet<MorAna>();

;           // MCMLXXXIV
;           if (word.matches("^M{0,4}" + rMDC + rCLX + rXVI + "$"))
;           {
;               int n = romanToArabic(word);
;               stems.add(new MorAna(String.valueOf(n), "Mc-snr"));
;           }

;           // MCMLXXXIV.
;           else if (word.matches("^M{0,4}" + rMDC + rCLX + rXVI + "\\.$"))
;           {
;               int n = romanToArabic(word.substring(0, word.length() - 1));
;               stems.add(new MorAna(String.valueOf(n) + ".", "Mo-snr"));
;           }

;           // MCMLXXXIV-MMIX
;           else if (word.matches("^M{0,4}" + rMDC + rCLX + rXVI + "-M{0,4}" + rMDC + rCLX + rXVI + "$"))
;           {
;               int n = romanToArabic(word.substring(0, word.indexOf("-")));
;               int m = romanToArabic(word.substring(word.indexOf("-") + 1, word.length()));
;               stems.add(new MorAna(String.valueOf(n) + "-" + String.valueOf(m), "Mc-snr"));
;           }

;           // MCMLXXXIV-MMIX.
;           else if (word.matches("^M{0,4}" + rMDC + rCLX + rXVI + "-M{0,4}" + rMDC + rCLX + rXVI + "\\.$"))
;           {
;               int n = romanToArabic(word.substring(0, word.indexOf("-")));
;               int m = romanToArabic(word.substring(word.indexOf("-") + 1, word.length()));
;               stems.add(new MorAna(String.valueOf(n) + "-" + String.valueOf(m) + ".", "Mo-snr"));
;           }

;           return stems;
;       }

;       public static void main(String[] args)
;       {
;           // System.out.println(getMorphologicalAnalyses("lehet"));

;           System.out.println(morPhonGuess("London", "ban"));

;           System.out.println(hyphenicGuess("Bush", "hoz"));
;           System.out.println(hyphenicGuess("Bush", "kormánynak"));

;           System.out.println(numberGuess("386-osok"));
;           System.out.println(numberGuess("16-ai"));
;           System.out.println(numberGuess("5."));
;           System.out.println(numberGuess("20%"));
;       }
