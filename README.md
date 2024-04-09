# DBpedia Lookup - Generic RDF Indexer & Searcher

## About

The DBpedia Lookup can be used to index and search the contents of RDF files or databases. 

The search engine is based on the Lucene framework. RDF parsing and SPARQL querying is utilizing the Apache Jena framework, thus supporting a wide range of RDF formats.

## How does it work?

The general idea behind this indexer is leveraging the power of the SPARQL query language to select specific key-value pairs from a knowledge graph and add them to a inverse index. A user can then search over values and quickly retreive associated keys using fuzzy matching.

In order to create a meaningful index structure, it is important to have a rough understanding of the knowledge graph being indexed and to design the SPARQL queries accordingly.

A Lucene index can be understood as a collection of documents. Each document has a unique ID and can have multiple fields with one or more values each. The document collection is indexed in a way that documents can be found by searching over the values of all or only some fields. The lookup indexer handles the process of converting a knowledge graph into such a document collection.

## Quickstart Example

The [examples folder](../examples/) contains configuration files for a search index over a part of the **DBpedia knowledge graph** (using [https://dbpedia.org/sparql](https://dbpedia.org/sparql)). 

It contains 

* a configuration file for the lookup server instance ([lookup-config.yml](../examples/lookup-config.yml)) 
* a configuration file for the indexing request ([dbpedia-resource-indexer.yml](../examples/indexing/dbpedia-resource-indexer.yml))

### Step 1
Run a server instance using the provided configuration in [lookup-config.yml](../examples/lookup-config.yml).

You can run the *"Launch Lookup Server"* setup from the [launch-config.json](../.vscode/launch.json) in Visual Studio Code.
Alternatively, you can use maven to build a `.jar` file by issuing

```
mvn package
```
and then running the resulting `lookup-1.0-jar-with-dependencies` file via
```
java -jar ./target/lookup-1.0-jar-with-dependencies -c ../examples/lookup-config.yml
```

### Step 2

Run the indexing process. Issue the following HTTP request:

```
curl --request POST \
  --url https://localhost:8082/api/index/run \
  --header 'Content-Type: multipart/form-data' \
  --form config=@index-config.yml \
  --form values=http://dbpedia.org/resource/Berlin,http://dbpedia.org/resource/Leipzig,http://dbpedia.org/resource/Hamburg
```

This will send and indexing request to the indexer API that will fetch indexable data for the specified resource URIs from the DBpedia knowledge graph

### Step 3

Subsequently, the following request should return a result with the DBpedia entry of the city Berlin.

```
curl http://localhost:8082/api/search?query=Ber
```

## Configurations

There are two types of configuration files for lookup, each with their own documentation: 

* **Indexing Configuration:** [here](./doc/indexing.md).
* **Server Configuration:** [here](./doc/server.md)

## Discussion

There is a discussion thread on the DBpedia forums for questions and suggestions concerning this app and service [here](https://forum.dbpedia.org/t/new-dbpedia-lookup-application/607).

## Building the Docker Image

In order to build the docker image run:
```
cd lookup
mvn package
docker build -t lookup .
```

Do this before running `docker compose up`.
