package com.tripweaver.place;

import com.tripweaver.algorithms.trie.Trie;
import com.tripweaver.place.ingest.OsmExtract;
import com.tripweaver.place.ingest.TownGazetteerTool;
import com.tripweaver.search.Suggestion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Cities and towns from {@code <data-dir>/sample/towns-gazetteer.json} (built by {@code TownGazetteerTool}). A
 * missing file leaves the gazetteer empty: search then finds catalogue places only, and road trips stop at hotels.
 * Location search also finds the villages and suburbs (Candolim, Colva) from the catalogue extracts
 * ({@code <data-dir>/sample/*-osm-extract.json}); those are searchable only, never road-trip stays.
 */
@Component
public class TownGazetteer {

    private static final Logger log = LoggerFactory.getLogger(TownGazetteer.class);

    private final List<Town> towns;
    private final Trie<Town> trie = new Trie<>();

    /** OSM place kinds from the extracts added to search: smaller settlements that are not in the gazetteer. */
    static final Set<String> LOCALITY_KINDS = Set.of("suburb", "village");

    private static final List<String> KIND_ORDER = List.of("city", "town", "suburb", "village");

    @Autowired
    public TownGazetteer(@Value("${tripweaver.catalogue.data-dir:../data}") String dataDir) {
        this(load(Path.of(dataDir, "sample", "towns-gazetteer.json")), localities(Path.of(dataDir, "sample")));
    }

    public TownGazetteer(List<Town> towns) {
        this(towns, List.of());
    }

    /** @param localities searchable only; {@link #all()} stays the cities and towns */
    public TownGazetteer(List<Town> towns, List<Town> localities) {
        this.towns = List.copyOf(towns);
        for (Town t : Stream.concat(this.towns.stream(), localities.stream()).toList()) {
            trie.insert(t.name(), t);
            t.aliases().forEach(a -> trie.insert(a, t));
        }
    }

    /** Villages and suburbs from every catalogue extract in {@code dir}, once each. */
    static List<Town> localities(Path dir) {
        Map<String, Town> out = new LinkedHashMap<>();
        JsonMapper json = JsonMapper.builder().build();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith("-osm-extract.json"))
                    .sorted()
                    .toList())
                for (OsmExtract.Feature s :
                        json.readValue(f.toFile(), OsmExtract.class).settlements())
                    if (LOCALITY_KINDS.contains(s.tags().get("place"))
                            && s.tags().containsKey("name")) {
                        Town t = TownGazetteerTool.town(s.tags(), s.lat(), s.lon());
                        out.putIfAbsent(t.key(), t);
                    }
        } catch (Exception e) {
            log.warn("Localities from {} not loaded: {}", dir, e.toString());
            return List.of();
        }
        log.info("Location search: {} villages and suburbs", out.size());
        return List.copyOf(out.values());
    }

    static List<Town> load(Path file) {
        if (!Files.exists(file)) {
            log.warn("Town gazetteer {} not found: town search and town stays are off", file);
            return List.of();
        }
        List<Town> t = JsonMapper.builder().build().readValue(file.toFile(), new TypeReference<List<Town>>() {});
        log.info("Town gazetteer: {} cities and towns", t.size());
        return t;
    }

    public List<Town> all() {
        return towns;
    }

    /**
     * Towns and localities whose name or alias starts with {@code prefix}: an exact name first, then cities, towns,
     * suburbs, villages.
     */
    public List<Suggestion> autocomplete(String prefix, int limit) {
        Set<Town> seen = new LinkedHashSet<>();
        for (Trie.Match<Town> m : trie.searchPrefix(prefix, limit * 4)) seen.add(m.ref());
        List<Town> hits = new ArrayList<>(seen);
        String q = prefix.trim();
        hits.sort(Comparator.comparing((Town t) -> !t.name().equalsIgnoreCase(q))
                .thenComparing(t -> KIND_ORDER.indexOf(t.kind())));
        List<Suggestion> out = new ArrayList<>();
        for (Town t : hits.subList(0, Math.min(limit, hits.size())))
            out.add(new Suggestion("TOWN", t.name(), t.key(), null, t.kind(), t.lat(), t.lon()));
        return out;
    }
}
