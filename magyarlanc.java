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

import szte.pos.morphology.HungarianMorphology;

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

        sentence = (ArrayList<TaggedWord>) HungarianMorphology.recoverTags(sentence);

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

import szte.pos.morphology.HungarianMorphology;

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
            tags = HungarianMorphology.getPossibleTags(word, maxentTagger.tags.getOpenTags());
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
mkdir -p szte/converter/nooj && cat > szte/converter/nooj/Dep2Nooj.java <<'EOF'
package szte.converter.nooj;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class Dep2Nooj
{
    private static Set<String> getGovs(int index, String[][] sentence)
    {
        Set<String> govs = new TreeSet<String>();

        for (String[] token : sentence)
        {
            if (Integer.parseInt(token[6]) == index)
            {
                if (!token[7].equals("ROOT"))
                {
                    govs.add(token[7]);
                }
            }
        }

        return govs;
    }

    private static String convertSentence(String[][] sentence)
    {
        Map<String, Integer> map = new TreeMap<String, Integer>();

        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < sentence.length; ++i)
        {
            String[] token = sentence[i];

            sb.append("<LU LEMMA=\"");
            sb.append(token[2]).append("\" ");
            sb.append("CAT=\"");
            sb.append(token[4]).append("\" ");
            sb.append("POS").append(token[3]);

            for (String gov : getGovs(i + 1, sentence))
            {
                String templabel = gov + "GOV";
                if (!map.containsKey(templabel))
                {
                    map.put(templabel, 0);
                }
                map.put(templabel, map.get(templabel) + 1);
                int counter = map.get(templabel);
                sb.append(" ").append(gov).append(counter).append("GOV");
            }

            if (!token[7].equals("ROOT"))
            {
                String templabel = token[7] + "DEP";
                if (!map.containsKey(templabel))
                {
                    map.put(templabel, 0);
                }
                map.put(templabel, map.get(templabel) + 1);
                int counter = map.get(templabel);
                sb.append(" ").append(token[7]).append(counter).append("DEP");
            }

            sb.append(">").append(token[1]).append("</LU> ");
        }

        return sb.toString();
    }

    private static String[][][] read(String file)
    {
        List<String[][]> document = new ArrayList<String[][]>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            List<String[]> sentence = new ArrayList<String[]>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.trim().length() == 0)
                {
                    document.add(sentence.toArray(new String[sentence.size()][]));
                    sentence = new ArrayList<String[]>();
                }
                else
                {
                    sentence.add(line.split("\t"));
                }
            }

            reader.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return document.toArray(new String[document.size()][][]);
    }

    public static void convert(String[][][] sentences, String outFile)
    {
        Writer writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"));

            writer.write("<doc>\n");
            for (String[][] sentence : sentences)
            {
                writer.write(convertSentence(sentence));
                writer.write('\n');
            }
            writer.write("</doc>");

            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            try
            {
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void convert(String file, String outFile)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outFile), "UTF-8"));

            for (String[][] s : read(file))
            {
                writer.write(convertSentence(s));
                writer.write('\n');
            }

            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
EOF
mkdir -p szte/converter/webcorpus && cat > szte/converter/webcorpus/Conll2007To2009.java <<'EOF'
package szte.converter.webcorpus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;

import szte.pos.converter.MSDToCoNLLFeatures;

public class Conll2007To2009
{
    private static final MSDToCoNLLFeatures msdToConllFeatures = new MSDToCoNLLFeatures();

    public static void convert(String in, String out)
    {
        BufferedReader reader = null;
        Writer writer = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(in), "UTF-8"));
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.trim().length() == 0)
                {
                    writer.write('\n');
                }
                else
                {
                    String[] split = line.split("\t");

                    String num = split[0],
                      wordform = split[1],
                         lemma = split[2],
                           msd = split[3],
                        parent = split[6],
                        deprel = split[7];

                    StringBuilder sb = new StringBuilder();

                    if (msd.equals("VAN") || msd.equals("ELL"))
                    {
                        sb.append(num).append("\t_\t_\t_\t").append(msd).append('\t').append(msd).append("\t_\t_\t");
                    }
                    else
                    {
                        if (msd.equals("null"))
                            msd = wordform;

                        sb.append(num).append('\t').append(wordform)
                                      .append('\t').append(lemma)
                                      .append('\t').append(lemma)
                                      .append('\t').append(msd.charAt(0))
                                      .append('\t').append(msd.charAt(0))
                                      .append('\t').append(msdToConllFeatures.convert(lemma, msd))
                                      .append('\t').append(msdToConllFeatures.convert(lemma, msd))
                                      .append('\t');
                    }

                    sb.append(parent).append('\t').append(parent)
                                     .append('\t').append(deprel)
                                     .append('\t').append(deprel)
                                     .append('\n');

                    writer.write(sb.toString());
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args)
    {
        // convert("./data/webcorpus/facebook.conll", "./data/webcorpus/facebook.conll-2009");
        // convert("./data/webcorpus/faq.conll", "./data/webcorpus/faq.conll-2009");
        // convert("./data/webcorpus/web.conll", "./data/webcorpus/web.conll-2009");

        // convert("./data/webcorpus_1222/face_1222.conll", "./data/webcorpus_1222/face_1222.conll-2009");
        // convert("./data/webcorpus_1222/faq_1222.conll", "./data/webcorpus_1222/faq_1222.conll-2009");
        // convert("./data/webcorpus_1222/web_1222.conll", "./data/webcorpus_1222/web_1222.conll-2009");
    }
}
EOF
mkdir -p szte/converter/webcorpus && cat > szte/converter/webcorpus/Converter.java <<'EOF'
package szte.converter.webcorpus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import szte.magyarlanc.MorAna;
import szte.pos.converter.MSDReducer;

public class Converter
{
    private static final MSDReducer MSD_REDUCER = new MSDReducer();

    private static final Set<String> PUNCT = new HashSet<String>(Arrays.asList(new String[] { "!", ",", "-", ".", ":", ";", "?", "�" }));

    private static final String[] CORPUSES = { "faq1", "faq2", "faq3", "faq4", "face1", "face2", "face3", "face4", };

    private static final double DIVISION = 0.8;

    private static final String WORDFORM_LEMMA_SEPARATOR = "\t";
    private static final String LEMMA_MSD_SEPARATOR = "\t";

    private static final String STANFORD_TRAIN_WORDFORM_MSD_SEPARATOR = "@";

    private static final String STANFORD_TRAIN_TOKEN_SEPARATOR = " ";

    private static final String CLOSED_TAGS = "! , - . : ; ? Cccp Cccw Ccsp Ccsw Cscp Cssp Cssw S T Z";

    // full lex (no train/test parts)
    private static final Map<String, Set<MorAna>> FULL_LEX = new TreeMap<String, Set<MorAna>>();

    /**
     * Get all nodes from the document by the given tag name.
     *
     * @param document
     * @param tagName
     * @param type
     * @return
     */
    public static List<Node> getNodes(Document document, String tagName, String type)
    {
        NodeList nodeList = document.getElementsByTagName(tagName);

        List<Node> nodes = new LinkedList<Node>();

        for (int i = 0; i < nodeList.getLength(); ++i)
        {
            Node node = nodeList.item(i);

            if (node.getAttributes().getNamedItem("type") != null)
            {
                if (node.getAttributes().getNamedItem("type").getTextContent().equals(type))
                {
                    nodes.add(node);
                }
            }
        }

        return nodes;
    }

    public static String getLemma(Node node)
    {
        return getNodes(getNodes(node, "msd").get(0), "lemma").get(0).getTextContent();
    }

    public static String getMsd(Node node)
    {
        String msd = getNodes(getNodes(node, "msd").get(0), "mscat").get(0).getTextContent();

        return msd.substring(1, msd.length() - 1);
    }

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

    public static String wToTrain(Node node)
    {
        StringBuilder sb = new StringBuilder();

        String spelling = node.getChildNodes().item(0).getTextContent().trim();

        NodeList nodes = ((Element) node).getElementsByTagName("ana");

        for (int i = 0; i < nodes.getLength(); ++i)
        {
            String lemma = getLemma(nodes.item(i));
            String msd = getMsd(nodes.item(i));

            if (PUNCT.contains(lemma))
            {
                msd = lemma;
            }
            else if (!lemma.equals("�") && isPunctation(lemma))
            {
                msd = "K";
            }

            sb.append(spelling).append(WORDFORM_LEMMA_SEPARATOR).append(lemma).append(LEMMA_MSD_SEPARATOR).append(msd);
        }

        return sb.toString();
    }

    public static void wToFullLex(Node node)
    {
        Set<MorAna> morAnas = new TreeSet<MorAna>();

        String spelling = node.getChildNodes().item(0).getTextContent().trim();

        NodeList nodes = ((Element) node).getElementsByTagName("anav");

        for (int i = 0; i < nodes.getLength(); ++i)
        {
            String lemma = getLemma(nodes.item(i));
            String msd = getMsd(nodes.item(i));

            if (PUNCT.contains(lemma))
            {
                msd = lemma;
            }
            else if (!lemma.equals("�") && isPunctation(lemma))
            {
                msd = "K";
            }

            morAnas.add(new MorAna(lemma, msd));
        }

        // already in the lex
        if (FULL_LEX.containsKey(spelling))
        {
            FULL_LEX.get(spelling).addAll(morAnas);
        }
        // not in lex yet
        else
        {
            FULL_LEX.put(spelling, morAnas);
        }
    }

    public static String splitToTrainString(String[][] split)
    {
        StringBuilder sb = new StringBuilder();

        for (String[] s : split)
        {
            sb.append(s[0]);
            sb.append(WORDFORM_LEMMA_SEPARATOR);
            sb.append(s[1]);
            sb.append(LEMMA_MSD_SEPARATOR);
            sb.append(s[2]);
            sb.append('\n');
        }

        return sb.toString().trim();
    }

    public static String cToTrain(Node node)
    {
        StringBuilder sb = new StringBuilder();

        String c = node.getTextContent();

        sb.append(c);
        sb.append(WORDFORM_LEMMA_SEPARATOR);
        sb.append(c);
        sb.append(LEMMA_MSD_SEPARATOR);

        // if (!ResourceHolder.getPunctations().contains(c)) {
        // sb.append("K");
        // } else {
        // sb.append(c);
        // }

        return sb.toString();
    }

    public static String choiceToTrain(Node node)
    {
        for (Node correctedNode : getNodes(getNodes(node, new String[] { "corr", "reg" }).get(0), new String[] { "w", "c" }))
        {
            return nodeToTrain(correctedNode);
        }

        return null;
    }

    public static String nodeToTrain(Node node)
    {
        String nodeName = node.getNodeName();

        if (nodeName.equals("w"))
        {
            return wToTrain(node);
        }
        else if (nodeName.equals("c"))
        {
            return cToTrain(node);
        }
        else if (nodeName.equals("choice"))
        {
            return choiceToTrain(node);
        }

        return null;
    }

    private static void nodeToFullLex(Node node)
    {
        String nodeName = node.getNodeName();

        if (nodeName.equals("w"))
        {
            wToFullLex(node);
        }
        else if (nodeName.equals("c"))
        {
            // cToTrain(node);
        }
        else if (nodeName.equals("choice"))
        {
            // choiceToTrain(node);
        }
    }

    public static List<Node> getNodes(Node node, String... tagNames)
    {
        List<Node> nodes = new LinkedList<Node>();

        NodeList childNodes = ((Element) node).getChildNodes();

        for (int i = 0; i < childNodes.getLength(); ++i)
        {
            Node tempNode = childNodes.item(i);
            String tempNodeName = tempNode.getNodeName();

            for (String tagName : tagNames)
            {
                if (tempNodeName.equals(tagName))
                {
                    nodes.add((Element) tempNode);
                    break;
                }
            }
        }

        return nodes;
    }

    private static List<Node> readXml(String xml)
    {
        List<Node> divNodes = null;

        try
        {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new FileInputStream(new File(xml)), "UTF-8");
            divNodes = getNodes(document, "div", "article");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return divNodes;
    }

    private static String sentenceNodeToTrain(Node sentenceNode)
    {
        StringBuilder sb = new StringBuilder();

        for (Node node : getNodes(sentenceNode, new String[] { "w", "c", "choice" }))
        {
            String trainNode = nodeToTrain(node);

            if (trainNode != null)
                sb.append(trainNode).append('\n');
        }

        return sb.toString();
    }

