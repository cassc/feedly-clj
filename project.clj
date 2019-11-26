(defproject feedly-clj "0.1.1-SNAPSHOT"
  :description "Download your feedly feeds as ebook in epub format."
  :url "https://github.com/cassc/feedly-clj"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojars.august/sparrows "0.2.9"]
                 [hiccup "1.0.5"]
                 [hickory "0.7.1"]

                 [com.climate/claypoole "1.1.4"]
                 [com.rpl/specter "1.1.2"]
                 [org.clojure/tools.reader "1.0.0-alpha1"]
                 [cheshire "5.8.1"]
                 [org.clojure/java.jdbc "0.6.1"]  ; jdbc
                 [org.xerial/sqlite-jdbc "3.7.2"] ; sqlite
                 [clj-time "0.15.0"]
                 [net.glxn.qrgen/javase "2.0"] ;; qrcode
                 [cassc/clj-props "0.1.2"]
                 [com.aliyun.oss/aliyun-sdk-oss "3.3.0"]
                 ]
  :main feedly-clj.core)
