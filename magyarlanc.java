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

import java.util.ArrayList;
import java.util.List;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.HasWord;
import edu.stanford.nlp.ling.TaggedWord;

import magyarlanc.Morphology;

public class SzteMaxentTagger extends MaxentTagger
{
    public SzteMaxentTagger(String model)
        throws RuntimeIOException
    {
        this(model, new TaggerConfig("-model", model, "-verbose", "false"));
    }

    public SzteMaxentTagger(String model, TaggerConfig config)
        throws RuntimeIOException
    {
        super(model, config, false);
    }

    public ArrayList<TaggedWord> apply(List<? extends HasWord> in)
    {
        return new TestSentence(this)
        {
            protected String[] stringTagsAt(int pos)
            {
                int left = this.leftWindow();

                if (pos < left || size + left <= pos)
                {
                    return new String[] { this.naTag };
                }

                String[] tags;

                String word = this.sent.get(pos - left);

                if (this.maxentTagger.dict.isUnknown(word))
                {
                    tags = Morphology.getPossibleTags(word, this.maxentTagger.tags.getOpenTags());
                }
                else
                {
                    tags = this.maxentTagger.dict.getTags(word);
                }

                return this.maxentTagger.tags.deterministicallyExpandTags(tags);
            }
        }.tagSentence(in, false);
    }

    public void setVerbose(boolean verbose)
    {
        super.VERBOSE = verbose;
        super.config.setProperty("verbose", verbose ? "true" : "false");
    }

