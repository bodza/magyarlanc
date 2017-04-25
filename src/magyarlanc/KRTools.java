package magyarlanc;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import magyarlanc.Morphology.MorAna;

public class KRTools
{
    /**
     * melléknévi igenevek
     */
    public static boolean isParticiple(String krAns)
    {
        int verbIndex = krAns.indexOf("/VERB"), adjIndex = krAns.indexOf("/ADJ");

        return (verbIndex > -1 && adjIndex > -1 && adjIndex > verbIndex);
    }

    public static String getPostPLemma(String ans)
    {
        if (ans.startsWith("$én/NOUN<POSTP<")
         || ans.startsWith("$te/NOUN<POSTP<")
         || ans.startsWith("$ő/NOUN<POSTP<")
         || ans.startsWith("$mi/NOUN<POSTP<")
         || ans.startsWith("$ti/NOUN<POSTP<")
         || ans.startsWith("$ők/NOUN<POSTP<"))
        {
            String post = null;

            if (ans.startsWith("$én") || ans.startsWith("$te"))
            {
                post = ans.substring(15, ans.length() - 11).toLowerCase();
            }
            else if (ans.startsWith("$ők"))
            {
                post = ans.substring(15, ans.length() - 14).toLowerCase();
            }
            else if (ans.startsWith("$ő"))
            {
                post = ans.substring(14, ans.length() - 8).toLowerCase();
            }
            else if (ans.startsWith("$mi") || ans.startsWith("$ti"))
            {
                post = ans.substring(15, ans.length() - 17).toLowerCase();
            }

            if (ans.startsWith("$ő") && !ans.startsWith("$ők"))
            {
                ans = ans.substring(2);
            }
            else
            {
                ans = ans.substring(3);
            }

            return post;
        }

        if (ans.startsWith("$ez/NOUN<POSTP<") || ans.startsWith("$az/NOUN<POSTP<"))
        {
            String affix = ans.substring(15);
            affix = affix.substring(0, affix.indexOf(">")).toLowerCase();

            // alá, alatt, alól, által, elő, előbb, ellen, elől, előtt, iránt, után (pl.: ezután)
            if (ans.contains("(i)"))
            {
                if (affix.startsWith("a")
                 || affix.startsWith("á")
                 || affix.startsWith("e")
                 || affix.startsWith("i")
                 || affix.startsWith("u"))
                    return ans.substring(1, 3) + affix + "i";

                return ans.substring(1, 2) + affix + "i";
            }

            return ans.substring(1, 3) + affix;
        }

        return ans.substring(1, ans.indexOf("/"));
    }

