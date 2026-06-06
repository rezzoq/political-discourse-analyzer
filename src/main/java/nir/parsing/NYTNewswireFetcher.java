package nir.parsing;

import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.Instant;
import com.fasterxml.jackson.databind.*;

public class NYTNewswireFetcher {

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


    public static void main(String[] args) throws Exception {

        HttpClient client = HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();

        String url =
                "https://api.nytimes.com/svc/news/v3/content/all/all.json?api-key=" + API_KEY;

        JsonNode results = mapper.readTree(
                client.send(
                        HttpRequest.newBuilder(URI.create(url)).GET().build(),
                        HttpResponse.BodyHandlers.ofString()
                ).body()
        ).get("results");

        for (JsonNode article : results) {
            String text = article.toString().toLowerCase();

            for (String kw : KEYWORDS) {
                if (text.contains(kw.toLowerCase())) {
                    Timestamp ts = null;
                    String publishedAt = article.findValue("published_at").asText() + "T00:00:00Z";
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
                            PERSON_NAME,
                            article.path("title").asText(""),
                            article.path("abstract").asText(""),
                            article.path("url").asText(""),
                            "NYT Newswire",
                            ts
                    );
                }
            }
        }
    }
}
