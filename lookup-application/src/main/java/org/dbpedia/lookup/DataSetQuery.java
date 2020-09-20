package org.dbpedia.lookup;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;

import org.apache.commons.io.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class DataSetQuery {

	private URL url;
	
	public DataSetQuery(String endPointUrl, String query) throws UnsupportedEncodingException, MalformedURLException {
		
		String urlString = endPointUrl + "?query=" + URLEncoder.encode(query, "UTF-8") + "&format=application%2Fsparql-results%2Bjson&timeout=0&debug=on";
		
		this.url = new URL(urlString);
	}
	
	public String[] queryDownloadLinks() throws IOException
	{
		URLConnection urlConnection = url.openConnection();
	
		urlConnection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
		urlConnection.setRequestProperty("Data-Type", "json");
		urlConnection.connect();
	
		final InputStream inputStream = urlConnection.getInputStream();
	
		String result = IOUtils.toString(inputStream, "UTF-8");
	
		JSONObject json = new JSONObject(result);
	
		JSONArray results = json.getJSONObject("results").getJSONArray("bindings");
	
		String[] links = new String[results.length()];
	
		for(int i = 0; i < results.length(); i++) {
	
			links[i] = results.getJSONObject(i).getJSONObject("file").getString("value");
		}
	
		return links;
	}
}
