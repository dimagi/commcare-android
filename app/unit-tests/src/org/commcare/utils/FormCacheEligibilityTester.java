package org.commcare.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.util.List;
import java.util.ArrayList;

import org.commcare.CommCareApplication;
import org.commcare.CommCareTestApplication;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.commcare.android.util.TestUtils;
import org.javarosa.core.model.condition.EvaluationContext;
import org.javarosa.core.model.instance.FormInstance;
import org.javarosa.core.model.instance.TreeElement;
import org.javarosa.xpath.XPathParseTool;
import org.javarosa.xpath.expr.InFormCacheableExpr;
import org.javarosa.xpath.expr.XPathExpression;
import org.javarosa.xpath.parser.XPathSyntaxException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.annotation.Config;

/**
 * PURPOSE: This a utility for quickly computing and examining how many cacheable XPath expressions
 * the forms within a given app contain. Given the files of an app's .ccz, running this test file
 * will print out the exact distribution of cacheable, not-cacheable, and not-computable XPath
 * expressions for each form in the app.
 *
 * TO USE:
 *   1. Create a directory within /resources/commcare-apps called cache_eligibility_testing.
 *   2. Download the .ccz for the app you are interested in examining, and unzip it. Copy the
 *      contents directly into the directory you just created.
 *   3. Run this test file, and observe the output to sys.out.
 */
@Config(application = CommCareTestApplication.class)
@RunWith(AndroidJUnit4.class)
public class FormCacheEligibilityTester {

    // Place whatever app you want to test at this path
    private static final String PATH_TO_CCZ_RESOURCES = "/commcare-apps/cache_eligibility_testing";

    private static void categorizeAndPrintExpressions(String resourcePath) throws IOException {
        System.out.println();
        System.out.println(String.format("NEXT FORM: Cacheability for form %s:", resourcePath));

        List<XPathExpression> allExpressions = getXPathExpressions(resourcePath);
        Map<XPathExpression, Integer> cacheable = new HashMap<>();
        Map<XPathExpression, Integer> notCacheable = new HashMap<>();
        Map<XPathExpression, Integer> notComputable = new HashMap<>();

        FormInstance instance = new FormInstance(new TreeElement("data"));
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession(instance);
        for (XPathExpression expr : allExpressions) {
            
            boolean isCacheable;
            try {
                isCacheable = !InFormCacheableExpr.referencesMainFormInstance(expr, ec) &&
                        !InFormCacheableExpr.containsUncacheableSubExpression(expr, ec);
            } catch (Exception e) {
                int count = notComputable.containsKey(expr) ? notComputable.get(expr) : 0;
                notComputable.put(expr, count+1);
                continue;
            }

            if (isCacheable) {
                int count = cacheable.containsKey(expr) ? cacheable.get(expr) : 0;
                cacheable.put(expr, count+1);
            } else {
                int count = notCacheable.containsKey(expr) ? notCacheable.get(expr) : 0;
                notCacheable.put(expr, count+1);
            }
        }

        System.out.println();
        System.out.println("# of expressions w/ error computing cacheability: " + notComputable.size());
        for (XPathExpression expr : notComputable.keySet()) {
            System.out.println(notComputable.get(expr) + ": " + expr.toPrettyString());
        }

        System.out.println();
        System.out.println("# of cacheable expressions: " + cacheable.size());
        for (XPathExpression expr : cacheable.keySet()) {
            System.out.println(cacheable.get(expr) + ": " + expr.toPrettyString());
        }

        System.out.println();
        System.out.println("# of un-cacheable expressions: " + notCacheable.size());
        for (XPathExpression expr : notCacheable.keySet()) {
            System.out.println(notCacheable.get(expr) + ": " + expr.toPrettyString());
        }
    }

    private static List<XPathExpression> getXPathExpressions(String resourcePath) throws IOException {
        InputStream is = System.class.getResourceAsStream(resourcePath);
        BufferedReader br = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        List<XPathExpression> expressions = new ArrayList<>();

        String line;
        while ((line = br.readLine()) != null) {
            addExpressions(sanitize(line), expressions);
        }
        return expressions;
    }

    private static String sanitize(String formLine) {
        return formLine.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "'");
    }

    private static void addExpressions(String line, List<XPathExpression> expressions) {
        Pattern relevant = Pattern.compile("(?:relevant=)\"(.*?)(?:\")");
        Pattern calculate = Pattern.compile("(?:calculate=)\"(.*?)(?:\")");

        Matcher m = relevant.matcher(line);
        String currentString = null;
        try {
            while (m.find()) {
                currentString = m.group(1);
                expressions.add(XPathParseTool.parseXPath(currentString));
            }
            m = calculate.matcher(line);
            while (m.find()) {
                currentString = m.group(1);
                expressions.add(XPathParseTool.parseXPath(currentString));
            }
        } catch (XPathSyntaxException e) {
            System.out.println("Error parsing string as XPathExpression: " + currentString);
        }
    }

    private static List<String> getAllFormsToTest() throws URISyntaxException {
        List<String> resourcePaths = new ArrayList<>();
        URI uri = System.class.getResource(PATH_TO_CCZ_RESOURCES).toURI();
        File directory = new File(uri);
        for (File f : directory.listFiles()) {
            if (f.getName().contains("modules-")) {
                String moduleName = f.getName();
                for (File form : f.listFiles()) {
                    String formResourcePath = String.format("%s/%s/%s", PATH_TO_CCZ_RESOURCES, moduleName, form.getName());
                    resourcePaths.add(formResourcePath);
                }
            }
        }
        return resourcePaths;
    }

    //@Test //IMPORTANT: Keep this commented out in version control for normal test runs because
    // it will be slow and it's not a real test
    public void run() {
        try {
            List<String> formPaths = getAllFormsToTest();
            for (String path : formPaths) {
                categorizeAndPrintExpressions(path);
            }
        } catch (IOException e) {
            System.out.println("IO error with file");
            e.printStackTrace();
        } catch (URISyntaxException e) {
            System.out.println("Error converting resource path to URI");
        }
    }

    // DO NOT REMOVE -- Prevents test file initialization error due to not having any runnable test
    // methods when the real test above is commented out
    @Test
    public void dummy() {

    }
}