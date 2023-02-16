import java.io.Console;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;

/*
 * PROTOCOL:
 * JOINING {
 * guest connects to host
 * {
 * DENY (game already started): host -> guest: 0x00; STOP
 * ACCEPT: host -> guest: 0x01
 * DENY (other): host -> guest: (byte){ecode} (int){msglength} (byte[]){UTF-16BE encoded string}; STOP
 * }
 * host -> guest: (int){TEAM}
 * host -> guest: (int){size}
 * host -> guest: (boolean){turn}
 * host -> guest: (int){number of non-default team colors}
 * foreach (non-default team colors) {
 * host -> guest: {r} {g} {b}
 * }
 * IF {TEAM > 7} {
 * host -> foreach (joined) {
 * host -> one: 0x01
 * host -> one: {r} {g} {b}
 * }
 * joined.add(guest)
 * }
 * }
 * host -> foreach (joined) {
 * host -> one: 0x02
 * }
 * loop {
 * IF {turn} {
 * guest -> host: {x} {y}
 * host -> guest: (boolean){valid}
 * IF {valid} {
 * guest.turn = false;
 * host -> foreach (joined) {
 * host -> one: 0x02
 * host -> one: {x} {y} {TEAM}
 * IF {one} is (next player) {
 * host -> one: 0x01
 * } ELSE {
 * host -> one: 0x00
 * }
 * }
 * }
 * }
 * }
 */

