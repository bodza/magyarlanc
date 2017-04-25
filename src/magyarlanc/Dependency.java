package magyarlanc;

import java.util.ArrayList;
import java.util.List;

import is2.data.SentenceData09;
import is2.parser.Options;
import is2.parser.Parser;

public class Dependency
{
    private static Parser parser;

    private static Parser getParser()
    {
        if (parser == null)
        {
            parser = new Parser(new Options(new String[] { "-model", "./data/" + "szeged.dep.model", "-cores", "1" }));
        }

        return parser;
    }

    /**
     * Dependency parsing of a sentence, using the forms and morphological analysis.
     *
     * @param form
     *          array of the forms of the sentence
     * @param morph
     *          two dimensional array of the morphological analysis of the forms
     *          each row contains two elements, the first is the lemma, the second
     *          is the full POS (MSD) code e.g.:[alma][Nn-sn]
     * @return two dimensional array, which contains the dependency parsed values,
     *         all of the rows contain two elements, the first element is the
     *         parent, the second is the relation type, e.g.: [8][ATT]
     */
    public static String[][] parseSentence(String[][] morph)
    {
        String[] form = new String[morph.length],
                lemma = new String[morph.length],
                  MSD = new String[morph.length],
                  POS = new String[morph.length],
              feature = new String[morph.length];

        for (int i = 0; i < morph.length; ++i)
        {
            form[i] = morph[i][0];
            lemma[i] = morph[i][1];
            MSD[i] = morph[i][2];

            POS[i] = String.valueOf(MSD[i].charAt(0));
            feature[i] = MSDTools.msdToConllFeatures(lemma[i], MSD[i]);
        }

        return parseSentence(form, lemma, MSD, POS, feature);
    }

    /**
     * Dependency parsing of a sentence, using the forms, the lemmas and the MSD
     * codes.
     *
     * @param wordForm
     *          array of the forms of the sentence
     * @param lemma
     *          array of the lemmas of the sentence
     * @param msd
     *          array of the MSD codes of the sentence
     * @return two dimensional array, which contains the dependency parsed values,
     *         all of the rows contain two elements, the first element is the
     *         parent, the second is the relation type, e.g.: [8][ATT]
     */
    public static String[][] parseSentence(String[] wordForm, String[] lemma, String[] msd)
    {
        String[] POS = new String[msd.length];
        String[] feature = new String[msd.length];

        for (int i = 0; i < msd.length; ++i)
        {
            POS[i] = String.valueOf(msd[i].charAt(0));
            feature[i] = MSDTools.msdToConllFeatures(lemma[i], msd[i]);
        }

        return parseSentence(wordForm, lemma, msd, POS, feature);
    }

    public static String[][] parseSentence(List<String> form, List<String> lemma, List<String> msd, List<String> pos, List<String> feature)
    {
        return parseSentence(form.toArray(new String[form.size()]),
                             lemma.toArray(new String[lemma.size()]),
                             msd.toArray(new String[msd.size()]),
                             pos.toArray(new String[pos.size()]),
                             feature.toArray(new String[feature.size()]));
    }

    /**
     * Dependency parsing of a sentence, using the forms, the lemmas, the POS
     * (first character of the MSD code) and the CoNLL2009 formatted features.
     */
    public static String[][] parseSentence(String[] form, String[] lemma, String[] msd, String[] pos, String[] feature)
    {
        SentenceData09 data = new SentenceData09();

        String[] s = new String[form.length + 1];
        String[] l = new String[lemma.length + 1];
        String[] p = new String[pos.length + 1];
        String[] f = new String[feature.length + 1];

        s[0] = "<root>";       for (int i = 0; i < form.length; i++)    s[1 + i] = form[i];
        l[0] = "<root-LEMMA>"; for (int i = 0; i < lemma.length; i++)   l[1 + i] = lemma[i];
        p[0] = "<root-POS>";   for (int i = 0; i < pos.length; i++)     p[1 + i] = pos[i];
        f[0] = "<no-type>";    for (int i = 0; i < feature.length; i++) f[1 + i] = feature[i];

        data.init(s);
        data.setLemmas(l);
        data.setPPos(p);
        data.setFeats(f);

        if (data.length() < 2)
        {
            return null;
        }

        data = getParser().apply(data);

        String[][] result = new String[data.length()][8];

        for (int i = 0; i < data.length(); ++i)
        {
            result[i][0] = String.valueOf(i + 1);
            result[i][1] = form[i];
            result[i][2] = lemma[i];
            result[i][3] = msd[i];
            result[i][4] = pos[i];
            result[i][5] = feature[i];
            result[i][6] = String.valueOf(data.pheads[i]);
            result[i][7] = data.plabels[i];
        }

        return result;
    }

    public static String[][][] depParse(String text)
    {
        List<String[][]> dep = new ArrayList<String[][]>();

        for (String[] sentence : HunSplitter.splitToArray(text))
        {
            dep.add(depParseSentence(sentence));
        }

        return dep.toArray(new String[dep.size()][][]);
    }

    /**
     * Line by line.
     */
    public static String[][][] depParse(List<String> lines)
    {
        List<String[][]> dep = new ArrayList<String[][]>();

        for (String line : lines)
        {
            for (String[] sentence : HunSplitter.splitToArray(line))
            {
                dep.add(depParseSentence(sentence));
            }
        }

        return dep.toArray(new String[dep.size()][][]);
    }

    public static String[][] depParseSentence(String sentence)
    {
        return depParseSentence(HunSplitter.tokenizeToArray(sentence));
    }

    public static String[][] depParseSentence(String[] form)
    {
        return parseSentence(Morphology.morphParseSentence(form));
    }
}
