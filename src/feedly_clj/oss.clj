(ns feedly-clj.oss
  (:require
   [feedly-clj.config :refer [props]]
   [sparrows.http :refer [async-request]]
   [sparrows.cypher :refer [form-encode sha1 md5]]
   [sparrows.system :refer [get-mime]]
   [sparrows.misc :refer [dissoc-nil-val]]
   [clojure.java.io :as io]
   [cheshire.core :refer [parse-string]]
   [taoensso.timbre :as t]
   [clojure.string :as s]
   [com.rpl.specter :as specter :refer [transform]])
  (:import
   [com.aliyun.oss OSSClient]
   [java.util Date]))

(defn make-oss-client []
  (OSSClient.
   (props [:aliyun-oss :endpoint])
   (props [:aliyun-oss :access-key-id])
   (props [:aliyun-oss :access-key-secret])))

(defn upload
  ([^java.io.File f]
   (upload f (.getName f)))
  ([^java.io.File file ^String key]
   (let [client (make-oss-client)]
     (try
       (.putObject client (props [:aliyun-oss :bucket-name]) key file)
       (finally (.shutdown client))))))

;; https://www.alibabacloud.com/help/zh/doc-detail/32016.htm?spm=a2c63.p38356.879954.8.5171384980tvdW
(defn generate-download-url [obj-name expire-millis]
  (let [client (make-oss-client)
        bucket (props [:aliyun-oss :bucket-name])
        expire-at (Date. (+ (System/currentTimeMillis) expire-millis))]
    (try
      (str
       (.generatePresignedUrl client bucket obj-name expire-at))
      (finally (.shutdown client)))))
