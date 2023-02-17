package JCultureOL;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * uses an {@link InputStream} to provide an interruptable input handler
 */
public class ThreadsafeInputHandler {
    private InputStream source;
    private volatile boolean interrupted = false;

    /**
     * call is equivalent to {@link ThreadsafeInputHandler#ThreadsafeInputHandler(Inputstream source, boolean interrupted)} where <code>interrupted = false</code>
     * @param source {@link InputStream} to read from
     */
    public ThreadsafeInputHandler(InputStream source) {
        this.source = source;
    }
    /**
     * creates a new {@link ThreadsafeInputHandler}
     * @param source {@link InputStream} to read from
     * @param interrupted boolean whether the handler starts as interrupted
     */
    public ThreadsafeInputHandler(InputStream source, boolean interrupted) {
        this.source = source;
        this.interrupted = interrupted;
    }
    public int getChar() throws IOException {
        return getChar(true);
    }
    public int getChar(boolean waitfor) throws IOException {
        while (true) {
            if (interrupted) {
                throw new IOException("input was interrupted");
            }
            if (source.available() > 0) { // only reads if source has available bytes, prevents blocking by the read operation so state can be checked
                return source.read();
            } else if (!waitfor) {
                return -1;
            }
        }
    }
    /**
     * call is equivalent to {@link ThreadsafeInputHandler#getCharUnsafe(boolean waitfor)} where <code>waitfor = true</code>
     * @return the resulting char
     * @throws IOException
     */
    public int getCharUnsafe() throws IOException {
        return getCharUnsafe(true);
    }
    /**
     * gets a char from the source input stream
     * @param waitfor boolean
     * @return <code>-1</code> if the {@link ThreadsafeInputHandler} is interrupted or <code>waitfor = false</code> and <code>source.available()</code> returns <code>0</code>, otherwise returns the result of <code>source.read()</code>
     * @throws IOException
     */
    public int getCharUnsafe(boolean waitfor) throws IOException {
        while (true) {
            if (interrupted) {
                return -1;
            }
            if (source.available() > 0) {
                return source.read();
            } else if (!waitfor) {
                return -1;
            }
        }
    }
    /**
     * call is equivalent to {@code getChars(count, true)}
     * @param count number of chars to get
     * @return the resulting chars
     * @throws IOException
     */
    public int[] getChars(int count) throws IOException {
        return getChars(count, true);
    }
    /**
     * gets a specified number of characters
     * @param count
     * @param waitfor
     * @return the resulting characters, returns whatever chars have already been read if {@link ThreadsafeInputHandler#getChar(boolean waitfor)} returns <code>-1</code>
     * @throws IOException
     */
    public int[] getChars(int count, boolean waitfor) throws IOException {
        if (count < 0) {
            throw new IllegalArgumentException("count must be greater than or equal to zero");
        }
        int[] result = new int[count];
        int i = 0;
        while (i < count) {
            int gotten = getChar(waitfor);
            if (gotten == -1) {
                result = Arrays.copyOf(result, i);
            }
            result[i++] = gotten;
        }
        return result;
    }
    /**
     * call is equivalent to {@code getCharsUnsafe(count, true)}
     * @param count number of chars to get
     * @return the resulting chars
     * @throws IOException
     */
    public int[] getCharsUnsafe(int count) throws IOException {
        return getChars(count, true);
    }
    /**
     * gets a specified number of characters
     * @param count
     * @param waitfor
     * @return the resulting characters, returns whatever chars have already been read if {@link ThreadsafeInputHandler#getCharUnsafe(boolean waitfor)} returns <code>-1</code>
     * @throws IOException
     */
    public int[] getCharsUnsafe(int count, boolean waitfor) throws IOException {
        if (count < 0) {
            throw new IllegalArgumentException("count must be greater than or equal to zero");
        }
        int[] result = new int[count];
        int i = 0;
        while (i < count) {
            int gotten = getCharUnsafe(waitfor);
            if (gotten == -1) {
                result = Arrays.copyOf(result, i);
            }
            result[i++] = gotten;
        }
        return result;
    }
    /**
     * @return the value of the interrupted flag
     */
    public boolean isInterrupted() {
        return interrupted;
    }
    /**
     * clears the interrupted flag
     */
    public void clearInterrupt() {
        interrupted = false;
    }
    /**
     * interrupts the handler
     */
    public void interrupt() {
        interrupted = true;
    }
}