public class JCultureOL {
    private static Console console = System.console();
    private static final String resetPrompt = "\u001b[2A\u001b[0J";
    private static int team;
    private static boolean turn;
    private static Tile[][] map;
    private static Color[] colors = new Color[]{Bit8Color.Grey, Bit8Color.Blue, Bit8Color.Red, Bit8Color.Green, Bit8Color.Yellow, Bit8Color.Magenta, Bit8Color.Cyan, Bit8Color.DarkGreen};
    private static void init(int size) {
        map = new Tile[size][size];
        for (int y = 0; y < size; y ++) {
            for (int x = 0; x < size; x ++) {
                map[y][x] = new Tile(0, 1, colors[0]);
            }
        }
    }
    private static void setPos(int x, int y, int team) {
        map[y][x].team = team;
        map[y][x].color = colors[team];
        map[y][x].value ++;
    }
    private static void displayMove(int x, int y, int team) {
        int z = 1/0;
    }
    private static void displayBoard() {
        int z = 1/0;
    }
    private static boolean checkValidMove(int x, int y, int team) {
        return y >= 0 && y < map.length && x >= 0 && x < map[0].length && (map[y][x].team == 0 || map[y][x].team == team);
    }
    private static long getUserPosition() throws Exception {
        while (true) {
            String[] line = console.readLine("enter position: ").split(",([\\s]*)");
            if (line.length != 2) {
                console.readLine("invalid format");
                System.out.print(resetPrompt);
                continue;
            }
            if (line[0].matches("^[\\d]+$") && line[1].matches("^[\\d]+$")) {
                int x = Integer.parseInt(line[0]);
                int y = Integer.parseInt(line[1]);
                if (!checkValidMove(x, y, team)) {
                    console.readLine("invalid position");
                    System.out.print(resetPrompt);
                    continue;
                }
                return (((long)y)<<32)|((long)x);
            } else {
                console.readLine("at least one entry was not a number");
                System.out.print(resetPrompt);
            }
        }
    }
    private static void join(Socket host) throws Exception {
        DataInputStream input = new DataInputStream(host.getInputStream());
        DataOutputStream output = new DataOutputStream(host.getOutputStream());

        team = input.readInt();
        final int size = input.readInt();
        init(size);
        turn = input.readBoolean();
        { // loads custom colors from host
            int ndtc = input.readInt(); // number of custom colors
            colors = Arrays.copyOf(colors, ndtc+8);
            for (int i = 0; i < ndtc; i ++) {
                colors[i+8] = new FullColor(input.read(), input.read(), input.read());
            }
        }
        { // handle other players joining
            LinkedList<Color> pColors = new LinkedList<>(); // use linked list structure for speed
            while (true) {
                int hcode = input.read();
                if (hcode == 0x02) {
                    break;
                }
                if (hcode == 0x01) {
                    pColors.add(new FullColor(input.read(), input.read(), input.read()));
                }
            }
            int i = colors.length;
            colors = Arrays.copyOf(colors, colors.length + pColors.size());
            for (Color c : pColors) { // iterate and set new colors, linked list causes no significant performance drops here
                colors[i] = c;
                i ++;
            }
        }
        while (true) { // main game loop
            if (turn) {
                long position = getUserPosition();
                output.writeLong(position);
                int y = (int)(position >> 32);
                int x = (int)(position & 0xffffffff);
                if (input.readBoolean()) {
                    setPos(x, y, team);
                }
                turn = false;
            } else {
                while (true) {
                    if (input.read() == 0x02) {
                        int x = input.readInt();
                        int y = input.readInt();
                        int mteam = input.readInt();
                        setPos(x, y, mteam);
                        displayMove(x, y, mteam);
                        displayBoard();
                        if (input.read() == 0x01) {
                            turn = true;
                        } else {
                            System.out.println("please wait for other player(s)");
                        }
                        break;
                    }
                }
            }
        }
    }
    private static void hostgame(Socket other, int size) throws Exception {
        init(size);
    }
    private static void dbTest() throws Exception {
        init(8);
        team = 1;
        long pos = getUserPosition();
        System.out.println((pos >> 32) + " " + (pos & 0xffffffff));
    }
    @SuppressWarnings("all")
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equalsIgnoreCase("--help")) {
            return;
        }
        if (args[0].equalsIgnoreCase("--test")) {
            dbTest();
            return;
        }
        if (args[0].equalsIgnoreCase("host")) {
            ServerSocket server = new ServerSocket(14650);
            Thread th = null;
            try {
                Socket other = server.accept();
                th = new Thread(){
                    public void run() {
                        while (true) {
                            try {
                                server.accept().close();
                            } catch (Throwable _T) {}
                        }
                    }
                };
                hostgame(other, args.length > 0 && args[1].matches("^[\\d]+$") ? Integer.parseInt(args[1]) : 8);
            } finally {
                if (th != null) th.stop();
                server.close();
            }
            return;
        }
        if (args[0].equalsIgnoreCase("join")) {
            Socket host = new Socket(args[1].split(":")[0], Integer.parseInt(args[1].split(":")[1]));
            try {
                int code = host.getInputStream().read();
                if (code == 1) {
                    join(host);
                } else if (code == 0) {
                    System.out.println("Failed to join (1): game already started");
                } else {
                    DataInputStream inp = new DataInputStream(host.getInputStream());
                    System.out.println("Failed to join (" + code + "): " + new String(inp.readNBytes(inp.readInt()), StandardCharsets.UTF_16BE));
                }
            } finally {
                host.close();
            }
            return;
        }
    }
}
interface Color {
    String toAnsi();
}
enum Bit8Color implements Color {
    Grey(8),
    Red(9),
    Blue(12),
    Green(10),
    DarkGreen(2),
    Yellow(11),
    Magenta(13),
    Cyan(14);
    private int color;
    Bit8Color (int color) {
        this.color = color;
    }
    public String toAnsi () {
        return "\u001b[38;5;" + color + "m";
    }
}
class FullColor implements Color {
    int r;
    int g;
    int b;
    FullColor (int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }
    public String toAnsi () {
        return "\u001b[38;2;" + r + ";" + g + ";" + b + "m";
    }
}
class Tile {
    int team;
    int value;
    Color color;
    Tile (int team, int value, Color color) {
        this.team = team;
        this.value = value;
    }
}