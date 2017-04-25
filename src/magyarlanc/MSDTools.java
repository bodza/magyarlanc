package magyarlanc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

public class MSDTools
{
    private static final Pattern
        rxNOUN_1 = Pattern.compile("N.-..---s3"),
        rxNOUN_2 = Pattern.compile("N.-..---..s"),

        rxADJECTIVE_1 = Pattern.compile("A..-..-.--s3"),
        rxADJECTIVE_2 = Pattern.compile("A..-..-.--..s"),

        rxNUMERAL_1 = Pattern.compile("M.-...-.--s3"),
        rxNUMERAL_2 = Pattern.compile("M.-...-.--..s"),

        rxOPEN_1 = Pattern.compile("O..-..---s3"),
        rxOPEN_2 = Pattern.compile("O..-..---..s"),

        rxVERB_1 = Pattern.compile("V[^a]cp[12]p---y"),
        rxVERB_2 = Pattern.compile("V[^a]ip1s---y"),
        rxVERB_3 = Pattern.compile("V[^a]cp3p---y"),
        rxVERB_4 = Pattern.compile("V[^a]is1[sp]---y");

    /**
     * Reduce noun.
     */
    private static String reduceN(String msd)
    {
        StringBuilder sb = new StringBuilder("N");

        // dative/genitive
        // superessive/essive
        if (msd.length() > 4 && (msd.charAt(4) == 'd' || msd.charAt(4) == 'g' || msd.charAt(4) == 'p'))
        {
            sb.append(msd.charAt(4));
        }

        // N.-..---s3
        if (rxNOUN_1.matcher(msd).find())
        {
            sb.append('s');
        }

        // N.-..---..s
        if (rxNOUN_2.matcher(msd).find())
        {
            sb.append('z');
        }

        return sb.toString();
    }

    /**
     * Reduce other
     */
    private static String reduceO(String msd)
    {
        StringBuilder sb = new StringBuilder("O");

        // dative/genitive
        // superessive/essive
        if (msd.length() > 5 && (msd.charAt(5) == 'd' || msd.charAt(5) == 'g' || msd.charAt(5) == 'p'))
        {
            sb.append(msd.charAt(5));
        }

        // O..-..---s3
        if (rxOPEN_1.matcher(msd).find())
        {
            sb.append('s');
        }

        // O..-..---..s
        if (rxOPEN_2.matcher(msd).find())
        {
            sb.append('z');
        }

        return sb.toString();
    }

    /**
     * Reduce verb
     */
    private static String reduceV(String msd)
    {
        String result = null;

        // Va
        if (msd.startsWith("Va"))
        {
            result = "Va";
        }

        // múlt idejű műveltető igealakok
        // Vsis[123][sp]---[yn]
        else if (msd.startsWith("Vsis"))
        {
            if (msd.endsWith("---y"))
            {
                // 1
                result = "Vsy";
            }

            // festettek
            if (msd.equals("Vsis3p---y"))
            {
                result = "Vs3py";
            }

            // festettem
            if (msd.equals("Vsis1s---y"))
            {
                result = "Vs1y";
            }

            else
            {
                result = "Vs";
            }
        }

        // festetek
        else if (msd.equals("Vsip1s---n"))
        {
            result = "Vs";
        }

        // olvasnánk
        // V[^a]cp1p---y
        else if (rxVERB_1.matcher(msd).find())
        {
            result = "Vcp";
        }

        // eszek eszem
        // V[^a]ip1s---y
        else if (rxVERB_2.matcher(msd).find())
        {
            result = "Vip";
        }

        // festetnék
        // V[^a]cp3p---y
        else if (rxVERB_3.matcher(msd).find())
        {
            if (msd.charAt(1) == 's')
            {
                result = "Vs3p";
            }
            else
            {
                result = "V3p";
            }
        }

        // festenék
        // V[^a]cp3p---y
        else if (msd.equals("Vmcp1s---n"))
        {
            result = "V1";
        }

        // festettem
        // V s is[123][sp]---[yn]
        // V[^a]is 1 [sp]---y
        else if (rxVERB_4.matcher(msd).find())
        {
            if (msd.charAt(1) == 's')
            {
                result = "Vs1y";
            }
            else
            {
                result = "Vy";
            }
        }

        // V-m felszólító mód
        else if (msd.length() > 2 && msd.charAt(2) == 'm')
        {
            result = "Vm";
        }

        // V--p jelen idő egybeeshet múlttal pl.: �rt
        else if (msd.length() > 3 && msd.charAt(3) == 'p')
        {
            result = "Vp";
        }

        else
        {
            result = "V";
        }

        return result;
    }