    private static void convert(String corpusPath, String trainFile, String testFile)
    {
        BufferedWriter trainWriter = null;
        BufferedWriter testWriter = null;

        try
        {
            trainWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(trainFile), "UTF-8"));
            testWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(testFile), "UTF-8"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        for (String corpus : CORPUSES)
        {
            StringBuilder xml = new StringBuilder(corpusPath);
            xml.append(corpus + ".xml");
            List<Node> divNodes = readXml(xml.toString());

            int treshold = (int) (divNodes.size() * DIVISION);

            int sentenceCounter = 0;
            try
            {
                for (int i = 0; i < divNodes.size(); ++i)
                {
                    ++sentenceCounter;

                    for (Node paragraphNode : getNodes(divNodes.get(i), "p"))
                    {
                        for (Node sentenceNode : getNodes(paragraphNode, "s"))
                        {
                            if (i <= treshold)
                            {
                                trainWriter.write(sentenceNodeToTrain(sentenceNode) + '\n');
                            }
                            else
                            {
                                testWriter.write(sentenceNodeToTrain(sentenceNode) + '\n');
                            }

                            sentenceNodeToFullLex(sentenceNode);
                        }
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            System.out.println(xml + "\t" + sentenceCounter);
        }
        try
        {
            trainWriter.close();
            testWriter.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private static void sentenceNodeToFullLex(Node sentenceNode)
    {
        for (Node node : getNodes(sentenceNode, new String[] { "w", "c", "choice" }))
        {
            nodeToFullLex(node);
        }
    }

    public static void stanfordTrain(String in, String out)
    {
        BufferedReader reader = null;
        BufferedWriter writer = null;

        Set<String> msdCodes = new TreeSet<String>();

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(in), "UTF-8"));
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            StringBuilder sb = new StringBuilder();

            for (String line; (line = reader.readLine()) != null; )
            {
                String[] split = line.split("\t");
                if (split.length == 3)
                {
                    sb.append(split[0]);
                    sb.append(STANFORD_TRAIN_WORDFORM_MSD_SEPARATOR);

                    String reducedMsd = MSD_REDUCER.reduce(split[2]);
                    sb.append(reducedMsd);
                    msdCodes.add(reducedMsd);

                    sb.append(STANFORD_TRAIN_TOKEN_SEPARATOR);
                }
                else
                {
                    writer.write(sb.toString().trim());
                    writer.write('\n');
                    sb = new StringBuilder();
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        for (String msdCode : msdCodes)
        {
            System.err.print("openClassTags ");

            if (!Arrays.asList(CLOSED_TAGS.split(" ")).contains(msdCode))
            {
                System.err.print(msdCode + " ");
            }
        }
    }

    public static void writeLex(Map<String, Set<MorAna>> lexicon, String out)
    {
        BufferedWriter writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Map.Entry<String, Set<MorAna>> entry : lexicon.entrySet())
            {
                writer.write(entry.getKey());
                for (MorAna morAna : entry.getValue())
                {
                    writer.write('\t');
                    writer.write(morAna.getLemma());
                    writer.write('\t');
                    writer.write(morAna.getMsd());
                }
                writer.write('\n');
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void writeFullLex(Map<String, Set<MorAna>> lexicon, String out)
    {
        BufferedWriter writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Map.Entry<String, Set<MorAna>> entry : lexicon.entrySet())
            {
                writer.write(entry.getKey());
                for (MorAna morAna : entry.getValue())
                {
                    writer.write('\t');
                    writer.write(morAna.getLemma());
                    writer.write('\t');
                    writer.write(morAna.getMsd());
                }
                writer.write('\n');
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void writeFreq(Map<String, Integer> frequencies, String out)
    {
        BufferedWriter writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Map.Entry<String, Integer> entry : frequencies.entrySet())
            {
                writer.write(entry.getKey());
                writer.write('\t');
                writer.write(entry.getValue());
                writer.write('\n');
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    /**
     * Genrates lex from the given 'IOB' file.
     *
     * @param file
     *          'IOB' file
     * @return map of morphological analyzis
     */
    public static Map<String, Set<MorAna>> getLex(String file)
    {
        Map<String, Set<MorAna>> lexicon = new TreeMap<String, Set<MorAna>>();

        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                String[] split = line.split(WORDFORM_LEMMA_SEPARATOR);

                if (split.length == 3)
                {
                    if (!lexicon.containsKey(split[0]))
                    {
                        lexicon.put(split[0], new TreeSet<MorAna>());
                    }
                    lexicon.get(split[0]).add(new MorAna(split[1], split[2]));
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

        return lexicon;
    }

    public static Map<String, Integer> getFreq(String file)
    {
        Map<String, Integer> frequencies = new TreeMap<String, Integer>();

        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                String[] split = line.split(WORDFORM_LEMMA_SEPARATOR);

                if (split.length == 3)
                {
                    if (!frequencies.containsKey(split[2]))
                    {
                        frequencies.put(split[2], 0);
                    }
                    frequencies.put(split[2], frequencies.get(split[2]) + 1);
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

        return frequencies;
    }

    public static void lex(String input, String output)
    {
        writeLex(getLex(input), output);
    }

    public static void freq(String input, String output)
    {
        writeFreq(getFreq(input), output);
    }

    public static void main(String[] args)
    {
        String corpusPath = "./data/webcorpus/";
        String trainFile = "./data/webcorpus/web.train";
        String testFile = "./data/webcorpus/web.test";
        String lexFile = "./data/webcorpus/web.lex";

        String fullLexFile = "./data/webcorpus/web.full.lex";

        String freqFile = "./data/webcorpus/web.freq";
        String stanfordTrainFile = "./data/webcorpus/web.stanford.train";

        convert(corpusPath, trainFile, testFile);
        stanfordTrain(trainFile, stanfordTrainFile);
        lex(trainFile, lexFile);
        writeFullLex(FULL_LEX, fullLexFile);
        freq(trainFile, freqFile);
    }
}
EOF
mkdir -p szte/converter/webcorpus && cat > szte/converter/webcorpus/DepPrediction.java <<'EOF'
package szte.converter.webcorpus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import szte.dep.parser.MateParserWrapper;

public class DepPrediction
{
    private static final List<List<String>> forms = new ArrayList<List<String>>();
    private static final List<List<String>> lemmas = new ArrayList<List<String>>();
    private static final List<List<String>> msds = new ArrayList<List<String>>();
    private static final List<String[][]> dep = new ArrayList<String[][]>();

    private static void read(String file)
    {
        BufferedReader reader = null;

        List<String> form = new ArrayList<String>();
        List<String> lemma = new ArrayList<String>();
        List<String> msd = new ArrayList<String>();

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                if ("".equals(line.trim()))
                {
                    forms.add(form);
                    lemmas.add(lemma);
                    msds.add(msd);

                    form = new ArrayList<String>();
                    lemma = new ArrayList<String>();
                    msd = new ArrayList<String>();
                }
                else
                {
                    String[] split = line.split("\t");

                    form.add(split[0]);
                    lemma.add(split[1]);
                    msd.add(split[2]);
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
    }

    private static void parse()
    {
        for (int i = 0; i < forms.size(); ++i)
        {
            dep.add(MateParserWrapper.parseSentence(forms.get(i).toArray(new String[forms.get(i).size()]), lemmas.get(i).toArray(new String[lemmas.get(i).size()]), msds.get(i).toArray(new String[msds.get(i).size()])));
        }
    }

    private static void write(String file)
    {
        Writer writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (int i = 0; i < forms.size(); ++i)
            {
                for (int j = 0; j < forms.get(i).size(); ++j)
                {
                    writer.write(j + 1);
                    writer.write('\t');
                    writer.write(forms.get(i).get(j));
                    writer.write('\t');
                    writer.write(lemmas.get(i).get(j));
                    writer.write('\t');

                    // full msd
                    writer.write(msds.get(i).get(j));
                    writer.write('\t');

                    // reduced msd
                    // writer.write(ResourceHolder.getMSDReducer().reduce(msds.get(i).get(j)));
                    // writer.write('\t');

                    // full msd
                    writer.write(msds.get(i).get(j));
                    writer.write('\t');

                    writer.write("_");
                    writer.write('\t');

                    writer.write(dep.get(i)[j][6]);
                    writer.write('\t');
                    writer.write(dep.get(i)[j][7]);
                    writer.write('\t');

                    writer.write("-");
                    writer.write('\t');
                    writer.write("_");

                    writer.write('\n');
                }
                writer.write('\n');
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args)
    {
        // read("./data/webcorpus_1222/faq_1222.dep.test");
        // parse();
        // write("./data/webcorpus/web.conll2007");
    }
}
EOF
mkdir -p szte/converter/webcorpus && cat > szte/converter/webcorpus/Test.java <<'EOF'
package szte.converter.webcorpus;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

public class Test
{
    public static void main(String[] args)
    {
        try
        {
            BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("./data/twitter/tweets.txt"), "UTF-8"));

            for (String line; (line = in.readLine()) != null; )
            {
                List<String> a = TwitterQuestion.getAnswers(TwitterUtil.cleanTweet(line));

                if (a != null && !a.contains("igen"))
                {
                    System.err.println(line);
                    System.err.println(a);
                    System.err.println();
                }
                // System.out.println(TwitterUtil.cleanTweet(line));
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
EOF
mkdir -p szte/converter/webcorpus && cat > szte/converter/webcorpus/TrainResources.java <<'EOF'
package szte.converter.webcorpus;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import szte.dep.parser.MateParserWrapper;
import szte.magyarlanc.Magyarlanc;
import szte.magyarlanc.MorAna;
import szte.magyarlanc.resource.ResourceHolder;
import szte.pos.converter.CoNLLFeaturesToMSD;

public class TrainResources
{
    public static final CoNLLFeaturesToMSD CFTM = new CoNLLFeaturesToMSD();

    private static final Random RANDOM = new Random();

    /**
     * Generates random doc IDs to test.
     *
     * @param trainIDs
     *          set of train ids to exclude
     * @param testSize
     *          number of test IDs
     * @return set of random doc IDs to test excluded train IDs
     */

    public static Set<Integer> getRandom(int size, int max)
    {
        // random ids
        Set<Integer> randomIDs = new TreeSet<Integer>();

        while (randomIDs.size() < size)
        {
            randomIDs.add(RANDOM.nextInt(max));
        }

        return randomIDs;
    }

    /**
     * Reads the sentences.
     *
     * @param file
     *          file
     * @return list if the sentences
     */
    public static List<List<String>> readSentences(String file)
    {
        List<List<String>> sentences = new LinkedList<List<String>>();

        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            List<String> sentence = new ArrayList<String>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.trim().length() == 0)
                {
                    sentences.add(sentence);
                    sentence = new ArrayList<String>();
                }
                else
                {
                    sentence.add(line);
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

        return sentences;
    }

    /**
     *
     * @param sentence
     * @return
     */
    public static boolean isContainsVirtual(List<String> sentence)
    {
        for (String token : sentence)
        {
            String[] split = token.split("\t");

            if (split[4].equals("VAN") || split[4].equals("ELL"))
            {
                return true;
            }
        }

        return false;
    }

    public static List<List<String>> filterSentences(List<List<String>> sentences)
    {
        List<List<String>> filtered = new ArrayList<List<String>>();

        for (List<String> sentence : sentences)
        {
            if (!isContainsVirtual(sentence))
            {
                filtered.add(sentence);
            }
        }

        return filtered;
    }

    public static void writePosTrain(List<List<String>> sentences, String out)
    {
        Writer writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                if (sentence.size() > 0)
                {
                    for (String token : sentence)
                    {
                        String[] split = token.split("\t");

                        String wordForm = split[1];
                        String msd = CFTM.convert(split[4], split[6]);

                        writer.write(wordForm);
                        writer.write("@");
                        writer.write(ResourceHolder.getMSDReducer().reduce(msd));
                        writer.write(" ");
                    }
                    writer.write('\n');
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void writeCorpus(List<List<String>> sentences, String out)
    {
        Map<String, Set<MorAna>> corpus = new TreeMap<String, Set<MorAna>>();

        for (List<String> sentence : sentences)
        {
            for (String token : sentence)
            {
                String[] split = token.split("\t");

                String wordForm = split[1];
                String lemma = split[2];
                String msd = CFTM.convert(split[4], split[6]);

                MorAna morAna = new MorAna(lemma, msd);

                if (!corpus.containsKey(wordForm))
                    corpus.put(wordForm, new TreeSet<MorAna>());

                corpus.get(wordForm).add(morAna);
            }
        }

        Writer writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Entry<String, Set<MorAna>> enrty : corpus.entrySet())
            {
                writer.write(enrty.getKey());

                for (MorAna m : enrty.getValue())
                {
                    writer.write('\t');
                    writer.write(m.getLemma());
                    writer.write('\t');
                    writer.write(m.getMsd());
                }

                writer.write('\n');
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void writeFreq(List<List<String>> sentences, String out)
    {
        Map<String, Integer> freq = new TreeMap<String, Integer>();

        for (List<String> sentence : sentences)
        {
            for (String token : sentence)
            {
                String[] split = token.split("\t");

                String msd = CFTM.convert(split[4], split[6]);

                if (msd.equals("O"))
                {
                    System.err.println(token);
                }

                if (!freq.containsKey(msd))
                {
                    freq.put(msd, 0);
                }

                freq.put(msd, freq.get(msd) + 1);
            }
        }

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Entry<String, Integer> enrty : freq.entrySet())
            {
                writer.write(enrty.getKey());
                writer.write('\t');
                writer.write(enrty.getValue());
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

    public static void writePosTest(List<List<String>> sentences, String out)
    {
        Writer writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                if (sentence.size() > 0)
                {
                    for (String token : sentence)
                    {
                        String[] split = token.split("\t");

                        String wordForm = split[1];
                        String lemma = split[2];
                        String msd = CFTM.convert(split[4], split[6]);

                        writer.write(wordForm);
                        writer.write('\t');
                        writer.write(lemma);
                        writer.write('\t');
                        writer.write(msd);
                        writer.write('\n');
                    }
                    writer.write('\n');
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void writeDep(List<List<String>> sentences, String out)
    {
        // 1 A a a T T SubPOS=f SubPOS=f 3 3 DET DET _ _

        Writer writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                for (String token : sentence)
                {
                    String[] split = token.split("\t");

                    String id = split[0];
                    String form = split[1];
                    String lemma = split[2];
                    // splitted[3];
                    String POS = split[4];
                    String feature = split[6];

                    String head = split[8];
                    String label = split[10];

                    writer.write(id);
                    writer.write('\t');
                    writer.write(form);
                    writer.write('\t');
                    writer.write(lemma);
                    writer.write('\t');
                    writer.write(lemma);
                    writer.write('\t');
                    writer.write(POS);
                    writer.write('\t');
                    writer.write(POS);
                    writer.write('\t');
                    writer.write(feature);
                    writer.write('\t');
                    writer.write(feature);
                    writer.write('\t');
                    writer.write(head);
                    writer.write('\t');
                    writer.write(head);
                    writer.write('\t');
                    writer.write(label);
                    writer.write('\t');
                    writer.write(label);
                    writer.write("\t_\t_\n");
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
        finally
        {
            try
            {
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void generate()
    {
        for (String file : new String[] { "web_1222", "face_1222", "faq_1222" })
        {
            List<List<String>> trainSentences = new ArrayList<List<String>>();
            List<List<String>> testSentences = new ArrayList<List<String>>();

            // virtualis node-okat tartalmazo mondatok elhagyasa
            List<List<String>> filteredSentences = filterSentences(readSentences("./data/webcorpus_1222/" + file + ".conll-2009"));

            Set<Integer> ids = new TreeSet<Integer>();
            for (int i = 0; i < filteredSentences.size(); ++i)
            {
                ids.add(i);
            }

            System.err.println(ids.size());
            // full test
            writePosTest(filteredSentences, "./data/webcorpus_1222/" + file + ".pos.full");

            Set<Integer> trainIds = getRandom((int) (filteredSentences.size() * 0.8), ids.size());

            // trainSentences = filteredSentences.subList(0, (int) (filteredSentences.size() * 0.8));
            // testSentences = filteredSentences.subList((int) (filteredSentences.size() * 0.8), filteredSentences.size());

            for (int i : trainIds)
            {
                trainSentences.add(filteredSentences.get(i));
            }

            ids.removeAll(trainIds);

            for (int i : ids)
            {
                testSentences.add(filteredSentences.get(i));
            }

            System.err.println(trainIds);
            System.err.println(trainSentences.size());

            System.err.println(ids);
            System.err.println(testSentences.size());

            // 80-20 split

            // writePosTrain(trainSentences, "./data/webcorpus_1222/" + file + ".pos.train");
            // writePosTest(testSentences, "./data/webcorpus_1222/" + file + ".pos.test");

            // writeCorpus(trainSentences, "./data/webcorpus_1222/" + file + ".lex");
            // writeFreq(trainSentences, "./data/webcorpus_1222/" + file + ".freq");

            // writeDep(trainSentences, "./data/webcorpus_1222/" + file + ".dep.train");
            // writeDep(testSentences, "./data/webcorpus_1222/" + file + ".dep.test");
        }
    }

    /**
     * Reads CoNLL-2009 file.
     *
     * @param file
     */
    private static void predictAndEvalConll2009UsingGoldFeatures(String file, String out)
    {
        // 1 De de de C C SubPOS=c... SubPOS=c... 4 4 CONJ CONJ _ _

        int wordFormIndex = 1;
        int lemmaIndex = 2;
        int posIndex = 4;
        int featIndex = 6;
        int headIndex = 8;
        int relIndex = 10;

        BufferedReader reader = null;
        Writer writer = null;

        List<String> wordForm = new ArrayList<String>();
        List<String> lemma = new ArrayList<String>();
        List<String> pos = new ArrayList<String>();
        List<String> feat = new ArrayList<String>();

        List<String> msd = new ArrayList<String>();

        List<String> head = new ArrayList<String>();
        List<String> rel = new ArrayList<String>();

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                if ("".equals(line.trim()))
                {
                    String[][] pred = MateParserWrapper.parseSentence(wordForm, lemma, msd, pos, feat);

                    if (pred != null)
                    {
                        for (int i = 0; i < pred.length; ++i)
                        {
                            writer.write(wordForm.get(i));
                            writer.write('\t');
                            writer.write(head.get(i));
                            writer.write('\t');
                            writer.write(pred[i][6]);
                            writer.write('\t');
                            writer.write(rel.get(i));
                            writer.write('\t');
                            writer.write(pred[i][7]);
                            writer.write('\n');
                        }

                        writer.write('\n');
                    }

                    // gold values
                    wordForm = new ArrayList<String>();
                    lemma = new ArrayList<String>();
                    pos = new ArrayList<String>();
                    feat = new ArrayList<String>();
                    msd = new ArrayList<String>();

                    head = new ArrayList<String>();
                    rel = new ArrayList<String>();
                }
                else
                {
                    String[] split = line.split("\t");

                    wordForm.add(split[wordFormIndex]);
                    lemma.add(split[lemmaIndex]);
                    pos.add(split[posIndex]);
                    feat.add(split[featIndex]);

                    msd.add(CFTM.convert(split[posIndex], split[featIndex]));

                    head.add(split[headIndex]);
                    rel.add(split[relIndex]);
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        evalPred(out);
    }

    public static void predictPred(List<List<String>> testSentences)
    {
        // String[][] parseSentence(String[] form, String[] lemma, String[] MSD) {
        // for (int i = 0; i < forms.size(); ++i) {
        // dep.add(MateParserWrapper.parseSentence();
        // }
        // }
    }

    public static void evalPred(String file)
    {
        List<List<String>> sentences = readSentences(file);

        int counter = 0;

        int las = 0;
        int uas = 0;

        for (List<String> sentence : sentences)
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");
                if (splitted[1].equals(splitted[2]))
                {
                    ++uas;
                    if (splitted[3].equals(splitted[4]))
                    {
                        ++las;
                    }
                }
                ++counter;
            }
        }

        DecimalFormat df = new DecimalFormat("#.####");

        System.out.print("\t" + df.format((double) las / counter) + "/" + df.format((double) uas / counter));
    }

    // leiras
    // https://docs.google.com/document/d/1y9yICnPE-xccrLbkrgDnZOrg972dttECakS0Z2C9T3o/edit

    public static void main(String[] args)
    {
        // generate();

        // Magyarlanc.eval("./data/webcorpus_1222/faq_1222.pos.full", "./data/webcorpus_1222/faq_1222.pos.full.out");

        // predictAndEvalConll2009UsingGoldFeatures("./data/webcorpus_1222/face_1222.conll-2009", "./data/webcorpus_1222/face_1222.conll-2009.dep.out");
    }
}
EOF
mkdir -p szte/converter/webcorpus && cat > szte/converter/webcorpus/TwitterQuestion.java <<'EOF'
package szte.converter.webcorpus;

import szte.magyarlanc.Magyarlanc;
import szte.magyarlanc.resource.ResourceHolder;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Class that tries to answer simple questions via its morphological analysis.
 *
 * @author zsibritajanos
 *
 */
public class TwitterQuestion
{
    /**
     * Decides if the sentence is question.
     *
     * @param sentence
     *          tokenized sentence
     * @return true is the sentence is question, false otherwise
     */
    private static boolean isQuestion(List<String> sentence)
    {
        if (sentence.get(sentence.size() - 1).equals("?"))
        {
            return true;
        }

        return false;
    }

    /**
     * Searches for the given lemma and msd (prefix) pair in the given
     * morphologically analyzed sentence.
     *
     * @param morphSentence
     *          morphologically analyzed sentence
     * @param lemma
     *          lemma
     * @param msd
     *          msd (prefix)
     * @return index of the first occurrence of the given lemma and msd (prefix)
     *         pair, of -1 if the sentence doesn't contains it
     */
    private static int getMorAnaIndex(String[][] morphSentence, String lemma, String msd)
    {
        for (int i = 0; i < morphSentence.length; ++i)
        {
            if ("vagy".equals(morphSentence[i][1]) && morphSentence[i][2].startsWith(msd))
            {
                return i;
            }
        }

        return -1;
    }

    /**
     * Get the msd code at the given index in the morphologically analyzed
     * sentence.
     *
     * @param morphSentence
     *          morphologically analyzed sentence
     * @param index
     *          msd index
     * @return msd code at the index position
     */
    private static String getMsd(String[][] morphSentence, int index)
    {
        return morphSentence[index][2];
    }

    /**
     * Searches forward for the given msd (prefix) from the given index in the
     * morphologically analyzed sentence.
     *
     * @param morphSentence
     *          morphologically analyzed sentence
     * @param fromIndex
     *          index
     * @param msd
     *          msd (prefix)
     * @return the index of the msd or -1 if the sentence does not contains the
     *         given msd
     *
     */
    private static int getMsdIndexForward(String[][] morphSentence, int fromIndex, String msd)
    {
        for (int i = fromIndex; i < morphSentence.length; ++i)
        {
            if (morphSentence[i][2].startsWith(msd))
            {
                return i;
            }
        }

        return -1;
    }

    /**
     * Get the possible (simple) answers for the given sentence.
     *
     * @param tokenized
     *          sentence
     * @return set of the possible answers for the given sentence, or empty set if
     *         we couldn't predict any answers
     */
    private static List<String> getAnswers(List<String> sentence)
    {
        List<String> answers = new LinkedList<String>();

        if (isQuestion(sentence))
        {
            String[][] morphSentence = Magyarlanc.morphParseSentence(sentence);

            // YES/NO questions
            if (getMsd(morphSentence, 0).startsWith("V"))
            {
                answers.add("igen");
                answers.add("nem");
            }

            // 'vagy' questions
            int morAnaIndex = getMorAnaIndex(morphSentence, "vagy", "Ccs");
            if (morAnaIndex > 0)
            {
                String prevMsd = morphSentence[morAnaIndex - 1][2];

                // Nn Q
                if (prevMsd.startsWith("Nn"))
                {
                    int msdForward = -1;
                    msdForward = getMsdIndexForward(morphSentence, morAnaIndex, "Nn");

                    if (msdForward > 1)
                    {
                        // phrase
                        if ((morAnaIndex > 1) && (morphSentence[morAnaIndex - 2][2].startsWith("Nn")
                                               || morphSentence[morAnaIndex - 2][2].startsWith("Afp")))
                        {
                            answers.add(morphSentence[morAnaIndex - 2][0] + " " + morphSentence[morAnaIndex - 1][0]);
                        }
                        else
                        {
                            answers.add(morphSentence[morAnaIndex - 1][0]);
                        }

                        // phrase
                        if (morphSentence[msdForward - 1][2].startsWith("Nn") || morphSentence[msdForward - 1][2].startsWith("Afp"))
                        {
                            answers.add(morphSentence[msdForward - 1][0] + " " + morphSentence[msdForward][0]);
                        }
                        else
                        {
                            answers.add(morphSentence[msdForward][0]);
                        }
                    }
                }
                else
                {
                    // Afp Q
                    if (prevMsd.startsWith("Afp"))
                    {
                        int msdForward = -1;
                        msdForward = getMsdIndexForward(morphSentence, morAnaIndex, "Afp");

                        if (msdForward > 1)
                        {
                            answers.add(morphSentence[morAnaIndex - 1][0]);
                            answers.add(morphSentence[msdForward][0]);
                        }
                    }
                }
            }
        }

        return answers;
    }

    /**
     * Get the possible (simple) answers for the given raw text.
     *
     * @param text
     *          raw text
     * @return set of the possible answers of the last answerable sentence in the
     *         given text, or NULL if we couldn't predict any answers
     */
    public static List<String> getAnswers(String text)
    {
        List<List<String>> sentences = ResourceHolder.getHunSplitter().split(text);

        // reverse list
        Collections.reverse(sentences);

        for (List<String> sentence : sentences)
        {
            List<String> sentenceAnswers = getAnswers(sentence);

            if (sentenceAnswers.size() > 0)
                return sentenceAnswers;
        }

        return null;
    }

    public static void main(String[] args)
    {
        System.err.println(getAnswers("A Kínai Nagy Fal étterembe vagy a sarki kifőzdébe menjünk enni?"));
    }
}
EOF
mkdir -p szte/converter/webcorpus && cat > szte/converter/webcorpus/TwitterUtil.java <<'EOF'
package szte.converter.webcorpus;

public class TwitterUtil
{
    /**
     * Removes the specified parts of the tweet content.
     *
     * @param tweet
     *          raw tweet content
     * @return cleaned tweet content
     */
    public static String cleanTweet(String tweet)
    {
        String ret = tweet;

        if (ret.startsWith("@") && ret.contains(" "))
        {
            ret = ret.substring(ret.indexOf(" ") + 1);
        }

        return ret;
    }
}
EOF
mkdir -p szte/dep/parser && cat > szte/dep/parser/MateParserWrapper.java <<'EOF'
package szte.dep.parser;

import szte.magyarlanc.resource.ResourceHolder;
import is2.data.SentenceData09;

import java.util.List;

public class MateParserWrapper
{
    /**
     * Dependency parsing of a sentence, using the forms and morphological
     * analysis.
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
mkdir -p szte/dep/removevirtual && cat > szte/dep/removevirtual/CoNLL2009Sentence.java <<'EOF'
package szte.dep.removevirtual;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CoNLL2009Sentence
{
    private String[][] tokens = null;

    public CoNLL2009Sentence(String[][] sentence)
    {
        this.setTokens(sentence);
    }

    public void setTokens(String[][] tokens)
    {
        this.tokens = tokens;
    }

    public String[][] getTokens()
    {
        return tokens;
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.getTokens().length; ++i)
        {
            sb.append(tokens[i][0]);
            for (int j = 1; j < tokens[i].length; ++j)
            {
                sb.append('\t').append(tokens[i][j]);
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    public void removeVirtuals()
    {
        // VAN ROOT
        if (this.containsVANRoot())
        {
            this.removeVANRoot();
        }

        // ELL ROOT
        else if (this.containsELLRoot())
        {
            this.removeELLRoot();
        }

        // VAN/ELL
        if (this.containsVirtual())
        {
            this.removeVirtualNodes();
        }
    }

    /**
     * Get the String column from the sentence with the specified index.
     *
     * @param index
     * @return
     */
    private String[] getColumn(int index)
    {
        String[] column = new String[this.getTokens().length];

        for (int i = 0; i < this.getTokens().length; ++i)
        {
            column[i] = this.getTokens()[i][index];
        }

        return column;
    }

    /**
     * Get the POS array of the sentence
     *
     * @return the array of the POS
     */
    private String[] getPOS()
    {
        return this.getColumn(4);
    }

    /**
     * Get the rel. array of the sentence
     *
     * @return the array of the rel.
     */
    private String[] getRel()
    {
        return this.getColumn(10);
    }

    /**
     *
     * @return
     */
    private boolean containsVirtual()
    {
        String[] POS = this.getPOS();

        for (String s : POS)
        {
            if (s.equals("VAN") || s.equals("ELL"))
            {
                return true;
            }
        }

        return false;
    }

    private Integer[] getVirtualIndexes()
    {
        String[] POS = this.getPOS();

        List<Integer> virtualId = new ArrayList<Integer>();

        for (int i = 0; i < POS.length; ++i)
        {
            if (POS[i].equals("VAN") || POS[i].equals("ELL"))
            {
                virtualId.add(i);
            }
        }

        // ez igy 'itt' nem elegans, de gyors es mukodik
        // ha egy mondatban tobb virtualis node szerepel, es kivesszuk az elsot
        // akkor a tobbi virtualis id-ja is csokkenni fog!!!
        for (int i = 0; i < virtualId.size(); ++i)
        {
            virtualId.set(i, virtualId.get(i) - i);
        }

        return virtualId.toArray(new Integer[virtualId.size()]);
    }

    /**
     *
     * @return
     */
    private boolean containsVirtualRoot(String rootType)
    {
        if (this.getVirtualRootIndex(rootType) > -1)
            return true;

        return false;
    }

    private boolean containsELLRoot()
    {
        return containsVirtualRoot("ELL");
    }

    private boolean containsVANRoot()
    {
        return containsVirtualRoot("VAN");
    }

    private int getVirtualRootIndex(String rootType)
    {
        String[] POS = this.getPOS();
        String[] rel = this.getRel();

        for (int i = 0; i < POS.length; ++i)
        {
            if (POS[i].equals(rootType) && rel[i].equals("ROOT"))
            {
                return i;
            }
        }

        return -1;
    }

    private Integer[] getChildrenIndexes(int parentIndex)
    {
        List<Integer> childrenId = new ArrayList<Integer>();

        for (int i = 0; i < this.getTokens().length; ++i)
        {
            if (getNodeParentId(tokens[i]) == getNodeId(tokens[parentIndex]))
            {
                childrenId.add(i);
            }
        }

        return childrenId.toArray(new Integer[childrenId.size()]);
    }

    private int getNodeId(String[] node)
    {
        return Integer.parseInt(node[0]);
    }

    private String getNodePOS(String[] node)
    {
        return node[4];
    }

    private int getNodeParentId(String[] node)
    {
        return Integer.parseInt(node[8]);
    }

    private String getNodeRel(String[] node)
    {
        return node[10];
    }

    private void setNodeRel(String[] node, String rel)
    {
        node[10] = rel;
        node[11] = rel;
    }

    private void setNodeId(String[] node, int id)
    {
        node[0] = String.valueOf(id);
    }

    private void setNodeParentId(String[] node, int parentId)
    {
        node[8] = String.valueOf(parentId);
        node[9] = String.valueOf(parentId);
    }

    private void renumberIds(int index)
    {
        for (int i = index + 1; i < this.getTokens().length; ++i)
        {
            setNodeId(this.getTokens()[i], i);
        }
    }

    private void renumberParents(int deletedId)
    {
        for (int i = 0; i < this.getTokens().length; ++i)
        {
            if (getNodeParentId(this.getTokens()[i]) > deletedId)
            {
                setNodeParentId(this.getTokens()[i], getNodeParentId(this.getTokens()[i]) - 1);
            }
        }
    }

    private int countLabel(String[] labels, String relLabel)
    {
        int counter = 0;
        for (String l : labels)
        {
            if (l.equals(relLabel))
            {
                ++counter;
            }
        }

        return counter;
    }

    private void removeNode(int removeIndex)
    {
        String[][] array = new String[this.getTokens().length - 1][];

        System.arraycopy(this.getTokens(), 0, array, 0, removeIndex);
        System.arraycopy(this.getTokens(), removeIndex + 1, array, removeIndex, this.getTokens().length - removeIndex - 1);

        this.setTokens(array);
    }

    private String[] getRelLabel(Integer... index)
    {
        String[] rels = new String[index.length];

        for (int i = 0; i < index.length; ++i)
        {
            rels[i] = getNodeRel(tokens[index[i]]);
        }

        return rels;
    }

    private void removeVirtualNodes()
    {
        // fontos, hogy ezek nem a valodi id-k, ha tobb virtualis van!!!
        for (int i : this.getVirtualIndexes())
        {
            for (int j : getChildrenIndexes(i))
            {
                StringBuilder sb = new StringBuilder(getNodeRel(tokens[i])).append("-" + getNodePOS(tokens[i])).append("-" + getNodeRel(tokens[j]));
                setNodeRel(tokens[j], sb.toString());
                setNodeParentId(tokens[j], getNodeParentId(tokens[i]));
            }
            renumberIds(i);
            renumberParents(getNodeId(this.getTokens()[i]));
            removeNode(i);
        }
    }

    /**
     * Virtualis virtualis VAN/ELL ROOT eltavoltasa.
     */
    private void removeVirtualRoot(String rootType, String[] rels)
    {
        // virtualis ROOT indexe
        int rootIndex = this.getVirtualRootIndex(rootType);
        // virtualis ROOT child node-jainan idexei
        Integer[] childrenIndexes = this.getChildrenIndexes(rootIndex);
        // virtualis ROOT child node-jainak relacioi
        String[] childrenRelLabels = getRelLabel(childrenIndexes);

        // a virtualis ROOT lehetseges child relacioi, fontos a sorrend
        for (String rel : rels)

            // ha pontosan egy van az adott relaciobol
            if (countLabel(childrenRelLabels, rel) == 1)
            {
                // System.out.println(this);
                // System.out.println(rootIndex);
                // System.out.println(Arrays.toString(childrenIndexes));
                // System.out.println(Arrays.toString(childrenRelLabels));

                // az uj root node indexe
                int index = Arrays.asList(childrenIndexes).get(Arrays.asList(childrenRelLabels).indexOf(rel));

                // a virtualis ROOT-hoz kapcsolodo adott relacioju child lesz az uj ROOT
                // az uj relacio pl. ROOT-VAN-PRED lesz
                setNodeRel(this.getTokens()[index], "ROOT-" + rootType + "-" + rel);
                // az uj ROOT parentID-je 0 lesz
                setNodeParentId(this.getTokens()[index], 0);

                // a korábban a virtualis ROOT-hoz kapcsolt childok az uj ROOT gyermekei
                // lesznek pl. ELL-PUNCT relacioval
                for (int i : childrenIndexes)
                {
                    if (i != index)
                    {
                        setNodeParentId(this.getTokens()[i], getNodeId(this.getTokens()[index]));

                        setNodeRel(this.getTokens()[i], "ROOT-" + rootType + "-" + getNodeRel(this.getTokens()[i]));
                    }
                }

                // atszamozas (id-k csokentese)
                renumberIds(rootIndex);
                // szulo azonositok atszamozasa
                renumberParents(getNodeId(this.getTokens()[rootIndex]));
                // virtualis ROOT eltavolitasa
                removeNode(rootIndex);
                break;
            }
    }

    /**
     * ELL
     */
    private void removeELLRoot()
    {
        this.removeVirtualRoot("ELL", new String[] { "PRED", "SUBJ", "OBJ", "OBL" });
    }

    /**
     * VAN
     */
    private void removeVANRoot()
    {
        this.removeVirtualRoot("VAN", new String[] { "PRED", "SUBJ", "ATT" });
    }
}
EOF
mkdir -p szte/dep/removevirtual && cat > szte/dep/removevirtual/CorrectPUNCT.java <<'EOF'
package szte.dep.removevirtual;

import szte.pos.converter.MSDToCoNLLFeatures;

import java.util.Arrays;
import java.util.List;

public class CorrectPUNCT
{
    private final static List<String> relevant = Arrays.asList(new String[] { "!", ",", "-", ".", ":", ";", "?", "�" });

    private static MSDToCoNLLFeatures msdToCoNLLFeatures = null;

    public static boolean isPunctation(String form)
    {
        for (int i = 0; i < form.length(); ++i)
        {
            if (Character.isLetterOrDigit(form.charAt(i)))
            {
                return false;
            }
        }

        return true;
    }

    public static String getPunctLemma(String form)
    {
        return form;
    }

    public static String getPunctMSD(String form)
    {
        // a legfontosabb irasjelek MSD kodja maga az irasjel
        if (relevant.contains(form))
            return form;

        // § MSD kodja Nc-sn
        if (form.equals("§"))
            return "Nc-sn";

        // egyeb irasjelek MSD kódja 'K' lesz
        else
            return "K";
    }

    public static String getPunctFeature(String lemma, String MSDCode)
    {
        if (msdToCoNLLFeatures == null)
            msdToCoNLLFeatures = new MSDToCoNLLFeatures();

        return msdToCoNLLFeatures.convert(lemma, MSDCode);
    }

    public static void correctCoNLL2009(String in, String out)
    {
        String[][][] coNLL2009 = Util.readCoNLL2009(in);

        for (String[][] sentence : coNLL2009)
        {
            for (String[] token : sentence)
            {
                if (isPunctation(token[1]))
                {
                    token[2] = getPunctLemma(token[1]);
                    token[3] = getPunctLemma(token[1]);
                    token[4] = getPunctMSD(token[1]);
                    token[5] = getPunctMSD(token[1]);
                    token[6] = getPunctFeature(getPunctLemma(token[1]), getPunctMSD(token[1]));
                    token[7] = getPunctFeature(getPunctLemma(token[1]), getPunctMSD(token[1]));
                }
            }
        }

        Util.writeCoNLL2009(coNLL2009, out);
    }
}
EOF
mkdir -p szte/dep/removevirtual && cat > szte/dep/removevirtual/RemoveVirtualNodes.java <<'EOF'
package szte.dep.removevirtual;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class RemoveVirtualNodes
{
    public static void main(String[] args)
    {
        String file = "./data/objfx/10fold/law.0.test";

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file + ".virtual"), "UTF-8"));

            // for (String[][] sentence : Util.readCoNLL2009(file)) {
            // CoNLL2009Sentence coNLL2009Sentence = new CoNLL2009Sentence(sentence);
            // coNLL2009Sentence.removeVirtuals();
            // writer.write(coNLL2009Sentence.toString());
            // writer.write('\n');
            // }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}
EOF
mkdir -p szte/dep/removevirtual && cat > szte/dep/removevirtual/Util.java <<'EOF'
package szte.dep.removevirtual;

import szte.pos.converter.MSDToCoNLLFeatures;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class Util
{
    /**
     * Reads an CoNLL-2009 format file to a String array of the sentences.
     * All sentence contains the String array os the tokens.
     * All token contais the String array of the ConNLL-2009 values.
     *
     * @param file
     *          the CoNLL-2009 file
     *
     * @return the CoNLL-2009 sentences
     * @see http://ufal.mff.cuni.cz/conll2009-st/task-description.html
     */
    public static String[][][] readCoNLL2009(String file)
    {
        List<String[][]> sentences = new ArrayList<String[][]>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            List<String[]> sentence = new ArrayList<String[]>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (!line.equals(""))
                {
                    sentence.add(line.split("\t"));
                }
                else
                {
                    sentences.add(sentence.toArray(new String[sentence.size()][]));
                    sentence = new LinkedList<String[]>();
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return sentences.toArray(new String[sentences.size()][][]);
    }

    public static void writeCoNLL2009(String[][][] sentences, String file)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (String[][] sentence : sentences)
            {
                for (String[] token : sentence)
                {
                    for (String s : token)
                    {
                        writer.write(s);
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
        String[][][] conll = readCoNLL2009("newspaper.conll2009" + "_test_virtual_1");
        String[][][] msd = readCoNLL2009("newspaper.pred2");

        MSDToCoNLLFeatures msdToCoNLLFeatures = new MSDToCoNLLFeatures();

        for (int i = 0; i < conll.length; ++i)
        {
            for (int j = 0; j < conll[i].length; ++j)
            {
                // plemma
                conll[i][j][3] = msd[i][j][1];

                // pposs
                conll[i][j][5] = String.valueOf(msd[i][j][2].charAt(0));

                // pfeat
                String pfeat = msdToCoNLLFeatures.convert(msd[i][j][1], msd[i][j][2]);
                if (pfeat == null)
                    pfeat = "_";
                conll[i][j][7] = pfeat;
            }
        }

        writeCoNLL2009(conll, "newspaper.conll2009.pred.2");
    }
}
EOF
mkdir -p szte/dep/util && cat > szte/dep/util/EmptyNodeEvaluator.java <<'EOF'
package szte.dep.util;

public class EmptyNodeEvaluator
{
    public static final String EMPTY_LABEL = "_";
    public static final int GS_PARENT_COLUMN = 8;
    public static final int PRED_PARENT_COLUMN = 9;
    public static final int GS_LABEL_COLUMN = 10;
    public static final int PRED_LABEL_COLUMN = 11;
    public static final String SEPARATOR = "@";
}
EOF
mkdir -p szte/dep/util && cat > szte/dep/util/RemoveEmptyNodes.java <<'EOF'
package szte.dep.util;

import java.io.*;
import java.util.*;

public class RemoveEmptyNodes
{
    public static boolean useLabelConcatForCollapsedEdges = true;

    public static void processFile(String in, String out)
        throws IOException
    {
        BufferedReader input = new BufferedReader(new InputStreamReader(new FileInputStream(in), "UTF-8"));
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

        List<String[]> sentence = new LinkedList<String[]>();

        for (String line; (line = input.readLine()) != null; )
        {
            if (line.isEmpty())
            {
                removeEmptyNodes(sentence, true);
                writeSentence(sentence, output);
                // restoreEmptyNodes(sentence, true);
                // writeSentence(sentence, output);
                sentence = new LinkedList<String[]>();
            }
            else
                sentence.add(line.split("\t"));
        }

        output.flush();
        output.close();
    }

    public static void processStd()
        throws IOException
    {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, "UTF-8"));
        BufferedWriter output = new BufferedWriter(new OutputStreamWriter(System.out, "UTF-8"));

        List<String[]> sentence = new LinkedList<String[]>();

        for (String line; (line = input.readLine()) != null; )
        {
            if (line.isEmpty())
            {
                removeEmptyNodes(sentence, true);
                writeSentence(sentence, output);
                // restoreEmptyNodes(sentence);
                // writeSentence(sentence, output);
                sentence = new LinkedList<String[]>();
            }
            else
                sentence.add(line.split("\t"));
        }

        output.flush();
        output.close();
    }

    public static void removeEmptyNodes(List<String[]> sentence, boolean usePredicted)
    {
        int parentcolumn = usePredicted ? EmptyNodeEvaluator.PRED_PARENT_COLUMN : EmptyNodeEvaluator.GS_PARENT_COLUMN;
        int labelcolumn = usePredicted ? EmptyNodeEvaluator.PRED_LABEL_COLUMN : EmptyNodeEvaluator.GS_LABEL_COLUMN;

        for (int e; (e = firstEmptyNode(sentence)) > 0; )
        {
            String parent = sentence.get(e - 1)[parentcolumn];
            for (String[] w : sentence)
                if (Integer.parseInt(w[parentcolumn]) == e)
                {
                    w[8] = w[9] = parent;
                    String parentLabel = sentence.get(e - 1)[labelcolumn]; // the label
                    // from the
                    // empty node
                    // to its
                    // parent
                    String dtrLabel = w[labelcolumn]; // the label form the node to the
                    // empty node
                    String nodeLabel = sentence.get(e - 1)[usePredicted ? 5 : 4]; // the
                    // type
                    // of
                    // the
                    // empty
                    // node
                    String labelConcat = /*
                                          * parentLabel + EmptyNodeEvaluator.SEPARATOR +
                                          * nodeLabel + EmptyNodeEvaluator.SEPARATOR +
                                          */dtrLabel;

                    w[10] = w[11] = useLabelConcatForCollapsedEdges ? labelConcat : "Exd";
                }
            sentence.remove(e - 1);
            for (int i = e - 1; i < sentence.size(); ++i)
            {
                String[] w = sentence.get(i);
                w[0] = Integer.toString(Integer.parseInt(w[0]) - 1);
            }
            for (String[] w : sentence)
                if (Integer.parseInt(w[parentcolumn]) > e)
                    w[8] = w[9] = Integer.toString(Integer.parseInt(w[parentcolumn]) - 1);
        }
    }

    private static int firstEmptyNode(List<String[]> sentence)
    {
        for (int i = 0; i < sentence.size(); ++i)
            if (sentence.get(i)[1].equals(EmptyNodeEvaluator.EMPTY_LABEL))
            {
                // if(Integer.parseInt(sentence.get(i)[0]) != i+1)
                // System.err.println("Problem with token index!!");
                return i + 1;
            }
        return -1;
    }

    private static void restoreEmptyNodes(List<String[]> sentence, boolean usePredicted)
    {
        int parentcolumn = usePredicted ? EmptyNodeEvaluator.PRED_PARENT_COLUMN : EmptyNodeEvaluator.GS_PARENT_COLUMN;
        int labelcolumn = usePredicted ? EmptyNodeEvaluator.PRED_LABEL_COLUMN : EmptyNodeEvaluator.GS_LABEL_COLUMN;
        if (!useLabelConcatForCollapsedEdges)
        {
            System.err.println("You have to use labelconcats for restoring empty nodes!");
            System.exit(1);
        }

        List<Integer> e;
        while (!(e = firstRestoreNode(sentence, parentcolumn, labelcolumn)).isEmpty())
        {
            Integer emptyNodePosition = calcEmptyNodePosition(sentence, e);
            Integer emptyParent = Integer.parseInt(sentence.get(e.get(0))[parentcolumn]);
            String emptyLabel = sentence.get(e.get(0))[labelcolumn].split(EmptyNodeEvaluator.SEPARATOR)[1];
            String emptyEdgeLabel = sentence.get(e.get(0))[labelcolumn].split(EmptyNodeEvaluator.SEPARATOR)[0];
            for (String[] w : sentence)
                if (Integer.parseInt(w[parentcolumn]) > emptyNodePosition)
                    w[8] = w[9] = Integer.toString(Integer.parseInt(w[parentcolumn]) + 1);

            for (Integer en : e)
            {
                String[] w = sentence.get(en);
                w[8] = w[9] = Integer.toString(emptyNodePosition + 1);
                w[10] = w[11] = w[labelcolumn].substring(w[labelcolumn].indexOf(
                            EmptyNodeEvaluator.SEPARATOR, w[labelcolumn].indexOf(EmptyNodeEvaluator.SEPARATOR) + 1) + 1);
            }
            String[] empty = new String[sentence.get(0).length];
            for (int i = 0; i < empty.length; ++i)
                empty[i] = EmptyNodeEvaluator.EMPTY_LABEL;
            empty[0] = Integer.toString(emptyNodePosition + 1);
            empty[4] = empty[5] = emptyLabel;
            empty[8] = empty[9] = emptyParent <= emptyNodePosition ? emptyParent.toString() : Integer.toString(emptyParent + 1);
            empty[10] = empty[11] = emptyEdgeLabel;
            sentence.add(emptyNodePosition, empty);
            for (int i = emptyNodePosition + 1; i < sentence.size(); ++i)
            {
                String[] w = sentence.get(i);
                w[0] = Integer.toString(Integer.parseInt(w[0]) + 1);
            }
        }
    }

    private static int calcEmptyNodePosition(List<String[]> sentence, List<Integer> e)
    {
        return Collections.min(e);
    }

    private static List<Integer> firstRestoreNode(List<String[]> sentence, int parentcolumn, int labelcolumn)
    {
        List<Integer> set = new LinkedList<Integer>();
        int i = 0;
        while (i < sentence.size() && !sentence.get(i)[labelcolumn].contains(EmptyNodeEvaluator.SEPARATOR))
            ++i;
        if (i < sentence.size())
        {
            String e = sentence.get(i)[labelcolumn].split(EmptyNodeEvaluator.SEPARATOR)[0]
                + sentence.get(i)[labelcolumn].split(EmptyNodeEvaluator.SEPARATOR)[1]
                + sentence.get(i)[parentcolumn]; // parentLabel+nodeLabel+parent
            while (i < sentence.size())
            {
                if (sentence.get(i)[labelcolumn].contains(EmptyNodeEvaluator.SEPARATOR))
                {
                    String o = sentence.get(i)[labelcolumn].split(EmptyNodeEvaluator.SEPARATOR)[0]
                        + sentence.get(i)[labelcolumn].split(EmptyNodeEvaluator.SEPARATOR)[1]
                        + sentence.get(i)[parentcolumn];
                    if (e.equals(o))
                        set.add(i);
                }
                ++i;
            }
        }

        return set;
    }

    public static void writeSentence(List<String[]> sentence, BufferedWriter output)
        throws IOException
    {
        for (String[] w : sentence)
        {
            for (int i = 0; i < w.length - 1; ++i)
                output.write(w[i] + "\t");
            // output.write(w[w.length - 1] + "\n");
            output.write(w[w.length - 1] + "\t_\t_" + "\n");
        }
        output.write("\n");
    }

    public static void main(String[] args)
        throws IOException
    {
        // processFile("./data/newsml.dep.fx.noDS", "./newsml.dep.fx.noDS.uot");

        // processFile("/home/users0/farkas/c12/Hungarian/Dep/jav/SzegedDependencyTreebank_dev.conll2009",
        // "/home/users0/farkas/c12/Hungarian/Dep/jav/SzegedDependencyTreebank_dev.restored");
        // processStd();
    }
}
EOF
mkdir -p szte/dep/whatswrong && cat > szte/dep/whatswrong/WhatsWrongWrapper.java <<'EOF'
package szte.dep.whatswrong;

import szte.dep.parser.MateParserWrapper;
import szte.magyarlanc.Magyarlanc;

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

public class WhatsWrongWrapper
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

        WhatsWrongWrapper.exportToPNG(depParsedSentence, "d:/feladat1.png");
    }
}
EOF
mkdir -p szte/gui && cat > szte/gui/GUI.java <<'EOF'
package szte.gui;

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

import szte.dep.whatswrong.WhatsWrongWrapper;
import szte.magyarlanc.Magyarlanc;
import szte.magyarlanc.resource.ResourceHolder;

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
                        bufferedImage = ImageIO.read(new ByteArrayInputStream(WhatsWrongWrapper.exportToByteArray(depParsed)));
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
mkdir -p szte/magyarlanc && cat > szte/magyarlanc/Eval.java <<'EOF'
package szte.magyarlanc;

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
mkdir -p szte/magyarlanc && cat > szte/magyarlanc/HunLemMor.java <<'EOF'
package szte.magyarlanc;

import szte.magyarlanc.resource.ResourceHolder;
import szte.pos.guesser.CompoundWord;
import szte.pos.guesser.HyphenicGuesser;
import szte.pos.guesser.HyphenicWord;
import szte.pos.guesser.NumberGuesser;

import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
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
        if (szte.magyarlanc.resource.Util.isPunctation(word))
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
                morAnas.add(new MorAna(word, Settings.DEFAULT_NOUN));
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
        morAnas = NumberGuesser.guess(word);

        if (morAnas.size() > 0)
        {
            return morAnas;
        }

        // romai szam
        morAnas.addAll(NumberGuesser.guessRomanNumber(word));

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
                for (String morphCode : HyphenicWord.analyseHyphenicCompoundWord(word))
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

                morAnas.addAll(HyphenicGuesser.guess(root, suffix));
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

        try
        {
            ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("d:/m.ser"));

            out.writeObject(ResourceHolder.getCorpus());

            out.close();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }
}
EOF
mkdir -p szte/magyarlanc && cat > szte/magyarlanc/MagyarlancTest.java <<'EOF'
package szte.magyarlanc;

public class MagyarlancTest
{
    public static void main(String[] args)
    {
        // Magyarlanc.main("-mode tokenized -input d:/test.txt -output d:/out.txt".split(" "));

        // Magyarlanc.main("-mode depparse -input d:/Dialogus2_el.txt -output d:/Dialogus2_el.txt.dep_out".split(" "));

        // Magyarlanc.main("-mode morphparse -input d:/Dialogus2_el.txt -output d:/Dialogus2_el.txt.morph_out".split(" "));

        // System.err.println(Magyarlanc.morphParse("méretek: 200 cm x 80 cm x 10 cm"));

        // System.err.println(HunLemMor.getMorphologicalAnalyses("dolgozgathat"));
    }
}
EOF
mkdir -p szte/magyarlanc && cat > szte/magyarlanc/Magyarlanc.java <<'EOF'
package szte.magyarlanc;

import szte.converter.nooj.Dep2Nooj;
import szte.dep.parser.MateParserWrapper;
import szte.gui.GUI;
import szte.magyarlanc.resource.ResourceHolder;
import szte.magyarlanc.resource.Util;
import szte.magyarlanc.util.SafeReader;

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
import java.util.List;
import java.util.Map;

public class Magyarlanc
{
    private static final String USAGE_MESSAGE = "usage: -mode gui|morana|morphparse|depparse|eval";

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
        return MateParserWrapper.parseSentence(Magyarlanc.morphParseSentence(form));
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
                    String[] split = line.split(Settings.DEFAULT_SEPARATOR);

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
        if (args.length < 2)
        {
            System.out.println(USAGE_MESSAGE);
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
                System.out.println(USAGE_MESSAGE);
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
                        List<String> lines = SafeReader.read(params.get("-input"));

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
                        List<String> lines = SafeReader.read(params.get("-input"));

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

                case "nooj":
                    if (params.containsKey("-input") && params.containsKey("-output"))
                    {
                        Dep2Nooj.convert(depParse(Util.readFileToString(params.get("-input"))), params.get("-output"));
                    }
                    else
                    {
                        System.out.println("usage: -mode nooj -input input -output output");
                    }
                    break;

                default:
                    System.out.println(USAGE_MESSAGE);
                    break;
            }
        }
    }
}
EOF
mkdir -p szte/magyarlanc && cat > szte/magyarlanc/MorAna.java <<'EOF'
package szte.magyarlanc;

import java.io.Serializable;

public class MorAna implements Comparable<MorAna>, Serializable
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
mkdir -p szte/magyarlanc/resource && cat > szte/magyarlanc/resource/ResourceBuilder.java <<'EOF'
package szte.magyarlanc.resource;

import szte.magyarlanc.MorAna;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ResourceBuilder
{
    public static List<List<String>> read(String file)
    {
        List<List<String>> sentences = new LinkedList<List<String>>();

        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            List<String> sentence = new ArrayList<String>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.equals(""))
                {
                    sentences.add(sentence);
                    sentence = new ArrayList<String>();
                }
                else
                {
                    sentence.add(line);
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

        return sentences;
    }

    public static void minimalize(List<List<String>> sentences, String out)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                for (String token : sentence)
                {
                    String[] splitted = token.split("\t");

                    String id = splitted[0];
                    String form = splitted[1];
                    String lemma = splitted[2];
                    String POS = splitted[4];
                    String feature = splitted[6];
                    String head = splitted[8];
                    String label = splitted[10];

                    String MSD = ResourceHolder.getCoNLLFeaturesToMSD().convert(POS, feature);

                    if (lemma.equals("-e"))
                        MSD = MSD + "-y";

                    // Nn-
                    if (MSD.startsWith("N"))
                    {
                        StringBuilder sb = new StringBuilder(MSD);
                        sb.setCharAt(1, 'n');
                        MSD = sb.toString();

                        sb = new StringBuilder(feature);
                        sb.setCharAt(7, 'n');
                        feature = sb.toString();
                    }

                    writer.write(id);
                    writer.write('\t');
                    writer.write(form);
                    writer.write('\t');
                    writer.write(lemma);
                    writer.write('\t');
                    writer.write(MSD);
                    writer.write('\t');
                    writer.write(POS);
                    writer.write('\t');
                    writer.write(feature);
                    writer.write('\t');
                    writer.write(head);
                    writer.write('\t');
                    writer.write(label);
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

    public static boolean containsRoot(List<String> sentence, int index)
    {
        for (String line : sentence)
        {
            if (line.split("\t")[index].equals("ROOT"))
                return true;
        }

        return false;
    }

    public static void write(List<List<String>> sentences, String file)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                for (String token : sentence)
                {
                    writer.write(token);
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

    public static boolean containsZ(List<String> sentence)
    {
        for (String token : sentence)
        {
            String[] splitted = token.split("\t");

            if (splitted[4].equals("Z"))
            {
                // System.err.println(token);
                return true;
            }
        }

        return false;
    }

    public static void removeEmptyNodes(String file)
    {
        // System.err.println(file);
        System.err.println("\t" + read(file).size());

        // virtualis node-ok eltavolitasa
        try
        {
            szte.dep.util.RemoveEmptyNodes.processFile(file, file + ".removed-virtuals");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        List<List<String>> sentences = read(file + ".removed-virtuals");
        System.err.println("VAN/ELL\t" + sentences.size());

        // root nelkuli mondatok

        List<List<String>> hasRoot = new LinkedList<List<String>>();

        for (List<String> sentence : sentences)
        {
            if (containsRoot(sentence, 10))
            {
                hasRoot.add(sentence);
            }
        }

        System.err.println("ROOT\t" + hasRoot.size());
        write(hasRoot, file + ".removed-virtuals" + ".has-root");
    }

    public static void removeZ(String file)
    {
        List<List<String>> sentences = read(file);
        // System.err.println(sentences.size());

        // root nelkuli mondatok

        List<List<String>> noZ = new LinkedList<List<String>>();

        for (List<String> sentence : sentences)
        {
            if (!containsZ(sentence))
            {
                noZ.add(sentence);
            }
        }

        System.err.println("no Z\t" + noZ.size());
        write(noZ, file + ".no-z");
    }

    public static void writePosTrain(String file, String out)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (List<String> sentence : read(file))
            {
                for (String token : sentence)
                {
                    String[] splitted = token.split("\t");

                    String form = splitted[1];
                    String MSD = splitted[3];

                    writer.write(form);
                    writer.write("@");
                    writer.write(ResourceHolder.getMSDReducer().reduce(MSD));
                    writer.write(" ");
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

    public static void writeDepTrain(String file, String out)
    {
        // 1 A a a T T SubPOS=f SubPOS=f 3 3 DET DET _ _

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (List<String> sentence : read(file))
            {
                for (String token : sentence)
                {
                    String[] splitted = token.split("\t");

                    String id = splitted[0];
                    String form = splitted[1];
                    String lemma = splitted[2];
                    // splitted[3];
                    String POS = splitted[4];
                    String feature = splitted[5];

                    String head = splitted[6];
                    String label = splitted[7];

                    writer.write(id);
                    writer.write('\t');
                    writer.write(form);
                    writer.write('\t');
                    writer.write(lemma);
                    writer.write('\t');
                    writer.write(lemma);
                    writer.write('\t');
                    writer.write(POS);
                    writer.write('\t');
                    writer.write(POS);
                    writer.write('\t');
                    writer.write(feature);
                    writer.write('\t');
                    writer.write(feature);
                    writer.write('\t');
                    writer.write(head);
                    writer.write('\t');
                    writer.write(head);
                    writer.write('\t');
                    writer.write(label);
                    writer.write('\t');
                    writer.write(label);
                    writer.write("\t_\t_\n");
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

    public static void writeCorpus(String file, String out)
    {
        Map<String, Set<MorAna>> corpus = new TreeMap<String, Set<MorAna>>();

        for (List<String> sentence : read(file))
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");

                String form = splitted[1];
                String lemma = splitted[2];
                String MSD = splitted[3];

                MorAna morAna = new MorAna(lemma, MSD);

                if (!corpus.containsKey(form))
                    corpus.put(form, new TreeSet<MorAna>());

                corpus.get(form).add(morAna);
            }
        }

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Entry<String, Set<MorAna>> enrty : corpus.entrySet())
            {
                writer.write(enrty.getKey());

                for (MorAna m : enrty.getValue())
                {
                    writer.write('\t');
                    writer.write(m.getLemma());
                    writer.write('\t');
                    writer.write(m.getMsd());
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

    public static void writeFreq(String file, String out)
    {
        Map<String, Integer> freq = new TreeMap<String, Integer>();

        for (List<String> sentence : read(file))
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");

                String msd = splitted[3];

                if (!freq.containsKey(msd))
                    freq.put(msd, 0);

                freq.put(msd, freq.get(msd) + 1);
            }
        }

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Entry<String, Integer> enrty : freq.entrySet())
            {
                writer.write(enrty.getKey());
                writer.write('\t');
                writer.write(enrty.getValue());
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

    public static void merge(String[] files, String out)
    {
        List<List<String>> merged = new LinkedList<List<String>>();

        for (String file : files)
        {
            for (List<String> sentence : read("c:/mszny2012/minimalized/" + file + ".minimalized"))
            {
                merged.add(sentence);
            }
        }

        write(merged, "c:/mszny2012/minimalized-merged/" + out + ".minimalized");
    }

    public static Integer[] randomArray(int n)
    {
        Integer[] random = new Integer[n];

        for (int i = 0; i < n; ++i)
        {
            random[i] = i;
        }

        Collections.shuffle(Arrays.asList(random));

        return random;
    }

    public static void split(String file)
    {
        List<List<String>> sentences = read(file);

        int numberOfSentences = 0;
        numberOfSentences = sentences.size();

        System.out.println(numberOfSentences);

        Integer[] ids = randomArray(numberOfSentences);

        int treshold = 0;

        treshold = (int) (numberOfSentences * 0.2);

        System.out.println(treshold);

        List<List<String>> test = new LinkedList<List<String>>();

        List<List<String>> train = new LinkedList<List<String>>();

        // test
        for (int i = 0; i < treshold; ++i)
        {
            test.add(sentences.get(ids[i]));
        }
        write(test, "c:/mszny2012/huge/szeged.test");

        // train
        for (int i = treshold; i < sentences.size(); ++i)
        {
            train.add(sentences.get(ids[i]));
        }
        write(train, "c:/mszny2012/huge/szeged.train");

        // group = aparts[i];
        // n += group.length;
        // System.err.println("|" + group.length + "|" + "\t" + Arrays.toString(group));
        //
        // write(sentences, group, (WORK_DIR + TEST_2_DIR + file + ".test." + i), false);
        // }

        // train
        // Integer[] trainArray = null;
        // ArrayList<Integer> train = null;
        //
        // for (int i = 0; i < aparts.length; ++i) {
        // train = new ArrayList<Integer>();
        // for (int j = 0; j < aparts.length; ++j) {
        // if (i != j) {
        // train.addAll(Arrays.asList(aparts[j]));
        // }
        // }

        // trainArray = train.toArray(new Integer[train.size()]);
        // Arrays.sort(trainArray);
        // System.err.println("|" + trainArray.length + "|" + "\t" + Arrays.toString(trainArray));
        //
        // write(sentences, trainArray, WORK_DIR + TRAIN_2_DIR + file + ".train." + i, true);
        // }
        // System.err.println("||" + n + "||");
    }

    public static void main(String args[])
    {
        // merge(new String[] { "10erv", "10elb", "8oelb" }, "composition");
        // merge(new String[] { "utas", "pfred", "1984" }, "literature");
        // merge(new String[] { "gazdtar", "szerzj" }, "law");
        // merge(new String[] { "nv", "np", "hvg", "mh" }, "newspaper");
        // merge(new String[] { "newsml" }, "newsml");
        // merge(new String[] { "win2000", "cwszt" }, "computer");

        String[] corpuses = new String[] { "10erv", "10elb", "1984", "8oelb", "cwszt",
            "gazdtar", "hvg", "mh", "newsml", "np", "nv", "pfred", "szerzj",
            "utas", "win2000" };

        merge(corpuses, "szeged");

        split("c:/mszny2012/huge/szeged.minimalized");

        // corpuses = new String[] { "composition", "literature", "law", "newspaper", "newsml", "computer" };

        // for (String corpus : corpuses)
        // removeEmptyNodes("c:/mszny2012/" + corpus + ".corpus");

        // for (String corpus : corpuses)
        // removeZ("c:/mszny2012/removed-virtuals-has-root/" + corpus + ".corpus.removed-virtuals.has-root");

        // for (String corpus : corpuses)
        // minimalize(read("c:/mszny2012/removed-virtuals-has-root-no-z/" + corpus
        // + ".corpus.removed-virtuals.has-root.no-z"),
        // "c:/mszny2012/minimalized/" + corpus + ".minimalized");

        // // train
        // for (String corpus : corpuses)
        // writeTrain("c:/mszny2012/minimalized-merged/" + corpus + ".minimalized",
        // "c:/mszny2012/merged-train/" + corpus + ".train");

        // // lex
        // for (String corpus : corpuses)
        // writeCorpus("c:/mszny2012/minimalized-merged/" + corpus + ".minimalized",
        // "c:/mszny2012/merged-lex/" + corpus + ".lex");

        // // freq
        // for (String corpus : corpuses)
        // writeFreq("c:/mszny2012/minimalized-merged/" + corpus + ".minimalized",
        // "c:/mszny2012/merged-freq/" + corpus + ".freq");

        // dep-train
        // for (String corpus : corpuses)
        // writeDepTrain("c:/mszny2012/minimalized-merged/" + corpus
        // + ".minimalized", "c:/mszny2012/merged-dep-train/" + corpus
        // + ".dep.train");

        // pos train
        writePosTrain("c:/mszny2012/huge/szeged.train", "c:/mszny2012/huge/szeged.pos.train");

        // pos lex
        writeCorpus("c:/mszny2012/huge/szeged.train", "c:/mszny2012/huge/szeged.lex");

        // pos freq
        writeFreq("c:/mszny2012/huge/szeged.train", "c:/mszny2012/huge/szeged.freq");

        // dep-train
        writeDepTrain("c:/mszny2012/huge/szeged.train", "c:/mszny2012/hugews/szeged.dep.train");
    }
}
EOF
mkdir -p szte/magyarlanc/resource && cat > szte/magyarlanc/resource/ResourceHolder.java <<'EOF'
package szte.magyarlanc.resource;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer.DefaultWordTokenizer;
import edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer.WordTokenizer;
import edu.stanford.nlp.tagger.maxent.SzteMaxentTagger;

import is2.parser.Options;
import is2.parser.Parser;

import rfsa.RFSA;

import szte.magyarlanc.MorAna;
import szte.pos.converter.CoNLLFeaturesToMSD;
import szte.pos.converter.KRToMSD;
import szte.pos.converter.MSDReducer;
import szte.pos.converter.MSDToCoNLLFeatures;
import szte.splitter.HunSplitter;

import data.Data;

public class ResourceHolder
{
    public enum SZK_VERSION
    {
        /**
         * SzK 2.3
         */
        v23,
        /**
         * SzK 2.5
         */
        v25
    }

    /**
     * SET THE VERSION OF THE USED SZEGED CORPUS
     */
    private static SZK_VERSION ver = SZK_VERSION.v25;

    private static String POS_MODEL = null;
    private static String CORPUS = null;
    private static String FREQUENCIES = null;

    static
    {
        switch (ver)
        {
            case v23:
                POS_MODEL = "23.model";
                CORPUS = "23.lex";
                FREQUENCIES = "23.freq";
                break;
            case v25:
                POS_MODEL = "25.model";
                CORPUS = "25.lex";
                FREQUENCIES = "25.freq";
                break;
        }
    }

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
                maxentTagger = new SzteMaxentTagger(POS_MODEL);
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
mkdir -p szte/magyarlanc/resource && cat > szte/magyarlanc/resource/Util.java <<'EOF'
package szte.magyarlanc.resource;

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

import szte.magyarlanc.MorAna;

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
mkdir -p szte/magyarlanc && cat > szte/magyarlanc/Settings.java <<'EOF'
package szte.magyarlanc;

public class Settings
{
    public static final String DEFAULT_NOUN = "Nn-sn";
    public static final String DEFAULT_SEPARATOR = "\t";
}
EOF
mkdir -p szte/magyarlanc/util && cat > szte/magyarlanc/util/CorpusStats.java <<'EOF'
package szte.magyarlanc.util;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class CorpusStats
{
    public static Map<String, Integer> stat(List<List<String>> document, int columnIndex)
    {
        Map<String, Integer> stat = new TreeMap<String, Integer>();

        for (List<String> sentence : document)
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");
                // if (splitted[4].equals("VAN") || splitted[4].equals("ELL")) {
                if (!stat.containsKey(splitted[columnIndex]))
                    stat.put(splitted[columnIndex], 0);
                stat.put(splitted[columnIndex], stat.get(splitted[columnIndex]) + 1);
                // }
            }
        }

        return stat;
    }

    public static void main(String[] args)
    {
        String path = "./data/conll/corrected_features/";

        String[] files = new String[] { "8oelb", "10elb", "10erv", "1984", "cwszt",
            "gazdtar", "hvg", "mh", "newsml", "np", "nv", "pfred", "szerzj",
            "utas", "win2000" };

        String extension = ".conll-2009-msd";
    }
}
EOF
mkdir -p szte/magyarlanc/util && cat > szte/magyarlanc/util/Eval.java <<'EOF'
package szte.magyarlanc.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import is2.parser.Parser;

import szte.dep.parser.MateParserWrapper;
import szte.magyarlanc.Magyarlanc;
import szte.magyarlanc.resource.ResourceHolder;

public class Eval
{
    public static void writePosTrain(String file, String out)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (List<String> sentence : read(file))
            {
                for (String token : sentence)
                {
                    String[] splitted = token.split("\t");

                    String form = splitted[1];
                    String MSD = splitted[3];

                    writer.write(form);
                    writer.write("@");
                    writer.write(ResourceHolder.getMSDReducer().reduce(MSD));
                    writer.write(" ");
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

    public static void writeDepTrain(String file, String out)
    {
        // 1 A a a T T SubPOS=f SubPOS=f 3 3 DET DET _ _

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (List<String> sentence : read(file))
            {
                for (String token : sentence)
                {
                    String[] splitted = token.split("\t");

                    String id = splitted[0];
                    String form = splitted[1];
                    String lemma = splitted[2];
                    // splitted[3];
                    String POS = splitted[4];
                    String feature = splitted[5];

                    String head = splitted[6];
                    String label = splitted[7];

                    writer.write(id);
                    writer.write('\t');
                    writer.write(form);
                    writer.write('\t');
                    writer.write(lemma);
                    writer.write('\t');
                    writer.write(lemma);
                    writer.write('\t');
                    writer.write(POS);
                    writer.write('\t');
                    writer.write(POS);
                    writer.write('\t');
                    writer.write(feature);
                    writer.write('\t');
                    writer.write(feature);
                    writer.write('\t');
                    writer.write(head);
                    writer.write('\t');
                    writer.write(head);
                    writer.write('\t');
                    writer.write(label);
                    writer.write('\t');
                    writer.write(label);
                    writer.write("\t_\t_\n");
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

    public static int[] getOffsets(Integer[] ids, int n)
    {
        if (n < 1)
        {
            System.err.println("N must be grater than 0.");
            System.exit(1);
        }

        if (n > ids.length)
        {
            System.err.println("N must be less than the length of the array.");
            System.exit(1);
        }

        int[] offsets = new int[n + 1];

        double length = (double) ids.length / n;

        for (int i = 0; i < n; ++i)
        {
            offsets[i] = (int) Math.round(i * length);
        }
        offsets[n] = ids.length;

        return offsets;
    }

    public static Integer[][] apart(Integer[] ids, int n)
    {
        Integer[][] aparted = new Integer[n][];

        int[] offsets = getOffsets(ids, n);

        for (int i = 0; i < offsets.length - 1; ++i)
        {
            aparted[i] = new Integer[offsets[i + 1] - offsets[i]];
            int index = 0;
            for (int j = offsets[i]; j < offsets[i + 1]; ++j)
            {
                aparted[i][index] = ids[j];
                ++index;
            }
            Arrays.sort(aparted[i]);
        }

        return aparted;
    }

    public static Integer[] randomArray(int n)
    {
        Integer[] random = new Integer[n];

        for (int i = 0; i < n; ++i)
        {
            random[i] = i;
        }

        Collections.shuffle(Arrays.asList(random));

        return random;
    }

    public static void write(List<List<String>> sentences, Integer[] ids, String out)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (int id : ids)
            {
                for (String token : sentences.get(id))
                {
                    writer.write(token);
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

    public static void write10Fold(String file)
    {
        List<List<String>> sentences = read(file);
        int numberOfSentences = sentences.size();

        System.err.println(file + "\t" + sentences.size() + " sentences");

        Integer[] ids = randomArray(numberOfSentences);

        int n = 0;
        Integer[][] aparts = apart(ids, 10);

        // test
        for (int i = 0; i < aparts.length; ++i)
        {
            Integer[] group = aparts[i];
            n += group.length;
            System.err.println("|" + group.length + "|" + "\t" + Arrays.toString(group));

            write(sentences, group, (file + ".test." + i));
        }

        // train
        for (int i = 0; i < aparts.length; ++i)
        {
            ArrayList<Integer> train = new ArrayList<Integer>();

            for (int j = 0; j < aparts.length; ++j)
            {
                if (i != j)
                {
                    train.addAll(Arrays.asList(aparts[j]));
                }
            }

            Integer[] trainArray = train.toArray(new Integer[train.size()]);
            Arrays.sort(trainArray);
            System.err.println("|" + trainArray.length + "|" + "\t" + Arrays.toString(trainArray));

            write(sentences, trainArray, (file + ".train." + i));
        }
        System.err.println("||" + n + "||");
    }

    public static List<List<String>> read(String file)
    {
        List<List<String>> sentences = new LinkedList<List<String>>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            List<String> sentence = new ArrayList<String>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.equals(""))
                {
                    sentences.add(sentence);
                    sentence = new ArrayList<String>();
                }
                else
                {
                    sentence.add(line);
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return sentences;
    }

    public static String[] getColumn(List<String> sentence, int index)
    {
        String[] forms = new String[sentence.size()];

        for (int i = 0; i < sentence.size(); ++i)
        {
            String[] splitted = sentence.get(i).split("\t");
            forms[i] = splitted[index];
        }

        return forms;
    }

    public static void prediatePOS(String file, String out)
    {
        List<List<String>> sentences = read(file);
        System.out.println(sentences.size());

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                String[] form = getColumn(sentence, 1);
                String[] lemma = getColumn(sentence, 2);
                String[] MSD = getColumn(sentence, 3);

                String[][] morph = Magyarlanc.morphParseSentence(form);

                String[] head = getColumn(sentence, 6);
                String[] label = getColumn(sentence, 7);

                for (int i = 0; i < form.length; ++i)
                {
                    writer.write(form[i]);
                    writer.write('\t');
                    writer.write(lemma[i]);
                    writer.write('\t');
                    writer.write(MSD[i]);
                    writer.write('\t');
                    writer.write(morph[i][0]);
                    writer.write('\t');
                    writer.write(morph[i][1]);

                    writer.write('\t');
                    writer.write(head[i]);
                    writer.write('\t');
                    writer.write(label[i]);

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

    public static void eval(String file)
    {
        int tp = 0;
        int cntr = 0;

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                if (!line.equals(""))
                {
                    String[] splitted = line.split("\t");
                    //System.out.println(line);
                    if (splitted[1].equalsIgnoreCase(splitted[3]) && splitted[2].equals(splitted[4]))
                    {
                        ++tp;
                    }
                    else
                    {
                        // System.out.println(line);
                    }
                    ++cntr;
                }
                System.out.println((double) tp / cntr);
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        System.out.println((double) tp / cntr);
    }

    public static void predicateDep(Parser parser, String file, String out)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            int cntr = 0;
            for (List<String> sentence : read(file))
            {
                if (sentence.size() < 100)
                {
                    String[] form = getColumn(sentence, 0);

                    // String[] lemma = getColumn(sentence, 2);
                    String[] lemma = getColumn(sentence, 3);

                    // String[] POS = getColumn(sentence, 4);
                    String[] MSD = getColumn(sentence, 4);

                    String[] head = getColumn(sentence, 5);
                    String[] label = getColumn(sentence, 6);

                    String[][] parsed = MateParserWrapper.parseSentence(form, lemma, MSD);

                    for (int i = 0; i < form.length; ++i)
                    {
                        writer.write(form[i]);
                        writer.write('\t');
                        writer.write(head[i]);
                        writer.write('\t');
                        writer.write(label[i]);
                        writer.write('\t');
                        writer.write(parsed[i][0]);
                        writer.write('\t');
                        writer.write(parsed[i][1]);
                        writer.write('\n');
                    }
                    writer.write('\n');
                    System.out.println(++cntr);
                }
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void evalPred(String file)
    {
        List<List<String>> sentences = read(file);

        int counter = 0;

        int las = 0;
        int uas = 0;

        for (List<String> sentence : sentences)
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");
                if (splitted[1].equals(splitted[3]))
                {
                    ++uas;
                    if (splitted[2].equals(splitted[4]))
                    {
                        ++las;
                    }
                }
                ++counter;
            }
        }

        DecimalFormat df = new DecimalFormat("#.####");

        System.out.print("\t" + df.format((double) las / counter) + "/" + df.format((double) uas / counter));
    }

    public static void main(String[] args)
    {
        // for (String corpus : new String[] { "composition", "computer", "law",
        // "literature", "newsml", "newspaper" }) {
        //
        // // write10Fold("c:/mszny2012/10fold/" + corpus + ".corpus");
        //
        // for (int i = 0; i < 10; ++i) {
        // // writeDepTrain("c:/mszny2012/10fold/" + corpus + ".corpus.train." + i,
        // // "c:/mszny2012/10fold/" + corpus + ".dep.train." + i);
        //
        // writePosTrain("c:/mszny2012/10fold/" + corpus + ".corpus.train." + i,
        // "c:/mszny2012/10fold/" + corpus + ".pos.train." + i);
        //
        // }
        // }
        // Magyarlanc.init();

        // String model = "newspaper";

        // for (String test : new String[] { /* "composition", "computer", */"law"/*
        // * ,
        // * "literature"
        // * ,
        // * "newsml"
        // * ,
        // * "newspaper"
        // */}) {
        //
        // prediatePOS("./data/resource/mszny/test/" + test + ".corpus",
        // "./data/resource/mszny/predicated-pos/" + test + "2." + model);
        //
        // eval("./data/resource/mszny/predicated-pos/" + test + "2." + model);
        // }

        // String test = "szeged";
        //
        // String model = "szeged";

        // long start = System.currentTimeMillis();
        // prediatePOS("./data/resource/mszny/test/" + test + ".corpus",
        // "./data/resource/mszny/predicated-pos/" + test + "2." + model);
        // System.err.println((System.currentTimeMillis() - start) / 1000 + " secs.");

        // eval("./data/resource/mszny/predicated-pos/" + test + "." + model);

        // for (String model : new String[] { "composition", "computer",
        // "literature",
        // "law", "newsml", "newspaper" }) {
        //
        // for (String test : new String[] { "composition", "computer",
        // "literature", "law", "newsml", "newspaper" }) {
        // evalPred("./data/resource/mszny/predicated-dep/" + test + "." + model);
        // }
        // System.out.println();
        // }
        //
        // System.exit(0);
        // Parser parser = null;
        //
        // for (String model : new String[] { "composition", "computer",
        // "literature",
        // "law", "newsml", "newspaper" }) {
        // parser = new Parser(new Options(new String[] { "-model",
        // "./data/resource/mszny/" + model + ".dep.model", "-cores", "8" }));
        //
        // for (String test : new String[] { "composition", "computer",
        // "literature", "law", "newsml", "newspaper" }) {
        //
        // predicateDep(parser, "./data/resource/mszny/test/" + test + ".corpus",
        // "./data/resource/mszny/predicated-dep/" + test + "." + model);
        // }
        // parser = null;
        // }

        // Parser parser = null;
        //
        // parser = new Parser(new Options(new String[] { "-model",
        // "./data/resource/mszny/szeged.dep.model", "-cores", "8" }));
        //
        // predicateDep(parser, "./data/resource/mszny/test/szeged.szeged",
        // "./data/resource/mszny/predicated-dep/szeged.szeged.szeged");

        // evalPred("./data/resource/mszny/predicated-dep/szeged.szeged.szeged");

        // eval("./data/resource/mszny/predicated-pos/szeged.szeged");

        eval("c:/p2.ppp");
    }
}
EOF
mkdir -p szte/magyarlanc/util && cat > szte/magyarlanc/util/FxDepParse.java <<'EOF'
package szte.magyarlanc.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;

import is2.data.SentenceData09;
import is2.parser.Options;
import is2.parser.Parser;

public class FxDepParse
{
    static final String WORK_DIR = "./data/fx-dep-parse/";
    static final String MODEL_DIR = "model/";
    static final String REMOVED_EMPTY_NODES = "removed_empty_nodes.";
    static final String HAS_ROOT = "has_root.";

    private static Parser parser = null;

    static int truePositive = 0;
    static int falseNegative = 0;
    static int falsePositive = 0;

    public enum Value
    {
        TP, FP, FN, FP_FN
    }

    public static String[][] parseSentence(String[] form, String[] lemma, String[] pos, String[] feature)
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

        sentenceData09 = parser.apply(sentenceData09);

        String[][] result = new String[sentenceData09.plabels.length][2];

        for (int i = 0; i < sentenceData09.plabels.length; ++i)
        {
            result[i][0] = String.valueOf(sentenceData09.pheads[i]);
            result[i][1] = sentenceData09.plabels[i];
        }

        return result;
    }

    public static void split(String file)
    {
        List<List<String>> sentences = read(WORK_DIR + HAS_ROOT + REMOVED_EMPTY_NODES + file);

        // a
        write(sentences.subList(0, sentences.size() / 2), WORK_DIR + "a." + file);
        System.err.println("a\t" + read(WORK_DIR + "a." + file).size());

        // b
        write(sentences.subList(sentences.size() / 2, sentences.size()), WORK_DIR + "b." + file);
        System.err.println("b\t" + read(WORK_DIR + "b." + file).size());
    }

    public static List<List<String>> read(String file)
    {
        List<List<String>> sentences = new LinkedList<List<String>>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            List<String> sentence = new LinkedList<String>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (!line.equals(""))
                {
                    sentence.add(line);
                }
                else if (line.equals(""))
                {
                    sentences.add(sentence);
                    sentence = new LinkedList<String>();
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return sentences;
    }

    public static void write(List<List<String>> sentences, String file)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                if (sentence.size() < 100)
                {
                    for (String token : sentence)
                    {
                        writer.write(token);
                        writer.write('\n');
                    }
                    writer.write('\n');
                }
                else
                {
                    System.err.println("|" + sentence.size() + "|");
                    for (String line : sentence)
                        System.err.println(line);
                    System.err.println();
                }
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static boolean containsRoot(List<String> sentence, int index)
    {
        for (String line : sentence)
        {
            if (line.split("\t")[index].equals("ROOT"))
                return true;
        }

        return false;
    }

    public static void prepare(String file)
    {
        System.err.println(file);
        System.err.println(read(WORK_DIR + file).size());

        // virtualis node-ok eltavolitasa
        try
        {
            szte.dep.util.RemoveEmptyNodes.processFile(WORK_DIR + file, WORK_DIR + REMOVED_EMPTY_NODES + file);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        List<List<String>> sentences = read(WORK_DIR + REMOVED_EMPTY_NODES + file);
        System.err.println(sentences.size());

        // root nelkuli mondatok
        List<List<String>> hasRoot = new LinkedList<List<String>>();

        for (List<String> sentence : sentences)
        {
            if (containsRoot(sentence, 10))
            {
                hasRoot.add(sentence);
            }
        }

        System.err.println(hasRoot.size());
        write(hasRoot, WORK_DIR + HAS_ROOT + REMOVED_EMPTY_NODES + file);

        sentences = null;
        split(file);
    }

    public static String[] getColumn(List<String> sentence, int index)
    {
        String[] column = new String[sentence.size()];

        for (int i = 0; i < column.length; ++i)
        {
            column[i] = sentence.get(i).split("\t")[index];
        }

        return column;
    }

    public static void predicate(String file, String model, String pred)
    {
        if (parser == null)
        {
            parser = new Parser(new Options(new String[] { "-model", model, "-cores", "1" }));
        }

        List<List<String>> sentences = read(file);

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(pred), "UTF-8"));

            int cntr = 0;

            for (List<String> sentence : sentences)
            {
                if (sentence.size() < 100)
                {
                    String[][] parsed = parseSentence(getColumn(sentence, 1),
                                                      getColumn(sentence, 2),
                                                      getColumn(sentence, 4),
                                                      getColumn(sentence, 6));

                    for (int i = 0; i < getColumn(sentence, 10).length; ++i)
                    {
                        writer.write(getColumn(sentence, 1)[i]);
                        writer.write('\t');
                        writer.write(getColumn(sentence, 8)[i]);
                        writer.write('\t');
                        writer.write(getColumn(sentence, 10)[i]);
                        writer.write('\t');
                        writer.write(parsed[i][0]);
                        writer.write('\t');
                        writer.write(parsed[i][1]);
                        writer.write('\n');
                    }
                    writer.write('\n');
                    System.err.println(++cntr + "/" + sentences.size());
                }
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void reduce(String file)
    {
        List<List<String>> sentences = read(WORK_DIR + file);

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(WORK_DIR + file + ".shorted"), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                for (int i = 0; i < sentence.size(); ++i)
                {
                    writer.write(getColumn(sentence, 0)[i]);
                    writer.write('\t');
                    writer.write(getColumn(sentence, 1)[i]);
                    writer.write('\t');
                    writer.write(getColumn(sentence, 8)[i]);
                    writer.write('\t');
                    writer.write(getColumn(sentence, 10)[i]);
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

    public static Value evalToken(String parent, String pparent, String label, String plabel)
    {
        // 2 2 OBJFX OBJFX
        if (parent.equals(pparent) && label.equals(plabel))
        {
            return Value.TP;
        }

        // O OBJFX
        if (!label.endsWith("FX") && plabel.endsWith("FX"))
        {
            return Value.FP;
        }

        // OBJFX O
        if (label.endsWith("FX") && !plabel.endsWith("FX"))
        {
            return Value.FN;
        }

        // OBJFX ATTFX
        if (label.endsWith("FX") && plabel.endsWith("FX") && !label.equals(plabel))
        {
            return Value.FP_FN;
        }

        // 2 5 OBJFX OBJFX
        if (label.equals(plabel) && !parent.equals(pparent))
        {
            return Value.FP;
        }

        return null;
    }

    public static void evalSentence(String[] parent, String[] pparent, String[] label, String[] plabel, String[] form)
    {
        for (int i = 0; i < parent.length; ++i)
        {
            if (label[i].endsWith("FX") || plabel[i].endsWith("FX"))
            {
                Value value = evalToken(parent[i], pparent[i], label[i], plabel[i]);

                switch (value)
                {
                    case TP:
                        ++truePositive;
                        break;
                    case FP:
                        ++falsePositive;
                        break;
                    case FN:
                        ++falseNegative;
                        break;
                    case FP_FN:
                        ++falseNegative;
                        ++falsePositive;
                        break;
                }

                // System.err.println(parent[i] + "\t" + pparent[i] + "\t" + label[i] + "\t" + plabel[i] + "\t" + value + "\t" + form[i]);
            }
        }
    }

    public static void eval(String file)
    {
        List<List<String>> sentences = read(file);

        truePositive = 0;
        falseNegative = 0;
        falsePositive = 0;

        for (List<String> sentence : sentences)
        {
            evalSentence(getColumn(sentence, 1),
                         getColumn(sentence, 3),
                         getColumn(sentence, 2),
                         getColumn(sentence, 4),
                         getColumn(sentence, 0));
        }

        System.err.println("TP: " + truePositive + "\tFN: " + falseNegative + "\tFP: " + falsePositive);

        float precision = (float) truePositive / (truePositive + falsePositive);
        float recall = (float) truePositive / (truePositive + falseNegative);

        System.err.print("P: " + precision);
        System.err.print("\tR: " + recall);

        System.err.println("\tF: " + 2 * precision * (recall / (precision + recall)));
        // System.err.println("F: " + (float) (2 * truePositive) / (2 * truePositive + falsePositive + falseNegative));
    }

    public static void LAS(String file)
    {
        List<List<String>> sentences = read(file);

        int counter = 0;
        int las = 0;
        for (List<String> sentence : sentences)
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");
                if (splitted[1].equals(splitted[3]) && splitted[2].equals(splitted[4]))
                {
                    ++las;
                }
                ++counter;
            }
        }
        System.out.println("LAS:" + (double) las / counter);
    }

    public static void UAS(String file)
    {
        List<List<String>> sentences = read(file);

        int counter = 0;
        int uas = 0;
        for (List<String> sentence : sentences)
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");
                if (splitted[1].equals(splitted[3]))
                {
                    ++uas;
                }
                ++counter;
            }
        }
        System.out.println("UAS:" + (double) uas / counter);
    }

    public static void rewriteAnnotation(String corpusFile, String correctedFile, String out)
    {
        List<List<String>> corpus = read(WORK_DIR + corpusFile);
        List<List<String>> corrected = read(WORK_DIR + correctedFile);

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(WORK_DIR + out), "UTF-8"));

            for (int i = 0; i < corpus.size(); ++i)
            {
                for (int j = 0; j < corpus.get(i).size(); ++j)
                {
                    String[] splittedCorpus = corpus.get(i).get(j).split("\t");
                    String[] splittedCorrected = corrected.get(i).get(j).split("\t");

                    writer.write(splittedCorpus[0]);
                    for (int k = 1; k < 10; ++k)
                    {
                        writer.write('\t');
                        writer.write(splittedCorpus[k]);
                    }
                    writer.write('\t');
                    writer.write(splittedCorrected[3]);
                    writer.write('\t');
                    writer.write(splittedCorrected[3]);
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

    public static int[] getFoldOffsets(String file)
    {
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            int offset = 0;
            int sentenceCounter = 0;

            for (String line; (line = reader.readLine()) != null; )
            {
                sentenceCounter += Integer.parseInt(line.split("\t")[1]);
                if ((++offset % 120) == 0)
                {
                    System.out.println(offset + "\t" + sentenceCounter);
                }
            }

            System.out.println(offset);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return null;
    }

    public static boolean containsVirtualNode(List<String> sentence)
    {
        String[] forms = getColumn(sentence, 1);

        for (String form : forms)
            if (form.equals("_"))
                return true;

        return false;
    }

    public static void writeFolds(String file)
    {
        List<List<String>> sentences = read(file);

        int[] foldOffsets = new int[] { 0, 688, 1470, 2221, 2848, 4113, 4755, 5666, 6961, 8190, sentences.size() };

        for (int i = 0; i < foldOffsets.length - 1; ++i)
        {
            try
            {
                Writer testWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("./data/objfx/10fold-removed-virtuals2/law." + i + ".test"), "UTF-8"));
                Writer trainWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("./data/objfx/10fold-removed-virtuals2/law." + i + ".train"), "UTF-8"));

                for (int j = 0; j < sentences.size(); ++j)
                {
                    // virtualokat tart mondatok nekiul
                    if (!containsVirtualNode(sentences.get(j)) && sentences.get(j).size() < 200)
                    {
                        if (j >= foldOffsets[i] && j <= (foldOffsets[i + 1] - 1))
                        {
                            for (String s : sentences.get(j))
                            {
                                // testWriter.write(s + "\t_\t_\n");
                                testWriter.write(s + "\n");
                            }
                            testWriter.write("\n");
                        }
                        else
                        {
                            for (String s : sentences.get(j))
                            {
                                // trainWriter.write(s + "\t_\t_\n");
                                trainWriter.write(s + "\n");
                            }
                            trainWriter.write("\n");
                        }
                    }
                }

                testWriter.flush();
                trainWriter.flush();
                testWriter.close();
                trainWriter.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            // System.out.println(foldOffsets[i] + "\t" + ));
        }
    }

    public static void main(String[] args)
    {
        // rewriteAnnotation("gazdtar.dep.fx.out", "gazdtar.dep.fx.out_jav",
        // "gazdtar.dep.fx.out.corrected");

        // for (String file : new String[] { /* "gazdtar", */"szerzj" })
        // prepare(file + ".dep.fx.out");

        // for (int i = 0; i < 10; ++i) {
        // predicate("./data/objfx/10fold-removed-virtuals/law." + i + ".test",
        // "./data/objfx/10fold-removed-virtuals/law." + i + ".model",
        // "./data/objfx/10fold-removed-virtuals/law." + i + ".pred");
        // }

        // reduce("szerzj.dep.fx.out");

        // evalSentence(new String[] { "12", "14", "14", "14", "14" }, new String[]
        // {
        // "12", "15", "14", "14", "14" }, new String[] { "OBJFX", "OBJFX",
        // "OBJFX", "OBJ", "OBJFX" }, new String[] { "OBJFX", "OBJFX", "OBJ",
        // "OBJFX", "ATTFX" });

        for (int i = 0; i < 10; ++i)
        {
            eval("./data/objfx/10fold-removed-virtuals/law." + i + ".pred");
        }

        // LAS("./data/objfx/10fold-removed-virtuals/law.0123456789.pred");
        // UAS("./data/objfx/10fold-removed-virtuals/law.0123456789.pred");

        // getFoldOffsets("./data/objfx/doc-sentences.txt");
        // writeFolds("./data/objfx/law.out");
    }
}
EOF
mkdir -p szte/magyarlanc/util && cat > szte/magyarlanc/util/FXIAA.java <<'EOF'
package szte.magyarlanc.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class FXIAA
{
    static int TP = 0;
    static int FP = 0;
    static int FN = 0;

    public static Integer[][] getPhrases(String[] sentence)
    {
        boolean inPhrase = false;

        ArrayList<Integer> start = new ArrayList<Integer>();
        ArrayList<Integer> end = new ArrayList<Integer>();

        for (int i = 0; i < sentence.length; ++i)
        {
            if (!inPhrase)
            {
                if ((i == 0 && !sentence[i].equals("O")) || (i > 0 && !sentence[i].equals("O") && !sentence[i - 1].equals(sentence[i])))
                {
                    start.add(i);
                    inPhrase = true;
                }
            }

            if (inPhrase)
            {
                if ((i == sentence.length - 1) || (!sentence[i].equals(sentence[i + 1])))
                {
                    end.add(i);
                    inPhrase = false;
                }
            }
        }

        Integer[][] phrases = new Integer[start.size()][2];

        for (int i = 0; i < start.size(); ++i)
        {
            phrases[i][0] = start.get(i);
            phrases[i][1] = end.get(i);
        }

        return phrases;
    }

    public static boolean containsPhrase(Integer[] phrase, Integer[][] phrases)
    {
        for (int i = 0; i < phrases.length; ++i)
        {
            if ((phrases[i][0] == phrase[0]) && (phrases[i][1] == phrase[1]))
            {
                return true;
            }
        }

        return false;
    }

    public static void evalSentence(String[] e, String[] p)
    {
        Integer[][] ePhrases = getPhrases(e);
        Integer[][] pPhrases = getPhrases(p);

        for (int i = 0; i < ePhrases.length; ++i)
        {
            if (containsPhrase(ePhrases[i], pPhrases))
            {
                ++TP;
            }
            if (!containsPhrase(ePhrases[i], pPhrases))
            {
                ++FN;
            }
        }

        for (int i = 0; i < pPhrases.length; ++i)
        {
            if (!containsPhrase(pPhrases[i], ePhrases))
            {
                ++FP;
            }
        }
    }

    public static List<List<String>> read(String file)
    {
        List<List<String>> sentences = new LinkedList<List<String>>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            List<String> sentence = new ArrayList<String>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.equals(""))
                {
                    sentences.add(sentence);
                    sentence = new ArrayList<String>();
                }
                else
                {
                    sentence.add(line.split("\t")[1]);
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return sentences;
    }

    public static void eval(List<List<String>> e, List<List<String>> p)
    {
        for (int i = 0; i < e.size(); ++i)
        {
            evalSentence(e.get(i).toArray(new String[e.get(i).size()]), p.get(i).toArray(new String[p.get(i).size()]));
        }
    }

    public static void eval(String e, String p)
    {
        eval(read(e), read(p));
        System.err.print(e + " -> " + p);
        System.err.print("\nTP: " + TP);
        System.err.print("\tFP: " + FP);
        System.err.println("\tFN: " + FN);
        double precision = (TP / (double) (TP + FP));
        double recall = (TP / (double) (TP + FN));
        System.err.println("Prec: " + precision);
        System.err.println("Rec: " + recall);
        System.err.println("F: " + 2 * (precision * recall) / (precision + recall));

        TP = 0;
        FP = 0;
        FN = 0;
    }

    public static void main(String[] args)
    {
        eval("./data/fxiaa/vera.fx.iob", "./data/fxiaa/adam.fx.iob");
    }
}
EOF
mkdir -p szte/magyarlanc/util && cat > szte/magyarlanc/util/SafeReader.java <<'EOF'
package szte.magyarlanc.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;

public class SafeReader
{
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
}
EOF
mkdir -p szte/magyarlanc/util && cat > szte/magyarlanc/util/SzK25.java <<'EOF'
package szte.magyarlanc.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import szte.magyarlanc.Magyarlanc;
import szte.magyarlanc.MorAna;
import szte.magyarlanc.resource.ResourceHolder;
import szte.magyarlanc.resource.Util;

public class SzK25
{
    private static Writer writer = null;

    private static final int WORD_FORM_INDEX = 3;
    private static final int LEMMA_INDEX = 4;
    private static final int MSD_INDEX = 5;

    private static final String DEFAULT_ADJECTIVE_MSD = "Afp-sn";
    private static final String DEFAULT_NUMERAL_MSD = "Mc-snd";
    private static final String DEFAULT_NUMERAL_FRACTION_MSD = "Mf-snd";
    private static final String DEFAULT_NOUN_MSD = "Np-sn";

    private static final float DEFAULR_TRAIN_TRESHOLD = 0.8f;

    static Map<String, Set<MorAna>> readLexicon(String file)
    {
        Map<String, Set<MorAna>> lexicon = new TreeMap<String, Set<MorAna>>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                Set<MorAna> morAnas = new TreeSet<MorAna>();

                String[] splitted = line.split("\t");
                for (int i = 1; i < splitted.length - 1; i++)
                {
                    morAnas.add(new MorAna(splitted[i], splitted[i + 1]));
                    i++;
                }

                lexicon.put(splitted[0], morAnas);
            }

            reader.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return lexicon;
    }

    public static Map<String, Integer> readIntMap(String file, String separator, boolean isCaseSensitive)
    {
        Map<String, Integer> map = new HashMap<String, Integer>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                line = line.trim();

                if (!isCaseSensitive)
                {
                    line = line.toLowerCase();
                }

                String[] splitted = line.split(separator);

                if (splitted.length > 1)
                {
                    if (!map.containsKey(splitted[0]))
                    {
                        map.put(splitted[0], 0);
                    }

                    try
                    {
                        map.put(splitted[0], Integer.parseInt(splitted[1]));
                    }
                    catch (Exception e)
                    {
                    }
                }
            }

            reader.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return map;
    }

    /**
     * Builds a String from the given String array and separator.
     *
     * @param parts
     *          array of String parts
     * @param separator
     *          separator between the parts
     * @return
     */
    private static String sallow(String[] parts, String separator)
    {
        StringBuilder sb = new StringBuilder();

        for (String part : parts)
        {
            sb.append(part).append(separator);
        }

        return sb.toString().trim();
    }

    /**
     * Post processes a line from the corpus.
     *
     * @param line
     *          line from the corpus
     * @return post processed line
     */
    private static String postProcess(String line)
    {
        String[] split = line.split("\t");

        // és
        if (split[LEMMA_INDEX].equals("és"))
        {
            split[MSD_INDEX] = "Ccsw";
        }

        // &
        if (split[LEMMA_INDEX].equals("&"))
        {
            split[MSD_INDEX] = "K";
        }

        // sallow and replace spaces
        return sallow(split, "\t");
    }

    /**
     * Splits an MW adjective.
     *
     * @param line
     * @return MW split
     */
    private static String[] splitA(String line)
    {
        String[] splittedLine = line.split("\t");
        // word form
        String[] splittedWordForm = splittedLine[WORD_FORM_INDEX].split(" ");
        // lemma
        String[] splittedLemma = splittedLine[LEMMA_INDEX].split(" ");

        String[] lines = new String[splittedWordForm.length];

        for (int i = 0; i < splittedWordForm.length; ++i)
        {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < WORD_FORM_INDEX; ++j)
            {
                sb.append(splittedLine[j]).append('\t');
            }
            sb.append(splittedWordForm[i]).append('\t');
            sb.append(splittedLemma[i]).append('\t');
            if (i < splittedWordForm.length - 1)
            {
                // default MSD for the last token
                sb.append(DEFAULT_ADJECTIVE_MSD);
            }
            else
            {
                // original MSD for the last token
                sb.append(splittedLine[MSD_INDEX]);
            }
            lines[i] = sb.toString();
        }

        return lines;
    }

    /**
     * Splits an MW numerals.
     *
     * @param line
     * @return MW split
     */
    private static String[] splitM(String line)
    {
        String[] splittedLine = line.split("\t");
        // word form
        String[] splittedWordForm = splittedLine[WORD_FORM_INDEX].split(" ");
        // lemma
        String[] splittedLemma = splittedLine[LEMMA_INDEX].split(" ");

        String[] lines = new String[splittedWordForm.length];

        for (int i = 0; i < splittedWordForm.length; ++i)
        {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < WORD_FORM_INDEX; ++j)
            {
                sb.append(splittedLine[j]).append('\t');
            }
            sb.append(splittedWordForm[i]).append('\t');
            sb.append(splittedLemma[i]).append('\t');
            if (i < splittedWordForm.length - 1)
            {
                if (splittedLine[WORD_FORM_INDEX].contains(","))
                {
                    // fraction
                    sb.append(DEFAULT_NUMERAL_FRACTION_MSD);
                }
                else
                {
                    sb.append(DEFAULT_NUMERAL_MSD);
                }
            }
            else
            {
                // original MSD for the last token
                sb.append(splittedLine[MSD_INDEX]);
            }
            lines[i] = sb.toString();
        }

        return lines;
    }

    /**
     * Splits an MW noun.
     *
     * @param line
     * @return MW split
     */
    private static String[] splitN(String line)
    {
        String[] splittedLine = line.split("\t");
        // word form
        String[] splittedWordForm = splittedLine[WORD_FORM_INDEX].split(" ");
        // lemma
        String[] splittedLemma = splittedLine[LEMMA_INDEX].split(" ");

        String[] lines = new String[splittedWordForm.length];

        // System.err.println(line);
        for (int i = 0; i < splittedWordForm.length; ++i)
        {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < WORD_FORM_INDEX; ++j)
            {
                sb.append(splittedLine[j]).append('\t');
            }
            sb.append(splittedWordForm[i]).append('\t');
            sb.append(splittedLemma[i]).append('\t');
            if (i < splittedWordForm.length - 1)
            {
                sb.append(DEFAULT_NOUN_MSD).append('\t');
            }
            else
            {
                // original MSD for the last token
                sb.append(splittedLine[MSD_INDEX]);
            }
            lines[i] = sb.toString();
        }

        return lines;
    }

    /**
     * Splits an MW.
     *
     * @param line
     * @return MW split
     */
    private static String[] splitMW(String line)
    {
        if (line.split("\t")[WORD_FORM_INDEX].contains(" "))
        {
            // System.err.println(line.split("\t")[MSD_INDEX]);
            System.err.println(line);

            String[] split = null;

            switch (line.split("\t")[MSD_INDEX].charAt(0))
            {
            case 'N':
                split = splitN(line);
                break;
            case 'M':
                split = splitM(line);
                break;
            case 'A':
                split = splitA(line);
                break;
            }

            if (split != null)
            {
                for (int i = 0; i < split.length; ++i)
                {
                    split[i] = postProcess(split[i]);
                }
            }

            return split;
        }

        return null;
    }

    /**
     *
     * @param document
     * @param tagName
     * @param type
     * @return
     */
    private static List<Node> getNodes(Document document, String tagName, String type)
    {
        NodeList nodeList = document.getElementsByTagName(tagName);

        List<Node> nodes = new LinkedList<Node>();

        for (int i = 0; i < nodeList.getLength(); ++i)
        {
            Node node = nodeList.item(i);
            if (node.getAttributes().getNamedItem("type").getTextContent().equals(type))
            {
                nodes.add(node);
            }
        }

        return nodes;
    }

    /**
     *
     * @param node
     * @param tagNames
     * @return
     */
    private static List<Node> getNodes(Node node, String... tagNames)
    {
        NodeList childNodes = ((Element) node).getChildNodes();

        List<Node> nodes = new LinkedList<Node>();

        for (int i = 0; i < childNodes.getLength(); ++i)
        {
            Node tempNode = childNodes.item(i);
            String tempNodeName = tempNode.getNodeName();

            for (String tagName : tagNames)
            {
                if (tempNodeName.equals(tagName))
                {
                    nodes.add((Element) tempNode);
                    break;
                }
            }
        }

        return nodes;
    }

    /**
     *
     * @param node
     * @return
     */
    private static String getSpelling(Node node)
    {
        return node.getChildNodes().item(0).getTextContent().trim();
    }

    /**
     *
     * @param node
     * @return
     */
    private static String getLemma(Node node)
    {
        return getNodes(getNodes(node, "msd").get(0), "lemma").get(0).getTextContent();
    }

    /**
     *
     * @param node
     * @return
     */
    private static String getMsd(Node node)
    {
        String msd = getNodes(getNodes(node, "msd").get(0), "mscat").get(0).getTextContent();

        return msd.substring(1, msd.length() - 1);
    }

    /**
     *
     * @param node
     * @throws IOException
     */
    private static void printW(Node node)
        throws IOException
    {
        String spelling = node.getChildNodes().item(0).getTextContent().trim();

        writer.write(spelling);

        NodeList nodes = ((Element) node).getElementsByTagName("ana");
        for (int i = 0; i < nodes.getLength(); ++i)
        {
            writer.write('\t');
            writer.write(getLemma(nodes.item(i)).replace("+", ""));
            writer.write('\t');
            writer.write(getMsd(nodes.item(i)));
        }

        nodes = ((Element) node).getElementsByTagName("anav");

        for (int i = 0; i < nodes.getLength(); ++i)
        {
            writer.write('\t');
            writer.write(getLemma(nodes.item(i)));
            writer.write('\t');
            writer.write(getMsd(nodes.item(i)));
        }
    }

    /**
     *
     * @param node
     * @throws DOMException
     * @throws IOException
     */
    private static void printC(Node node)
        throws DOMException, IOException
    {
        String c = node.getTextContent();
        writer.write(c);

        if (!ResourceHolder.getPunctations().contains(c))
        {
            writer.write('\t');
            writer.write(node.getTextContent());
            writer.write('\t');
            writer.write("K");
            writer.write('\t');
            writer.write(node.getTextContent());
            writer.write('\t');
            writer.write("K");
        }
        else
        {
            writer.write('\t');
            writer.write(node.getTextContent());
            writer.write('\t');
            writer.write(node.getTextContent());
            writer.write('\t');
            writer.write(node.getTextContent());
            writer.write('\t');
            writer.write(node.getTextContent());
        }
    }

    /**
     *
     * @param node
     * @throws IOException
     */
    private static void printChoice(Node node)
        throws IOException
    {
        try
        {
            for (Node correctedNode : getNodes(getNodes(node, new String[] { "corr", "reg" }).get(0), new String[] { "w", "c" }))
            {
                printNode(correctedNode);
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            System.err.println(node.getTextContent());
        }
    }

    /**
     *
     * @param node
     * @throws IOException
     */
    private static void printNode(Node node)
        throws IOException
    {
        String nodeName = node.getNodeName();
        if (nodeName.equals("w"))
        {
            printW(node);
        }
        else if (nodeName.equals("c"))
        {
            printC(node);
        }
        else if (nodeName.equals("choice"))
        {
            printChoice(node);
        }
    }

    /**
     *
     * @param nodes
     * @throws DOMException
     * @throws IOException
     */
    private static void printPrefix(Node... nodes)
        throws DOMException, IOException
    {
        for (Node node : nodes)
        {
            writer.write(node.getAttributes().getNamedItem("id").getTextContent());
            writer.write('\t');
        }
    }

    private static void convertXMLtoTXT(String XML, String txt)
    {
        if (writer == null)
            try
            {
                writer = new BufferedWriter(new FileWriter(txt));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        try
        {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(XML));

            // "1984"
            // for (Node partDivNode : getNodes(document, "div", "part")) {
            // for (Node divNode : getNodes(partDivNode, "div", "chapter")) {
            // "10elb", "10erv", "8oelb"
            // for (Node divNode : getNodes(document, "div", "composition")) {
            // "nv", "mh", "hvg", "np", "cwszt", "win2000", "utas", "pfred"
            // for (Node divNode : getNodes(document, "div", "article")) {
            // "gazdtar", "szerzj"
            for (Node divNode : getNodes(document, "div", "section"))
            {
                for (Node pNode : getNodes(divNode, "p"))
                {
                    for (Node sNode : getNodes(pNode, "s"))
                    {
                        for (Node node : getNodes(sNode, new String[] { "w", "c", "choice" }))
                        {
                            try
                            {
                                printPrefix(divNode, pNode, sNode);
                                printNode(node);
                                writer.write('\n');
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        writer.write('\n');
                    }
                }
            }

            writer.flush();
            writer.close();
            writer = null;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    private static String[][] read(String file)
    {
        List<String[]> sentences = new ArrayList<String[]>();

        List<String> sentence = new ArrayList<String>();

        for (String line : Util.readFileToString(file).split("\n"))
        {
            if (line.trim().equals(""))
            {
                sentences.add(sentence.toArray(new String[sentence.size()]));
                sentence = new ArrayList<String>();
            }
            else
            {
                sentence.add(line);
            }
        }

        return sentences.toArray(new String[sentences.size()][]);
    }

    private static void splitMWs(String file, String out)
    {
        String corpus = Util.readFileToString(file);

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (String line : corpus.split("\n"))
            {
                if (line.length() > 0)
                {
                    // split MW
                    String[] split = splitMW(line);
                    if (split != null)
                    {
                        // split lines
                        for (String s : split)
                        {
                            writer.write(s);
                            writer.write('\n');
                        }
                    }
                    else
                    {
                        writer.write(line);
                        writer.write('\n');
                    }
                }
                else
                {
                    writer.write('\n');
                }
            }

            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private static void writeTrain(String[][] sentences, String out)
    {
        System.err.println(out);

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (String[] sentence : sentences)
            {
                // train
                for (String token : sentence)
                {
                    writer.write(token.split("\t")[WORD_FORM_INDEX].replace(" ", "_"));
                    writer.write("@");
                    writer.write(ResourceHolder.getMSDReducer().reduce(token.split("\t")[MSD_INDEX]));
                    writer.write(" ");
                }
                writer.write('\n');
            }

            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private static void writeTest(String[][] sentences, String out)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (String[] sentence : sentences)
            {
                for (String token : sentence)
                {
                    String msd = token.split("\t")[MSD_INDEX].replace("Np", "Nn").replace("Nc", "Nn");

                    writer.write(token.split("\t")[WORD_FORM_INDEX].replace(" ", "_"));
                    writer.write('\t');
                    writer.write(token.split("\t")[LEMMA_INDEX].replace(" ", "_"));
                    writer.write('\t');
                    writer.write(msd);
                    writer.write('\n');
                }
                writer.write('\n');
            }

            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private static void writeLexicon(String[][] sentences, String out)
    {
        Map<String, Set<MorAna>> lexicon = new TreeMap<String, Set<MorAna>>();

        for (String[] sentence : sentences)
        {
            for (String token : sentence)
            {
                String[] split = token.split("\t");

                if (!lexicon.containsKey(split[WORD_FORM_INDEX]))
                {
                    lexicon.put(split[WORD_FORM_INDEX], new TreeSet<MorAna>());
                }

                String msd = split[MSD_INDEX].replace("Np", "Nn").replace("Nc", "Nn");

                lexicon.get(split[WORD_FORM_INDEX]).add(new MorAna(split[LEMMA_INDEX], msd));
            }
        }

        writeLexiconToFile(lexicon, out);
    }

    public static void writeLexiconToFile(Map<String, Set<MorAna>> lexicon, String out)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Map.Entry<String, Set<MorAna>> entry : lexicon.entrySet())
            {
                writer.write(entry.getKey());

                for (MorAna morAna : entry.getValue())
                {
                    writer.write('\t');
                    writer.write(morAna.getLemma());
                    writer.write('\t');
                    writer.write(morAna.getMsd());
                }
                writer.write('\n');
            }

            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private static void writeFreqs(String[][] sentences, String out)
    {
        Map<String, Integer> freqs = new TreeMap<String, Integer>();

        for (String[] sentence : sentences)
        {
            for (String token : sentence)
            {
                String[] split = token.split("\t");

                String msd = split[MSD_INDEX].replace("Np", "Nn").replace("Nc", "Nn");

                if (!freqs.containsKey(msd))
                {
                    freqs.put(msd, 0);
                }

                freqs.put(msd, freqs.get(msd) + 1);
            }
        }

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Map.Entry<String, Integer> entry : freqs.entrySet())
            {
                writer.write(entry.getKey());
                writer.write('\t');
                writer.write(entry.getValue());
                writer.write('\n');
            }

            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private static void writeResources(String file)
    {
        String[][] sentences = read(file);

        int treshold = (int) (sentences.length * DEFAULR_TRAIN_TRESHOLD);

        writeTrain(Arrays.copyOfRange(sentences, 0, treshold), file + ".train");
        writeLexicon(Arrays.copyOfRange(sentences, 0, treshold), file + ".lexicon");
        writeFreqs(Arrays.copyOfRange(sentences, 0, treshold), file + ".freqs");

        writeTest(Arrays.copyOfRange(sentences, treshold, sentences.length), file + ".test");
    }

    public static String[] getColumn(String[] sentence, int index)
    {
        String[] column = new String[sentence.length];

        for (int i = 0; i < column.length; ++i)
        {
            column[i] = sentence[i].split("\t")[index];
        }

        return column;
    }

    public static void predicate(String file, String out)
    {
        String[][] sentences = read(file);

        int correct = 0;
        int tokenCounter = 0;

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (String[] sentence : sentences)
            {
                String[] wordform = getColumn(sentence, 0);
                String[] lemma = getColumn(sentence, 1);
                String[] msd = getColumn(sentence, 2);

                String[][] pred = Magyarlanc.morphParseSentence(wordform);

                for (int i = 0; i < pred.length; ++i)
                {
                    writer.write(wordform[i]);
                    writer.write('\t');
                    writer.write(lemma[i]);
                    writer.write('\t');
                    writer.write(msd[i]);
                    writer.write('\t');
                    writer.write(pred[i][1]);
                    writer.write('\t');
                    writer.write(pred[i][2]);
                    writer.write('\n');

                    // if (!lemma[i].equals(pred[i][1]) || !msd[i].equals(pred[i][2])) {
                    // System.err.println(lemma[i] + "\t" + pred[i][1] + "\t" + msd[i] + "\t" + (pred[i][2]));
                    // }

                    if (lemma[i].equalsIgnoreCase(pred[i][1]) && msd[i].equals(pred[i][2]))
                    {
                        ++correct;
                    }
                    else
                    {
                        // System.err.println(wordform[i] + "\t" + lemma[i] + "\t" + msd[i] + "\t" + pred[i][1] + "\t" + pred[i][2]);
                    }
                    ++tokenCounter;
                }
                writer.write('\n');
            }

            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        System.err.println(tokenCounter);

        System.err.println((float) correct / tokenCounter);
    }

    public static void buildResources(String xml)
    {
        // convert XML to TXT
        convertXMLtoTXT(xml, xml + ".txt");

        // split MWs
        splitMWs(xml + ".txt", xml + ".txt.split");

        // write train
        writeResources(xml + ".txt.split");
    }

    public static void merge(String path, String[] files, String extension, String out)
    {
        StringBuilder sb = new StringBuilder();

        for (String file : files)
        {
            sb.append(readFileToString(path + file + extension));
        }

        writeStringToFile(sb.toString(), out);
    }

    public static void mergeFrequencies(String path, String[] files, String extension, String out)
    {
        Map<String, Integer> map = new TreeMap<String, Integer>();

        for (String file : files)
        {
            for (Map.Entry<String, Integer> entry : readIntMap(path + file + extension, "\t", true).entrySet())
            {
                if (!map.containsKey(entry.getKey()))
                {
                    map.put(entry.getKey(), 0);
                }
                map.put(entry.getKey(), map.get(entry.getKey()) + entry.getValue());
            }
        }

        writeMapToFile(map, out, "\t");
    }

    public static void writeMapToFile(Map<String, Integer> map, String file, String separator)
    {
        writeMapToFile(map, new File(file), separator);
    }

    public static void writeMapToFile(Map<String, Integer> map, File file, String separator)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (Entry<String, Integer> entry : map.entrySet())
            {
                writer.write(entry.getKey());
                writer.write(separator);
                writer.write(entry.getValue());
                writer.write('\n');
            }

            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void writeStringToFile(String s, File file)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
            writer.write(s);
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void writeStringToFile(String s, String file)
    {
        writeStringToFile(s, new File(file));
    }

    public static String readFileToString(String file)
    {
        return readFileToString(new File(file));
    }

    public static String readFileToString(File file)
    {
        StringBuilder sb = new StringBuilder();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                sb.append(line).append('\n');
            }

            reader.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return sb.toString();
    }

    public static void mergeLexicons(String path, String[] files, String extension, String out)
    {
        Map<String, Set<MorAna>> lexicon = new TreeMap<String, Set<MorAna>>();

        for (String file : files)
        {
            for (Map.Entry<String, Set<MorAna>> entry : readLexicon(path + file + extension).entrySet())
            {
                if (!lexicon.containsKey(entry.getKey()))
                {
                    lexicon.put(entry.getKey(), new TreeSet<MorAna>());
                }
                lexicon.get(entry.getKey()).addAll(entry.getValue());
            }
        }

        writeLexiconToFile(lexicon, out);
    }

    public static void mergeResources(String path, String[] files)
    {
        // merge train files
        merge(path, files, ".xml.txt.split.train", path + "szeged.xml.txt.split.train");

        // merge test files
        merge(path, files, ".xml.txt.split.test", path + "szeged.xml.txt.split.test");

        // merge frequencies
        mergeFrequencies(path, files, ".xml.txt.split.freqs", path + "szeged.xml.txt.split.freqs");

        // merge corpuses
        mergeLexicons(path, files, ".xml.txt.split.lexicon", path + "szeged.xml.txt.split.lexicon");
    }

    public static void main(String args[])
    {
        // String[] courpus = new String[] { "nv", "mh", "hvg", "np", "cwszt", "win2000", "utas", "pfred", "newsml" };
        // String[] courpus = new String[] { "1984" };
        // String[] courpus = new String[] { "10elb", "10erv", "8oelb" };
        // String[] courpus = new String[] { "gazdtar", "szerzj" };

        // for (String c : courpus) {
        // buildResources("./data/szk2.5/xml/" + c + ".xml");
        // }

        // String[] corpus = new String[] { "gazdtar", "szerzj", "10elb", "10erv",
        // "8oelb", "1984", "nv", "mh", "hvg", "np", "cwszt", "win2000", "utas",
        // "pfred", "newsml" };

        // mergeResources("./data/szk2.5/xml/", corpus);

        long start = System.currentTimeMillis();
        predicate("./data/szk2.5/xml/szeged.xml.txt.split.test", "./data/szk2.5/xml/szeged.xml.txt.split.test.pred.full");
        System.err.println(System.currentTimeMillis() - start);

        // predicate("./25.test", "./data/szk2.5/xml/newsml_1.xml.txt.split.test.pred");
    }
}
EOF
mkdir -p szte/magyarlanc/util && cat > szte/magyarlanc/util/Tools.java <<'EOF'
package szte.magyarlanc.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import edu.northwestern.at.utils.MapUtils;

import szte.magyarlanc.HunLemMor;
import szte.magyarlanc.MorAna;
import szte.pos.converter.CoNLLFeaturesToMSD;
import szte.pos.converter.MSDReducer;
import szte.pos.converter.MSDToCoNLLFeatures;

public class Tools
{
    static int tokens = 0;
    static int sentences = 0;

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
            e.printStackTrace();
        }

        return sb.toString();
    }

    public static List<List<String>> readFile(String file)
    {
        List<List<String>> document = new LinkedList<List<String>>();
        List<String> sentence = new LinkedList<String>();

        int tokenCounter = 0;
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.equals(""))
                {
                    document.add(sentence);
                    tokenCounter += sentence.size();
                    sentence = new LinkedList<String>();
                }
                else
                {
                    sentence.add(line);
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        System.err.println(file + "\t" + tokenCounter + " tokens\t" + document.size() + " sentences");

        tokens += tokenCounter;
        sentences += document.size();

        return document;
    }

    public static List<List<List<String>>> readFiles(String[] files)
    {
        List<List<List<String>>> documents = new LinkedList<List<List<String>>>();

        for (String file : files)
        {
            documents.add(readFile(file));
        }

        System.err.println(documents.size() + " documents\t" + tokens + " tokens\t" + sentences + " sentences");

        return documents;
    }

    public static String[] fileList(String path, String[] files, String extension)
    {
        String[] fileList = new String[files.length];

        for (int i = 0; i < files.length; ++i)
        {
            fileList[i] = path + files[i] + extension;
        }

        return fileList;
    }

    public static void write(String out, List<List<List<String>>> documents)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (List<List<String>> document : documents)
            {
                for (List<String> sentence : document)
                {
                    for (String line : sentence)
                    {
                        String[] splitted = line.split("\t");

                        writer.write(splitted[0]);
                        for (int i = 1; i < 12; ++i)
                        {
                            writer.write('\t');
                            writer.write(splitted[i]);
                        }
                        writer.write('\n');
                    }
                    writer.write('\n');
                }
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static Map<String, Integer> verbStat(List<List<List<String>>> documents)
    {
        Map<String, Integer> verbStat = new TreeMap<String, Integer>();

        for (List<List<String>> document : documents)
        {
            for (List<String> sentence : document)
            {
                for (String token : sentence)
                {
                    String[] splitted = token.split("\t");
                    if (splitted[4].equals("V") && splitted[6].startsWith("SubPOS=m"))
                    {
                        if (!verbStat.containsKey(splitted[2]))
                        {
                            verbStat.put(splitted[2], 0);
                        }
                        verbStat.put(splitted[2], verbStat.get(splitted[2]) + 1);
                    }
                }
            }
        }

        for (Map.Entry<String, Integer> entry : verbStat.entrySet())
        {
            System.out.println(entry.getKey() + "\t" + entry.getValue());
        }

        return verbStat;
    }

    // public static void writePosTrain(List<List<List<String>>> documents,
    // String file) {
    // String[] splitted = null;
    // Writer writer = null;
    //
    // Set<String> reduced = null;
    // reduced = new TreeSet<String>();
    //
    // try {
    // writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    // for (List<List<String>> document : documents) {
    // for (List<String> sentence : document) {
    // for (String line : sentence) {
    // splitted = line.split("\t");
    // if (!splitted[4].equals("ELL") && !splitted[4].equals("VAN")) {
    // writer.write(splitted[1] + "@" + splitted[13] + " ");
    // reduced.add(splitted[13]);
    // }
    // }
    // writer.write('\n');
    // }
    // }
    // writer.flush();
    // writer.close();
    // } catch (IOException e) {
    // e.printStackTrace();
    // }
    //
    // for (String r : reduced) {
    // System.out.print(r + " ");
    // }
    // }

    public static void writePosTrain(List<List<String>> sentences, String file)
    {
        Set<String> reduced = new TreeSet<String>();

        CoNLLFeaturesToMSD coNLLFeaturesToMSD = new CoNLLFeaturesToMSD();
        MSDReducer msdReducer = new MSDReducer();

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                for (String line : sentence)
                {
                    String[] splitted = line.split("\t");
                    if (!splitted[4].equals("ELL") && !splitted[4].equals("VAN"))
                    {
                        String msd = coNLLFeaturesToMSD.convert(splitted[4], splitted[6]);

                        writer.write(splitted[1]);
                        writer.write("@");
                        writer.write(msdReducer.reduce(msd));
                        writer.write(" ");

                        reduced.add(splitted[13]);
                    }
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

        for (String r : reduced)
        {
            System.out.print(r + " ");
        }
    }

    public static void validate()
    {
        MSDToCoNLLFeatures msdToCoNLLFeatures = new MSDToCoNLLFeatures();
        CoNLLFeaturesToMSD coNLLFeaturesToMSD = new CoNLLFeaturesToMSD();

        MSDReducer msdReducer = new MSDReducer();

        Writer writer = null;
        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("./new_features.txt"), "UTF-8"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        String path = "./data/conll/msd/";

        String[] files = new String[] { "8oelb", /*
                                      * "10elb", "10erv", "1984", "cwszt",
                                      * "gazdtar", "hvg", "mh", "newsml", "np",
                                      * "nv", "pfred", "szerzj", "utas",
                                      * "win2000"
                                      */};

        String extension = ".conll-2009-msd";

        List<List<List<String>>> documents = readFiles(fileList(path, files, extension));

        Set<String> ftrs = new TreeSet<String>();

        Map<String, Map<String, Integer>> rdcds = new TreeMap<String, Map<String, Integer>>();

        Map<String, Integer> msds = new TreeMap<String, Integer>();

        for (List<List<String>> document : documents)
        {
            for (List<String> sentence : document)
            {
                for (String token : sentence)
                {
                    String[] splitted = token.split("\t");

                    String oldFetures = splitted[6];
                    String newFeatures = msdToCoNLLFeatures.convert(splitted[2], splitted[12]);

                    // new features
                    if (!oldFetures.equals(newFeatures))
                    {
                        // try {
                        // ftrs.add(splitted[12] + "\t" + oldFetures + "\t" + newFeatures);
                        // writer.write(splitted[2] + "\t" + splitted[12] + "\t"
                        // + oldFetures + "\t" + newFeatures + "\n");
                        // } catch (IOException e) {
                        // e.printStackTrace();
                        // }
                    }

                    try
                    {
                        // if (!coNLLFeaturesToMSD.getMsd(splitted[4], oldFetures).equals(
                        // splitted[12])) {
                        // System.out.println("SDM : " + splitted[12]);
                        // }
                    }
                    catch (Exception e)
                    {
                        // TODO: handle exception
                    }

                    try
                    {
                        if (!msdReducer.reduce(splitted[12]).equals(splitted[13]))
                        {
                            if (!Character.isLetterOrDigit(splitted[12].charAt(0)))
                            {
                                // System.out.println(token);
                            }
                            // rdcds.add(splitted[12] + "\t" + splitted[13] + "\t" + msdReducer.reduce(splitted[12]));
                        }
                    }
                    catch (Exception e)
                    {
                        // TODO: handle exception
                    }

                    if (!msds.containsKey(splitted[12] + "\t" + msdReducer.reduce(splitted[12])))
                    {
                        if (!msds.containsKey(splitted[12] + "\t" + msdReducer.reduce(splitted[12])))
                        {
                            msds.put(splitted[12] + "\t" + msdReducer.reduce(splitted[12]), 0);
                        }
                        msds.put(splitted[12] + "\t" + msdReducer.reduce(splitted[12]),
                                    msds.get(splitted[12] + "\t" + msdReducer.reduce(splitted[12])) + 1);
                    }
                }
            }

            try
            {
                writer.flush();
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            for (String ftr : ftrs)
            {
                // System.out.println(ftr);
            }

            // for (String rdcd : rdcds) {
            // // System.out.println(rdcd);
            // }

            for (Map.Entry entry : msds.entrySet())
            {
                System.out.println(entry.getKey() + " \t" + entry.getValue());
            }
        }
    }

    public static void correct(String file, String out)
    {
        List<List<String>> document = readFile(file);

        Writer writer = null;
        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        MSDToCoNLLFeatures msdToCoNLLFeatures = new MSDToCoNLLFeatures();

        MSDReducer msdReducer = new MSDReducer();

        for (List<String> sentence : document)
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");

                String oldFetures = splitted[6];
                String oldPFetures = splitted[7];

                String newFeatures = msdToCoNLLFeatures.convert(splitted[2], splitted[12]);
                String newPFeatures = msdToCoNLLFeatures.convert(splitted[3], splitted[14]);

                String oldReduced = splitted[13];
                String oldPReduced = splitted[15];

                String newReduced = msdReducer.reduce(splitted[12]);
                String newPReduced = msdReducer.reduce(splitted[14]);

                if (!oldFetures.equals(newFeatures))
                {
                    System.out.println(oldFetures + "\t" + newFeatures);
                    splitted[6] = newFeatures;
                }

                if (!oldPFetures.equals(newPFeatures))
                {
                    System.out.println(oldPFetures + "\t" + newPFeatures);
                    splitted[7] = newPFeatures;
                }

                if (!oldReduced.equals(newReduced))
                {
                    System.out.println(oldReduced + "\t" + newReduced);
                    splitted[13] = newReduced;
                }

                if (!oldPReduced.equals(newPReduced))
                {
                    System.out.println(oldPReduced + "\t" + newPReduced);
                    splitted[15] = newPReduced;
                }

                try
                {
                    writer.write(splitted[0]);
                    for (int i = 1; i < splitted.length; ++i)
                    {
                        writer.write('\t');
                        writer.write(splitted[i]);
                    }
                    writer.write('\n');
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }
            }
            try
            {
                writer.write('\n');
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        try
        {
            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static Map<String, Integer> edgeStat(List<List<String>> document)
    {
        Map<String, Integer> edgeStat = new TreeMap<String, Integer>();

        for (List<String> sentence : document)
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");

                if (!edgeStat.containsKey(splitted[10]))
                    edgeStat.put(splitted[10], 0);

                edgeStat.put(splitted[10], edgeStat.get(splitted[10]) + 1);
            }
        }

        return edgeStat;
    }

    static String getCas(String features)
    {
        String[] splittedFeatures = features.split("\\|");

        for (String s : splittedFeatures)
        {
            if (s.startsWith("Cas"))
            {
                return s.split("=")[1];
            }
        }

        return null;
    }

    public static void buildFreqs(List<List<List<String>>> documents, String file)
    {
        Map<String, Integer> freqs = new TreeMap<String, Integer>();

        for (List<List<String>> document : documents)
            for (List<String> sentence : document)
                for (String token : sentence)
                {
                    String[] splitted = token.split("\t");
                    if (!splitted[4].equals("ELL") && !splitted[4].equals("VAN"))
                    {
                        if (!freqs.containsKey(splitted[12]))
                            freqs.put(splitted[12], 0);

                        freqs.put(splitted[12], freqs.get(splitted[12]) + 1);
                    }
                }

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (Map.Entry<String, Integer> entry : freqs.entrySet())
            {
                writer.write(entry.getKey());
                writer.write('\t');
                writer.write(entry.getValue());
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

    public static void buildLexicon(List<List<List<String>>> documents, String file)
    {
        Map<String, Set<MorAna>> lexicon = new TreeMap<String, Set<MorAna>>();

        for (List<List<String>> document : documents)
            for (List<String> sentence : document)
                for (String token : sentence)
                {
                    String[] splitted = token.split("\t");
                    if (!splitted[4].equals("ELL") && !splitted[4].equals("VAN"))
                    {
                        if (!lexicon.containsKey(splitted[1]))
                            lexicon.put(splitted[1], new TreeSet<MorAna>());

                        MorAna morAna = new MorAna(splitted[2], splitted[12]);
                        lexicon.get(splitted[1]).add(morAna);
                    }
                }

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (Map.Entry<String, Set<MorAna>> entry : lexicon.entrySet())
            {
                writer.write(entry.getKey());
                for (MorAna m : entry.getValue())
                {
                    writer.write('\t');
                    writer.write(m.getLemma());
                    writer.write('\t');
                    writer.write(m.getMsd());
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

    public static Map<String, Integer> casStat(List<List<String>> document)
    {
        Map<String, Integer> casStat = new TreeMap<String, Integer>();

        for (List<String> sentence : document)
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");
                if (splitted[4].equals("N"))
                {
                    String cas = getCas(splitted[6]);
                    if (!casStat.containsKey(cas))
                    {
                        casStat.put(cas, 0);
                    }
                    casStat.put(cas, casStat.get(cas) + 1);
                }
            }
        }

        return casStat;
    }

    public static void possibles(String file)
    {
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                System.err.println(line + "\t" + HunLemMor.getMorphologicalAnalyses(line));
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String args[])
    {
        // possibles("c:/jav2_elemezni.txt");
        String path = "./data/conll/corrected_features/";
        String[] files = new String[] { /*
                                         * "8oelb", "10elb", "10erv", "1984", "cwszt",
                                         * "gazdtar", "hvg", "mh",
                                         */"newsml"/*
                                         * , "np", "nv", "pfred", "szerzj",
                                         * "utas", "win2000"
                                         */};
        String extension = ".conll-2009-msd";

        // for (String file : files) {
        // for (Map.Entry<String, Integer> entry : edgeStat(
        // readFile(path + file + extension)).entrySet())
        // System.out.println(entry.getKey() + "\t" + entry.getValue());
        // }

        // for (String file : files) {
        // for (Map.Entry<String, Integer> entry : casStat(
        // readFile(path + file + extension)).entrySet())
        // System.out.println(entry.getKey() + "\t" + entry.getValue());
        // }

        // correct("./data/conll/msd/" + file + extension,
        // "./data/conll/corrected_features/" + file + extension);

        // verbStat(documents);

        // List<List<List<String>>> documents = readFiles(fileList(path, files,
        // extension));

        // buildLexicon(documents, "./corrected.lex");
        // buildFreqs(documents, "./corrected.freqs");

        // write("./corrected_features_temp.dep", documents);
        // documents = null;

        // try {
        // RemoveEmptyNodes.processFile("./corrected_features_temp.dep",
        // "./corrected_features_temp.dep.train");
        // } catch (IOException e) {
        // e.printStackTrace();
        // }

        // List<List<String>> sentences = readFile("./corrected_features_temp.dep");

        for (int i = 0; i < 10; ++i)
        {
            List<List<String>> sentences = readFile("./data/newspaper/newspaper.conll2009_train" + i);

            writePosTrain(sentences, "./data/newspaper/newspaper.conll2009_train" + i + ".stanford");
        }
    }
}
EOF
mkdir -p szte/magyarlanc/util && cat > szte/magyarlanc/util/TrainTest.java <<'EOF'
package szte.magyarlanc.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import is2.parser.Options;
import is2.parser.Parser;

import szte.dep.parser.MateParserWrapper;

public class TrainTest
{
    private static final String WORK_DIR = "./data/fx-dep/";
    private static final String TRAIN_DIR = "train/";
    private static final String TEST_DIR = "test/";

    private static final String CORPUS2_DIR = "corpus2/";
    private static final String TEST_2_DIR = "test2/";
    private static final String TRAIN_2_DIR = "train2/";

    private static final String MODEL_DIR = "model/";
    private static final String PRED_DIR = "predicated/";

    private static final String CORPUS_DIR = "corpus/";

    static final String NO_DOCSTART = "no_docstart.";
    static final String REMOVED_EMPTY_NODES = "removed_empty_nodes.";
    static final String HAS_ROOT = "has_root.";

    public static int[] getOffsets(Integer[] ids, int n)
    {
        if (n < 1)
        {
            System.err.println("N must be grater than 0.");
            System.exit(1);
        }

        if (n > ids.length)
        {
            System.err.println("N must be less than the length of the array.");
            System.exit(1);
        }

        int[] offsets = new int[n + 1];

        double length = 0;
        length = (double) ids.length / n;

        for (int i = 0; i < n; ++i)
        {
            offsets[i] = (int) Math.round(i * length);
        }
        offsets[n] = ids.length;

        return offsets;
    }

    public static Integer[][] apart(Integer[] ids, int n)
    {
        Integer[][] aparted = new Integer[n][];

        int[] offsets = getOffsets(ids, n);

        for (int i = 0; i < offsets.length - 1; ++i)
        {
            aparted[i] = new Integer[offsets[i + 1] - offsets[i]];
            int index = 0;
            for (int j = offsets[i]; j < offsets[i + 1]; ++j)
            {
                aparted[i][index] = ids[j];
                ++index;
            }
            Arrays.sort(aparted[i]);
        }

        return aparted;
    }

    public static Integer[] randomArray(int n)
    {
        Integer[] random = new Integer[n];

        for (int i = 0; i < n; ++i)
        {
            random[i] = i;
        }

        Collections.shuffle(Arrays.asList(random));

        return random;
    }

    public static List<List<String>> read(String file)
    {
        List<List<String>> sentences = new LinkedList<List<String>>();

        List<List<List<String>>> documents = new LinkedList<List<List<String>>>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            List<String> sentence = new LinkedList<String>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.equals("--DOCSTART--"))
                {
                    // documents.add(sentences);
                    // sentences = new LinkedList<List<String>>();
                    reader.readLine();
                }

                else if (!line.equals(""))
                {
                    sentence.add(line);
                }
                else if (line.equals(""))
                {
                    sentences.add(sentence);
                    sentence = new LinkedList<String>();
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        // System.out.println(sentences.size());
        return sentences;
    }

    public static String[] getColumn(List<String> sentence, int index)
    {
        String[] column = new String[sentence.size()];

        for (int i = 0; i < column.length; ++i)
        {
            column[i] = sentence.get(i).split("\t")[index];
        }

        return column;
    }

    public static void write(List<List<String>> sentences, Integer[] ids, String out, boolean removeColumn)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (int id : ids)
            {
                for (String token : sentences.get(id))
                {
                    if (removeColumn)
                    {
                        String[] splitted = token.split("\t");

                        writer.write(splitted[0]);
                        for (int i = 1; i < 12; ++i)
                        {
                            writer.write('\t');
                            writer.write(splitted[i]);
                        }

                        for (int i = 13; i < splitted.length; ++i)
                        {
                            writer.write('\t');
                            writer.write(splitted[i]);
                        }
                    }
                    else
                    {
                        writer.write(token);
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

    // public static void removeDocstart(String file) {
    // write(read(WORK_DIR + file), WORK_DIR + NO_DOCSTART + file);
    // }

    public static void removeLongSentences(String file, int length)
    {
        List<List<String>> sentences = read(WORK_DIR + CORPUS_DIR + file);

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(WORK_DIR + CORPUS_DIR + length + "_" + file), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                if (sentence.size() < length)
                {
                    for (String token : sentence)
                    {
                        writer.write(token);
                        writer.write('\n');
                    }
                    writer.write('\n');
                }
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void write10Fold(String file)
    {
        System.err.println(file);

        // removeEmptyNodes(file, false);
        // removeLongSentences(HAS_ROOT + REMOVED_EMPTY_NODES + file, 100);

        // List<List<String>> sentences = read(WORK_DIR + CORPUS_DIR + 100 + "_" + HAS_ROOT + REMOVED_EMPTY_NODES + file);

        // System.err.println("100<\t" + sentences.size());

        List<List<String>> sentences = read(WORK_DIR + CORPUS2_DIR + REMOVED_EMPTY_NODES + file);
        int numberOfSentences = sentences.size();

        Integer[] ids = randomArray(numberOfSentences);

        int n = 0;
        Integer[][] aparts = apart(ids, 10);

        // test
        for (int i = 0; i < aparts.length; ++i)
        {
            Integer[] group = aparts[i];
            n += group.length;
            System.err.println("|" + group.length + "|" + "\t" + Arrays.toString(group));

            write(sentences, group, (WORK_DIR + TEST_2_DIR + file + ".test." + i), false);
        }

        // train
        for (int i = 0; i < aparts.length; ++i)
        {
            ArrayList<Integer> train = new ArrayList<Integer>();
            for (int j = 0; j < aparts.length; ++j)
            {
                if (i != j)
                {
                    train.addAll(Arrays.asList(aparts[j]));
                }
            }

            Integer[] trainArray = train.toArray(new Integer[train.size()]);
            Arrays.sort(trainArray);
            System.err.println("|" + trainArray.length + "|" + "\t" + Arrays.toString(trainArray));

            write(sentences, trainArray, (WORK_DIR + TRAIN_2_DIR + file + ".train." + i), true);
        }
        System.err.println("||" + n + "||");
    }

    public static boolean containsRoot(List<String> sentence, int index)
    {
        for (String line : sentence)
        {
            if (line.split("\t")[index].equals("ROOT"))
                return true;
        }

        return false;
    }

    public static void write(List<List<String>> sentences, String file)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (List<String> sentence : sentences)
            {
                for (String token : sentence)
                {
                    writer.write(token);
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

    public static void removeEmptyNodes(String file, boolean removeNonRoot)
    {
        // System.err.println(file);
        System.err.println("\t" + read(WORK_DIR + CORPUS2_DIR + file).size());

        // virtualis node-ok eltavolitasa
        try
        {
            szte.dep.util.RemoveEmptyNodes.processFile(WORK_DIR + CORPUS2_DIR + file, WORK_DIR + CORPUS2_DIR + REMOVED_EMPTY_NODES + file);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        List<List<String>> sentences = read(WORK_DIR + CORPUS2_DIR + REMOVED_EMPTY_NODES + file);
        System.err.println("VAN/ELL\t" + sentences.size());

        // root nelkuli mondatok

        if (removeNonRoot)
        {
            List<List<String>> hasRoot = new LinkedList<List<String>>();

            for (List<String> sentence : sentences)
            {
                if (containsRoot(sentence, 10))
                {
                    hasRoot.add(sentence);
                }
            }

            System.err.println("ROOT\t" + hasRoot.size());
            write(hasRoot, WORK_DIR + CORPUS_DIR + HAS_ROOT + REMOVED_EMPTY_NODES + file);
        }
    }

    public static void preidcate(String testFile, String modelFile, Writer writer)
        throws IOException
    {
        Parser parser = new Parser(new Options(new String[] { "-model", modelFile, "-cores", "1" }));

        List<List<String>> sentences = read(testFile);

        for (int i = 0; i < sentences.size(); ++i)
        {
            if ((i % 200) == 0)
                System.out.print(sentences.size() + "/" + i + " ");

            List<String> sentence = sentences.get(i);

            String[] id = getColumn(sentence, 0),
                   form = getColumn(sentence, 1),
                  lemma = getColumn(sentence, 2),
                 plemma = getColumn(sentence, 3),
                    pos = getColumn(sentence, 4),
                   ppos = getColumn(sentence, 5),
                   feat = getColumn(sentence, 6),
                  pfeat = getColumn(sentence, 7),
                   head = getColumn(sentence, 8),
                                           // 9
                    rel = getColumn(sentence, 10),
                                           // 11
                     fx = getColumn(sentence, 12);

            String[][] prediction = null/*!*/;
            // String[][] prediction = DParser.parseSentence(getColumn(sentence, 1), getColumn(sentence, 3), getColumn(sentence, 5), getColumn(sentence, 7), parser);

            for (int j = 0; j < id.length; ++j)
            {
                writer.write(id[j]);
                writer.write('\t');
                writer.write(form[j]);
                writer.write('\t');
                writer.write(lemma[j]);
                writer.write('\t');
                writer.write(plemma[j]);
                writer.write('\t');
                writer.write(pos[j]);
                writer.write('\t');
                writer.write(ppos[j]);
                writer.write('\t');
                writer.write(feat[j]);
                writer.write('\t');
                writer.write(pfeat[j]);
                // HEAD
                writer.write('\t');
                writer.write(head[j]);
                writer.write('\t');
                writer.write(prediction[j][0]);
                // REL
                writer.write('\t');
                writer.write(rel[j]);
                writer.write('\t');
                writer.write(prediction[j][1]);
                // FX
                writer.write('\t');
                writer.write(fx[j]);

                writer.write('\n');
            }
            writer.write('\n');
        }

        System.out.println();
    }

    public static void predicateCorpus(String corpus)
    {
        String predFile = WORK_DIR + PRED_DIR + corpus + ".dep.fx.pred";

        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(predFile), "UTF-8"));

            for (int i = 0; i < 10; ++i)
            {
                String testFile = WORK_DIR + TEST_DIR + corpus + ".dep.fx.test." + i;
                String modelFile = WORK_DIR + MODEL_DIR + corpus + ".dep.fx.model." + i;

                System.out.println(testFile + "\t" + modelFile + "\t" + predFile);
                preidcate(testFile, modelFile, writer);
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void mergeSubCorpuses(String[] subCorpuses, String out)
    {
        try
        {
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(WORK_DIR + CORPUS_DIR + out), "UTF-8"));

            for (String subCorpus : subCorpuses)
            {
                List<List<String>> sentences = read(WORK_DIR + subCorpus);

                System.err.println(subCorpus + "\t" + sentences.size());

                for (List<String> sentence : sentences)
                {
                    for (String token : sentence)
                    {
                        String[] splitted = token.split("\t");

                        writer.write(splitted[0]);
                        for (int i = 1; i < 8; ++i)
                        {
                            writer.write('\t');
                            writer.write(splitted[i]);
                        }

                        for (int i = 10; i < splitted.length; ++i)
                        {
                            writer.write('\t');
                            writer.write(splitted[i]);
                        }

                        writer.write('\n');
                    }
                    writer.write('\n');
                }
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        System.err.println("\n" + out + "\t\t" + read(WORK_DIR + CORPUS_DIR + out).size() + "\n\n");
    }

    public static double LAS(String file)
    {
        List<List<String>> sentences = read(WORK_DIR + PRED_DIR + file);

        int counter = 0;
        int las = 0;

        for (List<String> sentence : sentences)
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");

                if (splitted[2].equals(splitted[3])
                 && splitted[4].equals(splitted[5])
                 && splitted[6].equals(splitted[7])
                 && splitted[8].equals(splitted[9])
                 && splitted[10].equals(splitted[11]))
                {
                    ++las;
                }
                ++counter;
            }
        }

        return ((double) las / counter);
    }

    public static double UAS(String file)
    {
        List<List<String>> sentences = read(WORK_DIR + PRED_DIR + file);

        int counter = 0;
        int uas = 0;

        for (List<String> sentence : sentences)
        {
            for (String token : sentence)
            {
                String[] splitted = token.split("\t");

                if (splitted[8].equals(splitted[9]))
                {
                    ++uas;
                }
                ++counter;
            }
        }

        return ((double) uas / counter);
    }

    public static void main(String args[])
    {
        // merge

        // mergeSubCorpuses(new String[] { "10erv.dep.fx", "10elb.dep.fx", "8oelb.dep.fx" }, "composition.dep.fx");
        // mergeSubCorpuses(new String[] { "utas.dep.fx", "pfred.dep.fx", "1984.dep.fx" }, "literature.dep.fx");
        // mergeSubCorpuses(new String[] { "gazdtar.dep.fx", "szerzj.dep.fx" }, "law.dep.fx");
        // mergeSubCorpuses(new String[] { "nv.dep.fx", "np.dep.fx", "hvg.dep.fx", "mh.dep.fx" }, "newspaper.dep.fx");
        // mergeSubCorpuses(new String[] { "newsml.dep.fx" }, "newsml.dep.fx");

        // 10 fold

        // for (String corpus : new String[] { "composition", "law", "newsml", "newspaper", "literature" })
        // write10Fold(corpus + ".dep.fx");

        // predicate

        // for (String corpus : new String[] { "newspaper", "literature", "composition", "newsml", "law" })
        // predicateCorpus(corpus);

        // for (String corpus : new String[] { "newspaper", "literature", "composition", "newsml", "law" }) {
        // System.out.println(corpus + "\t" + UAS(corpus + ".dep.fx.pred") + "\t" + LAS(corpus + ".dep.fx.pred"));
        // System.out.println(corpus + "\t" + LAS(corpus + ".dep.fx.pred"));
        // }
    }
}
EOF
mkdir -p szte/pos/converter && cat > szte/pos/converter/CoNLLFeaturesToMSD.java <<'EOF'
package szte.pos.converter;

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

    private Set<String> possibleFeatures;

    public CoNLLFeaturesToMSD()
    {
        /**
         * possible conll-2009 feature names
         */
        String[] features = new String[] { "SubPOS", "Num", "Cas", "NumP", "PerP",
            "NumPd", "Mood", "Tense", "Per", "Def", "Deg", "Clitic", "Form",
            "Coord", "Type" };
        this.setPossibleFeatures(new TreeSet<String>());

        for (String feature : features)
        {
            this.getPossibleFeatures().add(feature);
        }
    }

    private void setPossibleFeatures(Set<String> possibleFeatures)
    {
        this.possibleFeatures = possibleFeatures;
    }

    private Set<String> getPossibleFeatures()
    {
        return possibleFeatures;
    }

    /**
     * clean the unnecessary - signs from the end of the MSD code for ex. the
     * Nc-sn------ will be cleandet to Nc-sn.
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
     * Split the String of the features via the | sing, and put the featurenames
     * and its values to a map.
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
     * Convert the features to MSD code, using the MSD positions and
     * featurevalues, that belongs to the current POS.
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

        /**
         * featuresstring can't be empy or null
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

            if ((featuresMap == null) && (pos != 'X' && pos != 'Y' && pos != 'Z' && pos != 'I'))
            {
            }

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
mkdir -p szte/pos/converter && cat > szte/pos/converter/KRToMSD.java <<'EOF'
package szte.pos.converter;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import szte.magyarlanc.MorAna;
import szte.magyarlanc.Settings;
import szte.magyarlanc.resource.ResourceHolder;

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
        int verbIndex = krAnalysis.indexOf("/VERB");
        int adjIndex = krAnalysis.indexOf("/ADJ");

        if (verbIndex > -1 && adjIndex > -1 && adjIndex > verbIndex)
        {
            return true;
        }

        return false;
    }

    public String getPostPLemma(String analysis)
    {
        if (analysis.startsWith("$én/NOUN<POSTP<")
         || analysis.startsWith("$te/NOUN<POSTP<")
         || analysis.startsWith("$ők/NOUN<POSTP<")
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
        StringBuilder msd = new StringBuilder(Settings.DEFAULT_NOUN + "------");

        /*
         * nevmas minden PERS-t tartalmazo NOUN
         */

        // velem
        // /NOUN<PERS<1>><CAS<INS>>

        if (kr.contains("PERS"))
        {
            msd = new StringBuilder("Pp--sn-----------");
            /*
             * szemely
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
             * szam
             */

            if (kr.contains("<PLUR>"))
            {
                msd.setCharAt(4, 'p');
            }

            /*
             * eset
             */

            // n nincs jelolve alapeset

            // a
            if (kr.contains("<CAS<ACC>>"))
            {
                msd.setCharAt(5, 'a');
            }

            // g nincs jelolve
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
         * nevmas minden POSTP-t tartalmazo NOUN
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
         * egyes szam/tobbes szam NOUN<PLUR> NUON<PLUR<FAM>>
         */

        if (kr.contains("NOUN<PLUR"))
        {
            msd.setCharAt(3, 'p');
        }

        /*
         * eset
         */

        // n nincs jelolve alapeset

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
         * birtokos szama/szemelye
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
         * birtok(olt) szama
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
         * tipus (melleknev vagy melleknevi igenev)
         */

        // f (melleknev) nincs jelolve, alapeset

        // p (folyamatos melleknevi igenev)

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

        // p nincs jelolve alapeset

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
        // s nincs jelolve alapeset

        // p
        if (kr.contains("ADJ<PLUR>"))
        {
            msd.setCharAt(4, 'p');
        }

        /*
         * eset
         */

        // n nincs jelolve alapeset

        // a
        if (kr.contains("<CAS<ACC>>"))
        {
            msd.setCharAt(5, 'a');
        }

        // g nincs jelolve
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
         * birtokos szama/szemelye
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
         * birtok(olt) szama
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

        /*
         * magyarlanc 2.5-től
         */

        // hato
        if (kr.contains("<MODAL>") && !kr.contains("[FREQ]") && !kr.contains("[CAUS]"))
        {
            msd.setCharAt(1, 'o');
        }

        // gyakorito
        if (!kr.contains("<MODAL>") && kr.contains("[FREQ]") && !kr.contains("[CAUS]"))
        {
            msd.setCharAt(1, 'f');
        }

        // muvelteto
        if (!kr.contains("<MODAL>") && !kr.contains("[FREQ]") && kr.contains("[CAUS]"))
        {
            msd.setCharAt(1, 's');
        }

        // gyakorito + hato
        if (kr.contains("<MODAL>") && kr.contains("[FREQ]") && !kr.contains("[CAUS]"))
        {
            msd.setCharAt(1, '1');
        }

        // muvelteto + hato
        if (kr.contains("<MODAL>") && !kr.contains("[FREQ]") && kr.contains("[CAUS]"))
        {
            msd.setCharAt(1, '2');
        }

        // muvelteto + hato
        if (!kr.contains("<MODAL>") && kr.contains("[FREQ]") && kr.contains("[CAUS]"))
        {
            msd.setCharAt(1, '3');
        }

        // muvelteto + gyakorito + hato
        if (kr.contains("<MODAL>") && kr.contains("[FREQ]") && kr.contains("[CAUS]"))
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

        // c alapeset, nincs jelolve

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

        // s alapeset, nincs jelolve
        // p
        if (kr.contains("NUM<PLUR>"))
        {
            msd.setCharAt(3, 'p');
        }

        /*
         * eset
         */

        // n nincs jelolve alapeset

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
         * birtokos szama/szemelye
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

        String stem = null;

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

        String msd = null;

        if (krCode.startsWith("NOUN"))
        {
            msd = convertNoun(lemma, krCode);

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
            /*
             * magyarlanc 2.5-től
             */

            // melleknevi igenev
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

            // msd = convertAdjective(krCode);
            // analisis.add(new MorAna(lemma, msd));

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
            msd = convertNumber(krCode, krAnalysis);
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
mkdir -p szte/pos/converter && cat > szte/pos/converter/KRUtils.java <<'EOF'
package szte.pos.converter;

import java.util.LinkedList;
import java.util.List;
import java.util.regex.MatchResult;
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
    public enum KRPOS
    {
        VERB, NOUN, ADJ, NUM, ADV, PREV, ART, POSTP, UTT_INT, DET, CONJ, ONO, PREP, X;
    }

    /**
     * Search for a pattern in a text by group number.
     *
     * @param text
     * @param pattern
     * @param group
     * @return
     */
    private static String findPattern(String text, String pattern, int group)
    {
        Matcher m = Pattern.compile(pattern).matcher(text);
        m.find();

        return m.group(group);
    }

    /**
     *
     * Search for the first occurence of the pattern in the text.
     *
     * @param text
     * @param pattern
     * @return
     */
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
        {
            return "$több/NUM<CAS<ACC>>";
        }

        // legtöbb
        if (morph.startsWith("$sok/NUM[SUPERLAT]/NUM<CAS<"))
        {
            return "$legtöbb/NUM<CAS<ACC>>";
        }

        // legeslegtöbb
        if (morph.startsWith("$sok/NUM[SUPER-SUPERLAT]/NUM<CAS<"))
        {
            return "$legeslegtöbb/NUM<CAS<ACC>>";
        }

        String root = null;

        if (!morph.contains("/"))
        {
            return morph;
        }
        else
        {
            String igekoto = "";

            // igekoto
            if (morph.contains("/PREV+"))
            {
                igekoto = morph.split("/PREV\\+")[0];
                morph = morph.split("/PREV\\+")[1];
            }

            String[] tovek = morph.split("/");
            tovek = preProcess(tovek);

            String vegsoto = findPatterns(tovek[0], "^([^\\(\\/]*)").get(0);
            boolean ikes = false;

            List<String> feladatok;

            if (tovek.length > 2)
            {
                for (int i = 0; i < tovek.length - 1; i++)
                {
                    if (tovek[i].matches(".*\\(.*\\).*"))
                    {
                        feladatok = findPatterns(tovek[i], "\\((.*?)\\)");
                        int backValue = 0;
                        for (String feladat : feladatok)
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

                                String firsPart;
                                String secondPart;
                                firsPart = findPattern(vegsoto, "^(.*?).([bcdfghjklmnpqrstvwxyz!]*)$", 1);
                                secondPart = findPattern(vegsoto, "^(.*?).([bcdfghjklmnpqrstvwxyz!]*)$", 2);
                                vegsoto = firsPart + "!" + secondPart;
                            }
                            else if (feladat.matches("^\\.(.*)"))
                            {
                                // .a .e .i .o .� .u .�
                                String csere;
                                csere = findPattern(feladat, "^\\.(.*)", 1);
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

        if ((end > code.length()))
        {
            end = code.length();
        }

        if (code.substring(start, end).equals("VERB"))
        {
            return KRPOS.VERB;
        }
        if (code.substring(start, end).equals("NOUN"))
        {
            return KRPOS.NOUN;
        }
        if (code.substring(start + 1, end).equals("ADJ"))
        {
            return KRPOS.ADJ;
        }
        if (code.substring(start + 1, end).equals("NUM"))
        {
            return KRPOS.NUM;
        }
        if (code.substring(start + 1, end).equals("ADV"))
        {
            return KRPOS.ADV;
        }
        if (code.substring(start + 1, end).equals("PREV"))
        {
            return KRPOS.PREV;
        }
        if (code.substring(start + 1, end).equals("ART"))
        {
            return KRPOS.ART;
        }
        if (code.substring(start + 1, end).equals("POSTP"))
        {
            return KRPOS.POSTP;
        }
        if (code.substring(start + 1, end).equals("UTT-INT"))
        {
            return KRPOS.UTT_INT;
        }
        if (code.substring(start + 1, end).equals("DET"))
        {
            return KRPOS.DET;
        }
        if (code.substring(start + 1, end).equals("CONJ"))
        {
            return KRPOS.CONJ;
        }
        if (code.substring(start + 1, end).equals("ONO"))
        {
            return KRPOS.ONO;
        }
        if (code.substring(start + 1, end).equals("PREP"))
        {
            return KRPOS.PREP;
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
mkdir -p szte/pos/converter && cat > szte/pos/converter/MSDReducer.java <<'EOF'
package szte.pos.converter;

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
mkdir -p szte/pos/converter && cat > szte/pos/converter/MSDToCoNLLFeatures.java <<'EOF'
package szte.pos.converter;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import szte.magyarlanc.resource.ResourceHolder;
import szte.magyarlanc.resource.Util;

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
mkdir -p szte/pos/guesser && cat > szte/pos/guesser/CompoundWord.java <<'EOF'
/**
 * This file is part of magyarlanc 2.0.
 *
 * magyarlanc 2.0 is free software for evaluation, research and
 * educational purposes: you can use it under the terms of the license
 * available here: http://www.inf.u-szeged.hu/rgai/magyarlanc_license
 *
 * magyarlanc 2.0 is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * license for more details.
 *
 * http://www.inf.u-szeged.hu/rgai/magyarlanc
 *
 * Developed by:
 * Research Group on Artificial Intelligence of the Hungarian Academy of Sciences
 * http://www.inf.u-szeged.hu/rgai/
 *
 * Contact:
 * Richárd Farkas
 * rfarkas@inf.u-szeged.hu
 */
package szte.pos.guesser;

import java.util.Collection;
import java.util.LinkedHashSet;

import szte.magyarlanc.resource.ResourceHolder;
import szte.pos.converter.KRUtils;
import szte.pos.converter.KRUtils.KRPOS;

/**
 * összetett szavak elemzése
 *
 * @author zsjanos
 *
 */
public class CompoundWord
{
    public static boolean isCompatibleAnalyises(String firstPartKR, String secondPartKR)
    {
        KRPOS secondPartPOS = KRUtils.getPOS(secondPartKR);
        KRPOS firstPartPOS = KRUtils.getPOS(secondPartKR);

        // UTT-INT nem lehet a második rész
        if (secondPartPOS.equals(KRPOS.UTT_INT))
        {
            return false;
        }

        // ART nem lehet a második rész
        if (secondPartPOS.equals(KRPOS.ART))
        {
            return false;
        }

        // NUM előtt csak NUM állhat
        if (secondPartPOS.equals(KRPOS.NUM) && !firstPartPOS.equals(KRPOS.NUM))
        {
            return false;
        }

        // PREV nem lehet a második rész
        if (secondPartPOS.equals(KRPOS.PREV))
        {
            return false;
        }

        // NOUN + ADV letiltva
        if (firstPartPOS.equals(KRPOS.NOUN) && secondPartPOS.equals(KRPOS.ADV))
        {
            return false;
        }

        // VERB + ADV letiltva
        if (firstPartPOS.equals(KRPOS.VERB) && secondPartPOS.equals(KRPOS.ADV))
        {
            return false;
        }

        // PREV + NOUN letiltva
        if (firstPartPOS.equals(KRPOS.PREV) && secondPartPOS.equals(KRPOS.NOUN))
        {
            return false;
        }

        // ADJ + VERB letiltva
        if (firstPartPOS.equals(KRPOS.ADJ) && secondPartPOS.equals(KRPOS.VERB))
        {
            return false;
        }

        // VERB + NOUN letiltva
        if (firstPartPOS.equals(KRPOS.VERB) && secondPartPOS.equals(KRPOS.NOUN))
        {
            return false;
        }

        // NOUN + VERB csak akkor lehet, ha van a NOUN-nak <CAS>
        if (firstPartPOS.equals(KRPOS.NOUN) && secondPartPOS.equals(KRPOS.VERB)
                && !firstPartKR.contains("CAS"))
        {
            return false;
        }

        // NOUN + VERB<PAST><DEF> �s nincs a NOUN-nak <CAS> akkor /ADJ
        if (firstPartPOS.equals(KRPOS.NOUN) && secondPartPOS.equals(KRPOS.VERB)
                && !firstPartKR.contains("CAS") && secondPartKR.contains("<PAST><DEF>")
                && secondPartKR.contains("<DEF>"))
        {
            return false;
        }

        return true;
    }

    public static boolean isBisectable(String compoundWord)
    {
        for (int i = 2; i < compoundWord.length() - 1; ++i)
        {
            if (ResourceHolder.getRFSA().analyse(compoundWord.substring(0, i)).size() > 0
                    && ResourceHolder.getRFSA().analyse(compoundWord.substring(i)).size() > 0)
            {
                return true;
            }
        }

        return false;
    }

    public static int bisectIndex(String compoundWord)
    {
        for (int i = 2; i < compoundWord.length() - 1; ++i)
        {
            if (ResourceHolder.getRFSA().analyse(compoundWord.substring(0, i)).size() > 0
                    && ResourceHolder.getRFSA().analyse(compoundWord.substring(i)).size() > 0)
            {
                return i;
            }
        }

        return 0;
    }

    public static LinkedHashSet<String> getCompatibleAnalises(String firstPart, String secondPart)
    {
        return getCompatibleAnalises(firstPart, secondPart, false);
    }

    public static LinkedHashSet<String> getCompatibleAnalises(String firstPart, String secondPart, boolean hyphenic)
    {
        LinkedHashSet<String> analises = new LinkedHashSet<String>();

        Collection<String> firstAnalises = ResourceHolder.getRFSA().analyse(firstPart);
        Collection<String> secondAnalises = ResourceHolder.getRFSA().analyse(secondPart);

        if (firstAnalises.size() > 0 && secondAnalises.size() > 0)
        {
            for (String f : firstAnalises)
            {
                for (String s : secondAnalises)
                {
                    String firstPartKR = KRUtils.getRoot(f);
                    String secondPartKR = KRUtils.getRoot(s);

                    if (isCompatibleAnalyises(firstPartKR, secondPartKR))
                    {
                        if (hyphenic)
                        {
                            analises.add(secondPartKR.replace("$", "$" + firstPart + "-"));
                        }
                        else
                        {
                            analises.add(secondPartKR.replace("$", "$" + firstPart));
                        }
                    }
                }
            }
        }

        return analises;
    }

    public static LinkedHashSet<String> analyseCompoundWord(String compoundWord)
    {
        // 2 részre vágható van elemzés
        if (isBisectable(compoundWord))
        {
            int bisectIndex = bisectIndex(compoundWord);
            String firstPart = compoundWord.substring(0, bisectIndex);
            // System.out.println(firstPart);
            String secondPart = compoundWord.substring(bisectIndex);
            // System.out.println(secondPart);
            return getCompatibleAnalises(firstPart, secondPart);
        }

        LinkedHashSet<String> analises = new LinkedHashSet<String>();

        // ha nem bontható 2 részre
        for (int i = 2; i < compoundWord.length() - 1; ++i)
        {
            String firstPart = compoundWord.substring(0, i);
            String secondPart = compoundWord.substring(i);

            Collection<String> firstPartAnalises = ResourceHolder.getRFSA().analyse(firstPart);
            if (firstPartAnalises.size() > 0)
            {
                // ha a második rész két részre bontható
                if (isBisectable(secondPart))
                {
                    int bisectIndex = bisectIndex(secondPart);
                    String firstPartOfSecondSection = secondPart.substring(0, bisectIndex);
                    String secondPartOfSecondSection = secondPart.substring(bisectIndex);

                    LinkedHashSet<String> secondPartAnalises = getCompatibleAnalises(firstPartOfSecondSection, secondPartOfSecondSection);

                    for (String firstAnalyse : firstPartAnalises)
                    {
                        for (String secondAnalyse : secondPartAnalises)
                        {
                            if (isCompatibleAnalyises(KRUtils.getRoot(firstAnalyse), KRUtils.getRoot(secondAnalyse)))
                            {
                                analises.add(KRUtils.getRoot(secondAnalyse).replace("$", "$" + firstPart));
                            }
                        }
                    }
                }
            }
        }

        return analises;
    }
}
EOF
mkdir -p szte/pos/guesser && cat > szte/pos/guesser/HyphenicGuesser.java <<'EOF'
/**
 * Developed by:
 *   Research Group on Artificial Intelligence of the Hungarian Academy of Sciences
 *   http://www.inf.u-szeged.hu/rgai/
 *
 * Contact:
 *  János Zsibrita
 *  zsibrita@inf.u-szeged.hu
 *
 * Licensed by Creative Commons Attribution Share Alike
 *
 * http://creativecommons.org/licenses/by-sa/3.0/legalcode
 */
package szte.pos.guesser;

import java.util.Set;
import java.util.TreeSet;

import szte.magyarlanc.MorAna;
import szte.magyarlanc.resource.ResourceHolder;

/**
 *
 * @author zsjanos
 */

public class HyphenicGuesser
{
    public static Set<MorAna> guess(String root, String suffix)
    {
        Set<MorAna> morAnas = new TreeSet<MorAna>();

        // kötőjeles suffix (pl.: Bush-hoz)
        morAnas.addAll(MorPhonGuesser.guess(root, suffix));

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

    public static void main(String[] args)
    {
        System.out.println(HyphenicGuesser.guess("Bush", "hoz"));
        System.out.println(HyphenicGuesser.guess("Bush", "kormánynak"));
    }
}
EOF
mkdir -p szte/pos/guesser && cat > szte/pos/guesser/HyphenicWord.java <<'EOF'
package szte.pos.guesser;

import java.util.Collection;
import java.util.LinkedHashSet;

import szte.magyarlanc.resource.ResourceHolder;
import szte.pos.converter.KRUtils;

public class HyphenicWord
{
    public static LinkedHashSet<String> analyseHyphenicCompoundWord(String hyphenicCompoundWord)
    {
        LinkedHashSet<String> analises = new LinkedHashSet<String>();

        if (!hyphenicCompoundWord.contains("-"))
        {
            return analises;
        }

        int hyphenPosition = hyphenicCompoundWord.indexOf('-');
        String firstPart = hyphenicCompoundWord.substring(0, hyphenPosition);
        String secondPart = hyphenicCompoundWord.substring(hyphenPosition + 1);

        // a kötőjel előtti és a kötőjel utáni résznek is van elemzése (pl.: adat-kezelőt)
        if (CompoundWord.isBisectable(firstPart + secondPart))
        {
            analises = CompoundWord.getCompatibleAnalises(firstPart, secondPart, true);
        }

        // a kötőjel előtti résznek is van elemzése, a kötőjel utáni rész két részre bontható
        else if (ResourceHolder.getRFSA().analyse(firstPart).size() > 0 && CompoundWord.isBisectable(secondPart))
        {
            Collection<String> firstPartAnalises = ResourceHolder.getRFSA().analyse(firstPart);

            int bisectIndex = CompoundWord.bisectIndex(secondPart);
            String firstPartOfSecondSection = secondPart.substring(0, bisectIndex);
            String secondPartOfSecondSection = secondPart.substring(bisectIndex);

            LinkedHashSet<String> secondSectionAnalises = CompoundWord.getCompatibleAnalises(firstPartOfSecondSection, secondPartOfSecondSection);

            for (String firstAnalyse : firstPartAnalises)
            {
                for (String secondAnalyse : secondSectionAnalises)
                {
                    if (CompoundWord.isCompatibleAnalyises(KRUtils.getRoot(firstAnalyse), KRUtils.getRoot(secondAnalyse)))
                    {
                        if (analises == null)
                        {
                            analises = new LinkedHashSet<String>();
                        }
                        analises.add(KRUtils.getRoot(secondAnalyse).replace("$", "$" + firstPart + "-"));
                    }
                }
            }
        }

        else if (CompoundWord.isBisectable(firstPart) && ResourceHolder.getRFSA().analyse(secondPart).size() > 0)
        {
            Collection<String> secondPartAnalises = ResourceHolder.getRFSA().analyse(secondPart);

            int bisectIndex = CompoundWord.bisectIndex(firstPart);
            String firstSectionOfFirstPart = firstPart.substring(0, bisectIndex);
            String secondSectionOfFirstPart = firstPart.substring(bisectIndex);

            LinkedHashSet<String> firstPartAnalises = CompoundWord.getCompatibleAnalises(firstSectionOfFirstPart, secondSectionOfFirstPart);

            for (String firstAnalyse : firstPartAnalises)
            {
                for (String secondAnalyse : secondPartAnalises)
                {
                    if (CompoundWord.isCompatibleAnalyises(KRUtils.getRoot(firstAnalyse), KRUtils.getRoot(secondAnalyse)))
                    {
                        if (analises == null)
                        {
                            analises = new LinkedHashSet<String>();
                        }
                        analises.add(KRUtils.getRoot(secondAnalyse).replace("$", "$" + firstPart + "-"));
                    }
                }
            }
        }

        return analises;
    }
}
EOF
mkdir -p szte/pos/guesser && cat > szte/pos/guesser/MorPhonGuesser.java <<'EOF'
/**
 * Developed by:
 *   Research Group on Artificial Intelligence of the Hungarian Academy of Sciences
 *   http://www.inf.u-szeged.hu/rgai/
 *
 * Contact:
 *  Janos Zsibrita
 *  zsibrita@inf.u-szeged.hu
 *
 * Licensed by Creative Commons Attribution Share Alike
 *
 * http://creativecommons.org/licenses/by-sa/3.0/legalcode
 */
package szte.pos.guesser;

import java.util.Set;
import java.util.TreeSet;

import szte.magyarlanc.MorAna;
import szte.magyarlanc.resource.ResourceHolder;

/**
 * A MorPhonGuesser osztaly egy ismeretlen (nem elemezheto) fonevi szoto es
 * tetszoleges suffix guesselesere szolgal. A guesseles soran az adott suffixet
 * a rendszer morPhonDir szotaranak elemeire illesztve probajuk elemezni. A
 * szotar reprezentalja a magyar nyelv minden (nem hasonulo) illeszkedesi
 * szabalyat, igy biztosak lehetenk benne, hogy egy valos toldalek mindenkepp
 * illeszkedni fog legalabb egy szotarelemre. Peldaul egy 'hoz'rag eseten,
 * eleszor a kod elemre probalunk illeszteni, majd elemezni. A kapott szoalak
 * igy a kodhez lesz, melyre a KR elemzonk nem ad elemzest. A kovetkezo
 * szotarelem a talany, a szoalak a talanyhoz lesz, melyre megkapjuk az Nc-st
 * (kulso kozelito/allative) fonevi elemzest.
 */
public class MorPhonGuesser
{
    public static Set<MorAna> guess(String root, String suffix)
    {
        Set<MorAna> stems = new TreeSet<MorAna>();

        for (String guess : ResourceHolder.getMorPhonDir())
        {
            if (ResourceHolder.getRFSA().analyse(guess + suffix).size() > 0)
            {
                for (String kr : ResourceHolder.getRFSA().analyse(guess + suffix))
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
        }

        return stems;
    }

    public static void main(String[] args)
    {
        System.out.println(guess("London", "ban"));
    }
}
EOF
mkdir -p szte/pos/guesser && cat > szte/pos/guesser/NumberGuesser.java <<'EOF'
/**
 * Developed by:
 *   Research Group on Artificial Intelligence of the Hungarian Academy of Sciences
 *   http://www.inf.u-szeged.hu/rgai/
 *
 * Contact:
 *  Janos Zsibrita
 *  zsibrita@inf.u-szeged.hu
 *
 * Licensed by Creative Commons Attribution Share Alike
 *
 * http://creativecommons.org/licenses/by-sa/3.0/legalcode
 */
package szte.pos.guesser;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import szte.magyarlanc.MorAna;
import szte.magyarlanc.Settings;
import szte.magyarlanc.resource.ResourceHolder;

/**
 * Minden szammal kezdodo token elemzesét a NumberGuesser osztaly végzi,
 * regularis kifejezések segitsegevel. Egy szolakhoz tobb elemzes is tartozhat.
 * Egy szammal kezdodo token lehet fonev (N) (pl.: 386-os@Nn-sn), melleknev
 * (pl.: 16-ai@Afp-sn), szamnev (pl. 5.@Mo-snd) vagy nyilt tokenosztalyba
 * tartozo (pl.: 20%@Onp-sn).
 */
public class NumberGuesser
{
    // main number pattern
    private final static Pattern PATTERN_0 = Pattern.compile("[0-9]+.*");

    // 1-es 1.3-as 1,5-ös 1/6-os 16-17-es [Afp-sn, Nn-sn]
    private static Pattern PATTERN_1 = Pattern.compile("([0-9]+[0-9\\.,%-/]*-(as|ás|es|os|ös)+)([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 16-i
    private static Pattern PATTERN_2 = Pattern.compile("[0-9]+[0-9\\.,-/]*-i");

    // 16-(ai/ei/jei)
    private static Pattern PATTERN_3 = Pattern.compile("([0-9]+-(ai|ei|jei)+)([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // +12345
    private static Pattern PATTERN_4 = Pattern.compile("([\\+|\\-]{1}[0-9]+[0-9\\.,-/]*)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 12345-12345
    private static Pattern PATTERN_5 = Pattern.compile("([0-9]+-[0-9]+)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 12:30 12.30 Ont-sn
    private static Pattern PATTERN_6 = Pattern.compile("(([0-9]{1,2})[\\.:]([0-9]{2}))-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 123,45-12345
    private static Pattern PATTERN_7 = Pattern.compile("([0-9]+,[0-9]+-[0-9]+)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 12345-12345,12345
    private static final Pattern PATTERN_8 = Pattern.compile("([0-9]+-[0-9]+,[0-9]+)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 12345,12345-12345,12345
    private static final Pattern PATTERN_9 = Pattern.compile("([0-9]+,[0-9]+-[0-9]+,[0-9]+)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 12345.12345,12345
    private static final Pattern PATTERN_10 = Pattern.compile("([0-9]+\\.[0-9]+,[0-9]+)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 10:30
    private static final Pattern PATTERN_11 = Pattern.compile("([0-9]+:[0-9]+)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 12345.12345.1234-.
    private static final Pattern PATTERN_12 = Pattern.compile("([0-9]+\\.[0-9]+[0-9\\.]*)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 12,3-nak
    private static final Pattern PATTERN_13 = Pattern.compile("([0-9]+,[0-9]+)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 20-nak
    private static final Pattern PATTERN_14 = Pattern.compile("([0-9]+)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 20.
    private static final Pattern PATTERN_15 = Pattern.compile("(([0-9]+-??[0-9]*)\\.)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 16-áig
    private static final Pattern PATTERN_16 = Pattern.compile("(([0-9]{1,2})-(á|é|jé))([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

    // 16-a
    private static final Pattern PATTERN_17 = Pattern.compile("(([0-9]{1,2})-(a|e|je))()");

    // 50%
    private static final Pattern PATTERN_18 = Pattern.compile("([0-9]+,??[0-9]*%)-??([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]*)");

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
    public static Set<MorAna> guess(String number)
    {
        Set<MorAna> stemSet = new TreeSet<MorAna>();

        // base number pattern
        Matcher matcher = PATTERN_0.matcher(number);
        if (!matcher.matches())
        {
            return stemSet;
        }

        matcher = PATTERN_1.matcher(number);

        if (matcher.matches())
        {
            String root = matcher.group(1);
            // group 3!!!
            // 386-osok (386-(os))(ok)
            String suffix = matcher.group(3);

            if (suffix.length() > 0)
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, stem.getMsd()));
                    stemSet.add(new MorAna(root, stem.getMsd().replace(Settings.DEFAULT_NOUN.substring(0, 2), "Afp")));
                }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(matcher.group(1), "Afp-sn"));
                stemSet.add(new MorAna(matcher.group(1), Settings.DEFAULT_NOUN));
            }

            return stemSet;
        }

        // 16-i
        matcher = PATTERN_2.matcher(number);
        if (matcher.matches())
        {
            stemSet.add(new MorAna(number, "Afp-sn"));
            stemSet.add(new MorAna(number, "Onf-sn"));
            return stemSet;
        }

        // 16-(ai/ei/1-jei)
        matcher = PATTERN_3.matcher(number);
        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(3);

            if (suffix.length() > 0)
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, "Afp-" + stem.getMsd().substring(3)));
                }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(matcher.group(1), "Afp-sn"));
            }

            return stemSet;
        }

        // +/-12345
        matcher = PATTERN_4.matcher(number);
        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Ons----------")));
                }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(number, "Ons-sn"));
            }

            return stemSet;
        }

        // 12:30 12.30 Ont-sn
        matcher = PATTERN_6.matcher(number);
        if (matcher.matches())
        {
            if (Integer.parseInt(matcher.group(2)) < 24 && Integer.parseInt(matcher.group(3)) < 60)
            {
                String root = matcher.group(1);
                String suffix = matcher.group(4);

                if (suffix.length() > 0)
                    for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                    {
                        stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Ont---------")));
                    }

                if (stemSet.size() == 0)
                {
                    stemSet.add(new MorAna(number, "Ont-sn"));
                }
            }
        }

        // 12345-12345-*
        matcher = PATTERN_5.matcher(number);
        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Onr---------")));
                    stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Onf----------")));
                    stemSet.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d-------")));
                }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(number, "Onr-sn"));
                stemSet.add(new MorAna(number, "Onf-sn"));
                stemSet.add(new MorAna(number, "Mc-snd"));
            }

            return stemSet;
        }

        // 12345,12345-12345,12345-*
        // 12345-12345,12345-*
        // 12345,12345-12345-*

        matcher = PATTERN_7.matcher(number);

        if (!matcher.matches())
        {
            matcher = PATTERN_8.matcher(number);
        }
        if (!matcher.matches())
        {
            matcher = PATTERN_9.matcher(number);
        }

        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mf---d-------")));
                }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(number, "Mf-snd"));
            }

            return stemSet;
        }

        // 12345.12345,12345
        matcher = PATTERN_10.matcher(number);
        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Ond---------")));
                }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(number, "Ond-sn"));
            }

            return stemSet;
        }

        // 10:30-*
        matcher = PATTERN_11.matcher(number);
        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(2);

            if (suffix.length() > 0)
            {
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Onf---------")));
                    stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Onq---------")));
                    stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Onr---------")));
                }
            }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(number, "Onf-sn"));
                stemSet.add(new MorAna(number, "Onq-sn"));
                stemSet.add(new MorAna(number, "Onr-sn"));
            }

            return stemSet;
        }

        // 12345.12345.1234-.
        matcher = PATTERN_12.matcher(number);
        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(2);

            if (suffix.length() > 0)
            {
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Oi----------")));
                    stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Ond---------")));
                }
            }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(number, "Oi--sn"));
                stemSet.add(new MorAna(number, "Ond-sn"));
            }

            return stemSet;
        }

        // 16-a 17-e 16-áig 17-éig 1-je 1-jéig

        matcher = PATTERN_16.matcher(number);

        if (!matcher.matches())
        {
            matcher = PATTERN_17.matcher(number);
        }

        if (matcher.matches())
        {
            String root = matcher.group(2);
            String suffix = matcher.group(4);

            if (suffix.length() > 0)
            {
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d----s3-")));
                    if (szte.magyarlanc.resource.Util.isDate(matcher.group(2)))
                    {
                        stemSet.add(new MorAna(root + ".", nounToNoun(stem.getMsd(), Settings.DEFAULT_NOUN.substring(0, 2) + "------s3-")));
                    }

                    if (matcher.group(3).equals("�"))
                    {
                        stemSet.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d------s")));
                    }
                }
            }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(matcher.group(2), "Mc-snd----s3"));
                if (szte.magyarlanc.resource.Util.isDate(matcher.group(2)))
                {
                    stemSet.add(new MorAna(matcher.group(2) + ".", Settings.DEFAULT_NOUN + "---s3"));
                }
            }

            return stemSet;
        }

        // 50%
        matcher = PATTERN_18.matcher(number);

        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToOther(stem.getMsd(), "Onp---------")));
                }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(root, "Onp-sn"));
            }

            return stemSet;
        }

        // 12,3-nak
        matcher = PATTERN_13.matcher(number);
        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mf---d-------")));
                }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(number, "Mf-snd"));
            }

            return stemSet;
        }

        // 20-nak
        matcher = PATTERN_14.matcher(number);
        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(2);

            if (suffix.length() > 0)
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mc---d-------")));
                }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(number, "Mc-snd"));
            }

            return stemSet;
        }

        // 15.
        matcher = PATTERN_15.matcher(number);
        if (matcher.matches())
        {
            String root = matcher.group(1);
            String suffix = matcher.group(3);

            if (suffix.length() > 0)
                for (MorAna stem : MorPhonGuesser.guess(root, suffix))
                {
                    stemSet.add(new MorAna(root, nounToNumeral(stem.getMsd(), "Mo---d-------")));

                    if (szte.magyarlanc.resource.Util.isDate(matcher.group(2)))
                    {
                        stemSet.add(new MorAna(root, stem.getMsd()));
                    }
                }

            if (stemSet.size() == 0)
            {
                stemSet.add(new MorAna(number, "Mo-snd"));
                if (szte.magyarlanc.resource.Util.isDate(matcher.group(2)))
                {
                    stemSet.add(new MorAna(number, Settings.DEFAULT_NOUN));
                    stemSet.add(new MorAna(number, Settings.DEFAULT_NOUN + "---s3"));
                }
            }

            return stemSet;
        }

        if (stemSet.size() == 0)
        {
            stemSet.add(new MorAna(number, "Oi--sn"));
        }

        return stemSet;
    }

    public static Set<MorAna> guessRomanNumber(String word)
    {
        Set<MorAna> stemSet = new HashSet<MorAna>();

        // MCMLXXXIV
        if (word.matches("^M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$"))
        {
            stemSet.add(new MorAna(String.valueOf(romanToArabic(word)), "Mc-snr"));
        }

        // MCMLXXXIV.
        else if (word.matches("^M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})\\.$"))
        {
            stemSet.add(new MorAna(String.valueOf(romanToArabic(word.substring(0, word.length() - 1))) + ".", "Mo-snr"));
        }

        // MCMLXXXIV-MMIX
        else if (word.matches("^M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})-M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$"))
        {
            stemSet.add(new MorAna(String.valueOf(romanToArabic(word.substring(0, word.indexOf("-"))))
                        + "-"
                        + String.valueOf(romanToArabic(word.substring(word.indexOf("-") + 1, word.length()))), "Mc-snr"));
        }

        // MCMLXXXIV-MMIX.
        else if (word.matches("^M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})-M{0,4}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})\\.$"))
        {
            stemSet.add(new MorAna(String.valueOf(romanToArabic(word.substring(0, word.indexOf("-"))))
                        + "-"
                        + String.valueOf(romanToArabic(word.substring(word.indexOf("-") + 1, word.length()))) + ".", "Mo-snr"));
        }

        return stemSet;
    }

    public static void main(String[] args)
    {
        System.err.println(Settings.DEFAULT_NOUN.substring(0, 2));
        System.out.println(NumberGuesser.guess("386-osok"));
        System.out.println(NumberGuesser.guess("16-ai"));
        System.out.println(NumberGuesser.guess("5."));
        System.out.println(NumberGuesser.guess("20%"));
    }
}
EOF
mkdir -p szte/pos/mainpartofspeech && cat > szte/pos/mainpartofspeech/MainPartOfSpeech.java <<'EOF'
package szte.pos.mainpartofspeech;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainPartOfSpeech
{
    static Pattern pattern = Pattern.compile("(.*@)(.*)");

    public static void readTrain(String file, String out)
    {
        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
            Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                String[] splitted = line.split(" ");

                for (int i = 0; i < splitted.length; ++i)
                {
                    Matcher matcher = pattern.matcher(splitted[i]);
                    if (matcher.matches())
                    {
                        splitted[i] = matcher.group(1) + matcher.group(2).charAt(0);
                    }
                    else
                    {
                        System.err.println(splitted[i]);
                    }
                }

                for (String s : splitted)
                {
                    writer.write(s);
                    writer.write(" ");
                }
                writer.write('\n');
            }

            reader.close();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void main(String[] args)
    {
        readTrain("d:/szeged.pos.train", "d:/szeged.pos.train.main");
    }
}
EOF
mkdir -p szte/pos/morphology && cat > szte/pos/morphology/HungarianMorphology.java <<'EOF'
package szte.pos.morphology;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import edu.stanford.nlp.ling.TaggedWord;

import szte.magyarlanc.HunLemMor;
import szte.magyarlanc.MorAna;
import szte.magyarlanc.resource.ResourceHolder;

public class HungarianMorphology
{
    public static String[] getPossibleTags(String word, Set<String> possibleTags)
    {
        Set<MorAna> morAnas = HunLemMor.getMorphologicalAnalyses(word);
        Set<String> res = new HashSet<String>();

        for (MorAna morAna : morAnas)
        {
            String reduced = ResourceHolder.getMSDReducer().reduce(morAna.getMsd());
            if (possibleTags.contains(reduced))
            {
                res.add(reduced);
            }
        }

        if (res.size() == 0)
        {
            res.add("X");
        }

        return res.toArray(new String[res.size()]);
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
mkdir -p szte/pos/util && cat > szte/pos/util/CoNLLPredicate.java <<'EOF'
package szte.pos.util;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import szte.magyarlanc.Magyarlanc;

public class CoNLLPredicate
{
    private static String[] removeEmpty(String[] form)
    {
        List<String> cleaned = new ArrayList<String>();

        for (String f : form)
        {
            if (!f.equals("<empty>"))
            {
                cleaned.add(f);
            }
        }

        return cleaned.toArray(new String[cleaned.size()]);
    }

    private static void predicate(String[][][] sentences, String out)
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (String[][] sentence : sentences)
            {
                CoNLLSentence coNLLSentence = new CoNLLSentence(sentence);

                String[] form = removeEmpty(coNLLSentence.getForm());
                String[][] morph = Magyarlanc.morphParseSentence(form);

                for (int i = 0; i < form.length; ++i)
                {
                    writer.write(form[i]);
                    writer.write('\t');
                    writer.write(morph[i][1]);
                    writer.write('\t');
                    writer.write(morph[i][2]);
                    writer.write('\n');
                    // writer.write((form[i]);
                    // writer.write('\t');
                    // writer.write(morph[i][2]);
                    // writer.write('\n'));
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
        int szam = 9;

        predicate(CoNLLUtil.read("./data/newspaper/newspaper.conll2009_test" + szam), "./data/newspaper/newspaper.conll2009_test" + szam + ".pred2");
    }
}
EOF
mkdir -p szte/pos/util && cat > szte/pos/util/CoNLLSentence.java <<'EOF'
package szte.pos.util;

public class CoNLLSentence
{
    private String[][] tokens = null;

    public CoNLLSentence(String[][] sentence)
    {
        this.setTokens(sentence);
    }

    public String toString()
    {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < this.getTokens().length; ++i)
        {
            sb.append(tokens[i][0]);
            for (int j = 1; j < tokens[i].length; ++j)
            {
                sb.append('\t').append(tokens[i][j]);
            }
            sb.append('\n');
        }

        return sb.toString();
    }

    private String[] getColumn(int index)
    {
        String[] column = new String[this.getTokens().length];

        for (int i = 0; i < this.getTokens().length; ++i)
        {
            column[i] = this.getTokens()[i][index];
        }

        return column;
    }

    public String[] getRel()
    {
        return this.getColumn(5);
    }

    public String[] getForm()
    {
        return this.getColumn(1);
    }

    public void setTokens(String[][] tokens)
    {
        this.tokens = tokens;
    }

    public String[][] getTokens()
    {
        return tokens;
    }
}
EOF
mkdir -p szte/pos/util && cat > szte/pos/util/CoNLLToCorpus.java <<'EOF'
package szte.pos.util;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;

import szte.magyarlanc.MorAna;
import szte.pos.converter.CoNLLFeaturesToMSD;

public class CoNLLToCorpus
{
    private static CoNLLFeaturesToMSD coNLLFeaturesToMSD = null;

    private static Map<String, Set<MorAna>> getCorpus(String[][][] sentences)
    {
        if (coNLLFeaturesToMSD == null)
            coNLLFeaturesToMSD = new CoNLLFeaturesToMSD();

        Map<String, Set<MorAna>> corpus = new TreeMap<String, Set<MorAna>>();

        for (String[][] sentence : sentences)
            for (String[] token : sentence)
            {
                if (!corpus.containsKey(token[1]))
                {
                    corpus.put(token[1], new TreeSet<MorAna>());
                }

                corpus.get(token[1]).add(new MorAna(token[2], coNLLFeaturesToMSD.convert(token[4], token[6])));
            }

        return corpus;
    }

    private static void write(Map<String, Set<MorAna>> corpus, String file)
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (Entry<String, Set<MorAna>> entry : corpus.entrySet())
            {
                if (!entry.getKey().equals("<empty>"))
                {
                    writer.write(entry.getKey());
                    for (MorAna morAna : entry.getValue())
                    {
                        writer.write('\t');
                        writer.write(morAna.getLemma());
                        writer.write('\t');
                        writer.write(morAna.getMsd());
                    }
                    writer.write('\n');
                }
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void coNLLToCorpus(String corpus, String out)
    {
        write(getCorpus(CoNLLUtil.read(corpus)), out);
    }

    public static void main(String[] args)
    {
        for (int i = 0; i < 10; ++i)
            coNLLToCorpus("./data/newspaper/newspaper.conll2009_train" + i, "./data/newspaper/newspaper.conll2009_train" + i + ".corpus");
    }
}
EOF
mkdir -p szte/pos/util && cat > szte/pos/util/CoNLLToFreq.java <<'EOF'
package szte.pos.util;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import szte.pos.converter.CoNLLFeaturesToMSD;

public class CoNLLToFreq
{
    private static CoNLLFeaturesToMSD coNLLFeaturesToMSD = null;

    private static Map<String, Integer> getFreq(String[][][] sentences)
    {
        if (coNLLFeaturesToMSD == null)
            coNLLFeaturesToMSD = new CoNLLFeaturesToMSD();

        Map<String, Integer> freq = new TreeMap<String, Integer>();

        for (String[][] sentence : sentences)
            for (String[] token : sentence)
            {
                String msd = coNLLFeaturesToMSD.convert(token[4], token[6]);

                if (!freq.containsKey(msd))
                {
                    freq.put(msd, 0);
                }

                freq.put(msd, freq.get(msd) + 1);
            }

        return freq;
    }

    private static void write(Map<String, Integer> freq, String file)
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));

            for (Entry<String, Integer> entry : freq.entrySet())
            {
                writer.write(entry.getKey());
                writer.write('\t');
                writer.write(entry.getValue());
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

    public static void coNLLToFreq(String corpus, String out)
    {
        write(getFreq(CoNLLUtil.read(corpus)), out);
    }

    public static void main(String[] args)
    {
        for (int i = 0; i < 10; ++i)
            coNLLToFreq("./data/newspaper/newspaper.conll2009_train" + i, "./data/newspaper/newspaper.conll2009_train" + i + ".freq");
    }
}
EOF
mkdir -p szte/pos/util && cat > szte/pos/util/CoNLLUtil.java <<'EOF'
package szte.pos.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

public class CoNLLUtil
{
    static String readToString(String file)
    {
        StringBuilder sb = new StringBuilder();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                sb.append(line).append('\n');
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return sb.toString();
    }

    public static void merge(String out)
    {
        try
        {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (int i = 0; i < 10; ++i)
            {
                writer.write(readToString("./data/newspaper/newspaper.conll2009_test" + i + ".pred2"));
            }

            writer.flush();
            writer.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    static String[][][] read(String file)
    {
        List<String[][]> sentences = new ArrayList<String[][]>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            List<String[]> sentence = new ArrayList<String[]>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.trim().equals(""))
                {
                    sentences.add(sentence.toArray(new String[sentence.size()][]));
                    sentence = new ArrayList<String[]>();
                }
                else
                {
                    sentence.add(line.split("\t"));
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return sentences.toArray(new String[sentences.size()][][]);
    }

    public static void main(String[] args)
    {
        merge("./newspaper.pred2");
    }
}
EOF
mkdir -p szte/pos/util && cat > szte/pos/util/Objfx.java <<'EOF'
package szte.pos.util;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class Objfx
{
    static String[][][] read(String file)
    {
        List<String[][]> sentences = new ArrayList<String[][]>();

        try
        {
            BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            List<String[]> sentence = new ArrayList<String[]>();

            for (String line; (line = reader.readLine()) != null; )
            {
                if (line.trim().equals(""))
                {
                    sentences.add(sentence.toArray(new String[sentence.size()][]));
                    sentence = new ArrayList<String[]>();
                }
                else
                {
                    sentence.add(line.split("\t"));
                }
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        return sentences.toArray(new String[sentences.size()][][]);
    }

    public static void countSentences(String file)
    {
        System.err.println(read(file).length);
    }

    public static void countTokens(String file)
    {
        int tokens = 0;

        for (String[][] s : read(file))
        {
            tokens += s.length;
        }
        System.err.println(tokens);
    }

    public static void listEtalonFX(String file)
    {
        String[][][] document = read(file);

        for (String[][] sentence : document)
        {
            for (String[] token : sentence)
            {
                if (token[2].endsWith("FX"))
                    System.err.println(Arrays.toString(token) + "\t" + Arrays.toString(sentence[Integer.parseInt(token[1]) - 1]));
            }
        }
    }

    public static void listPredFX(String file)
    {
        String[][][] document = read(file);

        for (String[][] sentence : document)
        {
            for (String[] token : sentence)
            {
                if (token[4].endsWith("FX"))
                    System.err.println(Arrays.toString(token) + "\t" + Arrays.toString(sentence[Integer.parseInt(token[3]) - 1]));
            }
        }
    }

    public static void listFN(String file)
    {
        String[][][] document = read(file);

        for (String[][] sentence : document)
        {
            for (String[] token : sentence)
            {
                // az etalon fx
                if (token[2].endsWith("FX"))
                    // parent vagy rel nem egyezik
                    if (!token[1].equals(token[3]) || !token[2].equals(token[4]))
                    {
                        System.err.println(token[0] + " " + sentence[Integer.parseInt(token[1]) - 1][0]);
                    }
            }
        }
    }

    public static void listTP(String file)
    {
        String[][][] document = read(file);

        for (String[][] sentence : document)
        {
            for (String[] token : sentence)
            {
                // fx
                if (token[2].endsWith("FX"))
                    if ((token[1].equals(token[3])) && (token[2].equals(token[4])))
                        System.err.println(token[0] + " " + sentence[Integer.parseInt(token[1]) - 1][0]);
            }
        }
    }

    public static void listFP(String file)
    {
        String[][][] document = read(file);

        for (String[][] sentence : document)
        {
            for (String[] token : sentence)
            {
                // a predikalt FX
                if (token[4].endsWith("FX"))
                    // parent vagy rel nem egyezik
                    if (!token[1].equals(token[3]) || !token[2].equals(token[4]))
                    {
                        System.err.println(token[0] + " " + sentence[Integer.parseInt(token[3]) - 1][0]);
                    }
            }
        }
    }

    public static void etalonStat(String file)
    {
        String[][][] document = read(file);

        Map<String, Integer> stat = new TreeMap<String, Integer>();

        for (String[][] sentence : document)
        {
            for (String[] token : sentence)
            {
                // a etalon FX
                if (token[2].endsWith("FX"))
                {
                    if (!stat.containsKey(token[2]))
                    {
                        stat.put(token[2], 0);
                    }
                    stat.put(token[2], stat.get(token[2]) + 1);
                }
            }
        }

        for (Map.Entry<String, Integer> entry : stat.entrySet())
        {
            System.err.println(entry.getKey() + "\t" + entry.getValue());
        }
    }

    public static void predStat(String file)
    {
        String[][][] document = read(file);

        Map<String, Integer> stat = new TreeMap<String, Integer>();

        for (String[][] sentence : document)
        {
            for (String[] token : sentence)
            {
                if (token[4].endsWith("FX"))
                {
                    if (!stat.containsKey(token[4]))
                    {
                        stat.put(token[4], 0);
                    }
                    stat.put(token[4], stat.get(token[4]) + 1);
                }
            }
        }

        for (Map.Entry<String, Integer> entry : stat.entrySet())
        {
            System.err.println(entry.getKey() + "\t" + entry.getValue());
        }
    }

    public static void LAS(String file)
    {
        String[][][] document = read(file);

        int cntr = 0;
        int correct = 0;

        for (String[][] sentence : document)
        {
            for (String[] token : sentence)
            {
                if (token[1].equals(token[3]) && token[2].replace("FX", "").equals(token[4].replace("FX", "")))
                {
                    ++correct;
                }
                ++cntr;
            }
        }
        System.err.println((float) correct / cntr);
    }

    public static void ULA(String file)
    {
        String[][][] document = read(file);

        int cntr = 0;
        int correct = 0;

        for (String[][] sentence : document)
        {
            for (String[] token : sentence)
            {
                if (token[1].equals(token[3]))
                {
                    ++correct;
                }
                ++cntr;
            }
        }
        System.err.println((float) correct / cntr);
    }
}
EOF
mkdir -p szte/splitter && cat > szte/splitter/ForceSplit.java <<'EOF'
package szte.splitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ForceSplit
{
    private static final String[] HYPHENS = new String[] { "-", "­", "–", "—", "―", "−", "─" }; 

    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\S+@\\S+\\.[a-zA-Z]{2,4}");

    private static boolean isEmail(String token)
    {
        Matcher matcher = EMAIL_PATTERN.matcher(token);
        if (matcher.matches())
        {
            return true;
        }

        return false;
    }

    private static boolean isContainsHyphen(String token)
    {
        for (String h : HYPHENS)
        {
            if (token.contains(h))
            {
                return true;
            }
        }

        return false;
    }

    private static List<String> forceSplitSentence(List<String> sentence)
    {
        List<String> forced = new ArrayList<String>();

        for (int i = 0; i < sentence.size(); ++i)
        {
            if (!isEmail(sentence.get(i)) && isContainsHyphen(sentence.get(i)))
            {
                forced.addAll(split(sentence.get(i)));
            }
            else
            {
                forced.add(sentence.get(i));
            }
        }

        return forced;
    }

    private static List<String> split(String token)
    {
        for (String h : HYPHENS)
        {
            token = token.replaceAll(h, " " + h + " ");
        }

        return Arrays.asList(token.split(" "));
    }

    public static List<List<String>> forceSplit(List<List<String>> sentences)
    {
        List<List<String>> forced = new ArrayList<List<String>>();

        for (List<String> sentence : sentences)
        {
            forced.add(forceSplitSentence(sentence));
        }

        return forced;
    }
}
EOF
mkdir -p szte/splitter && cat > szte/splitter/HunSplitter.java <<'EOF'
package szte.splitter;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import edu.northwestern.at.morphadorner.corpuslinguistics.sentencesplitter.DefaultSentenceSplitter;
import edu.northwestern.at.morphadorner.corpuslinguistics.sentencesplitter.SentenceSplitter;
import edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer.DefaultWordTokenizer;
import edu.northwestern.at.morphadorner.corpuslinguistics.tokenizer.WordTokenizer;

import szte.magyarlanc.resource.ResourceHolder;

public class HunSplitter
{
    private boolean lineSentence = false;

    private WordTokenizer tokenizer = null;
    private SentenceSplitter splitter = null;
    private StringCleaner stringCleaner = null;

    private List<List<String>> splittedTemp = null;

    public HunSplitter()
    {
        this(false);
    }

    public HunSplitter(WordTokenizer wordTokenizer, SentenceSplitter sentenceSplitter, boolean lineSentence)
    {
        this.setLineSentence(lineSentence);
        this.setStringCleaner(new StringCleaner());

        this.setSplitter(sentenceSplitter);
        this.setTokenizer(wordTokenizer);
    }

    public HunSplitter(boolean lineSentence)
    {
        this.setLineSentence(lineSentence);
        this.setStringCleaner(new StringCleaner());

        this.setSplitter(new DefaultSentenceSplitter());
        this.setTokenizer(new DefaultWordTokenizer());
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

    /**
     * Insert the specified character, between the tokens.
     *
     * @param tokens
     *          splitted tokens
     * @param c
     *          character to instert
     * @return
     */
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

    /**
     * Retokenize the sentence via the specified HYPHENS.
     *
     * @param sentence
     *          tokenized sentence
     * @return retokenzied sentence
     */
    // private static List<String> splitSentenceHypens(List<String> sentence) {
    //
    // List<String> tokens = null;
    // tokens = new LinkedList<String>(sentence);
    //
    // String[] splitted = null;
    // for (int i = 0; i < tokens.size(); ++i) {
    // splitted = tokens.get(i).split(
    // String.valueOf(HunSplitterResources.DEFAULT_HYPHEN));
    //
    // if (splitted.length > 1) {
    // splitted = insertChars(splitted, HunSplitterResources.DEFAULT_HYPHEN);
    // tokens.remove((int) i);
    // for (int j = 0; j < splitted.length; ++j) {
    // tokens.add(i + j, splitted[j]);
    // }
    // }
    // }
    // return tokens;
    // }

    // private static List<List<String>> splitHyphens(List<List<String>>
    // sentences) {
    // for (int i = 0; i < sentences.size(); ++i) {
    // sentences.set(i, splitSentenceHypens(sentences.get(i)));
    // }
    //
    // return sentences;
    // }

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

        // return splitHyphens(splitted);
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
                    // System.out.println(sentences.get(i) + "i: " +i + " ss: " + sentences.size());
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
        for (char c : HunSplitterResources.QUOTES)
        {
            text = text.replaceAll(String.valueOf(c), String.valueOf(HunSplitterResources.DEFAULT_QUOTE));
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
        for (char c : HunSplitterResources.HYPHENS)
        {
            text = text.replaceAll(String.valueOf(c), String.valueOf(HunSplitterResources.DEFAULT_HYPHEN));
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

        for (char c : HunSplitterResources.FORCE_TOKEN_SEPARATORS)
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
mkdir -p szte/splitter && cat > szte/splitter/HunSplitterResources.java <<'EOF'
package szte.splitter;

public class HunSplitterResources
{
    // static array of the hyphen characters
    public static final char[] HYPHENS = new char[] { '-', '­', '–', '—', '―', '−', '─' };

    // default hyphen character
    public static final char DEFAULT_HYPHEN = '-';

    // static array of the quotes
    public static final char[] QUOTES = new char[] { '"', '\'', '`', '´', '‘', '’', '“', '”', '„' };

    // default quote character
    public static final char DEFAULT_QUOTE = '"';

    // static array of the force token separators
    public static final char[] FORCE_TOKEN_SEPARATORS = new char[] { ',', '.', ':' };
}
EOF
mkdir -p szte/splitter && cat > szte/splitter/StringCleaner.java <<'EOF'
package szte.splitter;

import java.util.Set;
import java.util.TreeSet;

public class StringCleaner
{
    private static Set<Integer> errorCharacters = loadErrorCharacters();

    private static Set<Integer> loadErrorCharacters()
    {
        Set<Integer> ecs = new TreeSet<Integer>();

        ecs.add(11);
        ecs.add(12);
        ecs.add(28);
        ecs.add(29);
        ecs.add(30);
        ecs.add(31);
        ecs.add(5760);
        ecs.add(6158);
        ecs.add(8192);
        ecs.add(8193);
        ecs.add(8194);
        ecs.add(8195);
        ecs.add(8196);
        ecs.add(8197);
        ecs.add(8198);
        ecs.add(8200);
        ecs.add(8201);
        ecs.add(8202);
        ecs.add(8203);
        ecs.add(8232);
        ecs.add(8233);
        ecs.add(8287);
        ecs.add(12288);
        ecs.add(65547);
        ecs.add(65564);
        ecs.add(65565);
        ecs.add(65566);
        ecs.add(65567);

        return ecs;
    }

    public StringCleaner()
    {
    }

    public String cleanString(String text)
    {
        StringBuilder sb = new StringBuilder(text);

        for (int i = 0; i < sb.length(); ++i)
        {
            if (errorCharacters.contains((int) sb.charAt(i)))
            {
                // System.err.println("unknown character: " + text.charAt(i) + " has been removed");
                sb.setCharAt(i, ' ');
            }

            if ((int) sb.charAt(i) == 733)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 768)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 769)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 771)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 803)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 900)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 1475)
            {
                sb.setCharAt(i, ':');
            }
            if ((int) sb.charAt(i) == 1523)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 1524)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 1614)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 1643)
            {
                sb.setCharAt(i, ',');
            }
            if ((int) sb.charAt(i) == 1648)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 1764)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 8211)
            {
                sb.setCharAt(i, '-');
            }
            if ((int) sb.charAt(i) == 8212)
            {
                sb.setCharAt(i, '-');
            }
            if ((int) sb.charAt(i) == 8216)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 8217)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 8218)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 8219)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 8220)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 8221)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 8243)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 8722)
            {
                sb.setCharAt(i, '-');
            }
            if ((int) sb.charAt(i) == 61448)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 61449)
            {
                sb.setCharAt(i, '\'');
            }
            if ((int) sb.charAt(i) == 61472)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 61474)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 61475)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 61476)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 61477)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 61480)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 61481)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 61482)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 61483)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 61484)
            {
                sb.setCharAt(i, '.');
            }
            if ((int) sb.charAt(i) == 61485)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 61486)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 61487)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 61488)
            {
                sb.setCharAt(i, '"');
            }
            if ((int) sb.charAt(i) == 65533)
            {
                sb.setCharAt(i, '-');
            }
        }

        return sb.toString();
    }
}
EOF
mkdir -p szte/train && cat > szte/train/MultiWordSplitter.java <<'EOF'
package szte.train;

