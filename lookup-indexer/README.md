# Lookup Indexer

## How does it work?

The general idea behind this indexer is leveraging the power of the SPARQL query language to select specific key-value pairs from a knowledge graph and add them to a inverse index. A user can then search over values and quickly retreive associated keys using fuzzy matching.

In order to create a meaningful index structure, it is important to have a rough understanding of the knowledge graph being indexed and to design the SPARQL queries properly.

A Lucene index can be understood as a collection of documents. Each document has a unique ID and can have multiple fields with one or more values each. The document collection is indexed in a way that documents can be found by searching over the values of all or only some fields. The lookup indexer handles the process of converting a knowledge graph into such a document collection.

## Quickstart Example

The [examples folder](../examples/) contains configuration files for a search index over a part of the DBpedia knowledge graph (using [https://dbpedia.org/sparql](https://dbpedia.org/sparql)). This document will only discuss the index configuration. The servlet configuration will be addressed in the [servlet documentation](../lookup-servlet/README.md).

### Configuration

The [index configuration](../examples/index-config.yml) looks as follows:
```yaml
version: "1.0"
indexPath: ./index
indexMode: INDEX_SPARQL_ENDPOINT
sparqlEndpoint: https://dbpedia.org/sparql
cleanIndex: true
cacheSize: 1000000
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
        %VALUES%
      } LIMIT 10000
```

The configuration fields describe the following:

* *indexPath:* The path of the target folder for the index structure. This can be either an empty folder or a folder containing an already existing index structure.
* *indexMode:* Has to be one of BUILD_MEM, BUILD_DISK, INDEX_DISK, INDEX_SPARQL_ENDPOINT (see [IndexMode](../lookup-indexer/src/main/java/org/dbpedia/lookup/config/IndexMode.java) and the index mode section below). In this example, we are indexing a knowledge graph through its SPARQL endpoint, hence INDEX_SPARQL_ENDPOINT.
* *cleanIndex:* Indicates whether we want to start a fresh index structure or extend an existing index.
* *sparqlEndpoint:* Needs to be specified when *indexMode* is set to INDEX_SPARQL_ENDPOINT. In this example, this points to the SPARQL endpoint of the DBpedia knowledge graph
* *cacheSize:* Indicates the amount of documents that will be held in memory during indexing. This speeds up the process of adding multiple fields to a single document. This can be limited to avoid excessive RAM usage.
* *commitInterval:* In the Lucene indexing framework, changes to the index structure are first held in memory and then only written to disk when doing an explicit "commit". The interval denotes the maximum amount of inserts between commits. A lower value results in longer indexing times but saves intermediate results more frequently
* *indexFields:* The index fields are the core of a lookup indexer configuration. The conist of a list of index field objects that consist of the following:
  * *fieldName:* The name of the field to be indexed. This is **also** the name of the variable in the query that will hold the field values
  * *documentVariable:* The name of the variable in the query that will hold the ID of the lucene documents.
  * *type*: The type of the field in the Lucene document. Please refer to the field type section below. This is an optional field and will default to *text*.
  * *query:* The query describing the selection of document ID and field value from the target knowledge graph.


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

### Running the Example:

You can run the *Launch Index Main*](./src/main/java/org/dbpedia/lookup/Main.java)* setup from the [launch-config.json](../.vscode/launch.json) in Visual Studio Code.

Alternatively, you can use maven to build a `.jar` file by issuing
```
mvn package
```
and then running the resulting `lookup-indexer-1.0-jar-with-dependencies.jar` file via
```
java -jar ./target/lookup-indexer-1.0-jar-with-dependencies.jar -c ../examples/index-config.yml
```