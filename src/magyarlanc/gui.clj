(ns magyarlanc.gui
    (:import [java.awt BorderLayout Dimension Insets Toolkit]
             [java.awt.event ActionListener]
             [java.awt.image BufferedImage]
             [javax.swing BoxLayout ImageIcon JButton JFrame JLabel JPanel JTextArea JTextField]
             [javax.swing.border EmptyBorder])
    (:import [com.googlecode.whatswrong SingleSentenceRenderer]
             [com.googlecode.whatswrong.io CoNLL2009])
    (:import [magyarlanc Dependency HunSplitter])
    (:gen-class))

(defn- conll [lines]
    (map #(let [[a b c _ e _ g h] % _ "_"] (list a b c e _ _ _ _ g _ h _ _ _)) lines))

(defn- whats-wrong [lines]
    (let [renderer (SingleSentenceRenderer.)
          instance (.create (CoNLL2009.) (conll lines))]
        (let [image (BufferedImage. 1 1 BufferedImage/TYPE_4BYTE_ABGR)
              dim (.render renderer instance (.createGraphics image))

              image (BufferedImage. (+ (.getWidth dim) 5) (.getHeight dim) BufferedImage/TYPE_4BYTE_ABGR)
              dim (.render renderer instance (.createGraphics image))]
            image)))

(defn- pretty [lines]
    (apply str (mapcat conj (map #(vec (interpose \tab %)) lines) (repeat \newline))))

(defn- centered [component]
    (let [v (.. Toolkit getDefaultToolkit getScreenSize)
          w (.getPreferredSize component)
          x (/ (- (.getWidth v) (.getWidth w)) 2)
          y (/ (- (.getHeight v) (.getHeight w)) 2)]
        (doto component (.setLocation x y))))

(defn init [sentence]
    (let [frame  (JFrame. "magyarlanc 2.0")
          panel  (JPanel.)
          input  (JTextField. sentence)
          button (JButton. "OK")
          label  (JLabel.)
          output (JTextArea.)]

        (let [v (.. Toolkit getDefaultToolkit getScreenSize)]
            (doto frame (.setDefaultCloseOperation JFrame/DISPOSE_ON_CLOSE) (.setLayout (BorderLayout.))
                        (.setPreferredSize (Dimension. (- (.getWidth v) 150) (- (.getHeight v) 150))) (.setResizable false)))

        (doto panel (.setBorder (EmptyBorder. 15 15 15 15)) (.setLayout (BoxLayout. panel BoxLayout/X_AXIS)))
        (doto label (.setHorizontalAlignment JLabel/CENTER) (.setVisible false))
        (doto output (.setMargin (Insets. 10 10 10 10)) (.setVisible false))

        (doto frame (.add (doto panel (.add input) (.add button)) "North") (.add label "Center") (.add output "South"))

        (.addActionListener button (reify ActionListener (actionPerformed [_ actionEvent]
            (let [sentence (.getText input)]
                (if-not (empty? sentence)
                    (let [lines (Dependency/depParseSentence (first (HunSplitter/splitToArray sentence)))]

                        (doto label (.setIcon (ImageIcon. (whats-wrong lines))) (.setVisible true))
                        (doto output (.setText (pretty lines)) (.setVisible true))

                        (doto (centered frame) .pack (.setVisible true))))))))

        (doto (centered frame) .pack (.setVisible true))))

(defn -main []
    (init "Nehéz lesz megszokni a sok üres épületet, de a kínai áruházak hamar pezsgővé változtathatják a szellemházakat."))
