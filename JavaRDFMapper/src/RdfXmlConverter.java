import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

/**
 * RDF/XML形式のInputStreamをオブジェクトに変換する
 */
public class RdfXmlConverter {

    private Document document;

    public RdfXmlConverter(InputStream inputStream) {
        try {
            //InputStreamを解析してDocumentにする
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            this.document = builder.parse(inputStream);
        } catch (SAXException | IOException | ParserConfigurationException ex) {
            throw new RdfXmlConvertException(RdfXmlConvertError.PARSE);
        }

    }

    /**
     * 変換する
     *
     * @param <T>
     * @param target
     * @return
     */
    public <T> List<T> convert(Class<T> target) {
        //Elementを取得する
        Element rootElement = this.document.getDocumentElement();

        //RDFを解析して、変換する
        return parseRdf(rootElement, target);
    }

    /**
     * RDFを解析して、変換する
     *
     * @param <T>
     * @param node
     * @param target
     * @return
     */
    private <T> List<T> parseRdf(Node node, Class<T> target) {

        //変換するクラスのコンストラクタを取得する
        Constructor<?>[] constructors = target.getConstructors();

        //値を取得するノード
        String targetNode = null;

        //変換に使用するコンストラクタ
        Constructor<T> constract = null;

        //変換する際の引数
        List<RdfArgument> argumentList = new ArrayList<>();

        //子ノードの種別
        ChildNodeType childNode = null;

        for (Constructor<?> con : constructors) {

            //コンストラクタにRdfConstructorが付いているかチェックする
            RdfConstructor rdfConstructor = con.getAnnotation(RdfConstructor.class);
            if (rdfConstructor == null) {
                continue;
            }

            targetNode = rdfConstructor.parentNode();
            constract = (Constructor<T>) con;
            childNode = rdfConstructor.childNode();

            //コンストラクタの引数
            Parameter[] parameters = con.getParameters();

            for (Parameter param : parameters) {
                //コンストラクタの引数にNodeElementまたはNodeAttributeが付いているかチェックする
                NodeElement element = param.getAnnotation(NodeElement.class);
                NodeAttribute attribute = param.getAnnotation(NodeAttribute.class);
                if (element == null && attribute == null) {
                    throw new RdfXmlConvertException(RdfXmlConvertError.JAVA_CLASS_DEFINITION);
                } else if (element != null) {
                    argumentList.add(new RdfArgument(LocationType.ELEMENT, element.nodeName(), null));
                } else {
                    argumentList.add(new RdfArgument(LocationType.ATTRIBUTE, attribute.nodeName(), attribute.attributeName()));
                }
            }
        }

        if (targetNode == null || constract == null || argumentList.isEmpty() || childNode == null) {
            throw new RdfXmlConvertException(RdfXmlConvertError.JAVA_CLASS_DEFINITION);
        }

        List<T> resultList = new ArrayList<>();
        for (Node current = node.getFirstChild(); current != null; current = current.getNextSibling()) {

            //ノードが要素なのかチェックする
            if (current.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            //ノードが値を取得するノードなのかチェックする
            String nodeName = current.getNodeName();
            if (nodeName.equals(targetNode)) {
                switch (childNode) {
                    case SINGLE:
                        T result = getTarget(current, constract, argumentList);
                        resultList.add(result);
                        break;
                    case MULTI:
                        resultList.addAll(getMultiTarget(current, constract, argumentList));
                        break;
                    default:
                        throw new RdfXmlConvertException(RdfXmlConvertError.JAVA_CLASS_DEFINITION);
                }
            } else {
                //再帰処理
                resultList.addAll(parseRdf(current, target));
            }
        }
        return resultList;
    }


    /**
     * クラスTを取得する
     * @param <T>
     * @param node
     * @param constract
     * @param argumentList
     * @return
     */
    private <T> T getTarget(Node node, Constructor<T> construct, List<RdfArgument> argumentList) {
        Map<RdfArgument, String> valueMap = new LinkedHashMap<>();

        try {
            for (Node current = node.getFirstChild(); current != null; current = current.getNextSibling()) {

                //ノードが要素なのかチェックする
                if (current.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                //ノード名が引数の名称と一致するかチェックする
                String nodeName = current.getNodeName();
                List<RdfArgument> nodeList = argumentList.stream().filter(argument -> {
                    return (argument.nodeName == null ? nodeName == null : argument.nodeName.equals(nodeName));
                }).collect(Collectors.toList());

                getContents(valueMap, current, nodeList);
            }

            //インスタンスを作る
            return construct.newInstance(
                    argumentList.stream().map(argument -> {
                        return valueMap.get(argument);
                    }).toArray()
            );

        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new RdfXmlConvertException(RdfXmlConvertError.CONSTRACT_INSTANCE);
        }
    }

    private void getContents(Map<RdfArgument, String> valueMap, Node node, List<RdfArgument> argumentList) {
        argumentList.forEach(argument -> {
            valueMap.put(argument, getContent(node, argument));

        });
    }

    private String getContent(Node node, RdfArgument value) {
        String content = "";
        if (value.type == LocationType.ATTRIBUTE) {
            return node.getAttributes().getNamedItem(value.attributeName).getNodeValue();
        }

        node = node.getFirstChild();
        if (node.getNodeType() == Node.TEXT_NODE
                && node.getNodeValue().trim().length() != 0) {
            content = node.getNodeValue();
        } else if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
            content = node.getNodeValue();
        }
        return content;
    }

    private <T> List<T> getMultiTarget(Node node, Constructor<T> constract, List<RdfArgument> argumentList) {
        List<T> resultList = new ArrayList<>();

        Map<RdfArgument, String> valueMap = new LinkedHashMap<>();

        try {
            for (Node current = node.getFirstChild(); current != null; current = current.getNextSibling()) {
                if (current.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }

                String nodeName = current.getNodeName();
                List<RdfArgument> nodeList = argumentList.stream().filter(argument -> {
                    return (argument.nodeName == null ? nodeName == null : argument.nodeName.equals(nodeName));
                }).collect(Collectors.toList());

                getContents(valueMap, current, nodeList);

                if (valueMap.size() == argumentList.size()) {
                    resultList.add(constract.newInstance(
                            argumentList.stream().map(argument -> {
                                return valueMap.get(argument);
                            }).toArray()
                    ));
                    valueMap.clear();
                }

            }
            return resultList;

        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new RdfXmlConvertException(RdfXmlConvertError.CONSTRACT_INSTANCE);
        }
    }

    class RdfArgument {

        //値の場所
        public LocationType type;

        //ノード名
        public String nodeName;

        //要素名
        public String attributeName;

        public RdfArgument(LocationType type, String nodeName, String attributeName) {
            this.type = type;
            this.nodeName = nodeName;
            this.attributeName = attributeName;
        }

        @Override
        public int hashCode() {
            int hashCode = this.type.hashCode() + this.nodeName.hashCode();
            if (attributeName != null) {
                hashCode += this.attributeName.hashCode();
            }
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            final RdfArgument other = (RdfArgument) obj;
            if (!Objects.equals(this.type, other.type)) {
                return false;
            }
            if (this.nodeName != other.nodeName) {
                return false;
            }
            if (this.attributeName != other.attributeName) {
                return false;
            }
            return true;
        }
    }

}