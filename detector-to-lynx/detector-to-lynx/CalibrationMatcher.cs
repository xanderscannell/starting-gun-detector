namespace detector_to_lynx
{
    /// <summary>
    /// One matched pair: a detection time aligned with a FinishLynx start time,
    /// plus the residual error remaining after the computed offset is applied.
    /// </summary>
    public record MatchedPair(TimeSpan Detection, TimeSpan LynxStart)
    {
        /// <summary>Raw offset for this pair: LynxStart − Detection (milliseconds).</summary>
        public double RawOffsetMs => (LynxStart - Detection).TotalMilliseconds;
    }

    /// <summary>
    /// A row in the calibration table. Exactly one of Detection / LynxStart may be null
    /// when it has no counterpart.
    /// </summary>
    public record CalibrationRow(TimeSpan? Detection, TimeSpan? LynxStart)
    {
        /// <summary>True when both sides are present (i.e. a matched pair).</summary>
        public bool IsMatched => Detection.HasValue && LynxStart.HasValue;

        /// <summary>
        /// Residual for this row after the global offset has been applied (milliseconds).
        /// Null for unmatched rows.
        /// </summary>
        public double? ResidualMs(double offsetMs)
        {
            if (!IsMatched) return null;
            return (LynxStart!.Value - Detection!.Value).TotalMilliseconds - offsetMs;
        }
    }

    /// <summary>
    /// Result of a calibration match operation.
    /// </summary>
    public record MatchResult(
        IReadOnlyList<CalibrationRow> Rows,
        double OffsetMs  // mean(LynxStart − Detection) across all matched pairs; 0 if no pairs
    )
    {
        /// <summary>Matched rows only.</summary>
        public IEnumerable<CalibrationRow> MatchedRows => Rows.Where(r => r.IsMatched);

        /// <summary>Number of matched pairs.</summary>
        public int PairCount => Rows.Count(r => r.IsMatched);
    }

    /// <summary>
    /// Matches gun detections to FinishLynx event start times using a greedy
    /// nearest-neighbour algorithm.
    ///
    /// Algorithm:
    ///   1. Sort both lists by time.
    ///   2. For each detection (ascending), find the nearest unmatched Lynx start
    ///      within <paramref name="maxMatchWindowMs"/>.
    ///   3. Compute offset = mean(LynxStart − Detection) over all matched pairs.
    ///   4. Append unmatched items as their own rows.
    ///
    /// Edge cases:
    ///   - Empty input on either side → all items unmatched, offset = 0.
    ///   - No pairs within window → all unmatched, offset = 0.
    ///   - Two detections equidistant from one Lynx time → earlier detection wins.
    ///   - Two Lynx times equidistant from one detection → earlier Lynx time wins.
    /// </summary>
    public static class CalibrationMatcher
    {
        /// <summary>
        /// Matches detections to Lynx start times.
        /// </summary>
        /// <param name="detections">Gun detection times (any order).</param>
        /// <param name="lynxStartTimes">FinishLynx event start times (any order).</param>
        /// <param name="maxMatchWindowMs">
        /// Maximum allowed gap between a detection and a Lynx start for them to be
        /// considered the same gun fired. Configurable; default 10 seconds.
        /// </param>
        public static MatchResult Match(
            IReadOnlyList<TimeSpan> detections,
            IReadOnlyList<TimeSpan> lynxStartTimes,
            double maxMatchWindowMs = 10_000.0)
        {
            var sortedDetections = detections.OrderBy(t => t).ToList();
            var sortedLynx = lynxStartTimes.OrderBy(t => t).ToList();

            var matchedPairs = new List<MatchedPair>();
            var usedDetectionIndices = new HashSet<int>();
            var usedLynxIndices = new HashSet<int>();

            // For each detection, find the nearest unmatched Lynx start within the window.
            for (int di = 0; di < sortedDetections.Count; di++)
            {
                var detection = sortedDetections[di];
                int bestLi = -1;
                double bestGapMs = double.MaxValue;

                for (int li = 0; li < sortedLynx.Count; li++)
                {
                    if (usedLynxIndices.Contains(li)) continue;

                    double gapMs = Math.Abs((sortedLynx[li] - detection).TotalMilliseconds);

                    // Strictly less than: ties go to the earlier Lynx time (lower li),
                    // which is visited first because sortedLynx is ascending.
                    if (gapMs < bestGapMs && gapMs <= maxMatchWindowMs)
                    {
                        bestGapMs = gapMs;
                        bestLi = li;
                    }
                }

                if (bestLi >= 0)
                {
                    matchedPairs.Add(new MatchedPair(detection, sortedLynx[bestLi]));
                    usedDetectionIndices.Add(di);
                    usedLynxIndices.Add(bestLi);
                }
            }

            // Compute offset = mean(LynxStart − Detection) across all matched pairs.
            double offsetMs = matchedPairs.Count > 0
                ? matchedPairs.Average(p => p.RawOffsetMs)
                : 0.0;

            // Build the ordered row list.
            // Strategy: walk through detections in time order. When a detection is matched,
            // emit a matched row. When a Lynx time falls *before* the next detection (or
            // there are no more detections), emit the Lynx-only row first.
            var rows = new List<CalibrationRow>();

            int dIdx = 0; // index into sortedDetections
            int lIdx = 0; // index into sortedLynx

            // Map matched detection → its Lynx partner (by sorted index).
            // Build a lookup: detection-index → lynx-index for matched pairs.
            // We need this for O(1) lookup during merge.
            var detectionToLynx = new Dictionary<int, int>();
            var lynxToDetection = new Dictionary<int, int>();
            {
                // Match pairs correlate by value; rebuild index map.
                // Since sortedDetections and sortedLynx are sorted, we can recover the
                // indices by tracking which (detection, lynx) pair maps to which indices.
                var usedD = new HashSet<int>();
                var usedL = new HashSet<int>();
                foreach (var pair in matchedPairs)
                {
                    int di2 = IndexOf(sortedDetections, pair.Detection, usedD);
                    int li2 = IndexOf(sortedLynx, pair.LynxStart, usedL);
                    detectionToLynx[di2] = li2;
                    lynxToDetection[li2] = di2;
                    usedD.Add(di2);
                    usedL.Add(li2);
                }
            }

            // Merge-walk both sorted lists to produce time-ordered rows.
            while (dIdx < sortedDetections.Count || lIdx < sortedLynx.Count)
            {
                bool hasD = dIdx < sortedDetections.Count;
                bool hasL = lIdx < sortedLynx.Count;

                if (hasD && (!hasL || sortedDetections[dIdx] <= sortedLynx[lIdx]))
                {
                    // Emit detection row (possibly matched).
                    if (detectionToLynx.TryGetValue(dIdx, out int matchedLi))
                    {
                        rows.Add(new CalibrationRow(sortedDetections[dIdx], sortedLynx[matchedLi]));
                        // Skip past the Lynx index if it comes next in the Lynx stream.
                        if (lIdx == matchedLi) lIdx++;
                        // Advance past any Lynx-only rows that fall before the matched Lynx time.
                        // (Those would be emitted before this matched row in the merge walk,
                        //  handled by the else branch below as lIdx catches up.)
                    }
                    else
                    {
                        rows.Add(new CalibrationRow(sortedDetections[dIdx], null));
                    }
                    dIdx++;
                }
                else
                {
                    // Emit Lynx-only row (already emitting matched Lynx rows via detection side above).
                    if (!lynxToDetection.ContainsKey(lIdx))
                        rows.Add(new CalibrationRow(null, sortedLynx[lIdx]));
                    lIdx++;
                }
            }

            return new MatchResult(rows, offsetMs);
        }

        // Returns the first index of `value` in `list` that is not in `skip`.
        private static int IndexOf<T>(List<T> list, T value, HashSet<int> skip)
        {
            for (int i = 0; i < list.Count; i++)
            {
                if (!skip.Contains(i) && EqualityComparer<T>.Default.Equals(list[i], value))
                    return i;
            }
            return -1; // should never happen if inputs are consistent
        }
    }
}