    public static String convertNoun(String lemma, String kr)
    {
        /* névmás minden PERS-t tartalmazó NOUN */

        if (kr.contains("PERS"))
        {
            StringBuilder msd = new StringBuilder("Pp--sn-----------");

            /* személy */

            if (kr.contains("<PERS<1>>"))   msd.setCharAt(2, '1');  // 1
            if (kr.contains("<PERS<2>>"))   msd.setCharAt(2, '2');  // 2
            if (kr.contains("<PERS>"))      msd.setCharAt(2, '3');  // 3

            /* szám */

            if (kr.contains("<PLUR>"))      msd.setCharAt(4, 'p');

            /* eset */

            // n nincs jelölve alapeset

            if (kr.contains("<CAS<ACC>>"))  msd.setCharAt(5, 'a');  // a
            if (kr.contains("<CAS<GEN>>"))  msd.setCharAt(5, 'g');  // g nincs jelölve
            if (kr.contains("<CAS<DAT>>"))  msd.setCharAt(5, 'd');  // d
            if (kr.contains("<CAS<INS>>"))  msd.setCharAt(5, 'i');  // i
            if (kr.contains("<CAS<ILL>>"))  msd.setCharAt(5, 'x');  // x
            if (kr.contains("<CAS<INE>>"))  msd.setCharAt(5, '2');  // 2
            if (kr.contains("<CAS<ELA>>"))  msd.setCharAt(5, 'e');  // e
            if (kr.contains("<CAS<ALL>>"))  msd.setCharAt(5, 't');  // t
            if (kr.contains("<CAS<ADE>>"))  msd.setCharAt(5, '3');  // 3
            if (kr.contains("<CAS<ABL>>"))  msd.setCharAt(5, 'b');  // b
            if (kr.contains("<CAS<SBL>>"))  msd.setCharAt(5, 's');  // s
            if (kr.contains("<CAS<SUE>>"))  msd.setCharAt(5, 'p');  // p
            if (kr.contains("<CAS<DEL>>"))  msd.setCharAt(5, 'h');  // h
            if (kr.contains("<CAS<TER>>"))  msd.setCharAt(5, '9');  // 9
            if (kr.contains("[MANNER]"))    msd.setCharAt(5, 'w');  // w
            if (kr.contains("<CAS<FOR>>"))  msd.setCharAt(5, 'f');  // f
            if (kr.contains("<CAS<TEM>>"))  msd.setCharAt(5, 'm');  // m
            if (kr.contains("<CAS<CAU>>"))  msd.setCharAt(5, 'c');  // c
            if (kr.contains("[COM]"))       msd.setCharAt(5, 'q');  // q
            if (kr.contains("<CAS<TRA>>"))  msd.setCharAt(5, 'y');  // y
            if (kr.contains("[PERIOD1]"))   msd.setCharAt(5, 'u');  // u

            return cleanMsd(msd.toString());
        }

        /* névmás minden POSTP-t tartalmazó NOUN */

        if (kr.contains("POSTP"))
        {
            StringBuilder msd = new StringBuilder("Pp3-sn");

            switch (lemma)
            {
                case "én": msd.setCharAt(2, '1'); break;
                case "te": msd.setCharAt(2, '2'); break;
                case "ő":  msd.setCharAt(2, '3'); break;
                case "mi": msd.setCharAt(2, '1'); msd.setCharAt(4, 'p'); break;
                case "ti": msd.setCharAt(2, '2'); msd.setCharAt(4, 'p'); break;
                case "ők": msd.setCharAt(2, '3'); msd.setCharAt(4, 'p'); break;
            }

            return cleanMsd(msd.toString());
        }

        StringBuilder msd = new StringBuilder("Nn-sn" + "------");

        /* egyes szám/többes szám NOUN<PLUR> NUON<PLUR<FAM>> */

        if (kr.contains("NOUN<PLUR"))   msd.setCharAt(3, 'p');

        /* eset */

        // n nincs jelölve alapeset

        if (kr.contains("<CAS<ACC>>"))  msd.setCharAt(4, 'a');  // a
        if (kr.contains("<CAS<GEN>>"))  msd.setCharAt(4, 'g');  // g nincs jelolve
        if (kr.contains("<CAS<DAT>>"))  msd.setCharAt(4, 'd');  // d
        if (kr.contains("<CAS<INS>>"))  msd.setCharAt(4, 'i');  // i
        if (kr.contains("<CAS<ILL>>"))  msd.setCharAt(4, 'x');  // x
        if (kr.contains("<CAS<INE>>"))  msd.setCharAt(4, '2');  // 2
        if (kr.contains("<CAS<ELA>>"))  msd.setCharAt(4, 'e');  // e
        if (kr.contains("<CAS<ALL>>"))  msd.setCharAt(4, 't');  // t
        if (kr.contains("<CAS<ADE>>"))  msd.setCharAt(4, '3');  // 3
        if (kr.contains("<CAS<ABL>>"))  msd.setCharAt(4, 'b');  // b
        if (kr.contains("<CAS<SBL>>"))  msd.setCharAt(4, 's');  // s
        if (kr.contains("<CAS<SUE>>"))  msd.setCharAt(4, 'p');  // p
        if (kr.contains("<CAS<DEL>>"))  msd.setCharAt(4, 'h');  // h
        if (kr.contains("<CAS<TER>>"))  msd.setCharAt(4, '9');  // 9
        if (kr.contains("<CAS<ESS>>"))  msd.setCharAt(4, 'w');  // w
        if (kr.contains("<CAS<FOR>>"))  msd.setCharAt(4, 'f');  // f
        if (kr.contains("<CAS<TEM>>"))  msd.setCharAt(4, 'm');  // m
        if (kr.contains("<CAS<CAU>>"))  msd.setCharAt(4, 'c');  // c
        if (kr.contains("[COM]"))       msd.setCharAt(4, 'q');  // q
        if (kr.contains("<CAS<TRA>>"))  msd.setCharAt(4, 'y');  // y
        if (kr.contains("[PERIOD1]"))   msd.setCharAt(4, 'u');  // u

        /* birtokos száma/személye */

        if (kr.contains("<POSS>"))          { msd.setCharAt(8, 's'); msd.setCharAt(9, '3'); }
        if (kr.contains("<POSS<1>>"))       { msd.setCharAt(8, 's'); msd.setCharAt(9, '1'); }
        if (kr.contains("<POSS<2>>"))       { msd.setCharAt(8, 's'); msd.setCharAt(9, '2'); }
        if (kr.contains("<POSS<1><PLUR>>")) { msd.setCharAt(8, 'p'); msd.setCharAt(9, '1'); }
        if (kr.contains("<POSS<2><PLUR>>")) { msd.setCharAt(8, 'p'); msd.setCharAt(9, '2'); }
        if (kr.contains("<POSS<PLUR>>"))    { msd.setCharAt(8, 'p'); msd.setCharAt(9, '3'); }

        /* birtok(olt) száma */

        if (kr.contains("<ANP>"))       msd.setCharAt(10, 's');
        if (kr.contains("<ANP<PLUR>>")) msd.setCharAt(10, 'p');

        return cleanMsd(msd.toString());
    }

