(ns magyarlanc.stanford
  #_(:import [edu.stanford.nlp.tagger.maxent SzteSentence])
    (:gen-class :main false))

(def ^:private newSzteSentence'
    (delay #(clojure.lang.Reflector/invokeConstructor (import 'edu.stanford.nlp.tagger.maxent.SzteSentence) (into-array [%]))))
(def ^:private getPossibleTags'
    (delay @(resolve 'magyarlanc.morphology/getPossibleTags)))

(gen-class :prefix "t--" :name edu.stanford.nlp.tagger.maxent.SzteTagger :extends edu.stanford.nlp.tagger.maxent.MaxentTagger
    :exposes {dict {:get getDict}})

(gen-class :prefix "s--" :name edu.stanford.nlp.tagger.maxent.SzteSentence :extends edu.stanford.nlp.tagger.maxent.TestSentence
    :exposes {maxentTagger {:get getTagger} sent {:get getSent} size {:get getSize}})

(defn- t--apply [_ in]
    (.tagSentence (@newSzteSentence' _) in false))

(defn- s--stringTagsAt [_ pos]
    (let [left (.leftWindow _)]
        (if (or (< pos left) (<= (+ left (.getSize _)) pos))
            (into-array ["NA"])
            (let [tagger (.getTagger _) dict (.getDict tagger) ttags (.getTags tagger)
                  word (.get (.getSent _) (- pos left))
                  tags (if (.isUnknown dict word) (@getPossibleTags' word (.getOpenTags ttags)) (.getTags dict word))]
                (.deterministicallyExpandTags ttags tags)))))
