package org.dbpedia.lookup.indexer;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.analysis.miscellaneous.TrimFilter;

/**
 * Analzyer setup with lowercase and trim filter
 */
public class StringPhraseAnalyzer extends Analyzer  {    

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		KeywordTokenizer tok = new KeywordTokenizer();
		
        TokenFilter lowerCaseFilter = new LowerCaseFilter(tok);
        TokenFilter trimFilter = new TrimFilter(lowerCaseFilter);
        
        return new TokenStreamComponents(tok, trimFilter);
	}
}