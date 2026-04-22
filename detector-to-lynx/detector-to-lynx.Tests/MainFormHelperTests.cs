namespace detector_to_lynx.Tests
{
    public class MainFormHelperTests
    {
        private static DateTime At(int h, int m, int s)
            => new DateTime(2026, 4, 22, h, m, s);

        [Fact]
        public void ComputeOffset_TimeInPast_ReturnsPositiveSeconds()
        {
            var now = At(14, 30, 0);
            var offset = MainForm.ComputeOffset("14:20:00", now);
            Assert.Equal(600.0, offset, precision: 1);
        }

        [Fact]
        public void ComputeOffset_TimeInFuture_ReturnsZero()
        {
            var now = At(14, 20, 0);
            var offset = MainForm.ComputeOffset("14:30:00", now);
            Assert.Equal(0.0, offset);
        }

        [Fact]
        public void ComputeOffset_SameSecond_ReturnsZero()
        {
            var now = At(10, 0, 0);
            var offset = MainForm.ComputeOffset("10:00:00", now);
            Assert.Equal(0.0, offset, precision: 1);
        }

        [Fact]
        public void ComputeOffset_OneSecondAgo_ReturnsOne()
        {
            var now = At(10, 0, 1);
            var offset = MainForm.ComputeOffset("10:00:00", now);
            Assert.Equal(1.0, offset, precision: 1);
        }

        [Fact]
        public void ComputeOffset_UnparseableString_ThrowsFormatException()
        {
            Assert.Throws<FormatException>(() => MainForm.ComputeOffset("not-a-time", DateTime.Now));
        }
    }
}
