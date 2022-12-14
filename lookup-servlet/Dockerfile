FROM tomcat:9.0.35-jdk11-openjdk

RUN apt-get update
RUN apt-get -y install git maven inotify-tools
RUN mvn --version
RUN java -version

WORKDIR /root/

COPY ./ /root/lookup/
WORKDIR /root/lookup/
RUN export MAVEN_OPTS='-Xmx4096m -Xms1024m'
RUN mvn install

ENTRYPOINT /bin/bash run.sh