    /**
     * Reduce adjective.
     */
    private static String reduceA(String msd)
    {
        StringBuilder sb = new StringBuilder("A");

        // igenevek
        if (msd.charAt(1) != 'f')
        {
            sb.append('r');
        }

        // dative/genitive
        // superessive/essive
        if (msd.length() > 5 && (msd.charAt(5) == 'd' || msd.charAt(5) == 'g' || msd.charAt(5) == 'p'))
        {
            sb.append(msd.charAt(5));
        }

        // A..-..-.--s3
        if (rxADJECTIVE_1.matcher(msd).find())
        {
            sb.append('s');
        }

        // A..-..-.--..s
        if (rxADJECTIVE_2.matcher(msd).find())
        {
            sb.append('z');
        }

        return sb.toString();
    }

    /**
     * Reduce pronoun.
     */
    private static String reduceP(String msd)
    {
        StringBuilder sb = new StringBuilder("P");

        // Pq Pr Pp
        if (msd.length() > 1 && (msd.charAt(1) == 'q' || msd.charAt(1) == 'r' || msd.charAt(1) == 'p'))
        {
            if (msd.charAt(1) == 'p')
            {
                sb.append('e');
            }
            else
            {
                sb.append(msd.charAt(1));
            }
        }

        // dative/genitive
        // superessive/essive
        if (msd.length() > 5 && (msd.charAt(5) == 'd' || msd.charAt(5) == 'g' || msd.charAt(5) == 'p'))
        {
            sb.append(msd.charAt(5));
        }

        return sb.toString();
    }

    /**
     * Reduce adverb.
     */
    private static String reduceR(String msd)
    {
        StringBuilder sb = new StringBuilder("R");

        // Rq Rr Rp
        if (msd.length() > 1 && (msd.charAt(1) == 'q' || msd.charAt(1) == 'r' || msd.charAt(1) == 'p'))
        {
            sb.append(msd.charAt(1));
        }

        return sb.toString();
    }

    /**
     * Reduce numeral.
     */
    private static String reduceM(String msd)
    {
        StringBuilder sb = new StringBuilder("M");

        // fractal
        if (msd.length() > 1 && msd.charAt(1) == 'f')
        {
            sb.append(msd.charAt(1));
        }

        // dative/genitive
        // superessive/essive
        if (msd.length() > 4 && (msd.charAt(4) == 'd' || msd.charAt(4) == 'g' || msd.charAt(4) == 'p'))
        {
            sb.append(msd.charAt(4));
        }

        // M.-...-.--s3
        if (rxNUMERAL_1.matcher(msd).find())
        {
            sb.append('s');
        }

        // M.-...-.--..s
        if (rxNUMERAL_2.matcher(msd).find())
        {
            sb.append('z');
        }

        return sb.toString();
    }

    public static String reduceMSD(String msd)
    {
        if (msd.length() == 1)
        {
            return msd;
        }

        switch (msd.charAt(0))
        {
            case 'N': return reduceN(msd);
            case 'V': return reduceV(msd);
            case 'A': return reduceA(msd);
            case 'P': return reduceP(msd);
            case 'R': return reduceR(msd);
            case 'M': return reduceM(msd);
            case 'O': return reduceO(msd);
            case 'C': return msd;

            // T, S, I, X, Y, Z
            default:
                return String.valueOf(msd.charAt(0));
        }
    }

