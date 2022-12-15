FROM maven:3.8.2-jdk-11

COPY . .
RUN export MAVEN_OPTS='-Xmx4096m -Xms1024m'
RUN mvn clean package
CMD ["java","-jar","/target/lookup-servlet-1.0-jar-with-dependencies.jar", "-c", "/lookup-servlet/servlet-config.yml"]

