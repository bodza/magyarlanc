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
