package org.gk.reach;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.methods.StringRequestEntity;

public class ReachHttpCall {
    private String graphQLUrl;

    public ReachHttpCall() {
    }
    
    private String getGraphQLUrl() throws Exception {
        if (graphQLUrl == null) {
            // Check if it is configured
            graphQLUrl = ReachUtils.getConfigReachURL("reachGraphQLUrl");
            if (graphQLUrl == null)
                graphQLUrl = ReachConstants.GRAPHQL_API_URL;
        }
        return graphQLUrl;
    }

    /**
     * GraphQL front-end for {@link org.gk.reach.ReachHttpCall#callHttp}.
     *
     * @param query
     * @return String, JSON REACH results.
     * @throws IOException
     */
	public String callGraphQL(String query) throws Exception{
	    if (query == null || query.length() == 0)
	        return null;
		RequestEntity entity = new StringRequestEntity(query, ReachConstants.GRAPHQL_SEND_TYPE, "UTF-8");
		return callHttpPost(getGraphQLUrl(), entity);
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
