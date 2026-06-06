package nir.parsing;

import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ScholzSpeechScraper {

    public static void main(String[] args) throws InterruptedException {
        System.setProperty("webdriver.chrome.driver", "C:\\Program Files (x86)\\Google\\chromedriver-win64\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-position=-2500,800");
        options.addArguments("--window-size=1200,900");

        WebDriver driver = new ChromeDriver(options);

        // Главная страница архива
        String baseUrl = "https://www.bundesregierung.de/breg-en/service/archive?page=";
        int count = 1;
        driver.get(baseUrl+count);
        Thread.sleep(3000);

        boolean hasNextPage = true;

        while (hasNextPage) {
            // Прокрутка вниз, чтобы загрузились все записи
            for (int i = 0; i < 5; i++) {
                ((org.openqa.selenium.JavascriptExecutor) driver)
                        .executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(2000);
            }
            // 1) Получаем весь HTML страницы через Selenium
            String pageHtml = driver.getPageSource();

            // 2) Ищем в HTML JSON через регулярку
            Pattern pattern = Pattern.compile(
                    "BPA\\.initialSearchResultsJson\\s*=\\s*(\\{.*?\\});",
                    Pattern.DOTALL
            );
            Matcher matcher = pattern.matcher(pageHtml);

            if (!matcher.find()) {
                System.err.println("⚠️ JSON с речами не найден на странице!");
                driver.quit();
                return;
            }

            String jsonStr = matcher.group(1); // чистый JSON

            // 3) Парсим JSON
            JSONObject rootJson = new JSONObject(jsonStr);
            JSONObject result = rootJson.getJSONObject("result");
            JSONArray items = result.getJSONArray("items");

            System.out.println("✅ Найдено речей: " + items.length());

            for (int i = 0; i < items.length(); i++) {
                try {
                    JSONObject item = items.getJSONObject(i);
                    String payload = item.getString("payload");

                    Document doc = Jsoup.parse(payload);

                    // ===== ССЫЛКА =====
                    Element linkElem = doc.selectFirst("a[href]");
                    if (linkElem == null) {
                        System.err.println("⚠️ Пропущена запись: нет ссылки");
                        continue;
                    }
                    String speechUrl = linkElem.attr("href").trim();
                    if (!speechUrl.startsWith("http")) {
                        speechUrl = "https://www.bundesregierung.de" + speechUrl;
                    }

                    // ===== ТЕКСТ РЕЧИ =====
                    driver.get(speechUrl);
                    Thread.sleep(2500);

                    String pageHtmlGet = driver.getPageSource();
                    Document docGet = Jsoup.parse(pageHtmlGet);
                    Element content = docGet.selectFirst("div.bpa-article div.bpa-richtext");

                    Element transcriptLink = docGet.selectFirst(
                            "div.bpa-richtext h2:matches((?i)you can read a transcript) a[href]"
                    );
                    if (transcriptLink == null) {
                       transcriptLink = docGet.selectFirst(
                                "h2:matchesOwn((?i)you can read a transcript) a[href], " +
                                        "h3:matchesOwn((?i)you can read a transcript) a[href]"
                        );
                    }
                    String baseUrlGet = "https://www.bundesregierung.de";

                    if (transcriptLink != null) {
                        String transcriptUrl = transcriptLink.attr("href");
                        if (transcriptUrl.startsWith("/")) {
                            transcriptUrl = baseUrlGet + transcriptUrl;
                        }
                        System.out.println("➡ Найден transcript: " + transcriptUrl);

                        // грузим страницу транскрипта
                        driver.get(transcriptUrl);
                        Thread.sleep(2500);
                        speechUrl = transcriptUrl;

                        String transcriptHtml = driver.getPageSource();
                        Document transcriptDoc = Jsoup.parse(transcriptHtml);

                        Element transcriptContent =
                                transcriptDoc.selectFirst("div.bpa-article div.bpa-richtext");

                        if (transcriptContent != null) {
                            content = transcriptContent; // ⬅ ПЕРЕОПРЕДЕЛЯЕМ КОНТЕНТ
                        }
                    }

                    StringBuilder fullTextBuilder = new StringBuilder();

                    Elements elements = content.select("p, li, h2, h3");

                    for (Element el : elements) {
                        String text = el.text().trim();

                        // фильтруем мусор
                        if (text.isEmpty()) continue;
                        if (text.startsWith("Photo:")) continue;
                        if (text.equalsIgnoreCase("Share the article")) continue;

                        fullTextBuilder.append(text).append("\n\n");
                    }

                    String fullText = fullTextBuilder.toString().trim();

                    // ===== ЗАГОЛОВОК =====
                    Element titleElem = linkElem.selectFirst("h2");
                    if (titleElem == null) {
                        System.err.println("⚠️ Пропущена запись: нет заголовка");
                        continue;
                    }
                    String title = titleElem.text().trim();

                    // ===== ДАТА ПУБЛИКАЦИИ =====
                    Element timeElem = doc.selectFirst("time[datetime]");
                    Timestamp publishedAt;
                    if (timeElem != null) {
                        publishedAt = Timestamp.from(
                                java.time.Instant.parse(timeElem.attr("datetime"))
                        );
                    } else {
                        publishedAt = new Timestamp(System.currentTimeMillis());
                    }

                    System.out.println("🔹 " + publishedAt + " — " + title);
                    System.out.println("🔗 " + speechUrl);

                    // ===== СОХРАНЕНИЕ (НЕ ТРОГАЕМ StatementSaver) =====
                    StatementSaver.saveStatement(
                            "шольц",
                            title,
                            fullText,
                            speechUrl,
                            "Bundesregierung Archive",
                            publishedAt
                    );

                } catch (Exception e) {
                    System.err.println("⚠️ Ошибка при обработке речи: " + e.getMessage());
                }
            }

// Переход на следующую страницу
            try {
                count++;
                driver.get(baseUrl+count);
                Thread.sleep(3000);
            } catch (Exception e) {
                hasNextPage = false;
            }

        }
        driver.quit();
        System.out.println("🏁 Парсинг архива речей завершён.");
    }
}
