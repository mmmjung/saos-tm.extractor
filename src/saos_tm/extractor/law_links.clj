(ns saos-tm.extractor.law-links
  (:require
    [ saos-tm.extractor.common :as common ]
    [ clojure.string :as str ]
    [ langlab.core.parsers :refer [ lg-split-tokens-bi ] ]
    [ taoensso.timbre.profiling :as profiling ]
    )
  (:import java.io.File)
  (:gen-class))

(defn split-to-tokens [s]
  (lg-split-tokens-bi "pl" s))

(defn find-first [f coll]
  (first (filter f coll)))

(defn indices [pred coll]
   (keep-indexed #(when (pred %2) %1) coll))

(defn in? [seq elm]
  (some #(= elm %) seq))

(def coords-tokens
  ["." "," "Art" "art" "ust" "par" "§" "pkt" "zd" "i"
  "oraz" "lub" "z" "-" "a" "także" "lit"])

(defn not-coords-nmb? [s]
  (let [
          result (re-matches #"((\d+)(-\d+)?[a-z]*)|([a-u])" s)
          ]
    (if (= result nil) true false))) 

(defn not-coord-token? [token]
  (let [
          result (in? coords-tokens token)
          ]
  (if (= result nil) true false)))

(defn get-coords-tokens [first-token-index tokens]
  (first
    (indices
      #(and (not-coord-token? %) (not-coords-nmb? %))
      (drop first-token-index tokens))))

(defn find-coords-ranges [first-token-index tokens]
  (let [
          first-non-coord-token-index
            (get-coords-tokens first-token-index tokens)
        ]
  [first-token-index
  (if (nil? first-non-coord-token-index)
    (inc (count tokens))
    (+ first-token-index first-non-coord-token-index))]))

(defn get-range [coll from to]
  (take (- to from) (drop from coll)))

(defn get-range* [coll fromto]
  (get-range coll (first fromto) (second fromto)))

(defn w-zwiazku-z? [tokens]
  (or
    (and
      (= 3 (count tokens))
      (= "w" (nth tokens 0))
      (= "związku" (nth tokens 1))
      (= "z" (nth tokens 2)))
    (and
      (= 4 (count tokens))
      (= "w" (nth tokens 0))
      (= "zw" (nth tokens 1))
      (= "." (nth tokens 2))
      (= "z" (nth tokens 3)))))

(defn handle-w-zwiazku-z [tokens-and-coords]
  (profiling/p :zzwz
  (for [
          i (range 0 (count tokens-and-coords))
        ]
    (if (map? (nth tokens-and-coords i))  
      (nth tokens-and-coords i)
      (if (w-zwiazku-z? (nth tokens-and-coords i))
        (nth tokens-and-coords (+ i 1))
        (nth tokens-and-coords i))))))

(def dictionary-for-acts
  [
    [#"(?i)^Konstytucji" {:nr "78" :pos "483", :year "1997"}]
    [#"(?i)^k\.?c" {:nr "16" :pos "93", :year "1964"}]
    [#"(?i)^k\.?h" {:nr "57" :pos "502", :year "1934"}]
    [#"(?i)^k\.?k\.?s" {:nr "83" :pos "930", :year "1999"}]
    [#"(?i)^k\.?k\.?w" {:nr "90" :pos "557", :year "1997"}]
    [#"(?i)^k\.?k" {:nr "88" :pos "553", :year "1997"}]
    [#"(?i)^k\.?m" {:nr "138" :pos "1545", :year "2001"}]
    [#"(?i)^k\.?p\.?a" {:nr "30" :pos "168", :year "1960"}]
    [#"(?i)^k\.?p\.?c" {:nr "43" :pos "296", :year "1964"}]
    [#"(?i)^k\.?p\.?k" {:nr "89" :pos "555", :year "1997"}]
    [#"(?i)^k\.?p\.?w" {:nr "106" :pos "1148", :year ""}]
    [#"(?i)^k\.?p" {:nr "24" :pos "141", :year "1974"}]
    [#"(?i)^k\.?r\.?o" {:nr "9" :pos "59", :year "2001"}]
    [#"(?i)^k\.?s\.?h" {:nr "94" :pos "1037", :year "2000"}]
    [#"(?i)^k\.?w" {:nr "12" :pos "114", :year "1971"}]
    [#"(?i)^k\.?z" {:nr "82" :pos "598", :year "1933"}]
    [#"(?i)^u\.?s\.?p" {:nr "98" :pos "1070", :year "2001"}]
    [#"(?i)^ustawy o TK" {:nr "102" :pos "643", :year "1997"}]
    [#"(?i)^ustawy o Trybunale Konstytucyjnym" {:nr "102" :pos "643", :year "1997"}]
    [#"(?i)^ustawy o komornikach" {:nr "133" :pos "882", :year "1997"}]
    [#"(?i)^ustawy o ochronie konkurencji" {:nr "50" :pos "331", :year "2007"}]
    [#"(?i)^prawa o adwokat" {:nr "16" :pos "124", :year "1982"}]
    [#"(?i)^pzp" {:nr "19" :pos "177", :year "2004"}]
    [#"(?i)^ustawy pzp" {:nr "19" :pos "177", :year "2004"}]
    [#"(?i)^ustawy prawo zamówień publicznych" {:nr "19" :pos "177", :year "2004"}]
    [#"(?i)^prawa zamówień publicznych" {:nr "19" :pos "177", :year "2004"}]
    ])

(defn replace-several [content & replacements]
  (let [replacement-list (partition 2 replacements)]
    (reduce #(apply str/replace %1 %2) content replacement-list)))

(defn tokens-to-string [tokens]
  (let [ 
          txt (str/join " " tokens)
          without-unnecessary-spaces
            (replace-several txt
              #" \." "."
              #" ," ","
              #" / " "/"
              #"\( " "("
              #" \)" ")"
              #" ;" ";")
    ]
    without-unnecessary-spaces))

(def not-nil? (complement nil?))
(def not-map? (complement map?))

(defn re-pos [re s]
  (loop [m (re-matcher re s)]
   (if (.find m)
    (.start m))))

(defn min-index [coll]
  (.indexOf coll
    (apply min coll)))

(defn extract-dictionary-case [tokens dictionary]
  (profiling/p :dict
  (let [
          txt (tokens-to-string tokens)
          matched-indices
            (indices
              #(not-nil? (re-find % txt))
              (map #(first %) dictionary))
          positions
          (if-not (= 1 (count matched-indices))
            (map
              #(re-pos (first (nth dictionary %)) txt)
              matched-indices))
          min-i
          (if-not (= 1 (count matched-indices))
            (if-not (empty? positions)
              (min-index positions)))
          first-index
          (if-not (nil? min-i)
            (nth matched-indices min-i)
            (first matched-indices))
          dictionary-record
          (if (not-nil? first-index)
            (second
              (nth dictionary first-index))
            nil)
          ]
  (if (nil? dictionary-record)
    tokens
    dictionary-record))))

(defn extract-nr-pos-case [tokens dictionary]
  (profiling/p :nr-pos
  (let [
          year (common/get-year-of-law-act (tokens-to-string tokens))
          nr-indices (indices #(= % "Nr") tokens)
          nr-index
            (if (nil? nr-indices)
              nil
              (first nr-indices))
          pos-indices (indices #(= % "poz") tokens)
          pos-index
            (if (nil? pos-indices)
              nil
              (first pos-indices))
        ]
  (if (or (nil? nr-index) (nil? pos-index))
    (extract-dictionary-case tokens dictionary)
    (zipmap
      [:year :nr :pos]
      [year (nth tokens (+ nr-index 1)) (nth tokens (+ pos-index 2))] )))))

(defn coord-to-text [token]
  (if (or (= "." token) (= "-" token))
    token
    (str " " token)))

(defn build-coords-text [tokens-range tokens]
  (str/replace
    (str/join ""
      (map
        coord-to-text
        (get-range* tokens tokens-range)))
    "- " "-"))

(defn get-interfering-art-coords-ranges [tokens]
  (map #(find-coords-ranges % tokens) 
    (indices 
      #(or (= % "art") (= % "Art") (= % "§")) 
      tokens)))

(defn get-inter-coords-ranges [tokens]
  (let [
          interfering-art-coords-ranges (get-interfering-art-coords-ranges tokens)
          ]
  (filter
    #(< (first %) (second %))
    (partition 2
      (concat
        (drop 1
          (flatten interfering-art-coords-ranges))
        [(count tokens)])))))

(defn get-correct-art-coords-ranges [tokens]
  (let [
          interfering-art-coords-ranges (get-interfering-art-coords-ranges tokens)
          inter-coords-ranges (get-inter-coords-ranges tokens)
          ]
  (partition 2
    (concat
    [(first (first interfering-art-coords-ranges))]
    (flatten inter-coords-ranges)))))

(defn get-majority-act-coords-for-art-coords [art-coords-record links]
  (let [
        sorted
          (sort-by val >
            (frequencies
              (map
                #(:act %)
                (filter
                  #(= art-coords-record (:art %))
                  links))))
          ]
  [art-coords-record
  (first
    (find-first
      #(map? (first %))
      sorted))]))

(defn change-act-coords-to-majorities [majority-vote-act-coord links]
  (let [
          art-coord (first majority-vote-act-coord)
          act-coord (second majority-vote-act-coord)
          records-for-art-coord
            (filter
              #(= art-coord (:art %))
              links)]
  (if (nil? act-coord)
    records-for-art-coord
    (zipmap [:art :act] [art-coord act-coord]))))

(defn extract-signature [s]
  (-> (str/replace s "Sygn." "")
    (str/replace "akt" "")
    (str/replace "(" "")
    (str/replace ")" "")
    (str/replace "*" "")
    (str/trim)))

(defn get-line-with-signature [s]
  (let [
          lines (str/split s #"\n")
          lines-with-sygn-text (filter #(.startsWith % "Sygn.") lines)
          index-of-first-line-ending-with-date
            (first
              (indices
                #(.endsWith % " r.")
                lines))
    ]
  (if (empty? lines-with-sygn-text)
    (nth lines (+ index-of-first-line-ending-with-date 1))
    (first lines-with-sygn-text))))

(def art-coords-names [:art :par :ust :pkt :zd :lit])

(defn create-map-for-art-coords [art-coords]
  (zipmap art-coords-names art-coords))

(defn get-data-for-act-art [art-act]
  (let [
          art (:art art-act)
          act (:act art-act)
    ]
    (map #(zipmap [:art :act] [(create-map-for-art-coords %1) act]) art)))

(defn get-art-coords-csv [art-coords]
  (let [
          art-nr (:art art-coords)
          par-nr (:par art-coords)
          ust-nr (:ust art-coords)
          pkt-nr (:pkt art-coords)
          zd-nr (:zd art-coords)
          lit-nr (:lit art-coords)
    ]
    (apply str
      "\"" art-nr "\"" common/csv-delimiter
      "\"" par-nr "\"" common/csv-delimiter
      "\"" ust-nr "\"" common/csv-delimiter
      "\"" pkt-nr "\"" common/csv-delimiter
      "\"" zd-nr "\"" common/csv-delimiter
      "\"" lit-nr "\"" common/csv-delimiter)))

(defn get-csv-for-extracted-link [link signature]
  (let [
          art (:art link)
          act (:act link)
    ]
  (apply str (get-art-coords-csv art)
    "\"" signature "\"" common/csv-delimiter
    "\"" (:year act) "\"" common/csv-delimiter
    "\"" (:nr act) "\"" common/csv-delimiter
    "\"" (:pos act) "\"" "\n")))

(defn get-csv-for-orphaned-link [link signature]
  (let [
          art (:art link)
          txt (:txt link)
    ]
    (apply str
      "\"" txt "\"" common/csv-delimiter
      (apply str
        (map
          #(str "\"" % "\"" common/csv-delimiter)
          art))
      "\"" signature "\"" "\n")))

(defn get-csv-for-links [get-csv-func links signature]
  (str/join ""
    (map
      #(get-csv-func % signature)
      links)))

(defn get-data-for-orphaned-link [orphaned-link]
  (let [
          txt (tokens-to-string (:act orphaned-link))
    ]
  (map #(zipmap [:txt :art]
                [ txt %])
                 (:art orphaned-link))))
  
(defn load-dictionary [path]
  (let [
          txt (slurp path)
          lines (str/split txt #"\n")
          trimmed-lines
          (map
            #(subs % 1 (dec (count %)))
            lines)
          pattern
          (re-pattern
            (str "\"" common/csv-delimiter "\""))
          records
          (map
            #(str/split % pattern)
            trimmed-lines)
          dictionary
          (map
            #(vector
              (re-pattern
                (replace-several (str "(?i)" (nth % 0))
                  #"\(" "\\("
                  #"\)" "\\)"))
              (zipmap
                [:year :nr :pos]
                [(nth % 1) (nth % 2) (nth % 3)]))
            records)
    ]
    dictionary))

(defn extract-law-links [s dictionary-file-path]
  (let [
        dictionary (load-dictionary dictionary-file-path)
        merged-dictionary (concat dictionary dictionary-for-acts)
        txt (replace-several s
              #"art\." " art. "
              #"ust\." " ust. "
              #"§" " § "
              #"pkt" " pkt "
              #"zd\." " zd. ")
        tokens (lg-split-tokens-bi "pl" txt)
        interfering-art-coords-ranges
        (profiling/p :a
          (get-interfering-art-coords-ranges tokens))
        inter-coords-ranges
        (profiling/p :b
          (get-inter-coords-ranges tokens))
        correct-art-coords-ranges
        (profiling/p :c
          (get-correct-art-coords-ranges tokens))
        coords-texts
        (profiling/p :d
          (map
            #(build-coords-text % tokens)
            correct-art-coords-ranges))
        art-coords
        (profiling/p :e
          (map 
            common/extract-coords
            (map #(build-coords-text % tokens) correct-art-coords-ranges)))
        act-coords
        (profiling/p :f
          (handle-w-zwiazku-z
            (map #(extract-nr-pos-case % merged-dictionary)
              (map 
                #(get-range tokens (first %) (second %)) 
                inter-coords-ranges))))
        links
        (profiling/p :g
          (map #(zipmap [:art :act] [%1 %2])
            art-coords act-coords))
        distinct-art-coords
        (profiling/p :h
          (distinct
            (map #(:art %)
              links)))
        majority-votes-for-act-coords
        (profiling/p :i
          (map
            #(get-majority-act-coords-for-art-coords % links)
            distinct-art-coords))
        links-after-majority-voting
        (profiling/p :j
          (map
            #(change-act-coords-to-majorities % links)
            majority-votes-for-act-coords))
        extracted-links
        (profiling/p :k
          (filter
            #(map? (:act %))
            links-after-majority-voting))
        orphaned-links
        (profiling/p :l
        (flatten
          (filter
            #(not-map? (:act %))
            links-after-majority-voting)))
        ; nill (println orphaned-links)
        ]
  (->>
    (zipmap
    [:extracted-links :orphaned-links]
    [(mapcat get-data-for-act-art extracted-links)
     (mapcat get-data-for-orphaned-link orphaned-links)]))))

(defn extract-law-links-from-file
  [input-file-path output-file-path orphaned-links-file-path
   dictionary-file-path signature ]
  (let [
          input-txt (slurp input-file-path)
          signature
          (if(nil? signature)
            (extract-signature (get-line-with-signature input-txt))
            signature)
          signature-file-name (last (str/split input-file-path #"/"))
          links
          ; (profiling/profile :info :Arithmetic
            (extract-law-links input-txt dictionary-file-path)
        ]
  (spit output-file-path
    (get-csv-for-links
      get-csv-for-extracted-link
      (:extracted-links links)
      signature))
  (spit orphaned-links-file-path
    (get-csv-for-links
      get-csv-for-orphaned-link
      (:orphaned-links links)
      signature))))
