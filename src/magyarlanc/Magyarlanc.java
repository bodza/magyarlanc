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
