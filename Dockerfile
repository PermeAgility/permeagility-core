FROM jeanblanchard/java:jre-8
MAINTAINER Glenn Irwin - PermeAgility
EXPOSE 1999
RUN mkdir -p /opt/permeagility
ADD target/permeagility-0.8.2-SNAPSHOT-jar-with-dependencies.jar /opt/permeagility/permeagility-0.8.2.jar
WORKDIR /home
CMD ["java", "-jar", "/opt/permeagility/permeagility-0.8.2.jar"]