    public static String convertAdjective(String kr)
    {
        StringBuilder msd = new StringBuilder("Afp-sn-------");

        /* típus (melléknév vagy melléknévi igenév) */

        // f (melléknév) nincs jelölve, alapeset

        if (kr.contains("[IMPERF_PART"))    msd.setCharAt(1, 'p');  // p (folyamatos melléknévi igenév)
        if (kr.contains("[PERF_PART"))      msd.setCharAt(1, 's');  // s (befejezett melleknevi igenev)
        if (kr.contains("[FUT_PART"))       msd.setCharAt(1, 'u');  // u (beallo melleknevi igenev)

        /* fok */

        // p nincs jelölve alapeset

        if (kr.contains("[COMPAR"))         msd.setCharAt(2, 'c');  // c
        if (kr.contains("[SUPERLAT"))       msd.setCharAt(2, 's');  // s
        if (kr.contains("[SUPERSUPERLAT"))  msd.setCharAt(2, 'e');  // e

        /* szám */

        // s nincs jelölve alapeset

        if (kr.contains("ADJ<PLUR>"))       msd.setCharAt(4, 'p');  // p

        /* eset */

        // n nincs jelölve alapeset

        if (kr.contains("<CAS<ACC>>"))  msd.setCharAt(5, 'a');  // a
        if (kr.contains("<CAS<GEN>>"))  msd.setCharAt(5, 'g');  // g nincs jelölve
        if (kr.contains("<CAS<DAT>>"))  msd.setCharAt(5, 'd');  // d
        if (kr.contains("<CAS<INS>>"))  msd.setCharAt(5, 'i');  // i
        if (kr.contains("<CAS<ILL>>"))  msd.setCharAt(5, 'x');  // x
        if (kr.contains("<CAS<INE>>"))  msd.setCharAt(5, '2');  // 2
        if (kr.contains("<CAS<ELA>>"))  msd.setCharAt(5, 'e');  // e
        if (kr.contains("<CAS<ALL>>"))  msd.setCharAt(5, 't');  // t
        if (kr.contains("<CAS<ADE>>"))  msd.setCharAt(5, '3');  // 3
        if (kr.contains("<CAS<ABL>>"))  msd.setCharAt(5, 'b');  // b
        if (kr.contains("<CAS<SBL>>"))  msd.setCharAt(5, 's');  // s
        if (kr.contains("<CAS<SUE>>"))  msd.setCharAt(5, 'p');  // p
        if (kr.contains("<CAS<DEL>>"))  msd.setCharAt(5, 'h');  // h
        if (kr.contains("<CAS<TER>>"))  msd.setCharAt(5, '9');  // 9
        if (kr.contains("[MANNER]"))    msd.setCharAt(5, 'w');  // w
        if (kr.contains("<CAS<FOR>>"))  msd.setCharAt(5, 'f');  // f
        if (kr.contains("<CAS<TEM>>"))  msd.setCharAt(5, 'm');  // m
        if (kr.contains("<CAS<CAU>>"))  msd.setCharAt(5, 'c');  // c
        if (kr.contains("[COM]"))       msd.setCharAt(5, 'q');  // q
        if (kr.contains("<CAS<TRA>>"))  msd.setCharAt(5, 'y');  // y
        if (kr.contains("[PERIOD1]"))   msd.setCharAt(5, 'u');  // u

        /* birtokos száma/személye */

        if (kr.contains("<POSS>"))          { msd.setCharAt(10, 's'); msd.setCharAt(11, '3'); }
        if (kr.contains("<POSS<1>>"))       { msd.setCharAt(10, 's'); msd.setCharAt(11, '1'); }
        if (kr.contains("<POSS<2>>"))       { msd.setCharAt(10, 's'); msd.setCharAt(11, '2'); }
        if (kr.contains("<POSS<1><PLUR>>")) { msd.setCharAt(10, 'p'); msd.setCharAt(11, '1'); }
        if (kr.contains("<POSS<2><PLUR>>")) { msd.setCharAt(10, 'p'); msd.setCharAt(11, '2'); }
        if (kr.contains("<POSS<PLUR>>"))    { msd.setCharAt(10, 'p'); msd.setCharAt(11, '3'); }

        /* birtok(olt) száma */

        if (kr.contains("<ANP>"))       msd.setCharAt(12, 's');
        if (kr.contains("<ANP<PLUR>>")) msd.setCharAt(12, 'p');

        return cleanMsd(msd.toString());
    }

