package org.dbpedia.lookup.impl.lucene;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.FunctionScoreQuery;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.DocValuesFieldExistsQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.join.JoinUtil;
import org.apache.lucene.search.join.ScoreMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.dbpedia.lookup.ILookupSearcher;
import org.dbpedia.lookup.IndexField;
import org.dbpedia.lookup.LookupConfig;
import org.dbpedia.lookup.LookupConfig.QueryConfig;
import org.dbpedia.lookup.QueryField;
import org.json.JSONArray;
import org.json.JSONObject;
;
/**
 * Constructs queries and runs searches on the lucene index
 * @author Jan Forberg
 *
 */
public class LuceneLookupSearcher implements ILookupSearcher {

	public static final String FIELD_DOCUMENTS = "docs";
	
	public static final String FIELD_RESULT = "result";

	public static final String FIELD_REFCOUNT = "refCount";

	public static final String FIELD_RESOURCE = "resource";

	public static final String FIELD_LABEL = "label";

	public static final String FIELD_DESCRIPTION = "description";

	private float exactMatchBoost = 5.0f;

	private int fuzzyPrefixLength = 2;

	private int fuzzyEditDistance = 1;

	private IndexSearcher searcher;

	private DoubleValuesSource boostValueSource;

	private float fuzzyMatchBoost;

	private SimpleHTMLFormatter formatter;

	private DirectoryReader reader;

	private StandardAnalyzer analyzer;

	private float prefixMatchBoost;

	/**
	 * Creates a new lookup searcher
	 * @param filePath The file path of the index
	 * @throws IOException
	 * @throws java.text.ParseException
	 */
	public LuceneLookupSearcher(String filePath, LookupConfig config)
			throws IOException, java.text.ParseException {

		QueryConfig queryConfig = config.getQueryConfig();
		this.exactMatchBoost = queryConfig.getExactMatchBoost();
		this.fuzzyEditDistance = queryConfig.getFuzzyEditDistance();
		this.fuzzyPrefixLength = queryConfig.getFuzzyPrefixLength();
		this.fuzzyMatchBoost = queryConfig.getFuzzyMatchBoost();
		this.prefixMatchBoost = queryConfig.getPrefixMatchBoost();
		
		Directory index = FSDirectory.open(new File(filePath).toPath());

		this.reader = DirectoryReader.open(index);
		this.searcher = new IndexSearcher(reader);
		this.formatter = new SimpleHTMLFormatter();
		this.analyzer = new StandardAnalyzer();
		
		
		createBoostSource(config);
	}


	private void createBoostSource(LookupConfig config) {
		
		String boostFormula = config.getQueryConfig().getBoostFormula();
		
		if(boostFormula == null) {
			return;
		}
		
		try {
			Expression expression = JavascriptCompiler.compile(boostFormula);
			
			SimpleBindings bindings = new SimpleBindings();
			
			for(IndexField field : config.getIndexConfig().getIndexFields()) {
			
				if(!LookupConfig.CONFIG_FIELD_TYPE_NUMERIC.equalsIgnoreCase(field.getFieldType())) {
					continue;
				}
				
				if(!boostFormula.contains(field.getFieldName())) {
					continue;
				}
						
				DoubleValuesSource fieldSource = DoubleValuesSource.fromLongField(field.getFieldName());
				
				if(fieldSource != null) {
					bindings.add(field.getFieldName(), DoubleValuesSource.fromLongField(field.getFieldName()));
				}
			}

			this.boostValueSource = expression.getDoubleValuesSource(bindings);
			
		} catch(ParseException e) {
			this.boostValueSource = null;
		}
	}
	
	
	/**
	 * Searches the index based on a given query
	 * @param queryMap Maps a queryfield for a field specific query
	 * @param maxHits max number of search results
	 * @return
	 */
	public JSONObject search(Hashtable<QueryField, String> queryMap, int maxHits, float minRelevance, String fieldFormat) {
		
		QueryField[] fields = new QueryField[queryMap.size()];
		String[] queries = new String[queryMap.size()];
		
		int i = 0;
		
		for(Entry<QueryField, String> entry : queryMap.entrySet()) {
			fields[i] = entry.getKey();
			queries[i] = entry.getValue();
			
			i++;
		}
		
		return search(fields, queries, maxHits, minRelevance, fieldFormat);
	}

