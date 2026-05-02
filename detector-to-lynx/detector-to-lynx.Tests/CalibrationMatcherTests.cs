namespace detector_to_lynx.Tests
{
    /// <summary>
    /// Helper to build TimeSpan values concisely in tests.
    /// </summary>
    file static class T
    {
        public static TimeSpan At(int h, int m, int s, int ms = 0) =>
            new TimeSpan(0, h, m, s, ms);
    }

    public class CalibrationMatcherTests
    {
        // ─── Empty / trivial inputs ───────────────────────────────────────────

        [Fact]
        public void Match_BothEmpty_EmptyRows_ZeroOffset()
        {
            var result = CalibrationMatcher.Match([], []);
            Assert.Empty(result.Rows);
            Assert.Equal(0.0, result.OffsetMs);
        }

        [Fact]
        public void Match_EmptyDetections_AllLynxUnmatched()
        {
            var lynx = new[] { T.At(10, 0, 0), T.At(10, 5, 0) };
            var result = CalibrationMatcher.Match([], lynx);
            Assert.Equal(2, result.Rows.Count);
            Assert.All(result.Rows, r => Assert.False(r.IsMatched));
            Assert.All(result.Rows, r => Assert.True(r.LynxStart.HasValue));
            Assert.Equal(0.0, result.OffsetMs);
        }

        [Fact]
        public void Match_EmptyLynxTimes_AllDetectionsUnmatched()
        {
            var detections = new[] { T.At(10, 0, 0), T.At(10, 5, 0) };
            var result = CalibrationMatcher.Match(detections, []);
            Assert.Equal(2, result.Rows.Count);
            Assert.All(result.Rows, r => Assert.False(r.IsMatched));
            Assert.All(result.Rows, r => Assert.True(r.Detection.HasValue));
            Assert.Equal(0.0, result.OffsetMs);
        }

        // ─── Single-pair scenarios ────────────────────────────────────────────

        [Fact]
        public void Match_PerfectOneToOneMatch_SinglePair()
        {
            var det = T.At(10, 0, 0);
            var lynx = T.At(10, 0, 0, 200); // Lynx is 200ms after detection
            var result = CalibrationMatcher.Match([det], [lynx]);
            Assert.Equal(1, result.PairCount);
            Assert.Equal(200.0, result.OffsetMs, precision: 3);
        }

        [Fact]
        public void Match_PerfectOneToOne_ResidualIsZero()
        {
            var det = T.At(10, 0, 0);
            var lynx = T.At(10, 0, 0, 200);
            var result = CalibrationMatcher.Match([det], [lynx]);
            var row = result.Rows.Single();
            Assert.Equal(0.0, row.ResidualMs(result.OffsetMs)!.Value, precision: 3);
        }

        [Fact]
        public void Match_DetectionFarFromLynx_NoMatch()
        {
            var det = T.At(10, 0, 0);
            var lynx = T.At(10, 0, 15); // 15 seconds apart, window default 10s
            var result = CalibrationMatcher.Match([det], [lynx], maxMatchWindowMs: 10_000);
            Assert.Equal(0, result.PairCount);
            Assert.Equal(2, result.Rows.Count);
            Assert.Equal(0.0, result.OffsetMs);
        }

        [Fact]
        public void Match_ExactlyAtWindowEdge_IsMatched()
        {
            var det = T.At(10, 0, 0);
            var lynx = T.At(10, 0, 10); // exactly 10000ms = within window
            var result = CalibrationMatcher.Match([det], [lynx], maxMatchWindowMs: 10_000);
            Assert.Equal(1, result.PairCount);
        }

        [Fact]
        public void Match_JustOverWindowEdge_NotMatched()
        {
            var det = T.At(10, 0, 0);
            var lynx = new TimeSpan(0, 10, 0, 10, 1); // 10000.1ms
            var result = CalibrationMatcher.Match([det], [lynx], maxMatchWindowMs: 10_000);
            Assert.Equal(0, result.PairCount);
        }

        // ─── Multiple pairs ───────────────────────────────────────────────────

        [Fact]
        public void Match_PerfectMultiplePairs_CorrectOffset()
        {
            // All Lynx starts are exactly 300ms later than detections.
            var detections = new[]
            {
                T.At(10, 0, 0),
                T.At(10, 5, 0),
                T.At(10, 10, 0)
            };
            var lynx = new[]
            {
                T.At(10, 0, 0, 300),
                T.At(10, 5, 0, 300),
                T.At(10, 10, 0, 300)
            };

            var result = CalibrationMatcher.Match(detections, lynx);
            Assert.Equal(3, result.PairCount);
            Assert.Equal(300.0, result.OffsetMs, precision: 3);
        }

        [Fact]
        public void Match_PerfectMultiplePairs_AllResidualNearZero()
        {
            var detections = new[]
            {
                T.At(10, 0, 0),
                T.At(10, 5, 0)
            };
            var lynx = new[]
            {
                T.At(10, 0, 0, 300),
                T.At(10, 5, 0, 300)
            };

            var result = CalibrationMatcher.Match(detections, lynx);
            foreach (var row in result.MatchedRows)
                Assert.Equal(0.0, row.ResidualMs(result.OffsetMs)!.Value, precision: 3);
        }

        [Fact]
        public void Match_OffsetCalculatedAsAverage()
        {
            // Lynx is 100ms ahead for first, 300ms ahead for second → average 200ms
            var detections = new[] { T.At(10, 0, 0), T.At(10, 5, 0) };
            var lynx = new[] { T.At(10, 0, 0, 100), T.At(10, 5, 0, 300) };
            var result = CalibrationMatcher.Match(detections, lynx);
            Assert.Equal(200.0, result.OffsetMs, precision: 3);
        }

        [Fact]
        public void Match_OffsetCalculated_ResidualsMirrorDeviation()
        {
            // offset = 200ms, first pair is 100ms (residual -100ms), second is 300ms (residual +100ms)
            var detections = new[] { T.At(10, 0, 0), T.At(10, 5, 0) };
            var lynx = new[] { T.At(10, 0, 0, 100), T.At(10, 5, 0, 300) };
            var result = CalibrationMatcher.Match(detections, lynx);
            var residuals = result.MatchedRows.Select(r => r.ResidualMs(result.OffsetMs)!.Value).ToList();
            Assert.Equal(-100.0, residuals[0], precision: 3);
            Assert.Equal(100.0, residuals[1], precision: 3);
        }

        // ─── Unequal counts ───────────────────────────────────────────────────

        [Fact]
        public void Match_MoreDetectionsThanLynx_ExtraDetectionsUnmatched()
        {
            var detections = new[]
            {
                T.At(10, 0, 0),
                T.At(10, 5, 0),
                T.At(10, 10, 0)
            };
            var lynx = new[] { T.At(10, 0, 0, 200) };

            var result = CalibrationMatcher.Match(detections, lynx);
            Assert.Equal(1, result.PairCount);
            Assert.Equal(3, result.Rows.Count(r => r.Detection.HasValue));
            Assert.Equal(1, result.Rows.Count(r => r.IsMatched));
        }

        [Fact]
        public void Match_MoreLynxThanDetections_ExtraLynxUnmatched()
        {
            var detections = new[] { T.At(10, 0, 0, 200) };
            var lynx = new[]
            {
                T.At(10, 0, 0),
                T.At(10, 5, 0),
                T.At(10, 10, 0)
            };

            var result = CalibrationMatcher.Match(detections, lynx);
            Assert.Equal(1, result.PairCount);
            Assert.Equal(3, result.Rows.Count(r => r.LynxStart.HasValue));
        }

        // ─── Tie-breaking ─────────────────────────────────────────────────────

        [Fact]
        public void Match_TwoDetectionsNearOneLynxTime_OnlyClosestMatches()
        {
            // Detection A is 1s before Lynx; Detection B is 1s after Lynx.
            // They are equidistant. Detection A comes first → wins.
            var lynx = T.At(10, 0, 1); // 10:00:01
            var detA = T.At(10, 0, 0); // 1s before
            var detB = T.At(10, 0, 2); // 1s after (same gap)

            var result = CalibrationMatcher.Match([detA, detB], [lynx]);
            Assert.Equal(1, result.PairCount);
            // The matched pair should be (detA, lynx) — detA processed first.
            var matchedRow = result.Rows.Single(r => r.IsMatched);
            Assert.Equal(detA, matchedRow.Detection);
        }

        [Fact]
        public void Match_TwoLynxTimesNearOneDetection_ClosestLynxWins()
        {
            var det = T.At(10, 0, 0);
            var lynxClose = T.At(10, 0, 0, 500); // 500ms away
            var lynxFar = T.At(10, 0, 3);         // 3000ms away

            var result = CalibrationMatcher.Match([det], [lynxFar, lynxClose]);
            var matchedRow = result.Rows.Single(r => r.IsMatched);
            Assert.Equal(lynxClose, matchedRow.LynxStart);
        }

        // ─── Sorting ──────────────────────────────────────────────────────────

        [Fact]
        public void Match_UnsortedInputs_ProducesCorrectResult()
        {
            var detections = new[]
            {
                T.At(10, 10, 0),
                T.At(10, 0, 0),
                T.At(10, 5, 0)
            };
            var lynx = new[]
            {
                T.At(10, 5, 0, 300),
                T.At(10, 10, 0, 300),
                T.At(10, 0, 0, 300)
            };

            var result = CalibrationMatcher.Match(detections, lynx);
            Assert.Equal(3, result.PairCount);
            Assert.Equal(300.0, result.OffsetMs, precision: 3);
        }

        // ─── Row ordering ────────────────────────────────────────────────────

        [Fact]
        public void Match_Rows_AreOrderedByTime()
        {
            var detections = new[] { T.At(10, 5, 0), T.At(10, 0, 0) };
            var lynx = new[] { T.At(10, 2, 30) }; // unmatched Lynx between the two

            var result = CalibrationMatcher.Match(detections, lynx);

            var times = result.Rows.Select(r => r.Detection ?? r.LynxStart ?? TimeSpan.Zero).ToList();
            Assert.Equal(times.OrderBy(t => t).ToList(), times);
        }

        [Fact]
        public void Match_LynxOnlyRow_HasNullResidual()
        {
            var lynx = T.At(10, 0, 0);
            var result = CalibrationMatcher.Match([], [lynx]);
            Assert.Null(result.Rows.Single().ResidualMs(0));
        }

        [Fact]
        public void Match_DetectionOnlyRow_HasNullResidual()
        {
            var det = T.At(10, 0, 0);
            var result = CalibrationMatcher.Match([det], []);
            Assert.Null(result.Rows.Single().ResidualMs(0));
        }
    }
}
