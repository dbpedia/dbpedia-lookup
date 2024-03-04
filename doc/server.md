# Lookup Searcher

## About

The lookup searcher is a Lucene query generator using its query builder framework. It is made accessible via a servlet that is hosted on the Lookup's jetty HTTP server. The servlet provides:

* a HTML homepage with a search window 
* a search API at `/api/search`
* an indexing API at 
    * `/api/index/run`
    * `/api/index/clear`



### Example Configuration

The [example configuration](../examples/lookup-config.yml) used above looks as follows

```yaml
version: "1.0"
indexPath: ./index
maxBufferedDocs: 1000000
logInterval: 1
exactMatchBoost: 10
prefixMatchBoost: 7
fuzzyMatchBoost: 1
fuzzyEditDistance: 2
fuzzyPrefixLength: 2
maxResults: 10000
format: JSON
minScore: 0.1
lookupFields:
  - name: id
    weight: 10
    exact: true
    highlight: false
    tokenize: false
    queryByDefault: false
  - name: label
    weight: 1
    highlight: true
    tokenize: true
    allowPartialMatch: true
    queryByDefault: true
  - name: comment
    weight: .2
    highlight: true
    tokenize: true
    allowPartialMatch: true
    queryByDefault: true
```

The configuration holds a path to the index structure that will be used for searching along with a list of variables used for scoring results. Please refer to the [configuration documentation](#configuration) for more detailed explanations.

The [queryFields](#queryfields) variable holds the most important information with a list of objects describing fields that the searcher can potentially include in the search. Each query field spefifies a field name that should be present in the index structure (as specified in the index configuration). Each field can be given default settings for score weights and other properties, such as an indicator if query inputs should be tokenized for a specific field.

## Configuration

### indexPath
The index path points to the directory holding the index structure (*absolute* or *relative to the configuration file*). This is usually the folder containing the result of the lookup indexer.

### exactMatchBoost
Denotes a multiplier that is applied to any result score if the search term matches a document field value exactly. Can be overriden via HTTP query parameter (e.g. `...&exactMatchBoost=100`).

### prefixMatchBoost
Denotes a multiplier this is applied to any result score if the search term is a prefix of a document field value. Can be overriden via HTTP query parameter (e.g. `...&prefixMatchBoost=100`).

### fuzzyMatchBoost
Denotes a multiplier this is applied to any result score if the search term roughly matches a document field value. Can be overriden via HTTP query parameter (e.g. `...&fuzzyMatchBoost=100`).

### fuzzyEditDistance
Denotes the maximum edit distance between a search input and a document field such that it is still considered a fuzzy match.

### fuzzyPrefixLength
This is the number of characters at the start of a term that must be identical (not fuzzy) to the query term if the query is to match that term.

### maxBufferedDocs
Configuration value passed to the lucene indexer (see [setMaxBufferedDocs()](https://lucene.apache.org/core/8_1_1/core/org/apache/lucene/index/IndexWriterConfig.html#setMaxBufferedDocs-int-)).

### maxResults
The maximum number of results. Can be overriden via HTTP query parameter (e.g. `...&maxResults=10`)

### format
The format of the results. Defaults to [JSON](#json) and can be one of the following:

#### JSON
Returns the result as JSON which includes highlighting tags in matched strings.

#### JSON_RAW
Returns the result as JSON without any highlighting tags.

#### JSON_FULL
Returns the result as JSON with highlighted *and* non-highlighted strings.

#### XML
Returns the result as XML.

### formatTemplate 
When the selected format is [XML](#xml) you can provide the path to an *XSL* template to transform the result into any desired XML output.

### minScore
The minimum score that a potential search result has to reach in order to be included in the result set. Can be overriden via HTTP query parameter (e.g. `...&minScore=10`).

### boostFormula 
*[Optional]* An mathematical function that will be applied to result documents based on any numeric field indexed to that document.

### lookupFields
A list of objects describing the query fields on which the searcher will operate. The objects consist of the following subfields:

#### name
The name of the field. The field name can be used as a query parameter.

#### type
The type of the field (see [Field Types](#field-types)).

#### aliases
A list of strings specifying alternative names for a field name

#### weight
A numerical weight that is applied to a match on the respective field. E.g. can be used to express, that a match on a `title` field is worth more than a match on an `abstract` field. Can be overriden via HTTP query parameter using the field name followed by the string `Weight` (e.g. `...&labelWeight=true` when searching on the field `label`).

#### highlight
Denotes whether the result should include special html tags highlighting the match between the query string and the field value. Defaults to `false`. Can be overriden via HTTP query parameter using the field name followed by the string `Highlight` (e.g. `...&labelHighlight=true` when searching on the field `label`).

#### tokenize
If `true`, the query string will be tokenized and each token will be matched against the field value of this field. Tokenization includes, for instance, splitting of input strings by whitespaces. If set to `false`, the input will be matched against the document fields as is. Defaults to `false`. Can be overriden via HTTP query parameter using the field name followed by the string `Tokenize` (e.g. `...&labelTokenize=true` when searching on the field `label`).

#### required
If `true`, a match on this field is required. A document that has a match on any other field included in the search but does *not* have a match on this field, will be dropped from the result set even if it would have been included otherwise. Defaults to `false`. Can be overriden via HTTP query parameter using the field name followed by the string `Required` (e.g. `...&labelRequired=true` when searching on the field `label`).

#### exact
If `true`, a match on this field only counts if the field value matches the query string exactly. In that case, fuzzy and prefix matching will not be applied. Defaults to `false`. Can be overriden via HTTP query parameter using the field name followed by the string `Exact` (e.g. `...&labelExact=true` when searching on the field `label`).

#### allowPartialMatch
Before matching against a field, the query string can be [tokenized](#tokenize). If [allowPartialMatch](#allowpartialmatch) is set to `false`, a field will only count as matched if all query tokens match the field. If `true`, the field will match if at least one query token matches the field. Can be overriden via HTTP query parameter using the field name followed by the string `AllowPartialMatch` (e.g. `...&labelAllowPartialMatch=true` when searching on the field `label`).

#### queryByDefault
If `true`, all queries sent with the [query](#query) parameter will be matched against this field.

## Query Parameters

### query
The most important parameter: the search query string. The query string will be matched against all fields configured as [queryByDefault](#querybydefault).

### [fieldName]
Any configured field name can be used as a query parameter. The string specified with this parameter will only be matched against the respective field.

### [fieldName]Weight
Float value. See [weight](#weight).

### [fieldName]Exact
Boolean value. See [exact](#exact).

### [fieldName]Required
Boolean value. See [required](#required).

### [fieldName]AllowPartialMatch
Boolean value. See [allowPartialMatch](#allowpartialmatch).

### [fieldName]Tokenize
Boolean value. See [tokenize](#tokenize).

### [fieldName]Highlight
Boolean value. See [highlight](#highlight).

### join
Join can specify any indexed field. The search will then be executed normally but the initial result set will not be returned. Instead, Lucene will return all documents that have a value for the specified field equal to any document id in the initial result set (simplified Lucene join).

### exactMatchBoost
Float value. See [exactMatchBoost](#exactmatchboost)

### prefixMatchBoost
Float value. See [prefixMatchBoost](#prefixmatchboost)

### fuzzyMatchBoost
Float value. See [fuzzyMatchBoost](#fuzzymatchboost)

### fuzzyEditDistance
Int value. See [fuzzyEditDistance](#fuzzyeditdistance)

### fuzzyPrefixLength
Int value. See [fuzzyPrefixLength](#fuzzyprefixlength)

### maxResults
See [maxResults](#maxresults)

### format
See [format](#format)

## Field Types
When indexing and searching it is sometimes important to explicitly set the type of a field. This will keep the indexer from tokenizing URIs or allow it to run min/max queries on numeric values.
The field type can be set to the following values:

### text
The field will be indexed and tokenized, which is useful for indexing any text with multiple words. Search queries can then return matches for each word.

### uri
The field will be indexed but *not* tokenized or changed in any way. Useful for uris or other identifiers that should only match in its entirety. Uses the [UriAnalzyer](./src/main/java/org/dbpedia/lookup/indexer/UriAnalyzer.java).

### string
The field will be indexed but *not* tokenized. The field value will be indexed in its *lowercase* form. [StringPhraseAnalyzer](./src/main/java/org/dbpedia/lookup/indexer/StringPhraseAnalyzer.java).

### stored
Creates a field that is stored but not indexed. No changes to the string are applied.

### ngram
Uses the [NGramAnalyzer](./src/main/java/org/dbpedia/lookup/indexer/NGramAnalyzer.java) to tokenize strings into ngrams of lengths between 3 and 5 characters.

### numeric
Saves the file as a numeric field. Numeric field can be used in arithmetic operations during query time. Additionally, numeric fields can be included as variables in the global boost formula

