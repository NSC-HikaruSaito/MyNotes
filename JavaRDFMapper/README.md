# JavaRDFMapper

Rdf/XmlファイルをJavaのクラスにマッピングするやつ

（例）  
[hoge.rdf](https://github.com/NSC-HikaruSaito/MyNotes/blob/main/JavaRDFMapper/hoge.rdf)  
を  
```java
public class ContentDto {
    private final String title;
    private final Date datetime;
    private final String url;
    private final String thumbnail;
    private final String description;
}
```
[ContentDto](https://github.com/NSC-HikaruSaito/MyNotes/blob/main/JavaRDFMapper/src/sample/ContentDto.java)  
にマッピングする。
