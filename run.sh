cd target
java --add-exports java.management/sun.management=ALL-UNNAMED \
 --add-opens java.base/java.util.concurrent.atomic=ALL-UNNAMED \
 --add-opens java.base/java.nio.channels.spi=ALL-UNNAMED \
 -jar permeagility-0.9.0-SNAPSHOT-jar-with-dependencies.jar $1 $2 $3 $4
