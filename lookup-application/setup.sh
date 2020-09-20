if [ -z ${DATA_PATH+x} ]; then DATA_PATH='/root/data'; fi
if [ -z ${TDB_PATH+x} ]; then TDB_PATH='/root/tdb'; fi
if [ -z ${INDEX_MODE+x} ]; then INDEX_MODE='BUILD_MEM'; fi
if [ -z ${CLEAN+x} ]; then CLEAN='true'; fi
if [ -z ${CONFIG_PATH+x} ]; then CONFIG_PATH='/root/app-config.yml'; fi

echo "========================================="
echo "DATA_PATH is set to '$DATA_PATH'"; 
echo "TDB_PATH is set to '$TDB_PATH'"; 
echo "CONFIG_PATH is set to '$CONFIG_PATH'"; 
echo "========================================="
echo "Looking for download.lck in ${DATA_PATH}."
echo "========================================="
sleep 10
while [ -f "${DATA_PATH}/download.lck" ]; do
    sleep 1
done

cd /root/lookup-application
mvn exec:java -Dexec.mainClass="org.dbpedia.lookup.IndexMain" -Dexec.args="-data $DATA_PATH -tdb $TDB_PATH -config $CONFIG_PATH -clean $CLEAN -mode $INDEX_MODE"

echo "========================================="
echo "Copying .war to tomcat webapps directory."
echo "========================================="
cp ./target/lookup-application.war /usr/local/tomcat/webapps/
echo "Done! Starting tomcat..."
echo "========================================="
/usr/local/tomcat/bin/catalina.sh start
echo "Running..."


if [ -d "${DATA_PATH}" ]; then
    inotifywait -m -r -e create -e moved_to "${DATA_PATH}" | while read DIR ACTION FILE;
    do
        echo "File ${FILE} has been added to the data directory"
        mvn exec:java -Dexec.mainClass="org.dbpedia.lookup.IndexMain" -Dexec.args="-data ${DIR}${FILE} -tdb $TDB_PATH -config $CONFIG_PATH -clean $CLEAN -mode $INDEX_MODE"
        /usr/local/tomcat/bin/catalina.sh stop
        /usr/local/tomcat/bin/catalina.sh start
    done
else
    while true
    do
        sleep 60
    done
fi