import java.util.Arrays;

import szte.magyarlanc.Settings;
import szte.magyarlanc.resource.ResourceHolder;
import szte.magyarlanc.resource.Util;

public class MultiWordSplitter
{
    private static final String DEFAULT_ADJECTIVE_MSD = "Afp-sn";
    private static final String DEFAULT_NUMERAL_MSD = "Mc-snd";
    private static final String DEFAULT_NUMERAL_FRACTION_MSD = "Mf-snd";
    private static final String DEFAULT_NOUN_MSD = "Np-sn";

    public static String[][] splitMW(String wordForm, String lemma, String msd)
    {
        String[][] split = null;

        String[] wordForms = wordForm.split(" ");
        String[] lemmas = lemma.split(" ");

        if (lemmas.length != wordForms.length)
        {
            System.err.println("Different wordform and lemma length: " + wordForm + "\t" + lemma);
            split = new String[][] { { wordForm.replace(" ", "_"), lemma.replace(" ", "_"), msd } };
        }
        else
        {
            switch (msd.charAt(0))
            {
            case 'N':
                split = splitN(wordForms, lemmas, msd);
                break;
            case 'M':
                split = splitM(wordForms, lemmas, msd);
                break;
            case 'A':
                split = splitA(wordForms, lemmas, msd);
                break;
            default:
                System.err.println("Can't resolve split: " + wordForm + "\t" + lemma + "\t" + msd);
                split = new String[][] { { wordForm.replace(" ", "_"), lemma.replace(" ", "_"), msd } };
            }
        }

        return changeMsd(split);
    }

