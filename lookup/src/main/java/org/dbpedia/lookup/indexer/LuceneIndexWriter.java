package org.dbpedia.lookup.indexer;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.util.BytesRef;
import org.dbpedia.lookup.Constants;
import org.dbpedia.lookup.config.IndexField;
import org.dbpedia.lookup.config.LookupConfig;
import org.dbpedia.lookup.config.LookupField;
import org.slf4j.Logger;

public class LuceneIndexWriter {

	private IndexWriter indexWriter;

	private IndexSearcher searcher;

	private Logger logger;

	private ConcurrentHashMap<String, Document> documentCache;

	private String lastDocument = null;

	private String lastValue = null;

	private File stagingDirectory = null;

	private LookupConfig config;


	public LuceneIndexWriter(Logger logger, IndexWriter indexWriter, LookupConfig config) throws IOException {
		
		this.logger = logger;
		this.indexWriter = indexWriter;
		this.config = config;

		// Create a document cache to keep documents in between commits to the lucene structure
		documentCache = new ConcurrentHashMap<String, Document>();
		// Create the writer and searcher
		try {
			searcher = new IndexSearcher(DirectoryReader.open(indexWriter.getDirectory()));
		} catch(IOException exception) {
			searcher = null;
		}
	}

	/*
	public LuceneIndexWriter(IndexConfig indexConfig, Logger logger) {

		this.logger = logger;
		this.indexConfig = indexConfig;
		this.targetPath = indexConfig.getIndexPath(); 

		// Create a document cache to keep documents in between commits to the lucene structure
		documentCache = new ConcurrentHashMap<String, Document>();


		try {
			// Open the target directory
			targetDirectory = FSDirectory.open(Paths.get(targetPath));

			File file = new File(this.indexPath);
			file.mkdirs();

		} catch (IndexNotFoundException e) {
			return;
		} catch (IOException e) {
			cleanUp();
			e.printStackTrace();
			return;
		}
	}  */

	/*
	public void purge() {
		try {
			indexWriter.close();
			// Remove lock file
			File lockFile = new File(indexPath + lockFileName);
			lockFile.delete();

			File targetFile = new File(indexPath);
			FileUtils.cleanDirectory(targetFile);

			
			indexWriter = new IndexWriter(indexDirectory, createWriterConfig(maxBufferedDocs));
			searcher = new IndexSearcher(DirectoryReader.open(indexDirectory));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}*/

