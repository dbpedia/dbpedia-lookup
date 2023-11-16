package org.dbpedia.lookup.config;

import org.dbpedia.lookup.RequestUtils;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Configuration loaded from a YAML Document. Please refer to the configuration
 * documentation
 * at https://github.com/dbpedia/lookup-application
 * 
 * @author Jan Forberg
 *
 */
public class QuerySettings {

	public static final String EXACT_MATCH_BOOST = "exactMatchBoost";

	public static final String PREFIX_MATCH_BOOST = "prefixMatchBoost";

	public static final String MAX_RESULTS = "maxResults";

	public static final String FORMAT = "format";

	public static final String MIN_SCORE = "minScore";

	public static final String FUZZY_MATCH_BOOST = "fuzzyMatchBoost";

	public static final String FUZZY_PREFIX_LENGTH = "fuzzyPrefixLength";

	public static final String FUZZY_EDIT_DISTANCE = "fuzzyEditDistance";

	private float exactMatchBoost;

	private float prefixMatchBoost;

	private int maxResults;

	private int maxResultsCap;

	private String format;

	private float minScore;

	private float fuzzyMatchBoost;

	private int fuzzyPrefixLength = 2;

	private int fuzzyEditDistance = 1;

	public QuerySettings(QueryConfig config) {
		exactMatchBoost = config.getExactMatchBoost();
		prefixMatchBoost = config.getPrefixMatchBoost();
		maxResults = config.getMaxResults();
		maxResultsCap = config.getMaxResultsCap();
		format = config.getFormat();
		minScore = config.getMinScore();
		fuzzyEditDistance = config.getFuzzyEditDistance();
		fuzzyMatchBoost = config.getFuzzyMatchBoost();
		fuzzyPrefixLength = config.getFuzzyPrefixLength();

		if (format == null || format.equals("")) {
			format = QueryConfig.CONFIG_FIELD_FORMAT_XML;
		}

	}

	public int getFuzzyEditDistance() {
		return fuzzyEditDistance;
	}

	public int getFuzzyPrefixLength() {
		return fuzzyPrefixLength;
	}

	public float getFuzzyMatchBoost() {
		return fuzzyMatchBoost;
	}

	public float getMinScore() {
		return minScore;
	}

	public float getExactMatchBoost() {
		return exactMatchBoost;
	}

	public float getPrefixMatchBoost() {
		return prefixMatchBoost;
	}

	public int getMaxResult() {
		return maxResults;
	}

	public String getFormat() {
		return format;
	}

	public void parse(HttpServletRequest req) {

		exactMatchBoost = RequestUtils.getFloatParameter(req, EXACT_MATCH_BOOST, exactMatchBoost);
		prefixMatchBoost = RequestUtils.getFloatParameter(req, PREFIX_MATCH_BOOST, prefixMatchBoost);
		maxResults = RequestUtils.getIntParameter(req, MAX_RESULTS, maxResults);

		if (maxResultsCap > 0) {
			maxResults = Math.min(maxResults, maxResultsCap);
		}

		format = RequestUtils.getStringParameter(req, FORMAT, format);
		minScore = RequestUtils.getFloatParameter(req, MIN_SCORE, minScore);
		fuzzyMatchBoost = RequestUtils.getFloatParameter(req, FUZZY_MATCH_BOOST, fuzzyMatchBoost);
		fuzzyEditDistance = RequestUtils.getIntParameter(req, FUZZY_EDIT_DISTANCE, fuzzyEditDistance);
		fuzzyPrefixLength = RequestUtils.getIntParameter(req, FUZZY_PREFIX_LENGTH, fuzzyPrefixLength);
	}

	
}
