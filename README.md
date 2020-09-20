# DBpedia Lookup

There is a discussion thread on the DBpedia forums for questions and suggestions concerning this app and service here: https://forum.dbpedia.org/t/new-dbpedia-lookup-application/607

DBpedia Lookup is a generic entity retrieval service for RDF data. It can be configured to index any RDF data and provice a retrieval services that resolves keywords to entity identifiers.

This repository contains preset projects to run the DBpedia Lookup with the DBpedia Latest Core release. This can be achieved either by building the index on your machine or by loading a pre-built index from the DBpedia Databus [here](https://databus.dbpedia.org/jan/dbpedia-lookup/index/)

## Deployment

Running the Virtuoso SPARQL Endpoint Quickstart requires Docker and Docker Compose installed on your system. If you do not have those installed, please follow the install instructions for [here](https://docs.docker.com/engine/install/) and [here](https://docs.docker.com/compose/install/).

Download any of the preset projects from the example folder, navigate to the preset folder and run 

```
docker-compose up
```

If you are using a pre-built index preset (recommended), please download the respective index structure from the DBpedia Databus [here](https://databus.dbpedia.org/jan/dbpedia-lookup/index/) to your preset folder and unpack it (use either `index_autocomplete.tar.gz` or `index_keyword.tar.gz` depending on the loaded file) before you run the container. **Only use one index file - do not mix multiple indexes together.**

```
tar -zxvf index_autocomplete.tar.gz
mv ./index_autocomplete ./index
```

Make sure that the folder containing the pre-built index is labelled `/index` as the docker container will mount this folder as a volume to load the index structure. Once the index is unpacked, your preset folder should look like this:

```
example_folder/
--- app-config.yml
--- docker-compose.yml
--- index/
--- template.xsl
```
To start the retrieval service, run
```
docker-compose up
```

## Architecture

This application is an improved and DBpedia Databus compatible version of the DBpedia Lookup Service. It is best run using docker-compose. The process of creating an running instance of the Lookup can be outlined as follows:

* The user supplies an **Application Configuration** and a **Databus Collection**
* The DBpedia Download docker contaniner loads the RDF data specified in the collection
* The Lookup container picks up the downloaded data and loads it into a graph structure that supports SPARQL queries
* The Lookup container selects indexable key-value pairs via SPARQL queries specified in the **Application Configuration**
* A Java Servlet is started on a Tomcat Server that accepts queries and executes searches on the Lucene Index as specified in the **Application Configuration**

![alt text](https://github.com/dbpedia/dbpedia-lookup/blob/master/overview.jpg "Lookup Overview")

## How to run it

Adjust the docker-compose and application configuration - then run docker-compose up:

```version: "3.0"
docker-compose up
```

### Building the docker image

YOu can use the docker image on Dockerhub. Though, if you want to build the image youself you can clone the git repository

```version: "3.0"
git clone https://github.com/dbpedia/lookup-application.git
```

and build the docker image by running

```version: "3.0"
docker build -t lookup .
```

The configuration provided in this repository can be used to replicate the DBpedia Lookup index.

## Using a Pre-Built Index (Recommended)

Building the index for the DBpedia Lookup takes some time (5-7 hours). However, you can reuse already existing indices. The latest pre-built indices of the DBpedia Lookup can be downloaded from here: https://databus.dbpedia.org/jan/dbpedia-lookup/index/ - or by running:

```
wget http://akswnc7.informatik.uni-leipzig.de/dav/dbpedia-lookup/index/2020.05.29/index.tar.gz
tar -zxvf index.tar.gz
```

Download and unpack the index on your local machine and start the application in INDEX_MODE *NONE* with the provided `app-conifg.yml` and `template.xsl` and the following modifed `docker-compose.yml`:

```
version: "3.0"
services:
  dbpedia-lookup:
    image: dbpedia/lookup-application:latest
    ports:
      - 9273:8080
    environment:
      - CONFIG_PATH=/root/app-config.yml
      - INDEX_MODE=NONE
    volumes: 
      - ./app-config.yml:/root/app-config.yml
      - ./template.xsl:/root/template.xsl
      - ./index:/root/index/
      - ./data:/root/data/
```

Make sure to pass the downloaded index files to the container via the mounted index folder. The file structure of your setup should look like this:

```
example_folder/
--- app-config.yml
--- docker-compose.yml
--- index/
--- template.xsl
```
The index folder has to contain the downloaded and unpacked index files. Then run the application with `docker-compose up`.

## Docker Compose

```
version: "3.0"
services:
  download:
    image: dbpedia/dbpedia-databus-collection-downloader:latest
    environment:
      COLLECTION_URI: https://databus.dbpedia.org/jan/collections/lookup
      TARGET_DIR: /root/data
    volumes:
      - ./data:/root/data
  dbpedia-lookup:
    image: dbpedia/lookup-application:latest
    ports:
      - 9273:8080
    environment:
      - DATAPATH=/root/data/
      - TDBPATH=/root/tdb/
      - CONFIG_PATH=/root/app-config.yml
      - INDEX_MODE=BUILD_DISK
      - CLEAN=true
    volumes: 
      - ./app-config.yml:/root/app-config.yml
      - ./template.xsl:/root/template.xsl
      - ./data:/root/data/
      - ./index:/root/index/
      - ./tdb:/root/tdb/
	

```


The docker compose loads the latest lookup docker image and exposes the service on a configurable port. Additionally, it uses the minimal-download-client from Dockerhub to download the files to index from the DBpedia databus. The minimal-download-client container takes a Databus collection URI and downloads its data. You can use an existing collection or create your own at http://databus.dbpedia.org. The provided collection (https://databus.dbpedia.org/jan/collections/lookup) includes all the required data to build the DBpedia Lookup index. 

The following environment variables of the lookup application can be set in the docker-compose:

* **DATA_PATH** The folder relative to the docker container root where the data can be found. The data should be provided by the host system via docker volume. It also makes sense to let the download and lookup services share the data volume so the lookup service can find the data once the download is finished.
* **TDB_PATH** If you select the INDEX_MODE *BUILD_DISK* or *INDEX_DISK* the lookup will use an on-disk TDB2 database. You can specifiy the path of this data structure to save it via docker volume. This can save you the trouble of rebuilding the database each time you run the docker.
* **CONFIG_PATH** The application configuration is provided via docker volume. If you prefer to change the default path of the application configuration (even thought there is really no reason to do so) you can tell the lookup application where to find the configuration file using the environment variable *CONFIG_PATH*
* **INDEX_MODE** This environment variable should be set to one of: *BUILD_MEM*, *BUILD_DISK*, *INDEX_DISC*, *NONE*. Defaults to *BUILD_MEM*.
  * **BUILD_MEM** Uses an in-memory graph structure to load and query the data. Faster than *BUILD_DISK* but requires more RAM
  * **BUILD_DISK** Uses an on-disk graph structure (TDB2) to load and query the data. This on-disk database can be saved to the host system via docker volume.
  * **INDEX_DISK** Uses an already existing on-disk graph structure (TDB2) to query the indexable key-value pairs. An already existing database has to be present. The loading step will be skipped.
  * **NONE** Completely skips the indexing path and starts up the tomcat. This can be used to quickly reload the lookup with a modified application (query) configuration.
* **CLEAN** If CLEAN is set to true the indexer will create a new index from scratch and extend an already existing index otherwise.

The lookup container will wait for the download client to finish and then index all files in the configured data path. Once the index is running, you can add more files to this folder to add them to the index. Note that this will trigger a short (few seconds) downtime when the container restarts the tomcat after reindexing.


## Application Configuration


This is the example YAML Configuration that will be present in the docker container. Note that the configuration is very specific to your data so that you will have to overwrite it with your own configuration (see the description above) in almost all cases. The provided configuration can be used to build the DBpedia Lookup index.

```
version: "1.0"
indexConfig:
  indexPath: /root/index
  cacheSize: 1000000
  commitInterval: 10000000
  indexFields:
    - fieldName: label
      resourceName: resource
      query: >
        SELECT ?resource ?label WHERE {
          ?resource <http://www.w3.org/2000/01/rdf-schema#label> ?label.
        }
    - fieldName: comment
      resourceName: resource
      query: >
        SELECT ?resource ?comment WHERE {
          ?resource <http://www.w3.org/2000/01/rdf-schema#comment> ?comment.
        }
    - fieldName: typeName
      resourceName: resource
      query: >
        SELECT ?resource (REPLACE(STR(?type), "(.*)(/|#)", "") AS ?typeName) WHERE{
          ?resource a ?type.
        }
    - fieldName: type
      resourceName: resource
      fieldType : stored
      query: >
        SELECT DISTINCT ?resource ?type WHERE{
          ?resource a ?type.
        }
    - fieldName: category
      fieldType: stored
      resourceName: resource
      query: >
        SELECT DISTINCT ?resource ?category WHERE {
          ?resource <http://purl.org/dc/terms/subject> ?category .
        }
    - fieldName: refCount
      resourceName: resource
      fieldType: numeric
      query: >
        SELECT ?resource (COUNT(?s) as ?refCount) WHERE {
          ?s ?p ?resource .
          FILTER(!isLiteral(?resource))
        } GROUP BY ?resource
queryConfig:
  exactMatchBoost: 5
  prefixMatchBoost: 2
  fuzzyMatchBoost: 1
  fuzzyEditDistance: 2
  fuzzyPrefixLength: 2
  boostFormula: 0.1*ln(refCount+1)+1
  maxResults: 100
  format: XML
  formatTemplate: /root/template.xsl
  minRelevanceScore: 0.1
  queryFields:
    - fieldName: label
      weight: 10
      highlight: true
      queryByDefault: true
    - fieldName: category
      weight: 1
      highlight: true
      queryByDefault: true
    - fieldName: comment
      weight: 3
      highlight: true
      queryByDefault: true
    - fieldName: typeName
      weight: 0.1
      highlight: false
      required: true
      exact: true
      aliases:
        - QueryClass

```

The Configuration is split into the index configuration and the query configuration. The index configuration can be used to configure the indexing step, the query configuration can be used to configure the query servlet.

### Index Configuration

**indexPath** The path relative to the container root where the lucene index will be located

**cacheSize** The maximum number of documents that can be buffered by the indexer before a flush is required. A higher number will consume more RAM but also lead to faster indexing.

**commitInterval** The amount of updates on the index before changes are commited. Additionally, the commit will invoke garbage collection. More frequent garbage collection (lower values for *commitInterval*) can help prevent a Java Heap Space error that can occur when working with large data sets.

**indexFields** This configuration field is the most important one for the indexing process and consists of a list of index fields. Each index field has the following sub-fields

* **fieldName** The name of the field. This will be used to fetch the field value from the SPARQL query as well as for the field name in the index.
* **fieldType** The type of the field. Allowed values are *numeric*, *stored*, *text* and *string*. Defaults to *text*. Any number valued field has to be set to numeric. Stored fields will only be saved with the document but not indexed. String fields will be indexed without being tokenized. Text is the default tokenized indexed field.
* **resourceName** The SPARQL variable name of the resource to index
* **query** Before indexing, all RDF data is stored in a graph database. The Lookup indexer indexes resource URIs (the document id) with various fields. The selection of indexable key-value-pairs is done by a SPARQL query. The SPARQL has to return a result set with 2 columns matching the names of the **fieldName** and **resourceName**. Please refer to the default configuration above for examples.

### Important Note on Queries

When selecting key-value pairs with SPARQL queries using Apache Jena, the API will cache resources on the JVMs heap depending on the query. For large data sets this might turn into a problem in the form of an OutOfMemoryException. To prevent this issue the JVMs heap space is increased in the Dockerfile of this repository. If needed, the heap space can be increased even further (inrease the respective value for `Xmx` and `Xms`, rebuild the docker image and restart the indexing process).

One of the most efficient and easiest workarounds to the OutOfMemory issue is omitting the DISTINCT keyword from queries that will return a large amount of bindings. Whenever you use the DISTINCT keyword in a query, Jena will fill up its cache with already processed bindings in order to skip bindings it has already seen. (see https://stackoverflow.com/questions/45992176/java-outofmemoryerror-in-apache-jena-using-tdb) In our case the DISTINCT checks would usually make indexing significantly slower or even lead to an OutOfMemoryException when dealing with query result sets of around `30m` or more bindings.


### Index Field Examples

#### Example 1: Simple Label Indexing
```
- fieldName: label
  resourceName: resource
  query: >
    SELECT ?resource ?label WHERE {
      ?resource <http://www.w3.org/2000/01/rdf-schema#label> ?label.
    }
```
The above query will be run against the loaded data and return bindings representing indexable key-value-pairs. Thus, it will create a document for each URI that has an rdf:label indexed with the retrieved label literal.

For the resource 'http://dbpedia.org/resource/Berlin' an indexed document might look like this:
```
Document
  resource: 'http://dbpedia.org/resource/Berlin'
  label: 'Berlin'
```

#### Example 2: Refcount Indexing
```
- fieldName: refCount
  resourceName: resource
  numeric: true
  query: >
    SELECT ?resource (COUNT(?s) as ?refCount) WHERE {
      ?s ?p ?resource .
      FILTER(!isLiteral(?resource))
    } GROUP BY ?resource
```
The query selects all non-literal objects and counts the amount of subject linked to it. The field refCount is a number, thus the configuration *numeric* is set to true. This field is then later used for query boosting with the following formula (see query configuration)

```
queryConfig:
  ...
  boostFormula: 0.1*ln(refCount+1)+1
  ...
```

This way you can select all sorts of numeric fields and include them in the scoring function.
For the resource 'http://dbpedia.org/resource/Berlin' an indexed document might look like this (assuming that the label has already been indexed):

```
Document
  resource: 'http://dbpedia.org/resource/Berlin'
  label: 'Berlin'
  refCount: 4671
```

#### Example 3: Indexing URI Parts

```
- fieldName: uriPart
  resourceName: uri
  query: >
    SELECT ?uri ?uriPart WHERE {
      ?uri ^<http://dataid.dbpedia.org/ns/core#artifact>|^<http://dataid.dbpedia.org/ns/core#group>|^<http://xmlns.com/foaf/0.1/account> ?o .
      BIND(STR(?uri) AS ?uriStr) .
      ?uriPart <http://jena.hpl.hp.com/ARQ/property#strSplit> (?uriStr "/") .
      FILTER(?uriPart != "")
      FILTER(?uriPart != "https:")
      FILTER(?uriPart != "http:")
      FILTER(?uriPart != "databus.dbpedia.org")
    }
```
This example is not included in the application configuration of this repository. It is however used to build the search index of the DBpedia Databus. The query uses `<http://jena.hpl.hp.com/ARQ/property#strSplit>` to select the URI parts of specific resource as strings. 

For the resource 'https://databus.dbpedia.org/dbpedia/mappings/specific-mappingbased-properties/' an indexed document might look like this :

```
Document
  resource: 'https://databus.dbpedia.org/dbpedia/mappings/specific-mappingbased-properties/'
  uriPart: [ 'dbpedia', 'mappings', 'specific-mappingbased-properties' ]
```

### Query Configuration

**exactMatchBoost** The multiplier applied to the retrieval score when a search term matches a field exactly (e.g. applied to a result with the field "DBpedia" when searching for "DBpedia" )

**prefixMatchBoost** The multiplier applied to the retrieval score when a search term is a prefix of a field (e.g. applied to a result with the field "DBpedia" when searching for "DBp" )

**fuzzyMatchBoost** The multiplier applied to the retrieval score when a search term matches a field with some minor mistakes (e.g. applied to a result with the field "DBpedia" when searching for "DBp**o**dia" )

**fuzzyEditDistance** The maximum of this parameter is 2. The number of mistakes in a search term to be still considered a fuzzy match of a field (e.g. "DBpedia" and "DBp**o**dia" have an edit distance of 1)

**fuzzyPrefixLength** This is the number of characters at the start of a term that must be identical (not fuzzy) to the query term if the query is to match that term.

**maxResults** The maximum number of results returned in a single search.

**maxResultsCap** The hard cap for the maximum number of results returned in a single search.

**minRelevanceScore** The minimum score a document has to receive in a certain search to appear in the result set. This is helpful when dealing with required query fields with weights equal to zero - documents without the field will be omitted as well as documents that only have a match in the zero weight field.

**format** This can either be omitted (defaults to XML) or set to either XML, JSON or JSON_RAW or JSON_FULL. JSON will return all fields as json with highlighting tags (if the field is set to highlighted and highlightable tokens were found). JSON_RAW will return json with all fields *without* the highlighting tags. JSON_FULL will return a json object with the fields *value* and *highlight*. The *value* field contains the field value without highlighting, the *highlight* field contains the field value with highlighting tags (if any).

**formatTemplate** When the selected format is *XML* you can provide a *XSL* template to transform the result into any desired output.

**queryFields** A list of query fields, each with the following sub-fields

* **fieldName** The name of the search field (matching the ones in the indexing process)
* **weight** The default weight applied to the field when searching
* **highlight** Indicates whether matches should be highlighted in the search result for this field
* **required** Indicates whether the result MUST have matches for this field. **IMPORTANT: This  does not apply when the search does not include the field!**
* **queryByDefault** Indicates whether the field will be queried when using the *query* request parameter
* **exact** Indicates whether the field allows fuzzy matching or requires an exact match (default is false)
* **tokenize** Indicates whether the query string for this field should be tokenized.
* **aliases** A list of query parameter aliases for the query field.

## Using the Lookup Service

Once the docker container is running, you can query the index at the following address:

http://localhost:9273/lookup-application/api/search?query=YOUR_QUERY


### Static Query Parameters

* **query** This will query the index with the specified value on all fields with **queryByDefault** set to *true*
* **maxResults** The maximum number of results, overrides the value in the configuration.
* **format** The format of the lookup result, overrides the value in the configuration.
* **minRelevance** The minimum relevance score of the query, overrides the **minRelevanceScore** value in the configuration

### Dynamic Query Parameters

* **[FIELD_NAME]** You can search for a field name directly to query only for that field. Using both the static **query** parameter together with a field name will apply the value to all fields as if using only the **query** parameter and then override the ones specified with the field name directly.
* **[FIELD_NAME]Weight**  Modifies the weight for a specific field
* **[FIELD_NAME]Required** Overrides the default setting of the required parameter of a query field.


### Examples


#### Example 1: Searching on a Specific Field
```
http://localhost:9273/lookup-application/api/search?label=Berlin
```

This search will only be run on the "label" field with the term "Berlin"

#### Example 2: Searching on all 'QueryByDefault' Fields
```
http://localhost:9273/lookup-application/api/search?query=Berlin
```
This search will only be run on all fields that are configured to be queried by default (see Query Configuration). In the provided configuration this will perform a search over the `label`, `comment` and `category` field. The `typeName` field will be ignored.

#### Example 3: Including Non-Default Fields in the Search
```
http://localhost:9273/lookup-application/api/search?query=Berlin&typeName=City
```
This search will be run on all default fields (`label`, `comment`, `category`) *and* the `typeName` field. Note that the `typeName` field is configured to only accept **exact** matches *and* that it is set to **required**. This means that all documents will be omitted that do not have an exact match on 'City' as their `typeName`. 

Also note that the configurations **required** and **exact** are both needed to achieve this sort of query behaviour. Setting the `typeName` field to only **required** will allow Documents with a fuzzy match on the `typeName` field to appear in the result set. Setting the field to only **exact** won't exlcude Documents from the result set but simply assign them a lower score.

The field configuration can be altered in the query itself by using the dynamic search parameters:
```
http://localhost:9273/lookup-application/api/search?query=Berlin&typeName=City&typeNameRequired=true
```

#### Example 4: Overriding Queries for Specific Fields
```
http://localhost:9273/lookup-application/api/search?query=Berlin&label=Leipzig
```

This will search all fields for the term 'Berlin' except for the `label` field. The `label` field will be searched for the term 'Leipzig' instead.





