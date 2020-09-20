package org.dbpedia.lookup.impl.lucene;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.dbpedia.lookup.IndexField;
import org.dbpedia.lookup.LookupConfig;
import org.dbpedia.lookup.LookupConfig.IndexConfig;
import org.slf4j.Logger;


public class LuceneLookupIndexer {

	private IndexWriter indexWriter;

	private IndexSearcher searcher;

	private FSDirectory index;

	private String filePath;

	private IndexConfig indexConfig;

	private Logger logger;

	private ConcurrentHashMap<String, Document> documentCache;

	private Analyzer analyzer;


	
	/**
	 * Creates a new lucene lookup indexer
	 * @param filePath File path to use for indexing
	 * @param updateInterval Max amount of updates before a commit
	 * @param cacheSize Max cache size
	 */
	public LuceneLookupIndexer(IndexConfig indexConfig, Logger logger, boolean cleanIndex) {
		
		this.logger = logger;
		
		this.indexConfig = indexConfig;
		this.filePath = indexConfig.getIndexPath();
		
		// Create a document cache to keep documents in between commits to the lucene structure
		documentCache = new ConcurrentHashMap<String, Document>();
			
		// Create a new analyzer for the given index configuration
		analyzer = createAnalyzer(indexConfig);
		
		try {
			// Open the index directory
			index = FSDirectory.open(Paths.get(filePath));
			
			// Create an index writer with index directory and index configuration
			indexWriter = new IndexWriter(index, createWriterConfig());
			
			File file = new File(filePath);
			file.mkdirs();
			
			searcher = new IndexSearcher(DirectoryReader.open(index));
			
			if(cleanIndex) {
				clearIndex();
			} 
			
		} catch(IndexNotFoundException e) {
			return;
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}


	private IndexWriterConfig createWriterConfig() {
		IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
		indexWriterConfig.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
		indexWriterConfig.setMaxBufferedDocs(indexConfig.getCacheSize());
		return indexWriterConfig;
	}


	private Analyzer createAnalyzer(IndexConfig indexConfig) {
		
		Map<String, Analyzer> analyzerPerField = new HashMap<String, Analyzer>();
		
		for(IndexField field : indexConfig.getIndexFields()) {
			
			String fieldType = field.getFieldType();
			
			if(fieldType == null) {
				continue;
			} 
			
			if(fieldType.contentEquals(LookupConfig.CONFIG_FIELD_TYPE_STRING)) {
				analyzerPerField.put(field.getFieldName(), new StringPhraseAnalyzer());
			}
		}
		
		Analyzer analyzer = new PerFieldAnalyzerWrapper(new StandardAnalyzer(), analyzerPerField);
		return analyzer;
	}
	

	public void indexResult(ResultSet result, IndexField path) {
		
		String lastResource = null;
		String lastValue = null;
		int k = 0;
		
		while(result.hasNext()) {
			
			QuerySolution entry = result.next();
			RDFNode resource = entry.get(path.getResourceName());
			RDFNode value = entry.get(path.getFieldName());
			
			lastResource = resource.toString();
			
			if(value.isLiteral()) {
				lastValue = value.asLiteral().getString();
			} else {
				lastValue = value.toString();
			}
			
			indexField(lastResource, path, lastValue);
			
			k++;
			
			if(k % indexConfig.getCommitInterval() == 0) {
				logger.info("Latest: " + lastResource + " --> '" + lastValue + "'");
				commit();
			}
		}
	}


	
	public void indexField(String resource, IndexField path, String literal) {

		String field = path.getFieldName();
		String fieldType = path.getFieldType();
		
		if(fieldType != null) {
			fieldType = fieldType.toLowerCase();
		} else {
			fieldType = LookupConfig.CONFIG_FIELD_TYPE_TEXT;
		}
		
		try {
			Document doc = findDocument(resource);
			
			
			switch(fieldType) {
				case LookupConfig.CONFIG_FIELD_TYPE_NUMERIC:
					long value = Long.parseLong(literal);
					doc.removeFields(field);
					doc.add(new StoredField(field, value));
					doc.add(new NumericDocValuesField(field, value));
					break;
				case LookupConfig.CONFIG_FIELD_TYPE_STORED:
					doc.add(new StoredField(field, literal));
					break;
				default:
					doc.add(new TextField(field, literal, Field.Store.YES));
					break;
			}
			
			indexWriter.updateDocument(new Term(LuceneLookupSearcher.FIELD_RESOURCE, resource), doc);
		
		} catch (IOException e) {
			e.printStackTrace();
		}
	}


	private Document findDocument(String resource) throws IOException {

		if(documentCache.containsKey(resource)) {
			return documentCache.get(resource);
		}
		
		// First try: search the index
		Document document = getDocumentFromIndex(resource);

		if(document == null) {
			// Second try: create new document, add to cache
			document = new Document();
			document.add(new StringField(LuceneLookupSearcher.FIELD_RESOURCE, resource, Field.Store.YES));
			
		}
	
		documentCache.put(resource, document);
		return document;
	}

	private Document getDocumentFromIndex(String resource) throws IOException {

		if(searcher == null) {
			return null;
		}
		
		// resourceTerm.set(LuceneLookupSearcher.FIELD_RESOURCE, new BytesRef(resource));

		Term resourceTerm = new Term(LuceneLookupSearcher.FIELD_RESOURCE, resource);
		TermQuery resourceTermQuery = new TermQuery(resourceTerm);
	
		
		TopDocs docs = searcher.search(resourceTermQuery, 1);
		
		if(docs.totalHits > 0) {

			Document document = searcher.doc(docs.scoreDocs[0].doc);
			document.removeField("resource");
			document.add(new StringField("resource", resource, Field.Store.YES));
			
			// Fetched document fields are all reset to default stored/text fields
			// Special fields such as the numeric fields have to be reset to their respective type
			for(IndexField field : indexConfig.getIndexFields()) {
				
				String fieldType = field.getFieldType();
				
				if(fieldType == null) {
					continue;
				}
				
				// Remove all numeric index fields and re-add them to the document as
				// NumericDocValuesField (plus StoredField for retrieval)
				if(fieldType.contentEquals(LookupConfig.CONFIG_FIELD_TYPE_NUMERIC)) {
					
					// Fetch the field with the given index field name (does not support multi-values)
					IndexableField numericField = document.getField(field.getFieldName());
					
					// Only do this if a field with the given field name exists
					if(numericField != null) {
						
						// Fetch the field long value
						long value = numericField.numericValue().longValue();
						
						// Remove all fields with the respective name
						document.removeFields(field.getFieldName());
						
						// Re-add the field
						document.add(new NumericDocValuesField(field.getFieldName(), value));
						document.add(new StoredField(field.getFieldName(), value));
					}
				}
			}
			
			return document;
		}

		return null;
	}

	public void commit() {
		
		logger.info("=== COMMITING ===");


		try {
			indexWriter.commit();
			indexWriter.close();
			
			index = FSDirectory.open(new File(filePath).toPath());

			indexWriter = new IndexWriter(index, createWriterConfig());
		
			IndexReader reader = DirectoryReader.open(index);
			searcher = new IndexSearcher(reader);
			documentCache = new ConcurrentHashMap<String, Document>();

			System.gc();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
	}

	public boolean clearIndex() {

		try {
			indexWriter.deleteAll();
			commit();
			return true;
		} catch(IndexNotFoundException e) {
			return true;
		} catch (IOException e) {
			e.printStackTrace();
		}

		return false;
	}


	public void test() {
		
		
	}
	

}
