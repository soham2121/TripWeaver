package com.tripweaver.place.ingest;

import java.nio.file.Files;
import java.nio.file.Path;
import tools.jackson.databind.json.JsonMapper;

/**
 * Command-line step 1 of the catalogue pipeline: PBF → compact destination extract (JSON).
 *
 * <pre>
 * cd backend
 * ./mvnw -q compile exec:java -Dexec.mainClass=com.tripweaver.place.ingest.OsmExtractTool \
 *   -Dexec.args="../data/raw/western-zone-261122.osm.pbf ../data/sample/goa-osm-extract.json"
 * </pre>
 *
 * Defaults are for Goa: bbox lat 14.85–15.85, lon 73.60–74.40, boundary relation name=Goa, admin_level=4.
 */
public final class OsmExtractTool {

    private OsmExtractTool() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: OsmExtractTool <input.osm.pbf> <output.json>");
            System.exit(2);
        }
        Path in = Path.of(args[0]), out = Path.of(args[1]);
        long t0 = System.nanoTime();
        OsmExtract ex = OsmPbfExtractor.extract(in, new OsmPbfExtractor.Bbox(14.85, 73.60, 15.85, 74.40), "Goa", "4");
        Files.createDirectories(out.toAbsolutePath().getParent());
        JsonMapper.builder().build().writeValue(out.toFile(), ex);
        System.out.printf(
                "features=%d settlements=%d boundaryOuterRings=%d innerRings=%d timestamp=%s in %.1fs%n",
                ex.features().size(),
                ex.settlements().size(),
                ex.boundaryOuters().size(),
                ex.boundaryInners().size(),
                ex.sourceTimestamp(),
                (System.nanoTime() - t0) / 1e9);
    }
}
