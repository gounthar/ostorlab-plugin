package co.ostorlab.ci.jenkins.utils;

import co.ostorlab.ci.jenkins.connector.Credentials;
import co.ostorlab.ci.jenkins.mapper.CreateMobileScan;
import co.ostorlab.ci.jenkins.mapper.GetMobileScan;
import co.ostorlab.ci.jenkins.mapper.InputQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.cliftonlabs.json_simple.JsonObject;
import hudson.util.Secret;
import org.jetbrains.annotations.NotNull;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;


import static co.ostorlab.ci.jenkins.utils.FileHelper.load;

public class RequestHandler {
    private static final String CONTENT_TYPE = "Content-Type";
    private static final String AUTHORIZATION = "X-Api-Key";
    private static final String POST = "POST";
    private static final String QUERY_CREATE_MOBILE_SCAN = "mutation newMobileScan($title: String!, $assetType: String!, $application: Upload!, $scanProfile: String!, $credentialIds: [Int!]) {" +
            "createMobileScan(title: $title, assetType:$assetType, application: $application, scanProfile: $scanProfile, credentialIds: $credentialIds) {" +
            " scan {id}}}";

    private static final String QUERY_CREATE_TEST_CREDENTIAL = "mutation CreateTestCredential($testCredentials: TestCredentialsInput!) {" +
            "    createTestCredentials(testCredentials: $testCredentials) {" +
            "      __typename" +
            "      testCredentials {" +
            "        ... on CustomTestCredentials {" +
            "          id" +
            "        }" +
            "      }" +
            "    }" +
            "  }";

    private static final String QUERY_GET_RISK_SCAN_BY_ID = "query AllVulns($scanId: Int!) {" +
            "scan(scanId: $scanId) {" +
            " riskRating}}";

    private static final String QUERY_GET_PROGRESS_SCAN_BY_ID = "query AllVulns($scanId: Int!) {" +
            "scan(scanId: $scanId) {" +
            " progress}}";

    private static final String QUERY_GET_SUBSCRIPTIONS = "query getSubscriptions {" +
            "subscriptions { subscriptions { countRemainingScan plan { maxScans product {scanType} }}}}";

    private static final String TWO_HYPHENS = "--";
    private static final String BOUNDARY = "*****" + System.currentTimeMillis() + "*****";
    private static final String LINE_END = "\r\n";


    /**
     * Get Scan Progress.
     *
     *
     * @param uri    the uri
     * @param scanId the scan id
     * @param apiKey the api key
     * @return the string
     * @throws IOException the io exception
     */
    public static @NotNull String getProgress(String uri, int scanId, Secret apiKey) throws IOException {

        GetMobileScan scan = new GetMobileScan(scanId);
        InputQuery inputQuery = new InputQuery(QUERY_GET_PROGRESS_SCAN_BY_ID, scan);
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonInputString = objectMapper.writeValueAsString(inputQuery);
        return runRequest(uri, apiKey, jsonInputString);
    }

    /**
     * Get Scan Global risk.
     *
     * @param uri    the uri
     * @param scanId the scan id
     * @param apiKey the api key
     * @return the string
     * @throws IOException the io exception
     */
    public static @NotNull String getRisk(String uri, int scanId, Secret apiKey) throws IOException {

        GetMobileScan scan = new GetMobileScan(scanId);
        InputQuery inputQuery = new InputQuery(QUERY_GET_RISK_SCAN_BY_ID, scan);
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonInputString = objectMapper.writeValueAsString(inputQuery);
        return runRequest(uri, apiKey, jsonInputString);
    }

    /**
     * Check string.
     *
     * @param uri    the uri
     * @param apiKey the api key
     * @return the string
     * @throws IOException the io exception
     */
    public static @NotNull String check(String uri, Secret apiKey) throws IOException {

        InputQuery inputQuery = new InputQuery(QUERY_GET_SUBSCRIPTIONS);
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonInputString = objectMapper.writeValueAsString(inputQuery);
        return runRequest(uri, apiKey, jsonInputString);
    }

