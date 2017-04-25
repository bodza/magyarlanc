mkdir -p data && cat > data/Data.java <<'EOF'
package data;

public class Data
{
    private Data()
    {
    }
}
EOF
mkdir -p edu/stanford/nlp/tagger/maxent && cat > edu/stanford/nlp/tagger/maxent/SzteMaxentTagger.java <<'EOF'
package edu.stanford.nlp.tagger.maxent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;

import magyarlanc.Magyarlanc;

public class SzteMaxentTagger extends MaxentTagger
{
    public SzteMaxentTagger(String modelFile)
        throws IOException, ClassNotFoundException
    {
        this(modelFile, new TaggerConfig("-model", modelFile, "-verbose", "false"));
    }

    public SzteMaxentTagger(String modelFile, TaggerConfig config)
        throws IOException, ClassNotFoundException
    {
        super(modelFile, config, false);
    }

    public ArrayList<TaggedWord> apply(List<? extends HasWord> in)
    {
        TestSentence testSentence = new SzteTestSentence(this);
        return testSentence.tagSentence(in, false);
    }

    public boolean isUnknown(String word)
    {
        return dict.isUnknown(word);
    }

    public void setVerbose(boolean verbose)
    {
        super.VERBOSE = verbose;
        super.config.setProperty("verbose", verbose ? "true" : "false");
    }

    public String[][] morpSentence(String[] forms)
    {
        String[][] morph = new String[forms.length][3];

        List<TaggedWord> sentence = new ArrayList<TaggedWord>();

        for (String form : forms)
        {
            TaggedWord taggedWord = new TaggedWord();
            taggedWord.setWord(form);
            sentence.add(taggedWord);
        }

        sentence = this.apply(sentence);

        sentence = Magyarlanc.recoverTags(sentence);

        for (int i = 0; i < sentence.size(); ++i)
        {
            morph[i][0] = forms[i];
            morph[i][1] = sentence.get(i).value();
            morph[i][2] = sentence.get(i).tag();
        }

        return morph;
    }
}
EOF
mkdir -p edu/stanford/nlp/tagger/maxent && cat > edu/stanford/nlp/tagger/maxent/SzteTestSentence.java <<'EOF'
package edu.stanford.nlp.tagger.maxent;

import magyarlanc.Magyarlanc;

public class SzteTestSentence extends TestSentence
{
    public SzteTestSentence(MaxentTagger maxentTagger)
    {
        super(maxentTagger);
    }

