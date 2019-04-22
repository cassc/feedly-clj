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

```
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

## License

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
