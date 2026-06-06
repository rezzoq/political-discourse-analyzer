package nir.parsing;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.NoSuchElementException;

public class VonDerLeyenScraper {

    private static final String PERSON_NAME = "ursula_von_der_leyen";
    private static final String SOURCE_NAME = "European Commission Press Corner";

    private static final String START_URL = "https://ec.europa.eu/commission/presscorner/home/en" +
            "?keywords=von%20der%20Leyen" +
            "&dotyp=IP,SPEECH,STATEMENT" +
            "&commissioner=881" +
            "&pageNumber=1"; // без #news-block, чтобы Angular нормально инициализировался

    public static void main(String[] args) throws Exception {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-position=200,800");
        options.addArguments("--window-size=1700,1200");
        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));

        try {
            Set<String> processedUrls = new LinkedHashSet<>();
            driver.get(START_URL);
            String mainHandle = driver.getWindowHandle();

            // Закрываем куки-баннер (более надёжный вариант)
            closeCookieBanner(driver);

            // Ждём, пока Angular отрендерит список и ссылки станут видимыми
            System.out.println("⏳ Ожидание загрузки результатов...");
            wait.until(ExpectedConditions.visibilityOfElementLocated(
                    By.cssSelector("#news-block a.ecl-list-item__link")));
            System.out.println("✔️ Первая страница загружена.");

            int pageNum = 1;
            while (true) {
                // Собираем ссылки с детальных страниц
                Set<String> pageUrls = new LinkedHashSet<>();
                List<WebElement> linkElements = driver.findElements(
                        By.cssSelector("#news-block a.ecl-list-item__link"));
                for (WebElement link : linkElements) {
                    String href = link.getAttribute("href");
                    if (href != null && href.contains("/detail/")) {
                        pageUrls.add(href);
                    }
                }

                System.out.println("📦 Страница " + pageNum + " — найдено уникальных ссылок: " + pageUrls.size());
                if (pageUrls.isEmpty()) {
                    System.out.println("⚠ Ссылок не найдено. Вывожу фрагмент HTML #news-block:");
                    WebElement nb = driver.findElement(By.id("news-block"));
                    System.out.println(nb.getAttribute("innerHTML").substring(0, Math.min(2000, nb.getAttribute("innerHTML").length())));
                    break;
                }

                // Обработка каждой детальной страницы
                for (String detailUrl : pageUrls) {
                    if (processedUrls.contains(detailUrl)) {
                        System.out.println("⏭️ Уже обработано: " + detailUrl);
                        continue;
                    }
                    processedUrls.add(detailUrl);

                    System.out.println("➡️ Загружаем: " + detailUrl);
                    driver.switchTo().newWindow(WindowType.TAB);
                    String tabHandle = driver.getWindowHandle();
                    try {
                        driver.get(detailUrl);
                        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));
                        Thread.sleep(1500);

                        Document doc = Jsoup.parse(driver.getPageSource());
                        if (driver.getTitle().toLowerCase().contains("404") ||
                                driver.getTitle().toLowerCase().contains("error")) {
                            System.out.println("⚠ Ошибка на странице (пропускаем)");
                            continue;
                        }

                        String path = detailUrl.substring(detailUrl.lastIndexOf('/') + 1);
                        String docType = path.contains("_") ? path.split("_")[0].toUpperCase() : "";

                        String title = doc.title().replace(" - European Commission", "").trim();
                        if (title.isEmpty()) title = "Untitled";

                        // Новый, более умный фильтр:
                        String lowerTitle = title.toLowerCase();
                        boolean isSpeechOrStatement = docType.equals("SPEECH") || docType.equals("STATEMENT");
// Для речей и заявлений сайт уже отфильтровал по commissioner=881 — это все её
// Для пресс-релизов (IP) оставляем только те, где упоминается фон дер Ляйен
                        if (!isSpeechOrStatement) { // это IP
                            if (!lowerTitle.contains("von der leyen")) {
                                System.out.println("⏭️ Пропущено (пресс-релиз без упоминания von der Leyen): " + title);
                                continue;
                            }
                        }

                        LocalDate eventDate = extractDateFromDoc(doc);
                        if (eventDate == null || eventDate.isAfter(LocalDate.now())) {
                            System.out.println("⏭️ Пропущено (некорректная/будущая дата)");
                            continue;
                        }

                        String text = extractCleanContent(doc);
                        if (text.length() < 100) {
                            System.out.println("⚠ Слишком короткий текст: " + detailUrl);
                            continue;
                        }

                        Timestamp timestamp = Timestamp.valueOf(eventDate.atStartOfDay());
                        System.out.println("✅ " + timestamp + " — " + title);
                        StatementSaver.saveStatement(PERSON_NAME, title, text, detailUrl, SOURCE_NAME, timestamp);

                    } catch (Exception e) {
                        System.err.println("⚠ Ошибка при обработке: " + e.getMessage());
                    } finally {
                        try { driver.switchTo().window(tabHandle).close(); } catch (NoSuchWindowException ignored) {}
                        try { driver.switchTo().window(mainHandle); } catch (NoSuchWindowException ex) {
                            System.err.println("⚠ Главное окно потеряно, завершение.");
                            return;
                        }
                        Thread.sleep(500);
                    }
                }

                // Пагинация
                if (!goToNextPage(driver, wait)) {
                    System.out.println("🏁 Пагинация завершена.");
                    break;
                }
                pageNum++;
            }
        } finally {
            driver.quit();
            System.out.println("\n🏁 Парсинг завершён.");
        }
    }

    private static boolean goToNextPage(WebDriver driver, WebDriverWait wait) {
        try {
            WebElement nextButton = driver.findElement(
                    By.cssSelector("a.ecl-pager__link[title='Go to next page']"));
            ((JavascriptExecutor) driver).executeScript(
                    "arguments[0].scrollIntoView({block: 'center'});", nextButton);
            Thread.sleep(500);

            if (nextButton.isDisplayed() && nextButton.isEnabled()) {
                // Сохраняем старый список для staleness
                List<WebElement> oldLinks = driver.findElements(
                        By.cssSelector("#news-block a.ecl-list-item__link"));
                nextButton.click();
                if (!oldLinks.isEmpty()) {
                    wait.until(ExpectedConditions.stalenessOf(oldLinks.get(0)));
                }
                // Ждём появления новых видимых ссылок
                wait.until(ExpectedConditions.visibilityOfElementLocated(
                        By.cssSelector("#news-block a.ecl-list-item__link")));
                return true;
            }
        } catch (NoSuchElementException e) {
            System.out.println("Кнопка Next не найдена.");
        } catch (TimeoutException e) {
            System.out.println("Timeout при ожидании обновления страницы.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return false;
    }

    private static void closeCookieBanner(WebDriver driver) {
        try {
            // Ищем кнопку "Accept all cookies" по тексту
            WebElement acceptBtn = driver.findElement(
                    By.xpath("//a[contains(text(),'Accept all cookies')]"));
            if (acceptBtn.isDisplayed()) {
                acceptBtn.click();
                System.out.println("🍪 Куки-баннер закрыт.");
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            // не страшно
        }
    }

    private static LocalDate extractDateFromDoc(Document doc) {
        try {
            Elements timeElements = doc.select("time[datetime]");
            if (!timeElements.isEmpty()) {
                String dt = timeElements.first().attr("datetime");
                String datePart = dt.contains("T") ? dt.split("T")[0] : dt;
                return LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE);
            }
        } catch (Exception e) { }
        for (Element meta : doc.select("meta[name='date'], meta[property='article:published_time']")) {
            String content = meta.attr("content");
            if (content != null && !content.isEmpty()) {
                String datePart = content.contains("T") ? content.split("T")[0] : content;
                try { return LocalDate.parse(datePart, DateTimeFormatter.ISO_LOCAL_DATE); } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private static String extractCleanContent(Document doc) {
        doc.select("header, footer, nav, .ecl-site-header, .ecl-site-footer, " +
                ".ecl-breadcrumb, .ecl-language-selector, .ecl-social-media-share, " +
                "#cookie-consent-banner, script, style, .wt-cck--container, .wt-globan--container").remove();

        Elements blocks = doc.select(
                "div.ecl-content-block__description, div.ecl-paragraph, " +
                        "div.ecl-editor__body, div.field--name-body, article.ecl-article-body");
        if (blocks.isEmpty()) blocks = doc.select("main div.ecl");
        if (blocks.isEmpty()) blocks = doc.select("main");

        StringBuilder sb = new StringBuilder();
        for (Element block : blocks) {
            for (Element el : block.select("p, h2, h3, h4, blockquote")) {
                String text = el.text().trim();
                if (text.length() > 30) sb.append(text).append("\n\n");
            }
        }
        return sb.toString().trim();
    }
}