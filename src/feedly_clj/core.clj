(ns feedly-clj.core
  "Python client https://github.com/zgw21cn/FeedlyClient"
  (:gen-class)
  (:require
   [feedly-clj.config :refer [props]]
   [feedly-clj.utils :as utils]
   [feedly-clj.html :as html]
   [feedly-clj.oss :as oss]
   
   [sparrows.http :as http :refer [async-request]]
   [sparrows.cypher :refer [form-encode base64-encode]]
   [sparrows.time :as time]
   [sparrows.misc :refer [str->num wrap-exception]]
   [sparrows.io :as sio]

   [clj-time.core :refer [time-zone-for-id]]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as s]
   [clojure.java.jdbc :as j]
   [clojure.java.io :as io]
   [taoensso.timbre :as t :refer [example-config merge-config! default-timestamp-opts]]
   [taoensso.timbre.appenders.3rd-party.rolling :refer [rolling-appender]]
   [cheshire.core :refer [parse-string generate-string]]))

(defn make-timbre-config
  []
  {:timestamp-opts (merge default-timestamp-opts {:pattern "yy-MM-dd HH:mm:ss.SSS ZZ"
                                                  :timezone (java.util.TimeZone/getTimeZone "Asia/Shanghai")})
   :level          (props :log-level)
   :appenders      {:rolling (rolling-appender
                              {:path    (props :log-file)
                               :pattern :monthly})}})

(def db
  {:classname   "org.sqlite.JDBC"
   :subprotocol "sqlite"
   :subname     (props :db)})

(defn create-db []
  (j/db-do-commands
   db
   (j/create-table-ddl
    :article
    [[:xid :text]
     [:title :text] 
     [:crawled :integer]
     [:id :text]
     [:fingerprint :text]
     [:data :text]])))

(defn create-constraints
  []
  (j/execute! db ["CREATE INDEX article_xid on article(xid)"])
  (j/execute! db ["CREATE UNIQUE INDEX unique_article_id on article(id)"]))

(defn init-db! []
  (create-db)
  (create-constraints))


(defn -all-subscriptions []
  (let [url "https://cloud.feedly.com/v3/subscriptions"
        request {:url url
                 :method :get
                 :timeout 10000
                 :headers {"Authorization" (props :access-token)}}
        {:keys [body status error] :as resp} @(async-request request)]
    (if (= 200 status)
      (parse-string body true)
      (do
        (t/error "get all subscriptions error:" resp)
        (throw (RuntimeException. "all-subscriptions error"))))))

(defonce all-subscriptions (memoize -all-subscriptions))

(defn stream-by-id [id cnt]
  (let [url "https://cloud.feedly.com/v3/streams/contents"
        request {:url url
                 :method :get
                 :query-params {:count cnt
                                :streamId id}
                 :headers {"Authorization" (props :access-token)}}
        {:keys [body status error]} @(async-request request)]
    (when (= 200 status)
      (parse-string body true))))

(defn- try-insert [item]
  (try
    (j/insert! db :article item)
    (t/info "Save article" (:title item))
    ::continue
    (catch Exception e
      (if (s/includes? (or (.getMessage e) "") "unique")
        ::done
        (throw e)))))

(defn localize-img [{:keys [id crawled title fingerprint summary content] :as article}]
  (let [content-c (:content content)
        content-s (:content summary)
        body (html/transform-img-as-local (or content-c content-s))]
    (when (s/blank? body)
      (t/warn "No html output for" article))
    (if content-c
      (assoc-in article [:content :content] body)
      (assoc-in article [:summary :content] body))))

(defn load-all-feeds! []
  (doseq [{:keys [id title]} (all-subscriptions)]
    (let [cnt 50
          xid id
          stream (stream-by-id id cnt)
          x-item (map (fn [{:keys [id crawled title fingerprint summary content] :as article}]
                        {:xid xid
                         :id id
                         :title title
                         :crawled crawled
                         :data (generate-string article)})
                      (:items stream))]
      (t/info "Maybe save articles for" title)
      (loop [x-item x-item] ;; assuming feeds are returned by :crawled in descending order
        (when-first [item x-item]
          (when (= ::continue (try-insert item))
            (recur (rest x-item))))))))

(defn article-by-xid [xid]
  (j/query db ["select * from article where xid=? order by crawled desc" xid]))

