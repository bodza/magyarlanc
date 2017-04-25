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
