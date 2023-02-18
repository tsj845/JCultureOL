package JCultureOL;
import java.io.IOException;
import java.io.InputStream;

/**
 * provides a way for an inputstream to be interrupted by a parent thread allowing a child thread to exit safely
 */
public class InterruptableInputStream extends InputStream {
    private InputStream source;
    private volatile boolean interrupted = false;
    private volatile boolean closed = false;

    public InterruptableInputStream(InputStream source) {
        this.source = source;
    }

    public InterruptableInputStream(InputStream source, boolean interrupted) {
        this.source = source;
        this.interrupted = interrupted;
    }

    @Override
    public int read() throws IOException {
        while (true) {
            if (interrupted) {
                throw new IOException("read operation was interrupted via a method");
            }
            if (source.available() > 0) {
                return source.read();
            }
        }
    }

    /**
     * sets the closed flag and calls {@link InterruptableInputStream#interrupt()}
     */
    @Override
    public void close() throws IOException {
        closed = true;
        interrupt();
    }

    /**
     * clears the interrupted flag
     * @throws IllegalStateException if the closed flag is set
     */
    public void clearInterrupt() {
        if (closed) {
            throw new IllegalStateException("interrupt cannot be cleared when the stream has been closed");
        }
        interrupted = false;
    }

    /**
     * sets the interrupted flag
     */
    public void interrupt() {
        interrupted = true;
    }
    
}
