# DBpedia Lookup - Generic RDF Indexer

## About

The DBpedia Lookup can be used to index and search RDF files or databases. 

The indexer and searcher uses the Lucene framework for the index structures and queries and the Apache Jena framework for RDF and SPARQL parsing and loading, thus supporting a wide range of RDF formats.

## Modules

This software is split up into two modules:
* **Lookup-Indexer:** Index-time java program utilizing Apache Jena, Lucene and SPARQL queries to create an index structure. You can find an extensive documentation [here](./lookup-indexer/README.md).
* **Lookup-Servlet:** Query-time lightweight jetty-based server hosting a HTTP-servlet that answers search requests over the index structure built with the Lookup-Indexer. The full documentation of the servlet can be found [here](./lookup-servlet/README.md)

## Discussion

There is a discussion thread on the DBpedia forums for questions and suggestions concerning this app and service here: https://forum.dbpedia.org/t/new-dbpedia-lookup-application/607
