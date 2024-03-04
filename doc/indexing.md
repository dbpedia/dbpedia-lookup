

### Example Configuration

The [index configuration](../examples/index-config.yml) looks as follows:
```yaml
version: "1.0"
indexMode: INDEX_SPARQL_ENDPOINT
sparqlEndpoint: https://dbpedia.org/sparql
indexFields:
  - fieldName: label
    documentVariable: resource
    query: >
      SELECT ?resource ?label WHERE {
        {
          ?resource <http://www.w3.org/2000/01/rdf-schema#label> ?label .
          FILTER(lang(?label) = 'en')
          #VALUES#
        }
      } 
      LIMIT 10000
```

The configuration fields describe the following (some less important fields are not described here and can be found in the configurations sections below):

* **indexMode:** In this example, we are indexing a knowledge graph using its SPARQL endpoint, hence index mode is set to INDEX_SPARQL_ENDPOINT.
* **sparqlEndpoint:** Needs to be specified when *indexMode* is set to INDEX_SPARQL_ENDPOINT. In this example, this points to the SPARQL endpoint of the DBpedia knowledge graph
* **indexFields:** The index fields are the core of a lookup indexer configuration. The conist of a list of index field objects that consist of the following:
  * **fieldName:** The name of the field to be indexed. This is **also** the name of the variable in the query that will hold the field values
  * **documentVariable:** The name of the variable in the query that will hold the ID of the target lucene document.
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

### indexFields
The index fields are the core of a lookup indexer configuration. The consist of a list of index field objects that have the following subfields:

#### fieldName
The name of the field to be indexed. This is **also** the name of the variable in the query that will hold the field values
#### documentVariable
The name of the variable in the query that will hold the ID of the target lucene document. Has to match one of the binding variables selected in [query](#query).
#### query
The SPARQL select query describing the selection of document ID and field value from the target knowledge graph. The binding variables of the select query must contain variable names matching the values specified in [fieldName](#fieldname) and [documentVariable](#documentvariable).
