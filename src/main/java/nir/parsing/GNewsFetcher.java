package nir.parsing;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.*;

public class GNewsFetcher {

    private static final String API_KEY = System.getenv("GNEWS_API_KEY");
    private static final String PERSON_NAME = "баербок";
    private static final int DAYS_BACK = 100;
    private static final int MAX_PAGES = 10;

    public static void main(String[] args) throws Exception {
        String[] names = {
                "Annalena Baerbock",
                "Foreign Minister Baerbock",
                "German Foreign Minister Baerbock",
                "Außenministerin Baerbock"
        };
        /*String[] names = {
                "Olaf Scholz",
                "Scholz",
                "Bundeskanzler Scholz",
                "German Chancellor Scholz"
        };*/
        String[] languages = {"en", "de"};

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        ObjectMapper mapper = new ObjectMapper();

        for (int page = 1; page <= MAX_PAGES; page++) {
            for (String name : names) {
                for (String lang : languages) {

                    String query = URLEncoder.encode(name, StandardCharsets.UTF_8);

                    System.out.println("\n🔎 Поиск: " + name + " | язык: " + lang);


                    String url = "https://gnews.io/api/v4/search" +
                            "?q=" + query +
                            "&lang=" + lang +
                            "&sortby=publishedAt" +
                            "&max=100" +
                            "&page=" + page +
                            "&token=" + API_KEY;

                    System.out.println("🔎 Page " + page);

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() != 200) {
                        System.err.println("❌ Ошибка: " + response.statusCode());
                        break;
                    }

                    JsonNode root = mapper.readTree(response.body());
                    JsonNode articles = root.get("articles");

                    if (articles != null && articles.isArray()) {
                        System.out.println("✅ Получено статей: " + articles.size());

                        for (JsonNode article : articles) {
                            String title = article.hasNonNull("title") ? article.get("title").asText() : "";
                            String content = article.get("content").asText("");
                            String urlLink = article.get("url").asText();
                            String publishedAt = article.get("publishedAt").asText();

                            Timestamp publishedTimestamp = Timestamp.from(Instant.parse(publishedAt));
                            StatementSaver.saveStatement(
                                    PERSON_NAME, title, content, urlLink, "GNews", publishedTimestamp
                            );
                        }
                    } else {
                        System.out.println("⚠️ Пустой результат за этот день.");
                    }
                    if (articles.size() < 100) break;
                    Thread.sleep(3000);
                }
            }
        }
    }
}

