import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.hc.client5.http.fluent.Request;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class NewspaperWebScraper {

    private static final String BROWSERSTACK_URL = "https://sanketparasher_jfK3Sm:uq7qkA1HunGzesPhdPWa@hub-cloud.browserstack.com/wd/hub";
    private static final String GOOGLE_TRANSLATE_API_KEY = "AIzaSyAxo0qHRN3LgFLYhbcOafRtWgoesjCivN4";
    private static final List<String> originalTitles = new ArrayList<>();
    private static final List<String> translatedTitles = new ArrayList<>();
    private static final List<String> repeatedWords = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        // Start local test
        runLocally();

        // Run cross-browser test
        runOnBrowserStack();
        //BrowserStackParallelTest.runParallelTests();
    }

    /**
     * Run the scraper locally using ChromeDriver.
     */
    private static void runLocally() throws TimeoutException {
        //System.setProperty("webdriver.chrome.driver", "path_to_chromedriver");

        ChromeOptions options = new ChromeOptions();
       
        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();       
        try {
            driver.get("https://elpais.com/");
            Thread.sleep(3000);
            
            // Accepting cookies 
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(10));
            
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("didomi-notice-agree-button")));
			driver.findElement(By.id("didomi-notice-agree-button")).click();
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("html")));
            String lang = driver.findElement(By.tagName("html")).getAttribute("lang");
            
            // Check if we need to switch to Spanish
            if (!"es".equals(lang)) {
                // Try to find and click language selector if available
                try {
                    WebElement langSelector = driver.findElement(By.cssSelector("[data-lang='es'], a[href*='/espana/']"));
                    langSelector.click();
                    Thread.sleep(2000); // Wait for language change
                } catch (NoSuchElementException e) {
                    System.out.println("Warning: Could not find language selector, continuing anyway...");
                }
            }

            // Verify we're on the Spanish version of the site by checking URL or content
            String currentUrl = driver.getCurrentUrl();
            if (!currentUrl.contains("elpais.com")) {
                throw new RuntimeException("Failed to access El País website");
            }

            // Trying multiple times to locate the element
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("html")));
            
            // Try different methods to find the Opinion section
            try {
                // Method 1: Try direct link text
                WebElement opinionLink = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[contains(text(), 'Opinión') or contains(text(), 'OPINIÓN')]")));
                opinionLink.click();
            } catch (Exception e1) {
                try {
                    // Method 2: Try using partial link text
                    WebElement opinionLink = wait.until(ExpectedConditions.elementToBeClickable(
                        By.partialLinkText("OPINIÓN")));
                    opinionLink.click();
                } catch (Exception e2) {
                    try {
                        // Method 3: Try using href attribute
                        WebElement opinionLink = wait.until(ExpectedConditions.elementToBeClickable(
                            By.cssSelector("a[href*='/opinion']")));
                        opinionLink.click();
                    } catch (Exception e3) {
                        // If all methods fail, try navigating directly to the opinion URL
                        driver.get("https://elpais.com/opinion/");
                    }
                }
            }

            // Wait for the opinion page to load
            Thread.sleep(3000);

            // Verify we're on the opinion page
            wait.until(ExpectedConditions.urlContains("/opinion"));

            
           
            // Fetch the first 5 articles
            List<WebElement> articles = driver.findElements(By.cssSelector("article.c"));
            int articleCount = Math.min(5, articles.size());

            for (int i = 0; i < articleCount; i++) {
                WebElement article = articles.get(i);
                String title = article.getText().trim();
                originalTitles.add(title);
                System.out.println("📌 **Article " + (i + 1) + " Title (Spanish):** " + title);

                // Open article in a new tab
                String articleUrl = article.getAttribute("href");
                //driver.get(articleUrl);
                Thread.sleep(2000);

                // Fetch and print article content
                try {
                    
                    // Try multiple selectors in sequence until we find content
                    WebElement contentElement = null;
                    String[] possibleSelectors = {
                        ".article_body",  // Try main container first
                        "[data-dtm-region='articulo_cuerpo']",  // Alternative container
                        ".articulo-cuerpo",  // Another possible container
                        "#cuerpo_noticia"  // Legacy selector
                    };
                    
                    for (String selector : possibleSelectors) {
                        try {
                            contentElement = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
                            if (contentElement != null && !contentElement.getText().trim().isEmpty()) {
                                break;  // Found valid content, exit loop
                            }
                        } catch (Exception ignored) {
                            // Continue to next selector
                        }
                    }
                    
                    if (contentElement != null) {
                        // Get the text content
                        String content = contentElement.getText().trim();
                        System.out.println("📝 Content: " + content);
                    } else {
                        System.out.println("❗ Could not locate article content with any known selector.");
                    }
                    
                } catch (Exception e) {
                    System.out.println("❗ Error fetching article content: " + e.getMessage());
                }

                // Save cover image if available
              try {
                    WebElement imageElement = driver.findElement(By.cssSelector("article figure img, .foto img, .articulo-multimedia img"));
                    String imageUrl = imageElement.getAttribute("src");
                    if (imageUrl != null && !imageUrl.isEmpty()) {
                        byte[] imageBytes = Request.get(imageUrl)
                            .execute()
                            .returnContent()
                            .asBytes();
                        Files.write(Paths.get("images/article_" + (i + 1) + ".jpg"), imageBytes);
                        System.out.println("✅ Image saved for article: " + (i + 1));
                    }
                } catch (NoSuchElementException e) {
                    System.out.println("❗ No image found for article: " + title);
                }
             
                try {
                    // Open Google Translate in a new tab
                    ((JavascriptExecutor) driver).executeScript("window.open('about:blank', '_blank');");
                    ArrayList<String> tabs = new ArrayList<>(driver.getWindowHandles());
                    driver.switchTo().window(tabs.get(1)); // Switch to the new tab

                    // Navigate to Google Translate
                    String googleTranslateUrl = "https://translate.google.com/?sl=es&tl=en&text=" + 
                                                URLEncoder.encode(title, StandardCharsets.UTF_8);
                    driver.get(googleTranslateUrl);
                    Thread.sleep(3000); // Wait for translation to load

                    // Wait for the translated text to load
                    WebElement translationElement = wait.until(ExpectedConditions.presenceOfElementLocated(
                        By.cssSelector("span[jsname='W297wb']")
                    ));

                    // Fetch translated text
                    String translatedTitle = translationElement.getText().trim();
                    translatedTitles.add(translatedTitle);
                    System.out.println("🔄 **Translated Title (English):** " + translatedTitle);

                    // Close the Google Translate tab and switch back to the main tab
                    driver.close();
                    driver.switchTo().window(tabs.get(0)); // Switch back to the main tab
                    Thread.sleep(2000); // Ensure proper tab switch
                } catch (Exception e) {
                    System.out.println("❗ Translation failed for article: " + title);
                    translatedTitles.add("Translation failed");
                    driver.close();
                    driver.switchTo().window(driver.getWindowHandles().iterator().next());
                }
 
            }

            // Analyze repeated words in translated titles
            analyzeRepeatedWords();

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    /**
     * Run the scraper on BrowserStack with cross-browser testing.
     */
    private static void runOnBrowserStack() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(5);

        String[][] configurations = {
            {"Chrome", "latest", "Windows", "10"},
            {"Firefox", "latest", "Windows", "10"},
            {"Safari", "latest", "OS X", "Ventura"},
            {"Edge", "latest", "Windows", "11"},
            {"Samsung Galaxy S21", "Android", "11", "realMobile"}
        };

        for (String[] config : configurations) {
            executorService.submit(() -> {
                WebDriver driver = null;
                try {
                    DesiredCapabilities caps = new DesiredCapabilities();
                    caps.setCapability("browserName", config[0]);
                    caps.setCapability("browserVersion", config[1]);
                    caps.setCapability("os", config[2]);
                    caps.setCapability("osVersion", config[3]);
                    caps.setCapability("name", "ElPais CrossBrowser Test");

                    driver = new RemoteWebDriver(new URL(BROWSERSTACK_URL), caps);
                    driver.get("https://elpais.com/");
                    Thread.sleep(3000);

                    driver.findElement(By.linkText("Opinión")).click();
                    Thread.sleep(2000);

                    List<WebElement> articles = driver.findElements(By.cssSelector(".c_titulo a"));
                    for (int i = 0; i < Math.min(5, articles.size()); i++) {
                        System.out.println("🌐 **BrowserStack - Article Title:** " + articles.get(i).getText());
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (driver != null) driver.quit();
                }
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.MINUTES);
    }

    /**
     * Translate text using Google Translate API.
     */
    private static String translateToEnglish(String text) throws IOException {
        String apiUrl = "https://translation.googleapis.com/language/translate/v2";
        String jsonResponse = Request.post(apiUrl)
                .bodyString("{\"q\":\"" + text + "\",\"target\":\"en\"}", org.apache.hc.core5.http.ContentType.APPLICATION_JSON)
                .addHeader("Authorization", "Bearer " + GOOGLE_TRANSLATE_API_KEY)
                .execute().returnContent().asString();

        JsonObject jsonObject = JsonParser.parseString(jsonResponse).getAsJsonObject();
        return jsonObject.getAsJsonObject("data").getAsJsonArray("translations")
                .get(0).getAsJsonObject().get("translatedText").getAsString();
    }

    
    private static void analyzeRepeatedWords() {
        // Create list to store all words
        List<String> allWords = new ArrayList<>();
        
        // Process all titles and extract words
        for (String title : translatedTitles) {
            String cleanTitle = title.toLowerCase().replaceAll("[^a-zA-Z\\s]", "");
            String[] words = cleanTitle.split("\\s+");
            
            // Add words with length > 2 to the list
            for (String word : words) {
                if (word.length() > 2) {
                    allWords.add(word);
                }
            }
        }
        
        // Sort words to group identical words together
        Collections.sort(allWords);
        
        // Clear previous results
        repeatedWords.clear();
        
        // Count word frequencies using a single pass
        if (!allWords.isEmpty()) {
            String currentWord = allWords.get(0);
            int count = 1;
            
            // Create temporary list for storing word-count pairs
            List<WordCount> wordCounts = new ArrayList<>();
            
            // Count occurrences
            for (int i = 1; i < allWords.size(); i++) {
                if (allWords.get(i).equals(currentWord)) {
                    count++;
                } else {
                    if (count > 2) {
                        wordCounts.add(new WordCount(currentWord, count));
                    }
                    currentWord = allWords.get(i);
                    count = 1;
                }
            }
            
            // Check the last word
            if (count > 2) {
                wordCounts.add(new WordCount(currentWord, count));
            }
            
            // Sort by frequency (descending)
            wordCounts.sort((a, b) -> {
                int compareCount = Integer.compare(b.count, a.count);
                return compareCount != 0 ? compareCount : a.word.compareTo(b.word);
            });
            
            // Format and add results
            for (WordCount wc : wordCounts) {
                String formattedResult = String.format("Word: '%-20s' | Occurrences: %d", 
                                                     wc.word, 
                                                     wc.count);
                repeatedWords.add(formattedResult);
                System.out.println(formattedResult);
            }
            
            // Print summary
            System.out.println("\n📊 Word Frequency Analysis Summary:");
            System.out.println("Total unique repeated words: " + repeatedWords.size());
        } else {
            System.out.println("No words were repeated more than twice in the titles.");
        }
    }

    /**
     * Helper class to store word and count pairs
     */
    private static class WordCount {
        String word;
        int count;
        
        WordCount(String word, int count) {
            this.word = word;
            this.count = count;
        }
    }
}