    public String[][] morphSentence(String[] forms)
    {
        String[][] morph = new String[forms.length][3];

        List<TaggedWord> sentence = new ArrayList<TaggedWord>();

        for (String form : forms)
        {
            TaggedWord tw = new TaggedWord();
            tw.setWord(form);
            sentence.add(tw);
        }

        sentence = this.apply(sentence);

        sentence = Morphology.recoverTags(sentence);

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
mkdir -p magyarlanc && cat > magyarlanc/Dependency.java <<'EOF'
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
    private static JTextArea textarea;
    private static JLabel imageLabel;

    private static String _sentenceAsString(String[][] array)
    {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < array.length; i++)
        {
            for (int j = 0; j < array[i].length; j++)
            {
                sb.append(array[i][j]).append('\t');
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private static void _moveToCenter(Component component)
    {
        component.setLocation(
            (int) ((Toolkit.getDefaultToolkit().getScreenSize().getWidth() - component.getPreferredSize().getWidth()) / 2),
            (int) ((Toolkit.getDefaultToolkit().getScreenSize().getHeight() - component.getPreferredSize().getHeight()) / 2));
    }

    public static void init()
    {
        JFrame frame = new JFrame("magyarlanc 2.0");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setLayout(new BorderLayout());

        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.X_AXIS));
        inputPanel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JTextField inputField = new JTextField(
            "Nehéz lesz megszokni a sok üres épületet, de a kínai áruházak hamar pezsgővé változtathatják a szellemházakat.");

        JButton sendButton = new JButton("OK");
        sendButton.addActionListener(new ActionListener()
        {
            public void actionPerformed(ActionEvent actionEvent)
            {
                if (inputField.getText() != null && !inputField.getText().equals(""))
                {
                    String[] sentence = HunSplitter.splitToArray(inputField.getText())[0];

                    String[][] depParsed = Dependency.depParseSentence(sentence);

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

                    if (textarea != null)
                        textarea.setVisible(false);

                    textarea = new JTextArea();
                    textarea.setText(_sentenceAsString(depParsed));
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
        Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();

        frame.setPreferredSize(new Dimension((int) dim.getWidth() - 150, (int) dim.getHeight() - 150));
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
mkdir -p magyarlanc && cat > magyarlanc/HunSplitter.java <<'EOF'
package magyarlanc;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import edu.northwestern.at.morphadorner.corpuslinguistics.sentencesplitter.DefaultSentenceSplitter;
import edu.northwestern.at.morphadorner.corpuslinguistics.sentencesplitter.SentenceSplitter;
import edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer.DefaultWordTokenizer;
import edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer.WordTokenizer;

import data.Data;

public class HunSplitter
{
    public static final char[] HYPHENS = new char[] { '-', '­', '–', '—', '―', '−', '─' };
    public static final char DEFAULT_HYPHEN = '-';

    public static final char[] QUOTES = new char[] { '"', '\'', '`', '´', '‘', '’', '“', '”', '„' };
    public static final char DEFAULT_QUOTE = '"';

    public static final char[] FORCE_TOKEN_SEPARATORS = new char[] { ',', '.', ':' };

    private static SentenceSplitter splitter = new DefaultSentenceSplitter();
    private static WordTokenizer tokenizer = new DefaultWordTokenizer();

    public static List<String> tokenize(String sentence)
    {
        sentence = cleanString(sentence);

        List<String> splitted = tokenizer.extractWords(sentence);

        splitted = reSplit2Sentence(splitted);
        splitted = reTokenizeSentence(splitted);

        return splitted;
    }

    public static String[] tokenizeToArray(String sentence)
    {
        List<String> tokenized = tokenize(sentence);

        return tokenized.toArray(new String[tokenized.size()]);
    }

    public static List<List<String>> split(String text)
    {
        text = cleanString(text);

        // text = normalizeQuotes(text);
        // text = normalizeHyphans(text);

        // text = addSpaces(text);

        List<List<String>> splitted = splitter.extractSentences(text, tokenizer);

        splitted = reSplit1(splitted, text);
        splitted = reSplit2(splitted);
        splitted = reTokenize(splitted);

        return splitted;
    }

    public static final int[] getSentenceOffsets(String text)
    {
        return getSentenceOffsets(text, null);
    }

    public static int[] getSentenceOffsets(String text, List<List<String>> splitted)
    {
        if (splitted == null)
            splitted = split(text);

        return splitter.findSentenceOffsets(text, splitted);
    }

    public static final int[] getTokenOffsets(String text)
    {
        return getTokenOffsets(text, null);
    }

    public static int[] getTokenOffsets(String text, List<List<String>> splitted)
    {
        if (splitted == null)
            splitted = split(text);

        int[] sentenceOffsets = getSentenceOffsets(text, splitted);

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
            int[] tokenOffsets = tokenizer.findWordOffsets(sentence, splitted.get(i));

            for (int j = 0; j < splitted.get(i).size(); ++j)
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

    private static List<List<String>> reSplit1(List<List<String>> sentences, String text)
    {
        int tokenNumber = 0;

        int[] tokenOffsets = getTokenOffsets(text, sentences);

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

                if (getHunAbbrev().contains(firstToken.toLowerCase()))
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
             * mondatvégi írásjelek külön tokenek legyenek (.?!:;)
             */

            // utolsó token pl.: '1999.'
            String lastToken = sentence.get(sentence.size() - 1);

            // ha hosszabb, mint egy karakter '9.'
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

    public static String[][] splitToArray(String text)
    {
        List<List<String>> splitted = split(text);
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
        for (char c : QUOTES)
        {
            text = text.replaceAll(String.valueOf(c), String.valueOf(DEFAULT_QUOTE));
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
        for (char c : HYPHENS)
        {
            text = text.replaceAll(String.valueOf(c), String.valueOf(DEFAULT_HYPHEN));
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

        for (char c : FORCE_TOKEN_SEPARATORS)
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

    private static Set<String> stopwords;

    public static Set<String> getStopwords()
    {
        if (stopwords == null)
            stopwords = readSet("stopwords.txt");

        return stopwords;
    }

    private static Set<String> hunAbbrev;

    public static Set<String> getHunAbbrev()
    {
        if (hunAbbrev == null)
            hunAbbrev = readSet("hun_abbrev.txt");

        return hunAbbrev;
    }

    public static Set<String> readSet(String file)
    {
        Set<String> set = new TreeSet<String>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Data.class.getResourceAsStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                set.add(line);
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return set;
    }

    public static void main(String[] args)
    {
        String text = "A 2014-es választások előtt túl jó lehetőséget adna az ellenzék kezébe a dohányboltok profitját nyirbáló kezdeményezés.";

        for (List<String> sentence : split(text))
        {
            for (String token : sentence)
            {
                System.out.println(token);
            }
            System.out.println();
        }

        int[] sentenceOffsets = getSentenceOffsets(text);

        for (int i = 0; i < sentenceOffsets.length - 1; ++i)
        {
            String sentence = text.substring(sentenceOffsets[i], sentenceOffsets[i + 1]);

            System.out.println(sentence);

            int[] tokenOffsets = getTokenOffsets(sentence);
            for (int j = 0; j < tokenOffsets.length - 1; ++j)
            {
                String token = sentence.substring(tokenOffsets[j], tokenOffsets[j + 1]);
                System.out.println(token);
            }
        }
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/KRTools.java <<'EOF'
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class Magyarlanc
{
    public static List<String> readList(String file)
    {
        List<String> lines = new LinkedList<>();

        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader((file != null) ? new FileInputStream(file) : System.in, "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                line = line.trim();

                if (0 < line.length())
                {
                    lines.add(line);
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

    /**
     * Reads tokenized file. Each line contains exactly one token.
     */
    public static String[][] readTokenized(String file)
    {
        List<String[]> sentences = new ArrayList<String[]>(1 << 5);

        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader((file != null) ? new FileInputStream(file) : System.in, "UTF-8"));

            List<String> tokens = new ArrayList<String>(1 << 5);

            for (String line; (line = reader.readLine()) != null; )
            {
                line = line.trim();

                if (0 < line.length())
                {
                    tokens.add(line);
                }
                else
                {
                    sentences.add(tokens.toArray(new String[tokens.size()]));
                    tokens.clear();
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

    public static void write(String[][][] array, String file)
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter((file != null) ? new FileOutputStream(file) : System.out, "UTF-8"));

            for (int i = 0; i < array.length; i++)
            {
                for (int j = 0; j < array[i].length; j++)
                {
                    for (int k = 0; k < array[i][j].length; k++)
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

    public static void main(String[] args)
    {
        final String usage = "usage: -mode gui|morana|morphparse|tokenized|depparse";

        if (args.length < 2)
        {
            System.err.println(usage);
            System.exit(1);
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
                System.err.println(usage);
                System.exit(2);
            }
        }

        if (params.containsKey("-mode"))
        {
            switch (params.get("-mode"))
            {
                case "gui":
                    GUI.init();
                    break;

                case "morana":
                {
                    List<String> lines = readList(params.get("-input"));

                    for (String line : lines)
                        System.out.println(Morphology.getMorphologicalAnalyses(line));
                    break;
                }

                case "morphparse":
                {
                    List<String> lines = readList(params.get("-input"));

                    write(Morphology.morphParse(lines), params.get("-output"));
                    break;
                }

                case "tokenized":
                {
                    String[][] lines = readTokenized(params.get("-input"));

                    write(Morphology.morphParse(lines), params.get("-output"));
                    break;
                }

                case "depparse":
                {
                    List<String> lines = readList(params.get("-input"));

                    write(Dependency.depParse(lines), params.get("-output"));
                    break;
                }

                default:
                    System.err.println(usage);
                    System.exit(3);
            }
        }

        // System.exit(0);
    }
}
EOF
mkdir -p magyarlanc && cat > magyarlanc/Morphology.java <<'EOF'
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
EOF
mkdir -p magyarlanc && cat > magyarlanc/MSDTools.java <<'EOF'
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
EOF
mkdir -p magyarlanc && cat > magyarlanc/RFSA.java <<'EOF'
package magyarlanc;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.StringTokenizer;

import data.Data;

public class RFSA
{
    private static RFSA rfsa;

    public static List<String> analyse(String word)
    {
        if (rfsa == null)
        {
            try
            {
                rfsa = read(Data.class.getResourceAsStream("rfsa.txt"));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        List<String> ans = new ArrayList<String>();

        rfsa.analyse(rfsa.startingState, word, 0, "", ans);

        return ans;
    }

    private int startingState, stateCount, edgeCount;

    private boolean[] accepts;

    private char[] chars;
    private String[] symbols;
    private int[] targets;

    private int[] indices;

    public RFSA(int startingState, int stateCount, int edgeCount)
    {
        this.startingState = startingState;
        this.stateCount = stateCount;
        this.edgeCount = edgeCount;

        accepts = new boolean[stateCount];

        chars = new char[edgeCount];
        symbols = new String[edgeCount];
        targets = new int[edgeCount];

        indices = new int[stateCount + 1];
        Arrays.fill(indices, -1);
        indices[stateCount] = edgeCount;
    }

    // binary search
    private void analyse(int state, String word, int pos, String symbol, List<String> ans)
    {
        if (pos == word.length())
        {
            if (accepts[state])
                ans.add(symbol);

            return;
        }

        char ch = Character.toLowerCase(word.charAt(pos));

        int from = indices[state], over = indices[state + 1];

        for (int low = from, high = over - 1; low <= high; )
        {
            int mid = (low + high) >> 1;
            int cmp = chars[mid] - ch;

            if (cmp == 0)
            {
                int n = mid;
                while (++mid < over && chars[mid] == ch)
                    ;
                while (from <= --n && chars[n] == ch)
                    ;

                for (++n; n < mid; n++)
                    analyse(targets[n], word, pos + 1, symbol + symbols[n], ans);

                return;
            }

            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
        }
    }

    private void addState(int state, boolean accept)
    {
        accepts[state] = accept;
    }

    private void addEdge(int state, String label, int target, int ti)
    {
        if (indices[state] == -1)
            indices[state] = ti;

        chars[ti] = label.charAt(0);
        symbols[ti] = label.substring(1);
        targets[ti] = target;
    }

    private void noEdge(int state, int ti)
    {
        indices[state] = ti;
    }

    private RFSA sort()
    {
        for (int state = 0; state < stateCount; state++)
        {
            if (indices[state] == indices[state + 1])
                continue;

            sort(state);
        }

        return this;
    }

    private void sort(int state)
    {
        int length = indices[state + 1] - indices[state];

        char[] ac = new char[length];
        String[] as = new String[length];
        int[] at = new int[length];

        System.arraycopy(chars, indices[state], ac, 0, length);
        System.arraycopy(symbols, indices[state], as, 0, length);
        System.arraycopy(targets, indices[state], at, 0, length);

        Integer[] ai = new Integer[length];
        for (int i = 0; i < length; i++)
        {
            ai[i] = i + indices[state];
        }

        Arrays.sort(ai, new Comparator<Integer>()
        {
            public int compare(Integer i0, Integer i1)
            {
                return chars[i0] - chars[i1];
            }
        });

        for (int i = 0; i < length; i++)
        {
            int n = indices[state], m = ai[i] - n;

            chars[i + n] = ac[m];
            symbols[i + n] = as[m];
            targets[i + n] = at[m];
        }
    }

    private static RFSA read(InputStream is)
        throws IOException
    {
        LineNumberReader reader = new LineNumberReader(new InputStreamReader(is, "UTF-8"));

        StringTokenizer st = new StringTokenizer(reader.readLine());

        int startingState = Integer.parseInt(st.nextToken());
        int stateCount = Integer.parseInt(st.nextToken());
        int edgeCount = Integer.parseInt(st.nextToken());

        RFSA rfsa = new RFSA(startingState, stateCount, edgeCount);

        int si = 0; // where we are in states
        int ti = 0; // where we are in targets

        for (int i = 0; i < stateCount; i++)
        {
            // state line with state number and accepting
            st = new StringTokenizer(reader.readLine(), "\t");
            int state = Integer.parseInt(st.nextToken());
            boolean accept = new Boolean(st.nextToken());

            if (state < si)
                throw new IllegalArgumentException();

            rfsa.addState(state, accept);

            si = state;

            // line with edgecount
            st = new StringTokenizer(reader.readLine());
            int edges = Integer.parseInt(st.nextToken());
            if (edges == 0)
            {
                rfsa.noEdge(state, ti);
                continue;
            }

            // lines with edges
            for (int j = 0; j < edges; j++)
            {
                String line = reader.readLine();
                int tab = line.indexOf('\t');
                if (tab == 0)
                    throw new IllegalStateException();

                String label = line.substring(0, tab);
                int target = Integer.parseInt(line.substring(tab + 1));

                rfsa.addEdge(state, label, target, ti);

                ti++;
            }
        }

        reader.close();

        return rfsa.sort();
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
    private static SingleSentenceRenderer renderer;
    private static CoNLL2009 coNLL2009;

    /**
     * converts the magyarlanc output to CoNLL2009 format
     *
     * @param array
     *          magyarlanc output; two dimensional array,
     *          which contains the tokens of the a parsed sentence
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
     * Builds a buffered image from the given NLPInstance via the SentenceRenderer.
     *
     * @param instance
     * @return
     */
    private static BufferedImage createImage(NLPInstance instance)
    {
        if (renderer == null)
            renderer = new SingleSentenceRenderer();

        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_4BYTE_ABGR);
        Graphics2D g = image.createGraphics();
        Dimension dim = renderer.render(instance, g);

        image = new BufferedImage((int) dim.getWidth() + 5, (int) dim.getHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        g = image.createGraphics();
        renderer.render(instance, g);

        return image;
    }

    /**
     * Exports the given sentence to the specified PNG imgage.
     *
     * @param sentence
     *          dep. parsed sentence (magyarlanc output)
     * @param file
     *          the PNG
     */
    public static void exportToPNG(String[][] sentence, String file)
    {
        if (coNLL2009 == null)
            coNLL2009 = new CoNLL2009();

        try
        {
            FileOutputStream fos = new FileOutputStream(file);
            ImageIO.write(createImage(coNLL2009.create(arrayToList(sentence))), "PNG", fos);
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
            ImageIO.write(createImage(coNLL2009.create(arrayToList(sentence))), "PNG", baos);
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
        String[][] depParsedSentence =
        {
            { "1", "A", "a", "Rx", "R", "SubPOS=x|Deg=none", "2", "DET" },
            { "2", "ház", "ház", "Rx", "R", "SubPOS=x|Deg=none", "3", "ROOT-VAN-SUBJ" },
            { "3", "nagy", "nagy", "Afp-sn", "A", "SubPOS=f|Deg=p|Num=s|Cas=n|NumP=none|PerP=none|NumPd=none", "0", "ROOT-VAN-PRED" },
            { "4", ".", ".", ".", ".", "_", "0", "PUNCT" }
        };

        exportToPNG(depParsedSentence, "whatswrong.png");
    }
}
EOF