    public static String convertVerb(String kr)
    {
        StringBuilder msd = new StringBuilder("Vmip3s---n-");

        boolean modal = kr.contains("<MODAL>"), freq = kr.contains("[FREQ]"), caus = kr.contains("[CAUS]");

        if ( modal && !freq && !caus)   msd.setCharAt(1, 'o');  // ható
        if (!modal &&  freq && !caus)   msd.setCharAt(1, 'f');  // gyakorító
        if (!modal && !freq &&  caus)   msd.setCharAt(1, 's');  // műveltető
        if ( modal &&  freq && !caus)   msd.setCharAt(1, '1');  // gyakorító + ható
        if ( modal && !freq &&  caus)   msd.setCharAt(1, '2');  // műveltető + ható
        if (!modal &&  freq &&  caus)   msd.setCharAt(1, '3');  // műveltető + ható
        if ( modal &&  freq &&  caus)   msd.setCharAt(1, '4');  // műveltető + gyakorító + ható

        if (kr.contains("<COND>"))      msd.setCharAt(2, 'c');

        if (kr.contains("<INF>"))
        {
            msd.setCharAt(2, 'n');
            msd.setCharAt(9, '-');

            if (!kr.contains("<PERS"))
            {
                msd.setCharAt(3, '-');
                msd.setCharAt(4, '-');
                msd.setCharAt(5, '-');
            }
        }

        if (kr.contains("<SUBJUNC-IMP>"))   msd.setCharAt(2, 'm');
        if (kr.contains("<PAST>"))          msd.setCharAt(3, 's');
        if (kr.contains("<PERS<1>>"))       msd.setCharAt(4, '1');
        if (kr.contains("<PERS<2>>"))       msd.setCharAt(4, '2');
        if (kr.contains("<PLUR>"))          msd.setCharAt(5, 'p');
        if (kr.contains("<DEF>"))           msd.setCharAt(9, 'y');

        if (kr.contains("<PERS<1<OBJ<2>>>>"))
        {
            msd.setCharAt(4, '1');
            msd.setCharAt(9, '2');
        }

        return cleanMsd(msd.toString());
    }

