# Lookup Searcher

## About

The lookup searcher is a Lucene query generator using its query builder framework. It is made accessible via a servlet that is hosted on a jetty HTTP server. The servlet provides a HTML homepage with a search window along with a single API command at `api/search`. 

The query parameters of the search can be used to fine tune a search request and doing so, extend or override any configuration specified in the configuration file, that is provided upon servlet initialization.

## Quickstart Example

### Starting the Servlet

You can run the *Launch Jetty Lookup Server* setup from the [launch-config.json](../.vscode/launch.json) in [Visual Studio Code](https://code.visualstudio.com/).

Alternatively, you can use maven to build a `.jar` file by issuing
```
mvn package
```
and then running the resulting `lookup-searcher-1.0-jar-with-dependencies.jar` file via
```
java -jar ./target/lookup-searcher-1.0-jar-with-dependencies.jar -c ../examples/search-config.yml
```

where the `-c` arguments points to a valid configuration file

### Example Configuration

The [example configuration](../examples/search-config.yml) used above looks as follows

```yaml
version: "1.0"
indexPath: ../index
exactMatchBoost: 10
prefixMatchBoost: 5
fuzzyMatchBoost: 2
fuzzyEditDistance: 2
fuzzyPrefixLength: 2
maxResults: 100
format: JSON
minRelevanceScore: 0.1
queryFields:
  - fieldName: birthPlace
    weight: 5
    tokenize: true
  - fieldName: label
    weight: 10
    highlight: true
    tokenize: true
    queryByDefault: true
```

The configuration holds a path to the index structure that will be used for searching along with a list of variables used for scoring results. Please refer to the [configuration documentation](#configuration) for more detailed explanations.

The [queryFields](#queryfields) variable holds the most important information with a list of objects describing fields that the searcher can potentially include in the search. Each query field spefifies a field name that should be present in the index structure (as specified in the index configuration). Each field can be given default settings for score weights and other properties, such as an indicator if query inputs should be tokenized for a specific field.

## Configuration

### indexPath
The index path points to the directory holding the index structure (usually the result of the lookup indexer).

### exactMathBoost
Denotes a multiplier that is applied to any result score if the search term matches a document field value exactly. Can be overriden via HTTP query parameter (e.g. `...&exactMathBoost=100`).

### prefixMatchBoost
Denotes a multiplier this is applied to any result score if the search term is a prefix of a document field value. Can be overriden via HTTP query parameter (e.g. `...&prefixMatchBoost=100`).

### fuzzyMatchBoost
Denotes a multiplier this is applied to any result score if the search term roughly matches a document field value. Can be overriden via HTTP query parameter (e.g. `...&fuzzyMatchBoost=100`).

### fuzzyEditDistance
Denotes the maximum edit distance between a search input and a document field such that it is still considered a fuzzy match.

### fuzzyPrefixLength
This is the number of characters at the start of a term that must be identical (not fuzzy) to the query term if the query is to match that term.

### maxResults
The maximum number of results. Can be overriden via HTTP query parameter (e.g. `...&maxResults=10`)

### format
The format of the results. Defaults to [JSON](#json) and can be one of the following:

#### JSON

#### JSON_RAW

#### JSON_FULL

#### XML

### formatTemplate 
When the selected format is [XML](#xml) you can provide the path to an *XSL* template to transform the result into any desired XML output.

### minRelevanceScore
The minimum score that a potential search result has to reach in order to be included in the result set. Can be overriden via HTTP query parameter (e.g. `...&minRelevanceScore=10`).
