package taurustestng;

import org.testng.TestNG;

import java.io.FileReader;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TestNGRunner {
    private static final Logger log = Logger.getLogger(TestNGRunner.class.getName());
    public static final String REPORT_FILE = "report_file";
    public static final String TARGET_PREFIX = "target_";
    public static final String ITERATIONS = "iterations";
    public static final String HOLD = "hold_for";

    static {
        log.setLevel(Level.FINER);
    }

    public static void main(String[] args) throws Exception {
        log.info("Starting: " + Arrays.toString(args));
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage requires 1 parameter, containing path to properties file");
        }

        Properties props = new Properties();
        props.load(new FileReader(args[0]));

        ArrayList<Class> classes = getClasses(props);
        if (classes.isEmpty()) {
            throw new RuntimeException("Nothing to test");
        }
        log.info("Running with classes: " + classes.toString());
        Class[] classArray = classes.toArray(new Class[classes.size()]);

        TaurusReporter reporter = new TaurusReporter(props.getProperty(REPORT_FILE));
        TestListener testListener = new TestListener(reporter);
        // List<String> suites = Lists.newArrayList();
        // suites.add("testng.xml");
        // testng.setTestSuites(suites);

        long iterations = Long.valueOf(props.getProperty(ITERATIONS, "0"));
        float hold = Float.valueOf(props.getProperty(HOLD, "0"));
        if (iterations == 0) {
            if (hold > 0) {
                iterations = Long.MAX_VALUE;
            } else {
                iterations = 1;
            }
        }

        long startTime = System.currentTimeMillis();
        for (int iteration = 0; iteration < iterations; iteration++) {
            TestNG runner = new TestNG();
            runner.addListener(testListener);
            runner.setTestClasses(classArray);
            runner.run();
            log.info("Elapsed: " + (System.currentTimeMillis() - startTime) + ", limit: " + (hold * 1000));
            if (hold > 0 && System.currentTimeMillis() - startTime > hold * 1000) {
                log.info("Duration limit reached, stopping");
                break;
            }
        }

        reporter.close();
    }

    protected static ArrayList<Class> getClasses(Properties props) {
        ArrayList<Class> result = new ArrayList<>(0);

        Enumeration<?> it = props.propertyNames();
        while (it.hasMoreElements()) {
            String propName = (String) it.nextElement();
            if (propName.startsWith(TARGET_PREFIX)) {
                result.addAll(getClasses(props.getProperty(propName)));
            }
        }

        return result;
    }

    protected static List<Class<?>> getClasses(String jarPath) {
        List<Class<?>> testClasses = new ArrayList<>();
        try {
            processJAR(testClasses, jarPath);
        } catch (IOException | ClassNotFoundException e) {
            log.warning("Failed to add " + jarPath + "\n" + Utils.getStackTrace(e));
        }
        return testClasses;
    }

    protected static void processJAR(List<Class<?>> testClasses, String jarPath) throws IOException, ClassNotFoundException {
        log.info("Processing JAR: " + jarPath);
        JarFile jarFile = new JarFile(jarPath);
        Enumeration<JarEntry> jarEntriesEnum = jarFile.entries();

        URL[] urls = {new URL("jar:file:" + jarPath + "!/")};
        URLClassLoader cl = URLClassLoader.newInstance(urls);

        while (jarEntriesEnum.hasMoreElements()) {
            JarEntry jarEntry = jarEntriesEnum.nextElement();
            if (jarEntry.isDirectory() || !jarEntry.getName().endsWith(".class")) {
                continue;
            }

            String className = jarEntry.getName().substring(0, jarEntry.getName().length() - ".class".length());
            className = className.replace('/', '.');

            Class<?> c = cl.loadClass(className);

            testClasses.add(c);
        }
        jarFile.close();
    }
}
