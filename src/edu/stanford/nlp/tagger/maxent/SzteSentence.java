package edu.stanford.nlp.tagger.maxent;

import clojure.lang.IFn;

public class SzteSentence extends TestSentence
{
    protected IFn getPossibleTags;

    public SzteSentence(MaxentTagger tagger, IFn fn)
    {
        super(tagger);

        getPossibleTags = fn;
    }

    protected String[] stringTagsAt(int pos)
    {
        int left = this.leftWindow();

        if (pos < left || this.size + left <= pos)
            return new String[] { this.naTag };

        String word = this.sent.get(pos - left);

        String[] tags = this.maxentTagger.dict.getTags(word);

        if (tags == null)
            tags = (String[])getPossibleTags.invoke(word, this.maxentTagger.tags.getOpenTags());

        return this.maxentTagger.tags.deterministicallyExpandTags(tags);
    }
}
