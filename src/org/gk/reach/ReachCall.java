package org.gk.reach;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.methods.multipart.FilePart;
import org.apache.commons.httpclient.methods.multipart.MultipartRequestEntity;
import org.apache.commons.httpclient.methods.multipart.Part;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.gk.reach.model.fries.FriesObject;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ReachCall {

    public ReachCall() {
    }

    /**
     * Reach instance front-end for {@link org.gk.reach.ReachCall#callHttp}.
     *
     * @param Path
     * @return FriesObject
     * @throws IOException
     */
	public FriesObject callReachAPI(Path paper) throws IOException {
	    // TODO check that query file is valid.
	    // TODO call REACH instance and return result.
	    String url = "http://localhost:8080/api/uploadFile";
	    String results = null;
	    try (
	        InputStream inputStream = new FileInputStream(paper.toString());
	    ) {
	        Part[] parts = new Part[1];
	        parts[0] = new FilePart("file", paper.toFile());
	        MultipartRequestEntity entity = new MultipartRequestEntity(parts, new HttpMethodParams());
	        results = callHttpPost(url, entity);
	    }
	    if (Thread.currentThread().isInterrupted())
	        return null;
	    FriesObject friesObject = ReachUtils.readJsonText(results);
	    return friesObject;
	}

    /**
     * GraphQL front-end for {@link org.gk.reach.ReachCall#callHttp}.
     *
     * @param query
     * @return String, JSON REACH results.
     * @throws IOException
     */
	public String callGraphQL(String query) throws IOException{
	    if (query == null || query.length() == 0)
	        return null;
		RequestEntity entity = new StringRequestEntity(query, ReachConstants.GRAPHQL_SEND_TYPE, "UTF-8");
		return callHttpPost(ReachConstants.GRAPHQL_API_URL, entity);
	}

	private String callHttpPost(String url, RequestEntity requestEntity) throws IOException {
		PostMethod post = new PostMethod(url);
		post.setRequestEntity(requestEntity);
        return callHttp(url, post);
	}

	String callHttpGet(String url) throws IOException {
		GetMethod get = new GetMethod(url);
        return callHttp(url, get);
	}

	/**
	 * Send HTTP request (String) to given URL and return result.
	 *
	 * @param url
	 * @param method
	 * @return String
	 * @throws IOException
	 */
	private String callHttp(String url, HttpMethodBase method) throws IOException {
		HttpClient client = new HttpClient();
		StringBuilder builder = new StringBuilder();
		int responseCode = client.executeMethod(method);
		if (responseCode == HttpStatus.SC_OK) {
			builder.setLength(0);
			InputStream inputStream = method.getResponseBodyAsStream();
			InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
			BufferedReader reader = new BufferedReader(inputStreamReader);
			String line = null;
			while ((line = reader.readLine()) != null) {
				builder.append(line);
			}
			reader.close();
			inputStreamReader.close();
			inputStream.close();
			method.releaseConnection();
		}
		return builder.toString();
	}
}
