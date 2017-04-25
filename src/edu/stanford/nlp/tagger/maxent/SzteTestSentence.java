package edu.stanford.nlp.tagger.maxent;

//import clojure.java.api.Clojure;
import clojure.lang.IFn;

//import magyarlanc.Morphology;

public class SzteTestSentence extends TestSentence
{
    protected IFn getPossibleTags/* = Clojure.var("magyarlanc.morphology", "getPossibleTags")*/;

    public SzteTestSentence(MaxentTagger tagger, IFn fn)
    {
        super(tagger);

        getPossibleTags = fn;
    }

    protected String[] stringTagsAt(int pos)
    {
        int left = this.leftWindow();

        if (pos < left || this.size + left <= pos)
        {
            return new String[] { this.naTag };
        }

        String word = this.sent.get(pos - left);

        String[] tags;

        if (this.maxentTagger.dict.isUnknown(word))
        {
            //tags = Morphology.getPossibleTags(word, this.maxentTagger.tags.getOpenTags());
            tags = (String[])getPossibleTags.invoke(word, this.maxentTagger.tags.getOpenTags());
        }
        else
        {
            tags = this.maxentTagger.dict.getTags(word);
        }

        return this.maxentTagger.tags.deterministicallyExpandTags(tags);
    }
}
