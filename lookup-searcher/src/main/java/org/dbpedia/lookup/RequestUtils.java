package org.dbpedia.lookup;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import jakarta.servlet.http.HttpServletRequest;

public class RequestUtils {
    
    public static String getStringParameter(HttpServletRequest req, String[] keys, String defaultValue) {

		for (String key : keys) {

			String result = req.getParameter(key);

			if (result != null) {

				try {
					return URLDecoder.decode(result, "UTF-8");	
					
				} catch (UnsupportedEncodingException e) {
					return result;
				}
			}
		}

		return defaultValue;
	}

    public static float getFloatParameter(HttpServletRequest req, String key, float defaultValue) {

		String result = req.getParameter(key);

		if (result == null) {
			return defaultValue;
		}

		try {
			return Float.parseFloat(result);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public static int getIntParameter(HttpServletRequest req, String key, int defaultValue) {

		String result = req.getParameter(key);

		if (result == null) {
			return defaultValue;
		}

		try {
			return Integer.parseInt(result);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	public static String getStringParameter(HttpServletRequest req, String key, String defaultValue) {

		String result = req.getParameter(key);

		if (result != null) {
			return result;
		}

		return defaultValue;
	}

}
