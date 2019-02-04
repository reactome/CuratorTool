package org.gk.scripts;

/*
 * Created February 3rd, 2019
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
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

		List<String> newTopics = getFileLines(topicFiles[0]);
		List<String> previousTopics = getFileLines(topicFiles[1]);

		List<String> removedTopics = foundOnlyInFirstList(previousTopics, newTopics);
		List<String> addedTopics = foundOnlyInFirstList(newTopics, previousTopics);

		reportTopics(removedTopics, "removed");
		reportTopics(addedTopics, "added");
	}

	private static String[] getTopicFiles(String[] args) {
		final long numOfFilesToCompare = 2;

		String[] topicFiles = args.length == numOfFilesToCompare ?
			getTopicFilesFromArgs(args) :
			getTopicFilesFromCurrentDirectory();
		if (topicFiles.length == numOfFilesToCompare) {
			return topicFiles;
		} else {
			throw new RuntimeException("Could not find two version topic files\n");
		}
	}

	private static String[] getTopicFilesFromArgs(String[] args) {
		return filterAndSortVersionTopicFiles(Arrays.stream(args));
	}

	private static String[] getTopicFilesFromCurrentDirectory() {
		String currentWorkingDirectory = System.getProperty("user.dir");
		try {
			return filterAndSortVersionTopicFiles(
				Files.walk(Paths.get(currentWorkingDirectory))
					.filter(Files::isRegularFile)
					.map(file -> file.getFileName().toString())
			);

		} catch (IOException e) {
			throw new RuntimeException("Could not get version topic files in dir: " + currentWorkingDirectory, e);
		}
	}

	private static String[] filterAndSortVersionTopicFiles(Stream<String> fileNames) {
		return fileNames.filter(fileName -> VERSION_TOPIC_FILE_PATTERN.matcher(fileName).matches())
			.sorted(newestVersionFirst())
			.toArray(String[]::new);
	}

	private static Comparator<String> newestVersionFirst() {
		return Comparator.comparing(VersionTopicComparer::getVersion);
	}

	private static Long getVersion(String topicFile) {
		return Long.parseLong(VERSION_TOPIC_FILE_PATTERN.matcher(topicFile).group(1));
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