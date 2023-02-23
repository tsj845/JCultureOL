package JCultureOL;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ProcessBuilder.Redirect;
import java.util.Arrays;

/**
 * uses an {@link InputStream} to provide an interruptable input handler
 */
public class InterruptableInputHandler {
    private InputStream source;
    private OutputStream drain;
    private volatile boolean interrupted = false;
    private volatile boolean rawterm = false;
    private volatile String sttySettings;

    /**
     * call is equivalent to {@link InterruptableInputHandler#ThreadsafeInputHandler(Inputstream source, boolean interrupted)} where <code>interrupted = false</code>
     * @param source {@link InputStream} to read from
     * @param drain {@link OutputStream} to write to
     */
    public InterruptableInputHandler(InputStream source, OutputStream drain) {
        this.source = source;
        this.drain = drain;
    }
    /**
     * creates a new {@link InterruptableInputHandler}
     * @param source {@link InputStream} to read from
     * @param drain {@link OutputStream} to write to
     * @param interrupted boolean whether the handler starts as interrupted
     */
    public InterruptableInputHandler(InputStream source, OutputStream drain, boolean interrupted) {
        this.source = source;
        this.drain = drain;
        this.interrupted = interrupted;
    }
    /**
     * used to signal to the user that an invalid input was made, such as attempting to move the cursor left when at the start of the line
     * @throws IOException
     */
    public void signalInvalid() throws IOException {
        drain.write('\u0007');
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
     * call is equivalent to {@link InterruptableInputHandler#getCharUnsafe(boolean waitfor)} where <code>waitfor = true</code>
     * @return the resulting char
     * @throws IOException
     */
    public int getCharUnsafe() throws IOException {
        return getCharUnsafe(true);
    }
    /**
     * gets a char from the source input stream
     * @param waitfor boolean
     * @return <code>-1</code> if the {@link InterruptableInputHandler} is interrupted or <code>waitfor = false</code> and <code>source.available()</code> returns <code>0</code>, otherwise returns the result of <code>source.read()</code>
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
     * @return the resulting characters, returns whatever chars have already been read if {@link InterruptableInputHandler#getChar(boolean waitfor)} returns <code>-1</code>
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
     * @return the resulting characters, returns whatever chars have already been read if {@link InterruptableInputHandler#getCharUnsafe(boolean waitfor)} returns <code>-1</code>
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
    private void redisplayLine(String prompt, StringBuilder cinput, int curpos) throws IOException {
        drain.write(new byte[]{0x1b, '[', '8', 'm', 0x1b, '[', '2', 'K', 13}); // reset line and put cursor at left edge
        // drain.write(new byte[]{0x1b, '[', '6', 'n'});
        byte[] pbytes = prompt.getBytes();
        drain.write(pbytes); // write the prompt
        cinput.chars().forEach(n->{try{drain.write(n);}catch(IOException e){}}); // write the input currently entered
        // int dif = cinput.length()-curpos;
        // if (dif > 0) {
        //     drain.write(new byte[]{0x1b, '[', (byte)(dif), 'D'});
        // }
        // drain.write(Integer.toString(curpos).getBytes()); // TODO: cursor debug
        drain.write(new byte[]{0x1b, '['});
        drain.write(Integer.toString(curpos + pbytes.length + 1).getBytes());
        drain.write(new byte[]{'G', 0x1b, '[', '2', '8', 'm'});
        drain.flush();
    }
    /**
     * call is equivalent to {@link InterruptableInputHandler#readLine(String prompt)} where prompt is an empty string
     * @return
     * @throws IOException
     */
    public String readLine() throws IOException {
        return readLine("");
    }
    /**
     * reads until the end of the line, all input is handled properly, result will not include the line separator
     * @param propmt String prompt to display to the user
     * @return the user input
     * @throws IOException
     */
    public String readLine(String prompt) throws IOException {
        StringBuilder sb = new StringBuilder();
        int curpos = 0;
        int gotten = getChar();
        while (true) {
            if (gotten == '\n' || gotten == '\r') { // handle end of line
                if (rawterm) { // raw mode will not show user input in the terminal's output
                    drain.write(new byte[]{10, 13});
                } else if (gotten == '\r') {
                    source.read(); // when not in raw mode, '\r' is always followed by '\n' which must also be read
                }
                return sb.toString();
            }
            if (gotten == '\u001b') { // handle escape sequences
                int check = getChar(false); // don't wait for anymore user input in case there isn't any
                if (check == -1 || check != '[') { // escape key, causes termination
                    gotten = '\n';
                } else { // check is guarranteed to be '[' at this point
                    String res;
                    char finalc;
                    { // prevent namespace pollution
                        StringBuilder checker = new StringBuilder(1);
                        while (true) {
                            int next = getChar();
                            checker.append((char)next);
                            if (Character.isAlphabetic(next)) {
                                finalc = (char)next;
                                break;
                            }
                        }
                        res = checker.toString();
                    }
                    switch (finalc) { // do various things based on the control code entered
                        case ('C'): // (CUR RIGHT) move cursor right for text editing
                            if (curpos < sb.length()) {
                                curpos ++;
                                if (rawterm) {
                                    redisplayLine(prompt, sb, curpos);
                                }
                            } else {
                                signalInvalid();
                            }
                            break;
                        case ('D'): // (CUR LEFT) move cursor left for text editing
                            if (curpos > 0) {
                                curpos --;
                                if (rawterm) {
                                    redisplayLine(prompt, sb, curpos);
                                }
                            } else {
                                signalInvalid();
                            }
                            break;
                        case ('A'): // (CUR UP) no history implemented
                        case ('B'): // (CUR DOWN) no history implemented
                        case ('m'): // (GRAPHIC SETTING) no color changes allowed
                        default: // unrecognized, do nothing
                            break;
                    }
                    gotten = getChar();
                    // if (rawterm) {
                    //     redisplayLine(prompt, sb, curpos);
                    // }
                }
                continue;
            }
            if (gotten == 127) {
                if (curpos > 0) {
                    curpos --;
                    sb.deleteCharAt(curpos);
                } else {
                    signalInvalid();
                }
                if (rawterm) {
                    redisplayLine(prompt, sb, curpos);
                }
                gotten = getChar();
                continue;
            }
            sb.insert(curpos, (char)gotten);
            curpos ++;
            if (rawterm) {
                // try {
                //     Thread.sleep(500);
                // } catch (InterruptedException e) {
                //     // TODO Auto-generated catch block
                //     e.printStackTrace();
                // }
                redisplayLine(prompt, sb, curpos);
            }
            gotten = getChar();
        }
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
    /**
     * uses stty to put the terminal in raw mode, saves the original settings which can be restored through {@link InterruptableInputHandler#unmakeRaw()}
     * @throws IOException
     */
    public void makeRaw() throws IOException {
        Process proc = new ProcessBuilder("stty", "-g").redirectInput(Redirect.INHERIT).start();
        try {
            proc.waitFor();
        } catch (InterruptedException E) {}
        sttySettings = new String(proc.getInputStream().readAllBytes());
        // System.out.println(new String(new ProcessBuilder("stty", "-g").redirectInput(Redirect.INHERIT).start().getInputStream().readAllBytes()));
        try {
            new ProcessBuilder("stty", "raw").inheritIO().start().waitFor();
        } catch (InterruptedException E) {}
        setRaw();
    }
    /**
     * uses stty to restore original terminal settings which can be stored through {@link InterruptableInputHandler#makeRaw()}
     * @throws IOException
     */
    public void unmakeRaw() throws IOException {
        Process proc = new ProcessBuilder("stty", sttySettings).inheritIO().start();
        try {
            proc.waitFor();
        } catch (InterruptedException E) {}
        clearRaw();
    }
    /**
     * sets the rawterm flag, DOES NOT CHANGE TERMINAL MODE
     */
    public void setRaw() {
        rawterm = true;
    }
    /**
     * clears the rawterm flag, DOES NOT CHANGE TERMINAL MODE
     */
    public void clearRaw() {
        rawterm = false;
    }
}