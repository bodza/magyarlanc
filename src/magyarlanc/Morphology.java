package magyarlanc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.stanford.nlp.ling.TaggedWord;
import edu.stanford.nlp.tagger.maxent.SzteMaxentTagger;

import magyarlanc.KRTools.KRPOS;

import data.Data;

public class Morphology
{
    private static final String POS_MODEL = "25.model";
    private static final String CORPUS = "25.lex";
    private static final String FREQUENCIES = "25.freq";

    private static SzteMaxentTagger maxentTagger;

    public static SzteMaxentTagger getMaxentTagger()
    {
        if (maxentTagger == null)
        {
            try
            {
                maxentTagger = new SzteMaxentTagger("./data/" + POS_MODEL);
                maxentTagger.setVerbose(false);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return maxentTagger;
    }

    private static Map<String, Set<MorAna>> corpus;

    public static Map<String, Set<MorAna>> getCorpus()
    {
        if (corpus == null)
            corpus = readCorpus(CORPUS);

        return corpus;
    }

    public static Map<String, Set<MorAna>> readCorpus(String file)
    {
        Map<String, Set<MorAna>> corpus = new TreeMap<String, Set<MorAna>>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Data.class.getResourceAsStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                Set<MorAna> morAnas = new TreeSet<MorAna>();

                String[] splitted = line.split("\t");
                for (int i = 1; i < splitted.length - 1; i++)
                {
                    morAnas.add(new MorAna(splitted[i], splitted[i + 1]));
                    i++;
                }

                corpus.put(splitted[0], morAnas);
            }

            reader.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return corpus;
    }

    private static Map<String, Integer> frequencies;

    public static Map<String, Integer> getFrequencies()
    {
        if (frequencies == null)
            frequencies = readFrequencies(FREQUENCIES);

        return frequencies;
    }

    public static Map<String, Integer> readFrequencies(String file)
    {
        Map<String, Integer> frequencies = new TreeMap<String, Integer>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Data.class.getResourceAsStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                String[] splitted = line.split("\t");
                frequencies.put(splitted[0], Integer.parseInt(splitted[1]));
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return frequencies;
    }

    public static String[][] morphParseSentence(String[] form)
    {
        return getMaxentTagger().morphSentence(form);
    }

    public static String[][] morphParseSentence(List<String> form)
    {
        return getMaxentTagger().morphSentence(form.toArray(new String[form.size()]));
    }

    public static String[][] morphParseSentence(String sentence)
    {
        return morphParseSentence(HunSplitter.tokenize(sentence));
    }

    public static String[][][] morphParse(String text)
    {
        List<String[][]> morph = new ArrayList<String[][]>();

        for (String[] sentence : HunSplitter.splitToArray(text))
        {
            morph.add(morphParseSentence(sentence));
        }

        return morph.toArray(new String[morph.size()][][]);
    }

    /**
     * Line by line.
     */
    public static String[][][] morphParse(List<String> lines)
    {
        List<String[][]> morph = new ArrayList<String[][]>();

        for (String line : lines)
        {
            for (String[] sentence : HunSplitter.splitToArray(line))
            {
                morph.add(morphParseSentence(sentence));
            }
        }

        return morph.toArray(new String[morph.size()][][]);
    }

    public static String[][][] morphParse(String[][] text)
    {
        List<String[][]> morph = new ArrayList<String[][]>();

        for (String[] sentence : text)
        {
            morph.add(morphParseSentence(sentence));
        }

        return morph.toArray(new String[morph.size()][][]);
    }

    public static class MorAna implements Comparable<MorAna>
    {
        private String lemma;
        private String msd;

        public MorAna(String lemma, String msd)
        {
            this.lemma = lemma;
            this.msd = msd;
        }

        public String toString()
        {
            return lemma + "@" + msd;
        }

        public final String getLemma()
        {
            return lemma;
        }

        public final String getMsd()
        {
            return msd;
        }

        public int compareTo(MorAna morAna)
        {
            int cmp = lemma.compareTo(morAna.lemma);

            if (cmp == 0)
                cmp = msd.compareTo(morAna.msd);

            return cmp;
        }

        public boolean equals(MorAna morAna)
        {
            return (compareTo(morAna) == 0);
        }
    }

    /**
     * adott szó csak írásjeleket tartalmaz-e
     */
    private static boolean isPunctation(String spelling)
    {
        for (int i = 0; i < spelling.length(); ++i)
        {
            if (Character.isLetterOrDigit(spelling.charAt(i)))
            {
                return false;
            }
        }

        return true;
    }

    private static Set<String> punctations = new HashSet<String>()
    {{
        String[] puncts = { "!", ",", "-", ".", ":", ";", "?", "–" };

        for (String punct : puncts)
        {
            add(punct);
        }
    }};

    public static Set<String> getPunctations()
    {
        return punctations;
    }

    private static Map<String, String> corrDic;

    private static Map<String, String> getCorrDic()
    {
        if (corrDic == null)
            corrDic = readMap("corrdic.txt");

        return corrDic;
    }

    public static Map<String, String> readMap(String file)
    {
        Map<String, String> map = new TreeMap<String, String>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Data.class.getResourceAsStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                String[] splitted = line.split("\t");
                map.put(splitted[0], splitted[1]);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return map;
    }

    /**
     * adott szó lehetséges morfológiai elemzéseinek meghatározása
     */
    public static Set<MorAna> getMorphologicalAnalyses(String word)
    {
        Set<MorAna> morAnas = new TreeSet<MorAna>();

        // írásjelek
        if (isPunctation(word))
        {
            // a legfontosabb írásjelek lemmája maga az írásjel,
            // POS kódja szintén maga az írásjel lesz
            // . , ; : ! ? - -
            if (getPunctations().contains(word))
            {
                morAnas.add(new MorAna(word, word));
            }

            // § lemmája maga az írásjel, POS kódja 'Nn-sn' lesz
            else if (word.equals("§"))
            {
                morAnas.add(new MorAna(word, "Nn-sn"));
            }

            // egyéb írásjelek lemmája maga az írásjel, POS kódja 'K' lesz
            else
            {
                morAnas.add(new MorAna(word, "K"));
            }

            return morAnas;
        }

        // ha benne van a corpus.lex-ben
        if (getCorpus().containsKey(word))
        {
            return getCorpus().get(word);
        }

        // ha benne van a corpus.lex-ben kisbetűvel
        if (getCorpus().containsKey(word.toLowerCase()))
        {
            return getCorpus().get(word.toLowerCase());
        }

        // szám
        morAnas = numberGuess(word);

        if (morAnas.size() > 0)
        {
            return morAnas;
        }

        // római szám
        morAnas.addAll(guessRomanNumber(word));

        // rfsa
        for (String kr : RFSA.analyse(word))
        {
            morAnas.addAll(KRTools.getMSD(kr));
        }

        // (kötőjeles) összetett szó
        if (morAnas.size() == 0)
        {
            // kötőjeles
            if (word.contains("-") && word.indexOf("-") > 1)
            {
                for (String morphCode : analyseHyphenicCompoundWord(word))
                {
                    morAnas.addAll(KRTools.getMSD(morphCode));
                }
            }
            else
            {
                // összetett szó
                for (String morphCode : analyseCompoundWord(word.toLowerCase()))
                {
                    morAnas.addAll(KRTools.getMSD(morphCode));
                }
            }
        }

        // guess (Bush-nak, Bush-kormányhoz)
        if (morAnas.size() == 0)
        {
            int index = word.lastIndexOf("-") > 1 ? word.lastIndexOf("-") : 0;

            if (index > 0)
            {
                String root = word.substring(0, index);
                String suffix = word.substring(index + 1);

                morAnas.addAll(hyphenicGuess(root, suffix));
            }
        }

        // téves szavak
        if (morAnas.size() == 0)
        {
            Map<String, String> corrDic = getCorrDic();

            if (corrDic.containsKey(word) && !word.equals(corrDic.get(word)))
            {
                morAnas.addAll(getMorphologicalAnalyses(corrDic.get(word)));
            }
            else
            {
                String low = word.toLowerCase();

                if (corrDic.containsKey(low) && !word.equals(corrDic.get(low)))
                {
                    morAnas.addAll(getMorphologicalAnalyses(corrDic.get(low)));
                }
            }
        }

        return morAnas;
    }

    public static String[] getPossibleTags(String word, Set<String> possibleTags)
    {
        Set<MorAna> morAnas = getMorphologicalAnalyses(word);
        Set<String> tags = new HashSet<String>();

        for (MorAna morAna : morAnas)
        {
            String reduced = MSDTools.reduceMSD(morAna.getMsd());
            if (possibleTags.contains(reduced))
            {
                tags.add(reduced);
            }
        }

        if (tags.size() == 0)
        {
            tags.add("X");
        }

        return tags.toArray(new String[tags.size()]);
    }

    public static List<TaggedWord> recoverTags(List<TaggedWord> sentence)
    {
        for (TaggedWord tw : sentence)
        {
            int max = -1;
            MorAna argmax = null;

            for (MorAna morAna : getMorphologicalAnalyses(tw.word()))
            {
                int freq = getFrequencies().containsKey(morAna.getMsd()) ? getFrequencies().get(morAna.getMsd()) : 0;

                if (!morAna.getMsd().equals(null))
                {
                    if (MSDTools.reduceMSD(morAna.getMsd()).equals(tw.tag()) && (max < freq))
                    {
                        argmax = morAna;
                        max = freq;
                    }
                }
            }

            if (argmax != null)
            {
                tw.setValue(argmax.getLemma());
                tw.setTag(argmax.getMsd());
            }
            else
            {
                tw.setValue(tw.word());
            }
        }

        return sentence;
    }

    public static boolean isCompatibleAnalyises(String kr1, String kr2)
    {
        KRPOS pos1 = KRTools.getPOS(kr1), pos2 = KRTools.getPOS(kr2);

        // UTT-INT nem lehet a második rész
        if (pos2.equals(KRPOS.UTT_INT))
            return false;

        // ART nem lehet a második rész
        if (pos2.equals(KRPOS.ART))
            return false;

        // NUM előtt csak NUM állhat
        if (pos2.equals(KRPOS.NUM) && !pos1.equals(KRPOS.NUM))
            return false;

        // PREV nem lehet a második rész
        if (pos2.equals(KRPOS.PREV))
            return false;

        // NOUN + ADV letiltva
        if (pos1.equals(KRPOS.NOUN) && pos2.equals(KRPOS.ADV))
            return false;

        // VERB + ADV letiltva
        if (pos1.equals(KRPOS.VERB) && pos2.equals(KRPOS.ADV))
            return false;

        // PREV + NOUN letiltva
        if (pos1.equals(KRPOS.PREV) && pos2.equals(KRPOS.NOUN))
            return false;

        // ADJ + VERB letiltva
        if (pos1.equals(KRPOS.ADJ) && pos2.equals(KRPOS.VERB))
            return false;

        // VERB + NOUN letiltva
        if (pos1.equals(KRPOS.VERB) && pos2.equals(KRPOS.NOUN))
            return false;

        // NOUN + VERB csak akkor lehet, ha van a NOUN-nak <CAS>
        if (pos1.equals(KRPOS.NOUN) && pos2.equals(KRPOS.VERB) && !kr1.contains("CAS"))
            return false;

        // NOUN + VERB<PAST><DEF> és nincs a NOUN-nak <CAS> akkor /ADJ
        if (pos1.equals(KRPOS.NOUN) && pos2.equals(KRPOS.VERB) && !kr1.contains("CAS") && kr2.contains("<PAST><DEF>") && kr2.contains("<DEF>"))
            return false;

        return true;
    }

    public static boolean isBisectable(String word)
    {
        for (int i = 2; i < word.length() - 1; ++i)
        {
            if (RFSA.analyse(word.substring(0, i)).size() > 0 && RFSA.analyse(word.substring(i)).size() > 0)
            {
                return true;
            }
        }

        return false;
    }

    public static int bisectIndex(String word)
    {
        for (int i = 2; i < word.length() - 1; ++i)
        {
            if (RFSA.analyse(word.substring(0, i)).size() > 0 && RFSA.analyse(word.substring(i)).size() > 0)
            {
                return i;
            }
        }

        return 0;
    }

    public static Set<String> getCompatibleAnalises(String part1, String part2)
    {
        return getCompatibleAnalises(part1, part2, false);
    }

    public static Set<String> getCompatibleAnalises(String part1, String part2, boolean hyphenic)
    {
        Set<String> analises = new LinkedHashSet<String>();

        List<String> ans1 = RFSA.analyse(part1), ans2 = RFSA.analyse(part2);

        if (ans1.size() > 0 && ans2.size() > 0)
        {
            for (String f : ans1)
            {
                for (String s : ans2)
                {
                    String kr1 = KRTools.getRoot(f), kr2 = KRTools.getRoot(s);

                    if (isCompatibleAnalyises(kr1, kr2))
                    {
                        if (hyphenic)
                        {
                            analises.add(kr2.replace("$", "$" + part1 + "-"));
                        }
                        else
                        {
                            analises.add(kr2.replace("$", "$" + part1));
                        }
                    }
                }
            }
        }

        return analises;
    }

    public static Set<String> analyseCompoundWord(String word)
    {
        // 2 részre vágható van elemzés
        if (isBisectable(word))
        {
            int bi = bisectIndex(word);
            String part1 = word.substring(0, bi);
            String part2 = word.substring(bi);

            return getCompatibleAnalises(part1, part2);
        }

        Set<String> analises = new LinkedHashSet<String>();

        // ha nem bontható 2 részre
        for (int i = 2; i < word.length() - 1; ++i)
        {
            String part1 = word.substring(0, i);
            String part2 = word.substring(i);

            List<String> ans1 = RFSA.analyse(part1);
            if (ans1.size() > 0)
            {
                // ha a második rész két részre bontható
                if (isBisectable(part2))
                {
                    int bi = bisectIndex(part2);
                    String part21 = part2.substring(0, bi);
                    String part22 = part2.substring(bi);

                    Set<String> ans2 = getCompatibleAnalises(part21, part22);

                    for (String a1 : ans1)
                    {
                        for (String a2 : ans2)
                        {
                            if (isCompatibleAnalyises(KRTools.getRoot(a1), KRTools.getRoot(a2)))
                            {
                                analises.add(KRTools.getRoot(a2).replace("$", "$" + part1));
                            }
                        }
                    }
                }
            }
        }

        return analises;
    }

    public static Set<String> analyseHyphenicCompoundWord(String word)
    {
        Set<String> analises = new LinkedHashSet<String>();

        if (!word.contains("-"))
        {
            return analises;
        }

        int hp = word.indexOf('-');
        String part1 = word.substring(0, hp), part2 = word.substring(hp + 1);

        // a kötőjel előtti és a kötőjel utáni résznek is van elemzése (pl.: adat-kezelőt)
        if (isBisectable(part1 + part2))
        {
            analises = getCompatibleAnalises(part1, part2, true);
        }

        // a kötőjel előtti résznek is van elemzése, a kötőjel utáni rész két részre bontható
        else if (RFSA.analyse(part1).size() > 0 && isBisectable(part2))
        {
            List<String> ans1 = RFSA.analyse(part1);

            int bi = bisectIndex(part2);
            String part21 = part2.substring(0, bi), part22 = part2.substring(bi);

            Set<String> ans2 = getCompatibleAnalises(part21, part22);

            for (String a1 : ans1)
            {
                for (String a2 : ans2)
                {
                    if (isCompatibleAnalyises(KRTools.getRoot(a1), KRTools.getRoot(a2)))
                    {
                        if (analises == null)
                        {
                            analises = new LinkedHashSet<String>();
                        }
                        analises.add(KRTools.getRoot(a2).replace("$", "$" + part1 + "-"));
                    }
                }
            }
        }

        else if (isBisectable(part1) && RFSA.analyse(part2).size() > 0)
        {
            List<String> ans2 = RFSA.analyse(part2);

            int bi = bisectIndex(part1);
            String part11 = part1.substring(0, bi), part12 = part1.substring(bi);

            Set<String> ans1 = getCompatibleAnalises(part11, part12);

            for (String a1 : ans1)
            {
                for (String a2 : ans2)
                {
                    if (isCompatibleAnalyises(KRTools.getRoot(a1), KRTools.getRoot(a2)))
                    {
                        if (analises == null)
                        {
                            analises = new LinkedHashSet<String>();
                        }
                        analises.add(KRTools.getRoot(a2).replace("$", "$" + part1 + "-"));
                    }
                }
            }
        }

        return analises;
    }

    private static Set<String> morPhonDir = new HashSet<String>()
    {{
        String[] morPhons = { "talány", "némber", "sün", "fal", "holló", "felhő", "kalap", "hely", "köd" };

        for (String morPhon : morPhons)
        {
            add(morPhon);
        }
    }};

    private static Set<String> getMorPhonDir()
    {
        return morPhonDir;
    }

    /**
     * A morPhonGuess függvény egy ismeretlen (nem elemezhető) főnévi szótő és
     * tetszőleges suffix guesselésére szolgál. A guesselés során az adott suffixet
     * a rendszer morPhonDir szótárának elemeire illesztve probáljuk elemezni. A
     * szótár reprezentálja a magyar nyelv minden (nem hasonuló) illeszkedési
     * szabályát, így biztosak lehetünk benne, hogy egy valós toldalék mindenképp
     * illeszkedni fog legalább egy szótárelemre. Például egy 'hoz'rag esetén,
     * először a köd elemre próbálunk illeszteni, majd elemezni. A kapott szóalak
     * így a ködhoz lesz, melyre a KR elemzőnk nem ad elemzést. A következő
     * szótárelem a talány, a szóalak a talányhoz lesz, melyre megkapjuk az Nc-st
     * (külső közelítő/allative) főnévi elemzést.
     */
    public static Set<MorAna> morPhonGuess(String root, String suffix)
    {
        Set<MorAna> stems = new TreeSet<MorAna>();

        for (String guess : getMorPhonDir())
        {
            for (String kr : RFSA.analyse(guess + suffix))
            {
                for (MorAna stem : KRTools.getMSD(kr))
                {
                    if (stem.getMsd().startsWith("N"))
                    {
                        stems.add(new MorAna(root, stem.getMsd()));
                    }
                }
            }
        }

        return stems;
    }

    public static Set<MorAna> hyphenicGuess(String root, String suffix)
    {
        Set<MorAna> morAnas = new TreeSet<MorAna>();

        // kötőjeles suffix (pl.: Bush-hoz)
        morAnas.addAll(morPhonGuess(root, suffix));

        // suffix főnév (pl.: Bush-kormannyal)
        for (String kr : RFSA.analyse(suffix))
        {
            for (MorAna morAna : KRTools.getMSD(kr))
            {
                // csak fonevi elemzesek
                if (morAna.getMsd().startsWith("N"))
                {
                    morAnas.add(new MorAna(root + "-" + morAna.getLemma(), morAna.getMsd()));
                }
            }
        }

        return morAnas;
    }

    /**
     * Minden számmal kezdődő token elemzését reguláris kifejezések végzik.
     * Egy szóalakhoz több elemzés is tartozhat.
     * Egy számmal kezdődő token lehet főnév (N) (pl.: 386-os@Nn-sn),
     * melléknév (pl.: 16-ai@Afp-sn), számnév (pl.: 5.@Mo-snd)
     * vagy nyílt tokenosztályba tartozó (pl.: 20%@Onp-sn).
     */

        private static final String abc = "([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)";

    private static final Pattern
        rxN_0 = Pattern.compile("\\d+.*"),

        // 1-es 1.3-as 1,5-ös 1/6-os 16-17-es [Afp-sn, Nn-sn]
        rxN_1 = Pattern.compile("(\\d+[0-9\\.,%-/]*-(as|ás|es|os|ös)+)" + abc),

        // 16-i
        rxN_2 = Pattern.compile("\\d+[0-9\\.,-/]*-i"),

        // 16-(ai/ei/jei)
        rxN_3 = Pattern.compile("(\\d+-(ai|ei|jei)+)" + abc),

        // +12345
        rxN_4 = Pattern.compile("([\\+|\\-]{1}\\d+[0-9\\.,-/]*)-??" + abc),

        // 12345-12345
        rxN_5 = Pattern.compile("(\\d+-\\d+)-??" + abc),

        // 12:30 12.30 Ont-sn
        rxN_6 = Pattern.compile("((\\d{1,2})[\\.:](\\d{2}))-??" + abc),

        // 123,45-12345
        rxN_7 = Pattern.compile("(\\d+,\\d+-\\d+)-??" + abc),

        // 12345-12345,12345
        rxN_8 = Pattern.compile("(\\d+-\\d+,\\d+)-??" + abc),

        // 12345,12345-12345,12345
        rxN_9 = Pattern.compile("(\\d+,\\d+-\\d+,\\d+)-??" + abc),

        // 12345.12345,12345
        rxN_10 = Pattern.compile("(\\d+\\.\\d+,\\d+)-??" + abc),

        // 10:30
        rxN_11 = Pattern.compile("(\\d+:\\d+)-??" + abc),

        // 12345.12345.1234-.
        rxN_12 = Pattern.compile("(\\d+\\.\\d+[0-9\\.]*)-??" + abc),

        // 12,3-nak
        rxN_13 = Pattern.compile("(\\d+,\\d+)-??" + abc),

        // 20-nak
        rxN_14 = Pattern.compile("(\\d+)-??" + abc),

        // 20.
        rxN_15 = Pattern.compile("((\\d+-??\\d*)\\.)-??" + abc),

        // 16-áig
        rxN_16 = Pattern.compile("((\\d{1,2})-(á|é|jé))" + abc),

        // 16-a
        rxN_17 = Pattern.compile("((\\d{1,2})-(a|e|je))()"),

        // 50%
        rxN_18 = Pattern.compile("(\\d+,??\\d*%)-??" + abc);

    private static String nounToNumeral(String nounMsd, String numeralMsd)
    {
        StringBuilder msd = new StringBuilder(numeralMsd);

        // szam
        if (nounMsd.length() > 3)
            msd.setCharAt(3, nounMsd.charAt(3));

        // eset
        if (nounMsd.length() > 4)
            msd.setCharAt(4, nounMsd.charAt(4));

        // birtokos szama
        if (nounMsd.length() > 8)
            msd.setCharAt(10, nounMsd.charAt(8));

        // birtokos szemelye
        if (nounMsd.length() > 9)
            msd.setCharAt(11, nounMsd.charAt(9));

        // birtok(olt) szama
        if (nounMsd.length() > 10)
            msd.setCharAt(12, nounMsd.charAt(10));

        return KRTools.cleanMsd(msd.toString());
    }

    private static String nounToOther(String nounMsd, String otherMsd)
    {
        StringBuilder msd = new StringBuilder(otherMsd);

        // szam
        if (nounMsd.length() > 3)
            msd.setCharAt(4, nounMsd.charAt(3));

        // eset
        if (nounMsd.length() > 4)
            msd.setCharAt(5, nounMsd.charAt(4));

        // birtokos szama
        if (nounMsd.length() > 8)
            msd.setCharAt(9, nounMsd.charAt(8));

        // birtokos szemelye
        if (nounMsd.length() > 9)
            msd.setCharAt(10, nounMsd.charAt(9));

        // birtok(olt) szama
        if (nounMsd.length() > 10)
            msd.setCharAt(11, nounMsd.charAt(10));

        return KRTools.cleanMsd(msd.toString());
    }

    private static String nounToNoun(String nounMsd, String otherMsd)
    {
        StringBuilder msd = new StringBuilder(otherMsd);

        // szam
        if (nounMsd.length() > 3)
            msd.setCharAt(3, nounMsd.charAt(3));

        // eset
        if (nounMsd.length() > 4)
            msd.setCharAt(4, nounMsd.charAt(4));

        return KRTools.cleanMsd(msd.toString());
    }

    private static int romanToArabic(String romanNumber)
    {
        char romanChars[] = { 'I', 'V', 'X', 'L', 'C', 'D', 'M' };
        int arabicNumbers[] = { 1, 5, 10, 50, 100, 500, 1000 };
        int temp[] = new int[20];
        int sum = 0;

        for (int i = 0; i < romanNumber.toCharArray().length; i++)
        {
            for (int j = 0; j < romanChars.length; j++)
            {
                if (romanNumber.charAt(i) == romanChars[j])
                {
                    temp[i] = arabicNumbers[j];
                }
            }
        }

        for (int i = 0; i < temp.length; i++)
        {
            if (i == temp.length - 1)
            {
                sum += temp[i];
            }

            else
            {
                if (temp[i] < temp[i + 1])
                {
                    sum += (temp[i + 1] - temp[i]);
                    i++;
                }

                else
                {
                    sum += temp[i];
                }
            }
        }

        return sum;
    }

    /**
     * 16 15-18 minden szám < 32
     */
    private static boolean isDate(String spelling)
    {
        for (String s : spelling.split("-"))
        {
            if (Integer.parseInt(s) > 31)
            {
                return false;
            }
        }

        return true;
    }

    /**
     * számmal kezdődő token elemzése
     *
     * @param number
     *          egy (számmal kezdődő) String
     * @return lehetséges elemzéseket (lemma-msd párok)
     */
    public static Set<MorAna> numberGuess(String number)
    {
        Set<MorAna> stems = new TreeSet<MorAna>();

        Matcher m = rxN_0.matcher(number);
        if (!m.matches())
        {
            return stems;
        }

        m = rxN_1.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            // group 3!!!
            // 386-osok (386-(os))(ok)
            String suffix = m.group(3);

            if (suffix.length() > 0)
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, stem.getMsd()));
                    stems.add(new MorAna(root, stem.getMsd().replace("Nn-sn".substring(0, 2), "Afp")));
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(root, "Afp-sn"));
                stems.add(new MorAna(root, "Nn-sn"));
            }

            return stems;
        }

        // 16-i
        m = rxN_2.matcher(number);
        if (m.matches())
        {
            stems.add(new MorAna(number, "Afp-sn"));
            stems.add(new MorAna(number, "Onf-sn"));
            return stems;
        }

        // 16-(ai/ei/1-jei)
        m = rxN_3.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(3);

            if (suffix.length() > 0)
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, "Afp-" + stem.getMsd().substring(3)));
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(root, "Afp-sn"));
            }

            return stems;
        }

        // +/-12345
        m = rxN_4.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Ons----------")));
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(number, "Ons-sn"));
            }

            return stems;
        }

        // 12:30 12.30 Ont-sn
        m = rxN_6.matcher(number);
        if (m.matches())
        {
            if (Integer.parseInt(m.group(2)) < 24 && Integer.parseInt(m.group(3)) < 60)
            {
                String root = m.group(1);
                String suffix = m.group(4);

                if (suffix.length() > 0)
                    for (MorAna stem : morPhonGuess(root, suffix))
                    {
                        stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Ont---------")));
                    }

                if (stems.size() == 0)
                {
                    stems.add(new MorAna(number, "Ont-sn"));
                }
            }
        }

        // 12345-12345-*
        m = rxN_5.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onr---------")));
                    stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onf----------")));
                    stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d-------")));
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(number, "Onr-sn"));
                stems.add(new MorAna(number, "Onf-sn"));
                stems.add(new MorAna(number, "Mc-snd"));
            }

            return stems;
        }

        // 12345,12345-12345,12345-*
        // 12345-12345,12345-*
        // 12345,12345-12345-*

        m = rxN_7.matcher(number);

        if (!m.matches())
            m = rxN_8.matcher(number);
        if (!m.matches())
            m = rxN_9.matcher(number);

        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mf---d-------")));
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(number, "Mf-snd"));
            }

            return stems;
        }

        // 12345.12345,12345
        m = rxN_10.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Ond---------")));
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(number, "Ond-sn"));
            }

            return stems;
        }

        // 10:30-*
        m = rxN_11.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(2);

            if (suffix.length() > 0)
            {
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onf---------")));
                    stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onq---------")));
                    stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onr---------")));
                }
            }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(number, "Onf-sn"));
                stems.add(new MorAna(number, "Onq-sn"));
                stems.add(new MorAna(number, "Onr-sn"));
            }

            return stems;
        }

        // 12345.12345.1234-.
        m = rxN_12.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(2);

            if (suffix.length() > 0)
            {
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Oi----------")));
                    stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Ond---------")));
                }
            }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(number, "Oi--sn"));
                stems.add(new MorAna(number, "Ond-sn"));
            }

            return stems;
        }

        // 16-a 17-e 16-áig 17-éig 1-je 1-jéig

        m = rxN_16.matcher(number);

        if (!m.matches())
            m = rxN_17.matcher(number);

        if (m.matches())
        {
            String root = m.group(2);
            String suffix = m.group(4);

            if (suffix.length() > 0)
            {
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d----s3-")));
                    if (isDate(root))
                    {
                        stems.add(new MorAna(root + ".", nounToNoun(stem.getMsd(), "Nn-sn".substring(0, 2) + "------s3-")));
                    }

                    if (m.group(3).equals("�"))
                    {
                        stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d------s")));
                    }
                }
            }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(root, "Mc-snd----s3"));
                if (isDate(root))
                {
                    stems.add(new MorAna(root + ".", "Nn-sn" + "---s3"));
                }
            }

            return stems;
        }

        // 50%
        m = rxN_18.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToOther(stem.getMsd(), "Onp---------")));
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(root, "Onp-sn"));
            }

            return stems;
        }

        // 12,3-nak
        m = rxN_13.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mf---d-------")));
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(number, "Mf-snd"));
            }

            return stems;
        }

        // 20-nak
        m = rxN_14.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d-------")));
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(number, "Mc-snd"));
            }

            return stems;
        }

        // 15.
        m = rxN_15.matcher(number);
        if (m.matches())
        {
            String root = m.group(1);
            String suffix = m.group(3);

            if (suffix.length() > 0)
                for (MorAna stem : morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mo---d-------")));

                    if (isDate(m.group(2)))
                    {
                        stems.add(new MorAna(root, stem.getMsd()));
                    }
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(number, "Mo-snd"));
                if (isDate(m.group(2)))
                {
                    stems.add(new MorAna(number, "Nn-sn"));
                    stems.add(new MorAna(number, "Nn-sn" + "---s3"));
                }
            }

            return stems;
        }

        if (stems.size() == 0)
        {
            stems.add(new MorAna(number, "Oi--sn"));
        }

        return stems;
    }

        private static final String
            rMDC = "(CM|CD|D?C{0,3})",
            rCLX = "(XC|XL|L?X{0,3})",
            rXVI = "(IX|IV|V?I{0,3})";

    public static Set<MorAna> guessRomanNumber(String word)
    {
        Set<MorAna> stems = new HashSet<MorAna>();

        // MCMLXXXIV
        if (word.matches("^M{0,4}" + rMDC + rCLX + rXVI + "$"))
        {
            int n = romanToArabic(word);
            stems.add(new MorAna(String.valueOf(n), "Mc-snr"));
        }

        // MCMLXXXIV.
        else if (word.matches("^M{0,4}" + rMDC + rCLX + rXVI + "\\.$"))
        {
            int n = romanToArabic(word.substring(0, word.length() - 1));
            stems.add(new MorAna(String.valueOf(n) + ".", "Mo-snr"));
        }

        // MCMLXXXIV-MMIX
        else if (word.matches("^M{0,4}" + rMDC + rCLX + rXVI + "-M{0,4}" + rMDC + rCLX + rXVI + "$"))
        {
            int n = romanToArabic(word.substring(0, word.indexOf("-")));
            int m = romanToArabic(word.substring(word.indexOf("-") + 1, word.length()));
            stems.add(new MorAna(String.valueOf(n) + "-" + String.valueOf(m), "Mc-snr"));
        }

        // MCMLXXXIV-MMIX.
        else if (word.matches("^M{0,4}" + rMDC + rCLX + rXVI + "-M{0,4}" + rMDC + rCLX + rXVI + "\\.$"))
        {
            int n = romanToArabic(word.substring(0, word.indexOf("-")));
            int m = romanToArabic(word.substring(word.indexOf("-") + 1, word.length()));
            stems.add(new MorAna(String.valueOf(n) + "-" + String.valueOf(m) + ".", "Mo-snr"));
        }

        return stems;
    }

    public static void main(String[] args)
    {
        // System.out.println(getMorphologicalAnalyses("lehet"));

        System.out.println(morPhonGuess("London", "ban"));

        System.out.println(hyphenicGuess("Bush", "hoz"));
        System.out.println(hyphenicGuess("Bush", "kormánynak"));

        System.out.println(numberGuess("386-osok"));
        System.out.println(numberGuess("16-ai"));
        System.out.println(numberGuess("5."));
        System.out.println(numberGuess("20%"));
    }
}
