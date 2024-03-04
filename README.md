# DBpedia Lookup - Generic RDF Indexer & Searcher

## About

The DBpedia Lookup can be used to index and search RDF files or databases. 

The indexer and searcher uses the Lucene framework for the index structures and queries - and the Apache Jena framework for RDF and SPARQL parsing and loading, thus supporting a wide range of RDF formats.

### Running the Server:

You can run the *"Launch Lookup Server"* setup from the [launch-config.json](../.vscode/launch.json) in Visual Studio Code.

Alternatively, you can use maven to build a `.jar` file by issuing
```
mvn package
```
and then running the resulting `lookup-1.0-jar-with-dependencies` file via
```
java -jar ./target/lookup-1.0-jar-with-dependencies -c ../examples/lookup-config.yml
```

## How does it work?

The general idea behind this indexer is leveraging the power of the SPARQL query language to select specific key-value pairs from a knowledge graph and add them to a inverse index. A user can then search over values and quickly retreive associated keys using fuzzy matching.

In order to create a meaningful index structure, it is important to have a rough understanding of the knowledge graph being indexed and to design the SPARQL queries properly.

A Lucene index can be understood as a collection of documents. Each document has a unique ID and can have multiple fields with one or more values each. The document collection is indexed in a way that documents can be found by searching over the values of all or only some fields. The lookup indexer handles the process of converting a knowledge graph into such a document collection.

## Quickstart Example

The [examples folder](../examples/) contains configuration files for a search index over a part of the DBpedia knowledge graph (using [https://dbpedia.org/sparql](https://dbpedia.org/sparql)). 
It contains a configuration file for the lookup server instance ([lookup-config.yml](../examples/lookup-config.yml)) and a configuration for the indexing request ([dbpedia-resource-indexer.yml](../examples/indexing/dbpedia-resource-indexer.yml))

Run the server as described above to use the provided configuration in [lookup-config.yml](../examples/lookup-config.yml).
In order to run the indexer, issue the following HTTP request:

```
curl --request POST \
  --url https://localhost:8082/api/index/run \
  --header 'Content-Type: multipart/form-data' \
  --form config=@index-config.yml \
  --form values=http://dbpedia.org/resource/Berlin,http://dbpedia.org/resource/Leipzig,http://dbpedia.org/resource/Hamburg
```

Subsequently, the following request should return a result with the DBpedia entry of the city Berlin.

```
curl http://localhost:8082/api/search?query=Ber
```

## Configurations

There are two configuration types for lookup: The server configuration and the indexing request configuration. Both configurations use the YAML syntax. 
You can find the documentation for each configuration type in the following documents:

This software is split up into two modules:
* **Indexing Configuration:** [here](./doc/indexing.md).
* **Server Configuration:** [here](./doc/server.md)

## Discussion

There is a discussion thread on the DBpedia forums for questions and suggestions concerning this app and service [here](https://forum.dbpedia.org/t/new-dbpedia-lookup-application/607).
