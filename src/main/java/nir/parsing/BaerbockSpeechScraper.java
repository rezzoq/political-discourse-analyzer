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

public class BaerbockSpeechScraper {

    public static void main(String[] args) throws Exception {

        System.setProperty("webdriver.chrome.driver",
                "C:\\Program Files (x86)\\Google\\chromedriver-win64\\chromedriver.exe");

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--window-position=-2500,800");
        options.addArguments("--window-size=1200,900");

        WebDriver driver = new ChromeDriver(options);
        String baseUrl = "https://www.auswaertiges-amt.de/de/search?search=baerbock";

        boolean hasNext = true;
        int page = 18;

        while (hasNext) {
            System.out.println("📄 Страница " + page);
            driver.get(baseUrl);
            Thread.sleep(4000);

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            // Находим кнопку «Ergebnisse einschränken»
            WebElement filterToggler = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button.c-cta--anchor-nav.is-search-toggler")
            ));
            // Скроллим к кнопке и кликаем
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", filterToggler);
            filterToggler.click();
            // Ждём пока панель откроется
            Thread.sleep(1000); // или можно использовать ExpectedConditions.visibilityOfElementLocated

            // Например, Pressemitteilungen
            WebElement pressCheckbox = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//label[contains(@class,'filter__item-container')][.//span[contains(text(),'Pressemitteilungen')]]")
            ));
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", pressCheckbox);
            pressCheckbox.click();
            // Остальные категории, если нужны, делаем аналогично
            WebElement articleCheckbox = driver.findElement(
                    By.xpath("//label[contains(@class,'filter__item-container')][.//span[contains(text(),'Interviews')]]")
            );
            articleCheckbox.click();
            WebElement speechCheckbox = driver.findElement(
                    By.xpath("//label[contains(@class,'filter__item-container')][.//span[contains(text(),'Reden')]]")
            );
            speechCheckbox.click();

            WebElement applyBtn = driver.findElement(By.cssSelector("button.form__btn-submit"));
            applyBtn.click();
            // Ждём, пока обновятся результаты
            Thread.sleep(2000); // лучше заменить на ожидание загрузки результатов

            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", filterToggler);
            filterToggler.click();
            // Ждём пока панель откроется
            Thread.sleep(1000); // или можно использовать ExpectedConditions.visibilityOfElementLocated

            // скроллим, чтобы всё прогрузилось
            for (int i = 0; i < 4; i++) {
                ((JavascriptExecutor) driver)
                        .executeScript("window.scrollTo(0, document.body.scrollHeight);");
                Thread.sleep(1500);
            }

            if (page > 1) {
                // ===== NEXT PAGE =====
                try {
                    for (int k = 2; k <= page; k++) {
                        // Находим ссылку на следующую страницу по data-page
                        WebElement nextPageLink = driver.findElement(By.xpath(
                                "//a[@class='pagination__list-link' and @data-page='" + k + "']"
                        ));

                        ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView(true);", nextPageLink);
                        nextPageLink.click();

                        // Ждём загрузки новой страницы
                        Thread.sleep(2500); // или лучше через ExpectedConditions
                    }
                } catch (NoSuchElementException e) {
                    hasNext = false; // больше страниц нет
                }
            }
            page++;

            Document doc = Jsoup.parse(driver.getPageSource());

            Elements items = doc.select("li.search-results__item");
            System.out.println("🔎 Найдено записей: " + items.size());

            for (Element item : items) {
                try {
                    // ===== TITLE + URL =====
                    Element link = item.selectFirst("a.search-results__item-title");
                    if (link == null) continue;

                    String title = link.text().trim();
                    String url = link.attr("href");

                    if (!url.startsWith("http")) {
                        url = "https://www.auswaertiges-amt.de" + url;
                    }

                    // ===== DATE =====
                    Element meta = item.selectFirst("span.search-results__item-meta-text");
                    Timestamp publishedAt = new Timestamp(System.currentTimeMillis());

                    if (meta != null) {
                        String rawDate = meta.text().split("-")[0].trim(); // 11.11.2025
                        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
                        LocalDate date = LocalDate.parse(rawDate, fmt);
                        publishedAt = Timestamp.valueOf(date.atStartOfDay());
                    }

                    // ===== LOAD ARTICLE =====
                    driver.get(url);
                    Thread.sleep(3000);

                    Document articleDoc = Jsoup.parse(driver.getPageSource());
                    Element content = articleDoc.selectFirst(
                            "div.c-rte--default, div[data-css=c-rte]"
                    );

                    if (content == null) {
                        System.out.println("⚠ Нет контента: " + url);
                        continue;
                    }

                    StringBuilder textBuilder = new StringBuilder();

                    Elements blocks = content.select(
                            "p.rte__paragraph, h2.rte__heading2, li.rte__list-item, div.c-quote--default p, blockquote p"
                    );

                    for (Element b : blocks) {
                        String t = b.text().trim();

                        if (t.isEmpty()) continue;
                        if (t.length() < 40) continue;

                        textBuilder.append(t).append("\n\n");
                    }

                    String text = textBuilder.toString().trim();


                    System.out.println("✅ " + publishedAt + " — " + title);

                    // ===== SAVE =====
                    StatementSaver.saveStatement(
                            "baerbock",
                            title,
                            text,
                            url,
                            "Auswärtiges Amt",
                            publishedAt
                    );

                } catch (Exception e) {
                    System.err.println("⚠ Ошибка записи: " + e.getMessage());
                }
            }
        }

        driver.quit();
        System.out.println("🏁 Парсинг завершён.");
    }
}

