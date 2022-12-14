
echo "========================================="
echo "Copying .war to tomcat webapps directory."
echo "========================================="
cp ./target/lookup-servlet-1.0.war /usr/local/tomcat/webapps/
echo "Done! Starting tomcat..."
echo "========================================="
/usr/local/tomcat/bin/catalina.sh start
echo "Running..."
