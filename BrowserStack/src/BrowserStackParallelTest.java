import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

public class BrowserStackParallelTest {
    // Replace with your actual BrowserStack credentials
    private static final String BROWSERSTACK_USERNAME = "sanketparasher_jfK3Sm";
    private static final String BROWSERSTACK_ACCESS_KEY = "uq7qkA1HunGzesPhdPWa";
    private static final String BROWSERSTACK_URL = "https://" + BROWSERSTACK_USERNAME + ":" + BROWSERSTACK_ACCESS_KEY + "@hub-cloud.browserstack.com/wd/hub";
    private static final List<String> testResults = new ArrayList<>();
    
    // Method to validate credentials
    private static boolean validateCredentials() {
        return !BROWSERSTACK_USERNAME.equals("your_username") && !BROWSERSTACK_ACCESS_KEY.equals("your_access_key");
    }
    
    static class BrowserConfig {
        String browser;
        String version;
        String os;
        String osVersion;
        boolean isMobile;
        String device;
        
        BrowserConfig(String browser, String version, String os, String osVersion, boolean isMobile, String device) {
            this.browser = browser;
            this.version = version;
            this.os = os;
            this.osVersion = osVersion;
            this.isMobile = isMobile;
            this.device = device;
        }
    }
    
    public static void main(String[] args) {
        if (!validateCredentials()) {
            System.out.println("❌ Error: Please update BROWSERSTACK_USERNAME and BROWSERSTACK_ACCESS_KEY with your actual credentials");
            System.out.println("You can find them at: https://www.browserstack.com/accounts/settings");
            return;
        }
        
        System.out.println("🚀 Starting BrowserStack Parallel Tests...");
        runParallelTests();
    }
    