    public static String[][] changeMsd(String[][] tokens)
    {
        for (String[] token : tokens)
        {
            if (ResourceHolder.getPunctations().contains(token[1]))
            {
                token[2] = token[1];
            }
            else if (!token[1].equals("§") && Util.isPunctation(token[0]))
            {
                token[2] = "K";
            }
            else if (token[1].equalsIgnoreCase("és"))
            {
                token[2] = "Ccsw";
            }

            if (Settings.DEFAULT_NOUN.equals("Nn-sn"))
            {
                token[2] = token[2].replace("Nc-", "Nn-");
                token[2] = token[2].replace("Np-", "Nn-");
            }
        }

        return tokens;
    }

    public static String[][] splitN(String[] wordForms, String[] lemmas, String msd)
    {
        String[][] ret = new String[wordForms.length][3];

        for (int i = 0; i < wordForms.length; ++i)
        {
            ret[i][0] = wordForms[i];
            ret[i][1] = lemmas[i];

            if (i < wordForms.length - 1)
            {
                ret[i][2] = DEFAULT_NOUN_MSD;
            }
            else
            {
                ret[i][2] = msd;
            }
        }

        return ret;
    }

    public static String[][] splitA(String[] wordForms, String[] lemmas, String msd)
    {
        String[][] ret = new String[wordForms.length][3];

        for (int i = 0; i < wordForms.length; ++i)
        {
            ret[i][0] = wordForms[i];
            ret[i][1] = lemmas[i];

            if (i < wordForms.length - 1)
            {
                ret[i][2] = DEFAULT_ADJECTIVE_MSD;
            }
            else
            {
                ret[i][2] = msd;
            }
        }

        return ret;
    }