    /**
     * extract noun
     */
    private static String parseN(String msd)
    {
        int length = msd.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (msd.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(msd.charAt(1));
        }

        // 2 (not used)

        // 3 Num
        if (msd.charAt(3) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(msd.charAt(3));
        }

        // 4 Cas
        if (msd.charAt(4) == '-')
        {
            sb.append("|Cas=none");
        }
        else
        {
            sb.append("|Cas=").append(msd.charAt(4));
        }
        if (length == 5)
        {
            sb.append("|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 5 (not used)

        // 6 (not used)

        // 7 (not used)

        // 8 NumP
        if (msd.charAt(8) == '-')
        {
            sb.append("|NumP=none");
        }
        else
        {
            sb.append("|NumP=").append(msd.charAt(8));
        }
        if (length == 9)
        {
            sb.append("|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 9 PerP
        if (msd.charAt(9) == '-')
        {
            sb.append("|PerP=none");
        }
        else
        {
            sb.append("|PerP=").append(msd.charAt(9));
        }
        if (length == 10)
        {
            sb.append("|NumPd=none");
            return sb.toString();
        }

        // 10 NumPd
        if (msd.charAt(10) == '-')
        {
            sb.append("|NumPd=none");
        }
        else
        {
            sb.append("|NumPd=").append(msd.charAt(10));
        }

        return sb.toString();
    }

    /**
     * extract verb
     */
    private static String parseV(String msd)
    {
        int length = msd.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (msd.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(msd.charAt(1));
        }

        // 2 Mood
        if (msd.charAt(2) == '-')
        {
            sb.append("|Mood=none");
        }
        else
        {
            sb.append("|Mood=").append(msd.charAt(2));
        }
        if (length == 3)
        {
            if (msd.charAt(2) != 'n')
            {
                sb.append("|Tense=none");
            }
            sb.append("|Per=none|Num=none");
            if (msd.charAt(2) != 'n')
            {
                sb.append("|Def=none");
            }

            return sb.toString();
        }

        // 3 Tense (if Mood != n)
        if (msd.charAt(2) != 'n')
        {
            if (msd.charAt(3) == '-')
            {
                sb.append("|Tense=none");
            }
            else
            {
                sb.append("|Tense=").append(msd.charAt(3));
            }
        }
        if (length == 4)
        {
            sb.append("|Per=none|Num=none");
            if (msd.charAt(2) != 'n')
            {
                sb.append("|Def=none");
            }

            return sb.toString();
        }

        // 4 Per
        if (msd.charAt(4) == '-')
        {
            sb.append("|Per=none");
        }
        else
        {
            sb.append("|Per=").append(msd.charAt(4));
        }
        if (length == 5)
        {
            sb.append("|Num=none");
            if (msd.charAt(2) != 'n')
            {
                sb.append("|Def=none");
            }

            return sb.toString();
        }

        // 5 Num
        if (msd.charAt(5) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(msd.charAt(5));
        }
        if (length == 6)
        {
            if (msd.charAt(2) != 'n')
            {
                sb.append("|Def=none");
            }

            return sb.toString();
        }

        // 6 Def
        if (length == 7)
        {
            if (msd.charAt(2) != 'n')
            {
                sb.append("|Def=none");
            }

            return sb.toString();
        }

        // 7 (not used)

        // 8 (not used)

        // 9 Def
        if (msd.charAt(2) != 'n')
        {
            if (msd.charAt(9) == '-')
            {
                sb.append("|Def=none");
            }
            else
            {
                sb.append("|Def=").append(msd.charAt(9));
            }
        }
        if (length == 10)
        {
            return sb.toString();
        }

        // 10 (not used)
        if (length == 11)
            return sb.toString();

        return sb.toString();
    }

    /**
     * extract adjective
     */
    private static String parseA(String msd)
    {
        int length = msd.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (msd.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(msd.charAt(1));
        }

        // 2 Deg
        if (msd.charAt(2) == '-')
        {
            sb.append("|Deg=none");
        }
        else
        {
            sb.append("|Deg=").append(msd.charAt(2));
        }

        // 3 (not used)

        // 4 Num
        if (msd.charAt(4) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(msd.charAt(4));
        }

        // 5 Cas
        if (msd.charAt(5) == '-')
        {
            sb.append("|Cas=none");
        }
        else
        {
            sb.append("|Cas=").append(msd.charAt(5));
        }
        if (length == 6)
        {
            sb.append("|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 6 (not used)

        // 7 (not used)

        // 8 (not used)

        // 9 (not used)

        // 10 NumP
        if (msd.charAt(10) == '-')
        {
            sb.append("|NumP=none");
        }
        else
        {
            sb.append("|NumP=").append(msd.charAt(10));
        }
        if (length == 11)
        {
            sb.append("|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 11 PerP
        if (msd.charAt(11) == '-')
        {
            sb.append("|PerP=none");
        }
        else
        {
            sb.append("|PerP=").append(msd.charAt(11));
        }
        if (length == 12)
        {
            sb.append("|NumPd=none");
            return sb.toString();
        }

        // 12 NumPd
        if (msd.charAt(12) == '-')
        {
            sb.append("|NumPd=none");
        }
        else
        {
            sb.append("|NumPd=").append(msd.charAt(12));
        }

        return sb.toString();
    }

    /**
     * extract pronoun
     */
    private static String parseP(String msd)
    {
        int length = msd.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (msd.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(msd.charAt(1));
        }

        // 2 Per
        if (msd.charAt(2) == '-')
        {
            sb.append("|Per=none");
        }
        else
        {
            sb.append("|Per=").append(msd.charAt(2));
        }

        // 3 (not used)

        // 4 Num
        if (msd.charAt(4) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(msd.charAt(4));
        }

        // 5 Cas
        if (msd.charAt(5) == '-')
        {
            sb.append("|Cas=none");
        }
        else
        {
            sb.append("|Cas=").append(msd.charAt(5));
        }
        if (length == 6)
        {
            sb.append("|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 6 NumP
        if (msd.charAt(6) == '-')
        {
            sb.append("|NumP=none");
        }
        else
        {
            sb.append("|NumP=").append(msd.charAt(6));
        }

        if (length == 7)
        {
            sb.append("|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 7 (not used)

        // 8 (not used)

        // 9 (not used)

        // 10 (not used)

        // 11 (not used)

        // 12 (not used)

        // 13 (not used)

        // 14 (not used)

        // 15 PerP
        if (msd.charAt(15) == '-')
        {
            sb.append("|PerP=none");
        }
        else
        {
            sb.append("|PerP=").append(msd.charAt(15));
        }

        if (length == 16)
        {
            sb.append("|NumPd=none");
            return sb.toString();
        }

        // 16 NumPd
        if (msd.charAt(16) == '-')
        {
            sb.append("|NumPd=none");
        }
        else
        {
            sb.append("|NumPd=").append(msd.charAt(16));
        }

        return sb.toString();
    }

    /**
     * extract article
     */
    private static String parseT(String msd)
    {
        // 1 SubPOS
        if (msd.charAt(1) == '-')
        {
            return "SubPOS=none";
        }
        else
        {
            return "SubPOS=" + msd.charAt(1);
        }
    }

    /**
     * extract adverb
     */
    private static String parseR(String msd)
    {
        int length = msd.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (msd.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(msd.charAt(1));
        }
        if (length == 2)
        {
            sb.append("|Deg=none");
            if (msd.charAt(1) == 'l')
            {
                sb.append("|Num=none|Per=none");
            }

            return sb.toString();
        }

        // 2 Deg
        if (msd.charAt(2) == '-')
        {
            sb.append("|Deg=none");
        }
        else
        {
            sb.append("|Deg=").append(msd.charAt(2));
        }
        if (length == 3)
        {
            if (msd.charAt(1) == 'l')
            {
                sb.append("|Num=none|Per=none");
            }

            return sb.toString();
        }

        // 3 (not used)

        // 4 Num
        if (msd.charAt(1) == 'l')
        {
            if (msd.charAt(4) == '-')
            {
                sb.append("|Num=none");
            }
            else
            {
                sb.append("|Num=").append(msd.charAt(4));
            }
        }
        if (length == 5)
        {
            if (msd.charAt(1) == 'l')
            {
                sb.append("|Per=none");
            }

            return sb.toString();
        }

        // 5 Per
        if (msd.charAt(1) == 'l')
        {
            if (msd.charAt(5) == '-')
            {
                sb.append("|Per=none");
            }
            else
            {
                sb.append("|Per=").append(msd.charAt(5));
            }
        }

        return sb.toString();
    }

    /**
     * extract adposition
     */
    private static String parseS(String msd)
    {
        // 1 SubPOS
        if (msd.charAt(1) == '-')
        {
            return "SubPOS=none";
        }
        else
        {
            return "SubPOS=" + msd.charAt(1);
        }
    }

    /**
     * extract conjucion
     */
    private static String parseC(String msd)
    {
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (msd.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(msd.charAt(1));
        }

        // 2 Form
        if (msd.charAt(2) == '-')
        {
            sb.append("|Form=none");
        }
        else
        {
            sb.append("|Form=").append(msd.charAt(2));
        }

        // 3 Coord
        if (msd.charAt(3) == '-')
        {
            sb.append("|Coord=none");
        }
        else
        {
            sb.append("|Coord=").append(msd.charAt(3));
        }

        return sb.toString();
    }

    /**
     * extract numeral
     */
    private static String parseM(String msd)
    {
        int length = msd.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (msd.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(msd.charAt(1));
        }

        // 2 (not used)

        // 3 Num
        if (msd.charAt(3) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(msd.charAt(3));
        }

        // 4 Cas
        if (msd.charAt(4) == '-')
        {
            sb.append("|Cas=none");
        }
        else
        {
            sb.append("|Cas=").append(msd.charAt(4));
        }

        // 5 Form
        if (msd.charAt(5) == '-')
        {
            sb.append("|Form=none");
        }
        else
        {
            sb.append("|Form=").append(msd.charAt(5));
        }
        if (length == 6)
        {
            sb.append("|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 6 (not used)

        // 7 (not used)

        // 8 (not used)

        // 9 (not used)

        // 10 NumP
        if (msd.charAt(10) == '-')
        {
            sb.append("|NumP=none");
        }
        else
        {
            sb.append("|NumP=").append(msd.charAt(10));
        }
        if (length == 11)
        {
            sb.append("|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 11 PerP
        if (msd.charAt(11) == '-')
        {
            sb.append("|PerP=none");
        }
        else
        {
            sb.append("|PerP=").append(msd.charAt(11));
        }
        if (length == 12)
        {
            sb.append("|NumPd=none");
            return sb.toString();
        }

        // 12 NumPd
        if (msd.charAt(12) == '-')
        {
            sb.append("|NumPd=none");
        }
        else
        {
            sb.append("|NumPd=").append(msd.charAt(12));
        }

        return sb.toString();
    }

    /**
     * extract interjection
     */
    private static String parseI(String msdCode)
    {
        int length = msdCode.length();

        if (length == 1)
        {
            return "_";
        }
        // 1 SubPOS
        return "SubPOS=" + msdCode.charAt(1);
    }

    /**
     * extract other/open
     */
    private static String parseO(String msd)
    {
        int length = msd.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (msd.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(msd.charAt(1));
        }
        if (length == 2)
        {
            sb.append("|Num=none|Cas=none|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 2 Type (if SubPOS=e|d|n)
        if (msd.charAt(1) == 'e' || msd.charAt(1) == 'd' || msd.charAt(1) == 'n')
        {
            if (msd.charAt(1) == '-')
            {
                sb.append("|Type=none");
            }
            else
            {
                sb.append("|Type=").append(msd.charAt(2));
            }
        }
        if (length == 3)
        {
            sb.append("|Num=none|Cas=none|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 3 (not used)

        // 4 Num
        if (msd.charAt(4) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(msd.charAt(4));
        }
        if (length == 5)
        {
            sb.append("|Cas=none|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 5 Cas
        if (msd.charAt(5) == '-')
        {
            sb.append("|Cas=none");
        }
        else
        {
            sb.append("|Cas=").append(msd.charAt(5));
        }
        if (length == 6)
        {
            sb.append("|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 6 (not used)

        // 7 (not used)

        // 8 (not used)

        // 9 NumP
        if (msd.charAt(9) == '-')
        {
            sb.append("|NumP=none");
        }
        else
        {
            sb.append("|NumP=").append(msd.charAt(9));
        }
        if (length == 10)
        {
            sb.append("|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 10 PerP
        if (msd.charAt(10) == '-')
        {
            sb.append("|PerP=none");
        }
        else
        {
            sb.append("|PerP=").append(msd.charAt(10));
        }
        if (length == 11)
        {
            sb.append("|NumPd=none");
            return sb.toString();
        }

        // 11 NumPd
        if (msd.charAt(11) == '-')
        {
            sb.append("|NumPd=none");
        }
        else
        {
            sb.append("|NumPd=").append(msd.charAt(11));
        }

        return sb.toString();
    }

    public static String msdToConllFeatures(String lemma, String msd)
    {
        if (lemma.equals("_"))
            return "_";

        // relevant punctation
        if (Morphology.getPunctations().contains(lemma))
            return "_";

        // non relevant punctation
        if (msd.equals("K"))
        {
            return "SubPOS=" + lemma;
        }

        switch (msd.charAt(0))
        {
            case 'N': return parseN(msd);   // noun
            case 'V': return parseV(msd);   // verb
            case 'A': return parseA(msd);   // adjective
            case 'P': return parseP(msd);   // pronoun
            case 'T': return parseT(msd);   // article
            case 'R': return parseR(msd);   // adverb
            case 'S': return parseS(msd);   // adposition
            case 'C': return parseC(msd);   // conjuction
            case 'M': return parseM(msd);   // numeral
            case 'I': return parseI(msd);   // interjection
            case 'O': return parseO(msd);   // open/other

            case 'X': return "_";   // residual
            case 'Y': return "_";   // abbrevation
            case 'Z': return "_";   //

            case 'K': return "SubPOS=" + lemma; // punctation
        }

        return "_";
    }

    /**
     * Patterns for the MSD attribute positions for ex. the noun patern contains,
     * that the first character of a noun MSD code contains the SubPOS
     * featurevalue the third character contains the Num featurevalue etc. It is
     * important that the second, fifth etc. characters are empty, that means it
     * has no value, the represtation in the MSD is a - sign.
     */
    private static final Map<String, Integer>
                nounMap = patternToMap("SubPOS||Num|Cas||||NumP|PerP|NumPd"),
                verbMap = patternToMap("SubPOS|Mood|Tense|Per|Num||||Def"),
                 adjMap = patternToMap("SubPOS|Deg||Num|Cas|||||NumP|PerP|NumPd"),
             pronounMap = patternToMap("SubPOS|Per||Num|Cas|NumP|||||||||PerP|NumPd"),
             articleMap = patternToMap("SubPOS"),
              adverbMap = patternToMap("SubPOS|Deg|Clitic|Num|Per"),
          adpositionMap = patternToMap("SubPOS"),
         conjunctionMap = patternToMap("SubPOS|Form|Coord"),
             numeralMap = patternToMap("SubPOS||Num|Cas|Form|||||NumP|PerP|NumPd"),
        interjectionMap = patternToMap("SubPOS"),
               otherMap = patternToMap("SubPOS|Type||Num|Cas||||NumP|PerP|NumPd");

    /**
     * convert the pattern to map, that contains the position of the feature in
     * the MSD code for ex. the noun map will be {SubPOS=1, Num=3, Cas=4, NumP=8,
     * PerP=9, NumPd=10}
     */
    private static Map<String, Integer> patternToMap(String pattern)
    {
        Map<String, Integer> map = new TreeMap<String, Integer>();

        String[] splitted = pattern.split("\\|");

        for (int i = 0; i < splitted.length; i++)
        {
            if (0 < splitted[i].length())
                map.put(splitted[i], 1 + i);
        }

        return map;
    }

    /**
     * possible conll-2009 feature names
     */
    private static final String[] CONLL_2009_FEATURES =
    {
        "SubPOS", "Num", "Cas", "NumP", "PerP", "NumPd", "Mood", "Tense", "Per", "Def", "Deg", "Clitic", "Form", "Coord", "Type"
    };

    private static final Set<String> possibleFeatures;

    static
    {
        Set<String> features = new TreeSet<String>();

        for (String feature : CONLL_2009_FEATURES)
            features.add(feature);

        possibleFeatures = features;
    }

    /**
     * Split the String of the features via the | sing, and put the featurenames and its values to a map.
     */
    private static Map<String, String> getFeaturesMap(String features)
    {
        Map<String, String> featuresMap = new LinkedHashMap<String, String>();

        for (String feature : features.split("\\|"))
        {
            String[] pair = feature.split("=");

            if (pair.length != 2)
            {
                System.err.println("Incorrect feature: " + feature);
                return null;
            }

            if (!possibleFeatures.contains(pair[0]))
            {
                System.err.println("Incorrect featurename: " + pair[0]);
                return null;
            }

            featuresMap.put(pair[0], pair[1]);
        }

        return featuresMap;
    }

    /**
     * Convert the features to MSD code, using the MSD positions and featurevalues, that belongs to the current POS.
     */
    private static String _convert(Character pos, Map<String, Integer> positionsMap, Map<String, String> featuresMap)
    {
        StringBuilder msd = new StringBuilder(pos + "----------------");

        for (Map.Entry<String, String> entry : featuresMap.entrySet())
        {
            if (!entry.getValue().equals("none"))
                msd.setCharAt(positionsMap.get(entry.getKey()), entry.getValue().charAt(0));
        }

        /**
         * főnévi igenvek ha csak simán 'nézni' van, akkor nem kell, de ha néznie, akkor igen
         */

        if (pos == 'V' && msd.charAt(3) == '-')
        {
            msd.setCharAt(3, 'p');
            String cleaned = KRTools.cleanMsd(msd.toString());

            if (cleaned.length() == 4)
                return cleaned.substring(0, 3);
        }

        return KRTools.cleanMsd(msd.toString());
    }

    public static String conllFeaturesToMsd(String pos, String features)
    {
        if (pos.length() > 1)
        {
            return "_";
        }

        return conllFeaturesToMsd(pos.charAt(0), features);
    }

    /**
     * convert the POS character and feature String to MSD code for ex. the POS
     * character can be 'N' and the feature String that belongs to the POS
     * character can be "SubPOS=c|Num=s|Cas=n|NumP=none|PerP=none|NumPd=none"
     */
    public static String conllFeaturesToMsd(char pos, String features)
    {
        /**
         * The relevant punctations has no features, its featurestring contain only
         * a _ character. The MSD code of a relevant punctations is the punctation
         * itself.
         */

        if (features == null || features.length() == 0)
        {
            System.err.println("Unable to convert empty features: " + pos);
            return null;
        }

        /**
         * X, Y, Z has no features relevant punctation has no features it is it's
         * possible that I has no featues
         */
        if (features.equals("_"))
        {
            return String.valueOf(pos);
        }

        Map<String, String> featuresMap = getFeaturesMap(features);

        switch (pos)
        {
            case 'N': return _convert(pos, nounMap, featuresMap);
            case 'V': return _convert(pos, verbMap, featuresMap);
            case 'A': return _convert(pos, adjMap, featuresMap);
            case 'P': return _convert(pos, pronounMap, featuresMap);
            case 'T': return _convert(pos, articleMap, featuresMap);
            case 'R': return _convert(pos, adverbMap, featuresMap);
            case 'S': return _convert(pos, adpositionMap, featuresMap);
            case 'C': return _convert(pos, conjunctionMap, featuresMap);
            case 'M': return _convert(pos, numeralMap, featuresMap);
            case 'I': return _convert(pos, interjectionMap, featuresMap);
            case 'O': return _convert(pos, otherMap, featuresMap);
            case 'X': return "X";
            case 'Y': return "Y";
            case 'Z': return "Z";
            case 'K': return "K";
            default:
                System.err.println("Incorrect POS: " + pos);
                break;
        }

        return null;
    }

    public static void main(String[] args)
    {
        System.out.println(conllFeaturesToMsd("O", "SubPOS=e|Type=w|Num=s|Cas=n|NumP=none|PerP=none|NumPd=none"));
    }
}
