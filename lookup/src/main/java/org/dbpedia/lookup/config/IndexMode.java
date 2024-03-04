package org.dbpedia.lookup.config;

public enum IndexMode {
	INDEX_IN_MEMORY,
	BUILD_AND_INDEX_ON_DISK,
	INDEX_ON_DISK,
	INDEX_SPARQL_ENDPOINT,
}