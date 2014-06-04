package spins.promela.compiler.ltsmin.util;

public class LTSminProgress {
    public static int DEFAULT_WIDTH = 50;

    int total = 0;
    int current = 0;
    int last = -1;
    final int width; // progress bar width in chars
    final LTSminDebug debug;

    public LTSminProgress(LTSminDebug debug, int width) { 
        this.width = width;
        this.debug = debug;
    }

    public LTSminProgress(LTSminDebug debug) { 
        this(debug, DEFAULT_WIDTH);
    }

    public void setTotal (int total) {
        this.total = total;
        this.current = 0;
        this.last = -1;
    }

    public void updateProgress() {
        updateProgress(1);
    }

    public LTSminProgress updateProgress(int num) {
        current = current + num;
        double progressPercentage = ((double)current) / total;
        int nr = (int)(progressPercentage*width);
        if (nr == last) return this;
        last = nr;
        nr = nr < width ? nr : width;

        debug.carReturn();
        debug.add("[");
        int i = 0;
        for (; i < nr; i++)
            debug.add(".");
        for (; i < width; i++)
            debug.add(" ");
        debug.add("]");
        debug.addDone();
        return this;
    }

    public void overwrite(String msg) {
        debug.carReturn();
        debug.say(msg);
    }

    public int getTotal() {
        return total;
    }

    public String totals(int n, String msg) {
        double perc = ((double)n * 100) / getTotal();
        return String.format("Found %,10d /%,11d (%5.1f%%) %s               ",
                             n, getTotal(), perc, msg);
    }

    public void overwriteReport(int num, String msg) {
        overwrite(totals(num, msg));
    }

    long nano = 0;

    public LTSminProgress resetTimer() {
        nano = 0;
        return this;
    }
    
    public LTSminProgress startTimer() {
        nano += System.nanoTime();
        return this;
    }

    public LTSminProgress stopTimer() {
        nano = System.nanoTime() - nano;
        return this;
    }

    public String micro() {
        return String.format("%.1f", ((double)nano) / 1000);
    }

    public String millis() {
        return String.format("%.1f", ((double)nano) / 1000000);
    }

    public String sec() {
        return String.format("%.1f", ((double)nano) / 1000000000);
    }


    public void overwriteTotals(int n, String msg) {
        double perc = ((double)n * 100) / getTotal();
        String s = String.format("Found %,10d /%,11d (%5.1f%%) %s               ",
                                 n, getTotal(), perc, msg);
        overwrite(s);
    }

    public void overwriteTotals( int r, int mayW, int mustW, String msg) {
        double percR = ((double)r * 100) / getTotal();
        double percMayW = ((double)mayW * 100) / getTotal();
        double percMustW = ((double)mustW * 100) / getTotal();
        String s = String.format("Found %,9d, %,9d, %,9d / %,9d (%5.1f%%, %5.1f%%, %5.1f%%) %s               ",
                                 r, mayW, mustW, getTotal(), percR, percMayW, percMustW, msg);
        overwrite(s);
    }
}
