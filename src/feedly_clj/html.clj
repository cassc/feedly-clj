(ns feedly-clj.html
  (:require
   [feedly-clj.config          :refer :all]

   [hickory.core :refer [parse parse-fragment as-hiccup as-hickory]]
   [hickory.select :as hs]
   [hickory.render :refer [hickory-to-html]]
   [com.rpl.specter :refer :all]


   [com.climate.claypoole :as cp]
   [clj-http.client :as client]
   [cheshire.core :refer [parse-string]]
   [clojure.edn           :as e]
   [taoensso.timbre       :as t]
   [sparrows.misc         :as sm :refer [str->num]]
   [sparrows.cypher       :refer :all]
   [sparrows.time         :as time :refer [long->datetime-string long->date-string]]
   [com.climate.claypoole :as cp]
   [clojure.java.shell :refer [sh]]
   [clojure.string        :as s]
   [clojure.java.io       :as io]))

(def max-img-size (* (props :max-image-size) 1024))

(defn convert-img-link [dl-queue node]
  (transform (walker (fn [e-img] (and
                                  (map? e-img)
                                  (= :element (:type e-img))
                                  (= :img (:tag e-img)))))
             (fn [e-img]
               (let [src (get-in e-img [:attrs :src])]
                 (if (and src (s/starts-with? src "http"))
                   (let [target-img (str "img/" (System/currentTimeMillis) (rand-str 8) ".jpg")]
                     (swap! dl-queue conj [src target-img])
                     (assoc-in e-img [:attrs :src] target-img))
                   e-img)))
             node))

(def client-opts
  {:socket-timeout             5000
   :connection-timeout         5000
   :connection-request-timeout 5000
   :retry-handler              (fn [ex try-count http-context]
                                 (t/warn "ignore" ex)
                                 false)})

(defn- resize-img! [f]
  (let [tmp (io/file (str (System/currentTimeMillis) (rand-str 6) ".jpg"))]
    (sh "convert"
        (str (.getAbsolutePath f) "[0]")
        "-define"
        (str "jpeg:extent=" (props :max-image-size) "kb")
        (.getAbsolutePath tmp))
    (when (.exists tmp)
      (io/copy tmp f)
      (io/delete-file tmp true))))

(defn download-as [src target]
  (let [{:keys [body status headers]} (client/get src (assoc client-opts :as :stream))
        content-type (get headers "Content-Type" "")
        content-length (or (str->num (get headers "Content-Length")) 0)]
    (when-not (= 200 status)
      (t/warn "download fail" src target))
    (when (= 200 status)
      (t/info "download success" src target content-type content-length)
      (let [out (io/file target)]
        (io/make-parents out) 
        (io/copy body out)
        (when (> content-length max-img-size)
          (resize-img! out))))))

(defonce dl-pool (cp/threadpool 4 :daemon false))

(defn start-downloader [x-queue]
  (cp/prun!
   dl-pool
   (fn [[src target]]
     (try
       (t/info "downloading" src "to" target)
       (cp/future
         dl-pool
         (download-as src target))
       (catch Exception e
         (t/error "Ignore error" e))))
   x-queue))


(defn transform-img-as-local [html-frag]
  (let [dl-queue (atom [])
        out (some->> html-frag
                     parse-fragment
                     (mapv as-hickory)
                     (mapv (partial convert-img-link dl-queue))
                     (mapv hickory-to-html)
                     (s/join ""))]
    (start-downloader @dl-queue)
    out))

(comment
  (def content (slurp "sample.html"))
  (transform-img-as-local content)
  )