    public static void runParallelTests() {
        List<BrowserConfig> configs = new ArrayList<>();
        
        // Desktop configurations
        configs.add(new BrowserConfig("Chrome", "latest", "Windows", "11", false, null));
        configs.add(new BrowserConfig("Firefox", "latest", "Windows", "10", false, null));
        configs.add(new BrowserConfig("Edge", "latest", "Windows", "11", false, null));
        configs.add(new BrowserConfig("Safari", "latest", "OS X", "Ventura", false, null));
        
        // Mobile configuration - try iPhone first, fallback to Android if needed
        try {
            configs.add(new BrowserConfig("safari", "latest", "ios", "16", true, "iPhone 14"));
        } catch (Exception e) {
            // If iPhone doesn't work, try Android
            configs.add(new BrowserConfig("chrome", "latest", "android", "11.0", true, "Samsung Galaxy S21"));
        }
        
        // Alternative: If you want to skip mobile testing entirely, comment out the mobile config above
        // and uncomment this line:
        // configs.add(new BrowserConfig("Chrome", "latest", "OS X", "Monterey", false, null));
        
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        for (BrowserConfig config : configs) {
            executor.submit(() -> runTest(config));
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.MINUTES)) {
                System.out.println("⚠️ Tests didn't complete within 30 minutes, forcing shutdown...");
                executor.shutdownNow();
            }
            printTestResults();
        } catch (InterruptedException e) {
            System.out.println("❌ Parallel execution interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }
    }
    
    private static void runTest(BrowserConfig config) {
        WebDriver driver = null;
        String threadId = Thread.currentThread().getName();
        
        try {
            DesiredCapabilities caps = new DesiredCapabilities();
            
            // Debug: Print the configuration being used
            logResult(String.format("🔧 Configuring: %s %s on %s %s (Mobile: %s)", 
                config.browser, config.version, config.os, config.osVersion, config.isMobile), threadId);
            
            if (config.isMobile) {
                // Mobile specific capabilities - simplified for BrowserStack
                caps.setCapability("device", config.device);
                caps.setCapability("os_version", config.osVersion);
                caps.setCapability("browserName", config.browser);
                caps.setCapability("real_mobile", "true");
            } else {
                // Desktop specific capabilities
                caps.setCapability("browserName", config.browser);
                caps.setCapability("browserVersion", config.version);
                caps.setCapability("os", config.os);
                caps.setCapability("os_version", config.osVersion);
            }
            
            // Common capabilities
            caps.setCapability("project", "ElPais Parallel Test");
            caps.setCapability("build", "Build 1.0");
            caps.setCapability("name", String.format("ElPais Test - %s on %s %s", 
                config.browser, config.isMobile ? config.device : config.os, config.osVersion));
            caps.setCapability("browserstack.debug", "true");
            caps.setCapability("browserstack.console", "info");
            caps.setCapability("browserstack.networkLogs", "true");
            
            logResult(String.format("🚀 Starting test on %s - %s %s", 
                config.browser, config.os, config.osVersion), threadId);
            
            // Add connection timeout
            driver = new RemoteWebDriver(new URL(BROWSERSTACK_URL), caps);
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            
            // Navigate to El País
            logResult("🌐 Navigating to El País website...", threadId);
            driver.get("https://elpais.com/");
            Thread.sleep(3000);
            
            // Accept cookies if present
            try {
                WebElement cookieButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.id("didomi-notice-agree-button")));
                cookieButton.click();
                logResult("🍪 Cookie notice accepted", threadId);
            } catch (Exception e) {
                logResult("⚠️ Cookie notice not found or already accepted", threadId);
            }
            
            // Navigate to Opinion section
            logResult("📰 Navigating to Opinion section...", threadId);
            try {
                WebElement opinionLink = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[contains(text(), 'Opinión') or contains(text(), 'OPINIÓN')]")
                ));
                opinionLink.click();
            } catch (Exception e1) {
                try {
                    driver.get("https://elpais.com/opinion/");
                    logResult("📍 Direct navigation to Opinion section", threadId);
                } catch (Exception e2) {
                    logResult("❌ Failed to navigate to Opinion section", threadId);
                    throw e2;
                }
            }
            
            // Verify opinion page loaded
            wait.until(ExpectedConditions.urlContains("/opinion"));
            logResult("✅ Opinion page loaded successfully", threadId);
            
            // Get article titles
            List<WebElement> articles = driver.findElements(By.cssSelector("article"));
            if (articles.isEmpty()) {
                // Try alternative selectors
                articles = driver.findElements(By.cssSelector("h2 a, h3 a"));
            }
            
            int articleCount = Math.min(5, articles.size());
            logResult(String.format("📊 Found %d articles", articleCount), threadId);
            
            for (int i = 0; i < articleCount; i++) {
                try {
                    String title = articles.get(i).getText().trim();
                    if (!title.isEmpty()) {
                        logResult(String.format("📰 Article %d: %s", (i + 1), title), threadId);
                    }
                } catch (Exception e) {
                    logResult(String.format("⚠️ Could not extract title for article %d", (i + 1)), threadId);
                }
            }
            
            logResult(String.format("✅ Test completed successfully on %s - %s %s", 
                config.browser, config.os, config.osVersion), threadId);
                
        } catch (Exception e) {
            logResult(String.format("❌ Test failed on %s - %s %s: %s", 
                config.browser, config.os, config.osVersion, e.getMessage()), threadId);
            e.printStackTrace();
        } finally {
            if (driver != null) {
                try {
                    driver.quit();
                } catch (Exception e) {
                    logResult("⚠️ Error closing driver: " + e.getMessage(), threadId);
                }
            }
        }
    }
    
    private static void logResult(String message, String threadId) {
        synchronized (testResults) {
            String timestamp = java.time.LocalTime.now().toString();
            testResults.add(String.format("[%s] [%s] %s", timestamp, threadId, message));
            System.out.println(String.format("[%s] [%s] %s", timestamp, threadId, message));
        }
    }
    
    private static void printTestResults() {
        System.out.println("\n📊 Test Execution Summary:");
        System.out.println("=========================");
        
        long successCount = testResults.stream().filter(r -> r.contains("✅ Test completed successfully")).count();
        long failCount = testResults.stream().filter(r -> r.contains("❌ Test failed")).count();
        
        System.out.println(String.format("✅ Successful tests: %d", successCount));
        System.out.println(String.format("❌ Failed tests: %d", failCount));
        System.out.println(String.format("📋 Total tests: %d", successCount + failCount));
        
        System.out.println("\n📝 Detailed Results:");
        System.out.println("===================");
        for (String result : testResults) {
            System.out.println(result);
        }
    }
}