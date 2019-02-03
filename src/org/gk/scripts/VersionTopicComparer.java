package org.gk.scripts;

/*
 * Created February 3rd, 2019
 */

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * This class compares the current and previous release version topic files
 * used for the slicingTool.  It will report topics that have been removed
 * and added between the two files.  The topics reflect the top level pathways
 * in the Reactome hierarchy.
 *
 * @author Joel Weiser
 */
public class VersionTopicComparer {

	public static void main(String[] args) {
		String newTopicFile = args[0];
		String previousTopicFile = args[1];

		List<String> newTopics = getFileLines(newTopicFile);
		List<String> previousTopics = getFileLines(previousTopicFile);

		List<String> removedTopics = foundOnlyInFirstList(previousTopics, newTopics);
		List<String> addedTopics = foundOnlyInFirstList(newTopics, previousTopics);

		reportTopics(removedTopics, "removed");
		reportTopics(addedTopics, "added");
	}

	private static List<String> getFileLines(String file) {
		try {
			return Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
		} catch (IOException e) {
			System.err.println("Unable to read file contents for " + file);
			throw new RuntimeException(e);
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