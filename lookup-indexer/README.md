# Lookup Indexer

## How does it work?

The general idea behind this indexer is leveraging the power of the SPARQL query language to select specific key-value pairs from a knowledge graph and add them to a inverse index. A user can then search over values and quickly retreive associated keys using fuzzy matching.

In order to create a meaningful index structure, it is important to have a rough understanding of the knowledge graph being indexed and to design the SPARQL queries properly.

A Lucene index can be understood as a collection of documents. Each document has a unique ID and can have multiple fields with one or more values each. The document collection is indexed in a way that documents can be found by searching over the values of all or only some fields. The lookup indexer handles the process of converting a knowledge graph into such a document collection.

## Quickstart Example

The [examples folder](../examples/) contains configuration files for a search index over a part of the DBpedia knowledge graph (using [https://dbpedia.org/sparql](https://dbpedia.org/sparql)). This document will only discuss the index configuration. The searcher configuration will be addressed in the [searcher documentation](../lookup-searcher/README.md).

### Running the Example:

You can run the *Launch Index Main* setup from the [launch-config.json](../.vscode/launch.json) in Visual Studio Code.

Alternatively, you can use maven to build a `.jar` file by issuing
```
mvn package
```
and then running the resulting `lookup-indexer-1.0-jar-with-dependencies.jar` file via
```
java -jar ./target/lookup-indexer-1.0-jar-with-dependencies.jar -c ../examples/index-config.yml
```

### Example Configuration

The [index configuration](../examples/index-config.yml) looks as follows:
```yaml
version: "1.0"
indexPath: ./index
indexMode: INDEX_SPARQL_ENDPOINT
sparqlEndpoint: https://dbpedia.org/sparql
cleanIndex: true
maxBufferedDocs: 100000
commitInterval: 1000
indexFields:
  - fieldName: label
    documentVariable: city
    query: >
      SELECT ?city ?label WHERE {
        {
          ?city a <http://dbpedia.org/ontology/City> .
          ?city <http://www.w3.org/2000/01/rdf-schema#label> ?label .
          FILTER (LANG(?label) = "en").
        }
      } LIMIT 10000
  - fieldName: birthPlace
    documentVariable: person
    type: stored
    query: >
      SELECT ?person ?birthPlace WHERE {
        {
          ?person a <http://dbpedia.org/ontology/Person> .
          ?person <http://dbpedia.org/ontology/birthPlace> ?birthPlace .
        }
      } LIMIT 10000
```

The configuration fields describe the following (some less important fields are not described here and can be found in the configurations sections below):

* **indexPath:** The path of the target folder for the index structure. This can be either an empty folder or a folder containing an already existing index structure.
* **indexMode:** In this example, we are indexing a knowledge graph using its SPARQL endpoint, hence index mode is set to INDEX_SPARQL_ENDPOINT.
* **sparqlEndpoint:** Needs to be specified when *indexMode* is set to INDEX_SPARQL_ENDPOINT. In this example, this points to the SPARQL endpoint of the DBpedia knowledge graph
* **indexFields:** The index fields are the core of a lookup indexer configuration. The conist of a list of index field objects that consist of the following:
  * **fieldName:** The name of the field to be indexed. This is **also** the name of the variable in the query that will hold the field values
  * **documentVariable:** The name of the variable in the query that will hold the ID of the target lucene document.
  * **type:** The type of the field in the Lucene document. Please refer to the field type section below. This is an optional field and will default to *text*.
  * **query:** The query describing the selection of document ID and field value from the target knowledge graph.


### Testing the Queries

Before running the indexer, it is best to test each query in order to rule out any bugs that might be hard to trace later on.

Since this current example is configured to run queries against a SPARQL endpoint (index mode set to INDEX_SPARQL_ENDPOINT with [https://dbpedia.org/sparql](https://dbpedia.org/sparql)) we can simply test our queries via HTTP requests. The first configured query could be tested, by running

```sparql
SELECT ?city ?label WHERE 
{
  ?city a <http://dbpedia.org/ontology/City> .
  ?city <http://www.w3.org/2000/01/rdf-schema#label> ?label .
  FILTER (LANG(?label) = "en").
} LIMIT 10000
```

against the [DBpedia SPARQL endpoint](https://dbpedia.org/sparql?default-graph-uri=http%3A%2F%2Fdbpedia.org&query=SELECT+%3Fcity+%3Flabel+WHERE+%7B%0D%0A++++++++%7B%0D%0A++++++++++%3Fcity+a+%3Chttp%3A%2F%2Fdbpedia.org%2Fontology%2FCity%3E+.%0D%0A++++++++++%3Fcity+%3Chttp%3A%2F%2Fwww.w3.org%2F2000%2F01%2Frdf-schema%23label%3E+%3Flabel+.%0D%0A++++++++++FILTER+%28LANG%28%3Flabel%29+%3D+%22en%22%29.%0D%0A++++++++%7D%0D%0A++++++%7D+LIMIT+10000&format=text%2Fhtml&timeout=30000&signal_void=on&signal_unconnected=on).

The result is a list of bindings with two entries. The first entry (*city*) will be used a document ID, while the second value (*label*) will be written to each respective document under the field *label*. A document created by this query could look like the following:

```
Document
  id: 'http://dbpedia.org/resource/Berlin'
  label: 'Berlin'
```

A user searching for the string "Berl" over the field *label* will then be able to quickly retrieve the entire document, since the label field value partially matches the search string.

## Configuration

### indexPath
The path of the target folder for the index structure (*absolute* or *relative to the configuration file*). This can be either an empty folder or a folder containing an already existing index structure.

### dataPath
*[Optional]* This variable is only required when indexing the contents of RDF files. Points to the folder containing the files to index.

### databasePath
*[Optional]* This variable is only required when running with either build mode [BUILD_AND_INDEX_ON_DISK](#build_and_index_on_disk) or [INDEX_ON_DISK](#index_on_disk). Specifies the path of the on-disk graph database which is required for both modes.

### sparqlEndpoint 
*[Optional]* Only needs to be specified when [indexMode](#indexmode) is set to [INDEX_SPARQL_ENDPOINT](#index_sparql_endpoint). Specifies the target SPARQL endpoint URL.

### indexMode
Defines the indexing approach. Has to be one of [INDEX_IN_MEMORY](#index_in_memory), [BUILD_AND_INDEX_ON_DISK](#build_and_index_on_disk), [INDEX_ON_DISK](#index_on_disk) or [INDEX_SPARQL_ENDPOINT](#index_sparql_endpoint) (see enum [IndexMode](../lookup-indexer/src/main/java/org/dbpedia/lookup/config/IndexMode.java)). The index modes change the behaviour of the lookup indexer as follows:

#### INDEX_IN_MEMORY
The indexer loads the content of the RDF files defined at [dataPath](#datapath) into an in-memory graph database, which is then used to execute the configured SPARQL queries. Only works for small to medium files, since the index structure can eat up a lot of RAM.

#### BUILD_AND_INDEX_ON_DISK
Similar to [INDEX_IN_MEMORY](#index_in_memory), but the graph database is created on-disk (see [TDB2](https://jena.apache.org/documentation/tdb2/)). This takes considerably more time and makes querying slower but can handle much larger files, since disk space is generally more abundant than memory. The on-disk graph database will be created at the path specified in *databasePath**

#### INDEX_ON_DISK
Same as [BUILD_AND_INDEX_ON_DISK](#build_and_index_on_disk) but skips the on-disk graph database creation step. This mode tries to load an existing on-disk graph database from [databasePath](#databasepath) as a SPARQL query target.

#### INDEX_SPARQL_ENDPOINT
Runs the configured queries against the SPARQL endpoint URL specified in [sparqlEndpoint](#sparqlendpoint).

### cleanIndex
Indicates whether to start a fresh index structure or extend an existing index. Setting cleanIndex to `true` will clear the directory specified in [indexPath](#indexpath) before starting the indexing process.

### maxBufferedDocs
Configuration value passed to the lucene indexer (see [setMaxBufferedDocs()](https://lucene.apache.org/core/8_1_1/core/org/apache/lucene/index/IndexWriterConfig.html#setMaxBufferedDocs-int-)).

### commitInterval
In the Lucene indexing framework, changes to the index structure are first held in memory and then only written to disk when doing an explicit "commit". The interval denotes the maximum amount of edits between commits. A lower value results in longer indexing times but saves intermediate results more frequently. Usually set to values between 1000 to 10000.

### indexFields
The index fields are the core of a lookup indexer configuration. The consist of a list of index field objects that have the following subfields:

#### fieldName
The name of the field to be indexed. This is **also** the name of the variable in the query that will hold the field values
#### documentVariable
The name of the variable in the query that will hold the ID of the target lucene document. Has to match one of the binding variables selected in [query](#query).
#### query
The SPARQL select query describing the selection of document ID and field value from the target knowledge graph. The binding variables of the select query must contain variable names matching the values specified in [fieldName](#fieldname) and [documentVariable](#documentvariable).
#### type
The type of the index field. Influences how the value selected by the [query](#query) is tokenized and saved. Defaults to [text](#text) and may have one of the following values:

##### text
The field will be indexed and tokenized, which is useful for indexing any text with multiple words. Search queries can then return matches for each word.

##### uri
The field will be indexed but *not* tokenized or changed in any way. Useful for uris or other identifiers that should only match in its entirety. Uses the [UriAnalzyer](./src/main/java/org/dbpedia/lookup/indexer/UriAnalyzer.java).

##### string
The field will be indexed but *not* tokenized. The field value will be indexed in its *lowercase* form. [StringPhraseAnalyzer](./src/main/java/org/dbpedia/lookup/indexer/StringPhraseAnalyzer.java).


##### stored
Creates a field that is stored but not indexed. No changes to the string are applied.

##### ngram
Uses the [NGramAnalyzer](./src/main/java/org/dbpedia/lookup/indexer/NGramAnalyzer.java) to tokenize strings into ngrams of lengths between 3 and 5 characters.

##### numeric
Saves the file as a numeric field. Numeric field can be used in arithmetic operations during query time.