    public static String convertNumber(String kr, String ans)
    {
        StringBuilder msd = new StringBuilder("Mc-snl-------");

        // c alapeset, nincs jelölve

        if (kr.contains("[ORD"))        msd.setCharAt(1, 'o');  // o
        if (kr.contains("[FRACT"))      msd.setCharAt(1, 'f');  // f

        // l nincs a magyarban
        // d nincs KRben
        // s alapeset, nincs jelölve

        if (kr.contains("NUM<PLUR>"))   msd.setCharAt(3, 'p');  // p

        /* eset */

        // n nincs jelölve alapeset

        if (kr.contains("<CAS<ACC>>"))      msd.setCharAt(4, 'a');  // a
        if (kr.contains("<CAS<GEN>>"))      msd.setCharAt(4, 'g');  // g nincs jelölve
        if (kr.contains("<CAS<DAT>>"))      msd.setCharAt(4, 'd');  // d
        if (kr.contains("<CAS<INS>>"))      msd.setCharAt(4, 'i');  // i
        if (kr.contains("<CAS<ILL>>"))      msd.setCharAt(4, 'x');  // x
        if (kr.contains("<CAS<INE>>"))      msd.setCharAt(4, '2');  // 2
        if (kr.contains("<CAS<ELA>>"))      msd.setCharAt(4, 'e');  // e
        if (kr.contains("<CAS<ALL>>"))      msd.setCharAt(4, 't');  // t
        if (kr.contains("<CAS<ADE>>"))      msd.setCharAt(4, '3');  // 3
        if (kr.contains("<CAS<ABL>>"))      msd.setCharAt(4, 'b');  // b
        if (kr.contains("<CAS<SBL>>"))      msd.setCharAt(4, 's');  // s
        if (kr.contains("<CAS<SUE>>"))      msd.setCharAt(4, 'p');  // p
        if (kr.contains("<CAS<DEL>>"))      msd.setCharAt(4, 'h');  // h
        if (kr.contains("<CAS<TER>>"))      msd.setCharAt(4, '9');  // 9
        if (kr.contains("[MANNER]"))        msd.setCharAt(4, 'w');  // w
        if (kr.contains("<CAS<FOR>>"))      msd.setCharAt(4, 'f');  // f
        if (kr.contains("<CAS<TEM>>"))      msd.setCharAt(4, 'm');  // m
        if (kr.contains("<CAS<CAU>>"))      msd.setCharAt(4, 'c');  // c
        if (kr.contains("[COM]"))           msd.setCharAt(4, 'q');  // q
        if (kr.contains("<CAS<TRA>>"))      msd.setCharAt(4, 'y');  // y
        if (kr.contains("[PERIOD1]"))       msd.setCharAt(4, 'u');  // u
        if (kr.contains("[MULTIPL-ITER]"))  msd.setCharAt(4, '6');  // 6

        /* birtokos száma/személye */

        if (ans.contains("<POSS>"))          { msd.setCharAt(10, 's'); msd.setCharAt(11, '3'); }
        if (ans.contains("<POSS<1>>"))       { msd.setCharAt(10, 's'); msd.setCharAt(11, '1'); }
        if (ans.contains("<POSS<2>>"))       { msd.setCharAt(10, 's'); msd.setCharAt(11, '2'); }
        if (ans.contains("<POSS<1><PLUR>>")) { msd.setCharAt(10, 'p'); msd.setCharAt(11, '1'); }
        if (ans.contains("<POSS<2><PLUR>>")) { msd.setCharAt(10, 'p'); msd.setCharAt(11, '2'); }
        if (ans.contains("<POSS<PLUR>>"))    { msd.setCharAt(10, 'p'); msd.setCharAt(11, '3'); }

        /* birtok(olt) száma */

        if (ans.contains("<ANP>"))          msd.setCharAt(12, 's');
        if (ans.contains("<ANP<PLUR>>"))    msd.setCharAt(12, 'p');

        return cleanMsd(msd.toString());
    }

    public static String convertAdverb(String kr)
    {
        StringBuilder msd = new StringBuilder("Rx----");

        if (kr.contains("[COMPAR]"))        msd.setCharAt(2, 'c');  // c
        if (kr.contains("[SUPERLAT]"))      msd.setCharAt(2, 's');  // s
        if (kr.contains("[SUPERSUPERLAT]")) msd.setCharAt(2, 'e');  // e

        return cleanMsd(msd.toString());
    }

