package nir.parsing;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;

import com.fasterxml.jackson.databind.*;

public class NewsAPIFetcher {

    private static final String API_KEY = System.getenv("NEWS_API_KEY");
    private static final String PERSON_NAME = "баербок";
    private static final int MAX_PAGES = 10;
    private static final int PAGE_SIZE = 100; // максимум для NewsAPI

    public static void main(String[] args) throws Exception {
        /*String[] names = {
                "Olaf Scholz",
                "Chancellor Olaf Scholz",
                "Bundeskanzler Scholz",
                "German Chancellor Scholz"
        };*/
        String[] names = {
                "Annalena Baerbock",
                "Foreign Minister Baerbock",
                "German Foreign Minister Baerbock",
                "Außenministerin Baerbock"
        };
        String[] languages = {"en"};

        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        ObjectMapper mapper = new ObjectMapper();

        for (String name : names) {
            for (String lang : languages) {
                String query = URLEncoder.encode(name, StandardCharsets.UTF_8);

                for (int page = 1; page <= MAX_PAGES; page++) {

                    System.out.println("\n🔎 Поиск: " + name + " | язык: " + lang + " | страница: " + page);

                    String url = "https://newsapi.org/v2/everything" +
                            "?q=" + query +
                            "&language=" + lang +
                            "&pageSize=" + PAGE_SIZE +
                            "&page=" + page +
                            "&sortBy=publishedAt" +
                            "&apiKey=" + API_KEY;

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .GET()
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() != 200) {
                        System.err.println("❌ Ошибка запроса: " + response.statusCode());
                        break;
                    }

                    JsonNode root = mapper.readTree(response.body());
                    JsonNode articles = root.get("articles");

                    if (articles != null && articles.isArray()) {
                        System.out.println("✅ Получено статей: " + articles.size());

                        for (JsonNode article : articles) {
                            String title = article.hasNonNull("title") ? article.get("title").asText() : "";
                            String content = article.hasNonNull("content") ? article.get("content").asText() : "";
                            String urlLink = article.hasNonNull("url") ? article.get("url").asText() : "";
                            String publishedAt = article.hasNonNull("publishedAt") ? article.get("publishedAt").asText() : "";

                            Timestamp publishedTimestamp = null;
                            try {
                                publishedTimestamp = Timestamp.from(Instant.parse(publishedAt));
                            } catch (Exception e) {
                                System.err.println("⚠️ Не удалось преобразовать дату: " + publishedAt);
                            }

                            StatementSaver.saveStatement(
                                    PERSON_NAME, title, content, urlLink, "NewsAPI", publishedTimestamp
                            );
                        }
                    } else {
                        System.out.println("⚠️ Пустой результат на этой странице.");
                        break;
                    }

                    if (articles.size() < PAGE_SIZE) break; // меньше страниц, больше нет данных
                    Thread.sleep(3000); // пауза, чтобы не спамить API
                }
            }
        }
    }
}
