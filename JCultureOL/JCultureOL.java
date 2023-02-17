package JCultureOL;

import java.io.Console;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.Stack;

/*
 * PROTOCOL:
 * JOINING {
 * guest connects to host
 * guest -> host: (boolean){msg}
 * IF {msg} {
 * guest -> host: (int){msglength} {UTF-16BE encoded string}
 * }
 * {
 * DENY (game already started): host -> guest: 0x00; STOP
 * ACCEPT: host -> guest: 0x01
 * DENY (other): host -> guest: (byte){ecode} (int){msglength} (byte[]){UTF-16BE encoded string}; STOP
 * }
 * host -> guest: (int){TEAM}
 * host -> guest: (int){size}
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
 * Depth first topple: top, left, right, bottom
 */

public class JCultureOL {
    private static Console console = System.console();
    private static final String resetPrompt = "\u001b[2A\u001b[0J";
    private static final String affirmative = "(ok|yes|sure|okay|y|allow|accept)";
    private static Random prng = new Random();
    private static int team;
    private static boolean turn;
    private static Tile[][] map;
    private static Color[] colors = new Color[]{Bit8Color.Grey, Bit8Color.Blue, Bit8Color.Red, Bit8Color.Green, Bit8Color.Yellow, Bit8Color.Magenta, Bit8Color.Cyan, Bit8Color.DarkGreen};
    private static int randint(int lower, int upper) {
        return (int)Math.floor(prng.nextDouble() * (upper - lower) + lower);
    }
    private static void init(int size) {
        map = new Tile[size][size];
        for (int y = 0; y < size; y ++) {
            for (int x = 0; x < size; x ++) {
                map[y][x] = new Tile(0, 1, colors[0]);
            }
        }
    }
    private static boolean tileIsFull(int x, int y) {
        int maxval = 4;
        if (x == 0 || x == map[0].length - 1) {
            maxval --;
        }
        if (y == 0 || y == map.length - 1) {
            maxval --;
        }
        return map[y][x].value > maxval;
    }
    private static void setPos(int x, int y, int team) {
        Stack<Position> pStack = new Stack<>();
        pStack.add(new Position(x, y));
        while (!pStack.empty()) {
            Position pos = pStack.pop();
            Tile til = map[pos.y][pos.x];
            til.team = team;
            til.color = colors[team];
            til.value ++;
            if (tileIsFull(pos.x, pos.y)) {
                til.value = 1;
                if (pos.y < map.length - 1) {
                    pStack.add(new Position(pos.x, pos.y+1));
                }
                if (pos.x < map[0].length - 1) {
                    pStack.add(new Position(pos.x+1, pos.y));
                }
                if (pos.x > 0) {
                    pStack.add(new Position(pos.x-1, pos.y));
                }
                if (pos.y > 0) {
                    pStack.add(new Position(pos.x, pos.y-1));
                }
            }
        }
    }
    private static void displayMove(int x, int y, int mteam) {
        System.out.println("team " + colors[mteam].toAnsi() + mteam + "\u001b[0m has made the move: " + x + ", " + y);
    }
    private static void displayBoard() {
        try {
            for (int y = 0; y < map.length; y ++) {
                for (int x = 0; x < map[0].length; x ++) {
                    System.out.print(map[y][x].color.toAnsi() + map[y][x].value + " ");
                }
                System.out.println();
            }
        } finally {
            System.out.print("\u001b[0m");
        }
    }
    private static boolean checkValidMove(int x, int y, int team) {
        return y >= 0 && y < map.length && x >= 0 && x < map[0].length && (map[y][x].team == 0 || map[y][x].team == team);
    }
    private static FullColor getUserFullColor() throws Exception {
        while (true) {
            String[] line = console.readLine("enter color: ").split(",([\\s]*)");
            if (line.length != 3) {
                console.readLine("invalid format");
                System.out.print(resetPrompt);
                continue;
            }
            if (line[0].matches("^[\\d]+$") && line[1].matches("^[\\d]+$") && line[2].matches("^[\\d]+$")) {
                int r = Integer.parseInt(line[0]);
                int g = Integer.parseInt(line[1]);
                int b = Integer.parseInt(line[2]);
                if (r < 0 || g < 0 || b < 0 || r > 255 || g > 255 || b > 255) {
                    console.readLine("invalid color");
                    System.out.print(resetPrompt);
                    continue;
                }
                return new FullColor(r, g, b);
            } else {
                console.readLine("at least one entry was not a number");
                System.out.print(resetPrompt);
            }
        }
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

        output.writeBoolean(false);

        if (input.read() == 0x00) {
            System.out.println("host denied join request");
            return;
        }

        team = input.readInt();
        final int size = input.readInt();
        init(size);
        // turn = input.readBoolean();
        turn = false;
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
                // int y = (int)(position >> 32);
                // int x = (int)(position & 0xffffffff);
                if (input.readBoolean()) {
                    turn = false;
                }
            } else {
                while (true) {
                    int code = input.read();
                    if (code == -1) {
                        host.close();
                        System.exit(1);
                    }
                    System.out.print(code + " ");
                    if (code == 0x02) {
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
    private static void hostgame(int size) throws Exception {
        init(size);
        boolean turn = true;
        int cturn = 1;
        ServerSocket server = new ServerSocket(14650);
        team = 1;
        LinkedList<Socket> playerSockets = new LinkedList<>();
        try {
        LinkedList<DataInputStream> pIns = new LinkedList<>();
        LinkedList<DataOutputStream> pOuts = new LinkedList<>();
        int pCount;
        Runtime.getRuntime().addShutdownHook(new Thread(){
            public void run() {
                for (Socket sock : playerSockets) {
                    try {
                    sock.close();
                    }catch(Exception _E) {}
                }
            } 
        });
        try { // setup players
            int i = 0;
            while (true) { // accept new players
                Socket connection = server.accept();
                DataInputStream cIn = new DataInputStream(connection.getInputStream());
                DataOutputStream cOut = new DataOutputStream(connection.getOutputStream()); // get data streams
                String usrResp; // used to check host input
                if (cIn.readBoolean()) { // if guest is sending a message
                    usrResp = console.readLine("incoming join request from \"" + connection.getRemoteSocketAddress() + "\" (" + new String(cIn.readNBytes(cIn.readInt()), StandardCharsets.UTF_16BE) + ")\nACCEPT (y/N) ");
                } else {
                    usrResp = console.readLine("incoming join request from \"" + connection.getRemoteSocketAddress() + "\" (NO MESSAGE)\nACCEPT (y/N) ");
                }
                if (usrResp.toLowerCase().matches(affirmative)) { // check if host accepts the join request
                    cOut.write(0x01);
                    cOut.writeInt(i+2); // player team
                    cOut.writeInt(size); // board size
                    if ((i + 2) > 7) { // if all default colors are used
                        usrResp = console.readLine("preset colors are all in use, would you like to input a color? (y/N) ");
                        FullColor color;
                        if (usrResp.toLowerCase().matches(affirmative)) { // ask if host wants to manually assign a color
                            color = getUserFullColor(); // get manual color
                        } else {
                            color = new FullColor(randint(150, 200), randint(150, 200), randint(150, 200)); // generate a random mid-intensity color
                        }
                        colors = Arrays.copyOf(colors, colors.length + 1); // add color to color list
                        colors[i+2] = color;
                        for (DataOutputStream pOut : pOuts) { // update all current players that there is a new color
                            pOut.write(0x01);
                            pOut.write(color.r);
                            pOut.write(color.g);
                            pOut.write(color.b);
                        }
                    }
                    cOut.writeInt(Math.max(0, colors.length - 8)); // give guest number of non-default colors
                    if (colors.length > 8) {
                        for (int j = 8; j < colors.length; j ++) { // iterate over custom colors
                            FullColor c = (FullColor) colors[j];
                            cOut.write(c.r);
                            cOut.write(c.g);
                            cOut.write(c.b); // transmit color
                        }
                    }
                    playerSockets.add(connection);
                    pIns.add(cIn);
                    pOuts.add(cOut); // add player to list
                    i ++; // increment next team
                    usrResp = console.readLine(i + " other players present, would you like to start now? (y/N) ");
                    if (usrResp.matches(affirmative)) { // check if host wants to start game
                        pCount = i + 2;
                        break;
                    }
                } else { // deny join request
                    cOut.write(0x00);
                    connection.close();
                }
            }
        } finally {
            server.close(); // close server so that attempts to join don't hang the guest client
        }
        for (DataOutputStream out : pOuts) {
            out.write(0x02);
        }
        while (true) {
            int oturn = cturn;
            int x;
            int y;
            if (turn) {
                long position = getUserPosition();
                y = (int)(position >> 32);
                x = (int)(position & 0xffffffff);
                setPos(x, y, team);
                turn = false;
                cturn = Math.max(1, (cturn+1)%pCount);
                System.out.println(cturn);
                int tracker = 0;
                for (DataOutputStream out : pOuts) {
                    out.write(0x02);
                    out.writeInt(x);
                    out.writeInt(y);
                    out.writeInt(oturn);
                    if (tracker + 2 == cturn) {
                        out.write(0x01);
                    } else {
                        out.write(0x00);
                    }
                    tracker ++;
                }
            } else {
                DataInputStream pIn = pIns.get(cturn - 2);
                DataOutputStream pOut = pOuts.get(cturn - 2);
                y = pIn.readInt();
                x = pIn.readInt();
                if (!checkValidMove(x, y, cturn)) {
                    pOut.write(0x00);
                    continue;
                }
                pOut.write(0x01);
                setPos(x, y, cturn);
                int tracker = 0;
                cturn = Math.max(1, (cturn+1)%pCount);
                for (DataOutputStream out : pOuts) {
                    // if (tracker + 2 == oturn) {
                    //     out.write(0x00);
                    //     tracker ++;
                    //     continue;
                    // }
                    out.write(0x02);
                    out.writeInt(x);
                    out.writeInt(y);
                    out.writeInt(oturn);
                    if (tracker + 2 == cturn) {
                        out.write(0x01);
                    } else {
                        out.write(0x00);
                    }
                    tracker ++;
                }
            }
            displayMove(x, y, oturn);
            displayBoard();
            if (cturn == 1) {
                turn = true;
            }
        }
        } finally {
            for (Socket socket : playerSockets) {
                socket.close();
            }
        }
    }
    private static void dbTest() throws Exception {
        init(8);
        team = 1;
        long pos = getUserPosition();
        System.out.println((pos >> 32) + " " + (pos & 0xffffffff));
    }
    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equalsIgnoreCase("--help")) {
            return;
        }
        if (args[0].equalsIgnoreCase("--test")) {
            dbTest();
            return;
        }
        if (args[0].equalsIgnoreCase("host")) {
            hostgame(args.length > 1 && args[1].matches("^[\\d]+$") ? Integer.parseInt(args[1]) : 8);
            return;
        }
        if (args[0].equalsIgnoreCase("join")) {
            Socket host = new Socket(args[1].split(":")[0], Integer.parseInt(args[1].split(":")[1]));
            try {
                join(host);
            } finally {
                host.close();
            }
            return;
        }
    }
}
class Position {
    int x;
    int y;
    Position (int x, int y) {
        this.x = x;
        this.y = y;
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
        this.color = color;
    }
}