	/**
	 * Searches the index based on a given query
	 * @param maxHits The maximum amount of search results
	 * @return The search results as a string
	 */
	public JSONObject search(QueryField[] fields, String[] queries, int maxHits, float minRelevance, String fieldFormat) {

		try {
			if(fields.length != queries.length) {
				return null;
			}

			StandardAnalyzer analyzer = new StandardAnalyzer();

			BooleanQuery.Builder queryBuilder = new BooleanQuery.Builder();

			for(int i = 0; i < fields.length; i++) {

				String field = fields[i].getFieldName();
				String query = queries[i];
				boolean required = fields[i].isRequired();
				boolean allowPartialMatch = fields[i].isAllowPartialMatch();
				
				List<String> tokens;

				if(fields[i].tokenize()) {
					tokens = analyze(query, analyzer);
				} else {
					tokens =new ArrayList<String>();
					tokens.add(query.toLowerCase());
				}

				BooleanQuery.Builder tokenQueryBuilder = new BooleanQuery.Builder();

				for(String token : tokens) {

					BoostQuery boostQuery = new BoostQuery(createQueryFromToken(field, fields[i].isExact(),
							token), fields[i].getWeight());
					
					tokenQueryBuilder = tokenQueryBuilder.add(boostQuery, allowPartialMatch ? Occur.SHOULD : Occur.MUST);
				}
				
				queryBuilder = queryBuilder.add(tokenQueryBuilder.build(), required ? Occur.MUST : Occur.SHOULD);
			}

			analyzer.close();

			Query query = queryBuilder.build();

			if(boostValueSource != null) {
				query = FunctionScoreQuery.boostByValue(query, boostValueSource);
			}
			
			ArrayList<ScoredDocument> documents = runQuery(query, maxHits);
			JSONArray documentArray = new JSONArray();

			for(ScoredDocument document : documents) {
				
				if(document.getScore() < minRelevance) {
					continue;
				}

				ArrayList<Object> scoreList = new ArrayList<Object>();
				scoreList.add("" + document.getScore());
				
				HashMap<String, ArrayList<Object>> documentMap = parseResult(query, fields, document.getDocument(), fieldFormat);
				documentMap.put("score", scoreList);
				
				documentArray.put(documentMap);
			}

			JSONObject returnResults = new JSONObject();
			returnResults.put(fieldFormat.equalsIgnoreCase(LookupConfig.CONFIG_FIELD_FORMAT_XML) ? 
					FIELD_RESULT : FIELD_DOCUMENTS, documentArray);

			return returnResults;

		} catch (IOException e) {

			System.out.println("querying went wrong");
			e.printStackTrace();
		}

		return null;
	}

	/**
	 * Creates a query for a search term token. The query is composed of a prefix query,
	 * a fuzzy query with the maximum supported edit distance of 2 and an exact match query.
	 * Exact matches are weighted much higher then prefix or fuzzy matches.
	 * The weights can be adjusted via the config file
	 * @param field
	 * @param token
	 * @return
	 * @throws IOException
	 */
	public Query createQueryFromToken(String field, boolean isExact, String token) throws IOException {

		if(isExact) {
			return new BoostQuery(new TermQuery(new Term(field, token)), exactMatchBoost);
		}
		
		Term term = new Term(field, token);

		Query prefixQuery = new BoostQuery(new PrefixQuery(term), prefixMatchBoost);
		Query fuzzyQuery = new BoostQuery(new FuzzyQuery(term, fuzzyEditDistance, fuzzyPrefixLength), fuzzyMatchBoost);
		Query termQuery = new BoostQuery(new TermQuery(term), exactMatchBoost);

		BooleanQuery.Builder builder = new BooleanQuery.Builder();
		builder.setMinimumNumberShouldMatch(1);		
		
		if(prefixMatchBoost > 0) {
			builder.add(prefixQuery, Occur.SHOULD);
		}
		
		if(fuzzyMatchBoost > 0) {
			builder.add(fuzzyQuery, Occur.SHOULD);
		}
		
		if(exactMatchBoost > 0) {
			builder.add(termQuery, Occur.SHOULD);
		}
		
		return builder.build();
	}


	private List<String> analyze(String text, Analyzer analyzer) throws IOException{
		List<String> result = new ArrayList<String>();
		TokenStream tokenStream = analyzer.tokenStream(null, text);
		CharTermAttribute attr = tokenStream.addAttribute(CharTermAttribute.class);
		tokenStream.reset();
		while(tokenStream.incrementToken()) {
			result.add(attr.toString());
		}
		tokenStream.close();
		return result;
	}


	/**
	 * Searches the index using a prefix query for auto suggestions
	 * @param queryString The query string
	 * @param maxHits THe maximum amount of search results
	 * @return The search results as a string
	 * @throws IOException
	 */
	/*
	public String suggest(String queryString, int maxHits) throws IOException {

		Term term = new Term(FIELD_LABEL, queryString.toLowerCase());
		Query fuzzyQuery = new PrefixQuery(term);
		Query termQuery = new BoostQuery(new TermQuery(term), exactMatchBoost);

		BooleanQuery booleanQuery = new BooleanQuery.Builder()
				.add(fuzzyQuery, Occur.SHOULD)
				.add(termQuery, Occur.SHOULD)
				.setMinimumNumberShouldMatch(1)
				.build();

		FunctionScoreQuery scoreQuery = FunctionScoreQuery.boostByValue(booleanQuery, refCountValueSource);

		return runQuery(scoreQuery, maxHits).toString();
	}*/

