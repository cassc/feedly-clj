(ns feedly-clj.utils
  (:require
   [feedly-clj.config          :refer :all]
   
   [clojure.java.io       :as io])
  (:import
   [org.apache.commons.codec.binary Base64]
   com.google.zxing.EncodeHintType
   com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
   net.glxn.qrgen.javase.QRCode
   [java.io File]))

(defn gen-qrcode
  "Create a qr code, use :charset to set charset, e.g., to `ISO-8859-1` or `UTF-8`"
  [content & [{:keys [size out image-type charset]
               :or {size    120
                    image-type    "PNG"
                    charset "ISO-8859-1"}
               :as params}]]
  (let [out (or out (File/createTempFile "qrcode-" (str "." image-type)))]
    (doto (QRCode/from content)
      (.withCharset charset)
      (.withErrorCorrection (ErrorCorrectionLevel/H))
      (.withHint (EncodeHintType/CHARACTER_SET) charset)
      (.to (eval (symbol (str "net.glxn.qrgen.core.image.ImageType/" image-type))))
      (.withSize size size)
      (.writeTo (io/output-stream out)))
    out))

