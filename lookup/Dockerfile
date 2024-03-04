FROM openjdk:latest
EXPOSE 8082
COPY ./target/lookup-1.0-jar-with-dependencies.jar /opt/app/
CMD [ "java","-jar","/opt/app/lookup-1.0-jar-with-dependencies.jar", "-c", "/resources/config.yml" ]
