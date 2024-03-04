package org.dbpedia.lookup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;

public class DatabusUtils {
    
	public static File[] loadDatabusFiles(String collectionUri, Logger logger)  {
		ArrayList<File> result = new ArrayList<File>();

		try {
			String query = get("GET", collectionUri, "text/sparql");
			logger.info("Collections resolved to query");
			logger.info(query);
				
			// depending on running system, daytime or weather condition, the query is either already URL encoded or still plain text
			if(!isURLEncoded(query)) {
				query = URLEncoder.encode(query, "UTF-8");
			}

			URL url = new URI(collectionUri).toURL();
			String baseUrl = getBaseUrl(url);
			String queryResult = query(baseUrl + "/sparql", query);
			ArrayList<String> fileUris = new ArrayList<String>();
			JSONObject obj = new JSONObject(queryResult);	
			JSONArray bindings = obj.getJSONObject("results").getJSONArray("bindings");
			
			for (int i = 0; i < bindings.length(); i++)
			{
				JSONObject binding = bindings.getJSONObject(i);
				String key = binding.keys().next();
				JSONObject jsonObject = binding.getJSONObject(key);
				fileUris.add(jsonObject.getString("value"));
			}

			
			String collectionUriHash = DigestUtils.md5Hex(collectionUri);
			File tmpdir = Files.createTempDirectory(collectionUriHash).toFile();
		
			for(String fileUri : fileUris) {
				
				logger.info("Downloading file: " + fileUri);
				String filename = fileUri.substring(fileUri.lastIndexOf('/') + 1);
				
				String prefix = filename;
				String suffixes = "";
				
				if(filename.contains(".")) {
					prefix = filename.substring(0,filename.indexOf('.'));
					suffixes = filename.substring(filename.indexOf('.'));
				}

				String hash = DigestUtils.md5Hex(fileUri).toUpperCase().substring(0,4);
				String uniqname = prefix + "_" + hash + suffixes;

				HttpClient instance = HttpClientBuilder.create().setRedirectStrategy(new LaxRedirectStrategy()).build();
				HttpResponse response = instance.execute(new HttpGet(fileUri));

				Path path = Paths.get(tmpdir.getAbsolutePath() + "/" + uniqname);
				File file = path.toFile();
				response.getEntity().writeTo(new FileOutputStream(file,false));
				
				result.add(file);
				logger.info("File saved to " + path.toAbsolutePath().toString());
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		
		return result.toArray(new File[0]);
	}

	private static String query(String endpoint, String query) throws ParseException, IOException {
		
		HttpClient client = HttpClientBuilder.create().build();
		String body = "default-graph-uri=&format=application%2Fsparql-results%2Bjson&query=" + query;
		HttpEntity entity = new ByteArrayEntity(body.getBytes("UTF-8"));
		HttpPost request = new HttpPost(endpoint);
		request.setEntity(entity);
		request.setHeader("Content-type", "application/x-www-form-urlencoded");
		HttpResponse response = client.execute(request);
		HttpEntity responseEntity = response.getEntity();
		
		if(responseEntity != null) {
		    return EntityUtils.toString(responseEntity);
		}

		return null;
	}

	private static boolean isURLEncoded(String query) {
		
		Pattern hasWhites = Pattern.compile("\\s+");
		Matcher matcher = hasWhites.matcher(query);
		return !matcher.find();
	}

	public static String getBaseUrl(URL url) {
        StringBuilder baseUrl = new StringBuilder();
        baseUrl.append(url.getProtocol()).append("://").append(url.getHost());
        
        int port = url.getPort();
        if (port != -1) {
            baseUrl.append(":").append(port);
        }
        
        return baseUrl.toString();
    }

	private static String get(String method, String urlString, String accept) throws IOException {

		System.out.println(method + ": " + urlString + " / ACCEPT: " + accept);
			
		HttpClient client = HttpClientBuilder.create().build();
		
		if(method.equals("GET")) {

			HttpGet request = new HttpGet(urlString);
			request.addHeader("Accept",  accept);
			HttpResponse response = client.execute(request);
			HttpEntity responseEntity = response.getEntity();
			
			if(responseEntity != null) {
			    return EntityUtils.toString(responseEntity);
			}
		}
	
		return null;
	}
}
