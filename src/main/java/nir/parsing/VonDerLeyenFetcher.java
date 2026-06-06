package nir.parsing;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

public class VonDerLeyenFetcher {

    private static final String PERSON_NAME = "Ursula von der Leyen";
    private static final String SOURCE_NAME = "European Commission Press Corner";
    // Базовый URL для поиска речей и заявлений
    private static final String BASE_SEARCH_URL = "https://ec.europa.eu/commission/presscorner/api/search?language=en&search=%s&page=%d";
    private static final int MAX_PAGES = 50;

    public static void main(String[] args) throws Exception {
        // Ключевые слова для поиска (можно расширить)
        String[] queries = {
                "von der Leyen speech",
                "von der Leyen statement",
                "President von der Leyen"
        };

        for (String query : queries) {
            System.out.println("\n🔎 Поиск: " + query);
            fetchAndParse(query);
        }

        System.out.println("🏁 Парсинг фон дер Ляйен завершён.");
    }

    private static void fetchAndParse(String query) throws Exception {
        for (int page = 0; page < MAX_PAGES; page++) {
            String searchUrl = String.format(BASE_SEARCH_URL,
                    java.net.URLEncoder.encode(query, "UTF-8"), page);

            System.out.println("📄 Страница " + page + ": " + searchUrl);

            Document doc = Jsoup.connect(searchUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(30000)
                    .ignoreContentType(true)
                    .get();

            // Парсим JSON-подобный ответ или HTML (зависит от того, что возвращает API)
            // Обычно Press Corner возвращает JSON с полем "results"
            String body = doc.body().text();
            if (body.contains("\"results\":")) {
                parseJsonResponse(body);
            } else {
                // Если это HTML (старая версия)
                parseHtmlResponse(doc);
            }

            // Задержка между запросами, чтобы не нагружать сервер
            Thread.sleep(2000);

            // Если на странице меньше 10 результатов, значит, достигли конца
            if (getResultCount(body) < 10) break;
        }
    }

    private static void parseJsonResponse(String jsonBody) {
        // Простой парсинг JSON без дополнительных библиотек
        // Ищем блоки вида {"title":"...","url":"...","date":"..."}
        String[] items = jsonBody.split("\\{\"title\"");
        for (int i = 1; i < items.length; i++) {
            String item = items[i];
            try {
                String title = extractJsonValue(item, "title");
                String url = extractJsonValue(item, "url");
                String dateStr = extractJsonValue(item, "date");

                if (title.isEmpty() || url.isEmpty()) continue;

                // Формируем полный URL
                if (!url.startsWith("http")) {
                    url = "https://ec.europa.eu" + url;
                }

                // Парсим дату
                Timestamp publishedAt = parseDate(dateStr);

                // Загружаем полный текст речи
                String content = fetchFullContent(url);
                if (content == null || content.isEmpty()) continue;

                System.out.println("✅ " + publishedAt + " — " + title);

                StatementSaver.saveStatement(
                        PERSON_NAME.toLowerCase().replace(" ", "_"),
                        title,
                        content,
                        url,
                        SOURCE_NAME,
                        publishedAt
                );

                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка парсинга записи: " + e.getMessage());
            }
        }
    }

    private static void parseHtmlResponse(Document doc) {
        Elements items = doc.select("article.ec-document, div.view-content div.views-row");
        for (Element item : items) {
            try {
                Element link = item.selectFirst("a[href]");
                if (link == null) continue;

                String title = link.text().trim();
                String url = link.attr("href");
                if (!url.startsWith("http")) {
                    url = "https://ec.europa.eu" + url;
                }

                Element dateElem = item.selectFirst("time, .date, .published-date");
                String dateStr = dateElem != null ? dateElem.text().trim() : "";
                Timestamp publishedAt = parseDate(dateStr);

                String content = fetchFullContent(url);
                if (content == null || content.isEmpty()) continue;

                System.out.println("✅ " + publishedAt + " — " + title);

                StatementSaver.saveStatement(
                        PERSON_NAME.toLowerCase().replace(" ", "_"),
                        title,
                        content,
                        url,
                        SOURCE_NAME,
                        publishedAt
                );

                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.println("⚠️ Ошибка парсинга HTML-записи: " + e.getMessage());
            }
        }
    }

    private static String fetchFullContent(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(30000)
                    .get();

            // Селекторы для разных типов страниц Press Corner
            Element content = doc.selectFirst("div.field--name-field-text, div.ec-document__body, div.content");
            if (content == null) {
                content = doc.selectFirst("article, main, div.main-content");
            }

            if (content != null) {
                // Извлекаем текст из параграфов
                Elements paragraphs = content.select("p, div.ec-paragraph");
                StringBuilder sb = new StringBuilder();
                for (Element p : paragraphs) {
                    String text = p.text().trim();
                    if (!text.isEmpty() && text.length() > 20) {
                        sb.append(text).append("\n\n");
                    }
                }
                return sb.toString().trim();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Ошибка загрузки контента: " + url);
        }
        return null;
    }

    private static String extractJsonValue(String json, String key) {
        String pattern = "\"" + key + "\":\"";
        int start = json.indexOf(pattern);
        if (start == -1) {
            pattern = "\"" + key + "\":";
            start = json.indexOf(pattern);
            if (start == -1) return "";
            start += pattern.length();
            int end = json.indexOf(",", start);
            if (end == -1) end = json.indexOf("}", start);
            return json.substring(start, end).replace("\"", "").trim();
        }
        start += pattern.length();
        int end = json.indexOf("\"", start);
        if (end == -1) return "";
        return json.substring(start, end).trim();
    }

    private static int getResultCount(String jsonBody) {
        String pattern = "\"totalResults\":";
        int start = jsonBody.indexOf(pattern);
        if (start == -1) return 10; // По умолчанию
        start += pattern.length();
        int end = jsonBody.indexOf(",", start);
        if (end == -1) end = jsonBody.indexOf("}", start);
        try {
            return Integer.parseInt(jsonBody.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return 10;
        }
    }

    private static Timestamp parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return new Timestamp(System.currentTimeMillis());
        }

        // Пробуем разные форматы дат
        String[] patterns = {
                "yyyy-MM-dd",
                "dd/MM/yyyy",
                "dd MMM yyyy",
                "MMMM d, yyyy",
                "yyyy-MM-dd'T'HH:mm:ss"
        };

        for (String pattern : patterns) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
                LocalDate date = LocalDate.parse(dateStr.split(" ")[0], formatter);
                return Timestamp.valueOf(date.atStartOfDay());
            } catch (DateTimeParseException ignored) {}
        }

        return new Timestamp(System.currentTimeMillis());
    }
}