// import java.lang.ProcessBuilder.Redirect;
// import java.util.Arrays;

import JCultureOL.InterruptableInputHandler;

public class Test {
    public static void main(String[] args) throws Exception {
        // System.out.print("c\u001b[Dd\r\n");
        // System.out.println("\u0001fb2f");
        InterruptableInputHandler inputh = new InterruptableInputHandler(System.in, System.out);
        inputh.makeRaw();
        // inputh.setRaw();
        String line;
        try {
            line = inputh.readLine();
        } finally {
            inputh.unmakeRaw();
        }
        System.out.println(line);
        // Process proc = new ProcessBuilder("stty", "-g").redirectInput(Redirect.INHERIT).start();
        // String sttySettings = new String(proc.getInputStream().readAllBytes());
        // new ProcessBuilder("stty", "raw").inheritIO().start().waitFor();
        // int[] r2 = new int[2];
        // try {
        //     r2[0] = System.in.read();
        //     r2[1] = System.in.read();
        // } finally {
        //     new ProcessBuilder("stty", sttySettings).inheritIO().start().waitFor();
        // }
        // System.out.println(Arrays.toString(r2));
    }
}
