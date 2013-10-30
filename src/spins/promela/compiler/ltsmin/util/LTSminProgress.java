package spins.promela.compiler.ltsmin.util;

public class LTSminProgress {
    int total = 0;
    int current = 0;
    int last = -1;
    final int width; // progress bar width in chars
    final LTSminDebug debug;

    public LTSminProgress(LTSminDebug debug, int width) { 
        this.width = width;
        this.debug = debug;
    }

    public void setTotal (int total) {
        this.total = total;
        this.current = 0;
        this.last = -1;
    }

    public void updateProgress() {
        updateProgress(1);
    }

    public void updateProgress(int num) {
        current = current + num;
        double progressPercentage = ((double)current) / total;
        int nr = (int)(progressPercentage*width);
        if (nr == last) return;
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
    }

    public void overwrite(String msg) {
        debug.carReturn();
        debug.say(msg);
    }

    public int getTotal() {
        return total;
    }
}
