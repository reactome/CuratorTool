package org.gk.scripts;

/*
 * Created February 3rd, 2019
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class compares the current and previous release version topic files
 * used for the slicingTool.  It will report topics that have been removed
 * and added between the two files.  The topics reflect the top level pathways
 * in the Reactome hierarchy.
 *
 * @author Joel Weiser
 */
public class VersionTopicComparer {
	private static final Pattern VERSION_TOPIC_FILE_PATTERN = Pattern.compile("^ver(\\d+)_topics.txt$");

	public static void main(String[] args) {
		String[] topicFiles = getTopicFiles(args);
		System.out.println("Topic files: " + Arrays.toString(topicFiles));

		List<String> newTopics = getFileLines(topicFiles[0]);
		List<String> previousTopics = getFileLines(topicFiles[1]);

		List<String> removedTopics = foundOnlyInFirstList(previousTopics, newTopics);
		List<String> addedTopics = foundOnlyInFirstList(newTopics, previousTopics);

		if (removedTopics.isEmpty() && addedTopics.isEmpty()) {
			System.out.println("No differences found between topic files: " + topicFiles[0] + " and " + topicFiles[1]);
		} else {
			if (!removedTopics.isEmpty()) {
				reportTopics(removedTopics, "removed");
			}
			if (!addedTopics.isEmpty()) {
				reportTopics(addedTopics, "added");
			}
		}
	}

	private static String[] getTopicFiles(String[] args) {
		final long numOfFilesToCompare = 2;

		String[] topicFiles = args.length == numOfFilesToCompare ?
			getTopicFilesFromArgs(args) :
			getTopicFilesFromCurrentDirectory();
		if (topicFiles.length == numOfFilesToCompare) {
			return topicFiles;
		} else {
			throw new RuntimeException("Could not find two version topic files. Found " + Arrays.toString(topicFiles));
		}
	}

	private static String[] getTopicFilesFromArgs(String[] args) {
		return filterAndSortVersionTopicFiles(Arrays.stream(args));
	}

	private static String[] getTopicFilesFromCurrentDirectory() {
		String currentWorkingDirectory = System.getProperty("user.dir");
		try {
			return filterAndSortVersionTopicFiles(
				Files.list(Paths.get(currentWorkingDirectory))
					.filter(Files::isRegularFile)
					.map(file -> file.getFileName().toString())
			);

		} catch (IOException e) {
			throw new RuntimeException("Could not get version topic files in dir: " + currentWorkingDirectory, e);
		}
	}

	private static String[] filterAndSortVersionTopicFiles(Stream<String> fileNames) {
		return fileNames
			.filter(fileName -> getVersionTopicMatcher(fileName).matches())
			.sorted(newestVersionFirst())
			.toArray(String[]::new);
	}

	private static Matcher getVersionTopicMatcher(String filePath) {
		return VERSION_TOPIC_FILE_PATTERN.matcher(Paths.get(filePath).getFileName().toString());
	}

	private static Comparator<String> newestVersionFirst() {
		return Comparator.comparing(VersionTopicComparer::getVersion);
	}

	private static Long getVersion(String topicFile) {
		Matcher versionMatcher = getVersionTopicMatcher(topicFile);
		if (versionMatcher.matches()) {
			return Long.parseLong(versionMatcher.group(1));
		} else {
			throw new IllegalStateException("Topic file " + topicFile + " has no version number");
		}
	}

	private static List<String> getFileLines(String file) {
		try {
			return Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Unable to read file contents for " + file, e);
		}
	}

	// Adapted from https://stackoverflow.com/a/47163707
	private static List<String> foundOnlyInFirstList(List<String> firstList, List<String> secondList) {
		Predicate<String> inFirstList = (element -> new HashSet<>(firstList).contains(element));

		return secondList.stream()
			.filter(inFirstList.negate())
			.collect(Collectors.toList());
	}

	private static void reportTopics(List<String> topics, String actionPerformed) {
		System.out.println("The following topics have been " + actionPerformed + " in the new version topic file");

		for (String topic : topics) {
			System.out.println(topic);
		}

		System.out.println(); // Buffer space between reports
	}
}