package com.qaryati.dataquality.api;
import java.util.*;
/** Public source associations. REQUIRED writes join the caller transaction and lock the history.
 * Invalid IDs/collections: IllegalArgumentException; missing history/source: NoSuchElementException.
 * Source existence does not establish credibility. Reads include empty legacy associations.
 */
public interface VillageHistorySourceLinks {
    List<Long> sourceIds(Long historyId);
    Map<Long, List<Long>> sourceIdsByHistoryIds(Collection<Long> historyIds);
    void linkNewHistory(Long historyId, Collection<Long> sourceIds);
    void replaceSources(Long historyId, Collection<Long> sourceIds);
}
