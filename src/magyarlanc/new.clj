(ns magyarlanc.new
  #_(:gen-class))

(deftype MorAna [lemma msd self]
    Object
        (equals [_ that]    (.equals self (.self that)))
        (hashCode [_]       (.hashCode self))
        (toString [_]       self)
    Comparable
        (compareTo [_ that] (.compareTo self (.self that))))

(defn morAna
    ([tuple] #_(assert-args (== (count tuple) 2) "[lemma msd]") (apply morAna tuple))
    ([lemma msd] (MorAna. lemma msd (str lemma "@" msd))))
