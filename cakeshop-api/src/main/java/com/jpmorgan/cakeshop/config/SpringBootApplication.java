package com.jpmorgan.cakeshop.config;

import com.jpmorgan.cakeshop.bean.GethConfigBean;
import com.jpmorgan.cakeshop.util.FileUtils;
import com.jpmorgan.cakeshop.util.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.embedded.jetty.JettyServletWebServerFactory;
import org.springframework.boot.web.server.ErrorPage;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Configuration
@EnableAutoConfiguration
@ComponentScan(basePackages = "com.jpmorgan.cakeshop")
@Profile("spring-boot")
public class SpringBootApplication {

    public static void main(String[] args) {
        // setup configs
        WebAppInit.setLoggingPath(true);
        String configDir = FileUtils.expandPath(SystemUtils.USER_DIR, "data");
        System.setProperty("eth.config.dir", configDir);

        // default to 'local' spring profile
        // this determines which application-${profile}.properties file to load
        if (StringUtils.isBlank(System.getProperty("spring.profiles.active"))) {
            System.out.println("Defaulting to spring profile: local");
            System.setProperty("spring.profiles.active", "local");
        }

        if (StringUtils.isNotBlank(System.getProperty("spring.profiles.active"))) {
            System.setProperty("spring.config.location", "file:" + FileUtils.expandPath(AppConfig.getConfigPath(), "application.properties"));
        }

        // extract geth from WAR (if necessary)
        try {
            extractGeth(configDir);
        } catch (IOException e) {
            System.err.println("!!! ERROR: Failed to extract geth from WAR package");
            System.err.println(e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        if (args.length > 0 && StringUtils.isNotBlank(args[0]) && args[0].equalsIgnoreCase("init")) {
            // yep, it's a global. dealwithit.jpg
            System.setProperty("geth.init.only", "true");
        }

        if (args.length > 0 && StringUtils.isNotBlank(args[0]) && args[0].equalsIgnoreCase("example")) {
            System.setProperty("geth.init.example", "true");
        }

        // boot app
        new SpringApplicationBuilder(SpringBootApplication.class)
                .profiles("container", "spring-boot")
                .run(args);
    }

    private static void extractGeth(String configDir) throws IOException {
        URL url = GethConfigBean.class.getClassLoader().getResource("");
        String warUrl = null;

        if (url.getProtocol().equals("jar")) {
            warUrl = url.toString().replaceFirst("jar:", "");
            warUrl = warUrl.substring(0, warUrl.indexOf("!"));
        }

        URL newUrl = StringUtils.isNotBlank(warUrl) ? new URL(warUrl) : url;
        File war = FileUtils.toFile(newUrl);

        if (!war.toString().endsWith(".war")) {
            return; // no need to copy
        }

        String gethDir = FileUtils.expandPath(configDir, "geth");
        System.out.println("Extracting geth to " + gethDir);

        try (ZipFile warZip = new ZipFile(war)) {
            Enumeration<? extends ZipEntry> entries = warZip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                String file = zipEntry.getName();
                if (zipEntry.isDirectory() || !file.startsWith("WEB-INF/classes/geth")) {
                    continue;
                }

                File target = new File(FileUtils.join(configDir, file.substring(16)));
                File targetDir = target.getParentFile();
                if (!targetDir.exists()) {
                    targetDir.mkdirs();
                }
                FileUtils.copyInputStreamToFile(warZip.getInputStream(zipEntry), target);
            }
        }

        System.setProperty("eth.geth.dir", gethDir);
    }

    @Bean
    @Profile("spring-boot")
    public ConfigurableServletWebServerFactory webServerFactory() {
      JettyServletWebServerFactory factory = new JettyServletWebServerFactory();
      factory.setContextPath("/cakeshop");
      factory.addErrorPages(new ErrorPage(HttpStatus.NOT_FOUND, "/notfound.html"));
      return factory;
    }

}
