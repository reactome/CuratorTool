package org.gk.qualityCheck;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.gk.util.FileUtilities;

/**
 * This class is copied from release-qa repo. A refactor should be done in the future
 * to reuse the code there or here to avoid copying.
 * @author wug
 *
 */
public class QAReport {
    
	protected List<List<String>> reportLines = new ArrayList<List<String>>();
	protected List<String> columnHeaders = new ArrayList<String>();
    private String delimiter = "\t";

	public QAReport() {
	}
	
	/**
	 * Check if there is anything reported.
	 * @return
	 */
	public boolean isEmpty() {
	    return reportLines.isEmpty();
	}
	
	public void setDelimiter(String delimiter) {
	    this.delimiter = delimiter;
	}
    
	/**
	 * Add a line to the report.
	 * @param line - a list of values that makes up the line.
	 */
	public void addLine(List<String> line) {
		this.reportLines.add(line);
	}
	
	public void addLine(String... line) {
	    reportLines.add(Arrays.asList(line));
	}
	
	/**
	 * Add lines in bulk.
	 * @param lines
	 */
	public void addLines(List<List<String>> lines) {
		for (List<String> line : lines)
		{
			this.addLine(line);
		}
	}
	
	/**
	 * Sets the headers for the report.
	 * @param headers - a list of headers.
	 */
	public void setColumnHeaders(List<String> headers) {
		this.columnHeaders = headers;
	}
	
	public void setColumnHeaders(String... headers) {
	    this.columnHeaders = Arrays.asList(headers);
	}
	
	/**
	 * Adds a header to the headers.
	 * @param header - the header to add.
	 */
	public void addHeader(String header) {
		this.columnHeaders.add(header);
	}
	
	/**
	 * Gets an unmodifiable list of headers.
	 * @return an unmodifiable list of headers.
	 */
	public List<String> getHeaders() {
		return Collections.unmodifiableList(this.columnHeaders);
	}
	
	/**
	 * Gets an unmodifiable list of report lines.
	 * @return an unmodifiable list of report lines.
	 */
	public List<List<String>> getReportLines() {
		return Collections.unmodifiableList(this.reportLines);
	}

    /**
     * Print the report.
     */
    public void output(String fileName, String outputDir) throws IOException {
    	FileUtilities fu = new FileUtilities();
    	fu.setOutput(outputDir + File.separator + fileName);
    	fu.printLine(String.join(delimiter, columnHeaders));
    	for (List<String> line : reportLines) {
    	    fu.printLine(String.join(delimiter, line));
    	}
    	fu.close();
    }
    
    /**
     * This method is for debugging purpose.
     * @param builder
     */
    public void output(int maximumRow) {
        List<String> headers = getHeaders();
        System.out.println(String.join("\t", headers));
        int row = 0;
        List<List<String>> lines = getReportLines();
        for (int i = 0; i < lines.size(); i++) {
            row ++;
            System.out.println(String.join("\t", lines.get(i)));
            if (row > maximumRow)
                break;
        }
    }
}
