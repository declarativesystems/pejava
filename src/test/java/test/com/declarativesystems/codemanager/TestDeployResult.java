package test.com.declarativesystems.codemanager;

import com.declarativesystems.pejava.codemanager.DeployResult;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class TestDeployResult {

    @Test
    public void testEnvironmentParsed() throws IOException {
        String testCase = FileUtils.readFileToString(new File("src/test/resources/deploy_mixed.json"));
        List<DeployResult> results = DeployResult.toDeployResult(testCase);
        DeployResult dr;

        assertEquals("all results parsed", 6, results.size());

        dr = results.get(0);
        assertEquals(
                "environment parsed OK",
                "development",
                dr.getEnvironment()
        );
        assertEquals(
                "status parsed OK",
                "complete",
                dr.getStatus()
        );
        assertEquals(
                "deploy signature parsed OK",
                "22811999e6cbeaf4b6be744a4d0b454b831f8999",
                dr.getDeploySignature()
        );

        dr = results.get(1);
        assertEquals(
                "environment parsed OK",
                "fail_code_quality",
                dr.getEnvironment()
        );
        assertEquals(
                "status parsed OK",
                "failed",
                dr.getStatus()
        );
        assertEquals(
                "deploy signature parsed OK",
                "missing",
                dr.getDeploySignature()
        );

        dr = results.get(2);
        assertEquals(
                "environment parsed OK",
                "fail_onceover",
                dr.getEnvironment()
        );
        assertEquals(
                "status parsed OK",
                "complete",
                dr.getStatus()
        );
        assertEquals(
                "deploy signature parsed OK",
                "060fd8a21fa8fbb966c14c4dd9660c04c8da93af",
                dr.getDeploySignature()
        );

        dr = results.get(3);
        assertEquals(
                "environment parsed OK",
                "hmm",
                dr.getEnvironment()
        );
        assertEquals(
                "status parsed OK",
                "complete",
                dr.getStatus()
        );
        assertEquals(
                "deploy signature parsed OK",
                "22811999e6cbeaf4b6be744a4d0b454b831f8999",
                dr.getDeploySignature()
        );

        dr = results.get(4);
        assertEquals(
                "environment parsed OK",
                "no_makefile",
                dr.getEnvironment()
        );
        assertEquals(
                "status parsed OK",
                "complete",
                dr.getStatus()
        );
        assertEquals(
                "deploy signature parsed OK",
                "0de26ab36f9df9296e4ffb0958e641d39bfc3729",
                dr.getDeploySignature()
        );


        dr = results.get(5);
        assertEquals(
                "environment parsed OK",
                "production",
                dr.getEnvironment()
        );
        assertEquals(
                "status parsed OK",
                "complete",
                dr.getStatus()
        );
        assertEquals(
                "deploy signature parsed OK",
                "9e5475a0dbfb705bf474af80cbdf56f0477e7dc3",
                dr.getDeploySignature()
        );

    }

    @Test
    public void testHtmlTable() throws IOException {
        String testCase = FileUtils.readFileToString(new File("src/test/resources/deploy_mixed.json"));
        String output = DeployResult.toHtmlTableRows(testCase);

        String pattern = "</tr>";

        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(output);
        int count = 0;
        while (m.find()) {
            count++;
        }
        assertEquals("html row count ok and no crash", count, m.groupCount());
    }

    @Test
    public void testCheckJson() throws IOException {
        String testCase = FileUtils.readFileToString(new File("src/test/resources/deploy_mixed.json"));
        Map<String,String> target = new LinkedHashMap<String,String>(){{
            put("development", "22811999e6cbeaf4b6be744a4d0b454b831f8999");
            // this one wasn't deployed properly - we know this so just look to see the missing
            // commit was detected
            put("fail_code_quality", "missing");
            put("fail_onceover", "060fd8a21fa8fbb966c14c4dd9660c04c8da93af");
            put("hmm", "22811999e6cbeaf4b6be744a4d0b454b831f8999");
            put("no_makefile", "0de26ab36f9df9296e4ffb0958e641d39bfc3729");
            put("production", "9e5475a0dbfb705bf474af80cbdf56f0477e7dc3");
        }};

        String[] statusStrings = new String[] {
                "OK",
                "FAILED",
                "OK",
                "OK",
                "OK",
                "OK"
        };

        List<DeployResult> results = DeployResult.checkDeployResult(testCase, target);

        DeployResult dr;
        assertEquals("all results parsed", 6, results.size());

        int i = 0;
        for (Map.Entry<String, String> entry : target.entrySet()) {
            dr = results.get(i);

            assertEquals(
                    "right environment",
                    dr.getEnvironment(),
                    entry.getKey()
            );
            assertEquals(
                    "commit test OK",
                    dr.getDeploySignature(),
                    entry.getValue()
            );
            assertEquals(
                    "status interpreted ok",
                    statusStrings[i],
                    dr.getStatusAsString()
            );

            assertTrue(
                    "toString() output good",
                    dr.toString().contains(entry.getKey())
            );

            if (statusStrings[i].equals("OK")) {
                assertTrue(
                        "internal status test OK",
                        dr.isStatusOk()
                );
            } else {
                assertFalse(
                        "internal status test OK",
                        dr.isStatusOk()
                );
            }

            i++;
        }
    }

    @Test
    public void testMismatchDetected() throws IOException {
        String testCase = FileUtils.readFileToString(new File("src/test/resources/deploy_mixed.json"));
        Map<String,String> target = new LinkedHashMap<String,String>(){{
            put("development", "DEADBEEFDEADBEEF");
        }};

        List<DeployResult> results = DeployResult.checkDeployResult(testCase, target);

        assertEquals("all results parsed", 6, results.size());
        DeployResult dr = results.get(0);

        assertEquals(
                "missmatch detected",
                DeployResult.MSG_MISMATCH,
                dr.getStatusAsString()
        );

        assertTrue(
                "toString() output good",
                dr.toString().contains(DeployResult.MSG_MISMATCH)
        );
    }

    @Test
    public void testParseBadJsonOK() throws IOException {
        List<DeployResult> results = DeployResult.toDeployResult("an invalid json response");
        assertEquals("zero results returned and no crash",
                0,
                results.size()
        );
    }

    @Test
    public void testParseGeneralPuppetFail() throws IOException {
        String testCase = FileUtils.readFileToString(new File("src/test/resources/puppet_general_fail.json"));
        List<DeployResult> results = DeployResult.toDeployResult(testCase);
        assertEquals("zero results returned and no crash",
                0,
                results.size()
        );
    }

    @Test
    public void testParsePuppetErrorOk() throws IOException {
        String testCase = FileUtils.readFileToString(new File("src/test/resources/puppet_deploy_error.json"));
        List<DeployResult> results = DeployResult.toDeployResult(testCase);

        assertEquals(
                "one result, no crash",
                1,
                results.size()
        );

        DeployResult dr = results.get(0);
        assertEquals("environment parsed",
                "nothere",
                dr.getEnvironment()
        );
        assertEquals(
                "status parsed",
                "failed",
                dr.getStatus()
        );
        assertEquals(
                "deploy signature missing",
                DeployResult.MISSING,
                dr.getDeploySignature()
        );

        assertTrue(
                "toString() output good",
                dr.toString().contains("nothere")
        );
    }


    @Test
    public void testParseQueued() throws IOException {
        String testCase = FileUtils.readFileToString(new File("src/test/resources/queued.json"));
        List<DeployResult> results = DeployResult.toDeployResult(testCase);

        assertEquals(
                "one result parsed",
                1,
                results.size()
        );

        DeployResult dr = results.get(0);
        assertEquals("environment parsed",
                "production",
                dr.getEnvironment()
        );
        assertEquals(
                "status parsed",
                "queued",
                dr.getStatus()
        );
        assertEquals(
                "deploy signature missing",
                DeployResult.MISSING,
                dr.getDeploySignature()
        );
        assertTrue(
                "queued reported as OK",
                dr.isOk()
        );

        assertTrue(
                "toString() output good",
                dr.toString().contains("production")
        );
    }

    @Test
    public void testErrorDetection() throws IOException {
        String testCase = FileUtils.readFileToString(new File("src/test/resources/puppet_general_fail.json"));
        assertTrue(
                "error detected ok",
                DeployResult.responseStringContainError(testCase)
        );
    }

    @Test
    public void testPrettyPrintJson() {
        // none of these should error
        DeployResult.prettyPrintJson("{}");
        DeployResult.prettyPrintJson("[]");
        DeployResult.prettyPrintJson("not=json");
    }

}
