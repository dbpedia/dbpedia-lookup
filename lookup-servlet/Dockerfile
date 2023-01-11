FROM maven:3.8.2-jdk-11

COPY . /lookup
RUN export MAVEN_OPTS='-Xmx4096m -Xms1024m'
WORKDIR /lookup
RUN mvn clean package
CMD [ "java","-jar","/lookup/target/lookup-servlet-1.0-jar-with-dependencies.jar", "-c", "/lookup/servlet-config.yml" ]
