FROM maven:3.8.2-jdk-11

COPY . .

RUN mvn clean package
CMD ["java","-jar","/target/lookup-indexer-1.0-jar-with-dependencies.jar", "-config", "/lookup-indexer/index-config.yml"]