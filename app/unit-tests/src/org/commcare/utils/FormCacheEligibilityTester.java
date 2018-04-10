package org.commcare.utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.util.List;
import java.util.ArrayList;

import org.commcare.CommCareApplication;
import org.commcare.android.CommCareTestRunner;
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

@Config(application = CommCareApplication.class)
@RunWith(CommCareTestRunner.class)
public class FormCacheEligibilityTester {

    private static final String PATH_TO_CCZ_RESOURCES = "/commcare-apps/cache_eligibility_testing";

    private static void categorizeAndPrintExpressions(String resourcePath) throws IOException {
        List<XPathExpression> allExpressions = getXPathExpressions(resourcePath);
        Map<XPathExpression, Integer> cacheable = new HashMap<>();
        Map<XPathExpression, Integer> notCacheable = new HashMap<>();

        FormInstance instance = new FormInstance(new TreeElement("data"));
        EvaluationContext ec = TestUtils.getEvaluationContextWithoutSession(instance);
        for (XPathExpression expr : allExpressions) {

            boolean isCacheable = false;
            try {
                isCacheable = !InFormCacheableExpr.referencesMainFormInstance(expr, instance, ec) &&
                        !InFormCacheableExpr.containsUncacheableSubExpression(expr, ec);
            } catch (Exception e) {
                System.out.println("Error computing cacheability of expression: " + expr.toPrettyString());
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

    private static List<String> getAllFormsToTest() {
        URL path = System.class.getResource(PATH_TO_CCZ_RESOURCES);
        File f = new File(path);
    }

    @Test //Keep this commented out for normal test runs because it will be slow and it's not a real test
    public void run() {
        try {
            List<String> formPaths = getAllFormsToTest();
            for (String path : formPaths) {
                categorizeAndPrintExpressions(path);
            }
        } catch (IOException e) {
            System.out.println("IO error with file");
            e.printStackTrace();
        }
    }

    // to avoid test file initialization error due to not having any runnable test methods
    @Test
    public void dummy() {

    }
}