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
