package com.qaryati.dataquality.api;
import java.util.*;
/** Public source associations. REQUIRED writes join the caller transaction and lock the cultural heritage record.
 * Invalid IDs/collections: IllegalArgumentException; missing cultural heritage/source: NoSuchElementException.
 * Source existence does not establish credibility. Reads include empty legacy associations.
 * Heritage owns authorization and DRAFT checks. IDs must be positive; writes require at least one
 * existing source and normalize IDs into sorted distinct immutable lists. Read lists and maps are
 * immutable. Batch reads use one LEFT JOIN query, include uncited existing parents, and reject
 * missing parents; an empty batch performs no SQL. Initial linking rejects existing associations
 * with IllegalStateException. Callers must flush new entities before linking and propagate failures.
 * All replacement sources are validated before removal. Parent deletion cascades associations;
 * source deletion is restricted by the existing foreign key.
 */
public interface CulturalHeritageSourceLinks {
    List<Long> sourceIds(Long culturalHeritageId);
    Map<Long, List<Long>> sourceIdsByCulturalHeritageIds(Collection<Long> culturalHeritageIds);
    void linkNewCulturalHeritage(Long culturalHeritageId, Collection<Long> sourceIds);
    void replaceSources(Long culturalHeritageId, Collection<Long> sourceIds);
}
