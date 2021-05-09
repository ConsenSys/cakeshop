package com.jpmorgan.cakeshop.config;

import com.google.common.collect.Lists;
import com.jpmorgan.cakeshop.error.APIException;
import com.jpmorgan.cakeshop.error.ErrorLog;
import com.jpmorgan.cakeshop.service.task.InitializeNodesTask;
import com.jpmorgan.cakeshop.util.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.SystemUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

@Order(999999)
@Service(value = "appStartup")
public class AppStartup implements ApplicationListener<ApplicationEvent> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(AppStartup.class);
    private final Long REQUIRED_MEMORY = 2000000L;

    @Value("${cakeshop.config.dir}")
    private String CONFIG_ROOT;

    @Value("${server.port}")
    private String SERVER_PORT;

    @Value("${server.servlet.context-path}")
    private String CONTEXT_PATH;

    @Value("${nodejs.binary:node}")
    String nodeJsBinaryName;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier("asyncExecutor")
    private Executor executor;

    private boolean autoStartFired;

    private boolean healthy = true;

    private String solcVer;

    private final List<ErrorLog> errors;

    public AppStartup() {
        errors = new ArrayList<>();
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {

        if (!(event instanceof ContextRefreshedEvent)) {
            return;
        }

        if (autoStartFired) {
            return;
        }
        autoStartFired = true;
        healthy = testSystemHealth();
        if (healthy) {
            // Make sure initial nodes are in the DB if file provided
            executor.execute(applicationContext.getBean(InitializeNodesTask.class));
        }

        if (!healthy) {
            System.out.println(((ContextRefreshedEvent) event).getApplicationContext());
            printErrorReport();
            return;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("PRINTING DEBUG INFO");
            LOG.debug("\n" + getDebugInfo(null));
        }

        printWelcomeMessage();
    }

    private void printErrorReport() {
        System.out.println();
        System.out.println();
        System.out.println(StringUtils.repeat("*", 80));

        System.out.println("PRINTING DEBUG INFO");
        System.out.println("-------------------");
        System.out.println();
        System.out.println(getDebugInfo(null));

        System.out.println();
        System.out.println();
        System.out.println("PRINTING ERROR MESSAGES");
        System.out.println("-----------------------");
        System.out.println();
        System.out.println(getErrorInfo());

        System.out.println();
        System.out.println(StringUtils.repeat("*", 80));
        System.out.println();
        System.out.println();
    }

    private void printWelcomeMessage() {
        System.out.println();
        System.out.println();

        System.out.println();
        System.out.println("          CAKESHOP");
        System.out.println("          version:     " + AppVersion.BUILD_VERSION);
        System.out.println("          build id:    " + AppVersion.BUILD_ID);
        System.out.println("          build date:  " + AppVersion.BUILD_DATE);
        System.out.println("          Access the Cakeshop UI at: http://localhost:" + SERVER_PORT + CONTEXT_PATH);
    }

    public String getDebugInfo(ServletContext servletContext) {
        StringBuilder out = new StringBuilder();

        out.append("java.vendor: ").append(SystemUtils.JAVA_VENDOR).append("\n");
        out.append("java.version: ").append(System.getProperty("java.version")).append("\n");
        out.append("java.home: ").append(SystemUtils.JAVA_HOME).append("\n");
        out.append("java.io.tmpdir: ").append(SystemUtils.JAVA_IO_TMPDIR).append("\n");
        out.append("\n");

        out.append("servlet.container: ");
        if (servletContext != null) {
            out.append(servletContext.getServerInfo());
        }
        out.append("\n\n");

        out.append("cakeshop.version: ").append(AppVersion.BUILD_VERSION).append("\n");
        out.append("cakeshop.build.id: ").append(AppVersion.BUILD_ID).append("\n");
        out.append("cakeshop.build.date: ").append(AppVersion.BUILD_DATE).append("\n");
        out.append("\n");

        out.append("os.name: ").append(SystemUtils.OS_NAME).append("\n");
        out.append("os.version: ").append(SystemUtils.OS_VERSION).append("\n");
        out.append("os.arch: ").append(SystemUtils.OS_ARCH).append("\n");
        out.append("\n");

        out.append(getLinuxInfo());

        out.append("user.dir: ").append(SystemUtils.getUserDir()).append("\n");
        out.append("user.home: ").append(SystemUtils.getUserHome()).append("\n");
        out.append("\n");

        out.append("app.root: ").append(FileUtils.getClasspathPath("")).append("\n");
        out.append("eth.env: ").append(System.getProperty("eth.environment")).append("\n");
        out.append("cakeshop.config.dir: ").append(CONFIG_ROOT).append("\n");
        out.append("\n\n");

        // test solc binary
        out.append("solc.path: ").append(CakeshopUtils.getSolcPath()).append("\n");
        out.append("solc.version: ");
        try {
            List<String> args = Lists.newArrayList(
                nodeJsBinaryName,
                CakeshopUtils.getSolcPath(),
                "--version");
            ProcessBuilder builder = ProcessUtils.createProcessBuilder(args);
            Process process = builder.start();
            process.waitFor();
            solcVer = IOUtils.toString(process.getInputStream(), Charset.defaultCharset());
        } catch (Exception e) {
            addError(e);
        }
        if (StringUtils.isNotBlank(solcVer)) {
            out.append(solcVer);
        } else {
            out.append("!!! unable to read solc version !!!");
        }

        return out.toString();
    }

    private String getLinuxInfo() {

        if (!SystemUtils.IS_OS_LINUX) {
            return "";
        }

        // lists all the files ending with -release in the etc folder
        File dir = new File("/etc/");
        List<File> files = new ArrayList<>();

        if (dir.exists()) {
            files.addAll(Lists.newArrayList(dir.listFiles((File dir1, String filename) -> filename.endsWith("release"))));
        }

        // looks for the version file (not all linux distros)
        File fileVersion = new File("/proc/version");
        if (fileVersion.exists()) {
            files.add(fileVersion);
        }

        if (files.isEmpty()) {
            return null;
        }

        StringBuilder str = new StringBuilder();

        str.append("\n\n");
        str.append("Linux release info:");

        // prints all the version-related files
        files.forEach((f) -> {
            try {
                String ver = FileUtils.readFileToString(f, Charset.defaultCharset());
                if (!StringUtils.isBlank(ver)) {
                    str.append(ver);
                }
            } catch (IOException e) {
            }
        });
        str.append("\n\n");
        return str.toString();
    }

    private List<ErrorLog> getAllErrors() {
        // gather all errors and sort
        List<ErrorLog> allErrors = new ArrayList<>();
        allErrors.addAll(errors);

        Collections.sort(allErrors, (ErrorLog o1, ErrorLog o2) -> {
            long result = o1.nanos - o2.nanos;
            if (result < 0) {
                return -1;
            } else if (result > 0) {
                return 1;
            } else {
                return 0;
            }
        });
        return allErrors;
    }

    public String getErrorInfo() {
        List<ErrorLog> allErrors = getAllErrors();
        if (allErrors.isEmpty()) {
            return "(no errors logged)";
        }

        StringBuilder out = new StringBuilder();
        allErrors.forEach((err) -> {
            out.append(err.toString()).append("\n\n");
        });
        return out.toString();
    }

    /**
     * Checks that all dependencies are functioning correctly
     *
     * @return
     */
    private boolean testSystemHealth() {
        boolean isHealthy = true;

        System.out.println();
        System.out.println();
        System.out.println(StringUtils.repeat("*", 80));
        System.out.println("Running pre-flight checks...");
        System.out.println();

        // test config & db data dir
        System.out.println();
        String dbDir = FileUtils.expandPath(CONFIG_ROOT, "db");
        System.out.println("Testing db path");
        System.out.println(dbDir);
        if (isDirAccesible(dbDir)) {
            System.out.println("OK");
        } else {
            System.out.println("FAILED");
            isHealthy = false;
        }

        System.out.println(StringUtils.repeat("*", 80));
        System.out.println();

        return isHealthy;
    }

    private boolean isDirAccesible(String path) {
        try {
            File dir = new File(path);
            if (dir.exists()) {
                boolean ok = (dir.canRead() && dir.canWrite());
                if (!ok) {
                    addError("Unable to read/write " + path);
                }
                return ok;
            }

            dir.mkdirs();
            if (!dir.exists()) {
                addError("Unable to create path " + path);
                return false;
            }

            boolean ok = (dir.canRead() && dir.canWrite());
            if (!ok) {
                addError("Unable to read/write " + path);
            }
            return ok;
        } catch (Exception ex) {
            LOG.error("Caught ex while testing path " + path, ex);
            addError(ex);
            return false;
        }
    }

    public boolean isHealthy() {
        return healthy;
    }

    public void addError(Object err) {
        this.errors.add(new ErrorLog(err));
    }

}
