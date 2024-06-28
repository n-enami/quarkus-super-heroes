import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Compute the modules which have had any changes within a given timeframe
 */
class calculateModules {
	private static final String DEFAULT_TIMEFRAME = "24 hours";
	private static final List<String> MODULES = List.of(
		"event-statistics",
		"grpc-locations",
		"rest-fights",
		"rest-heroes",
		"rest-narration",
		"rest-villains",
		"ui-super-heroes"
	);

	private static boolean shouldIncludeFile(String fileName) {
		// Files in /deploy or <module-name>/deploy are generated by the build process,
		// so they could skew which modules we "think" have changed
		// So remove them and only look at the other changes
    return Objects.nonNull(fileName) &&
	    !fileName.trim().isEmpty() &&
	    !fileName.trim().contains("deploy/");
  }

	private static Set<String> getChangedModules(String timeframe) throws IOException, InterruptedException {
		// Use git log to get a list of all the files that changed in the timeframe
		var process = ProcessBuilder.startPipeline(
			List.of(
				new ProcessBuilder("git", "log", "--pretty=format:", "--since=\"%s ago\"".formatted(timeframe), "--name-only"),
				new ProcessBuilder("sort")
			)
		).getLast();

		process.waitFor();

		try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			var changedDirs = reader.lines()
				.filter(calculateModules::shouldIncludeFile)
				.map(fullFileName -> fullFileName.trim().split("/")[0]) // Grab just the first path, which will be the directory name
				.filter(directoryName -> MODULES.contains(directoryName)) // Only keep it if it is one of the module names
				.collect(Collectors.toCollection(LinkedHashSet::new));

			return changedDirs;
		}
	}

	private static String createJson(Set<String> changedModules) {
		return "[%s]".formatted(
			changedModules.stream()
				.flatMap(calculateModules::convertModuleToJson)
				.collect(Collectors.joining(","))
		);
	}

	private static Stream<String> convertModuleToJson(String moduleName) {
		var moduleJson = "{ \"name\": \"%s\" }".formatted(moduleName);

		return "rest-narration".equals(moduleName) ?
		       Stream.of(
						 moduleJson,
			       "{ \"name\": \"%s\", \"openai-type\": \"azure-openai\" }".formatted(moduleName)
		       ) :
		       Stream.of(moduleJson);
	}

	public static void main(String... args) throws IOException, InterruptedException {
		var timeframe = ((args != null) && (args.length == 1)) ?
		                Optional.ofNullable(args[0]).map(String::trim).filter(s -> !s.isEmpty()).orElse(DEFAULT_TIMEFRAME) :
		                DEFAULT_TIMEFRAME;
		var changedFiles = getChangedModules(timeframe);
		var json = createJson(changedFiles);
		System.out.println(json);
	}
}