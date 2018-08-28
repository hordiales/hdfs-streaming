package org.video.streaming;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import static java.nio.file.StandardOpenOption.READ;

import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import org.apache.commons.io.IOUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;

/*
 * Video streaming from HDFS (Hadoop Distributed File System)
 * by Hernán Ordiales <h@ordia.com.ar> (2018)
 * 
 * Based in http://www.adrianwalker.org/2012/06/html5-video-pseudosteaming-with-java-7.html
 * https://github.com/adrianwalker/pseudostreaming
 */
public final class HdfsStreamingServlet extends HttpServlet {

	private static final Logger logger = Logger.getLogger("io.hdfs.Main");

	private static final int BUFFER_LENGTH = 1024 * 16;

	private static final long EXPIRE_TIME = 1000 * 60 * 60 * 24;
	private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(?<start>\\d*)-(?<end>\\d*)");

	private String hdfsUri; //set in web.xml
	private String hdfsUserName; //set in web.xml
	private String hdfsHomeDir; //set in web.xml
	@Override
	public void init() throws ServletException {
		hdfsUri = getInitParameter("hdfsUri");
		hdfsUserName  = getInitParameter("hdfsUserName");
		hdfsHomeDir = getInitParameter("hdfsHomeDir");
	}

	@Override
	protected void doGet(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
		processRequest(request, response);
	}

	private void processRequest(final HttpServletRequest request, final HttpServletResponse response) throws IOException {

		String videoFilename = URLDecoder.decode( request.getParameter("video"), "UTF-8" );
		logger.info("URL video parameter filename: "+videoFilename);

		// HADOOP
		// Init HDFS File System Object
		Configuration conf = new Configuration();
		
		// Set FileSystem URI
		conf.set("fs.defaultFS", hdfsUri);

		// Because of Maven
		conf.set("fs.hdfs.impl", org.apache.hadoop.hdfs.DistributedFileSystem.class.getName());
		conf.set("fs.file.impl", org.apache.hadoop.fs.LocalFileSystem.class.getName());

		// Set HADOOP user
		System.setProperty("HADOOP_USER_NAME", hdfsUserName);
		System.setProperty("hadoop.home.dir", hdfsHomeDir);

		// Get the filesystem - HDFS
		FileSystem fs = FileSystem.get(URI.create(hdfsUri), conf);

		// Read file
		logger.info("Read file into hdfs");
		org.apache.hadoop.fs.Path hdfsReadPath = new org.apache.hadoop.fs.Path(hdfsHomeDir + videoFilename);

		//Init input stream
		FSDataInputStream inputStream = fs.open(hdfsReadPath);

		
		org.apache.hadoop.fs.ContentSummary cSummary = fs.getContentSummary(hdfsReadPath);		
		//WARNING: long type (and not int) is needed to stream huge videos
        long length = cSummary.getLength();
		logger.info("File length (in bytes) (from Hadoop): "+length);
		
		long start = 0;
		long end = length - 1;

		String range = request.getHeader("Range");
		logger.info("Range: "+range);
		if( range!=null ) {
			Matcher matcher = RANGE_PATTERN.matcher(range);

			if (matcher.matches()) {
				String startGroup = matcher.group("start");
				start = startGroup.isEmpty() ? start : Integer.valueOf(startGroup);
				start = start < 0 ? 0 : start;

				String endGroup = matcher.group("end");
				end = endGroup.isEmpty() ? end : Integer.valueOf(endGroup);
				end = end > length - 1 ? length - 1 : end;
			}
		}
		long contentLength = end - start + 1;

		response.reset();
		response.setBufferSize(BUFFER_LENGTH);
		response.setHeader("Content-Disposition", String.format("inline;filename=\"%s\"", videoFilename));
		response.setHeader("Accept-Ranges", "bytes");
		
		response.setDateHeader("Expires", System.currentTimeMillis() + EXPIRE_TIME);
		
		response.setContentType("video/webm"); //FIXME
		
		response.setHeader("Content-Range", String.format("bytes %s-%s/%s", start, end, length));
		response.setHeader("Content-Length", String.format("%s", contentLength));
		response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

		long bytesRead;
		long bytesLeft = contentLength;
		ByteBuffer buffer = ByteBuffer.allocate(BUFFER_LENGTH);

		try ( OutputStream output = response.getOutputStream() ) {

			try {
				//hadoop read
				while ((bytesRead = inputStream.read(buffer)) != -1) {
					buffer.clear();
					output.write(buffer.array(), 0, (int) (bytesLeft < bytesRead ? bytesLeft : bytesRead));
					bytesLeft -= bytesRead;
				} 
			}
			finally {

				inputStream.close();
				fs.close();
				output.close();
			}
		}
	}
}
