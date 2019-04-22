(ns feedly-clj.config
  (:require
   [clj-props.core :refer [defconfig]]))

(def ^:private cfg
  (or (.get (System/getenv) "FEEDLY_CONFIG") "config-fd.edn"))

(defconfig props (clojure.java.io/file cfg) {:secure false})
