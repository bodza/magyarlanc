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
