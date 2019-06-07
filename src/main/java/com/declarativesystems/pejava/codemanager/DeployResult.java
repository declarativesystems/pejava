package com.declarativesystems.pejava.codemanager;

import com.google.gson.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeployResult {
    /**
     * Deploy signature (git rev) reported by puppet
     */
    private String deploySignature;

    /**
     * Deployment result as reported by puppet
     */
    private String status;

    /**
     * Environment
     */
    private String environment;

    /**
     * git-rev we were supposed to be deploying
     */
    private String targetDeploySignature;

    /**
     * field in parsed MAP that indicates the git commit that just got deployed
     */
    public static final String FIELD_GIT_COMMIT_DEPLOYED    = "deploy-signature";

    /**
     * field in parsed MAP that indicates the environment (git branch) just got
     * deployed
     */
    public static final String FIELD_ENVIRONMENT_DEPLOYED   = "environment";

    /**
     * field in parsed MAP that indicates the status of deployment
     */
    public static final String FIELD_STATUS   = "status";

    /**
     * How puppet flags deployment OK in its JSON
     */
    public static final String STATUS_OK = "complete";

    public static final String STATUS_QUEUED = "queued";

    public static final String MISSING = "missing";

    public static final String MSG_QUEUED = "QUEUED";
    public static final String MSG_OK = "OK";
    public static final String MSG_FAILED = "FAILED";
    public static final String MSG_MISMATCH = "MISMATCH";


    public String getDeploySignature() {
        return deploySignature;
    }

    public String getStatus() {
        return status;
    }

    public String getEnvironment() {
        return environment;
    }

    public boolean isStatusOk() {
        return status.equals(STATUS_OK);
    }

    public boolean isStatusQueued() {
         return status.equals(STATUS_QUEUED);
    }

    public boolean isOk() {
        return isStatusQueued() || (isStatusOk() && isMatchingDeploymentSignature());
    }

    public boolean isMatchingDeploymentSignature() {
        return deploySignature.equalsIgnoreCase(targetDeploySignature);
    }

    public String getStatusAsString() {

        String statusString;
        if (isStatusQueued()) {
            statusString = MSG_QUEUED;
        } else if (isStatusOk() && targetDeploySignature != null && isMatchingDeploymentSignature()) {
            statusString = MSG_OK;
        } else if (isStatusOk() && targetDeploySignature != null && ! isMatchingDeploymentSignature()){
            statusString = MSG_MISMATCH;
        } else {
            statusString = MSG_FAILED;
        }

        return statusString;
    }

    public String toString() {
        String gitMessage = isStatusQueued() || isMatchingDeploymentSignature() ?
                targetDeploySignature :
                String.format("git=%s puppet=%s", targetDeploySignature, deploySignature);

        return String.format(
                "[%s] %s - %s",
                environment,
                getStatusAsString(),
                gitMessage
        );
    }

    /**
     * Detect the presence of errors in the output from the code manager
     * web-service
     * @param responseString JSON string from Puppet Enterprise Code Manager REST API
     * @return true if error(s) detected
     */
    public static boolean responseStringContainError(String responseString) {
        return responseString.contains("\"kind\"");
    }


    private static Map[] responseStringtoMap(String responseString) {
        Gson gson = new GsonBuilder().create();
        Map[] data;
        try {
            data = gson.fromJson(responseString, Map[].class);
        } catch (JsonSyntaxException e) {
            // Puppet will give a different JSON message that we cant parse to
            // an array of map if there were errors - in this case swallow the
            // error and return an empty list
            data = new Map[0];
        }
        return data;
    }

    /**
     * Parse the response string (which is hopefully json) and make java POJOs
     *
     * Throws (Runtime) JsonSyntaxException if deployment failed and puppet gives
     * us a different type of JSON output that we cant easily parse
     *
     * FROM:
     * [
     *   {
     *     "deploy-signature": "22811999e6cbeaf4b6be744a4d0b454b831f8999",
     *     "environment": "development",
     *     "file-sync": {
     *       "code-commit": "9a4c7c8174d48c35fd808313553bade148fc4cb8",
     *       "environment-commit": "943b1140bff1f510a0e4fd7dd7b769f04c14d510"
     *     },
     *     "id": 38,
     *     "status": "complete"
     *   },
     *   {
     *     "environment": "fail_code_quality",
     *     "error": {
     *       "details": {
     *         "corrected-env-name": "fail_code_quality"
     *       },
     *       ...
     *
     *
     * @param responseString JSON string from Puppet Enterprise Code Manager
     *                       REST API
     * @return Parsed DeployResult instances or empty list if none could be
     *         parsed
     */
    public static List<DeployResult> toDeployResult(String responseString) {
        List<DeployResult> results = new ArrayList<>();

        for (Map data : responseStringtoMap(responseString)) {
            DeployResult deployResult = new DeployResult();
            deployResult.environment = (String) data.get(FIELD_ENVIRONMENT_DEPLOYED);
            deployResult.deploySignature = (String) data.getOrDefault(FIELD_GIT_COMMIT_DEPLOYED, MISSING);
            deployResult.status = (String) data.get(FIELD_STATUS);
            results.add(deployResult);
        }

        return results;
    }

    /**
     * Check the raw output of a deployment against a list of targets. This way
     * we can verify that we are deploying the right code by commit ID (eg test
     * puppet not connected to a different server...). This is used by `hook`s
     * and `task`s which typically relate to git commits
     * @param responseString JSON string from Puppet Enterprise Code Manager
     *                       REST API
     * @param target Map of with key (environment name), value (git commits)
     *               that should be present
     * @return Parsed DeployResult instances or empty list if none could be
     *         parsed
     */
    public static List<DeployResult> checkDeployResult(String responseString, Map<String,String> target) {
        List<DeployResult> results = toDeployResult(responseString);
        List<String> puppetEnvironments = new ArrayList<>();

        for (DeployResult result : results) {
            puppetEnvironments.add(result.environment);
            // we will get "missing" if puppet deployed an environment it wasn't asked to
            result.targetDeploySignature = target.getOrDefault(result.environment, "missing");
        }

        // finally, we check that every requested environment was really deployed and
        // not somehow lost (eg puppet hooked up to different server, throwing away envs
        // with hypens, etc...). If we find any that are missing make an empty DeployResult
        // to indicate failure
        for (String targetEnvironment: target.keySet()) {
            if (! puppetEnvironments.contains(targetEnvironment)) {
                DeployResult missingEnvironment = new DeployResult();
                missingEnvironment.environment = targetEnvironment;
                missingEnvironment.targetDeploySignature = target.get(targetEnvironment);
                missingEnvironment.status = MISSING;
                missingEnvironment.deploySignature = MISSING;

                results.add(missingEnvironment);
            }
        }

        return results;
    }

    /**
     * Parse the raw output into an HTML table. We use this in the puppet deploy
     * plugin to do a deployment when the user clicks "deploy now" since this is
     * unrelated to any particular git commit, we don't do any of the checks
     * around ID mismatch etc
     *
     * @param responseString JSON string from Puppet Enterprise Code Manager
     *                       REST API
     * @return HTML table rows parsed from responseString or empty string if no
     *         DeployResult instances could be parsed
     */
    public static String toHtmlTableRows(String responseString) {
        StringBuilder sb = new StringBuilder();
        for (DeployResult deployResult :toDeployResult(responseString)) {
            String spanClass = deployResult.isStatusOk() ?
                    "class=\"puppetOk\"": "class=\"puppetError\"";
            sb.append("<tr>");
            sb.append("<td>").append(deployResult.environment).append("</td>");
            sb.append("<td>")
                    .append("<span ").append(spanClass).append(">")
                    .append(deployResult.status)
                    .append("</span>")
            .append("</td>");
            sb.append("<td>").append(deployResult.deploySignature).append("</td>");
            sb.append("<tr>");
        }

        return sb.toString();
    }

    /**
     * Attempt to pretty-print the JSON contained in a response string and
     * degrade witout error if its not valid JSON
     * @param responseString JSON string from Puppet Enterprise Code Manager
     *                       REST API
     * @return Pretty printed version of responseString (if possible)
     */
    public static String prettyPrintJson(String responseString) {
        String pretty;
        JsonParser parser = new JsonParser();

        try {
            JsonElement json = (responseString.startsWith("[")) ?
                    parser.parse(responseString).getAsJsonArray():
                    parser.parse(responseString).getAsJsonObject();

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            pretty = gson.toJson(json);
        } catch (JsonSyntaxException e) {
            pretty = responseString;
        }
        return pretty;
    }
}
