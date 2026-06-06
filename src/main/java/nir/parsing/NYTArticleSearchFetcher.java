package nir.parsing;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import com.fasterxml.jackson.databind.*;

public class NYTArticleSearchFetcher {

    private static final String API_KEY = System.getenv("NYT_API_KEY");
    private static final String PERSON_NAME = "баербок";

    private static final String[] KEYWORDS = {
            /*"Olaf Scholz",
            "Bundeskanzler Scholz",
            "German Chancellor Scholz"
    };*/
            "Annalena Baerbock",
            "Foreign Minister Baerbock",
            "German Foreign Minister Baerbock",
            "Außenministerin Baerbock"
    };

    private static final String BEGIN_DATE = "20230101";
    private static final String END_DATE = "20260101";
    private static final int MAX_PAGES = 100;

    public static void main(String[] args) throws Exception {

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        for (String kw : KEYWORDS) {
            String query = URLEncoder.encode(kw, StandardCharsets.UTF_8);

            for (int page = 0; page < MAX_PAGES; page++) {

                String url =
                        "https://api.nytimes.com/svc/search/v2/articlesearch.json" +
                                "?q=" + query +
                                "&begin_date=" + BEGIN_DATE +
                                "&end_date=" + END_DATE +
                                "&page=" + page +
                                "&api-key=" + API_KEY;

                HttpResponse<String> response = client.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                );

                JsonNode docs = mapper.readTree(response.body()).at("/response/docs");
                if (docs.isEmpty()) break;

                for (JsonNode doc : docs) {
                    String title = doc.at("/headline/main").asText("");
                    String content = doc.path("lead_paragraph").asText("");
                    String urlLink = doc.path("web_url").asText("");
                    String publishedAt = doc.path("pub_date").asText("");

                    Timestamp ts = null;

                    try {
                        if (!publishedAt.isBlank()) {

                            // Оставляем только yyyy-MM-ddTHH:mm:ss
                            if (publishedAt.length() >= 19) {
                                publishedAt = publishedAt.substring(0, 19) + "Z";
                            }

                            Instant instant = Instant.parse(publishedAt);
                            ts = Timestamp.from(instant);
                        }

                    } catch (Exception e) {
                        System.err.println("⚠️ Не удалось распарсить дату NYT: " + publishedAt);
                    }


                    StatementSaver.saveStatement(
                            PERSON_NAME, title, content, urlLink, "NYT Archive", ts
                    );
                }

                Thread.sleep(12_000);
            }
        }
    }
}