    public static String[][] splitM(String[] wordForms, String[] lemmas, String msd)
    {
        String[][] ret = new String[wordForms.length][3];

        for (int i = 0; i < wordForms.length; ++i)
        {
            ret[i][0] = wordForms[i];
            ret[i][1] = lemmas[i];

            if (i < wordForms.length - 1)
            {
                if (wordForms[i].contains(","))
                {
                    ret[i][2] = DEFAULT_NUMERAL_FRACTION_MSD;
                }
                else
                {
                    ret[i][2] = DEFAULT_NUMERAL_MSD;
                }
            }
            else
            {
                ret[i][2] = msd;
            }
        }

        return ret;
    }
}
EOF
mkdir -p szte/train && cat > szte/train/Train.java <<'EOF'
package szte.train;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import szte.magyarlanc.MorAna;
import szte.magyarlanc.Settings;
import szte.magyarlanc.resource.ResourceHolder;
import szte.magyarlanc.resource.Util;
import szte.pos.converter.MSDReducer;

public class Train
{
    private static final String[] CORPUSES = { "10elb", "10erv", "1984", "8oelb",
      "cwszt", "gazdtar", "hvg", "mh", "newsml", "np", "nv", "pfred", "szerzj",
      "utas", "win2000" };

    private static final double DIVISION = 0.8;

