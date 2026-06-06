package nir.parsing;

import java.net.URI;
import java.net.http.*;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.*;

public class NYTArchiveFetcher {

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

        LocalDate start = LocalDate.of(2023, 1, 1);
        LocalDate now = LocalDate.now();

        for (LocalDate date = start; !date.isAfter(now); date = date.plusMonths(1)) {

            String url = String.format(
                    "https://api.nytimes.com/svc/archive/v1/%d/%d.json?api-key=%s",
                    date.getYear(), date.getMonthValue(), API_KEY
            );

            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder(URI.create(url)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            );

            if (response.statusCode() != 200) continue;

            JsonNode docs = mapper.readTree(response.body()).at("/response/docs");

            for (JsonNode doc : docs) {
                String text = doc.toString().toLowerCase();

                boolean match = false;
                for (String kw : KEYWORDS) {
                    if (text.contains(kw.toLowerCase())) {
                        match = true;
                        break;
                    }
                }
                if (!match) continue;

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

            Thread.sleep(12_000); // строго по лимитам
        }
    }
}