    public static Set<MorAna> getMSD(String krAns)
    {
        Set<MorAna> ans = new TreeSet<MorAna>();

        String krRoot = getRoot(krAns);
        String lemma = krRoot.substring(1, krRoot.indexOf("/"));

        // $forog(-.)/VERB[CAUS](at)/VERB[FREQ](gat)/VERB<PAST><PERS<1>>

        String stem;

        if (krAns.contains("(") && krAns.indexOf("(") < krAns.indexOf("/"))
        {
            stem = krAns.substring(1, krAns.indexOf("("));
        }
        else if (krAns.contains("+"))
        {
            stem = lemma;
        }
        else
        {
            stem = krAns.substring(1, krAns.indexOf("/"));
        }

        String krCode = krRoot.substring(krRoot.indexOf("/") + 1);

        if (krCode.startsWith("NOUN"))
        {
            String msd = convertNoun(lemma, krCode);

            // pronoun
            if (msd.startsWith("P"))
            {
                lemma = getPostPLemma(krAns);

                // dative
                if (msd.charAt(5) == 'd')
                {
                    ans.add(new MorAna(lemma, msd.replace('d', 'g')));
                }
            }

            ans.add(new MorAna(lemma, msd));

            // dative
            if (msd.charAt(4) == 'd')
            {
                ans.add(new MorAna(lemma, msd.replace('d', 'g')));
            }
        }

        if (krCode.startsWith("ADJ"))
        {
            String msd;

            // melléknévi igenév
            if (isParticiple(krAns))
            {
                msd = convertAdjective(krAns);
                ans.add(new MorAna(lemma, msd));
            }
            else
            {
                msd = convertAdjective(krCode);
                ans.add(new MorAna(lemma, msd));
            }

            // dative
            if (msd.charAt(5) == 'd')
            {
                ans.add(new MorAna(lemma, msd.replace('d', 'g')));
            }
        }

        if (krCode.startsWith("VERB"))
        {
            // határozói igenév
            if (krCode.contains("VERB[PERF_PART]") || krCode.contains("VERB[PART]"))
            {
                ans.add(new MorAna(lemma, "Rv"));
            }
            else if (krAns.contains("[FREQ]") || krAns.contains("[CAUS]") || krAns.contains("<MODAL>"))
            {
                ans.add(new MorAna(stem, convertVerb(krAns)));
            }
            else
            {
                ans.add(new MorAna(lemma, convertVerb(krCode)));
            }
        }

        if (krCode.startsWith("NUM"))
        {
            String msd = convertNumber(krCode, krAns);
            ans.add(new MorAna(lemma, msd));

            // dative
            if (msd.charAt(4) == 'd')
                ans.add(new MorAna(lemma, msd.replace('d', 'g')));
        }

        if (krCode.startsWith("ART"))     ans.add(new MorAna(lemma, "T"));  // definite/indefinte
        if (krCode.startsWith("ADV"))     ans.add(new MorAna(lemma, convertAdverb(krCode)));
        if (krCode.startsWith("POSTP"))   ans.add(new MorAna(lemma, "St"));
        if (krCode.startsWith("CONJ"))    ans.add(new MorAna(lemma, "Ccsp"));
        if (krCode.startsWith("UTT-INT")) ans.add(new MorAna(lemma, "I"));
        if (krCode.startsWith("PREV"))    ans.add(new MorAna(lemma, "Rp"));
        if (krCode.startsWith("DET"))     ans.add(new MorAna(lemma, "Pd3-sn"));
        if (krCode.startsWith("ONO"))     ans.add(new MorAna(lemma, "X"));
        if (krCode.startsWith("E"))       ans.add(new MorAna(lemma, "Rq-y"));
        if (krCode.startsWith("ABBR"))    ans.add(new MorAna(lemma, "Y"));
        if (krCode.startsWith("TYPO"))    ans.add(new MorAna(lemma, "Z"));

        if (ans.isEmpty())
            ans.add(new MorAna(lemma, "X"));

        return ans;
    }

    public static String cleanMsd(String msd)
    {
        StringBuilder sb = new StringBuilder(msd.trim());

        for (int i = sb.length() - 1; sb.charAt(i) == '-'; --i)
        {
            sb.deleteCharAt(i);
        }

        return sb.toString();
    }

    public static enum KRPOS
    {
        VERB, NOUN, ADJ, NUM, ADV, PREV, ART, POSTP, UTT_INT, DET, CONJ, ONO, PREP, X;
    }

    private static String findPattern(String text, String pattern, int group)
    {
        Matcher m = Pattern.compile(pattern).matcher(text);
        m.find();

        return m.group(group);
    }

    private static String findPattern(String text, String pattern)
    {
        return findPattern(text, pattern, 1);
    }

    private static List<String> findPatterns(String text, String pattern)
    {
        List<String> found = new LinkedList<String>();

        for (Matcher m = Pattern.compile(pattern).matcher(text); m.find(); )
        {
            found.add(m.group(1));
        }

        return found;
    }

