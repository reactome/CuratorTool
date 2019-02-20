package org.gk.scripts;

/*
 * Created February 3rd, 2019
 */
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
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
	private static final int NUMBER_OF_FILES_TO_COMPARE = 2;
	private static final Pattern VERSION_TOPIC_FILE_PATTERN = Pattern.compile("^ver(\\d+)_topics.txt$");

	public static void main(String[] args) throws IOException {
		Path fileOutputPath = Paths.get(VersionTopicComparer.class.getSimpleName() + ".out");
		Files.deleteIfExists(fileOutputPath);

		String[] topicFiles = getTopicFiles(args);
		writeToScreenAndFile("Topic files: " + Arrays.toString(topicFiles), fileOutputPath);

		List<String> newTopics = getFileLines(topicFiles[0]);
		List<String> previousTopics = getFileLines(topicFiles[1]);

		List<String> removedTopics = dbIDFoundOnlyInFirstList(previousTopics, newTopics);
		List<String> addedTopics = dbIDFoundOnlyInFirstList(newTopics, previousTopics);

		if (removedTopics.isEmpty() && addedTopics.isEmpty()) {
			writeToScreenAndFile(
				"No differences found between topic files: " + topicFiles[0] + " and " + topicFiles[1], fileOutputPath
			);
		} else {
			if (!removedTopics.isEmpty()) {
				reportTopics(removedTopics, "removed", fileOutputPath);
			}

			if (!addedTopics.isEmpty()) {
				reportTopics(addedTopics, "added", fileOutputPath);
			}
		}
	}

	private static String[] getTopicFiles(String[] args) {
		String[] topicFiles = searchForTopicFiles(args);
		if (topicFiles.length != NUMBER_OF_FILES_TO_COMPARE) {
			System.out.print("Exactly " + NUMBER_OF_FILES_TO_COMPARE + " version topic files could not be found. ");
			System.out.println("Please provide their names (along with paths if in another directory) manually.");
			System.out.println();

			topicFiles = promptForVersionTopicFiles(new Scanner(System.in));
			if (topicFiles.length != NUMBER_OF_FILES_TO_COMPARE) {
				throw new RuntimeException("Unable to find " + NUMBER_OF_FILES_TO_COMPARE + " version topic files");
			}
		}

		return topicFiles;
	}

	private static String[] searchForTopicFiles(String[] args) {
		// Enough arguments that there is the potential for the
		// required number of version topic files to compare to
		// be found
		if (args.length >= NUMBER_OF_FILES_TO_COMPARE) {
			return getTopicFilesFromArgs(args);
		} else {
			return getTopicFilesFromCurrentDirectory();
		}
	}

	private static String[] getTopicFilesFromArgs(String[] args) {
		return filterAndSortVersionTopicFileNames(Arrays.stream(args));
	}

	private static String[] getTopicFilesFromCurrentDirectory() {
		String currentWorkingDirectory = System.getProperty("user.dir");
		try {
			return filterAndSortVersionTopicFileNames(
				Files.list(Paths.get(currentWorkingDirectory))
					.map(file -> file.getFileName().toString())
			);

		} catch (IOException e) {
			throw new RuntimeException("Could not get version topic files in dir: " + currentWorkingDirectory, e);
		}
	}

	private static String[] promptForVersionTopicFiles(Scanner reader) {
		String firstVersionTopicFile = promptForVersionTopicFile(
			reader, "Enter the name of first version topic file: "
		);
		String secondVersionTopicFile = promptForVersionTopicFile(
			reader, "Enter the name of second version topic file: "
		);

		return filterAndSortVersionTopicFileNames(Stream.of(firstVersionTopicFile, secondVersionTopicFile));
	}

	private static String promptForVersionTopicFile(Scanner reader, String query) {
		return promptForVersionTopicFile(reader, query, 1);
	}

	private static String promptForVersionTopicFile(Scanner reader, String query, int timesCalled) {
		final int METHOD_CALL_LIMIT = 3; // Attempt prompt 3 times before throwing exception

		System.out.print(query);
		String versionTopicFile = reader.next();

		Optional<String> error = findVersionTopicFileNameError(versionTopicFile);
		if (error.isPresent()) {
			String errorValue = error.get();
			if (timesCalled < METHOD_CALL_LIMIT) {
				System.out.println(errorValue);
				versionTopicFile = promptForVersionTopicFile(reader, query, ++timesCalled);
			} else {
				throw new RuntimeException("Unable to obtain version topic file: " + errorValue);
			}
		}

		return versionTopicFile;
	}

	private static Optional<String> findVersionTopicFileNameError(String fileName) {
		if (!hasCorrectPattern().test(fileName)) {
			return Optional.of("The version topic file provided must have the pattern " + VERSION_TOPIC_FILE_PATTERN);
		} else if (!fileExists().test(fileName)) {
			return Optional.of("The version topic " + fileName + " file can not be found");
		} else {
			return Optional.empty();
		}
	}

	private static Predicate<String> hasCorrectPattern() {
		return filePath -> getVersionTopicMatcher(filePath).matches();
	}

	private static Predicate<String> fileExists() {
		return filePath -> Files.isRegularFile(Paths.get(filePath));
	}

	private static String[] filterAndSortVersionTopicFileNames(Stream<String> fileNames) {
		return fileNames
			.filter(hasCorrectPattern().and(fileExists()))
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
			throw new IllegalArgumentException("Topic file " + topicFile + " has no version number");
		}
	}

	private static List<String> getFileLines(String file) {
		try {
			return Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8)
				.stream()
				.filter(line -> !line.isEmpty())
				.collect(Collectors.toList());
		} catch (IOException e) {
			throw new RuntimeException("Unable to read file contents for " + file, e);
		}
	}

	// Adapted from https://stackoverflow.com/a/47163707
	private static List<String> dbIDFoundOnlyInFirstList(List<String> firstList, List<String> secondList) {
		Predicate<String> dbIDInFirstList = (element ->
			new HashSet<>(getDBIDsFromFileLines(firstList)).contains(getDBIDFromFileLine(element))
		);

		return secondList.stream()
			.filter(dbIDInFirstList.negate())
			.collect(Collectors.toList());
	}

	private static List<Long> getDBIDsFromFileLines(List<String> fileLines) {
		return fileLines.stream().map(VersionTopicComparer::getDBIDFromFileLine).collect(Collectors.toList());
	}

	private static Long getDBIDFromFileLine(String line) {
		Pattern DBIDPattern = Pattern.compile("^(\\d+).*");
		Matcher DBIDMatcher = DBIDPattern.matcher(line);
		if (DBIDMatcher.matches()) {
			return Long.parseLong(DBIDMatcher.group(1));
		} else {
			throw new IllegalArgumentException("File line '" + line + "' must start with a db id");
		}
	}

	private static void reportTopics(List<String> topics, String actionPerformed, Path fileOutputPath) {
		writeToScreenAndFile(
			"The following topics have been " + actionPerformed + " in the new version topic file",
			fileOutputPath
		);

		for (String topic : topics) {
			writeToScreenAndFile(topic, fileOutputPath);
		}

		writeToScreenAndFile("\n", fileOutputPath); // Buffer space between reports
	}

	// Appends a new line with each output
	private static void writeToScreenAndFile(String output, Path fileOutputPath) {
		try {
			Files.write(
				fileOutputPath,
				output.concat("\n").getBytes(),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND
			);
		} catch (IOException e) {
			throw new RuntimeException(
				"Unable to write " + output + " to file " + fileOutputPath.getFileName().toString(),
				e
			);
		}
		System.out.println(output);
	}
}