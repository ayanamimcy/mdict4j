How to use mdict4j
==================

Install
-------

When you use gradle for build system, you can install mdict4j from mavenCentral.

.. code-block:: gradle

    dependencies {
        implementation 'tokyo.northside:mdict4j:0.4.0'
    }

.. note:

We changed groupId from version 0.4.0. If you want to use old versions,
please use group id 'io.github.eb4j'


Loading dictionary file
------------------------

You should load dictionary and create MdictDictionary object in order to invoke search query.

.. code-block:: java

    Path dictionaryPath = Paths.get("foo.mdx");
    MDictDictionary dictionary = MDictDictionary.loadDictionary(dictionaryPath);


MDictDictionary object has several method to indicate mdx file properties.

.. code-block:: java

    if (dictionary.isMDX()) {
        System.out.println("loaded file is .mdx");
    }
    if (StandardCharsets.UTF_8.equals(dictionary.getEncoding())) {
        System.out.println("MDX file encoding is UTF-8");
    }
    if (dictionary.isHeaderEncrypted()) {
        System.out.println("MDX file is encrypted.");
    }
    if (dictionary.isIndexEncrypted()) {
        System.out.println("MDX index part is encrypted.");
    }
    System.out.printf("MDX version: %d, format: %s", dictionary.getMdxVersion(), dictionary.getFormat());
    System.out.println(dictionary.getCreationDate());
    System.out.println(dictionary.getTitle());
    System.out.println(dictionary.getDescription());


You can invoke query from MDictDictionary object. MDictDictionary::readArticles method returns a list of entries.
MDictDictionary::readArticles method returns exact match results.
MDictDictionary::readArticlesPredictive method returns predictive search match results.

.. code-block:: java

    for (Map.Entry<String, String> entry: dictionary.readArticles("hello")) {
        System.out.println("<div><span>%s</span>: %s</div>", entry.getKey(), entry.getValue());
    }

You will see something like ``hello: 你好``

.. code-block:: java

    for (Map.Entry<String, String> entry: dictionary.readArticlesPredictive("happ")) {
        System.out.println("<div><span>%s</span>: %s</div>", entry.getKey(), entry.getValue());
    }

You will see something like ``happy: 快乐  happiness: 幸福``


When you want to load MDD data file, you can give it to MDictDictionary##loadDictionaryData method.

.. code-block:: java

    Path dataPath = Paths.get("foo.mdx");
    MDictDictionary dictData = MDictDictionary.loadDictionaryData(dataPath);
    if (!dictData.isMDX()) {
        System.out.println("loaded file is .mdd");
    }
    Map.Entry<String, Object> entry = dictData.getEntries("/audio/test.mp3").get(0);
    Object value = entry.getValue();
    byte[] buf = dictData.getData((Long) value);  // buf contains mp3 data.
    Tika tika = new Tika();
    String mediaType = tika.detect(buf);
    System.out.println("Media type should be audio/mpeg: %s", mediaType);


Please check javadoc for details.

.. code-block:: bash

    ./gradlew javadoc