    protected String[] stringTagsAt(int pos)
    {
        String[] tags = null;

        if ((pos < this.leftWindow()) || (pos >= size + this.leftWindow()))
        {
            tags = new String[1];
            tags[0] = naTag;
            return tags;
        }

        String word = sent.get(pos - this.leftWindow());

        if (this.maxentTagger.dict.isUnknown(word))
        {
            tags = Magyarlanc.getPossibleTags(word, maxentTagger.tags.getOpenTags());
        }
        else
        {
            tags = this.maxentTagger.dict.getTags(word);
        }

        tags = this.maxentTagger.tags.deterministicallyExpandTags(tags);
        return tags;
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/MateParser.java <<'EOF'
package magyarlanc;

import java.util.List;

import is2.data.SentenceData09;

public class MateParser
{
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
            feature[i] = ResourceHolder.getMSDToCoNLLFeatures().convert(lemma[i], MSD[i]);
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
            feature[i] = ResourceHolder.getMSDToCoNLLFeatures().convert(lemma[i], msd[i]);
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
     * (first character of the MSD code) and the CoNLL2009 formated featurtes
     *
     * @param form
     * @param lemma
     * @param pos
     * @param feature
     * @return
     */
    public static String[][] parseSentence(String[] form, String[] lemma, String[] msd, String[] pos, String[] feature)
    {
        SentenceData09 sentenceData09 = new SentenceData09();

        String[] s = new String[form.length + 1];
        String[] l = new String[lemma.length + 1];
        String[] p = new String[pos.length + 1];
        String[] f = new String[feature.length + 1];

        s[0] = "<root>";
        l[0] = "<root-LEMMA>";
        p[0] = "<root-POS>";
        f[0] = "<no-type>";

        for (int i = 0; i < form.length; ++i)
        {
            s[i + 1] = form[i];
        }

        for (int i = 0; i < lemma.length; ++i)
        {
            l[i + 1] = lemma[i];
        }

        for (int i = 0; i < pos.length; ++i)
        {
            p[i + 1] = pos[i];
        }

        for (int i = 0; i < feature.length; ++i)
        {
            f[i + 1] = feature[i];
        }

        sentenceData09.init(s);
        sentenceData09.setLemmas(l);
        sentenceData09.setPPos(p);
        sentenceData09.setFeats(f);

        if (sentenceData09.length() < 2)
        {
            return null;
        }

        sentenceData09 = ResourceHolder.getParser().apply(sentenceData09);

        String[][] result = new String[sentenceData09.length()][8];

        for (int i = 0; i < sentenceData09.length(); ++i)
        {
            result[i][0] = String.valueOf(i + 1);
            result[i][1] = form[i];
            result[i][2] = lemma[i];
            result[i][3] = msd[i];
            result[i][4] = pos[i];
            result[i][5] = feature[i];
            result[i][6] = String.valueOf(sentenceData09.pheads[i]);
            result[i][7] = sentenceData09.plabels[i];
        }

        return result;
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/WhatsWrong.java <<'EOF'
package magyarlanc;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.imageio.ImageIO;

import com.googlecode.whatswrong.NLPInstance;
import com.googlecode.whatswrong.SingleSentenceRenderer;
import com.googlecode.whatswrong.io.CoNLL2009;

public class WhatsWrong
{
    private static SingleSentenceRenderer renderer = null;
    private static CoNLL2009 coNLL2009 = null;

    /**
     * converts the magyarlanc output to CoNLL2009 format
     *
     * @param array
     *          magyarlanc output; two dimensional array, which contains the
     *          tokens of the a parsed sentence
     * @return
     */
    private static List<List<String>> arrayToList(String[][] array)
    {
        List<List<String>> list = new ArrayList<List<String>>();

        for (String[] a : array)
        {
            String[] s = new String[14];

            s[0] = a[0]; // id
            s[1] = a[1]; // form
            s[2] = a[2]; // lemma
            s[4] = "_"; // plemma
            s[3] = a[4]; // POS
            s[5] = "_"; // pPOS
         // s[6] = a[5]; // feat
            s[6] = "_"; // feat
            s[7] = "_"; // pfeat
            s[8] = a[6]; // head
            s[9] = "_"; // phead
            s[10] = a[7]; // rel
            s[11] = "_"; // prel
            s[12] = "_";
            s[13] = "_";

            list.add(Arrays.asList(s));
        }

        return list;
    }

    /**
     * Builds a buffered image from the given NLPInstance via the SentenceRenderer
     *
     * @param instance
     * @return
     */
    private static BufferedImage getImage(NLPInstance instance)
    {
        if (renderer == null)
            renderer = new SingleSentenceRenderer();

        BufferedImage bufferedImage = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D graphics = bufferedImage.createGraphics();
        Dimension dimension = renderer.render(instance, graphics);

        bufferedImage = new BufferedImage((int) dimension.getWidth() + 5, (int) dimension.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        graphics = bufferedImage.createGraphics();
        renderer.render(instance, graphics);

        return bufferedImage;
    }

    /**
     * Exports the given sentence to the specified PNG imgage.
     *
     * @param sentence
     *          dep. parsed sentence (magyarlanc output)
     * @param out
     *          the PNG
     */
    public static void exportToPNG(String[][] sentence, String out)
    {
        if (coNLL2009 == null)
            coNLL2009 = new CoNLL2009();

        try
        {
            FileOutputStream fos = new FileOutputStream(out);
            ImageIO.write(getImage(coNLL2009.create(arrayToList(sentence))), "PNG", fos);
            fos.flush();
            fos.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Exports the given sentence to a ByteArray.
     *
     * @param sentence
     *          dep. parsed sentence (magyarlanc output)
     */
    public static byte[] exportToByteArray(String[][] sentence)
    {
        if (coNLL2009 == null)
            coNLL2009 = new CoNLL2009();

        ByteArrayOutputStream baos = null;

        try
        {
            baos = new ByteArrayOutputStream();
            ImageIO.write(getImage(coNLL2009.create(arrayToList(sentence))), "PNG", baos);
            baos.flush();
            baos.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return baos.toByteArray();
    }

    public static void main(String[] args)
    {
        String[][] depParsedSentence = new String[4][];

        depParsedSentence[0] = new String[] { "1", "A", "a", "Rx", "R", "SubPOS=x|Deg=none", "2", "DET" };
        depParsedSentence[1] = new String[] { "2", "ház", "ház", "Rx", "R", "SubPOS=x|Deg=none", "3", "ROOT-VAN-SUBJ" };
        depParsedSentence[2] = new String[] { "3", "nagy", "nagy", "Afp-sn", "A", "SubPOS=f|Deg=p|Num=s|Cas=n|NumP=none|PerP=none|NumPd=none", "0", "ROOT-VAN-PRED" };
        depParsedSentence[3] = new String[] { "4", ".", ".", ".", ".", "_", "0", "PUNCT" };

        WhatsWrong.exportToPNG(depParsedSentence, "d:/feladat1.png");
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/GUI.java <<'EOF'
package magyarlanc;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

public class GUI
{
    private static Dimension dimension = null;
    private static JFrame frame = null;
    private static JTextField inputField = null;
    private static JButton sendButton = null;
    private static JTextArea textarea = null;
    private static JLabel imageLabel = null;

    private final static String BUTTON_TEXT = "OK";

    private static String[] sentence = null;
    private static String[][] depParsed = null;

    private static void _moveToCenter(Component component)
    {
        component.setLocation(
            (int) ((Toolkit.getDefaultToolkit().getScreenSize().getWidth() - component.getPreferredSize().getWidth()) / 2),
            (int) ((Toolkit.getDefaultToolkit().getScreenSize().getHeight() - component.getPreferredSize().getHeight()) / 2));
    }

    public static void init()
    {
        Magyarlanc.init();

        frame = new JFrame("magyarlanc 2.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.X_AXIS));
        inputPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        inputField = new JTextField(
                "Nehéz lesz megszokni a sok üres épületet, de a kínai áruházak hamar pezsgővé változtathatják a szellemházakat.");

        sendButton = new JButton(BUTTON_TEXT);
        sendButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent actionEvent)
            {
                if (inputField.getText() != null && !inputField.getText().equals(""))
                {
                    sentence = ResourceHolder.getHunSplitter().splitToArray(inputField.getText())[0];

                    depParsed = Magyarlanc.depParseSentence(sentence);

                    // image
                    BufferedImage bufferedImage = null;
                    try
                    {
                        bufferedImage = ImageIO.read(new ByteArrayInputStream(WhatsWrong.exportToByteArray(depParsed)));
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }

                    if (imageLabel != null)
                        imageLabel.setVisible(false);

                    imageLabel = new JLabel(new ImageIcon(bufferedImage));
                    frame.getContentPane().add(imageLabel, "Center");

                    // textarea
                    if (textarea != null)
                        textarea.setVisible(false);

                    textarea = new JTextArea();
                    textarea.setText(Magyarlanc.sentenceAsString(depParsed));
                    textarea.setMargin(new Insets(10, 10, 10, 10));
                    frame.getContentPane().add(textarea, "South");

                    _moveToCenter(frame);
                    frame.pack();
                    frame.setVisible(true);
                }
            }
        });

        inputPanel.add(inputField);
        inputPanel.add(sendButton);

        frame.getContentPane().add(inputPanel, "North");
        dimension = Toolkit.getDefaultToolkit().getScreenSize();

        frame.setPreferredSize(new Dimension((int) dimension.getWidth() - 150, (int) dimension.getHeight() - 150));
        frame.setResizable(false);

        _moveToCenter(frame);

        frame.pack();
        frame.setVisible(true);
    }

    public static void main(String[] args)
    {
        init();
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/Eval.java <<'EOF'
package magyarlanc;

import java.util.Map;
import java.util.TreeMap;

public class Eval
{
    private static Map<String, Integer[]> measure = null;

    static
    {
        measure = new TreeMap<String, Integer[]>();
    }

    private static boolean equals(String etalonLemma, String etalaonPos, String predicatedLemma, String predicatedPos)
    {
        if (etalonLemma.equalsIgnoreCase(predicatedLemma) && etalaonPos.equals(predicatedPos))
        {
            return true;
        }

        return false;
    }

    private static void evalToken(String etalonLemma, String etalaonPos, String predicatedLemma, String predicatedPos)
    {
        if (!measure.containsKey(etalaonPos))
        {
            measure.put(etalaonPos, new Integer[] { 0, 0 });
        }

        // equals
        if (equals(etalonLemma, etalaonPos, predicatedLemma, predicatedPos))
        {
            measure.get(etalaonPos)[0]++;
        }
        else
        {
            measure.get(etalaonPos)[1]++;
        }
    }

    private static void evalSentence(String[] etalonLemma, String[] etalaonPos, String[][] predicated)
    {
        for (int i = 0; i < etalonLemma.length; ++i)
        {
            evalToken(etalonLemma[i], etalaonPos[i], predicated[i][1], predicated[i][2]);
        }
    }

    public static void addSentence(String[] etalonLemma, String[] etalaonPos, String[][] predicated)
    {
        evalSentence(etalonLemma, etalaonPos, predicated);
    }

    public static void getStat()
    {
        int correct = 0;
        int error = 0;

        for (Map.Entry<String, Integer[]> entry : measure.entrySet())
        {
            if (Character.isLetter(entry.getKey().charAt(0)))
            {
                System.out.println(entry.getKey() + "\t" + entry.getValue()[0] + "/"
                        + entry.getValue()[1] + "/"
                        + (entry.getValue()[0] + entry.getValue()[1]) + "\t"
                        + (double) entry.getValue()[0]
                        / (entry.getValue()[0] + entry.getValue()[1]));

                correct += entry.getValue()[0];
                error += entry.getValue()[1];
            }
        }
        System.out.println(correct + "/" + error + "/" + (correct + error) + "\t"
                + correct / (double) (correct + error));
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/HunLemMor.java <<'EOF'
package magyarlanc;

import java.util.Set;
import java.util.TreeSet;

public class HunLemMor
{
    /**
     * addott szo lehetseges morfologiai elemzeseinek megahatarozasa
     */

    static boolean standardized = false;

    public static Set<MorAna> getMorphologicalAnalyses(String word)
    {
        Set<MorAna> morAnas = new TreeSet<MorAna>();

        // irasjelek
        if (Util.isPunctation(word))
        {
            // a legfontosabb irasjelek lemmaja maga az irasjel, POS kodja szinten
            // maga az irasjel lesz
            // . , ; : ! ? - -
            if (ResourceHolder.getPunctations().contains(word))
            {
                morAnas.add(new MorAna(word, word));
            }

            // § lemmaja maga az irasjel, POS kodja 'Nn-sn' lesz
            else if (word.equals("§"))
            {
                morAnas.add(new MorAna(word, Magyarlanc.DEFAULT_NOUN));
            }

            // egyeb irasjelek lemmaja maga az irasjel, POS kodja 'K' lesz
            else
            {
                morAnas.add(new MorAna(word, "K"));
            }

            return morAnas;
        }

        // ha benne van a corpus.lex-ben
        if (ResourceHolder.getCorpus().containsKey(word))
        {
            return ResourceHolder.getCorpus().get(word);
        }

        // ha benne van a corpus.lex-ben kisbetuvel
        if (ResourceHolder.getCorpus().containsKey(word.toLowerCase()))
        {
            return ResourceHolder.getCorpus().get(word.toLowerCase());
        }

        // szam
        morAnas = Guesser.numberGuess(word);

        if (morAnas.size() > 0)
        {
            return morAnas;
        }

        // romai szam
        morAnas.addAll(Guesser.guessRomanNumber(word));

        // rfsa
        for (String kr : ResourceHolder.getRFSA().analyse(word))
        {
            // System.err.println(kr);
            morAnas.addAll(ResourceHolder.getKRToMSD().getMSD(kr));
        }

        // (kotojeles)osszetett szo
        if (morAnas.size() == 0)
        {
            // kotojeles
            if (word.contains("-") && word.indexOf("-") > 1)
            {
                for (String morphCode : CompoundWord.analyseHyphenicCompoundWord(word))
                {
                    morAnas.addAll(ResourceHolder.getKRToMSD().getMSD(morphCode));
                }
            }
            else
            {
                // osszetett szo
                for (String morphCode : CompoundWord.analyseCompoundWord(word.toLowerCase()))
                {
                    morAnas.addAll(ResourceHolder.getKRToMSD().getMSD(morphCode));
                }
            }
        }

        // guess (Bush-nak, Bush-kormanyhoz)
        if (morAnas.size() == 0)
        {
            int index = word.lastIndexOf("-") > 1 ? word.lastIndexOf("-") : 0;

            if (index > 0)
            {
                String root = word.substring(0, index);
                String suffix = word.substring(index + 1);

                morAnas.addAll(Guesser.hyphenicGuess(root, suffix));
            }
        }

        // nepes szavak
        if (morAnas.size() == 0)
        {
            if (ResourceHolder.getCorrDic().containsKey(word)
                    && !word.equals(ResourceHolder.getCorrDic().get(word)))
            {
                morAnas.addAll(getMorphologicalAnalyses(ResourceHolder.getCorrDic().get(word)));
            }

            else if (ResourceHolder.getCorrDic().containsKey(word.toLowerCase())
                    && !word.equals(ResourceHolder.getCorrDic().get(word.toLowerCase())))
            {
                morAnas.addAll(getMorphologicalAnalyses(ResourceHolder.getCorrDic().get(word.toLowerCase())));
            }
        }

        return morAnas;
    }

    public static void main(String[] args)
    {
        // System.err.println(HunLemMor.getMorphologicalAnalyses("lehet"));
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/Magyarlanc.java <<'EOF'
package magyarlanc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.TaggedWord;

public class Magyarlanc
{
    public static final String DEFAULT_NOUN = "Nn-sn";

    public static void init()
    {
        ResourceHolder.initTokenizer();
        ResourceHolder.initHunSplitter();
        ResourceHolder.initCorpus();
        ResourceHolder.initFrequencies();
        ResourceHolder.initMSDReducer();
        ResourceHolder.initPunctations();
        ResourceHolder.initRFSA();
        ResourceHolder.initKRToMSD();
        ResourceHolder.initMSDToCoNLLFeatures();
        ResourceHolder.initCorrDic();
        ResourceHolder.initMorPhonDir();
        ResourceHolder.initMaxentTagger();
        ResourceHolder.initParser();
    }

    public static String[][] morphParseSentence(String[] form)
    {
        return ResourceHolder.getMaxentTagger().morpSentence(form);
    }

    public static String[][] morphParseSentence(List<String> form)
    {
        return ResourceHolder.getMaxentTagger().morpSentence(form.toArray(new String[form.size()]));
    }

    public static String[][] morphParseSentence(String sentence)
    {
        return morphParseSentence(ResourceHolder.getHunSplitter().tokenize(sentence));
    }

    public static String[][][] morphParse(String text)
    {
        List<String[][]> morph = new ArrayList<String[][]>();

        for (String[] sentence : ResourceHolder.getHunSplitter().splitToArray(text))
        {
            morph.add(morphParseSentence(sentence));
        }

        return morph.toArray(new String[morph.size()][][]);
    }

    /**
     * Line by line.
     *
     * @param lines
     * @return
     */
    public static String[][][] morphParse(List<String> lines)
    {
        List<String[][]> morph = new ArrayList<String[][]>();

        for (String line : lines)
        {
            for (String[] sentence : ResourceHolder.getHunSplitter().splitToArray(line))
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

    public static String[][][] depParse(String text)
    {
        List<String[][]> dep = new ArrayList<String[][]>();

        for (String[] sentence : ResourceHolder.getHunSplitter().splitToArray(text))
        {
            dep.add(depParseSentence(sentence));
        }

        return dep.toArray(new String[dep.size()][][]);
    }

    /**
     * Line by line.
     *
     * @param lines
     * @return
     */
    public static String[][][] depParse(List<String> lines)
    {
        List<String[][]> dep = new ArrayList<String[][]>();

        for (String line : lines)
        {
            for (String[] sentence : ResourceHolder.getHunSplitter().splitToArray(line))
            {
                dep.add(depParseSentence(sentence));
            }
        }

        return dep.toArray(new String[dep.size()][][]);
    }

    public static String[][] depParseSentence(String sentence)
    {
        return depParseSentence(ResourceHolder.getHunSplitter().tokenizeToArray(sentence));
    }

    public static String[][] depParseSentence(String[] form)
    {
        return MateParser.parseSentence(Magyarlanc.morphParseSentence(form));
    }

    public static List<String> read(String file)
    {
        List<String> lines = new LinkedList<>();

        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.trim().length() > 0)
                {
                    lines.add(line.trim());
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                reader.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        return lines;
    }

    public static void print(String[][][] array)
    {
        for (int i = 0; i < array.length; ++i)
        {
            for (int j = 0; j < array[i].length; ++j)
            {
                for (int k = 0; k < array[i][j].length; ++k)
                {
                    System.out.print(array[i][j][k] + "\t");
                }
                System.out.println();
            }
            System.out.println();
        }
    }

    public static void write(String[][][] array, String out)
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (int i = 0; i < array.length; ++i)
            {
                for (int j = 0; j < array[i].length; ++j)
                {
                    for (int k = 0; k < array[i][j].length; ++k)
                    {
                        writer.write(array[i][j][k]);
                        writer.write('\t');
                    }
                    writer.write('\n');
                }
                writer.write('\n');
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void printSentence(String[][] array)
    {
        for (int i = 0; i < array.length; ++i)
        {
            for (int j = 0; j < array[i].length; ++j)
            {
                System.out.print(array[i][j] + "\t");
            }
            System.out.println();
        }
    }

    public static String sentenceAsString(String[][] array)
    {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < array.length; ++i)
        {
            for (int j = 0; j < array[i].length; ++j)
            {
                sb.append(array[i][j]).append('\t');
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    public static void eval(String testFile)
    {
        eval(testFile, null);
    }

    public static void eval(String testFile, String output)
    {
        Writer writer = null;

        if (output != null)
        {
            try
            {
                writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), "UTF-8"));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        BufferedReader reader = null;

        List<String> wordForms = new ArrayList<String>();
        List<String> lemmas = new ArrayList<String>();
        List<String> msds = new ArrayList<String>();

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(testFile), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.equals(""))
                {
                    String[][] predicated = morphParseSentence(wordForms);

                    Eval.addSentence(lemmas.toArray(new String[lemmas.size()]), msds.toArray(new String[msds.size()]), predicated);

                    if (output != null)
                    {
                        try
                        {
                            for (int i = 0; i < predicated.length; ++i)
                            {
                                writer.write(predicated[i][0]);
                                writer.write('\t');
                                writer.write(lemmas.get(i));
                                writer.write('\t');
                                writer.write(msds.get(i));
                                writer.write('\t');
                                writer.write(predicated[i][1]);
                                writer.write('\t');
                                writer.write(predicated[i][2]);
                                writer.write('\n');
                            }
                            writer.write('\n');
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }

                    wordForms.clear();
                    lemmas.clear();
                    msds.clear();
                }
                else
                {
                    String[] split = line.split("\t");

                    wordForms.add(split[0]);
                    lemmas.add(split[1]);
                    msds.add(split[2]);
                }
            }

            Eval.getStat();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                reader.close();

                if (output != null)
                {
                    writer.close();
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args)
    {
        final String usage = "usage: -mode gui|morana|morphparse|depparse|eval";

        if (args.length < 2)
        {
            System.out.println(usage);
            System.exit(0);
        }

        Map<String, String> params = new HashMap<String, String>();

        for (int i = 0; i < args.length; i++)
        {
            try
            {
                params.put(args[i], args[i + 1]);
                i++;
            }
            catch (Exception e)
            {
                System.out.println(usage);
                System.exit(0);
            }
        }

        if (params.containsKey("-mode"))
        {
            String mode = params.get("-mode");

            switch (mode)
            {
                case "gui":
                    GUI.init();
                    break;

                case "eval":
                    if (params.containsKey("-testfile"))
                    {
                        if (params.containsKey("-output"))
                        {
                            eval(params.get("-testfile"), params.get("-output"));
                        }
                        else
                        {
                            eval(params.get("-testfile"));
                        }
                    }
                    else
                    {
                        System.out.println("usage: -mode eval -testfile testfile [-output output]");
                    }
                    break;

                case "morana":
                    if (params.containsKey("-spelling"))
                    {
                        System.out.println(HunLemMor.getMorphologicalAnalyses(params.get("-spelling")));
                    }
                    else
                    {
                        System.out.println("usage: -mode morana -spelling spelling");
                    }
                    break;

                case "morphparse":
                    if (params.containsKey("-input") && params.containsKey("-output"))
                    {
                        List<String> lines = read(params.get("-input"));

                        write(morphParse(lines), params.get("-output"));
                    }
                    else
                    {
                        System.out.println("usage: -mode morphparse -input input -output output");
                    }
                    break;

                case "depparse":
                    if (params.containsKey("-input") && params.containsKey("-output"))
                    {
                        List<String> lines = read(params.get("-input"));

                        write(depParse(lines), params.get("-output"));
                    }
                    else
                    {
                        System.out.println("usage: -mode depparse -input input -output output");
                    }
                    break;

                case "tokenized":
                    if (params.containsKey("-input") && params.containsKey("-output"))
                    {
                        write(morphParse(Util.readTokenizedFile(params.get("-input"))), params.get("-output"));
                    }
                    else
                    {
                        System.out.println("usage: -mode tokenized -input input -output output");
                    }
                    break;

                default:
                    System.out.println(usage);
                    break;
            }
        }
    }

    public static String[] getPossibleTags(String word, Set<String> possibleTags)
    {
        Set<MorAna> morAnas = HunLemMor.getMorphologicalAnalyses(word);
        Set<String> tags = new HashSet<String>();

        for (MorAna morAna : morAnas)
        {
            String reduced = ResourceHolder.getMSDReducer().reduce(morAna.getMsd());
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

            for (MorAna morAna : HunLemMor.getMorphologicalAnalyses(tw.word()))
            {
                int freq = ResourceHolder.getFrequencies().containsKey(morAna.getMsd()) ? ResourceHolder.getFrequencies().get(morAna.getMsd()) : 0;

                if (!morAna.getMsd().equals(null))
                {
                    if (ResourceHolder.getMSDReducer().reduce(morAna.getMsd()).equals(tw.tag()) && (max < freq))
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
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/MorAna.java <<'EOF'
package magyarlanc;

public class MorAna implements Comparable<MorAna>
{
    private String lemma;
    private String msd;

    public MorAna()
    {
        this.lemma = null;
        this.msd = null;
    }

    public MorAna(String lemma, String msd)
    {
        this.setLemma(lemma);
        this.setMsd(msd);
    }

    public String toString()
    {
        return this.getLemma() + "@" + this.getMsd();
    }

    public String getLemma()
    {
        return this.lemma;
    }

    public String getMsd()
    {
        return this.msd;
    }

    public void setLemma(String lemma)
    {
        this.lemma = lemma;
    }

    public void setMsd(String msd)
    {
        this.msd = msd;
    }

    public int compareTo(MorAna morAna)
    {
        // megegyezik az lemma es az MSD is
        if (this.getLemma().equals(morAna.getLemma()) && this.getMsd().equals(morAna.getMsd()))
            return 0;

        // megegyezik az lemma
        if (this.getLemma().equals(((MorAna) morAna).getLemma()))
            return this.getMsd().compareTo(((MorAna) morAna).getMsd());

        else
            return this.getLemma().compareTo(((MorAna) morAna).getLemma());
    }

    public boolean equals(MorAna morAna)
    {
        if (this.toString().equals(morAna.toString()))
        {
            return true;
        }

        return false;
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/ResourceHolder.java <<'EOF'
package magyarlanc;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer.DefaultWordTokenizer;
import edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer.WordTokenizer;
import edu.stanford.nlp.tagger.maxent.SzteMaxentTagger;

import is2.parser.Options;
import is2.parser.Parser;

import data.Data;

public class ResourceHolder
{
    private static String POS_MODEL = "25.model";
    private static String CORPUS = "25.lex";
    private static String FREQUENCIES = "25.freq";

    // DEP model
    private static final String PARSER_MODEL = "szeged.dep.model";

    // other resources
    private static final String STOPWORDS = "stopwords.txt";
    private static final String RFS = "rfsa.txt";
    private static final String CORRDIC = "corrdic.txt";
    private static final String HUN_ABBREV = "hun_abbrev.txt";

    // static objects
    private static Set<String> punctations = null;
    private static Set<String> morPhonDir = null;
    private static HunSplitter hunSplitter = null;
    private static MSDToCoNLLFeatures msdToConllFeatures = null;
    private static CoNLLFeaturesToMSD conllFeaturesToMsd = null;

    private static MSDReducer msdReducer = null;

    private static Map<String, Set<MorAna>> corpus = null;
    private static Map<String, Integer> frequencies = null;
    private static Set<String> stopwords = null;
    private static Map<String, String> corrDic = null;
    private static Set<String> hunAbbrev = null;

    private static RFSA rfsa = null;

    private static WordTokenizer tokenizer = null;

    private static KRToMSD krToMsd = null;

    private static SzteMaxentTagger maxentTagger = null;

    private static Parser parser = null;

    // MorPhonDir
    public static Set<String> getMorPhonDir()
    {
        if (morPhonDir == null)
            initMorPhonDir();

        return morPhonDir;
    }

    public static void initMorPhonDir()
    {
        if (morPhonDir == null)
        {
            morPhonDir = Util.loadMorPhonDir();
        }
    }

    // MaxentTagger
    public static SzteMaxentTagger getMaxentTagger()
    {
        if (maxentTagger == null)
        {
            initMaxentTagger();
        }

        return maxentTagger;
    }

    public static void initMaxentTagger()
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
    }

    // KRToMSD
    public static KRToMSD getKRToMSD()
    {
        if (krToMsd == null)
        {
            initKRToMSD();
        }

        return krToMsd;
    }

    public static void initKRToMSD()
    {
        if (krToMsd == null)
        {
            krToMsd = new KRToMSD();
        }
    }

    // RFSA
    public static RFSA getRFSA()
    {
        if (rfsa == null)
        {
            initRFSA();
        }

        return rfsa;
    }

    public static void initRFSA()
    {
        if (rfsa == null)
        {
            try
            {
                rfsa = RFSA.read(Data.class.getResourceAsStream(RFS));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    // tokenizer
    public static WordTokenizer getTokenizer()
    {
        if (tokenizer == null)
        {
            initTokenizer();
        }

        return tokenizer;
    }

    public static void initTokenizer()
    {
        if (tokenizer == null)
        {
            tokenizer = new DefaultWordTokenizer();
        }
    }

    // parser
    public static Parser getParser()
    {
        if (parser == null)
        {
            initParser();
        }

        return parser;
    }

    public static void initParser()
    {
        if (parser == null)
        {
            parser = new Parser(new Options(new String[] { "-model", "./data/" + PARSER_MODEL, "-cores", "1" }));
        }
    }

    // hunsplitter
    public static HunSplitter getHunSplitter()
    {
        return new HunSplitter();
    }

    public static void initHunSplitter()
    {
        if (hunSplitter == null)
        {
            hunSplitter = new HunSplitter();
        }
    }

    // corpus
    public static Map<String, Set<MorAna>> getCorpus()
    {
        if (corpus == null)
        {
            initCorpus();
        }

        return corpus;
    }

    public static void initCorpus()
    {
        if (corpus == null)
        {
            corpus = Util.readCorpus(CORPUS);
        }
    }

    // corrDic
    public static Map<String, String> getCorrDic()
    {
        if (corrDic == null)
        {
            initCorrDic();
        }

        return corrDic;
    }

    public static void initCorrDic()
    {
        if (corrDic == null)
        {
            corrDic = Util.readCorrDic(CORRDIC);
        }
    }

    // Frequencies
    public static Map<String, Integer> getFrequencies()
    {
        if (frequencies == null)
        {
            initFrequencies();
        }

        return frequencies;
    }

    public static void initFrequencies()
    {
        if (frequencies == null)
        {
            frequencies = Util.readFrequencies(FREQUENCIES);
        }
    }

    // CoNLLFeaturesToMSD
    public static CoNLLFeaturesToMSD getCoNLLFeaturesToMSD()
    {
        if (conllFeaturesToMsd == null)
        {
            initCoNLLFeaturesToMSD();
        }

        return conllFeaturesToMsd;
    }

    public static void initCoNLLFeaturesToMSD()
    {
        if (conllFeaturesToMsd == null)
        {
            conllFeaturesToMsd = new CoNLLFeaturesToMSD();
        }
    }

    // MsdToCoNLLFeatures
    public static MSDToCoNLLFeatures getMSDToCoNLLFeatures()
    {
        if (msdToConllFeatures == null)
        {
            initMSDToCoNLLFeatures();
        }

        return msdToConllFeatures;
    }

    public static void initMSDToCoNLLFeatures()
    {
        if (msdToConllFeatures == null)
        {
            msdToConllFeatures = new MSDToCoNLLFeatures();
        }
    }

    // MSDReducer
    public static MSDReducer getMSDReducer()
    {
        if (msdReducer == null)
        {
            initMSDReducer();
        }

        return msdReducer;
    }

    public static void initMSDReducer()
    {
        if (msdReducer == null)
        {
            msdReducer = new MSDReducer();
        }
    }

    // punctations

    public static Set<String> getPunctations()
    {
        if (punctations == null)
        {
            initPunctations();
        }

        return punctations;
    }

    public static void initPunctations()
    {
        if (punctations == null)
        {
            punctations = Util.loadPunctations();
        }
    }

    public static Set<String> getStopwords()
    {
        if (stopwords == null)
        {
            stopwords = Util.readStopwords(STOPWORDS);
        }

        return stopwords;
    }

    public static Set<String> getHunAbbrev()
    {
        if (hunAbbrev == null)
        {
            hunAbbrev = Util.readList(HUN_ABBREV);
        }

        return hunAbbrev;
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/Util.java <<'EOF'
package magyarlanc;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import data.Data;

public class Util
{
    /**
     * adott szo csak irasjeleket tartalmaz-e
     */
    public static boolean isPunctation(String spelling)
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

    /**
     * 16 15-18 minden szam < 32
     */
    public static boolean isDate(String spelling)
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

    static Map<String, Set<MorAna>> readCorpus(String file)
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

    static Map<String, Integer> readFrequencies(String file)
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

    static Set<String> readStopwords(String file)
    {
        Set<String> stopwords = new TreeSet<String>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Data.class.getResourceAsStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                stopwords.add(line);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return stopwords;
    }

    static Set<String> readList(String file)
    {
        Set<String> lines = new TreeSet<String>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Data.class.getResourceAsStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                lines.add(line);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return lines;
    }

    public static Set<String> loadPunctations()
    {
        Set<String> punctations = new HashSet<String>();

        String[] puncts = { "!", ",", "-", ".", ":", ";", "?", "–" };

        for (String punct : puncts)
        {
            punctations.add(punct);
        }

        return punctations;
    }

    public static Set<String> loadMorPhonDir()
    {
        Set<String> morPhonDir = new HashSet<String>();

        String[] morPhons = new String[] { "talány", "némber", "sün", "fal", "holló", "felhő", "kalap", "hely", "köd" };

        for (String morPhon : morPhons)
        {
            morPhonDir.add(morPhon);
        }

        return morPhonDir;
    }

    public static void writeMapToFile(Map<?, ?> map, File file)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (Map.Entry<?, ?> entry : map.entrySet())
            {
                writer.write(entry.getKey().toString());
                writer.write('\t');
                writer.write(entry.getValue().toString());
                writer.write('\n');
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static Map<String, String> readCorrDic(String file)
    {
        Map<String, String> dictionary = new TreeMap<String, String>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Data.class.getResourceAsStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                String[] splitted = line.split("\t");
                dictionary.put(splitted[0], splitted[1]);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return dictionary;
    }

    public static String[] getAbsoluteLemma(String form)
    {
        List<String> lemma = new ArrayList<String>();

        for (String s : ResourceHolder.getRFSA().analyse(form))
        {
            // igekotok levalasztasa
            s = s.substring(s.indexOf("$") + 1);

            if (s.contains("(") && s.indexOf("(") < s.indexOf("/"))
                lemma.add(s.substring(0, s.indexOf("(")));
            else
                lemma.add(s.substring(0, s.indexOf("/")));
        }

        return lemma.toArray(new String[lemma.size()]);
    }

    public static String readFileToString(String file)
    {
        StringBuilder sb = new StringBuilder(1 << 10);
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            char[] buf = new char[1024];
            int numRead = 0;
            while ((numRead = reader.read(buf)) != -1)
            {
                String readData = String.valueOf(buf, 0, numRead);
                sb.append(readData);
                buf = new char[1024];
            }
            reader.close();
        }
        catch (IOException e)
        {
            System.err.println("Problem with file: " + file);
            return new String();
        }

        return sb.toString();
    }

    /**
     * Reads tokenized file. Each line contains exactly one token.
     *
     * @param filenName
     *          filename
     *
     * @return array of token arrays
     */
    public static String[][] readTokenizedFile(String filenName)
    {
        List<String[]> sentences = new ArrayList<String[]>();

        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(filenName), "UTF-8"));

            List<String> tokens = new ArrayList<String>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.trim().length() == 0)
                {
                    // add sentece to senteces
                    sentences.add(tokens.toArray(new String[tokens.size()]));
                    tokens = new ArrayList<String>();
                }
                else
                {
                    // add token to tokens
                    tokens.add(line.trim());
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                reader.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        return sentences.toArray(new String[sentences.size()][]);
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/CoNLLFeaturesToMSD.java <<'EOF'
package magyarlanc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class CoNLLFeaturesToMSD
{
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

        for (int i = 0; i < splitted.length; ++i)
        {
            if (!splitted[i].equals(""))
                map.put(splitted[i], i + 1);
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

    private Set<String> possibleFeatures;

    public CoNLLFeaturesToMSD()
    {
        Set<String> features = new TreeSet<String>();

        for (String feature : CONLL_2009_FEATURES)
            features.add(feature);

        setPossibleFeatures(features);
    }

    private void setPossibleFeatures(Set<String> features)
    {
        possibleFeatures = features;
    }

    private Set<String> getPossibleFeatures()
    {
        return possibleFeatures;
    }

    /**
     * Strip the unnecessary - signs from the end of the MSD code.
     */
    private String cleanMsd(String msd)
    {
        int i = msd.length();

        while (msd.charAt(i - 1) == '-')
        {
            --i;
        }

        return msd.substring(0, i);
    }

    /**
     * Split the String of the features via the | sing, and put the featurenames and its values to a map.
     */
    private Map<String, String> getFeaturesMap(String features)
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

            if (!this.getPossibleFeatures().contains(pair[0]))
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
    private String convert(Character pos, Map<String, Integer> positionsMap, Map<String, String> featuresMap)
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
            String cleaned = cleanMsd(msd.toString());

            if (cleaned.length() == 4)
                return cleaned.substring(0, 3);
        }

        return cleanMsd(msd.toString());
    }

    public String convert(String pos, String features)
    {
        if (pos.length() > 1)
        {
            return "_";
        }

        return convert(pos.charAt(0), features);
    }

    /**
     * convert the POS character and feature String to MSD code for ex. the POS
     * character can be 'N' and the feature String that belongs to the POS
     * character can be "SubPOS=c|Num=s|Cas=n|NumP=none|PerP=none|NumPd=none"
     */
    public String convert(char pos, String features)
    {
        /**
         * The relevant punctations has no features, its featurestring contain only
         * a _ character. The MSD code of a relevant punctations is the punctation
         * itself.
         */

        if (features == "" || features == null)
        {
            System.err.println("Empty (or null) features");
            System.err.println("Unable to convert: " + pos + " " + features);
            return null;
        }

        try
        {
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
                case 'N':
                    return convert(pos, nounMap, featuresMap);
                case 'V':
                    return convert(pos, verbMap, featuresMap);
                case 'A':
                    return convert(pos, adjMap, featuresMap);
                case 'P':
                    return convert(pos, pronounMap, featuresMap);
                case 'T':
                    return convert(pos, articleMap, featuresMap);
                case 'R':
                    return convert(pos, adverbMap, featuresMap);
                case 'S':
                    return convert(pos, adpositionMap, featuresMap);
                case 'C':
                    return convert(pos, conjunctionMap, featuresMap);
                case 'M':
                    return convert(pos, numeralMap, featuresMap);
                case 'I':
                    return convert(pos, interjectionMap, featuresMap);
                case 'O':
                    return convert(pos, otherMap, featuresMap);
                case 'X':
                    return "X";
                case 'Y':
                    return "Y";
                case 'Z':
                    return "Z";
                case 'K':
                    return "K";
                default:
                    System.err.println("Incorrect POS: " + pos);
                    return null;
            }
        }
        catch (NullPointerException e)
        {
            System.err.println("Unable to convert: " + pos + " " + features);
        }

        return null;
    }

    public static void main(String[] args)
    {
        System.err.println(new CoNLLFeaturesToMSD().convert("O", "SubPOS=e|Type=w|Num=s|Cas=n|NumP=none|PerP=none|NumPd=none"));
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/KRToMSD.java <<'EOF'
package magyarlanc;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class KRToMSD
{
    private Map<String, Set<String>> cache = null;

    public KRToMSD()
    {
        this.setCache(new TreeMap<String, Set<String>>());
    }

    /**
     * melleknevi igenevek
     */
    public boolean isParticiple(String krAnalysis)
    {
        int verbIndex = krAnalysis.indexOf("/VERB"), adjIndex = krAnalysis.indexOf("/ADJ");

        return (verbIndex > -1 && adjIndex > -1 && adjIndex > verbIndex);
    }

    public String getPostPLemma(String analysis)
    {
        if (analysis.startsWith("$én/NOUN<POSTP<")
         || analysis.startsWith("$te/NOUN<POSTP<")
         || analysis.startsWith("$ő/NOUN<POSTP<")
         || analysis.startsWith("$mi/NOUN<POSTP<")
         || analysis.startsWith("$ti/NOUN<POSTP<")
         || analysis.startsWith("$ők/NOUN<POSTP<"))
        {
            String post = null;

            if (analysis.startsWith("$én") || analysis.startsWith("$te"))
            {
                post = analysis.substring(15, analysis.length() - 11).toLowerCase();
            }
            else if (analysis.startsWith("$ők"))
            {
                post = analysis.substring(15, analysis.length() - 14).toLowerCase();
            }
            else if (analysis.startsWith("$ő"))
            {
                post = analysis.substring(14, analysis.length() - 8).toLowerCase();
            }
            else if (analysis.startsWith("$mi") || analysis.startsWith("$ti"))
            {
                post = analysis.substring(15, analysis.length() - 17).toLowerCase();
            }

            if (analysis.startsWith("$ő") && !analysis.startsWith("$ők"))
            {
                analysis = analysis.substring(2);
            }
            else
            {
                analysis = analysis.substring(3);
            }

            return post;
        }

        if (analysis.startsWith("$ez/NOUN<POSTP<") || analysis.startsWith("$az/NOUN<POSTP<"))
        {
            String affix = analysis.substring(15);
            affix = affix.substring(0, affix.indexOf(">")).toLowerCase();

            // alá, alatt, alól, által, elő, előb, ellen, elől, előtt, iránt, után (pl.: ezután)
            if (analysis.contains("(i)"))
            {
                if (affix.startsWith("a") || affix.startsWith("á")
                        || affix.startsWith("e") || affix.startsWith("i")
                        || affix.startsWith("u"))
                    return analysis.substring(1, 3) + affix + "i";

                return analysis.substring(1, 2) + affix + "i";
            }

            return analysis.substring(1, 3) + affix;
        }

        return analysis.substring(1, analysis.indexOf("/"));
    }

    public String convertNoun(String lemma, String kr)
    {
        StringBuilder msd = new StringBuilder(Magyarlanc.DEFAULT_NOUN + "------");

        /*
         * névmás minden PERS-t tartalmazó NOUN
         */

        // velem
        // /NOUN<PERS<1>><CAS<INS>>

        if (kr.contains("PERS"))
        {
            msd = new StringBuilder("Pp--sn-----------");

            /*
             * személy
             */

            // 1
            if (kr.contains("<PERS<1>>"))
            {
                msd.setCharAt(2, '1');
            }

            // 2
            if (kr.contains("<PERS<2>>"))
            {
                msd.setCharAt(2, '2');
            }

            // 3
            if (kr.contains("<PERS>"))
            {
                msd.setCharAt(2, '3');
            }

            /*
             * szám
             */

            if (kr.contains("<PLUR>"))
            {
                msd.setCharAt(4, 'p');
            }

            /*
             * eset
             */

            // n nincs jelölve alapeset

            // a
            if (kr.contains("<CAS<ACC>>"))
            {
                msd.setCharAt(5, 'a');
            }

            // g nincs jelölve
            if (kr.contains("<CAS<GEN>>"))
            {
                msd.setCharAt(5, 'g');
            }

            // d
            if (kr.contains("<CAS<DAT>>"))
            {
                msd.setCharAt(5, 'd');
            }

            // i
            if (kr.contains("<CAS<INS>>"))
            {
                msd.setCharAt(5, 'i');
            }

            // x
            if (kr.contains("<CAS<ILL>>"))
            {
                msd.setCharAt(5, 'x');
            }

            // 2
            if (kr.contains("<CAS<INE>>"))
            {
                msd.setCharAt(5, '2');
            }

            // e
            if (kr.contains("<CAS<ELA>>"))
            {
                msd.setCharAt(5, 'e');
            }

            // t
            if (kr.contains("<CAS<ALL>>"))
            {
                msd.setCharAt(5, 't');
            }

            // 3
            if (kr.contains("<CAS<ADE>>"))
            {
                msd.setCharAt(5, '3');
            }

            // b
            if (kr.contains("<CAS<ABL>>"))
            {
                msd.setCharAt(5, 'b');
            }

            // s
            if (kr.contains("<CAS<SBL>>"))
            {
                msd.setCharAt(5, 's');
            }

            // p
            if (kr.contains("<CAS<SUE>>"))
            {
                msd.setCharAt(5, 'p');
            }

            // h
            if (kr.contains("<CAS<DEL>>"))
            {
                msd.setCharAt(5, 'h');
            }

            // 9
            if (kr.contains("<CAS<TER>>"))
            {
                msd.setCharAt(5, '9');
            }

            // w
            if (kr.contains("[MANNER]"))
            {
                msd.setCharAt(5, 'w');
            }

            // f
            if (kr.contains("<CAS<FOR>>"))
            {
                msd.setCharAt(5, 'f');
            }

            // m
            if (kr.contains("<CAS<TEM>>"))
            {
                msd.setCharAt(5, 'm');
            }

            // c
            if (kr.contains("<CAS<CAU>>"))
            {
                msd.setCharAt(5, 'c');
            }

            // q
            if (kr.contains("[COM]"))
            {
                msd.setCharAt(5, 'q');
            }

            // y
            if (kr.contains("<CAS<TRA>>"))
            {
                msd.setCharAt(5, 'y');
            }

            // u
            if (kr.contains("[PERIOD1]"))
            {
                msd.setCharAt(5, 'u');
            }

            return cleanMsd(msd.toString());
        }

        /*
         * névmás minden POSTP-t tartalmazó NOUN
         */

        if (kr.contains("POSTP"))
        {
            msd = new StringBuilder("Pp3-sn");

            if (lemma.equals("én"))
            {
                msd.setCharAt(2, '1');
            }

            if (lemma.equals("te"))
            {
                msd.setCharAt(2, '2');
            }

            if (lemma.equals("ő"))
            {
                msd.setCharAt(2, '3');
            }

            if (lemma.equals("mi"))
            {
                msd.setCharAt(2, '1');
                msd.setCharAt(4, 'p');
            }

            if (lemma.equals("ti"))
            {
                msd.setCharAt(2, '2');
                msd.setCharAt(4, 'p');
            }

            if (lemma.equals("ők"))
            {
                msd.setCharAt(2, '3');
                msd.setCharAt(4, 'p');
            }

            return cleanMsd(msd.toString());
        }

        /*
         * egyes szám/többes szám NOUN<PLUR> NUON<PLUR<FAM>>
         */

        if (kr.contains("NOUN<PLUR"))
        {
            msd.setCharAt(3, 'p');
        }

        /*
         * eset
         */

        // n nincs jelölve alapeset

        // a
        if (kr.contains("<CAS<ACC>>"))
        {
            msd.setCharAt(4, 'a');
        }

        // g nincs jelolve
        if (kr.contains("<CAS<GEN>>"))
        {
            msd.setCharAt(4, 'g');
        }

        // d
        if (kr.contains("<CAS<DAT>>"))
        {
            msd.setCharAt(4, 'd');
        }

        // i
        if (kr.contains("<CAS<INS>>"))
        {
            msd.setCharAt(4, 'i');
        }

        // x
        if (kr.contains("<CAS<ILL>>"))
        {
            msd.setCharAt(4, 'x');
        }

        // 2
        if (kr.contains("<CAS<INE>>"))
        {
            msd.setCharAt(4, '2');
        }

        // e
        if (kr.contains("<CAS<ELA>>"))
        {
            msd.setCharAt(4, 'e');
        }

        // t
        if (kr.contains("<CAS<ALL>>"))
        {
            msd.setCharAt(4, 't');
        }

        // 3
        if (kr.contains("<CAS<ADE>>"))
        {
            msd.setCharAt(4, '3');
        }

        // b
        if (kr.contains("<CAS<ABL>>"))
        {
            msd.setCharAt(4, 'b');
        }

        // s
        if (kr.contains("<CAS<SBL>>"))
        {
            msd.setCharAt(4, 's');
        }

        // p
        if (kr.contains("<CAS<SUE>>"))
        {
            msd.setCharAt(4, 'p');
        }

        // h
        if (kr.contains("<CAS<DEL>>"))
        {
            msd.setCharAt(4, 'h');
        }

        // 9
        if (kr.contains("<CAS<TER>>"))
        {
            msd.setCharAt(4, '9');
        }

        // w
        if (kr.contains("<CAS<ESS>>"))
        {
            msd.setCharAt(4, 'w');
        }

        // f
        if (kr.contains("<CAS<FOR>>"))
        {
            msd.setCharAt(4, 'f');
        }

        // m
        if (kr.contains("<CAS<TEM>>"))
        {
            msd.setCharAt(4, 'm');
        }

        // c
        if (kr.contains("<CAS<CAU>>"))
        {
            msd.setCharAt(4, 'c');
        }

        // q
        if (kr.contains("[COM]"))
        {
            msd.setCharAt(4, 'q');
        }

        // y
        if (kr.contains("<CAS<TRA>>"))
        {
            msd.setCharAt(4, 'y');
        }

        // u
        if (kr.contains("[PERIOD1]"))
        {
            msd.setCharAt(4, 'u');
        }

        /*
         * birtokos száma/személye
         */
        if (kr.contains("<POSS>"))
        {
            msd.setCharAt(8, 's');
            msd.setCharAt(9, '3');
        }
        if (kr.contains("<POSS<1>>"))
        {
            msd.setCharAt(8, 's');
            msd.setCharAt(9, '1');
        }
        if (kr.contains("<POSS<2>>"))
        {
            msd.setCharAt(8, 's');
            msd.setCharAt(9, '2');
        }
        if (kr.contains("<POSS<1><PLUR>>"))
        {
            msd.setCharAt(8, 'p');
            msd.setCharAt(9, '1');
        }
        if (kr.contains("<POSS<2><PLUR>>"))
        {
            msd.setCharAt(8, 'p');
            msd.setCharAt(9, '2');
        }
        if (kr.contains("<POSS<PLUR>>"))
        {
            msd.setCharAt(8, 'p');
            msd.setCharAt(9, '3');
        }

        /*
         * birtok(olt) száma
         */
        if (kr.contains("<ANP>"))
        {
            msd.setCharAt(10, 's');
        }
        if (kr.contains("<ANP<PLUR>>"))
        {
            msd.setCharAt(10, 'p');
        }

        return cleanMsd(msd.toString());
    }

    public String convertAdjective(String kr)
    {
        StringBuilder msd = new StringBuilder("Afp-sn-------");

        /*
         * típus (melléknév vagy melléknévi igenév)
         */

        // f (melléknév) nincs jelölve, alapeset

        // p (folyamatos melléknévi igenév)

        if (kr.contains("[IMPERF_PART"))
        {
            msd.setCharAt(1, 'p');
        }

        // s (befejezett melleknevi igenev)

        if (kr.contains("[PERF_PART"))
        {
            msd.setCharAt(1, 's');
        }

        // u (beallo melleknevi igenev)

        if (kr.contains("[FUT_PART"))
        {
            msd.setCharAt(1, 'u');
        }

        /*
         * fok
         */

        // p nincs jelölve alapeset

        // c
        if (kr.contains("[COMPAR"))
        {
            msd.setCharAt(2, 'c');
        }
        // s
        if (kr.contains("[SUPERLAT"))
        {
            msd.setCharAt(2, 's');
        }
        // e
        if (kr.contains("[SUPERSUPERLAT"))
        {
            msd.setCharAt(2, 'e');
        }

        /*
         * szam
         */
        // s nincs jelölve alapeset

        // p
        if (kr.contains("ADJ<PLUR>"))
        {
            msd.setCharAt(4, 'p');
        }

        /*
         * eset
         */

        // n nincs jelölve alapeset

        // a
        if (kr.contains("<CAS<ACC>>"))
        {
            msd.setCharAt(5, 'a');
        }

        // g nincs jelölve
        if (kr.contains("<CAS<GEN>>"))
        {
            msd.setCharAt(5, 'g');
        }

        // d
        if (kr.contains("<CAS<DAT>>"))
        {
            msd.setCharAt(5, 'd');
        }

        // i
        if (kr.contains("<CAS<INS>>"))
        {
            msd.setCharAt(5, 'i');
        }

        // x
        if (kr.contains("<CAS<ILL>>"))
        {
            msd.setCharAt(5, 'x');
        }

        // 2
        if (kr.contains("<CAS<INE>>"))
        {
            msd.setCharAt(5, '2');
        }

        // e
        if (kr.contains("<CAS<ELA>>"))
        {
            msd.setCharAt(5, 'e');
        }

        // t
        if (kr.contains("<CAS<ALL>>"))
        {
            msd.setCharAt(5, 't');
        }

        // 3
        if (kr.contains("<CAS<ADE>>"))
        {
            msd.setCharAt(5, '3');
        }

        // b
        if (kr.contains("<CAS<ABL>>"))
        {
            msd.setCharAt(5, 'b');
        }

        // s
        if (kr.contains("<CAS<SBL>>"))
        {
            msd.setCharAt(5, 's');
        }

        // p
        if (kr.contains("<CAS<SUE>>"))
        {
            msd.setCharAt(5, 'p');
        }

        // h
        if (kr.contains("<CAS<DEL>>"))
        {
            msd.setCharAt(5, 'h');
        }

        // 9
        if (kr.contains("<CAS<TER>>"))
        {
            msd.setCharAt(5, '9');
        }

        // w
        if (kr.contains("[MANNER]"))
        {
            msd.setCharAt(5, 'w');
        }

        // f
        if (kr.contains("<CAS<FOR>>"))
        {
            msd.setCharAt(5, 'f');
        }

        // m
        if (kr.contains("<CAS<TEM>>"))
        {
            msd.setCharAt(5, 'm');
        }

        // c
        if (kr.contains("<CAS<CAU>>"))
        {
            msd.setCharAt(5, 'c');
        }

        // q
        if (kr.contains("[COM]"))
        {
            msd.setCharAt(5, 'q');
        }

        // y
        if (kr.contains("<CAS<TRA>>"))
        {
            msd.setCharAt(5, 'y');
        }

        // u
        if (kr.contains("[PERIOD1]"))
        {
            msd.setCharAt(5, 'u');
        }

        /*
         * birtokos száma/személye
         */
        if (kr.contains("<POSS>"))
        {
            msd.setCharAt(10, 's');
            msd.setCharAt(11, '3');
        }
        if (kr.contains("<POSS<1>>"))
        {
            msd.setCharAt(10, 's');
            msd.setCharAt(11, '1');
        }
        if (kr.contains("<POSS<2>>"))
        {
            msd.setCharAt(10, 's');
            msd.setCharAt(11, '2');
        }
        if (kr.contains("<POSS<1><PLUR>>"))
        {
            msd.setCharAt(10, 'p');
            msd.setCharAt(11, '1');
        }
        if (kr.contains("<POSS<2><PLUR>>"))
        {
            msd.setCharAt(10, 'p');
            msd.setCharAt(11, '2');
        }
        if (kr.contains("<POSS<PLUR>>"))
        {
            msd.setCharAt(10, 'p');
            msd.setCharAt(11, '3');
        }

        /*
         * birtok(olt) száma
         */
        if (kr.contains("<ANP>"))
        {
            msd.setCharAt(12, 's');
        }
        if (kr.contains("<ANP<PLUR>>"))
        {
            msd.setCharAt(12, 'p');
        }

        return cleanMsd(msd.toString());
    }

    public String convertVerb(String kr)
    {
        StringBuilder msd = new StringBuilder("Vmip3s---n-");

        boolean modal = kr.contains("<MODAL>"), freq = kr.contains("[FREQ]"), caus = kr.contains("[CAUS]");

        // ható
        if (modal && !freq && !caus)
        {
            msd.setCharAt(1, 'o');
        }

        // gyakorító
        if (!modal && freq && !caus)
        {
            msd.setCharAt(1, 'f');
        }

        // műveltető
        if (!modal && !freq && caus)
        {
            msd.setCharAt(1, 's');
        }

        // gyakorító + ható
        if (modal && freq && !caus)
        {
            msd.setCharAt(1, '1');
        }

        // műveltető + ható
        if (modal && !freq && caus)
        {
            msd.setCharAt(1, '2');
        }

        // műveltető + ható
        if (!modal && freq && caus)
        {
            msd.setCharAt(1, '3');
        }

        // műveltető + gyakorító + ható
        if (modal && freq && caus)
        {
            msd.setCharAt(1, '4');
        }

        if (kr.contains("<COND>"))
        {
            msd.setCharAt(2, 'c');
        }

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

        if (kr.contains("<SUBJUNC-IMP>"))
        {
            msd.setCharAt(2, 'm');
        }

        if (kr.contains("<PAST>"))
        {
            msd.setCharAt(3, 's');
        }

        if (kr.contains("<PERS<1>>"))
        {
            msd.setCharAt(4, '1');
        }
        if (kr.contains("<PERS<2>>"))
        {
            msd.setCharAt(4, '2');
        }

        if (kr.contains("<PLUR>"))
        {
            msd.setCharAt(5, 'p');
        }

        if (kr.contains("<DEF>"))
        {
            msd.setCharAt(9, 'y');
        }

        if (kr.contains("<PERS<1<OBJ<2>>>>"))
        {
            msd.setCharAt(4, '1');
            msd.setCharAt(9, '2');
        }

        return cleanMsd(msd.toString());
    }

    public String convertNumber(String kr, String analysis)
    {
        StringBuilder msd = new StringBuilder("Mc-snl-------");

        // c alapeset, nincs jelölve

        // o
        if (kr.contains("[ORD"))
        {
            msd.setCharAt(1, 'o');
        }
        // f
        if (kr.contains("[FRACT"))
        {
            msd.setCharAt(1, 'f');
        }

        // l nincs a magyarban
        // d nincs KRben

        // s alapeset, nincs jelölve
        // p
        if (kr.contains("NUM<PLUR>"))
        {
            msd.setCharAt(3, 'p');
        }

        /*
         * eset
         */

        // n nincs jelölve alapeset

        // a
        if (kr.contains("<CAS<ACC>>"))
        {
            msd.setCharAt(4, 'a');
        }

        // g nincs jelölve
        if (kr.contains("<CAS<GEN>>"))
        {
            msd.setCharAt(4, 'g');
        }

        // d
        if (kr.contains("<CAS<DAT>>"))
        {
            msd.setCharAt(4, 'd');
        }

        // i
        if (kr.contains("<CAS<INS>>"))
        {
            msd.setCharAt(4, 'i');
        }

        // x
        if (kr.contains("<CAS<ILL>>"))
        {
            msd.setCharAt(4, 'x');
        }

        // 2
        if (kr.contains("<CAS<INE>>"))
        {
            msd.setCharAt(4, '2');
        }

        // e
        if (kr.contains("<CAS<ELA>>"))
        {
            msd.setCharAt(4, 'e');
        }

        // t
        if (kr.contains("<CAS<ALL>>"))
        {
            msd.setCharAt(4, 't');
        }

        // 3
        if (kr.contains("<CAS<ADE>>"))
        {
            msd.setCharAt(4, '3');
        }

        // b
        if (kr.contains("<CAS<ABL>>"))
        {
            msd.setCharAt(4, 'b');
        }

        // s
        if (kr.contains("<CAS<SBL>>"))
        {
            msd.setCharAt(4, 's');
        }

        // p
        if (kr.contains("<CAS<SUE>>"))
        {
            msd.setCharAt(4, 'p');
        }

        // h
        if (kr.contains("<CAS<DEL>>"))
        {
            msd.setCharAt(4, 'h');
        }

        // 9
        if (kr.contains("<CAS<TER>>"))
        {
            msd.setCharAt(4, '9');
        }

        // w
        if (kr.contains("[MANNER]"))
        {
            msd.setCharAt(4, 'w');
        }

        // f
        if (kr.contains("<CAS<FOR>>"))
        {
            msd.setCharAt(4, 'f');
        }

        // m
        if (kr.contains("<CAS<TEM>>"))
        {
            msd.setCharAt(4, 'm');
        }

        // c
        if (kr.contains("<CAS<CAU>>"))
        {
            msd.setCharAt(4, 'c');
        }

        // q
        if (kr.contains("[COM]"))
        {
            msd.setCharAt(4, 'q');
        }

        // y
        if (kr.contains("<CAS<TRA>>"))
        {
            msd.setCharAt(4, 'y');
        }

        // u
        if (kr.contains("[PERIOD1]"))
        {
            msd.setCharAt(4, 'u');
        }

        // 6
        if (kr.contains("[MULTIPL-ITER]"))
        {
            msd.setCharAt(4, '6');
        }

        /*
         * birtokos száma/személye
         */
        if (analysis.contains("<POSS>"))
        {
            msd.setCharAt(10, 's');
            msd.setCharAt(11, '3');
        }
        if (analysis.contains("<POSS<1>>"))
        {
            msd.setCharAt(10, 's');
            msd.setCharAt(11, '1');
        }
        if (analysis.contains("<POSS<2>>"))
        {
            msd.setCharAt(10, 's');
            msd.setCharAt(11, '2');
        }
        if (analysis.contains("<POSS<1><PLUR>>"))
        {
            msd.setCharAt(10, 'p');
            msd.setCharAt(11, '1');
        }
        if (analysis.contains("<POSS<2><PLUR>>"))
        {
            msd.setCharAt(10, 'p');
            msd.setCharAt(11, '2');
        }
        if (analysis.contains("<POSS<PLUR>>"))
        {
            msd.setCharAt(10, 'p');
            msd.setCharAt(11, '3');
        }

        /*
         * birtok(olt) szama
         */
        if (analysis.contains("<ANP>"))
        {
            msd.setCharAt(12, 's');
        }
        if (analysis.contains("<ANP<PLUR>>"))
        {
            msd.setCharAt(12, 'p');
        }

        return cleanMsd(msd.toString());
    }

    public String convertAdverb(String kr)
    {
        StringBuilder msd = new StringBuilder("Rx----");

        // c
        if (kr.contains("[COMPAR]"))
        {
            msd.setCharAt(2, 'c');
        }
        // s
        if (kr.contains("[SUPERLAT]"))
        {
            msd.setCharAt(2, 's');
        }
        // e
        if (kr.contains("[SUPERSUPERLAT]"))
        {
            msd.setCharAt(2, 'e');
        }

        return cleanMsd(msd.toString());
    }

    public Set<MorAna> getMSD(String krAnalysis)
    {
        Set<MorAna> analisis = new TreeSet<MorAna>();

        String krRoot = KRUtils.getRoot(krAnalysis);
        String lemma = krRoot.substring(1, krRoot.indexOf("/"));

        // $forog(-.)/VERB[CAUS](at)/VERB[FREQ](gat)/VERB<PAST><PERS<1>>

        String stem;

        if (krAnalysis.contains("(") && krAnalysis.indexOf("(") < krAnalysis.indexOf("/"))
        {
            stem = krAnalysis.substring(1, krAnalysis.indexOf("("));
        }
        else if (krAnalysis.contains("+"))
        {
            stem = lemma;
        }
        else
        {
            stem = krAnalysis.substring(1, krAnalysis.indexOf("/"));
        }

        String krCode = krRoot.substring(krRoot.indexOf("/") + 1);

        if (!krAnalysis.contains("[FREQ]") && krAnalysis.contains("[CAUS]") & krAnalysis.contains("<MODAL>"))
        {
            if (this.getCache().containsKey(krCode))
            {
                for (String m : this.getCache().get(krCode))
                {
                    analisis.add(new MorAna(lemma, m));
                }

                return analisis;
            }
        }

        if (krCode.startsWith("NOUN"))
        {
            String msd = convertNoun(lemma, krCode);

            // pronoun
            if (msd.startsWith("P"))
            {
                lemma = getPostPLemma(krAnalysis);

                // dative
                if (msd.charAt(5) == 'd')
                {
                    analisis.add(new MorAna(lemma, msd.replace('d', 'g')));
                }
            }

            analisis.add(new MorAna(lemma, msd));

            // dative
            if (msd.charAt(4) == 'd')
            {
                analisis.add(new MorAna(lemma, msd.replace('d', 'g')));
            }
        }

        if (krCode.startsWith("ADJ"))
        {
            String msd;

            // melléknévi igenév
            if (isParticiple(krAnalysis))
            {
                msd = convertAdjective(krAnalysis);
                analisis.add(new MorAna(lemma, msd));
            }
            else
            {
                msd = convertAdjective(krCode);
                analisis.add(new MorAna(lemma, msd));
            }

            // dative
            if (msd.charAt(5) == 'd')
            {
                analisis.add(new MorAna(lemma, msd.replace('d', 'g')));
            }
        }

        if (krCode.startsWith("VERB"))
        {
            // határozói igenév
            if (krCode.contains("VERB[PERF_PART]") || krCode.contains("VERB[PART]"))
            {
                analisis.add(new MorAna(lemma, "Rv"));
            }
            else if (krAnalysis.contains("[FREQ]") || krAnalysis.contains("[CAUS]") || krAnalysis.contains("<MODAL>"))
            {
                analisis.add(new MorAna(stem, convertVerb(krAnalysis)));
            }
            else
            {
                analisis.add(new MorAna(lemma, convertVerb(krCode)));
            }
        }

        if (krCode.startsWith("NUM"))
        {
            String msd = convertNumber(krCode, krAnalysis);
            analisis.add(new MorAna(lemma, msd));

            // dative
            if (msd.charAt(4) == 'd')
            {
                analisis.add(new MorAna(lemma, msd.replace('d', 'g')));
            }
        }

        if (krCode.startsWith("ART"))
        {
            // definite/indefinte
            analisis.add(new MorAna(lemma, "T"));
        }

        if (krCode.startsWith("ADV"))
        {
            analisis.add(new MorAna(lemma, convertAdverb(krCode)));
        }

        if (krCode.startsWith("POSTP"))
        {
            analisis.add(new MorAna(lemma, "St"));
        }

        if (krCode.startsWith("CONJ"))
        {
            analisis.add(new MorAna(lemma, "Ccsp"));
        }

        if (krCode.startsWith("UTT-INT"))
        {
            analisis.add(new MorAna(lemma, "I"));
        }

        if (krCode.startsWith("PREV"))
        {
            analisis.add(new MorAna(lemma, "Rp"));
        }

        if (krCode.startsWith("DET"))
        {
            analisis.add(new MorAna(lemma, "Pd3-sn"));
        }

        if (krCode.startsWith("ONO"))
        {
            analisis.add(new MorAna(lemma, "X"));
        }

        if (krCode.startsWith("E"))
        {
            analisis.add(new MorAna(lemma, "Rq-y"));
        }

        if (krCode.startsWith("ABBR"))
        {
            analisis.add(new MorAna(lemma, "Y"));
        }

        if (krCode.startsWith("TYPO"))
        {
            analisis.add(new MorAna(lemma, "Z"));
        }

        if (analisis.isEmpty())
        {
            analisis.add(new MorAna(lemma, "X"));
        }

        // cache
        if (!this.getCache().containsKey(krCode))
        {
            this.getCache().put(krCode, new TreeSet<String>());
        }

        for (MorAna m : analisis)
        {
            this.getCache().get(krCode).add(m.getMsd());
        }

        return analisis;
    }

    public String cleanMsd(String msd)
    {
        StringBuilder cleaned = new StringBuilder(msd.trim());

        int index = cleaned.length() - 1;
        while (cleaned.charAt(index) == '-')
        {
            cleaned.deleteCharAt(index);
            --index;
        }

        return cleaned.toString();
    }

    public void setCache(Map<String, Set<String>> cache)
    {
        this.cache = cache;
    }

    public Map<String, Set<String>> getCache()
    {
        return cache;
    }

    public static void main(String args[])
    {
        System.err.println(ResourceHolder.getRFSA().analyse("utánam"));

        System.err.println(ResourceHolder.getKRToMSD().getMSD("$én/NOUN<POSTP<UTÁN>><PERS<1>>"));
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/KRUtils.java <<'EOF'
package magyarlanc;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utils for KR codes
 *
 * @author zsjanos
 *
 */
public class KRUtils
{
    /**
     * possible KR part of speech tags
     *
     * @author zsjanos
     *
     */
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
                                // System.out.println("HIBA: " + feladat);
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
             * szamnevek, amik KRben /ADV
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

    public static void main(String[] args)
    {
        // System.out.println(getRoot("$fut/VERB[GERUND](�s)/NOUN<PLUR><POSS<1>><CAS<INS>>"));

        System.out.println(getPOS("$árapály/NOUN"));
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/MSDReducer.java <<'EOF'
package magyarlanc;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class MSDReducer
{
    private Map<String, String> cache = null;

    private static final Pattern NOUN_PATTERN_1 = Pattern.compile("N.-..---s3");
    private static final Pattern NOUN_PATTERN_2 = Pattern.compile("N.-..---..s");

    private static final Pattern ADJECTIVE_PATTERN_1 = Pattern.compile("A..-..-.--s3");
    private static final Pattern ADJECTIVE_PATTERN_2 = Pattern.compile("A..-..-.--..s");

    private static final Pattern NUMERAL_PATTERN_1 = Pattern.compile("M.-...-.--s3");
    private static final Pattern NUMERAL_PATTERN_2 = Pattern.compile("M.-...-.--..s");

    private static final Pattern OPEN_PATTERN_1 = Pattern.compile("O..-..---s3");
    private static final Pattern OPEN_PATTERN_2 = Pattern.compile("O..-..---..s");

    private static final Pattern VERB_PATTERN_1 = Pattern.compile("V[^a]cp[12]p---y");
    private static final Pattern VERB_PATTERN_2 = Pattern.compile("V[^a]ip1s---y");
    private static final Pattern VERB_PATTERN_3 = Pattern.compile("V[^a]cp3p---y");

    private static final Pattern VERB_PATTERN_4 = Pattern.compile("V[^a]is1[sp]---y");

    public MSDReducer()
    {
        this.setCache(new HashMap<String, String>());
    }

    /**
     * Reduce noun.
     *
     * @param msd
     *          msd code
     * @return reduced code
     */
    private String reduceN(String msd)
    {
        StringBuilder sb = new StringBuilder("N");

        // dative/genitive
        // superessive/essive
        if (msd.length() > 4 && (msd.charAt(4) == 'd' || msd.charAt(4) == 'g' || msd.charAt(4) == 'p'))
        {
            sb.append(msd.charAt(4));
        }

        // N.-..---s3
        if (NOUN_PATTERN_1.matcher(msd).find())
        {
            sb.append('s');
        }

        // N.-..---..s
        if (NOUN_PATTERN_2.matcher(msd).find())
        {
            sb.append('z');
        }

        return sb.toString();
    }

    /**
     * Reduce other
     *
     * @param msd
     *          msd code
     * @return reduced code
     */
    private String reduceO(String msd)
    {
        StringBuilder sb = new StringBuilder("O");

        // dative/genitive
        // superessive/essive
        if (msd.length() > 5 && (msd.charAt(5) == 'd' || msd.charAt(5) == 'g' || msd.charAt(5) == 'p'))
        {
            sb.append(msd.charAt(5));
        }

        // O..-..---s3
        if (OPEN_PATTERN_1.matcher(msd).find())
        {
            sb.append('s');
        }

        // O..-..---..s
        if (OPEN_PATTERN_2.matcher(msd).find())
        {
            sb.append('z');
        }

        return sb.toString();
    }

    /**
     * Reduce verb
     *
     * @param msd
     *          msd code
     * @return reduced code
     */
    private String reduceV(String msd)
    {
        String result = null;

        // Va
        if (msd.startsWith("Va"))
        {
            result = "Va";
        }

        // mult ideju muvelteto igealakok
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
        else if (VERB_PATTERN_1.matcher(msd).find())
        {
            result = "Vcp";
        }

        // eszek eszem
        // V[^a]ip1s---y
        else if (VERB_PATTERN_2.matcher(msd).find())
        {
            result = "Vip";
        }

        // festetnék
        // V[^a]cp3p---y
        else if (VERB_PATTERN_3.matcher(msd).find())
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
        else if (VERB_PATTERN_4.matcher(msd).find())
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
     *
     * @param msd
     *          msd code
     * @return reduced code
     */
    private String reduceA(String msd)
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
        if (ADJECTIVE_PATTERN_1.matcher(msd).find())
        {
            sb.append('s');
        }

        // A..-..-.--..s
        if (ADJECTIVE_PATTERN_2.matcher(msd).find())
        {
            sb.append('z');
        }

        return sb.toString();
    }

    /**
     * Reduce pronoun.
     *
     * @param msd
     *          msd code
     * @return reduced code
     */
    private String reduceP(String msd)
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
     *
     * @param msd
     *          msd code
     * @return reduced code
     */
    private String reduceR(String msd)
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
     *
     * @param msd
     *          msd code
     * @return reduced code
     */
    private String reduceM(String msd)
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
        if (NUMERAL_PATTERN_1.matcher(msd).find())
        {
            sb.append('s');
        }

        // M.-...-.--..s
        if (NUMERAL_PATTERN_2.matcher(msd).find())
        {
            sb.append('z');
        }

        return sb.toString();
    }

    /**
     * Reduce.
     *
     * @param msd
     *          msd code
     * @return reduced code
     */
    public String reduce(String msd)
    {
        String reduced = null;

        if (this.getCache().containsKey(msd))
        {
            return this.getCache().get(msd);
        }

        if (msd.length() == 1)
        {
            return msd;
        }

        switch (msd.charAt(0))
        {
        case 'N':
            reduced = reduceN(msd);
            break;

        case 'V':
            reduced = reduceV(msd);
            break;

        case 'A':
            reduced = reduceA(msd);
            break;

        case 'P':
            reduced = reduceP(msd);
            break;

        case 'R':
            reduced = reduceR(msd);
            break;

        case 'M':
            reduced = reduceM(msd);
            break;

        case 'O':
            reduced = reduceO(msd);
            break;

        case 'C':
            reduced = msd;
            break;

        // T, S, I, X, Y, Z
        default:
            reduced = String.valueOf(msd.charAt(0));
        }

        this.getCache().put(msd, reduced);

        return reduced;
    }

    public void setCache(Map<String, String> cache)
    {
        this.cache = cache;
    }

    public Map<String, String> getCache()
    {
        return cache;
    }

    public String toString()
    {
        return this.getClass().getName();
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/MSDToCoNLLFeatures.java <<'EOF'
package magyarlanc;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class MSDToCoNLLFeatures
{
    /**
     * cache for the extracted features
     */
    private Map<String, String> cache = null;

    public MSDToCoNLLFeatures()
    {
        this.setCache(new HashMap<String, String>());
    }

    private void setCache(Map<String, String> cache)
    {
        this.cache = cache;
    }

    public Map<String, String> getCache()
    {
        return cache;
    }

    /**
     * extract noun
     */
    private String parseN(String MSDCode)
    {
        int length = MSDCode.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (MSDCode.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(MSDCode.charAt(1));
        }

        // 2 (not used)

        // 3 Num
        if (MSDCode.charAt(3) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(MSDCode.charAt(3));
        }

        // 4 Cas
        if (MSDCode.charAt(4) == '-')
        {
            sb.append("|Cas=none");
        }
        else
        {
            sb.append("|Cas=").append(MSDCode.charAt(4));
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
        if (MSDCode.charAt(8) == '-')
        {
            sb.append("|NumP=none");
        }
        else
        {
            sb.append("|NumP=").append(MSDCode.charAt(8));
        }
        if (length == 9)
        {
            sb.append("|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 9 PerP
        if (MSDCode.charAt(9) == '-')
        {
            sb.append("|PerP=none");
        }
        else
        {
            sb.append("|PerP=").append(MSDCode.charAt(9));
        }
        if (length == 10)
        {
            sb.append("|NumPd=none");
            return sb.toString();
        }

        // 10 NumPd
        if (MSDCode.charAt(10) == '-')
        {
            sb.append("|NumPd=none");
        }
        else
        {
            sb.append("|NumPd=").append(MSDCode.charAt(10));
        }

        return sb.toString();
    }

    /**
     * extract verb
     */
    private String parseV(String MSDCode)
    {
        int length = MSDCode.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (MSDCode.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(MSDCode.charAt(1));
        }

        // 2 Mood
        if (MSDCode.charAt(2) == '-')
        {
            sb.append("|Mood=none");
        }
        else
        {
            sb.append("|Mood=").append(MSDCode.charAt(2));
        }
        if (length == 3)
        {
            if (MSDCode.charAt(2) != 'n')
            {
                sb.append("|Tense=none");
            }
            sb.append("|Per=none|Num=none");
            if (MSDCode.charAt(2) != 'n')
            {
                sb.append("|Def=none");
            }

            return sb.toString();
        }

        // 3 Tense (if Mood != n)
        if (MSDCode.charAt(2) != 'n')
        {
            if (MSDCode.charAt(3) == '-')
            {
                sb.append("|Tense=none");
            }
            else
            {
                sb.append("|Tense=").append(MSDCode.charAt(3));
            }
        }
        if (length == 4)
        {
            sb.append("|Per=none|Num=none");
            if (MSDCode.charAt(2) != 'n')
            {
                sb.append("|Def=none");
            }

            return sb.toString();
        }

        // 4 Per
        if (MSDCode.charAt(4) == '-')
        {
            sb.append("|Per=none");
        }
        else
        {
            sb.append("|Per=").append(MSDCode.charAt(4));
        }
        if (length == 5)
        {
            sb.append("|Num=none");
            if (MSDCode.charAt(2) != 'n')
            {
                sb.append("|Def=none");
            }

            return sb.toString();
        }

        // 5 Num
        if (MSDCode.charAt(5) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(MSDCode.charAt(5));
        }
        if (length == 6)
        {
            if (MSDCode.charAt(2) != 'n')
            {
                sb.append("|Def=none");
            }

            return sb.toString();
        }

        // 6 Def
        if (length == 7)
        {
            if (MSDCode.charAt(2) != 'n')
            {
                sb.append("|Def=none");
            }

            return sb.toString();
        }

        // 7 (not used)

        // 8 (not used)

        // 9 Def
        if (MSDCode.charAt(2) != 'n')
        {
            if (MSDCode.charAt(9) == '-')
            {
                sb.append("|Def=none");
            }
            else
            {
                sb.append("|Def=").append(MSDCode.charAt(9));
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
    private String parseA(String MSDCode)
    {
        int length = MSDCode.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (MSDCode.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(MSDCode.charAt(1));
        }

        // 2 Deg
        if (MSDCode.charAt(2) == '-')
        {
            sb.append("|Deg=none");
        }
        else
        {
            sb.append("|Deg=").append(MSDCode.charAt(2));
        }

        // 3 (not used)

        // 4 Num
        if (MSDCode.charAt(4) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(MSDCode.charAt(4));
        }

        // 5 Cas
        if (MSDCode.charAt(5) == '-')
        {
            sb.append("|Cas=none");
        }
        else
        {
            sb.append("|Cas=").append(MSDCode.charAt(5));
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
        if (MSDCode.charAt(10) == '-')
        {
            sb.append("|NumP=none");
        }
        else
        {
            sb.append("|NumP=").append(MSDCode.charAt(10));
        }
        if (length == 11)
        {
            sb.append("|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 11 PerP
        if (MSDCode.charAt(11) == '-')
        {
            sb.append("|PerP=none");
        }
        else
        {
            sb.append("|PerP=").append(MSDCode.charAt(11));
        }
        if (length == 12)
        {
            sb.append("|NumPd=none");
            return sb.toString();
        }

        // 12 NumPd
        if (MSDCode.charAt(12) == '-')
        {
            sb.append("|NumPd=none");
        }
        else
        {
            sb.append("|NumPd=").append(MSDCode.charAt(12));
        }

        return sb.toString();
    }

    /**
     * extract pronoun
     */
    private String parseP(String MSDCode)
    {
        int length = MSDCode.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (MSDCode.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(MSDCode.charAt(1));
        }

        // 2 Per
        if (MSDCode.charAt(2) == '-')
        {
            sb.append("|Per=none");
        }
        else
        {
            sb.append("|Per=").append(MSDCode.charAt(2));
        }

        // 3 (not used)

        // 4 Num
        if (MSDCode.charAt(4) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(MSDCode.charAt(4));
        }

        // 5 Cas
        if (MSDCode.charAt(5) == '-')
        {
            sb.append("|Cas=none");
        }
        else
        {
            sb.append("|Cas=").append(MSDCode.charAt(5));
        }
        if (length == 6)
        {
            sb.append("|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 6 NumP
        if (MSDCode.charAt(6) == '-')
        {
            sb.append("|NumP=none");
        }
        else
        {
            sb.append("|NumP=").append(MSDCode.charAt(6));
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
        if (MSDCode.charAt(15) == '-')
        {
            sb.append("|PerP=none");
        }
        else
        {
            sb.append("|PerP=").append(MSDCode.charAt(15));
        }

        if (length == 16)
        {
            sb.append("|NumPd=none");
            return sb.toString();
        }

        // 16 NumPd
        if (MSDCode.charAt(16) == '-')
        {
            sb.append("|NumPd=none");
        }
        else
        {
            sb.append("|NumPd=").append(MSDCode.charAt(16));
        }

        return sb.toString();
    }

    /**
     * extract article
     */
    private String parseT(String MSDCode)
    {
        // 1 SubPOS
        if (MSDCode.charAt(1) == '-')
        {
            return "SubPOS=none";
        }
        else
        {
            return "SubPOS=" + MSDCode.charAt(1);
        }
    }

    /**
     * extract adverb
     */
    private String parseR(String MSDCode)
    {
        int length = MSDCode.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (MSDCode.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(MSDCode.charAt(1));
        }
        if (length == 2)
        {
            sb.append("|Deg=none");
            if (MSDCode.charAt(1) == 'l')
            {
                sb.append("|Num=none|Per=none");
            }

            return sb.toString();
        }

        // 2 Deg
        if (MSDCode.charAt(2) == '-')
        {
            sb.append("|Deg=none");
        }
        else
        {
            sb.append("|Deg=").append(MSDCode.charAt(2));
        }
        if (length == 3)
        {
            if (MSDCode.charAt(1) == 'l')
            {
                sb.append("|Num=none|Per=none");
            }

            return sb.toString();
        }

        // 3 (not used)

        // 4 Num
        if (MSDCode.charAt(1) == 'l')
        {
            if (MSDCode.charAt(4) == '-')
            {
                sb.append("|Num=none");
            }
            else
            {
                sb.append("|Num=").append(MSDCode.charAt(4));
            }
        }
        if (length == 5)
        {
            if (MSDCode.charAt(1) == 'l')
            {
                sb.append("|Per=none");
            }

            return sb.toString();
        }

        // 5 Per
        if (MSDCode.charAt(1) == 'l')
        {
            if (MSDCode.charAt(5) == '-')
            {
                sb.append("|Per=none");
            }
            else
            {
                sb.append("|Per=").append(MSDCode.charAt(5));
            }
        }

        return sb.toString();
    }

    /**
     * extract adposition
     */
    private String parseS(String MSDCode)
    {
        // 1 SubPOS
        if (MSDCode.charAt(1) == '-')
        {
            return "SubPOS=none";
        }
        else
        {
            return "SubPOS=" + MSDCode.charAt(1);
        }
    }

    /**
     * extract conjucion
     */
    private String parseC(String MSDCode)
    {
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (MSDCode.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(MSDCode.charAt(1));
        }

        // 2 Form
        if (MSDCode.charAt(2) == '-')
        {
            sb.append("|Form=none");
        }
        else
        {
            sb.append("|Form=").append(MSDCode.charAt(2));
        }

        // 3 Coord
        if (MSDCode.charAt(3) == '-')
        {
            sb.append("|Coord=none");
        }
        else
        {
            sb.append("|Coord=").append(MSDCode.charAt(3));
        }

        return sb.toString();
    }

    /**
     * extract numeral
     */
    private String parseM(String MSDCode)
    {
        int length = MSDCode.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (MSDCode.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(MSDCode.charAt(1));
        }

        // 2 (not used)

        // 3 Num
        if (MSDCode.charAt(3) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(MSDCode.charAt(3));
        }

        // 4 Cas
        if (MSDCode.charAt(4) == '-')
        {
            sb.append("|Cas=none");
        }
        else
        {
            sb.append("|Cas=").append(MSDCode.charAt(4));
        }

        // 5 Form
        if (MSDCode.charAt(5) == '-')
        {
            sb.append("|Form=none");
        }
        else
        {
            sb.append("|Form=").append(MSDCode.charAt(5));
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
        if (MSDCode.charAt(10) == '-')
        {
            sb.append("|NumP=none");
        }
        else
        {
            sb.append("|NumP=").append(MSDCode.charAt(10));
        }
        if (length == 11)
        {
            sb.append("|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 11 PerP
        if (MSDCode.charAt(11) == '-')
        {
            sb.append("|PerP=none");
        }
        else
        {
            sb.append("|PerP=").append(MSDCode.charAt(11));
        }
        if (length == 12)
        {
            sb.append("|NumPd=none");
            return sb.toString();
        }

        // 12 NumPd
        if (MSDCode.charAt(12) == '-')
        {
            sb.append("|NumPd=none");
        }
        else
        {
            sb.append("|NumPd=").append(MSDCode.charAt(12));
        }

        return sb.toString();
    }

    /**
     * extract interjection
     */
    private String parseI(String msdCode)
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
    private String parseO(String MSDCode)
    {
        int length = MSDCode.length();
        StringBuilder sb = new StringBuilder();

        // 1 SubPOS
        if (MSDCode.charAt(1) == '-')
        {
            sb.append("SubPOS=none");
        }
        else
        {
            sb.append("SubPOS=").append(MSDCode.charAt(1));
        }
        if (length == 2)
        {
            sb.append("|Num=none|Cas=none|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 2 Type (if SubPOS=e|d|n)
        if (MSDCode.charAt(1) == 'e' || MSDCode.charAt(1) == 'd' || MSDCode.charAt(1) == 'n')
        {
            if (MSDCode.charAt(1) == '-')
            {
                sb.append("|Type=none");
            }
            else
            {
                sb.append("|Type=").append(MSDCode.charAt(2));
            }
        }
        if (length == 3)
        {
            sb.append("|Num=none|Cas=none|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 3 (not used)

        // 4 Num
        if (MSDCode.charAt(4) == '-')
        {
            sb.append("|Num=none");
        }
        else
        {
            sb.append("|Num=").append(MSDCode.charAt(4));
        }
        if (length == 5)
        {
            sb.append("|Cas=none|NumP=none|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 5 Cas
        if (MSDCode.charAt(5) == '-')
        {
            sb.append("|Cas=none");
        }
        else
        {
            sb.append("|Cas=").append(MSDCode.charAt(5));
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
        if (MSDCode.charAt(9) == '-')
        {
            sb.append("|NumP=none");
        }
        else
        {
            sb.append("|NumP=").append(MSDCode.charAt(9));
        }
        if (length == 10)
        {
            sb.append("|PerP=none|NumPd=none");
            return sb.toString();
        }

        // 10 PerP
        if (MSDCode.charAt(10) == '-')
        {
            sb.append("|PerP=none");
        }
        else
        {
            sb.append("|PerP=").append(MSDCode.charAt(10));
        }
        if (length == 11)
        {
            sb.append("|NumPd=none");
            return sb.toString();
        }

        // 11 NumPd
        if (MSDCode.charAt(11) == '-')
        {
            sb.append("|NumPd=none");
        }
        else
        {
            sb.append("|NumPd=").append(MSDCode.charAt(11));
        }

        return sb.toString();
    }

    /**
     * get the features of the given lemma/MSD pair
     */
    public String convert(String lemma, String MSDCode)
    {
        if (lemma.equals("_"))
            return "_";

        // relevant punctation
        if (ResourceHolder.getPunctations().contains(lemma))
        {
            return "_";
        }

        // non relevant punctation
        if (MSDCode.equals("K"))
        {
            return "SubPOS=" + lemma;
        }

        // cache
        if (this.getCache().containsKey(MSDCode))
        {
            return this.getCache().get(MSDCode);
        }

        String features = null;

        if (MSDCode.length() == 1)
        {
        }

        switch (MSDCode.charAt(0))
        {
            // noun
            case 'N':
                features = parseN(MSDCode);
                break;
                // verb
            case 'V':
                features = parseV(MSDCode);
                break;
                // adjective
            case 'A':
                features = parseA(MSDCode);
                break;
                // pronoun
            case 'P':
                features = parseP(MSDCode);
                break;
                // article
            case 'T':
                features = parseT(MSDCode);
                break;
                // adverb
            case 'R':
                features = parseR(MSDCode);
                break;
                // adposition
            case 'S':
                features = parseS(MSDCode);
                break;
                // conjuction
            case 'C':
                features = parseC(MSDCode);
                break;
                // numeral
            case 'M':
                features = parseM(MSDCode);
                break;
                // interjection
            case 'I':
                features = parseI(MSDCode);
                break;
                // open/other
            case 'O':
                features = parseO(MSDCode);
                break;
                // residual
            case 'X':
                features = "_";
                break;
                // abbrevation
            case 'Y':
                features = "_";
                break;
                //
            case 'Z':
                features = "_";
                break;
                // punctation
            case 'K':
                features = "SubPOS=" + lemma;
                break;
        }

        this.getCache().put(MSDCode, features);

        if (features == null)
            return "_";

        return features;
    }

    /*
     * write the cache to file
     */
    public void writeCacheToFile(String file)
    {
        Map<String, String> sorted = new TreeMap<String, String>(this.getCache());

        File f = new File(file);
        try
        {
            System.err.print("\nWriting MSDToCoNLLFeatures cache to " + f.getCanonicalPath());
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        Util.writeMapToFile(sorted, f);
    }

    public String[] convertArray(String[] forms, String[] MSDs)
    {
        String[] features = new String[forms.length];

        for (int i = 0; i < forms.length; ++i)
        {
            features[i] = convert(forms[i], MSDs[i]);
        }

        return features;
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/CompoundWord.java <<'EOF'
package magyarlanc;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import magyarlanc.KRUtils.KRPOS;

public class CompoundWord
{
    public static boolean isCompatibleAnalyises(String kr1, String kr2)
    {
        KRPOS pos1 = KRUtils.getPOS(kr1), pos2 = KRUtils.getPOS(kr2);

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
        RFSA rfsa = ResourceHolder.getRFSA();

        for (int i = 2; i < word.length() - 1; ++i)
        {
            if (rfsa.analyse(word.substring(0, i)).size() > 0 && rfsa.analyse(word.substring(i)).size() > 0)
            {
                return true;
            }
        }

        return false;
    }

    public static int bisectIndex(String word)
    {
        RFSA rfsa = ResourceHolder.getRFSA();

        for (int i = 2; i < word.length() - 1; ++i)
        {
            if (rfsa.analyse(word.substring(0, i)).size() > 0 && rfsa.analyse(word.substring(i)).size() > 0)
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

        RFSA rfsa = ResourceHolder.getRFSA();

        List<String> ans1 = rfsa.analyse(part1), ans2 = rfsa.analyse(part2);

        if (ans1.size() > 0 && ans2.size() > 0)
        {
            for (String f : ans1)
            {
                for (String s : ans2)
                {
                    String kr1 = KRUtils.getRoot(f), kr2 = KRUtils.getRoot(s);

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

            List<String> ans1 = ResourceHolder.getRFSA().analyse(part1);
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
                            if (isCompatibleAnalyises(KRUtils.getRoot(a1), KRUtils.getRoot(a2)))
                            {
                                analises.add(KRUtils.getRoot(a2).replace("$", "$" + part1));
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

        RFSA rfsa = ResourceHolder.getRFSA();

        int hp = word.indexOf('-');
        String part1 = word.substring(0, hp), part2 = word.substring(hp + 1);

        // a kötőjel előtti és a kötőjel utáni résznek is van elemzése (pl.: adat-kezelőt)
        if (isBisectable(part1 + part2))
        {
            analises = getCompatibleAnalises(part1, part2, true);
        }

        // a kötőjel előtti résznek is van elemzése, a kötőjel utáni rész két részre bontható
        else if (rfsa.analyse(part1).size() > 0 && isBisectable(part2))
        {
            List<String> ans1 = rfsa.analyse(part1);

            int bi = bisectIndex(part2);
            String part21 = part2.substring(0, bi), part22 = part2.substring(bi);

            Set<String> ans2 = getCompatibleAnalises(part21, part22);

            for (String a1 : ans1)
            {
                for (String a2 : ans2)
                {
                    if (isCompatibleAnalyises(KRUtils.getRoot(a1), KRUtils.getRoot(a2)))
                    {
                        if (analises == null)
                        {
                            analises = new LinkedHashSet<String>();
                        }
                        analises.add(KRUtils.getRoot(a2).replace("$", "$" + part1 + "-"));
                    }
                }
            }
        }

        else if (isBisectable(part1) && rfsa.analyse(part2).size() > 0)
        {
            List<String> ans2 = rfsa.analyse(part2);

            int bi = bisectIndex(part1);
            String part11 = part1.substring(0, bi), part12 = part1.substring(bi);

            Set<String> ans1 = getCompatibleAnalises(part11, part12);

            for (String a1 : ans1)
            {
                for (String a2 : ans2)
                {
                    if (isCompatibleAnalyises(KRUtils.getRoot(a1), KRUtils.getRoot(a2)))
                    {
                        if (analises == null)
                        {
                            analises = new LinkedHashSet<String>();
                        }
                        analises.add(KRUtils.getRoot(a2).replace("$", "$" + part1 + "-"));
                    }
                }
            }
        }

        return analises;
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/Guesser.java <<'EOF'
package magyarlanc;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Guesser
{
    /**
     * A morPhonGuess függvény egy ismeretlen (nem elemezhető) főnévi szótő és
     * tetszőleges suffix guesselésére szolgál. A guesselés során az adott suffixet
     * a rendszer morPhonDir szótárának elemeire illesztve probáljuk elemezni. A
     * szótár reprezentálja a magyar nyelv minden (nem hasonuló) illeszkedési
     * szabályát, így biztosak lehetünk benne, hogy egy valós toldalék mindenképp
     * illeszkedni fog legalább egy szótárelemre. Például egy 'hoz'rag esetén,
     * először a kód elemre próbálunk illeszteni, majd elemezni. A kapott szóalak
     * így a kódhoz lesz, melyre a KR elemzőnk nem ad elemzést. A következő
     * szótárelem a talány, a szóalak a talányhoz lesz, melyre megkapjuk az Nc-st
     * (külső közelítő/allative) főnévi elemzést.
     */
    public static Set<MorAna> morPhonGuess(String root, String suffix)
    {
        Set<MorAna> stems = new TreeSet<MorAna>();

        RFSA rfsa = ResourceHolder.getRFSA();

        for (String guess : ResourceHolder.getMorPhonDir())
        {
            for (String kr : rfsa.analyse(guess + suffix))
            {
                for (MorAna stem : ResourceHolder.getKRToMSD().getMSD(kr))
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
        for (String kr : ResourceHolder.getRFSA().analyse(suffix))
        {
            for (MorAna morAna : ResourceHolder.getKRToMSD().getMSD(kr))
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

        return ResourceHolder.getKRToMSD().cleanMsd(msd.toString());
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

        return ResourceHolder.getKRToMSD().cleanMsd(msd.toString());
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

        return ResourceHolder.getKRToMSD().cleanMsd(msd.toString());
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, stem.getMsd()));
                    stems.add(new MorAna(root, stem.getMsd().replace(Magyarlanc.DEFAULT_NOUN.substring(0, 2), "Afp")));
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(root, "Afp-sn"));
                stems.add(new MorAna(root, Magyarlanc.DEFAULT_NOUN));
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                    for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d----s3-")));
                    if (Util.isDate(root))
                    {
                        stems.add(new MorAna(root + ".", nounToNoun(stem.getMsd(), Magyarlanc.DEFAULT_NOUN.substring(0, 2) + "------s3-")));
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
                if (Util.isDate(root))
                {
                    stems.add(new MorAna(root + ".", Magyarlanc.DEFAULT_NOUN + "---s3"));
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
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
                for (MorAna stem : Guesser.morPhonGuess(root, suffix))
                {
                    stems.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mo---d-------")));

                    if (Util.isDate(m.group(2)))
                    {
                        stems.add(new MorAna(root, stem.getMsd()));
                    }
                }

            if (stems.size() == 0)
            {
                stems.add(new MorAna(number, "Mo-snd"));
                if (Util.isDate(m.group(2)))
                {
                    stems.add(new MorAna(number, Magyarlanc.DEFAULT_NOUN));
                    stems.add(new MorAna(number, Magyarlanc.DEFAULT_NOUN + "---s3"));
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
        System.out.println(morPhonGuess("London", "ban"));

        System.out.println(hyphenicGuess("Bush", "hoz"));
        System.out.println(hyphenicGuess("Bush", "kormánynak"));

        System.out.println(numberGuess("386-osok"));
        System.out.println(numberGuess("16-ai"));
        System.out.println(numberGuess("5."));
        System.out.println(numberGuess("20%"));
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/HunSplitter.java <<'EOF'
package magyarlanc;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import edu.northwestern.at.morphadorner.corpuslinguistics.sentencesplitter.DefaultSentenceSplitter;
import edu.northwestern.at.morphadorner.corpuslinguistics.sentencesplitter.SentenceSplitter;
import edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer.DefaultWordTokenizer;
import edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer.WordTokenizer;

public class HunSplitter
{
    public static final char[] HYPHENS = new char[] { '-', '­', '–', '—', '―', '−', '─' };
    public static final char DEFAULT_HYPHEN = '-';

    public static final char[] QUOTES = new char[] { '"', '\'', '`', '´', '‘', '’', '“', '”', '„' };
    public static final char DEFAULT_QUOTE = '"';

    public static final char[] FORCE_TOKEN_SEPARATORS = new char[] { ',', '.', ':' };

    private boolean lineSentence;
    private StringCleaner stringCleaner;

    private SentenceSplitter splitter;
    private WordTokenizer tokenizer;

    private List<List<String>> splittedTemp = null;

    public HunSplitter()
    {
        this(false);
    }

    public HunSplitter(boolean lineSentence)
    {
        this.setLineSentence(lineSentence);
        this.setStringCleaner(new StringCleaner());

        this.setSplitter(new DefaultSentenceSplitter());
        this.setTokenizer(new DefaultWordTokenizer());
    }

    public HunSplitter(WordTokenizer wordTokenizer, SentenceSplitter sentenceSplitter, boolean lineSentence)
    {
        this.setLineSentence(lineSentence);
        this.setStringCleaner(new StringCleaner());

        this.setSplitter(sentenceSplitter);
        this.setTokenizer(wordTokenizer);
    }

    public List<String> tokenize(String sentence)
    {
        sentence = this.getStringCleaner().cleanString(sentence);

        List<String> splitted = tokenizer.extractWords(sentence);

        splitted = reSplit2Sentence(splitted);
        splitted = reTokenizeSentence(splitted);

        return splitted;
    }

    public String[] tokenizeToArray(String sentence)
    {
        List<String> tokenized = tokenize(sentence);

        return tokenized.toArray(new String[tokenized.size()]);
    }

    private static String[] insertChars(String[] tokens, char c)
    {
        List<String> ret = new ArrayList<String>();

        ret.add(tokens[0]);

        for (int i = 1; i < tokens.length; ++i)
        {
            ret.add(String.valueOf(c));
            ret.add(tokens[i]);
        }

        return ret.toArray(new String[ret.size()]);
    }

    private List<List<String>> simpleSplit(String text)
    {
        text = this.getStringCleaner().cleanString(text);

        // text = normalizeQuotes(text);
        // text = normalizeHyphans(text);

        // text = addSpaces(text);

        List<List<String>> splitted = splitter.extractSentences(text, tokenizer);

        splittedTemp = splitted;

        splitted = reSplit1(splitted, text);
        splittedTemp = splitted;
        splitted = reSplit2(splitted);
        splittedTemp = splitted;
        splitted = reTokenize(splitted);
        splittedTemp = splitted;

        return splitted;
    }

    private List<List<String>> lineSentenceSplit(String text)
    {
        List<List<String>> splitted = new LinkedList<List<String>>();
        for (String line : text.split("\n"))
        {
            splitted.addAll(simpleSplit(line));
        }

        return splitted;
    }

    public List<List<String>> split(String text)
        throws StringIndexOutOfBoundsException
    {
        if (this.isLineSentence())
        {
            return lineSentenceSplit(text);
        }
        else
        {
            return simpleSplit(text);
        }
    }

    public int[] getSentenceOffsets(String text)
    {
        return this.getSplitter().findSentenceOffsets(text, this.split(text));
    }

    public int[] getSentenceOffsetsTemp(String text)
    {
        return this.getSplitter().findSentenceOffsets(text, splittedTemp);
    }

    public int[] getTokenOffsets(String text)
    {
        int[] sentenceOffsets = this.getSentenceOffsets(text);
        List<List<String>> splitted = this.split(text);

        int counter = 0;

        for (int i = 0; i < splitted.size(); ++i)
        {
            for (int j = 0; j < splitted.get(i).size(); ++j)
            {
                ++counter;
            }
        }

        int[] ret = new int[counter + 1];

        counter = 0;

        for (int i = 0; i < splitted.size(); ++i)
        {
            String sentence = text.substring(sentenceOffsets[i], sentenceOffsets[i + 1]);
            int[] tokenOffsets = this.getTokenizer().findWordOffsets(sentence, splitted.get(i));

            for (int j = 0; j < splitted.get(i).size(); ++j)
            {
                ret[counter] = sentenceOffsets[i] + tokenOffsets[j];
                ++counter;
            }
        }

        ret[counter] = text.length();

        return ret;
    }

    public int[] getTokenOffsetsTemp(String text)
    {
        int[] sentenceOffsets = this.getSentenceOffsetsTemp(text);

        int counter = 0;

        for (int i = 0; i < splittedTemp.size(); ++i)
        {
            for (int j = 0; j < splittedTemp.get(i).size(); ++j)
            {
                ++counter;
            }
        }

        int[] ret = new int[counter + 1];

        counter = 0;

        for (int i = 0; i < splittedTemp.size(); ++i)
        {
            String sentence = text.substring(sentenceOffsets[i], sentenceOffsets[i + 1]);
            int[] tokenOffsets = this.getTokenizer().findWordOffsets(sentence, splittedTemp.get(i));

            for (int j = 0; j < splittedTemp.get(i).size(); ++j)
            {
                ret[counter] = sentenceOffsets[i] + tokenOffsets[j];
                ++counter;
            }
        }

        ret[counter] = text.length();

        return ret;
    }

    /*
     * Separate ' 'm 's 'd 're 've 'll n't endings into apart tokens.
     */
    private static List<String> reTokenizeSentence(List<String> sentence)
    {
        for (int i = 0; i < sentence.size(); ++i)
        {
            String token = sentence.get(i);
            String tlc = token.toLowerCase();

            if (tlc.endsWith("'") && tlc.length() > 1)
            {
                sentence.set(i, token.substring(0, token.length() - 1));
                sentence.add(i + 1, token.substring(token.length() - 1));
                ++i;
            }
            if ((tlc.endsWith("'m") || tlc.endsWith("'s") || tlc.endsWith("'d")) && tlc.length() > 2)
            {
                sentence.set(i, token.substring(0, token.length() - 2));
                sentence.add(i + 1, token.substring(token.length() - 2));
                ++i;
            }
            if ((tlc.endsWith("'re") || tlc.endsWith("'ve") || tlc.endsWith("'ll") || tlc.endsWith("n't")) && tlc.length() > 3)
            {
                sentence.set(i, token.substring(0, token.length() - 3));
                sentence.add(i + 1, token.substring(token.length() - 3));
                ++i;
            }
        }

        return sentence;
    }

    private static List<List<String>> reTokenize(List<List<String>> sentences)
    {
        for (List<String> sentence : sentences)
        {
            reSplit2Sentence(sentence);
        }

        return sentences;
    }

    private List<List<String>> reSplit1(List<List<String>> sentences, String text)
    {
        int tokenNumber = 0;

        int[] tokenOffsets = getTokenOffsetsTemp(text);

        for (int i = 0; i < sentences.size(); i++)
        {
            List<String> sentence = sentences.get(i);

            // nem lehet üres mondat
            if (sentence.size() > 0)
            {
                /*
                 * 1 betűs rövidítés pl.: George W. Bush
                 */

                // utolsó token pl. (W.)
                String lastToken = sentence.get(sentence.size() - 1);
                // nem lehet üres token
                if (lastToken.length() > 0)
                {
                    // ha az utolsó karkter '.'
                    if (lastToken.charAt(lastToken.length() - 1) == '.')
                    {
                        // ha a token hossza 2 (W.)
                        if (lastToken.length() == 2)
                        {
                            // ha betű nagybetű ('W.', de 'i.' nem)
                            if (Character.isUpperCase(lastToken.charAt(lastToken.length() - 2)))
                            {
                                // ha nem az utolsó mondat
                                if (sentences.size() > i + 1)
                                {
                                    sentences.get(i).addAll(sentences.get(i + 1));
                                    sentences.remove(i + 1);
                                    // ha nem az első mondat
                                    if (i > -1)
                                    {
                                        --i;
                                    }
                                }
                            }
                        }
                    }

                    /*
                     * 2 betűs pl.: Sz.
                     */

                    if (lastToken.length() == 3)
                    {
                        // az első betű nagybetű (Sz. de 'az.' nem jó)
                        if (Character.isUpperCase(lastToken.charAt(lastToken.length() - 3)))
                        {
                            // ha nem az utolsó mondat
                            if (sentences.size() > i + 1)
                            {
                                sentences.get(i).addAll(sentences.get(i + 1));
                                sentences.remove(i + 1);
                                // ha nem az első mondat
                                if (i > -1)
                                {
                                    i--;
                                }
                            }
                        }
                    }
                }
            }

            tokenNumber += sentence.size();
            if (tokenNumber + 1 < tokenOffsets.length)
            {
                if (tokenOffsets[tokenNumber] + 1 == tokenOffsets[tokenNumber + 1])
                {
                    if ((sentences.size() > i + 1 && (i > -1)))
                    {
                        sentences.get(i).addAll(sentences.get(i + 1));
                        sentences.remove(i + 1);
                        // ha nem az első mondat
                        if (i > 0)
                        {
                            i--;
                        }
                    }
                }
            }

            if ((i < sentences.size() - 1) && (i > 0))
            {
                String firstToken = sentences.get(i + 1).get(0);

                if (ResourceHolder.getHunAbbrev().contains(firstToken.toLowerCase()))
                {
                    if (sentences.size() > i + 1)
                    {
                        sentences.get(i).addAll(sentences.get(i + 1));
                        sentences.remove(i + 1);
                        if (i > 0)
                        {
                            i--;
                        }
                    }
                }
            }
        }

        return sentences;
    }

    private static List<List<String>> reSplit2(List<List<String>> sentences)
    {
        for (List<String> sentence : sentences)
        {
            reSplit2Sentence(sentence);
        }

        return sentences;
    }

    private static List<String> reSplit2Sentence(List<String> sentence)
    {
        // nem lehet üres mondat
        if (sentence.size() > 0)
        {
            /*
             * mondtavégi írásjelek külön tokenek legyenek (.?!:;)
             */
            // utolsó token pl.: '1999.'
            String lastToken = sentence.get(sentence.size() - 1);
            // ha hosszabb mint egy karakter '9.'
            if (lastToken.length() > 1)
            {
                // utolsó karakter
                char lastChar = lastToken.charAt(lastToken.length() - 1);
                // írásjelre végződik
                if (!Character.isLetterOrDigit(lastChar))
                {
                    // írásjel levágása
                    lastToken = lastToken.substring(0, lastToken.length() - 1);
                    // utolsó token törlése
                    sentence.remove(sentence.size() - 1);
                    // új utolsó előtti token hozzáadása '1999'
                    sentence.add(sentence.size(), lastToken);
                    // új utolsó karaktertoken hozzáadása
                    sentence.add(String.valueOf(lastChar));
                }
            }
        }

        return sentence;
    }

    public boolean isLineSentence()
    {
        return lineSentence;
    }

    public void setLineSentence(boolean lineSentence)
    {
        this.lineSentence = lineSentence;
    }

    public WordTokenizer getTokenizer()
    {
        return tokenizer;
    }

    public void setTokenizer(WordTokenizer tokenizer)
    {
        this.tokenizer = tokenizer;
    }

    public SentenceSplitter getSplitter()
    {
        return splitter;
    }

    public void setSplitter(SentenceSplitter splitter)
    {
        this.splitter = splitter;
    }

    public void setStringCleaner(StringCleaner stringCleaner)
    {
        this.stringCleaner = stringCleaner;
    }

    public StringCleaner getStringCleaner()
    {
        return stringCleaner;
    }

    public String[][] splitToArray(String text)
    {
        List<List<String>> splitted = this.split(text);
        String[][] sentences = new String[splitted.size()][];

        for (int i = 0; i < sentences.length; ++i)
        {
            sentences[i] = splitted.get(i).toArray(new String[splitted.get(i).size()]);
        }

        return sentences;
    }

    /**
     * Normalizes the quote sings. Replace them to the regular " sign.
     *
     * @param text
     *          raw text
     * @return text wiht only regular " quote sings
     */
    private static String normalizeQuotes(String text)
    {
        for (char c : HunSplitter.QUOTES)
        {
            text = text.replaceAll(String.valueOf(c), String.valueOf(HunSplitter.DEFAULT_QUOTE));
        }

        return text;
    }

    /**
     * Normalizes the hyphen sings. Replace them to the regular - sign.
     *
     * @param text
     *          raw text
     * @return text wiht only regular - hyphen sings
     */
    private static String normalizeHyphans(String text)
    {
        for (char c : HunSplitter.HYPHENS)
        {
            text = text.replaceAll(String.valueOf(c), String.valueOf(HunSplitter.DEFAULT_HYPHEN));
        }

        return text;
    }

    /**
     * Add the missing space characters via the defined FORCE_TOKEN_SEPARATORS
     *
     * @param text
     *          raw text
     * @return text with added missing space cahracters
     */
    private static String addSpaces(String text)
    {
        StringBuilder sb = new StringBuilder(String.valueOf(text));

        for (char c : HunSplitter.FORCE_TOKEN_SEPARATORS)
        {
            int index = sb.indexOf(String.valueOf(c));

            while (index > 1 && index < sb.length() - 1)
            {
                if (sb.charAt(index - 1) != ' ')
                {
                    sb.insert(index + 1, ' ');
                }
                index = sb.indexOf(String.valueOf(c), index + 1);
            }
        }

        return sb.toString();
    }

    public static class StringCleaner
    {
        public static String cleanString(String text)
        {
            StringBuilder sb = new StringBuilder(text);

            for (int i = 0; i < sb.length(); ++i)
            {
                switch ((int) sb.charAt(i))
                {
                    case 11: case 12:
                    case 28: case 29: case 30: case 31:
                    case 5760:
                    case 6158:
                    case 8192: case 8193: case 8194: case 8195: case 8196: case 8197: case 8198:
                    case 8200: case 8201: case 8202: case 8203: case 8232: case 8233: case 8287:
                    case 12288:
                    case 65547: case 65564: case 65565: case 65566: case 65567:
                        sb.setCharAt(i, ' '); break;

                    case 733: sb.setCharAt(i, '"'); break;
                    case 768:
                    case 769: sb.setCharAt(i, '\''); break;
                    case 771: sb.setCharAt(i, '"'); break;
                    case 803: sb.setCharAt(i, '.'); break;
                    case 900: sb.setCharAt(i, '\''); break;
                    case 1475: sb.setCharAt(i, ':'); break;
                    case 1523: sb.setCharAt(i, '\''); break;
                    case 1524: sb.setCharAt(i, '"'); break;
                    case 1614: sb.setCharAt(i, '\''); break;
                    case 1643: sb.setCharAt(i, ','); break;
                    case 1648: sb.setCharAt(i, '\''); break;
                    case 1764: sb.setCharAt(i, '"'); break;
                    case 8211:
                    case 8212: sb.setCharAt(i, '-'); break;
                    case 8216:
                    case 8217:
                    case 8218:
                    case 8219: sb.setCharAt(i, '\''); break;
                    case 8220:
                    case 8221:
                    case 8243: sb.setCharAt(i, '"'); break;
                    case 8722: sb.setCharAt(i, '-'); break;
                    case 61448:
                    case 61449: sb.setCharAt(i, '\''); break;
                    case 61472:
                    case 61474:
                    case 61475:
                    case 61476:
                    case 61477:
                    case 61480:
                    case 61481:
                    case 61482:
                    case 61483:
                    case 61484: sb.setCharAt(i, '.'); break;
                    case 61485:
                    case 61486:
                    case 61487:
                    case 61488: sb.setCharAt(i, '"'); break;
                    case 65533: sb.setCharAt(i, '-'); break;
                }
            }

            return sb.toString();
        }

        public StringCleaner()
        {
        }
    }

    public static void main(String[] args)
    {
        HunSplitter hunSplitter = new HunSplitter(true);

        String text = "A 2014-es választások előtt túl jó lehetőséget adna az ellenzék kezébe a dohányboltok profitját nyirbáló kezdeményezés.";

        for (List<String> sentence : hunSplitter.split(text))
        {
            for (String token : sentence)
            {
                System.err.println(token);
            }
            System.err.println();
        }

        int[] sentenceOffsets = hunSplitter.getSentenceOffsets(text);

        for (int i = 0; i < sentenceOffsets.length - 1; ++i)
        {
            String sentence = text.substring(sentenceOffsets[i], sentenceOffsets[i + 1]);

            System.err.println(sentence);

            int[] tokenOffsets = hunSplitter.getTokenOffsets(sentence);
            for (int j = 0; j < tokenOffsets.length - 1; ++j)
            {
                String token = sentence.substring(tokenOffsets[j], tokenOffsets[j + 1]);
                System.err.println(token);
            }
        }
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/RFSA.java <<'EOF'
package magyarlanc;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.StringTokenizer;

public class RFSA
{
    public static class Pair<A, B>
    {
        protected A a;
        protected B b;

        public Pair(A a, B b)
        {
            this.a = a;
            this.b = b;
        }

        public A getA()
        {
            return a;
        }

        public B getB()
        {
            return b;
        }

        boolean myEq(Object o1, Object o2)
        {
            if (o1 == null)
            {
                return o2 == null;
            }

            return o1.equals(o2);
        }

        public boolean equals(Object obj)
        {
            if (!(obj instanceof Pair))
            {
                return false;
            }
            Pair p = (Pair) obj;
            return myEq(a, p.a) && myEq(b, p.b);
        }

        int myHash(Object o)
        {
            if (o == null)
            {
                return 0;
            }

            return o.hashCode();
        }

        public int hashCode()
        {
            return 31 * myHash(a) + myHash(b);
        }

        public String toString()
        {
            return "(" + a + "; " + b + ")";
        }
    }

    protected int stateCount;
    protected int edgeCount;
    protected int startingState;

    protected boolean[] ab;

    protected int[] indices;
    protected int[] targets;

    protected String[] symbols; // input char + output

    protected char[] charsymbols;

    // where we are in states, cf. addEdge
    protected int a;

    // where we are in targets
    protected int at;

    protected boolean sorted;

    protected String[] symbolhistory;

    public static interface Processor
    {
        void process(int state);
    }

    public RFSA(int startingState, int stateCount, int edgeCount)
    {
        this.startingState = startingState;
        this.stateCount = stateCount;
        this.edgeCount = edgeCount;

        ab = new boolean[stateCount];

        targets = new int[edgeCount];
        symbols = new String[edgeCount];
        charsymbols = new char[edgeCount];

        indices = new int[stateCount + 1];

        Arrays.fill(indices, -1);
        indices[stateCount] = edgeCount;
    }

    public boolean adeterministic()
    {
        int undeterministic = 0;
        int undets = 0;
        for (int s : allStates())
        {
            Map<String, Integer> labels = new HashMap<String, Integer>();
            boolean b = false;
            for (Pair<String, Integer> p : outgoing(s))
            {
                if (labels.containsKey(p.getA()))
                {
                    // System.out.println(getClass().getSimpleName() +
                    // ": not deterministic: " + s + "(" + ab[s] + "): " +
                    // p.getA() + ": " + labels.get(p.getA()) +
                    // "(" + ab[labels.get(p.getA())] + "), " +
                    // p.getB() + "(" + ab[p.getB()] + ")");
                    b = true;
                    undeterministic++;
                }
                labels.put(p.getA(), p.getB());
            }
            if (b)
            {
                undets++;
            }
        }

        return undeterministic == 0;
    }

    public boolean legal(String s)
    {
        return true;
    }

    public void binarySearch(int q, char c, Processor p)
    {
        int i = indices[q];
        int j = indices[q + 1];

        int low = i;
        int high = j - 1;
        int mid;
        while (low <= high)
        {
            mid = (low + high) >> 1;
            int cmp = charsymbols[mid] - c;

            if (cmp == 0)
            {
                int l = mid;

                while (++mid < j && charsymbols[mid] == c)
                    ;
                while (--l >= i && charsymbols[l] == c)
                    ;

                for (int next = l + 1; next < mid; next++)
                {
                    p.process(next);
                }
                break;
            }
            else if (cmp < 0)
            {
                low = mid + 1;
            }
            else if (cmp > 0)
            {
                high = mid - 1;
            }
        }
    }

    // assume sorted!
    public List<String> analyse(String s)
    {
        char[] ac = s.toLowerCase().toCharArray();
        return analyse(ac);
    }

    public List<String> analyse(char[] ac)
    {
        List<String> analyses = new ArrayList<String>();
        symbolhistory = new String[ac.length + 1];
        analyse(startingState, ac, 0, "", analyses);
        return analyses;
    }

    // binary search
    public void analyse(int q, char[] ac, int pos, String symbol, List<String> analyses)
    {
        // System.out.println(symbol);
        // System.out.println(new String(ac).substring(0,pos) + " " + q + (ab[q]?" veg":"") );
        // System.out.println(analyses);
        symbolhistory[pos] = symbol;
        if (pos == ac.length)
        {
            if (ab[q])
            {
                analyses.add(symbol/* +"@"+getMSDLemma(ac) */);
            }

            return;
        }

        char c = ac[pos];
        int i = indices[q];
        int j = indices[q + 1];

        int low = i;
        int high = j - 1;
        int mid;
        while (low <= high)
        {
            mid = (low + high) >> 1;
            int cmp = charsymbols[mid] - c;

            if (cmp == 0)
            {
                int l = mid;
                while (++mid < j && charsymbols[mid] == c)
                    ;
                while (--l >= i && charsymbols[l] == c)
                    ;

                for (int next = l + 1; next < mid; next++)
                {
                    analyse(targets[next], ac, pos + 1, symbol + symbols[next], analyses);
                }
                break;
            }
            else if (cmp < 0)
            {
                low = mid + 1;
            }
            else if (cmp > 0)
            {
                high = mid - 1;
            }
        }
    }

    // linear search
    public void analyse1(int q, char[] ac, int pos, String symbol, List<String> analyses)
    {
        if (pos == ac.length)
        {
            if (ab[q])
            {
                analyses.add(symbol);
            }

            return;
        }

        char c = ac[pos];
        int i = indices[q];
        int j = indices[q + 1];

        for (int next = i; next < j; next++)
        {
            if (c == charsymbols[next])
            {
                analyse(targets[next], ac, pos + 1, symbol + symbols[next], analyses);
            }
        }
    }

    public void addState(int s, boolean accepting)
    {
        ab[s] = accepting;
    }

    public void addEdge(int source, String label, int target)
    {
        if (source < a)
        {
            throw new IllegalArgumentException();
        }

        if (indices[source] == -1)
        {
            indices[source] = at;
        }

        char input = label.charAt(0);
        charsymbols[at] = input;
        symbols[at] = label.substring(1);
        targets[at] = target;
        a = source;
        at++;
    }

    public String getKRLemma(String symbol)
    {
        String KR_szoto = "";
        for (String morph : symbol.split("\\+"))
        {
            int s = (morph.startsWith("$")) ? 1 : 0;
            int ppp = morph.indexOf('/');
            if (ppp < 0)
                KR_szoto += morph.substring(s);
            else
                KR_szoto += morph.substring(s, ppp);
        }

        KR_szoto = KR_szoto.replace("@", "");
        return KR_szoto;
    }

    protected String getLastPOS(String symbol, String pos)
    {
        if (symbol.contains(pos))
            return symbol.substring(0, symbol.indexOf(pos)) + pos.substring(0, pos.indexOf("["));

        return symbol;
    }

    protected String getMSDLemma(char[] ac)
    {
        String symbol = symbolhistory[symbolhistory.length - 1];
        String POS = symbol;
        POS = getLastPOS(POS, "/ADJ[COMPAR]");
        POS = getLastPOS(POS, "/ADJ[SUPERLAT]");
        POS = getLastPOS(POS, "/ADJ[SUPERSUPERLAT]");
        POS = getLastPOS(POS, "/ADJ[MANNER]");
        POS = getLastPOS(POS, "/NOUN[ESS_FOR]");
        int p = POS.lastIndexOf('/');
        int pp = POS.indexOf('<', p);
        if (pp > 0)
            POS = symbol.substring(0, pp);

        int i = 0;
        while (!(symbolhistory[i].startsWith(POS) || (!symbolhistory[i].contains("/") && symbolhistory[i].equals(POS.substring(0, p)))))
        {
            ++i;
        }

        String szoalak_szoto = new String(ac).substring(0, i);

        // leg...
        if (symbol.contains("/ADJ[SUPERLAT]"))
        {
            szoalak_szoto = new String(ac).substring(3, i);
        }

        // legesleg...
        if ((symbol.contains("/ADJ[SUPERSUPERLAT]")) && szoalak_szoto.startsWith("legesleg"))
        {
            szoalak_szoto = new String(ac).substring(8, i);
        }

        String KR_szoto = getKRLemma(symbolhistory[i]);
        String MSDszoto = KR_szoto.length() >= szoalak_szoto.length() ? KR_szoto : szoalak_szoto;
        return MSDszoto;
    }

    public void noedge(int source)
    {
        if (source < a)
        {
            throw new IllegalArgumentException();
        }
        indices[source] = at;
        a = source;
    }

    public void setAccepting(int state, boolean b)
    {
        ab[state] = b;
    }

    public int startingState()
    {
        return startingState;
    }

    public Iterable<Integer> allStates()
    {
        return new StateIterator(stateCount);
    }

    public boolean isAccepting(int state)
    {
        return ab[state];
    }

    public Iterable<Pair<String, Integer>> outgoing(int state)
    {
        return new EdgeIterable(state);
    }

    // edges of state s are enlisted in
    // [targets[indices[i]], targets[indices[i+1]])
    public int size(int s)
    {
        if (s >= stateCount)
        {
            throw new IllegalArgumentException(s + " >= " + stateCount);
        }

        return indices[s + 1] - indices[s];
    }

    public int stateCount()
    {
        return stateCount;
    }

    public String toString()
    {
        return getClass().getSimpleName() + "[" + stateCount + ", " + edgeCount + "]";
    }

    public String toDetailedString()
    {
        StringBuilder sb = new StringBuilder("  " + stateCount + ", " + edgeCount + ", " + startingState + "\n");

        for (int i = 0; i < stateCount; i++)
        {
            sb.append("    ").append(i).append(", ").append(ab[i]).append(", ").append(indices[i + 1] - indices[i]).append('\n');
            for (int j = indices[i]; j < indices[i + 1]; j++)
            {
                sb.append("      ").append(targets[j]).append(": >").append(charsymbols[j]).append("|").append(symbols[j]).append("<\n");
            }
        }

        return sb.toString();
    }

    public class EdgeIter implements Iterator<Pair<String, Integer>>
    {
        protected int state;
        protected int size;
        protected int start;
        protected int next;

        public EdgeIter(int state)
        {
            this.state = state;
            size = size(state);
            start = indices[state];
            next = start;
        }

        public Pair<String, Integer> next()
        {
            if (!hasNext())
            {
                throw new NoSuchElementException();
            }
            int target = targets[next];
            String label = charsymbols[next] + symbols[next];
            next++;
            return new Pair<String, Integer>(label, target);
        }

        public boolean hasNext()
        {
            return next < start + size;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }

    public class EdgeIterable extends EdgeIter implements Iterable<Pair<String, Integer>>, Iterator<Pair<String, Integer>>
    {
        public EdgeIterable(int state)
        {
            super(state);
        }

        public Iterator<Pair<String, Integer>> iterator()
        {
            return new EdgeIterable(state);
        }

        public boolean hasNext()
        {
            return next < start + size;
        }
    }

    protected Sorter createSorter(int state)
    {
        return new Sorter(state);
    }

    public void sort()
    {
        sorted = true;
        for (int state = 0; state < stateCount; state++)
        {
            if (indices[state] == indices[state + 1])
            {
                continue;
            }

            Sorter sorter = createSorter(state);
            sorter.sort();
        }
    }

    public class Sorter
    {
        protected int state;
        protected int length;

        public Sorter(int state)
        {
            this.state = state;
        }

        public void sort()
        {
            length = indices[state + 1] - indices[state];

            String[] as = new String[length];
            char[] ac = new char[length];
            int[] at = new int[length];

            System.arraycopy(charsymbols, indices[state], ac, 0, length);
            System.arraycopy(symbols, indices[state], as, 0, length);
            System.arraycopy(targets, indices[state], at, 0, length);

            Integer[] ai = new Integer[length];
            for (int i = 0; i < length; i++)
            {
                ai[i] = i + indices[state];
            }

            Arrays.sort(ai, new Comparator<Integer>()
            {
                public int compare(Integer arg0, Integer arg1)
                {
                    return charsymbols[arg0] - charsymbols[arg1];
                }
            });

            for (int i = 0; i < length; i++)
            {
                int j = ai[i] - indices[state];
                charsymbols[i + indices[state]] = ac[j];
                symbols[i + indices[state]] = as[j];
                targets[i + indices[state]] = at[j];
            }
        }
    }

    public int valid()
    {
        Set<Integer> valid = new HashSet<Integer>();
        for (int i = 0; i < stateCount; i++)
        {
            if (ab[i])
            {
                valid.add(i);
            }
        }

        System.out.println(getClass().getSimpleName() + ": valid starts with " + valid.size() + " accepting states");

        int size;
        do
        {
            size = valid.size();
            for (int i = 0; i < stateCount; i++)
            {
                if (valid.contains(i))
                {
                    continue;
                }
                for (int j = indices[i]; j < indices[i + 1]; j++)
                {
                    if (valid.contains(targets[j]))
                    {
                        valid.add(i);
                        break;
                    }
                }
            }
        } while (valid.size() != size);

        return valid.size();
    }

    public void prints(String file)
        throws IOException
    {
        PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

        pw.println(startingState + "\t" + stateCount + "\t" + edgeCount);

        for (int i = 0; i < stateCount; i++)
        {
            pw.println(i + "\t" + ab[i]);
            pw.println(indices[i + 1] - indices[i]);
            for (int j = indices[i]; j < indices[i + 1]; j++)
            {
                pw.println(charsymbols[j] + symbols[j] + "\t" + targets[j]);
            }
        }
        pw.close();
    }

    public int getA()
    {
        return a;
    }

    public boolean[] getAb()
    {
        return ab;
    }

    public int getAt()
    {
        return at;
    }

    public char[] getCharsymbols()
    {
        return charsymbols;
    }

    public int getEdgeCount()
    {
        return edgeCount;
    }

    public int[] getIndices()
    {
        return indices;
    }

    public boolean isSorted()
    {
        return sorted;
    }

    public int getStartingState()
    {
        return startingState;
    }

    public int getStateCount()
    {
        return stateCount;
    }

    public String[] getSymbols()
    {
        return symbols;
    }

    public int[] getTargets()
    {
        return targets;
    }

    public static RFSA read(String file)
        throws IOException
    {
        return read(new FileInputStream(file));
    }

    public static RFSA read(InputStream rfsaStream)
        throws IOException
    {
        Map<String, String> labelMap = new HashMap<String, String>();

        LineNumberReader reader = new LineNumberReader(new InputStreamReader(rfsaStream, "UTF-8"));

        String line = reader.readLine();
        StringTokenizer st = new StringTokenizer(line);

        int startIndex = Integer.parseInt(st.nextToken());
        int stateCount = Integer.parseInt(st.nextToken());
        int edgeCount = Integer.parseInt(st.nextToken());

        RFSA rfsa = new RFSA(startIndex, stateCount, edgeCount);
        for (int i = 0; i < stateCount; i++)
        {
            // state line with state number and accepting
            line = reader.readLine();
            st = new StringTokenizer(line, "\t");
            int state = Integer.parseInt(st.nextToken());
            boolean accepting = new Boolean(st.nextToken());

            rfsa.addState(state, accepting);

            // line with edgecount
            line = reader.readLine();
            st = new StringTokenizer(line);
            int edges = Integer.parseInt(st.nextToken());
            if (edges == 0)
            {
                rfsa.noedge(state);
            }

            // lines with edges
            for (int j = 0; j < edges; j++)
            {
                line = reader.readLine();
                int index = line.indexOf('\t');
                String s = line.substring(0, index);
                if (s.length() == 0)
                {
                    throw new IllegalStateException();
                }
                int target = Integer.parseInt(line.substring(index + 1));
                String label = labelMap.get(s);
                if (label == null)
                {
                    labelMap.put(s, label = s);
                }
                rfsa.addEdge(state, label, target);
            }
        }
        reader.close();
        rfsa.sort();
        return rfsa;
    }

    public static class StateIterator implements Iterable<Integer>, Iterator<Integer>
    {
        protected int size;
        protected int next;

        public StateIterator(int size)
        {
            this.size = size;
        }

        public Iterator<Integer> iterator()
        {
            return new StateIterator(size);
        }

        public boolean hasNext()
        {
            return next < size;
        }

        public Integer next()
        {
            if (!hasNext())
            {
                throw new NoSuchElementException();
            }

            return next++;
        }

        public void remove()
        {
            throw new UnsupportedOperationException();
        }
    }
}
EOF
