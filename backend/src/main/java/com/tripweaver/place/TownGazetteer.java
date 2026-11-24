package com.tripweaver.place;

import com.tripweaver.algorithms.trie.Trie;
import com.tripweaver.search.Suggestion;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
 */
@Component
public class TownGazetteer {

    private static final Logger log = LoggerFactory.getLogger(TownGazetteer.class);

    private final List<Town> towns;
    private final Trie<Town> trie = new Trie<>();

    @Autowired
    public TownGazetteer(@Value("${tripweaver.catalogue.data-dir:../data}") String dataDir) {
        this(load(Path.of(dataDir, "sample", "towns-gazetteer.json")));
    }

    public TownGazetteer(List<Town> towns) {
        this.towns = List.copyOf(towns);
        for (Town t : this.towns) {
            trie.insert(t.name(), t);
            t.aliases().forEach(a -> trie.insert(a, t));
        }
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

    /** Towns whose name or alias starts with {@code prefix}, cities first. */
    public List<Suggestion> autocomplete(String prefix, int limit) {
        Set<Town> seen = new LinkedHashSet<>();
        for (Trie.Match<Town> m : trie.searchPrefix(prefix, limit * 4)) seen.add(m.ref());
        List<Town> hits = new ArrayList<>(seen);
        hits.sort((a, b) -> Boolean.compare(!"city".equals(a.kind()), !"city".equals(b.kind())));
        List<Suggestion> out = new ArrayList<>();
        for (Town t : hits.subList(0, Math.min(limit, hits.size())))
            out.add(new Suggestion("TOWN", t.name(), t.key(), null, t.kind(), t.lat(), t.lon()));
        return out;
    }
}
