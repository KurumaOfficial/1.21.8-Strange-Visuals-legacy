package ru.strange.client.utils.math;

public class TimerUtil {

    private long startTime;

    public long lastMS = System.currentTimeMillis();

    public void reset() {
        lastMS = System.currentTimeMillis();
    }

    public boolean isReached(long time) {
        return System.currentTimeMillis() - lastMS > time;
    }

    public void setLastMS(long newValue) {
        lastMS = System.currentTimeMillis() + newValue;
    }

    public void setTime(long time) {
        lastMS = time;
    }

    public boolean finished(final double delay) {
        return System.currentTimeMillis() - delay >= startTime;
    }


    public boolean every(final double delay) {
        boolean finished = this.finished(delay);
        if (finished) reset();
        return finished;
    }

    public long elapsedTime() {
        return System.currentTimeMillis() - this.startTime;
    }

    public void setMs(long ms) {
        this.startTime = System.currentTimeMillis() - ms;
    }

    public long getTime() {
        return System.currentTimeMillis() - lastMS;
    }

    public boolean isRunning() {
        return System.currentTimeMillis() - lastMS <= 0;
    }

    public boolean hasTimeElapsed(long l) {
        return System.currentTimeMillis() - this.lastMS > l;
    }

    public boolean hasTimeElapsed() {
        return lastMS < System.currentTimeMillis();
    }


    public boolean hasTimeElapsed(long time, boolean reset) {
        if (System.currentTimeMillis() - lastMS > time) {
            if (reset) reset();
            return true;
        }

        return false;
    }

    public boolean hasReached(double milliseconds) {
        return getTimePassed() >= milliseconds;
    }

    public long getTimePassed() {
        return System.currentTimeMillis() - lastMS;
    }

    public long setTimePassed(int delay) {
        return System.currentTimeMillis() + delay;
    }

    public long getLastMS() {
        return this.lastMS;
    }

    public void setLastMC() {
        lastMS = System.currentTimeMillis();
    }


    public boolean finished(long delay) {
        return System.currentTimeMillis() - this.lastMS >= delay;
    }

    public boolean every(long delay) {
        if (System.currentTimeMillis() - this.lastMS >= delay) {
            this.reset();
            return true;
        }
        return false;
    }

    public boolean passed(long ms) {
        return System.currentTimeMillis() - lastMS >= ms;
    }


    public static class satosTime {
        public long startTime;

        public long mc = System.currentTimeMillis();

        public void reset() {
            this.mc = System.currentTimeMillis();
        }

        public long getMc() {
            return System.currentTimeMillis() - this.mc;
        }

        public boolean hasReached(long n) {
            return System.currentTimeMillis() - this.mc > n;
        }
        public boolean hasReachedSecond(float s) {
            long n = (long) (s * 1000);
            return System.currentTimeMillis() - this.mc > n;
        }

        public boolean finished(final double delay) {
            return System.currentTimeMillis() - delay >= startTime;
        }

        public boolean hasReached(long time, boolean reset) {
            boolean hasElapsed = getMc() >= time;
            if (hasElapsed && reset) {
                reset();
            }
            return hasElapsed;
        }
        private long getCurrentMS() {
            return System.currentTimeMillis();
        }
        public long getTime() {
            return this.getCurrentMS() - this.mc;
        }
    }

    public static class legacyTime {
        private long lastMS;

        private legacyTime() {
            reset();
        }

        public static legacyTime create() {
            return new legacyTime();
        }

        public void reset() {
            lastMS = System.currentTimeMillis();
        }

        public long elapsedTime() {
            return System.currentTimeMillis() - lastMS;
        }

        public boolean hasReached(long time) {
            return elapsedTime() >= time;
        }

        public boolean hasReached(long time, boolean reset) {
            boolean hasElapsed = elapsedTime() >= time;
            if (hasElapsed && reset) {
                reset();
            }
            return hasElapsed;
        }

        public boolean hasReached(double ms) {
            return elapsedTime() >= ms;
        }

        public boolean delay(long ms) {
            boolean hasDelayElapsed = elapsedTime() - ms >= 0;
            if (hasDelayElapsed) {
                reset();
            }
            return hasDelayElapsed;
        }
    }

}
