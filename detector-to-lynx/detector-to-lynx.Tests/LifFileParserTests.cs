namespace detector_to_lynx.Tests
{
    public class LifFileParserTests
    {
        // ─── ParseStartTimeFromContent ────────────────────────────────────────

        [Fact]
        public void ParseStartTimeFromContent_ValidLine_ReturnsCorrectTime()
        {
            // Real .lif header row format: field 10 (0-based) = start time
            var line = "1,1,1,Boys 4x800 Relay Middle School,,,,,,3200,15:58:27.655";
            var result = LifFileParser.ParseStartTimeFromContent(line);
            Assert.Equal(new TimeSpan(15, 58, 27) + TimeSpan.FromMilliseconds(655), result);
        }

        [Fact]
        public void ParseStartTimeFromContent_FourDecimalPlaces_TruncatesToMilliseconds()
        {
            // .lif files write 4 decimal digits (1/10000 s); the 4th digit must be dropped
            var line = "1,1,1,Event,,,,,,3200,09:12:34.6789";
            var result = LifFileParser.ParseStartTimeFromContent(line);
            Assert.Equal(new TimeSpan(9, 12, 34) + TimeSpan.FromMilliseconds(678), result);
        }

        [Fact]
        public void ParseStartTimeFromContent_ThreeDecimalPlaces_ParsesCorrectly()
        {
            var line = "1,1,1,Event,,,,,,3200,09:12:34.678";
            var result = LifFileParser.ParseStartTimeFromContent(line);
            Assert.Equal(new TimeSpan(9, 12, 34) + TimeSpan.FromMilliseconds(678), result);
        }

        [Fact]
        public void ParseStartTimeFromContent_NoFractionalSeconds_ParsesCorrectly()
        {
            var line = "1,1,1,Event,,,,,,3200,10:00:00";
            var result = LifFileParser.ParseStartTimeFromContent(line);
            Assert.Equal(new TimeSpan(10, 0, 0), result);
        }

        [Fact]
        public void ParseStartTimeFromContent_EmptyString_ReturnsNull()
        {
            var result = LifFileParser.ParseStartTimeFromContent(string.Empty);
            Assert.Null(result);
        }

        [Fact]
        public void ParseStartTimeFromContent_WhitespaceOnly_ReturnsNull()
        {
            var result = LifFileParser.ParseStartTimeFromContent("   ");
            Assert.Null(result);
        }

        [Fact]
        public void ParseStartTimeFromContent_FewerThan11Fields_ReturnsNull()
        {
            // Only 10 fields (indices 0..9); index 10 is absent
            var line = "1,1,1,Event,,,,,,3200";
            var result = LifFileParser.ParseStartTimeFromContent(line);
            Assert.Null(result);
        }

        [Fact]
        public void ParseStartTimeFromContent_EmptyStartTimeField_ReturnsNull()
        {
            // Field 10 is empty
            var line = "1,1,1,Event,,,,,,3200,";
            var result = LifFileParser.ParseStartTimeFromContent(line);
            Assert.Null(result);
        }

        [Fact]
        public void ParseStartTimeFromContent_MalformedTime_ReturnsNull()
        {
            var line = "1,1,1,Event,,,,,,3200,not-a-time";
            var result = LifFileParser.ParseStartTimeFromContent(line);
            Assert.Null(result);
        }

        [Fact]
        public void ParseStartTimeFromContent_ExtraFieldsAfterStartTime_ParsesCorrectly()
        {
            // Header row with extra trailing fields (unusual but defensive)
            var line = "1,1,1,Event,,,,,,3200,15:58:27.655,extra,data";
            var result = LifFileParser.ParseStartTimeFromContent(line);
            Assert.Equal(new TimeSpan(15, 58, 27) + TimeSpan.FromMilliseconds(655), result);
        }

        [Fact]
        public void ParseStartTimeFromContent_WhitespaceAroundTime_ParsesCorrectly()
        {
            var line = "1,1,1,Event,,,,,,3200, 14:30:00.123 ";
            var result = LifFileParser.ParseStartTimeFromContent(line);
            Assert.Equal(new TimeSpan(14, 30, 0) + TimeSpan.FromMilliseconds(123), result);
        }
    }
}