    public static String getRoot(String morph)
    {
        // több
        if (morph.startsWith("$sok/NUM[COMPAR]/NUM<CAS<"))
            return "$több/NUM<CAS<ACC>>";

        // legtöbb
        if (morph.startsWith("$sok/NUM[SUPERLAT]/NUM<CAS<"))
            return "$legtöbb/NUM<CAS<ACC>>";

        // legeslegtöbb
        if (morph.startsWith("$sok/NUM[SUPER-SUPERLAT]/NUM<CAS<"))
            return "$legeslegtöbb/NUM<CAS<ACC>>";

        String root = null;

        if (!morph.contains("/"))
        {
            return morph;
        }
        else
        {
            String igekoto = "";

            // igekötő
            if (morph.contains("/PREV+"))
            {
                igekoto = morph.split("/PREV\\+")[0];
                morph = morph.split("/PREV\\+")[1];
            }

            String[] tovek = preProcess(morph.split("/"));

            String vegsoto = findPatterns(tovek[0], "^([^\\(\\/]*)").get(0);
            boolean ikes = false;

            if (tovek.length > 2)
            {
                for (int i = 0; i < tovek.length - 1; i++)
                {
                    if (tovek[i].matches(".*\\(.*\\).*"))
                    {
                        int backValue = 0;

                        for (String feladat : findPatterns(tovek[i], "\\((.*?)\\)"))
                        {
                            if (feladat.matches("^\\-\\d.*"))
                            {
                                // -1 -2ik
                                backValue = Integer.parseInt(findPattern(feladat, "^\\-(\\d)"));
                                vegsoto = vegsoto.substring(0, vegsoto.length() - backValue);
                                ikes = false;
                                if (feladat.matches(".*ik$"))
                                {
                                    ikes = true;
                                }
                                // feladat.matches("^\\-\\.[\\d]"
                            }
                            else if (feladat.matches("^\\-\\."))
                            {
                                // -.

                                String firsPart = findPattern(vegsoto, "^(.*?).([bcdfghjklmnpqrstvwxyz!]*)$", 1);
                                String secondPart = findPattern(vegsoto, "^(.*?).([bcdfghjklmnpqrstvwxyz!]*)$", 2);
                                vegsoto = firsPart + "!" + secondPart;
                            }
                            else if (feladat.matches("^\\.(.*)"))
                            {
                                // .a .e .i .o .� .u .�
                                String csere = findPattern(feladat, "^\\.(.*)", 1);
                                if (vegsoto.contains("!"))
                                {
                                    vegsoto = vegsoto.replace("!", csere);
                                }
                                else
                                {
                                    // TODO ez mikor van?
                                }
                                ikes = false;
                            }
                            else if (feladat.matches("^\\%.*"))
                            {
                                // TODO nem találtam ilyet
                            }
                            else if (feladat.matches("^[^\\.\\-\\%].*"))
                            {
                                // a, abb, ad, al, an, ank�nt, anta, askodik, astul, ast�l, at,
                                // az, azik, bb, beli, b�li, e, ebb, ed, eget, el, en, enk�nt,
                                // ente, eskedik, est�l, est�l, et, ett, ez, ezik, gat, get,
                                // hetn�k, i, kedik, k�pp, k�ppen, lag, leg, n, nk�nt, nta, nte,
                                // nyi, od, odik, ogat, ol, on, onk�nt, onta, oskodik, ostul,
                                // ost�l, ott, ov, oz, ozik, sodik, st�l, st�l, st�l, sul, szer,
                                // szerez, szeri, szerte, szor, szori, szoroz, szorta, sz�r,
                                // sz�ri, sz�rte, sz�r�z, s�t, s�dik, tat, tet, tt, ul, v, va,
                                // ve, v�n, v�n, z, zik, �, �, �t, �, �dik, �dik, �get, �l,
                                // �nk�nt, �sk�dik, �st�l, �st�l, �tt, �v, �z, �zik, �l, �dik
                                vegsoto = vegsoto + findPattern(feladat, "^([^\\.\\-\\%].*)", 1);
                                ikes = false;
                            }
                            else
                            {
                                // System.err.println("HIBA: " + feladat);
                            }
                        }
                    }
                }
            }

            String ikveg = ikes ? "ik" : "";
            root = igekoto + vegsoto + ikveg + "/" + tovek[tovek.length - 1];

            for (String rep : findPatterns(root, "(\\([^\\)]*\\))"))
            {
                root = root.replace(rep, "");
            }
        }

        root = root.replace("!", "");
        root = root.replace("@", "");
        root = root.replace("$", "");

        return "$" + root;
    }