    /**
     * Run request string.
     *
     * @param uri    the uri
     * @param apiKey the api key
     * @param input  the input
     * @return the string
     * @throws IOException the io exception
     */
    public static @NotNull String runRequest(String uri, Secret apiKey, String input) throws IOException {
        URL url = new URL(uri);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod(POST);
        con.setDoOutput(true);
        con.setRequestProperty(CONTENT_TYPE, "application/json");
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty(AUTHORIZATION, apiKey.getPlainText());

        DataOutputStream out = new DataOutputStream(con.getOutputStream());

        out.writeBytes(input);

        out.flush();
        out.close();

        StringBuilder content;

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(con.getErrorStream(), StandardCharsets.UTF_8))) {

            String line;
            content = new StringBuilder();

            while ((line = br.readLine()) != null) {
                content.append(line);
                content.append(System.lineSeparator());
                System.out.println(content);
            }
        } catch (Exception e) {
            System.out.println(e);
        }

        InputStream in = con.getInputStream();
        String json = new String(load(in), StandardCharsets.UTF_8);
        in.close();
        con.disconnect();
        return json;

    }


    /**
     * Upload string.
     *
     * @param title scan title
     * @param uri      the uri
     * @param apiKey   the api key
     * @param fileContent     the file
     * @param fileName    the file
     * @param scanProfile     the scanProfile
     * @param platform the platform
     * @return the string
     * @throws IOException the io exception
     */
    public static @NotNull String upload(String title, String uri, Secret apiKey, String fileName, byte[] fileContent, String scanProfile, String platform, Integer scanCredential) throws IOException {
        URL url = new URL(uri);
        HttpURLConnection con = (HttpURLConnection) url.openConnection();

        con.setRequestMethod(POST);
        con.setDoOutput(true);
        con.setDoInput(true);
        con.setRequestProperty("Connection", "Keep-Alive");
        con.setRequestProperty(CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY);
        con.setRequestProperty("Accept", "application/json");
        con.setRequestProperty(AUTHORIZATION, apiKey.getPlainText());

        DataOutputStream out = new DataOutputStream(con.getOutputStream());
        out.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);

        CreateMobileScan createMobileScan = new CreateMobileScan(platform, scanProfile, null, title, scanCredential);
        ObjectMapper objectMapper = new ObjectMapper();
        InputQuery inputCreateMobileScan = new InputQuery(QUERY_CREATE_MOBILE_SCAN, createMobileScan);
        String jsonInputString = objectMapper.writeValueAsString(inputCreateMobileScan);

        out.writeBytes("Content-Disposition: form-data; name=\"operations\"" + LINE_END);
        out.writeBytes("Content-Type: application/json" + LINE_END);
        out.writeBytes(LINE_END);
        out.writeBytes(jsonInputString);
        out.writeBytes(LINE_END);
        out.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);

        out.writeBytes("Content-Disposition: form-data; name=\"0\"; filename=\"" + fileName + "\"" + LINE_END);
        out.writeBytes("Content-Type: application/zip" + LINE_END);
        out.writeBytes(LINE_END);
        out.write(fileContent, 0, fileContent.length);
        out.writeBytes(LINE_END);
        out.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);

        String jsonMapString = "{\"0\":[\"variables.application\"]}";
        out.writeBytes("Content-Disposition: form-data; name=\"map\"" + LINE_END);
        out.writeBytes("Content-Type: application/json" + LINE_END);
        out.writeBytes(LINE_END);
        out.writeBytes(jsonMapString);
        out.writeBytes(LINE_END);
        out.writeBytes(TWO_HYPHENS + BOUNDARY + LINE_END);

        out.flush();
        out.close();

        StringBuilder content;

        // Get the response from the server without calling getInputStream
        con.getResponseCode();
        InputStream errorStream = con.getErrorStream();

        if (errorStream != null) {
            BufferedReader br = new BufferedReader(new InputStreamReader(errorStream, StandardCharsets.UTF_8));
            String line;
            content = new StringBuilder();
            while ((line = br.readLine()) != null) {
                content.append(line);
                content.append(System.lineSeparator());
            }
            throw new IOException(content.toString());
        }

        InputStream in = con.getInputStream();
        String json = new String(load(in), StandardCharsets.UTF_8);
        in.close();
        con.disconnect();
        return json;
    }

    /**
     * Call API to create Test credentials.
     *
     * @param uri The API url
     * @param credential the list os credentials to add
     * @param apiKey the api key to authenticate
     * @return the response string
     * @throws IOException the io exception
     */
    public static @NotNull String createTestCredential(String uri, List<Credentials> credential, Secret apiKey) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonObject variableCredential = new JsonObject();
        JsonObject creds = new JsonObject();
        creds.put("credentials", credential);
        JsonObject customCreds = new JsonObject();
        customCreds.put("custom", creds);
        variableCredential.put("testCredentials", customCreds);
        InputQuery inputQuery = new InputQuery(QUERY_CREATE_TEST_CREDENTIAL, variableCredential);
        String jsonInputString = objectMapper.writeValueAsString(inputQuery);
        return runRequest(uri, apiKey, jsonInputString);
    }
}

