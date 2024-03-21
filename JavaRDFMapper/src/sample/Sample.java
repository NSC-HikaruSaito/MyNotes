
public class Sample {
    public List<ContentDto> convertContent(InputStream inputStream) {

        RdfXmlConverter converter = new RdfXmlConverter(inputStream.get());

        List<ContentDto>  results = converter.convert(ArticleContentDto.class);

        return results;
    }
}