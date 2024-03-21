
public class RdfSeq {
    @Getter
    private final String key;

    @RdfConstructor(parentNode = "rdf:Seq", childNode = ChildNodeType.MULTI)
    public ArticleSequence(@NodeAttribute(nodeName = "rdf:li", attributeName = "rdf:resource") String key) {
        this.key = key;
    }
}