FROM gradle:4.5.1-jdk9 as builder
WORKDIR /tmp/

# complete first run download
COPY --chown=gradle build.gradle build.gradle
COPY --chown=gradle gradlew gradlew
COPY --chown=gradle gradle gradle

# start build
COPY --chown=gradle . .

# run build
RUN ./gradlew build
RUN ls -lh build/libs/


# build new image to run jar
FROM anapsix/alpine-java:8
ENV JAVA_CONF_DIR=$JAVA_HOME/conf
# To see work around for bug, uncomment this line...
RUN bash -c '([[ ! -d $JAVA_SECURITY_DIR ]] && ln -s $JAVA_HOME/lib $JAVA_HOME/conf) || (echo "Found java conf dir, package has been fixed, remove this hack"; exit -1)'

RUN mkdir -p /tmp/java-test
COPY --from=builder /tmp/build/libs/aka-sweep-0.0.2.jar /tmp/java-test/
CMD java -jar /tmp/java-test/aka-sweep-0.0.2.jar