    private static final String WORDFORM_LEMMA_SEPARATOR = "\t";
    private static final String LEMMA_MSD_SEPARATOR = "\t";

    private static final String STANFORD_TRAIN_WORDFORM_MSD_SEPARATOR = "@";

    private static final String STANFORD_TRAIN_TOKEN_SEPARATOR = " ";

    private static final String CLOSED_TAGS = "! , - . : ; ? Cccp Cccw Ccsp Ccsw Cscp Cssp Cssw S T Z";

    public static List<Node> getNodes(Document document, String tagName, String type)
    {
        NodeList nodeList = document.getElementsByTagName(tagName);

        List<Node> nodes = new LinkedList<Node>();

        for (int i = 0; i < nodeList.getLength(); ++i)
        {
            Node node = nodeList.item(i);

            if (node.getAttributes().getNamedItem("type") != null)
            {
                if (node.getAttributes().getNamedItem("type").getTextContent().equals(type))
                {
                    nodes.add(node);
                }
            }
        }

        return nodes;
    }

    public static String getLemma(Node node)
    {
        return getNodes(getNodes(node, "msd").get(0), "lemma").get(0).getTextContent();
    }

    public static String getMsd(Node node)
    {
        String msd = getNodes(getNodes(node, "msd").get(0), "mscat").get(0).getTextContent();
        return msd.substring(1, msd.length() - 1);
    }