	/**
	 * Runs a query and returns the results as a list of documents
	 * @param query
	 * @param maxHits
	 * @return
	 * @throws IOException
	 * @throws InvalidTokenOffsetsException
	 */
	public ArrayList<ScoredDocument> runQuery(Query query, int maxHits) throws IOException {

		TopDocs docs = searcher.search(query, maxHits);
		ScoreDoc[] hits = docs.scoreDocs;
		ArrayList<ScoredDocument> resultList = new ArrayList<ScoredDocument>();

		for(int i = 0; i < hits.length; i++) {

			int docId = hits[i].doc;
			Document document = searcher.doc(docId);
			
			ScoredDocument scoredDocument = new ScoredDocument();
			scoredDocument.setDocument(document);
			scoredDocument.setScore(hits[i].score);
			resultList.add(scoredDocument);
		}

		return resultList;
	}

	/**
	 * Creates a string-value map from a document and tries to highlight the query
	 * using the query and search fields used to retrieve the document
	 * @param document The document
	 * @return The string-value map
	 * @throws IOException
	 */
	private HashMap<String, ArrayList<Object>> parseResult(Query query, QueryField[] fields, Document document, String fieldFormat) {

		HashMap<String, ArrayList<Object>> documentMap = new HashMap<String, ArrayList<Object>>();
		
		for(IndexableField f : document.getFields()) {

			if(!documentMap.containsKey(f.name())) {
				documentMap.put(f.name(), new ArrayList<Object>());
			}

			String value = f.stringValue();
			String name = f.name();
			
			JSONObject jsonValue = new JSONObject();
			jsonValue.put("value", value);
			
			// Highlight the document field, if the field is one of the search fields
			for(QueryField field : fields) {
				
				if(name.equals(field.getFieldName()) && field.isHighlight()) {
					
	     			String highlightValue = highlightField(query, name, value);
					
					if(highlightValue != null && fieldFormat.equalsIgnoreCase(LookupConfig.CONFIG_FIELD_FORMAT_JSON)) {
						value = highlightValue;
					}
					
					if(fieldFormat.equalsIgnoreCase(LookupConfig.CONFIG_FIELD_FORMAT_JSON_FULL)) {
						jsonValue.put("highlight", highlightValue != null ? highlightValue : value);
					}
					
					break;
				}
			}
			
			if(fieldFormat.equalsIgnoreCase(LookupConfig.CONFIG_FIELD_FORMAT_JSON_FULL)) {
				documentMap.get(f.name()).add(jsonValue);
			} else {
				documentMap.get(f.name()).add(value);
			}
		}

		return documentMap;
	}

	private String highlightField(Query query, String name, String value) {

		QueryScorer scorer = new QueryScorer(query, name);

		//used to markup highlighted terms found in the best sections of a text
		Highlighter highlighter = new Highlighter(formatter, scorer);

		//It breaks text up into same-size texts but does not split up spans
		Fragmenter fragmenter = new SimpleSpanFragmenter(scorer);

		highlighter.setTextFragmenter(fragmenter);

		try {
			return highlighter.getBestFragment(analyzer, name, value);
		} catch (IOException e) {
			System.out.println("hightlighting went wrong");
			return value;
		} catch(InvalidTokenOffsetsException e) {
			System.out.println("hightlighting went wrong");
			return value;
		}
	}

	public IndexSearcher getIndexSearcher() {
		return searcher;
	}

	@Override
	public String[] findResourcesWithField(String field, boolean isStored) {

		try {
			Query exists = null;

			if(isStored) {
				exists = new PrefixQuery(new Term(field, ""));

			} else {
				exists = new DocValuesFieldExistsQuery(field);
			}

			TopDocs docs = searcher.search(exists, Integer.MAX_VALUE);
			ScoreDoc[] hits = docs.scoreDocs;

			String[] resourceIdentifiers = new String[hits.length];

			for(int i = 0; i < hits.length; i++) {

				int docId = hits[i].doc;

				Document doc = searcher.doc(docId);

				resourceIdentifiers[i] = doc.get("resource");
			}

			return resourceIdentifiers;
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public String[] findLinkedLiterals(String resource, String[] path) {

		try {
			Query head = new TermQuery(new Term("resource", resource));
			String literalField = path[path.length - 1];

			for(int i = 0; i < path.length - 1; i++) {
				head = JoinUtil.createJoinQuery(path[i], true, "resource", head, searcher, ScoreMode.None);
			}

			TopDocs pathDocs = searcher.search(head, Integer.MAX_VALUE);
			ScoreDoc[] hits = pathDocs.scoreDocs;

			ArrayList<String> literals = new ArrayList<String>();

			for(int i = 0; i < hits.length; i++) {

				int docId = hits[i].doc;
				Document doc = searcher.doc(docId);

				String[] values = doc.getValues(literalField);

				if(values != null) {

					for(String value : values) {
						literals.add(value);
					}
				}
			}

			return literals.toArray(new String[0]);
		} catch(IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	@Override
	public void close() {
		try {
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static class ScoredDocument 
	{
		private Document document;
		
		private float score;

		public Document getDocument() {
			return document;
		}

		public void setDocument(Document document) {
			this.document = document;
		}

		public float getScore() {
			return score;
		}

		public void setScore(float score) {
			this.score = score;
		}
	}
}
