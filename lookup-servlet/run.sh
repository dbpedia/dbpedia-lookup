INDEX_PATH='/root/index'

echo "========================================="
echo "Copying .war to tomcat webapps directory."
echo "========================================="
cp ./target/lookup-servlet-1.0.war /usr/local/tomcat/webapps/
echo "Done! Starting tomcat..."
echo "========================================="
/usr/local/tomcat/bin/catalina.sh start
echo "Running..."

if [ -d "${INDEX_PATH}" ]; then
    inotifywait -m -r -e create -e moved_to "${INDEX_PATH}" | while read DIR ACTION FILE;
    do
        echo "File ${FILE} has been added to the index"
        /usr/local/tomcat/bin/catalina.sh start
    done
else
    while true
    do
        sleep 60
    done
fi