    public static String wToTrain(Node node)
    {
        String spelling = node.getChildNodes().item(0).getTextContent().trim();

        StringBuilder sb = new StringBuilder();

        NodeList nodes = ((Element) node).getElementsByTagName("ana");
        for (int i = 0; i < nodes.getLength(); ++i)
        {
            String lemma = getLemma(nodes.item(i));

            if (spelling.contains(" "))
            {
                return splitToTrainString(MultiWordSplitter.splitMW(spelling, getLemma(nodes.item(i)), getMsd(nodes.item(i))));
            }
            else
            {
                String msd = getMsd(nodes.item(i));

                if (ResourceHolder.getPunctations().contains(lemma))
                {
                    msd = lemma;
                }
                else if (!lemma.equals("§") && Util.isPunctation(lemma))
                {
                    msd = "K";
                }

                if (Settings.DEFAULT_NOUN.equals("Nn-sn"))
                {
                    msd = msd.replace("Nc-", "Nn-");
                    msd = msd.replace("Np-", "Nn-");
                }

                sb.append(spelling).append(WORDFORM_LEMMA_SEPARATOR).append(lemma).append(LEMMA_MSD_SEPARATOR).append(msd);
            }
        }

        return sb.toString();
    }

    public static String splitToTrainString(String[][] split)
    {
        StringBuilder sb = new StringBuilder();

        for (String[] s : split)
        {
            sb.append(s[0]);
            sb.append(WORDFORM_LEMMA_SEPARATOR);
            sb.append(s[1]);
            sb.append(LEMMA_MSD_SEPARATOR);
            sb.append(s[2]);
            sb.append('\n');
        }

        return sb.toString().trim();
    }

