# feedly-clj

Download your Feedly articles and convert to epub.

## Requirement

* JDK8
* Linux
* [Pandoc](https://github.com/jgm/pandoc)
* [imagemagick](http://www.imagemagick.org)
* Your Feedly developer access token, your can  it [here](https://feedly.com/v3/auth/dev). You need to record your client-id.

## Usage

* Edit `config-fd.edn` and fill in your Feedly client-id and access-token: 

```clojure
:userid              "YOUR-USERID"
:access-token        "YOUR-FEEDLY-ACCESS-TOKEN"
```

* To start download feeds and convert to ebook, 
 * If you have git and [leinigen](https://github.com/technomancy/leiningen) installed, run 

```bash
git clone https://github.com/cassc/feedly-clj
cd feedly-clj
lein run
```
 * If you downloaded from released package, unzip and run this command, replacing `VERSION` with the appropriate version. 

```bash
java -jar feedly-VERSION.jar
``` 

## Customization

To reduce the generated ebook file size, you can resize images to smaller. Max image file size (in kb) allowed can be set in `config-fd.edn` by editing the following line:

``` clojure
:max-image-size      100
```

## Complete sample configuration

```clojure
{:userid              "YOUR-USERID"
 :access-token        "YOUR-FEEDLY-ACCESS-TOKEN" 
 :db                  "feedly.db"
 :log-level           :info
 :log-file            "feedly.log"
 :img-connect-options {:connect-timeout 5}
 :max-image-size      100 ;; reduce images to this size(kb)
 
 ;; :aliyun-oss nil ;; set to nil if you don't wish to auto upload to Aliyun OSS
 :aliyun-oss          {:endpoint          "your-oss-end-point-url"
                       :bucket-name       "your-bucket-name"
                       :access-key-id     "oss-access-key"
                       :access-key-secret "oss-access-serect"}
 }
```

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