	public void clear() {
		try {
			// DELETUS!
			indexWriter.deleteAll();
			// Commit the changes
			commit();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}
	

	public void cleanUp() {
		if(this.stagingDirectory != null) {
			for(File file : this.stagingDirectory.listFiles()) {
				file.delete();	
			}

			this.stagingDirectory.delete();
			this.stagingDirectory = null;
		}
	}

	public void indexResult(ResultSet result, IndexField path) {

		int k = 0;

		while (result.hasNext()) {

			QuerySolution entry = result.next();
			RDFNode documentNode = entry.get(path.getDocumentVariable());
			RDFNode fieldValueNode = entry.get(path.getFieldName());

			lastDocument = documentNode.toString();

			if (fieldValueNode.isLiteral()) {
				lastValue = fieldValueNode.asLiteral().getString();
			} else {
				lastValue = fieldValueNode.toString();
			}

			indexField(lastDocument, path, lastValue);

			if (k % config.getLogInterval() == 0) {
				logger.info("Binding " + k + ": [" + lastDocument + "] -> \"" + lastValue + "\"");
			}

			k++;
		}
	}

	public void indexField(String documentId, IndexField indexField, String valueString) {

		String field = indexField.getFieldName();
		String fieldType = indexField.getType();

		if (fieldType != null) {
			fieldType = fieldType.toLowerCase();
		} else {
			
			LookupField lookupField = config.getLookupField(field);

			if(lookupField != null && lookupField.getType() != null) {
				fieldType = lookupField.getType();
			} else {
				fieldType = Constants.CONFIG_FIELD_TYPE_TEXT;
			}
		}

		try {
			Document doc = findDocument(documentId);
			
			if(valueString != null) {
				IndexableField[] existingFields = doc.getFields(field);

				for(IndexableField existingField : existingFields) {
					if(existingField != null && valueString.equals(existingField.stringValue())) {
						return;
					}
				}
			}
			
			switch (fieldType) {
				case Constants.CONFIG_FIELD_TYPE_NUMERIC:
					long value = Long.parseLong(valueString);
					doc.removeFields(field);
					doc.add(new StoredField(field, value));
					doc.add(new LongPoint(field, value));
					doc.add(new NumericDocValuesField(field, value));
					break;
				case Constants.CONFIG_FIELD_TYPE_STORED:
					doc.add(new StoredField(field, valueString));
					break;
				case Constants.CONFIG_FIELD_TYPE_STRING:
					doc.add(new StringField(field, valueString, Field.Store.YES));
					break;
				case Constants.CONFIG_FIELD_TYPE_STORED_SORTED:
					doc.add(new StoredField(field, valueString));
					doc.add(new SortedSetDocValuesField(field, new BytesRef(valueString)));
					break;
				case Constants.CONFIG_FIELD_TYPE_URI:
					doc.add(new StringField(field, valueString, Field.Store.YES));
					break;
				default:
					doc.add(new TextField(field, valueString, Field.Store.YES));
					break;
			}
			
			indexWriter.updateDocument(new Term(Constants.FIELD_DOCUMENT_ID, documentId), doc);

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Document findDocument(String documentId) throws IOException {

		if(documentId == null) {
			return null;
		}

		if (documentCache.containsKey(documentId)) {
			return documentCache.get(documentId);
		}

		// First try: search the index
		Document document = getDocumentFromIndex(documentId);

		if (document == null) {
			// Second try: create new document
			document = new Document();

			document.add(new StringField(Constants.FIELD_DOCUMENT_ID, documentId, Field.Store.YES));
			document.add(new SortedDocValuesField(Constants.FIELD_DOCUMENT_ID, new BytesRef(documentId)));
		}

		// we touched the document, we might touch it again soon -> add to cache
		documentCache.put(documentId, document);
		return document;
	}

	private Document getDocumentFromIndex(String documentId) throws IOException {

		if (searcher == null) {
			return null;
		}

		Term documentIdTerm = new Term(Constants.FIELD_DOCUMENT_ID, documentId);
		TermQuery documentIdTermQuery = new TermQuery(documentIdTerm);

		TopDocs docs = searcher.search(documentIdTermQuery, 1);
 		StoredFields storedFields = searcher.storedFields();
 		
		if (docs.scoreDocs.length > 0) {

			ScoreDoc scoreDoc = docs.scoreDocs[0]; 
			Document document = storedFields.document(scoreDoc.doc);
				
			document.removeFields(Constants.FIELD_DOCUMENT_ID);
			document.add(new StringField(Constants.FIELD_DOCUMENT_ID, documentId, Field.Store.YES));
			document.add(new SortedDocValuesField(Constants.FIELD_DOCUMENT_ID, new BytesRef(documentId)));
			
			// Fetched document fields are all reset to default stored/text fields
			// Special fields such as the numeric fields have to be reset to their
			// respective type
			for (LookupField field : config.getLookupFields()) {

				String fieldType = field.getType();

				if (fieldType == null) {
					continue;
				}

				if(fieldType.contentEquals(Constants.CONFIG_FIELD_TYPE_STORED_SORTED)) {
					IndexableField[] storedSortedFields = document.getFields(field.getName());
					String fieldName = field.getName();
					document.removeFields(fieldName);

					for(IndexableField storedField : storedSortedFields) {
						String value = storedField.stringValue();
						// Re-add the field
						document.add(new StoredField(fieldName, value));
						document.add(new SortedSetDocValuesField(fieldName, new BytesRef(value)));
					}
				
				}

				// Remove all numeric index fields and re-add them to the document as
				// NumericDocValuesField (plus StoredField for retrieval)
				if (fieldType.contentEquals(Constants.CONFIG_FIELD_TYPE_NUMERIC)) {

					// Fetch the field with the given index field name (does not support
					// multi-values)
					IndexableField numericField = document.getField(field.getName());

					// Only do this if a field with the given field name exists
					if (numericField != null) {

						// Fetch the field long value
						long value = numericField.numericValue().longValue();

						// Remove all fields with the respective name
						document.removeFields(field.getName());

						// Re-add the field
						document.add(new NumericDocValuesField(field.getName(), value));
						document.add(new StoredField(field.getName(), value));
						document.add(new LongPoint(field.getName(), value));
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

			documentCache = new ConcurrentHashMap<String, Document>();
			searcher = new IndexSearcher(DirectoryReader.open(indexWriter.getDirectory()));
			System.gc();

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void finish() {
		
	}

	public void test() {

	}

}
