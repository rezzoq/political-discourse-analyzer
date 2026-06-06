package nir.parsing;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TwitterScrapper {

    public static void main(String[] args) throws InterruptedException {
        // Укажи путь к chromedriver, если не в PATH
        System.setProperty("webdriver.chrome.driver", "C:\\Program Files (x86)\\Google\\chromedriver-win64\\chromedriver.exe");

        LocalDate start = LocalDate.of(2023, 1, 1);
        LocalDate end = LocalDate.now();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String[] names = {
                "Annalena Baerbock",
                "Foreign Minister Baerbock",
                "German Foreign Minister Baerbock",
                "Außenministerin Baerbock"
        };

        // Идём по неделям
        LocalDate currentStart = start;
        while (currentStart.isBefore(end)) {
            LocalDate currentEnd = currentStart.plusDays(6);
            if (currentEnd.isAfter(end)) {
                currentEnd = end;
            }

            System.out.println("🔹 Парсинг с " + currentStart + " по " + currentEnd);


            ChromeOptions options = new ChromeOptions();
            options.addArguments("--window-position=-2500,0");
            options.addArguments("--window-size=1200,900");

            WebDriver driver = new ChromeDriver(options);

            driver.get("https://x.com/");
            Thread.sleep(3000);

            // 🔑 ВАЖНЫЕ КУКИ (вставь свои значения!)
            driver.manage().addCookie(new Cookie("auth_token", "0980d775ad10ad1e21873459c1e2b9b76a93305b"));
            driver.manage().addCookie(new Cookie("ct0", "f5393e4790a480db873208b90ba9164024e74038388740be4e93a309ead44d6794d5d1c6e4c9abd332f9c7ac4ea90c8e2f36aeb83a6af8e5b26d938ba14df0bf99f5da3577cb144d4ec22946e6eba158"));
            driver.manage().addCookie(new Cookie("twid", "u%3D1323730772411486209"));
            driver.navigate().refresh();
            Thread.sleep(3000);

            for (String name : names) {
                String rawQuery = name
                        + " since:" + currentStart.format(formatter)
                        + " until:" + currentEnd.format(formatter);

                String encodedQuery = URLEncoder.encode(rawQuery, StandardCharsets.UTF_8);

                String url = "https://x.com/search?q=" + encodedQuery
                        + "&src=typed_query&f=live";


                driver.get(url);
                Thread.sleep(5000);

                // подождем загрузки

                // Прокручиваем вниз, чтобы подгрузить больше твитов
                for (int i = 0; i < 10; i++) {
                    ((org.openqa.selenium.JavascriptExecutor) driver).executeScript("window.scrollTo(0, document.body.scrollHeight);");
                    Thread.sleep(3000);
                }

                List<WebElement> tweets = driver.findElements(By.xpath("//article"));

                System.out.println("✅ Найдено твитов: " + tweets.size());

                for (WebElement tweet : tweets) {
                    try {
                        String text = tweet.getText();
                        String tweetUrl = tweet.findElement(By.xpath(".//a[contains(@href, '/status/')]")).getAttribute("href");

                        Timestamp now = new Timestamp(System.currentTimeMillis());

                        System.out.println("🟦 " + tweetUrl + " — " + text);
                        StatementSaver.saveStatement("баербок", "Твит", text, tweetUrl, "Twitter (scraper)", now);

                    } catch (Exception e) {
                        System.out.println("⚠️ Ошибка парсинга одного твита.");
                    }
                }
            }
            driver.quit();
            currentStart = currentEnd.plusDays(1);
        }
    }
}