(defn gen-html [frm {:keys [id title]}]
  (let [x-article (j/query db ["select * from article where xid=? and crawled>? order by crawled desc" id frm])
        content (reduce
                 (fn [ss {:keys [title crawled data id]}]
                   (let [{:keys [summary content canonicalUrl alternate crawled] :as art} (parse-string data true)
                         loc-art (localize-img art)
                         url (or canonicalUrl (-> alternate first :href))
                         body (or (get-in loc-art [:content :content])
                                  (get-in loc-art [:summary :content]))]
                     (when-not (= loc-art art)
                       (j/update! db :article {:data (generate-string loc-art)} ["id=?" id]))
                     (str ss
                          (format
                           "<h1 class=\"articleTitle\">%s</h1><div class=\"crawledTime\">%s</div>
<div class=\"urlQR\"><img src=\"data:image/png;base64, %s\"></div> %s"
                           title
                           (time/long->datetime-string (or crawled 0))
                           (try
                             (base64-encode (utils/gen-qrcode url) :url-safe? false)
                             (catch Exception e
                               (t/error "Ignore gen-qrcode and encode error" e)
                               ""))
                           body))))
                 ""
                 x-article)]
    (t/info "Adding" (count x-article) "articles from" title)
    (when-not (s/blank? content)
      (let [out (io/file "rss/" (str (subs title 0 (min 8 (count title))) ".html"))
            style "<style>img{width: 100%;}</style>"]
        (spit out
              (format "<html><body>%s %s</body></html>" style content))))))

(defn assert-tools! []
  (and (= "linux" (s/lower-case (System/getProperty "os.name")))
       (= 0
          (:exit (sh "which" "calibre"))
          (:exit (sh "which" "ebook-convert"))
          (:exit (sh "which" "pandoc")))))

(defn- prepare-dirs! []
  (sio/del-dir "rss")
  (io/make-parents "rss/a.html"))

(defn- in-htmls []
  (->> (.listFiles (io/file "rss"))
       (map (fn [f]
              (.getAbsolutePath f)))
       (filter #(s/ends-with? % ".html"))))

;; ebook-convert 1903061650.epub 1903061650.mobi  --mobi-file-type both --title test.mobi --authors cern --mobi-toc-at-start --use-auto-toc --level1-toc "//h:h1[re:test(@class, \"feedTittle\", \"i\")]" --level2-toc "//h:h2[re:test(@class, \"articleTitle\", \"i\")]"
(defn gen-ebook [start]
  (prepare-dirs!)
  (let [x-feed (all-subscriptions)]
    (run! (partial gen-html start) x-feed)
    (if-let [x-in-html (in-htmls)]
      (let [now (time/long->datetime-string (time/now-in-millis) {:pattern "yyMMddHHmm" :offset "+8"})
            tmp-in (str now ".html")
            epub-out (str now ".epub")]
        (t/info (apply sh "pandoc" "--toc" "--toc-depth=2" "-s" "-r" "html" "-o" epub-out x-in-html))
        epub-out)
      (t/error "No content to create book, quit."))))

(defn- get-prev-push-time []
  (try
    (str->num (slurp "last-push.txt"))
    (catch Exception e
      (- (time/start-of-day) (* 3600 1000 24 4)))))

(defn- set-prev-push-time [ts]
  (t/info "change last-push time from" (get-prev-push-time) "to" ts)
  (spit "last-push.txt" (str ts)))

(defn make-book-and-set-time []
  (let [now (System/currentTimeMillis)
        start (get-prev-push-time)
        book (gen-ebook start)
        today (time/long->date-string (System/currentTimeMillis) {:pattern "yyMMdd" :offset "+8"})]
    (when (and book (.exists (io/file book)) (> (.length (io/file book)) 1000))
      ;;(io/copy (io/file book) (io/file (props :mobi-out-root) (str today book-ext)))
      (set-prev-push-time now)
      (io/file book))))

(defonce push-time-store (atom 0))

(defn should-push? []
  (let [now (System/currentTimeMillis)
        prev-push @push-time-store
        [h m] (props :push-time)
        next-push-time (+ (time/start-of-day)
                          (* 3600 1000 h)
                          (* 60 1000 m))]
    (and
     (< next-push-time now)
     (not= (time/start-of-day)
           (time/start-of-day prev-push)))))

(defn mark-pushed []
  (reset! push-time-store (System/currentTimeMillis)))

(defn -main [& args]
  (t/merge-config! (make-timbre-config))
  (when-not (.exists (io/file (props :db)))
    (init-db!))
  (try
    (load-all-feeds!)
    (when-let [f-book (make-book-and-set-time)]
      (when (props [:aliyun-oss :endpoint] {:default nil})
        (oss/upload f-book)))
    (catch Throwable e
      (t/error "Load and push error")
      (t/error e))))


