package com.tripweaver.place.ingest;

import com.tripweaver.place.Town;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.json.JsonMapper;

/**
 * Command-line step: PBF → gazetteer of cities and towns (JSON list of {@link Town}) for location search and
 * road-trip stays. Covers the whole file, not just the catalogue destinations.
 *
 * <pre>
 * cd backend
 * ./mvnw -q compile exec:java -Dexec.mainClass=com.tripweaver.place.ingest.TownGazetteerTool \
 *   -Dexec.args="../data/raw/western-zone-261122.osm.pbf ../data/sample/towns-gazetteer.json"
 * </pre>
 */
public final class TownGazetteerTool {

    static final Set<String> KINDS = Set.of("city", "town");

    private TownGazetteerTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: TownGazetteerTool <input.osm.pbf> <output.json>");
            System.exit(2);
        }
        long t0 = System.nanoTime();
        List<Town> towns = new ArrayList<>();
        for (OsmExtract.Feature f : OsmPbfExtractor.places(Path.of(args[0]), KINDS))
            towns.add(town(f.tags(), f.lat(), f.lon()));
        towns.sort(Comparator.comparing(Town::name).thenComparing(Town::lat));
        Path out = Path.of(args[1]);
        Files.createDirectories(out.toAbsolutePath().getParent());
        JsonMapper.builder().build().writeValue(out.toFile(), towns);
        System.out.printf("towns=%d in %.1fs%n", towns.size(), (System.nanoTime() - t0) / 1e9);
    }

    static Town town(Map<String, String> tags, double lat, double lon) {
        String name = tags.getOrDefault("name:en", tags.get("name"));
        Set<String> aliases = new LinkedHashSet<>();
        for (String k : List.of("name", "name:en", "alt_name", "alt_name:en", "old_name", "old_name:en"))
            if (tags.containsKey(k))
                for (String a : tags.get(k).split(";"))
                    if (!a.isBlank() && a.chars().allMatch(c -> c < 0x250)) aliases.add(a.trim()); // Latin only
        aliases.remove(name);
        return new Town(name, tags.get("place"), lat, lon, List.copyOf(aliases));
    }
}
