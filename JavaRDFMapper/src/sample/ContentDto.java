
@Getter
public class ContentDto {

    /**
     * 記事タイトル
     */
    private final String title;
    /**
     * 作成日時
     */
    private final Date datetime;

    /**
     * 記事URL
     */
    private final String url;

    /**
     * 画像URL
     */
    private final String thumbnail;
    /**
     * 説明文
     */
    private final String description;

    private static final List<String> DATE_FOMART_ACCEPTED = Arrays.asList(
            "yyyy/MM/dd HH:mm:ss",
            "yyyy/M/d HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-M-d HH:mm:ss",
            "yyyyMMddHHmmss",
            "yyyy-MM-dd'T'HH:mm:ssZ",
            "yyyy-MM-dd'T'HH:mm:ssX",
            "yyyy-MM-dd'T'HH:mm:ssXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX");

    @RdfConstructor(parentNode = "item")
    public ArticleContentDto(
            @NodeElement(nodeName = "title") String title,
            @NodeElement(nodeName = "dc:date") String datetime,
            @NodeElement(nodeName = "link") String url,
            @NodeElement(nodeName = "typicalImages") String thumbnail,
            @NodeElement(nodeName = "description") String description
    ) {
        this.title = title;
        this.datetime = this.convertDateTimeWithAnyFormat(datetime);
        this.url = url;
        this.thumbnail = thumbnail;
        this.description = this.shapingDescription(description);
    }

    private Date convertDateTimeWithAnyFormat(String inputDate) {

        Iterator<String> formatDates = DATE_FOMART_ACCEPTED.iterator();
        while (formatDates.hasNext()) {
            try {
                return new SimpleDateFormat(formatDates.next()).parse(inputDate);
            } catch (ParseException ex) {
                continue;
            }
        }
        throw new RuntimeException("Format Date is not supported. please add to DATE_FOMART_ACCEPTED!");
    }
}