import java.io.IOException;

import JCultureOL.ThreadsafeInputStream;

public class Test {
    public static void main(String[] args) throws IOException {
        ThreadsafeInputStream inp = new ThreadsafeInputStream(System.in);
        Thread th = new Thread(){
            public void run() {
                try {
                Thread.sleep(1500);
                inp.close();
                } catch (InterruptedException e) {}
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        th.start();
        System.out.println((char)inp.read());
        th.interrupt();
        inp.close();
    }
}