    private static String[] preProcess(String[] tovek)
    {
        for (int i = 0; i < tovek.length; i++)
        {
            // gyorsan -> gyors
            // hallgatólag -> hallgató
            if (tovek[i].contains("ADJ[MANNER]"))
            {
                tovek[tovek.length - 1] = tovek[i];
            }

            // mindenképp -> minden
            // mindenképpen -> minden
            if (tovek[i].contains("NOUN[ESS_FOR]"))
            {
                tovek[tovek.length - 1] = tovek[i];
            }

            // apástul -> apa
            if (tovek[i].contains("NOUN") && tovek[i].contains("[COM]"))
            {
                tovek[tovek.length - 1] = tovek[i];
            }

            // fejenként -> fej
            if (tovek[i].contains("NOUN[PERIOD1]"))
            {
                tovek[tovek.length - 1] = tovek[i];
            }

            /*
             * számnevek, amik KRben /ADV
             */
            if (tovek[i].contains("NUM") && tovek[i].contains("["))
            {
                tovek[tovek.length - 1] = tovek[i];
            }

            // rosszabb -> rossz
            // legrosszabb -> rossz
            // legeslegrosszabb -> rossz
            // rosszabbik -> rossz
            // legrosszabbik -> rossz
            // legeslegrosszabbik -> rossz

            if (tovek[i].contains("ADJ"))
            {
                if (tovek[i].contains("[COMPAR") || tovek[i].contains("[SUPERLAT") || tovek[i].contains("[SUPERSUPERLAT"))
                {
                    tovek[tovek.length - 1] = tovek[i];
                }
            }

            // futva, futván -> fut
            if (tovek[i].contains("VERB[PART](va)")
             || tovek[i].contains("VERB[PART](ve)")
             || tovek[i].contains("VERB[PERF_PART](ván)")
             || tovek[i].contains("VERB[PERF_PART](vén)"))
            {
                tovek[tovek.length - 1] = tovek[i];
            }
        }

        return tovek;
    }

    /*
     * "$fut/VERB[GERUND](�s)/NOUN<PLUR><POSS<1>><CAS<INS>>"
     */
    public static KRPOS getPOS(String code)
    {
        int end1 = Integer.MAX_VALUE;
        int end2 = Integer.MAX_VALUE;

        int end = 0;

        if (code.contains("@"))
        {
            end = code.lastIndexOf("@");
        }

        int start = code.lastIndexOf("/");

        if (code.indexOf("<", start) > 0)
        {
            end1 = (code.indexOf("<", start));
        }

        if (code.indexOf("[", start) > 0)
        {
            end2 = (code.indexOf("[", start));
        }

        end = (end1 < end2) ? end1 : end2;

        if (end > code.length())
        {
            end = code.length();
        }

        switch (code.substring(start, end))
        {
            case "VERB":    return KRPOS.VERB;
            case "NOUN":    return KRPOS.NOUN;
        }

        switch (code.substring(start + 1, end))
        {
            case "ADJ":     return KRPOS.ADJ;
            case "NUM":     return KRPOS.NUM;
            case "ADV":     return KRPOS.ADV;
            case "PREV":    return KRPOS.PREV;
            case "ART":     return KRPOS.ART;
            case "POSTP":   return KRPOS.POSTP;
            case "UTT-INT": return KRPOS.UTT_INT;
            case "DET":     return KRPOS.DET;
            case "CONJ":    return KRPOS.CONJ;
            case "ONO":     return KRPOS.ONO;
            case "PREP":    return KRPOS.PREP;
        }

        return KRPOS.X;
    }

    public static String[] getAbsoluteLemma(String form)
    {
        List<String> lemma = new ArrayList<String>();

        for (String s : RFSA.analyse(form))
        {
            // igekötők leválasztása
            s = s.substring(s.indexOf("$") + 1);

            if (s.contains("(") && s.indexOf("(") < s.indexOf("/"))
                lemma.add(s.substring(0, s.indexOf("(")));
            else
                lemma.add(s.substring(0, s.indexOf("/")));
        }

        return lemma.toArray(new String[lemma.size()]);
    }

    public static void main(String args[])
    {
        System.out.println(RFSA.analyse("utánam"));

        System.out.println(getMSD("$én/NOUN<POSTP<UTÁN>><PERS<1>>"));

        // System.out.println(getRoot("$fut/VERB[GERUND](�s)/NOUN<PLUR><POSS<1>><CAS<INS>>"));

        System.out.println(getPOS("$árapály/NOUN"));
    }
}
