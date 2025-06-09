FROM tomcat:10.1-jdk11

# Remove default webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy the WAR file
#COPY target/fabflix-login.war /usr/local/tomcat/webapps/fabflix-login.war
# 修改这一行
COPY target/fabflix-login.war /usr/local/tomcat/webapps/ROOT.war
# Copy context.xml with database configuration
COPY WebContent/META-INF/context.xml /usr/local/tomcat/conf/context.xml

# Expose port 8080
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]