    public static String cToTrain(Node node)
    {
        StringBuilder sb = new StringBuilder();

        String c = node.getTextContent();

        sb.append(c);
        sb.append(WORDFORM_LEMMA_SEPARATOR);
        sb.append(c);
        sb.append(LEMMA_MSD_SEPARATOR);

        if (!ResourceHolder.getPunctations().contains(c))
        {
            sb.append("K");
        }
        else
        {
            sb.append(c);
        }

        return sb.toString();
    }

    public static String choiceToTrain(Node node)
    {
        for (Node correctedNode : getNodes(getNodes(node, new String[] { "corr", "reg" }).get(0), new String[] { "w", "c" }))
        {
            return nodeToTrain(correctedNode);
        }

        return null;
    }

    public static String nodeToTrain(Node node)
    {
        String nodeName = node.getNodeName();

        if (nodeName.equals("w"))
        {
            return wToTrain(node);
        }
        else if (nodeName.equals("c"))
        {
            return cToTrain(node);
        }
        else if (nodeName.equals("choice"))
        {
            return choiceToTrain(node);
        }

        return null;
    }

    private static String sentenceNodeToTrain(Node sentenceNode)
    {
        StringBuilder sb = new StringBuilder();

        for (Node node : getNodes(sentenceNode, new String[] { "w", "c", "choice" }))
        {
            String trainNode = nodeToTrain(node);

            if (trainNode != null)
            {
                sb.append(trainNode);
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    public static List<Node> getNodes(Node node, String... tagNames)
    {
        List<Node> nodes = new LinkedList<Node>();

        NodeList childNodes = ((Element) node).getChildNodes();

        for (int i = 0; i < childNodes.getLength(); ++i)
        {
            Node tempNode = childNodes.item(i);
            String tempNodeName = tempNode.getNodeName();

            for (String tagName : tagNames)
            {
                if (tempNodeName.equals(tagName))
                {
                    nodes.add((Element) tempNode);
                    break;
                }
            }
        }

        return nodes;
    }

    private static List<Node> readXml(String xml)
    {
        List<Node> divNodes = null;

        try
        {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(xml));

            if (xml.contains("10elb") || xml.contains("10erv") || xml.contains("8oelb"))
            {
                divNodes = getNodes(document, "div", "composition");
            }
            else if (xml.contains("1984"))
            {
                divNodes = new ArrayList<Node>();
                for (Node partDivNode : getNodes(document, "div", "part"))
                {
                    divNodes.addAll(getNodes(partDivNode, "div", "chapter"));
                }
            }
            else if (xml.contains("gazdtar.xml") || xml.contains("szerzj"))
            {
                divNodes = getNodes(document, "div", "section");
            }
            else if (xml.contains("cwszt") || xml.contains("hvg")
                  || xml.contains("mh") || xml.contains("newsml") || xml.contains("np")
                  || xml.contains("nv") || xml.contains("pfred")
                  || xml.contains("utas") || xml.contains("win2000"))
            {
                divNodes = getNodes(document, "div", "article");
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return divNodes;
    }

    private static void convert(String corpusPath, String trainFile, String testFile)
    {
        BufferedWriter trainWriter = null;
        BufferedWriter testWriter = null;

        try
        {
            trainWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(trainFile), "UTF-8"));
            testWriter = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(testFile), "UTF-8"));
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

        List<Node> divNodes = null;

        for (String corpus : CORPUSES)
        {
            StringBuilder xml = new StringBuilder(corpusPath);
            xml.append(corpus + ".xml");
            divNodes = readXml(xml.toString());
            int treshold = (int) (divNodes.size() * DIVISION);

            int sentenceCounter = 0;
            try
            {
                for (int i = 0; i < divNodes.size(); ++i)
                {
                    ++sentenceCounter;

                    for (Node paragraphNode : getNodes(divNodes.get(i), "p"))
                    {
                        for (Node sentencNode : getNodes(paragraphNode, "s"))
                        {
                            if (i <= treshold)
                            {
                                trainWriter.write(sentenceNodeToTrain(sentencNode) + '\n');
                            }
                            else
                            {
                                testWriter.write(sentenceNodeToTrain(sentencNode) + '\n');
                            }
                        }
                    }
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

            System.out.println(xml + "\t" + sentenceCounter);
        }

        try
        {
            trainWriter.close();
            testWriter.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    public static void stanfordTrain(String in, String out)
    {
        BufferedReader reader = null;
        BufferedWriter writer = null;

        Set<String> msdCodes = new TreeSet<String>();

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(in), "UTF-8"));
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            StringBuilder sb = new StringBuilder();

            for (String line; (line = reader.readLine()) != null; )
            {
                String[] split = line.split("\t");
                if (split.length == 3)
                {
                    sb.append(split[0]);
                    sb.append(STANFORD_TRAIN_WORDFORM_MSD_SEPARATOR);

                    String reducedMsd = ResourceHolder.getMSDReducer().reduce(split[2]);
                    sb.append(reducedMsd);
                    msdCodes.add(reducedMsd);

                    sb.append(STANFORD_TRAIN_TOKEN_SEPARATOR);
                }
                else
                {
                    writer.write(sb.toString().trim());
                    writer.write('\n');
                    sb = new StringBuilder();
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        for (String msdCode : msdCodes)
        {
            System.err.print("openClassTags ");

            if (!Arrays.asList(CLOSED_TAGS.split(" ")).contains(msdCode))
            {
                System.err.print(msdCode + " ");
            }
        }
    }

    public static void writeLex(Map<String, Set<MorAna>> lexicon, String out)
    {
        BufferedWriter writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Map.Entry<String, Set<MorAna>> entry : lexicon.entrySet())
            {
                writer.write(entry.getKey());
                for (MorAna morAna : entry.getValue())
                {
                    writer.write('\t');
                    writer.write(morAna.getLemma());
                    writer.write('\t');
                    writer.write(morAna.getMsd());
                }
                writer.write('\n');
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static void writeFreq(Map<String, Integer> frequencies, String out)
    {
        BufferedWriter writer = null;

        try
        {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), "UTF-8"));

            for (Map.Entry<String, Integer> entry : frequencies.entrySet())
            {
                writer.write(entry.getKey());
                writer.write('\t');
                writer.write(entry.getValue());
                writer.write('\n');
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
                writer.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }
    }

    public static Map<String, Set<MorAna>> getLex(String file)
    {
        Map<String, Set<MorAna>> lexicon = new TreeMap<String, Set<MorAna>>();

        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                String[] split = line.split(WORDFORM_LEMMA_SEPARATOR);

                if (split.length == 3)
                {
                    if (!lexicon.containsKey(split[0]))
                    {
                        lexicon.put(split[0], new TreeSet<MorAna>());
                    }
                    lexicon.get(split[0]).add(new MorAna(split[1], split[2]));
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

        return lexicon;
    }

    public static Map<String, Integer> getFreq(String file)
    {
        Map<String, Integer> frequencies = new TreeMap<String, Integer>();

        BufferedReader reader = null;

        try
        {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));

            for (String line; (line = reader.readLine()) != null; )
            {
                String[] split = line.split(WORDFORM_LEMMA_SEPARATOR);

                if (split.length == 3)
                {
                    if (!frequencies.containsKey(split[2]))
                    {
                        frequencies.put(split[2], 0);
                    }
                    frequencies.put(split[2], frequencies.get(split[2]) + 1);
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

        return frequencies;
    }

    public static void lex(String input, String output)
    {
        writeLex(getLex(input), output);
    }

    public static void freq(String input, String output)
    {
        writeFreq(getFreq(input), output);
    }

    public static void main(String[] args)
    {
        String corpusPath = "./data/Szeged_Korpusz_2.3/";
        String trainFile = "./data/23.train";
        String testFile = "./data/23.test";
        String lexFile = "./data/23.lex";
        String freqFile = "./data/23.freq";
        String stanfordTrainFile = "./data/stanford/23.stanford.train";

        convert(corpusPath, trainFile, testFile);
        stanfordTrain(trainFile, stanfordTrainFile);
        lex(trainFile, lexFile);
        freq(trainFile, freqFile);
    }
}
EOF
mkdir -p szte/train && cat > szte/train/XMLtoTXT.java <<'EOF'
package szte.train;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import szte.magyarlanc.MorAna;
import szte.magyarlanc.resource.ResourceHolder;
import szte.magyarlanc.resource.Util;

public class XMLtoTXT
{
    private static Writer writer = null;
    private static Writer corpusWriter = null;
    private static Writer freqsWriter = null;

    private static Map<String, Integer> freqs = null;
    private static Map<String, Set<MorAna>> corpus = null;

    public static List<String> splitSentenceNsamedEntites(List<String> sentence)
    {
        for (int i = 0; i < sentence.size(); ++i)
        {
            String[] splittedLine = sentence.get(i).split("\t");

            // nem kivanatos _ elejen/vegen
            if (splittedLine[1].length() > 1 && (splittedLine[1].startsWith("_") || splittedLine[1].endsWith("_")))
            {
                // System.out.println(splittedLine[1]);
            }

            if ((splittedLine[3].startsWith("Np")
                        || splittedLine[3].startsWith("X")
                        || splittedLine[3].startsWith("M")
                        || splittedLine[3].startsWith("Afp")
                        || splittedLine[3].startsWith("Nc"))
                    && splittedLine[1].contains("_")
                    && splittedLine[1].length() > 1)
            {
                String[] splittedNamedEntity = splittedLine[1].split("_");
                String[] splittedLemma = splittedLine[2].split("_");

                StringBuilder sb = new StringBuilder(splittedLine[0]);

                try
                {
                    sb.append('\t').append(splittedNamedEntity[0]);
                }
                catch (ArrayIndexOutOfBoundsException e)
                {
                    // System.out.println(sentence.get(i));
                }
                sb.append('\t').append(splittedLemma[0]);

                // System.out.println(splittedLemma[0]);

                // utolso elotti tokenek
                if (splittedLine[3].startsWith("X"))
                {
                    sb.append('\t').append("Np-sn");
                    sb.append('\t').append("N");
                }

                // N
                else if (splittedLine[3].startsWith("Np"))
                {
                    sb.append('\t').append("Np-sn");
                    sb.append('\t').append("N");
                }

                // M
                else if (splittedLine[3].startsWith("M"))
                {
                    if (splittedLine[1].contains(","))
                    {
                        if (!splittedNamedEntity[0].contains(","))
                        {
                            sb.append('\t').append("Mc-snd");
                        }
                        else
                        {
                            sb.append('\t').append("Mf-snd");
                        }
                    }
                    else
                    {
                        sb.append('\t').append("Mc-snd");
                    }
                    sb.append('\t').append("M");
                }

                // AFP
                else if (splittedLine[3].startsWith("Afp"))
                {
                    if (Character.isDigit(splittedLine[1].charAt(0)))
                    {
                        if (splittedLine[1].contains(","))
                        {
                            sb.append('\t').append("Mf-snd");
                        }
                        else
                        {
                            sb.append('\t').append("Mc-snd");
                        }
                        sb.append('\t').append("NUM");
                    }
                    else
                    {
                        sb.append('\t').append("Np-sn");
                        sb.append('\t').append("N");
                    }
                }
                else
                {
                    sb.append('\t').append("Np-sn");
                    sb.append('\t').append(splittedLine[4]);
                }

                sb.append('\t').append(splittedLine[5]);
                sb.append('\t').append(Integer.valueOf(splittedLine[0]) + 1);

                if (splittedLine[3].startsWith("M"))
                {
                    sb.append('\t').append("NUM");
                }
                else
                {
                    sb.append('\t').append("NE");
                }

                sb.append('\t').append(splittedLine[8]);
                sb.append('\t').append(splittedLine[9]);

                sentence.set(i, sb.toString());

                // sentence = renumberOrdinal(sentence, i + 1, splittedNamedEntity.length - 1);

                // sentence = renumberParent(sentence, i + 1, splittedNamedEntity.length - 1);

                int token = 1;

                for (int j = i + 1; j < i + splittedNamedEntity.length; ++j)
                {
                    sb = new StringBuilder(String.valueOf(j + 1));
                    sb.append('\t').append(splittedNamedEntity[token]);
                    try
                    {
                        sb.append('\t').append(splittedLemma[token]);
                    }
                    catch (Exception e)
                    {
                        // System.err.println(sentence);
                    }

                    if (splittedLine[3].startsWith("X"))
                    {
                        if (j == (i + splittedNamedEntity.length - 1))
                        {
                            sb.append('\t').append("X");
                            sb.append('\t').append("X");
                        }
                        else
                        {
                            sb.append('\t').append("Np-sn");
                            sb.append('\t').append("N");
                        }
                    }

                    else if (splittedLine[3].startsWith("Afp"))
                    {
                        System.err.println(Arrays.toString(splittedNamedEntity));
                        if (j == (i + splittedNamedEntity.length - 1))
                        {
                            if (splittedLine[0].equals("és"))
                            {
                                sb.append('\t').append("Ccsw");
                                sb.append('\t').append("Ccsw");
                            }
                            else
                            {
                                sb.append('\t').append(splittedLine[3]);
                                sb.append('\t').append(splittedLine[4]);
                            }
                        }
                        else
                        {
                            sb.append('\t').append("Np-sn");
                            sb.append('\t').append("N");
                        }
                    }

                    else if (splittedLine[3].startsWith("Np"))
                    {
                        System.err.println(Arrays.toString(splittedNamedEntity));
                        if (j == (i + splittedNamedEntity.length - 1))
                        {
                            if (splittedLine[0].equals("és"))
                            {
                                sb.append('\t').append("Ccsw");
                                sb.append('\t').append("Ccsw");
                            }
                            else
                            {
                                sb.append('\t').append(splittedLine[3]);
                                sb.append('\t').append(splittedLine[4]);
                            }
                        }
                        else
                        {
                            sb.append('\t').append("Np-sn");
                            sb.append('\t').append("N");
                        }
                    }
                    else
                    {
                        sb.append('\t').append(splittedLine[3]);
                        sb.append('\t').append(splittedLine[4]);
                    }

                    sb.append('\t').append(splittedLine[5]);

                    // utols token
                    if (j + 1 == i + splittedNamedEntity.length)
                    {
                        if (Integer.parseInt(splittedLine[6]) > j - splittedNamedEntity.length + 1)
                        {
                            sb.append('\t').append(Integer.valueOf(splittedLine[6]) + splittedNamedEntity.length - 1);
                        }
                        else
                        {
                            sb.append('\t').append(Integer.valueOf(splittedLine[6]));
                        }

                        sb.append('\t').append(splittedLine[7]);
                    }
                    else
                    {
                        sb.append('\t').append(j + 2);
                        sb.append('\t').append("NE");
                    }
                    sb.append('\t').append(splittedLine[8]);
                    sb.append('\t').append(splittedLine[9]);
                    ++token;
                    sentence.add(j, sb.toString());
                }
            }
        }

        return sentence;
    }

    public static void addTofreq(String msd)
    {
        if (!freqs.containsKey(msd))
        {
            freqs.put(msd, 0);
        }
        freqs.put(msd, (freqs.get(msd) + 1));
    }

    public static void addToCorpus(String spelling, MorAna morAna)
    {
        if (!corpus.containsKey(spelling))
        {
            corpus.put(spelling, new TreeSet<MorAna>());
        }
        corpus.get(spelling).add(morAna);
    }

    public static List<Node> getNodes(Document document, String tagName, String type)
    {
        List<Node> nodes = new LinkedList<Node>();

        NodeList nodeList = document.getElementsByTagName(tagName);

        for (int i = 0; i < nodeList.getLength(); ++i)
        {
            Node node = nodeList.item(i);
            if (node.getAttributes().getNamedItem("type").getTextContent().equals(type))
                nodes.add(node);
        }

        return nodes;
    }

    public static List<Node> getNodes(Node node, String... tagNames)
    {
        NodeList childNodes = ((Element) node).getChildNodes();

        List<Node> nodes = new LinkedList<Node>();

        for (int i = 0; i < childNodes.getLength(); ++i)
        {
            Node tempNode = childNodes.item(i);
            String tempNodeName = tempNode.getNodeName();

            for (String tagName : tagNames)
            {
                if (tempNodeName.equals(tagName))
                {
                    nodes.add((Element) tempNode);
                    break;
                }
            }
        }

        return nodes;
    }

    public static String getSpelling(Node node)
    {
        return node.getChildNodes().item(0).getTextContent().trim();
    }

    public static String getLemma(Node node)
    {
        return getNodes(getNodes(node, "msd").get(0), "lemma").get(0).getTextContent();
    }

    public static String getMsd(Node node, boolean reduce)
    {
        String msd = getNodes(getNodes(node, "msd").get(0), "mscat").get(0).getTextContent();

        msd = msd.substring(1, msd.length() - 1);

        if (reduce)
            msd = ResourceHolder.getMSDReducer().reduce(msd);

        return msd;
    }

    public static void printW(Node node, boolean reduce, boolean train)
        throws IOException
    {
        String spelling = node.getChildNodes().item(0).getTextContent().trim();

        writer.write(spelling.replace(" ", " "));

        NodeList nodes = ((Element) node).getElementsByTagName("ana");
        for (int i = 0; i < nodes.getLength(); ++i)
        {
            if (train)
            {
                writer.write("@");
                writer.write(getMsd(nodes.item(i), reduce));

                addTofreq(getMsd(nodes.item(i), false));
            }

            else
            {
                writer.write('\t');
                writer.write(getLemma(nodes.item(i)).replace("+", ""));
                writer.write('\t');
                writer.write(getMsd(nodes.item(i), reduce));
            }
        }

        nodes = ((Element) node).getElementsByTagName("anav");

        for (int i = 0; i < nodes.getLength(); ++i)
        {
            if (train)
            {
                addToCorpus(spelling, new MorAna(getLemma(nodes.item(i)), getMsd(nodes.item(i), false)));
            }

            else
            {
                writer.write('\t');
                writer.write(getLemma(nodes.item(i)));
                writer.write('\t');
                writer.write(getMsd(nodes.item(i), reduce));
            }
        }
    }

    public static void printC(Node node, boolean train)
        throws DOMException, IOException
    {
        String c = node.getTextContent();
        writer.write(c);

        if (!ResourceHolder.getPunctations().contains(c))
        {
            writer.write('\t');
            writer.write(node.getTextContent());
            writer.write('\t');
            writer.write("K");
            writer.write('\t');
            writer.write(node.getTextContent());
            writer.write('\t');
            writer.write("K");
        }
        else
        {
            writer.write('\t');
            writer.write(node.getTextContent());
            writer.write('\t');
            writer.write(node.getTextContent());
            writer.write('\t');
            writer.write(node.getTextContent());
            writer.write('\t');
            writer.write(node.getTextContent());
        }
    }

    public static void printChoice(Node node, boolean reduce, boolean train)
        throws IOException
    {
        try
        {
            for (Node correctedNode : getNodes( getNodes(node, new String[] { "corr", "reg" }).get(0), new String[] { "w", "c" }))
            {
                printNode(correctedNode, reduce, train);
            }
        }
        catch (IndexOutOfBoundsException e)
        {
            System.err.println(node.getTextContent());
        }
    }

    public static void printNode(Node node, boolean reduce, boolean train)
        throws IOException
    {
        String nodeName = node.getNodeName();
        if (nodeName.equals("w"))
        {
            printW(node, reduce, train);
        }
        else if (nodeName.equals("c"))
        {
            printC(node, train);
        }
        else if (nodeName.equals("choice"))
        {
            printChoice(node, reduce, train);
        }
    }

    public static void writeCorpus()
    {
        try
        {
            for (Map.Entry<String, Set<MorAna>> entry : corpus.entrySet())
            {
                corpusWriter.write(entry.getKey().replace(" ", "_"));
                for (MorAna morAna : entry.getValue())
                {
                    corpusWriter.write("\t"
                            + morAna.getLemma().replace("+", "").replace(" ", "_") + "\t"
                            + morAna.getMsd());
                }
                corpusWriter.write("\n");
            }
            corpusWriter.flush();
            corpusWriter.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void writeFreqs()
    {
        try
        {
            for (Map.Entry<String, Integer> entry : freqs.entrySet())
            {
                freqsWriter.write(entry.getKey() + "\t" + entry.getValue() + "\n");
            }
            freqsWriter.flush();
            freqsWriter.close();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public static void printPrefix(Node... nodes)
        throws DOMException, IOException
    {
        for (Node node : nodes)
        {
            writer.write(node.getAttributes().getNamedItem("id").getTextContent());
            writer.write('\t');
        }
    }

    public static void write(String XML, String txt, boolean reduce, boolean train)
    {
        if (train)
        {
            freqs = new TreeMap<String, Integer>();
            corpus = new TreeMap<String, Set<MorAna>>();
        }

        if (writer == null)
            try
            {
                writer = new BufferedWriter(new FileWriter(txt));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }

        try
        {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(XML));

            // for (Node partDivNode : getNodes(document, "div", "part")) {
            // for (Node chapterDivNode : getNodes(partDivNode, "div", "chapter")) {
            // for (Node divNode : getNodes(document, "div", "composition")) {
            for (Node divNode : getNodes(document, "div", "article"))
            {
                for (Node pNode : getNodes(divNode, "p"))
                {
                    for (Node sNode : getNodes(pNode, "s"))
                    {
                        for (Node node : getNodes(sNode, new String[] { "w", "c", "choice" }))
                        {
                            try
                            {
                                if (!train)
                                    printPrefix(divNode, pNode, sNode);
                                printNode(node, reduce, train);
                                if (!train)
                                {
                                    writer.write('\n');
                                }
                                else
                                {
                                    writer.write(" ");
                                }
                            }
                            catch (IOException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        writer.write('\n');
                    }
                }
            }

            writer.flush();
            writer.close();
            writer = null;
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        if (train)
        {
            try
            {
                freqsWriter = new BufferedWriter(new FileWriter(txt.replace(".txt", "_freqs.txt")));
                corpusWriter = new BufferedWriter(new FileWriter(txt.replace(".txt", "_corpus.txt")));
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
            writeCorpus();
            writeFreqs();
        }
    }

    public static String[][] read(String file)
    {
        List<String[]> sentences = new ArrayList<String[]>();

        List<String> sentence = new ArrayList<String>();

        for (String line : Util.readFileToString(file).split("\n"))
        {
            if (line.trim().equals(""))
            {
                sentences.add(sentence.toArray(new String[sentence.size()]));
                sentence = new ArrayList<String>();
            }
            else
            {
                sentence.add(line);
            }
        }

        return sentences.toArray(new String[sentences.size()][]);
    }

    public static void convertToTrain(String file)
    {
    }

    public static void main(String args[])
    {
        String corpus = "./data/Szeged_Korpusz_2.3/newsml.xml";

        write("./data/Szeged_Korpusz_2.3/newsml.xml", "./data/Szeged_Korpusz_2.3/newsml.txt", false, false);

        // String c = Util.readFileToString("./data/szk2.5/txt/newsml_1.txt");
        // Writer writer = null;
        // try {
        // writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("./data/szk2.5/txt/newsml-split.txt"), "UTF-8"));
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        // String[] split = null;
        // for (String line : c.split("\n")) {
        // if (line.length() > 0) {
        // // System.err.println(line);
        // split = splitMW(line);
        // if (split != null) {
        // for (String s : split) {
        // try {
        // writer.write(s + "\n");
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        // }
        // } else {
        // try {
        // writer.write(line + "\n");
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        // }
        // } else {
        // try {
        // writer.write('\n');
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        // }
        // }
        //
        // try {
        // writer.close();
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        //
        // String file = null;
        // file = "./data/szk2.5/txt/newsml-split.txt";
        // String[][] sentences = read(file);
        //
        // int treshold = (int) (sentences.length * 0.8);
        //
        // System.err.println(treshold);
        //
        // Set<String> reducedSet = new TreeSet<String>();
        //
        // int cntr = 0;
        //
        // Writer writer2 = null;
        // if (writer2 == null)
        // try {
        // writer2 = new BufferedWriter(new OutputStreamWriter(new FileOutputStream("./data/szk2.5/txt/newsml-train-80.txt"), "UTF-8"));
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        //
        // for (String[] sentence : sentences) {
        // if (++cntr < treshold) {
        // for (String token : sentence) {
        // reducedSet.add(ResourceHolder.getMSDReducer().reduce(
        // token.split("\t")[MSD_INDEX]));
        //
        // try {
        // writer2.write(token.split("\t")[WORD_FORM_INDEX]
        // + "@"
        // + ResourceHolder.getMSDReducer().reduce(
        // token.split("\t")[MSD_INDEX]) + " ");
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        //
        // // System.err.print();
        // }
        // try {
        // writer2.write("\n");
        // } catch (IOException e) {
        // e.printStackTrace();
        // }
        // }
        // }
        //
        // try {
        // writer2.close();
        // } catch (IOException e) {
        // e.printStackTrace();
        // }

        // for (String s : reducedSet)
        // System.err.println(s);
        // System.err.println(reducedSet);
        // System.out.println(freqs);
        // System.out.println(corpus);

        // Document document = null;
        //
        // try {
        // document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        // .parse(new File("./data/szk2.5/xml/newsml_1.xml"));
        // } catch (SAXException e) {
        // e.printStackTrace();
        // } catch (IOException e) {
        // e.printStackTrace();
        // } catch (ParserConfigurationException e) {
        // e.printStackTrace();
        // }
    }
}
EOF
mkdir -p rfsa && cat > rfsa/Dumper.java <<'EOF'
package rfsa;

import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Map;

public class Dumper extends Thread
{
    protected static Runtime rt = Runtime.getRuntime();

    protected boolean stopped;

    public Dumper()
    {
        setDaemon(true);
    }

    public static void gc()
    {
        rt.gc();
        rt.runFinalization();
    }

    public void stopp()
    {
        stopped = true;
        interrupt();
    }

    public void run()
    {
        while (true)
        {
            try
            {
                LineNumberReader reader = new LineNumberReader(new InputStreamReader(System.in));

                while (!stopped && (reader.readLine()) != null)
                {
                    print();
                    gc();
                    printMem();
                }

                if (stopped)
                {
                    System.out.println(getClass().getSimpleName() + " stopped");
                    return;
                }
            }
            catch (Throwable t)
            {
                System.err.println("Dumper terminates:");
                t.printStackTrace();
                return;
            }
        }
    }

    public static String stackDump()
    {
        StringBuilder sb = new StringBuilder();
        Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
        for (Map.Entry<Thread, StackTraceElement[]> entry : map.entrySet())
        {
            Thread thread = entry.getKey();
            StackTraceElement[] dump = entry.getValue();

            System.out.println(thread);
            for (StackTraceElement trace : dump)
            {
                sb.append(" ").append(trace).append('\n');
            }
        }

        return sb.toString();
    }

    public static String memDump()
    {
        return memDump("");
    }

    public static String memDump(String head)
    {
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memBean.getNonHeapMemoryUsage();
        return head + "heap: " + heapUsage + "\n" + head + "non heap : " + nonHeapUsage;
    }

    public static void print()
    {
        System.out.print(stackDump());
        printMem();
    }

    public static void printMem()
    {
        System.out.println(memDump());
    }
}
EOF
mkdir -p rfsa && cat > rfsa/Pair.java <<'EOF'
package rfsa;

import java.io.Serializable;

@SuppressWarnings("serial")
public class Pair<A, B> implements Serializable
{
    protected A a;
    protected B b;

    // for deserialize extensions
    protected Pair()
    {
    }

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

    @SuppressWarnings("unchecked")
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
EOF
mkdir -p rfsa && cat > rfsa/RFSAAnalyser.java <<'EOF'
package rfsa;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Collection;

public class RFSAAnalyser
{
    public void online(RFSA rfsa)
        throws Exception
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        System.out.println(">");
        for (String line; (line = reader.readLine()) != null; )
        {
            Collection<String> a = rfsa.analyse(line);
            System.out.println(a.size() + ": ");
            for (String s : a)
            {
                System.out.println("  " + s);
            }
            System.out.println(">");
        }
    }
}
EOF
mkdir -p rfsa && cat > rfsa/RFSA.java <<'EOF'
package rfsa;

import java.io.BufferedReader;
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
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class RFSA
{
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
    public Collection<String> analyse(String s)
    {
        char[] ac = s.toLowerCase().toCharArray();
        return analyse(ac);
    }

    public Collection<String> analyse(char[] ac)
    {
        Collection<String> analyses = new ArrayList<String>();
        symbolhistory = new String[ac.length + 1];
        analyse(startingState, ac, 0, "", analyses);
        return analyses;
    }

    // binary search
    public void analyse(int q, char[] ac, int pos, String symbol, Collection<String> analyses)
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
    public void analyse1(int q, char[] ac, int pos, String symbol, Collection<String> analyses)
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
        Collection<Integer> valid = new HashSet<Integer>();
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
}
EOF
mkdir -p rfsa && cat > rfsa/StateIterator.java <<'EOF'
package rfsa;

import java.util.Iterator;
import java.util.NoSuchElementException;

public class StateIterator implements Iterable<Integer>, Iterator<Integer>
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
EOF
