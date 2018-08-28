# Streaming from HDFS

HTML5 video streaming servlet from Hadoop Distributed File System
 
Using .webm video files (Google VP8 codec)

HTML5 streaming based in http://www.adrianwalker.org/2012/06/html5-video-pseudosteaming-with-java-7.html 

## Usage:

Setup hadoop fs, user and path in web.xml file:

        <init-param>
            <param-name>hdfsUri</param-name>
            <param-value>hdfs://$IP:$PORT</param-value>
        </init-param>
        <init-param>
            <param-name>hdfsUserName</param-name>
            <param-value>hdfs</param-value>
        </init-param>
        <init-param>
            <param-name>hdfsHomeDir</param-name>
            <param-value>/</param-value>
        </init-param>


Run the webservice:

	$ mvn clean install jetty:run

Point the browser to: http://localhost:8080/stream?video=$VIDEO_FILE_NAME



