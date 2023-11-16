# Lookup Searcher

## About

The lookup searcher is a Lucene query generator using its query builder framework. It is made accessible via a servlet that is hosted on a jetty HTTP server. The servlet provides a HTML homepage with a search window along with a single API command at `api/search`. 

Additionally, the application establishes a file system watcher on the index structure and restarts the servlet whenever a change to the index is detected. This makes index changes queriable without touching stopping and restarting the application.

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
The index path points to the directory holding the index structure (*absolute* or *relative to the configuration file*). This is usually the folder containing the result of the lookup indexer.

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

### boostFormula 
*[Optional]* An mathematical function that will be applied to result documents based on any numeric field indexed to that document.

### queryFields
A list of objects describing the query fields on which the searcher will operate. The objects consist of the following subfields:

#### fieldName
The name of the field. The field name can be used as a query parameter.

#### aliases
A list of strings specifying alternative names for a field name

#### weight
A numerical weight that is applied to a match on the respective field. E.g. can be used to express, that a match on a `title` field is worth more than a match on an `abstract` field.

#### highlight
Denotes whether the result should include special html tags highlighting the match between the query string and the field value. Defaults to `false`. Can be overriden via HTTP query parameter using the field name followed by the string `Highlight` (e.g. `...&labelHighlight=true` when searching on the field `label`).

#### tokenize
If `true`, the query string will be tokenized and each token will be matched against the field value of this field. Defaults to `false`. Can be overriden via HTTP query parameter using the field name followed by the string `Tokenize` (e.g. `...&labelTokenize=true` when searching on the field `label`).

#### required
If `true`, a match on this field is required. A document that has a match on any other field included in the search but does *not* have a match on this field, will be dropped from the result set even if it would have been included otherwise. Defaults to `false`. Can be overriden via HTTP query parameter using the field name followed by the string `Required` (e.g. `...&labelRequired=true` when searching on the field `label`).

#### exact
If `true`, a match on this field only counts if the field value matches the query string exactly. Defaults to `false`. Can be overriden via HTTP query parameter using the field name followed by the string `Exact` (e.g. `...&labelExact=true` when searching on the field `label`).

#### allowPartialMatch

#### queryByDefault
If `true`, all queries sent with the [query](#query) parameter will be matched against this field.

## Query Parameters

### query
The most important parameter: the search query string

### [fieldName]
Any configured field name can be used as a query parameter. The string specified with this parameter will only be matched against the respective field.

### join
Join can specify any indexed field. The search will then be executed normally but the initial result set will not be returned. Instead, Lucene will return all documents that have a value for the specified field equal to any document id in the initial result set (simplified Lucene join).